package vip.mystery0.pixel.telo.data.query

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
 * 官方快照在 Provider 生命周期内固定；自建 Backend 一旦被安全阻止，[snapshot] 必须返回
 * `null`，调用方据此直接 Fail Open，绝不会隐式回退到官方服务。
 */
class QueryBackendProvider(
    officialQueryApi: QueryApi,
    private val configRepository: SelfHostedConfigRepository,
    private val clientFactory: SelfHostedQueryClientFactory,
) {
    private val lock = Any()
    private val validationMutex = Mutex()
    private val commandGeneration = AtomicLong(0L)
    private val officialSnapshot = QueryBackendSnapshot(
        backendId = OFFICIAL_BACKEND_ID,
        type = QueryBackendType.OFFICIAL,
        queryApi = officialQueryApi,
        feedbackSupported = true,
    )
    private var activeGeneration = 0L
    private var activeClient: SelfHostedClientBundle? = null
    private var currentSnapshot: QueryBackendSnapshot? = officialSnapshot
    private val _state = MutableStateFlow<QueryBackendState>(
        QueryBackendState.Ready(QueryBackendType.OFFICIAL, OFFICIAL_BACKEND_ID),
    )

    val state: StateFlow<QueryBackendState> = _state.asStateFlow()

    init {
        restoreSelectedBackend()
    }

    /** 返回单次操作使用的快照；安全阻止状态固定返回 `null`。 */
    fun snapshot(): QueryBackendSnapshot? = synchronized(lock) { currentSnapshot }

    /** 完整验证草稿，只有所有远端与本地提交步骤成功后才发布自建快照。 */
    suspend fun validateAndEnable(draft: SelfHostedDraft): SelfHostedValidationResult =
        validationMutex.withLock {
            val generation = commandGeneration.incrementAndGet()
            validateAndPublish(draft, generation)
        }

    /** 使用当前已保存的配置与凭据重新执行完整验证，不绕过 Blocked 状态。 */
    suspend fun revalidate(): SelfHostedValidationResult = validationMutex.withLock {
        val generation = commandGeneration.incrementAndGet()
        val material = configRepository.loadRevalidationMaterial().getOrElse { exception ->
            val failure = validationFailure(exception)
            blockAfterRevalidationFailure(failure)
            return@withLock failure
        }
        try {
            val allowPreRelease = BuildConfig.DEBUG &&
                SemanticVersion.parse(material.config.version, allowPreRelease = false) == null
            val result = validateAndPublish(
                draft = SelfHostedDraft(
                    baseUrl = material.config.baseUrl,
                    token = material.token.concatToString(),
                    tlsMode = material.config.tlsMode,
                    spkiPin = material.config.spkiPin,
                    allowPreRelease = allowPreRelease,
                ),
                generation = generation,
            )
            if (result is SelfHostedValidationResult.Failure) {
                blockAfterRevalidationFailure(result)
            }
            result
        } finally {
            material.token.fill('\u0000')
        }
    }

    /** 切换到固定官方快照；保留自建配置和凭据以供用户稍后显式重验证。 */
    fun useOfficial() {
        val generation = commandGeneration.incrementAndGet()
        var clientToClose: SelfHostedClientBundle? = null
        synchronized(lock) {
            if (configRepository.selectOfficialBackend().isFailure) return
            clientToClose = activeClient
            activeClient = null
            activeGeneration = generation
            currentSnapshot = officialSnapshot
            _state.value = QueryBackendState.Ready(QueryBackendType.OFFICIAL, OFFICIAL_BACKEND_ID)
        }
        clientToClose?.close?.invoke()
    }

    private suspend fun validateAndPublish(
        draft: SelfHostedDraft,
        generation: Long,
    ): SelfHostedValidationResult {
        val normalizedDraft = normalizeDraft(draft).getOrElse { exception ->
            return validationFailure(exception)
        }
        val temporaryClient = clientFactory.createDraftClient(normalizedDraft).getOrElse { exception ->
            return validationFailure(exception)
        }

        val verifiedConfig = try {
            validateRemote(temporaryClient, normalizedDraft)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            return validationFailure(exception)
        } finally {
            temporaryClient.close()
        }

        val token = normalizedDraft.token.toCharArray()
        val runtimeClient = try {
            clientFactory.createVerifiedClient(
                config = verifiedConfig,
                token = token,
                onBlocked = { reason -> markRuntimeBlocked(generation, reason) },
            ).getOrElse { exception -> return validationFailure(exception) }
        } finally {
            token.fill('\u0000')
        }
        val runtimeSnapshot = try {
            val identity = SelfHostedIdentity(
                instanceId = verifiedConfig.instanceId,
                version = verifiedConfig.version,
                apiVersion = verifiedConfig.apiVersion,
            )
            QueryBackendSnapshot(
                backendId = selfHostedBackendId(identity.instanceId),
                type = QueryBackendType.SELF_HOSTED,
                queryApi = runtimeClient.queryApi,
                feedbackSupported = false,
                selfHostedIdentity = identity,
            )
        } catch (exception: Exception) {
            runtimeClient.close()
            return validationFailure(exception)
        }

        var previousClient: SelfHostedClientBundle? = null
        var blockedClient: SelfHostedClientBundle? = null
        val commitToken = normalizedDraft.token.toCharArray()
        val result = try {
            synchronized(lock) {
                if (commandGeneration.get() != generation) {
                    return@synchronized SelfHostedValidationResult.Failure(
                        category = SelfHostedErrorCategory.CANCELLED,
                        safeMessage = null,
                    )
                }

                val commitResult = configRepository.commitVerified(
                    config = verifiedConfig,
                    token = commitToken,
                )
                if (commitResult.isFailure) {
                    blockedClient = synchronizeBlockedStateAfterCommitFailure()
                    return@synchronized SelfHostedValidationResult.Failure(
                        category = SelfHostedErrorCategory.STORAGE,
                        safeMessage = null,
                    )
                }

                previousClient = activeClient
                activeClient = runtimeClient
                activeGeneration = generation
                currentSnapshot = runtimeSnapshot
                _state.value = QueryBackendState.Ready(runtimeSnapshot.type, runtimeSnapshot.backendId)
                SelfHostedValidationResult.Success(verifiedConfig)
            }
        } finally {
            commitToken.fill('\u0000')
        }

        if (result !is SelfHostedValidationResult.Success) {
            runtimeClient.close()
            blockedClient?.close?.invoke()
        } else {
            previousClient?.close?.invoke()
        }
        return result
    }

    private suspend fun validateRemote(
        client: SelfHostedClientBundle,
        draft: SelfHostedDraft,
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

    private fun normalizeDraft(draft: SelfHostedDraft): Result<SelfHostedDraft> = runCatching {
        val baseUrl = normalizeSelfHostedBaseUrl(draft.baseUrl).getOrElse { exception ->
            throw SelfHostedConfigurationException(SelfHostedBlockReason.Configuration).also {
                it.initCause(exception)
            }
        }.toString()
        val normalizedPin = when (draft.tlsMode) {
            SelfHostedTlsMode.SYSTEM -> {
                if (draft.spkiPin.isNotEmpty()) {
                    throw SelfHostedConfigurationException(SelfHostedBlockReason.Configuration)
                }
                ""
            }

            SelfHostedTlsMode.SPKI_PIN -> normalizeSpkiPin(draft.spkiPin)
                .getOrElse { exception ->
                    throw SelfHostedConfigurationException(SelfHostedBlockReason.SpkiPin).also {
                        it.initCause(exception)
                    }
                }
        }
        draft.copy(
            baseUrl = baseUrl,
            spkiPin = normalizedPin,
            allowPreRelease = BuildConfig.DEBUG && draft.allowPreRelease,
        )
    }

    /** 启动恢复只解密凭据并构建 Client，不发送任何网络请求。 */
    private fun restoreSelectedBackend() {
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

        synchronized(lock) {
            activeClient = client
            activeGeneration = generation
            val identity = SelfHostedIdentity(config.instanceId, config.version, config.apiVersion)
            val snapshot = QueryBackendSnapshot(
                backendId = selfHostedBackendId(identity.instanceId),
                type = QueryBackendType.SELF_HOSTED,
                queryApi = client.queryApi,
                feedbackSupported = false,
                selfHostedIdentity = identity,
            )
            currentSnapshot = snapshot
            _state.value = QueryBackendState.Ready(snapshot.type, snapshot.backendId)
        }
    }

    private fun markRuntimeBlocked(generation: Long, reason: SelfHostedBlockReason) {
        var clientToClose: SelfHostedClientBundle? = null
        synchronized(lock) {
            if (activeGeneration != generation || currentSnapshot?.type != QueryBackendType.SELF_HOSTED) return
            configRepository.markBlocked(reason)
            clientToClose = activeClient
            activeClient = null
            activeGeneration = commandGeneration.incrementAndGet()
            currentSnapshot = null
            _state.value = QueryBackendState.Blocked(reason)
        }
        clientToClose?.close?.invoke()
    }

    private fun publishBlocked(reason: SelfHostedBlockReason) {
        synchronized(lock) {
            activeClient = null
            activeGeneration = commandGeneration.incrementAndGet()
            currentSnapshot = null
            _state.value = QueryBackendState.Blocked(reason)
        }
    }

    private fun synchronizeBlockedStateAfterCommitFailure(): SelfHostedClientBundle? {
        val selectedSelfHosted = configRepository.currentBackendType() == QueryBackendType.SELF_HOSTED
        val blocked = configRepository.connectionState.value as? SelfHostedConnectionState.Blocked
        if (selectedSelfHosted && blocked != null) {
            val clientToClose = activeClient
            activeClient = null
            activeGeneration = commandGeneration.incrementAndGet()
            currentSnapshot = null
            _state.value = QueryBackendState.Blocked(blocked.reason)
            return clientToClose
        }
        return null
    }

    /** 显式重验证发现安全不兼容时，不能继续复用已经启用的旧自建 Client。 */
    private fun blockAfterRevalidationFailure(failure: SelfHostedValidationResult.Failure) {
        val reason = failure.category.toBlockReasonForRevalidation() ?: return
        var clientToClose: SelfHostedClientBundle? = null
        synchronized(lock) {
            if (currentSnapshot?.type != QueryBackendType.SELF_HOSTED) return
            configRepository.markBlocked(reason)
            clientToClose = activeClient
            activeClient = null
            activeGeneration = commandGeneration.incrementAndGet()
            currentSnapshot = null
            _state.value = QueryBackendState.Blocked(reason)
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

    private companion object {
        const val SELF_HOSTED_SERVICE = "pixel-telo-mast-selfhost"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}
