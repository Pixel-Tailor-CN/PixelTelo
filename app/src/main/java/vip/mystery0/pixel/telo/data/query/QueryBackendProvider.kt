package vip.mystery0.pixel.telo.data.query

import android.util.Log
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import vip.mystery0.pixel.telo.BuildConfig
import vip.mystery0.pixel.telo.data.remote.QueryApi
import vip.mystery0.pixel.telo.data.remote.SelfHostedInfoResponse
import vip.mystery0.pixel.telo.data.repository.SelfHostedConfigRepository

/** 自建服务必须声明的 v2 查询能力。 */
internal const val SELF_HOSTED_QUERY_CAPABILITY = "query_v2"

/** Query Backend 对调用方暴露的运行状态。 */
sealed interface QueryBackendState {
    /** 当前存在可供新请求读取的不可变 Backend 快照。 */
    data class Ready(
        val type: QueryBackendType,
        val backendId: String,
        val activationId: Long,
    ) : QueryBackendState

    /** 用户选择了自建 Backend，但其安全状态不允许继续联网。 */
    data class Blocked(val reason: SelfHostedBlockReason) : QueryBackendState
}

/** 自建配置完整验证的稳定结果。 */
sealed interface SelfHostedValidationResult {
    /** 配置、凭据、服务身份和 sources 均已验证，并已原子切换。 */
    data class Success(val config: VerifiedSelfHostedConfig) : SelfHostedValidationResult

    /** 验证失败；[safeMessage] 不包含 URL、Token、Header 值或响应正文。 */
    data class Failure(
        val category: SelfHostedErrorCategory,
        val safeMessage: String?,
    ) : SelfHostedValidationResult
}

/** 自建验证失败的稳定分类，供 UI 本地化展示。 */
enum class SelfHostedErrorCategory {
    CONFIGURATION,
    CREDENTIALS,
    TLS,
    SPKI_PIN,
    SERVER_VERSION,
    API_VERSION,
    INSTANCE_CHANGED,
    IDENTITY_HEADERS,
    SERVICE,
    CAPABILITY,
    NETWORK,
    SERVER_RESPONSE,
    STORAGE,
    CANCELLED,
}

/**
 * 管理官方与自建查询 Client 的原子切换，并只发布已经完整验证的不可变快照。
 *
 * 官方 Query API 在 Provider 生命周期内固定，但每次激活都会创建新的 Snapshot；自建 Backend 一旦
 * 被安全阻止，[snapshot] 必须返回 `null`，调用方据此直接 Fail Open，绝不会隐式回退到官方服务。
 */
class QueryBackendProvider(
    private val officialQueryApi: QueryApi,
    private val configRepository: SelfHostedConfigRepository,
    private val clientFactory: SelfHostedQueryClientFactory,
) {
    /** 串行化配置命令与同步持久化；绝不供 [snapshot] 使用。 */
    private val commandLock = Any()
    /** 只保护已构造好的 Client/Snapshot 引用和 StateFlow 发布，临界区必须保持极短。 */
    private val snapshotLock = Any()
    private val validationMutex = Mutex()
    private val commandGeneration = AtomicLong(0L)
    private val initialOfficialSnapshot = createOfficialSnapshot(INITIAL_ACTIVATION_ID)
    private var activeGeneration = 0L
    private var activeClient: ManagedSelfHostedClient? = null
    private val managedClientsByGeneration = mutableMapOf<Long, ManagedSelfHostedClient>()
    private var currentSnapshot: QueryBackendSnapshot? = initialOfficialSnapshot
    private val _state = MutableStateFlow<QueryBackendState>(
        QueryBackendState.Ready(
            QueryBackendType.OFFICIAL,
            OFFICIAL_BACKEND_ID,
            INITIAL_ACTIVATION_ID,
        ),
    )

    val state: StateFlow<QueryBackendState> = _state.asStateFlow()

    init {
        restoreSelectedBackend()
    }

    /** 返回单次操作使用的快照；安全阻止状态固定返回 `null`。 */
    fun snapshot(): QueryBackendSnapshot? = synchronized(snapshotLock) { currentSnapshot }

    /** 判断原始 Snapshot 引用及其激活代次是否仍为当前运行实例。 */
    fun isCurrentSnapshot(snapshot: QueryBackendSnapshot): Boolean = synchronized(snapshotLock) {
        currentSnapshot === snapshot && activeGeneration == snapshot.activationId
    }

    /**
     * 获取一次查询或 source 刷新的 Snapshot 租约。
     *
     * 普通切换后旧自建 Client 会等待最后一个租约释放再关闭；安全阻止会禁止新租约并立即
     * 撤销旧 Client。租约获取与当前 Snapshot 读取共用同一短锁，不存在先读后加引用的 ABA 窗口。
     */
    fun acquireSnapshotLease(): QueryBackendLease? = synchronized(snapshotLock) {
        val snapshot = currentSnapshot ?: return@synchronized null
        if (snapshot.type == QueryBackendType.OFFICIAL) {
            return@synchronized QueryBackendLease(
                snapshot = snapshot,
                releaseAction = {},
            )
        }

        val client = activeClient
            ?.takeIf { it.acceptingLeases && !it.resourcesClosed }
            ?: return@synchronized null
        client.leaseCount += 1
        QueryBackendLease(
            snapshot = snapshot,
            releaseAction = { releaseClientLease(client) },
            usableAction = { isClientLeaseUsable(client) },
        )
    }

    /**
     * 仅当原始 [snapshot] 仍是当前激活实例时，在同一短锁窗口内执行 [publish]。
     *
     * [publish] 必须是非挂起、不可重入 Provider 的常量时间状态发布，禁止磁盘或网络操作。
     */
    fun publishIfCurrent(snapshot: QueryBackendSnapshot, publish: () -> Unit): Boolean =
        synchronized(snapshotLock) {
            if (currentSnapshot !== snapshot) return@synchronized false
            publish()
            true
        }

    /** 仅当 StateFlow 仍持有同一个状态事件时，原子执行短小状态发布。 */
    fun publishIfStateCurrent(state: QueryBackendState, publish: () -> Unit): Boolean =
        synchronized(snapshotLock) {
            if (_state.value !== state) return@synchronized false
            publish()
            true
        }

    private fun createOfficialSnapshot(activationId: Long): QueryBackendSnapshot {
        return QueryBackendSnapshot(
            backendId = OFFICIAL_BACKEND_ID,
            activationId = activationId,
            type = QueryBackendType.OFFICIAL,
            queryApi = officialQueryApi,
            feedbackSupported = true,
        )
    }

    /** 完整验证草稿，只有所有远端与本地提交步骤成功后才发布自建快照。 */
    suspend fun validateAndEnable(
        draft: SelfHostedDraft,
        token: CharArray,
    ): SelfHostedValidationResult = try {
        validationMutex.withLock {
            val generation = commandGeneration.incrementAndGet()
            val normalizedDraft = normalizeDraft(
                baseUrl = draft.baseUrl,
                tlsMode = draft.tlsMode,
                spkiPin = draft.spkiPin,
                allowPreRelease = draft.allowPreRelease,
            ).getOrElse { exception -> return@withLock validationFailure(exception) }
            validateAndPublish(normalizedDraft, token, generation)
        }
    } finally {
        token.fill('\u0000')
    }

    /** 使用当前已保存的配置与凭据重新执行完整验证，不绕过 Blocked 状态。 */
    suspend fun revalidate(): SelfHostedValidationResult = validationMutex.withLock {
        val generation = commandGeneration.incrementAndGet()
        val materialResult = synchronized(commandLock) {
            configRepository.loadRevalidationMaterial()
        }
        val material = materialResult.getOrElse { exception ->
            val failure = validationFailure(exception)
            return@withLock blockAfterRevalidationFailure(failure)
        }
        val token = material.takeToken()
        try {
            val allowPreRelease = BuildConfig.DEBUG &&
                SemanticVersion.parse(material.config.version, allowPreRelease = false) == null
            val normalizedDraft = normalizeDraft(
                baseUrl = material.config.baseUrl,
                tlsMode = material.config.tlsMode,
                spkiPin = material.config.spkiPin,
                allowPreRelease = allowPreRelease,
            ).getOrElse { exception ->
                val failure = validationFailure(exception)
                return@withLock blockAfterRevalidationFailure(failure)
            }
            val result = validateAndPublish(
                draft = normalizedDraft,
                token = token,
                generation = generation,
            )
            if (result is SelfHostedValidationResult.Failure) {
                return@withLock blockAfterRevalidationFailure(result)
            }
            result
        } finally {
            token.fill('\u0000')
        }
    }

    /** 切换到固定官方快照；保留自建配置和凭据以供用户稍后显式重验证。 */
    fun useOfficial() {
        val generation = commandGeneration.incrementAndGet()
        var clientToClose: SelfHostedClientBundle? = null
        synchronized(commandLock) {
            val selected = configRepository.selectOfficialBackend().isSuccess
            if (!selected) return@synchronized
            synchronized(snapshotLock) {
                clientToClose = retireClientLocked(activeClient)
                activeClient = null
                activeGeneration = generation
                val officialSnapshot = createOfficialSnapshot(generation)
                currentSnapshot = officialSnapshot
                _state.value = QueryBackendState.Ready(
                    QueryBackendType.OFFICIAL,
                    OFFICIAL_BACKEND_ID,
                    officialSnapshot.activationId,
                )
            }
        }
        clientToClose?.close?.invoke()
    }

    private suspend fun validateAndPublish(
        draft: NormalizedSelfHostedDraft,
        token: CharArray,
        generation: Long,
    ): SelfHostedValidationResult {
        val temporaryClient = clientFactory.createDraftClient(
            baseUrl = draft.baseUrl,
            tlsMode = draft.tlsMode,
            spkiPin = draft.spkiPin,
            allowPreRelease = draft.allowPreRelease,
            token = token,
        ).getOrElse { exception -> return validationFailure(exception) }

        val verifiedConfig = try {
            validateRemote(temporaryClient, draft)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            return validationFailure(exception)
        } finally {
            temporaryClient.close()
        }

        if (commandGeneration.get() != generation) {
            return SelfHostedValidationResult.Failure(
                category = SelfHostedErrorCategory.CANCELLED,
                safeMessage = null,
            )
        }

        val runtimeClient = clientFactory.createVerifiedClient(
            config = verifiedConfig,
            token = token,
            onBlocked = { reason -> markRuntimeBlocked(generation, reason) },
        ).getOrElse { exception -> return validationFailure(exception) }
        val runtimeSnapshot = try {
            val identity = SelfHostedIdentity(
                instanceId = verifiedConfig.instanceId,
                version = verifiedConfig.version,
                apiVersion = verifiedConfig.apiVersion,
            )
            QueryBackendSnapshot(
                backendId = selfHostedBackendId(identity.instanceId),
                activationId = generation,
                type = QueryBackendType.SELF_HOSTED,
                queryApi = runtimeClient.queryApi,
                feedbackSupported = false,
                selfHostedIdentity = identity,
            )
        } catch (exception: Exception) {
            runtimeClient.close()
            return validationFailure(exception)
        }

        val managedRuntimeClient = ManagedSelfHostedClient(generation, runtimeClient)
        var previousClientToClose: SelfHostedClientBundle? = null
        val result = synchronized(commandLock) {
            if (commandGeneration.get() != generation) {
                return@synchronized SelfHostedValidationResult.Failure(
                    category = SelfHostedErrorCategory.CANCELLED,
                    safeMessage = null,
                )
            }

            val commitResult = configRepository.commitVerified(
                config = verifiedConfig,
                token = token,
            )
            if (commitResult.isFailure) {
                return@synchronized SelfHostedValidationResult.Failure(
                    category = SelfHostedErrorCategory.STORAGE,
                    safeMessage = null,
                )
            }

            synchronized(snapshotLock) {
                previousClientToClose = retireClientLocked(activeClient)
                activeClient = managedRuntimeClient
                managedClientsByGeneration[generation] = managedRuntimeClient
                activeGeneration = generation
                currentSnapshot = runtimeSnapshot
                _state.value = QueryBackendState.Ready(
                    runtimeSnapshot.type,
                    runtimeSnapshot.backendId,
                    runtimeSnapshot.activationId,
                )
            }
            SelfHostedValidationResult.Success(verifiedConfig)
        }

        if (result !is SelfHostedValidationResult.Success) {
            runtimeClient.close()
        } else {
            previousClientToClose?.close?.invoke()
        }
        return result
    }

    private suspend fun validateRemote(
        client: SelfHostedClientBundle,
        draft: NormalizedSelfHostedDraft,
    ): VerifiedSelfHostedConfig {
        val infoResponse = client.selfHostedApi.getInfo()
        if (!infoResponse.isSuccessful) {
            infoResponse.errorBody()?.close()
            if (infoResponse.code() == HTTP_UNAUTHORIZED || infoResponse.code() == HTTP_FORBIDDEN) {
                throw SelfHostedConfigurationException(SelfHostedBlockReason.Credentials)
            }
            throw SelfHostedValidationException(SelfHostedErrorCategory.SERVER_RESPONSE)
        }
        val info = infoResponse.body()
            ?: throw SelfHostedValidationException(SelfHostedErrorCategory.SERVER_RESPONSE)
        val allowPreRelease = BuildConfig.DEBUG && draft.allowPreRelease
        val headerIdentity = validateSelfHostedIdentityHeaders(
            headers = infoResponse.headers(),
            allowPreRelease = allowPreRelease,
        )
        val identity = validateInfoBody(info, headerIdentity, allowPreRelease)
        client.bindIdentity(identity)

        // 必须在身份绑定后完成 sources 请求及其响应 Header/正文解码，才算完整验证成功。
        client.queryApi.getSources()
        return VerifiedSelfHostedConfig(
            baseUrl = draft.baseUrl,
            tlsMode = draft.tlsMode,
            spkiPin = draft.spkiPin,
            instanceId = identity.instanceId,
            version = identity.version,
            apiVersion = identity.apiVersion,
            capabilities = info.capabilities.toList(),
            verifiedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun validateInfoBody(
        info: SelfHostedInfoResponse,
        headerIdentity: SelfHostedResponseIdentity,
        allowPreRelease: Boolean,
    ): SelfHostedIdentity {
        if (info.service != SELF_HOSTED_SERVICE) {
            throw SelfHostedValidationException(SelfHostedErrorCategory.SERVICE)
        }
        val minimumVersion = checkNotNull(
            SemanticVersion.parse(BuildConfig.MIN_SELFHOST_SERVER_VERSION, allowPreRelease = false),
        )
        val bodyVersion = SemanticVersion.parse(info.version, allowPreRelease)
            ?: throw SelfHostedCompatibilityException(SelfHostedBlockReason.ServerVersion)
        if (bodyVersion < minimumVersion) {
            throw SelfHostedCompatibilityException(SelfHostedBlockReason.ServerVersion)
        }
        if (info.apiVersion != SELF_HOSTED_API_VERSION) {
            throw SelfHostedCompatibilityException(SelfHostedBlockReason.ApiVersion)
        }
        val instanceId = canonicalUuid(info.instanceId)
        if (
            info.version != headerIdentity.version ||
            info.apiVersion != headerIdentity.apiVersion ||
            instanceId != headerIdentity.instanceId
        ) {
            throw SelfHostedCompatibilityException(SelfHostedBlockReason.IdentityHeaders)
        }
        if (SELF_HOSTED_QUERY_CAPABILITY !in info.capabilities) {
            throw SelfHostedValidationException(SelfHostedErrorCategory.CAPABILITY)
        }
        return SelfHostedIdentity(
            instanceId = instanceId,
            version = info.version,
            apiVersion = info.apiVersion,
        )
    }

    private fun normalizeDraft(
        baseUrl: String,
        tlsMode: SelfHostedTlsMode,
        spkiPin: String,
        allowPreRelease: Boolean,
    ): Result<NormalizedSelfHostedDraft> = runCatching {
        val normalizedBaseUrl = normalizeSelfHostedBaseUrl(baseUrl).getOrElse { exception ->
            throw SelfHostedConfigurationException(SelfHostedBlockReason.Configuration).also {
                it.initCause(exception)
            }
        }.toString()
        val normalizedPin = when (tlsMode) {
            SelfHostedTlsMode.SYSTEM -> {
                if (spkiPin.isNotEmpty()) {
                    throw SelfHostedConfigurationException(SelfHostedBlockReason.Configuration)
                }
                ""
            }

            SelfHostedTlsMode.SPKI_PIN -> normalizeSpkiPin(spkiPin)
                .getOrElse { exception ->
                    throw SelfHostedConfigurationException(SelfHostedBlockReason.SpkiPin).also {
                        it.initCause(exception)
                    }
                }
        }
        NormalizedSelfHostedDraft(
            baseUrl = normalizedBaseUrl,
            tlsMode = tlsMode,
            spkiPin = normalizedPin,
            allowPreRelease = BuildConfig.DEBUG && allowPreRelease,
        )
    }

    /** 启动恢复只解密凭据并构建 Client，不发送任何网络请求。 */
    private fun restoreSelectedBackend(): Unit = synchronized(commandLock) {
        if (configRepository.currentBackendType() == QueryBackendType.OFFICIAL) return

        val connectionState = configRepository.connectionState.value
        if (connectionState !is SelfHostedConnectionState.Ready) {
            val reason = (connectionState as? SelfHostedConnectionState.Blocked)?.reason
                ?: SelfHostedBlockReason.Configuration
            publishBlocked(reason)
            return
        }
        val config = configRepository.loadVerifiedConfig()
        if (config == null) {
            configRepository.markBlocked(SelfHostedBlockReason.Configuration)
            publishBlocked(SelfHostedBlockReason.Configuration)
            return
        }
        if (SELF_HOSTED_QUERY_CAPABILITY !in config.capabilities) {
            configRepository.markBlocked(SelfHostedBlockReason.Configuration)
            publishBlocked(SelfHostedBlockReason.Configuration)
            return
        }
        val token = configRepository.loadToken().getOrElse {
            configRepository.markBlocked(SelfHostedBlockReason.Credentials)
            publishBlocked(SelfHostedBlockReason.Credentials)
            return
        }
        val generation = commandGeneration.incrementAndGet()
        val clientResult = try {
            clientFactory.createVerifiedClient(
                config = config,
                token = token,
                onBlocked = { reason -> markRuntimeBlocked(generation, reason) },
            )
        } finally {
            token.fill('\u0000')
        }
        val client = clientResult.getOrElse { exception ->
            val reason = exception.blockReason() ?: SelfHostedBlockReason.Configuration
            configRepository.markBlocked(reason)
            publishBlocked(reason)
            return
        }

        synchronized(snapshotLock) {
            val managedClient = ManagedSelfHostedClient(generation, client)
            activeClient = managedClient
            managedClientsByGeneration[generation] = managedClient
            activeGeneration = generation
            val identity = SelfHostedIdentity(config.instanceId, config.version, config.apiVersion)
            val snapshot = QueryBackendSnapshot(
                backendId = selfHostedBackendId(identity.instanceId),
                activationId = generation,
                type = QueryBackendType.SELF_HOSTED,
                queryApi = client.queryApi,
                feedbackSupported = false,
                selfHostedIdentity = identity,
            )
            currentSnapshot = snapshot
            _state.value = QueryBackendState.Ready(
                snapshot.type,
                snapshot.backendId,
                snapshot.activationId,
            )
        }
    }

    private fun markRuntimeBlocked(generation: Long, reason: SelfHostedBlockReason) {
        var clientToClose: SelfHostedClientBundle? = null
        var cancellationFailure: Throwable? = null
        var blockPersistenceResult: Result<Unit>? = null
        synchronized(commandLock) {
            var blocksCurrentSnapshot = false
            synchronized(snapshotLock) {
                val targetClient = managedClientsByGeneration[generation] ?: return
                blocksCurrentSnapshot = activeClient === targetClient &&
                    activeGeneration == generation &&
                    currentSnapshot?.type == QueryBackendType.SELF_HOSTED
                // 这里是安全线性化点：从同一短锁后的所有 lease 检查都会失败。
                clientToClose = revokeClientLocked(targetClient)
                if (blocksCurrentSnapshot) {
                    activeClient = null
                    activeGeneration = commandGeneration.incrementAndGet()
                    currentSnapshot = null
                }
            }

            // cancelAll() 不等待请求结束，可在不持 Snapshot 锁时尽快打断网络 I/O。
            cancellationFailure = runCatching { clientToClose?.cancelRequests?.invoke() }.exceptionOrNull()
            if (blocksCurrentSnapshot) {
                blockPersistenceResult = configRepository.markBlocked(reason)
                synchronized(snapshotLock) {
                    _state.value = QueryBackendState.Blocked(reason)
                }
            }
        }
        clientToClose?.close?.invoke()
        cancellationFailure?.let { exception ->
            Log.e(TAG, "Unable to cancel self-hosted requests", exception)
        }
        blockPersistenceResult?.exceptionOrNull()?.let { exception ->
            Log.e(TAG, "Unable to persist self-hosted security block", exception)
        }
    }

    private fun publishBlocked(reason: SelfHostedBlockReason) {
        synchronized(snapshotLock) {
            activeClient = null
            activeGeneration = commandGeneration.incrementAndGet()
            currentSnapshot = null
            _state.value = QueryBackendState.Blocked(reason)
        }
    }

    /** 显式重验证发现安全不兼容时，不能继续复用已经启用的旧自建 Client。 */
    private fun blockAfterRevalidationFailure(
        failure: SelfHostedValidationResult.Failure,
    ): SelfHostedValidationResult.Failure {
        val reason = failure.category.toBlockReasonForRevalidation() ?: return failure
        var clientToClose: SelfHostedClientBundle? = null
        var cancellationFailure: Throwable? = null
        var blockPersistenceResult: Result<Unit>? = null
        synchronized(commandLock) {
            synchronized(snapshotLock) {
                if (currentSnapshot?.type != QueryBackendType.SELF_HOSTED) return failure
                // 与运行期回调共享语义：先原子撤销，再执行任何持久化操作。
                clientToClose = revokeClientLocked(activeClient)
                activeClient = null
                activeGeneration = commandGeneration.incrementAndGet()
                currentSnapshot = null
            }
            cancellationFailure = runCatching { clientToClose?.cancelRequests?.invoke() }.exceptionOrNull()
            blockPersistenceResult = configRepository.markBlocked(reason)
            synchronized(snapshotLock) {
                _state.value = QueryBackendState.Blocked(reason)
            }
        }
        clientToClose?.close?.invoke()
        cancellationFailure?.let { exception ->
            Log.e(TAG, "Unable to cancel self-hosted requests", exception)
        }
        return if (blockPersistenceResult?.isFailure == true) {
            SelfHostedValidationResult.Failure(
                category = SelfHostedErrorCategory.STORAGE,
                safeMessage = null,
            )
        } else {
            failure
        }
    }

    /** 普通切换只标记退役；没有租约时才返回需要在锁外关闭的 Client。 */
    private fun retireClientLocked(client: ManagedSelfHostedClient?): SelfHostedClientBundle? {
        if (client == null) return null
        client.acceptingLeases = false
        client.retired = true
        if (client.leaseCount != 0 || client.resourcesClosed) return null
        client.resourcesClosed = true
        managedClientsByGeneration.remove(client.generation, client)
        return client.bundle
    }

    /** 安全阻止越过租约立即撤销 Client；租约稍后释放时不得重复关闭。 */
    private fun revokeClientLocked(client: ManagedSelfHostedClient?): SelfHostedClientBundle? {
        if (client == null || client.resourcesClosed) return null
        client.acceptingLeases = false
        client.retired = true
        client.revoked = true
        client.resourcesClosed = true
        managedClientsByGeneration.remove(client.generation, client)
        return client.bundle
    }

    private fun isClientLeaseUsable(client: ManagedSelfHostedClient): Boolean =
        synchronized(snapshotLock) { !client.revoked }

    /** 租约释放只获取 Snapshot 锁，并始终在锁外执行可能取消网络请求的 close。 */
    private fun releaseClientLease(client: ManagedSelfHostedClient) {
        var clientToClose: SelfHostedClientBundle? = null
        synchronized(snapshotLock) {
            check(client.leaseCount > 0) { "Self-hosted client lease underflow" }
            client.leaseCount -= 1
            if (client.retired && client.leaseCount == 0 && !client.resourcesClosed) {
                client.resourcesClosed = true
                managedClientsByGeneration.remove(client.generation, client)
                clientToClose = client.bundle
            }
        }
        clientToClose?.close?.invoke()
    }

    private fun validationFailure(exception: Throwable): SelfHostedValidationResult.Failure {
        if (exception is CancellationException) throw exception
        val category = when {
            exception is SelfHostedValidationException -> exception.category
            exception.findCause<SelfHostedCompatibilityException>() != null ->
                exception.findCause<SelfHostedCompatibilityException>()!!.reason.toErrorCategory()
            exception.findCause<SelfHostedConfigurationException>() != null ->
                exception.findCause<SelfHostedConfigurationException>()!!.reason.toErrorCategory()
            exception.tlsBlockReason() != null -> exception.tlsBlockReason()!!.toErrorCategory()
            exception is HttpException &&
                (exception.code() == HTTP_UNAUTHORIZED || exception.code() == HTTP_FORBIDDEN) ->
                SelfHostedErrorCategory.CREDENTIALS
            exception is HttpException -> SelfHostedErrorCategory.SERVER_RESPONSE
            exception is IOException -> SelfHostedErrorCategory.NETWORK
            else -> SelfHostedErrorCategory.SERVER_RESPONSE
        }
        return SelfHostedValidationResult.Failure(category = category, safeMessage = null)
    }

    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var current: Throwable? = this
        val visited = HashSet<Throwable>()
        while (current != null && visited.add(current)) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    private fun Throwable.blockReason(): SelfHostedBlockReason? =
        findCause<SelfHostedCompatibilityException>()?.reason
            ?: findCause<SelfHostedConfigurationException>()?.reason
            ?: tlsBlockReason()

    private fun SelfHostedBlockReason.toErrorCategory(): SelfHostedErrorCategory = when (this) {
        SelfHostedBlockReason.Configuration -> SelfHostedErrorCategory.CONFIGURATION
        SelfHostedBlockReason.Credentials -> SelfHostedErrorCategory.CREDENTIALS
        SelfHostedBlockReason.Tls -> SelfHostedErrorCategory.TLS
        SelfHostedBlockReason.SpkiPin -> SelfHostedErrorCategory.SPKI_PIN
        SelfHostedBlockReason.ServerVersion -> SelfHostedErrorCategory.SERVER_VERSION
        SelfHostedBlockReason.ApiVersion -> SelfHostedErrorCategory.API_VERSION
        SelfHostedBlockReason.InstanceChanged -> SelfHostedErrorCategory.INSTANCE_CHANGED
        SelfHostedBlockReason.IdentityHeaders -> SelfHostedErrorCategory.IDENTITY_HEADERS
    }

    private fun SelfHostedErrorCategory.toBlockReasonForRevalidation(): SelfHostedBlockReason? = when (this) {
        SelfHostedErrorCategory.CONFIGURATION,
        SelfHostedErrorCategory.SERVICE,
        SelfHostedErrorCategory.CAPABILITY,
        -> SelfHostedBlockReason.Configuration
        SelfHostedErrorCategory.CREDENTIALS -> SelfHostedBlockReason.Credentials
        SelfHostedErrorCategory.TLS -> SelfHostedBlockReason.Tls
        SelfHostedErrorCategory.SPKI_PIN -> SelfHostedBlockReason.SpkiPin
        SelfHostedErrorCategory.SERVER_VERSION -> SelfHostedBlockReason.ServerVersion
        SelfHostedErrorCategory.API_VERSION -> SelfHostedBlockReason.ApiVersion
        SelfHostedErrorCategory.INSTANCE_CHANGED -> SelfHostedBlockReason.InstanceChanged
        SelfHostedErrorCategory.IDENTITY_HEADERS -> SelfHostedBlockReason.IdentityHeaders
        SelfHostedErrorCategory.NETWORK,
        SelfHostedErrorCategory.SERVER_RESPONSE,
        SelfHostedErrorCategory.STORAGE,
        SelfHostedErrorCategory.CANCELLED,
        -> null
    }

    private class SelfHostedValidationException(
        val category: SelfHostedErrorCategory,
    ) : IOException("Self-hosted service validation failed")

    /** 已完成 URL/Pin 规范化且不包含 Token 的临时验证元数据。 */
    private data class NormalizedSelfHostedDraft(
        val baseUrl: String,
        val tlsMode: SelfHostedTlsMode,
        val spkiPin: String,
        val allowPreRelease: Boolean,
    )

    /** 只允许在 [snapshotLock] 内修改的 Client 生命周期状态。 */
    private class ManagedSelfHostedClient(
        val generation: Long,
        val bundle: SelfHostedClientBundle,
        var leaseCount: Int = 0,
        var acceptingLeases: Boolean = true,
        var retired: Boolean = false,
        var revoked: Boolean = false,
        var resourcesClosed: Boolean = false,
    )

    private companion object {
        const val TAG = "QueryBackendProvider"
        const val INITIAL_ACTIVATION_ID = 0L
        const val SELF_HOSTED_SERVICE = "pixel-telo-mast-selfhost"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
