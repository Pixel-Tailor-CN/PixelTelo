package vip.mystery0.pixel.telo.data.repository

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import vip.mystery0.pixel.telo.data.query.QueryBackendType
import vip.mystery0.pixel.telo.data.query.SelfHostedBlockReason
import vip.mystery0.pixel.telo.data.query.SelfHostedConfigurationException
import vip.mystery0.pixel.telo.data.query.SelfHostedConnectionState
import vip.mystery0.pixel.telo.data.query.SelfHostedCredentialStore
import vip.mystery0.pixel.telo.data.query.SelfHostedTlsMode
import vip.mystery0.pixel.telo.data.query.SELF_HOSTED_QUERY_CAPABILITY
import vip.mystery0.pixel.telo.data.query.VerifiedSelfHostedConfig

/** 显式完整重验证使用的配置与凭据；Token 只能向调用方转移一次。 */
internal class SelfHostedRevalidationMaterial(
    val config: VerifiedSelfHostedConfig,
    token: CharArray,
) {
    private var ownedToken: CharArray? = token

    /** 转移 Token 所有权；调用方完成验证后必须清空返回数组。 */
    @Synchronized
    fun takeToken(): CharArray = checkNotNull(ownedToken) {
        "Self-hosted revalidation token was already transferred"
    }.also { ownedToken = null }
}

/**
 * 持久化自建服务非敏感配置及活动 Backend。
 *
 * 凭据与配置文件之间不存在 Android 提供的跨文件事务。提交时以 journal 保留旧、
 * 新槽位的关系；恢复阶段先解析 journal 和活动项，确认其归属后才回收孤儿密文。
 */
class SelfHostedConfigRepository(
    context: Context,
    private val credentialStore: SelfHostedCredentialStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        CONFIG_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val securityBlockStore = SecurityBlockStore(context.applicationContext)
    private var processBlockedReason: SelfHostedBlockReason? = null
    private var backendSelectionBlocked = false
    private var processSelectionOverride: ProcessSelection? = null
    private val _connectionState = MutableStateFlow(readInitialState())

    val connectionState: StateFlow<SelfHostedConnectionState> = _connectionState.asStateFlow()

    /** 返回可用于构建 Client 的活动配置；进程内或持久化阻止时均拒绝返回。 */
    @Synchronized
    fun loadVerifiedConfig(): VerifiedSelfHostedConfig? {
        if (processBlockedReason != null) return null
        return readUsableActiveRecord()?.toVerifiedConfig()
    }

    /** 返回独立持久化的当前 Backend 类型，OFFICIAL 不会删除自建配置。 */
    @Synchronized
    internal fun currentBackendType(): QueryBackendType = readCurrentBackendType()

    /**
     * 选择官方 Backend，使用 journal 消除 `commit()` 返回 false 时的持久化歧义。
     *
     * 任一步失败都会保留恢复证据，但当前进程继续使用命令开始前的已发布 Backend；
     * journal 只在下次启动裁决磁盘上究竟完成了切换还是仍应回到旧选择。
     */
    @Synchronized
    internal fun selectOfficialBackend(): Result<Unit> {
        val previousSelection = currentProcessSelection()
        val journal = StoredBackendSelectionJournal(targetBackendType = BACKEND_TYPE_OFFICIAL)
        return try {
            check(
                preferences.edit()
                    .putString(BACKEND_SELECTION_JOURNAL_KEY, json.encodeToString(journal))
                    .commit(),
            ) { "Unable to persist backend selection journal" }
            check(
                preferences.edit()
                    .putString(CURRENT_BACKEND_TYPE_KEY, BACKEND_TYPE_OFFICIAL)
                    .remove(BACKEND_SELECTION_JOURNAL_KEY)
                    .remove(ACTIVATION_JOURNAL_KEY)
                    .commit(),
            ) { "Unable to select official backend" }
            backendSelectionBlocked = false
            processSelectionOverride = ProcessSelection(
                backendType = QueryBackendType.OFFICIAL,
                activeSlot = previousSelection.activeSlot,
            )
            Result.success(Unit)
        } catch (exception: Exception) {
            // commit=false 也可能已经改写 SharedPreferences 的进程内 Map；显式保留旧选择。
            processSelectionOverride = previousSelection
            Result.failure(exception)
        }
    }

    /**
     * 提交新配置时先保存候选密文、候选记录与激活 journal，最后写入活动指针。
     *
     * 最后一步返回 false 时，SharedPreferences 的内存状态无法可靠判定；此时保留
     * journal、候选和旧项，当前进程继续使用命令开始前的选择，等待下次恢复按活动指针裁决。
     */
    @Synchronized
    fun commitVerified(
        config: VerifiedSelfHostedConfig,
        token: CharArray,
    ): Result<Unit> {
        val previousSelection = currentProcessSelection()
        val previousSlot = previousSelection.activeSlot
        val previousBackendType = previousSelection.backendType
        val candidateSlot = UUID.randomUUID().toString()
        val candidateRecord = StoredConfigRecord(
            credentialSlot = candidateSlot,
            config = StoredVerifiedConfig.from(config),
            blockedReason = null,
        )
        var candidateCredentialWriteAttempted = false
        var candidateRecordWriteAttempted = false
        var journalWriteAttempted = false

        return try {
            candidateCredentialWriteAttempted = true
            credentialStore.save(candidateSlot, token).getOrThrow()

            candidateRecordWriteAttempted = true
            check(
                preferences.edit()
                    .putString(recordKey(candidateSlot), json.encodeToString(candidateRecord))
                    .commit(),
            ) { "Unable to persist self-hosted configuration" }

            journalWriteAttempted = true
            val journal = StoredActivationJournal(
                previousSlot = previousSlot,
                previousBackendType = previousBackendType.toStoredValue(),
                candidateSlot = candidateSlot,
            )
            check(preferences.edit().putString(ACTIVATION_JOURNAL_KEY, json.encodeToString(journal)).commit()) {
                "Unable to persist self-hosted activation journal"
            }

            val activated = preferences.edit()
                .putString(ACTIVE_SLOT_KEY, candidateSlot)
                .putString(CURRENT_BACKEND_TYPE_KEY, BACKEND_TYPE_SELF_HOSTED)
                .putString(LAST_KNOWN_GOOD_CONFIG_KEY, json.encodeToString(candidateRecord))
                .remove(BACKEND_SELECTION_JOURNAL_KEY)
                .commit()
            if (!activated) {
                throw IllegalStateException("Unable to activate self-hosted configuration")
            }

            // 安全阻止哨兵只能在完整远端验证和候选激活都成功后解除。
            securityBlockStore.clear().getOrThrow()
            processSelectionOverride = ProcessSelection(
                backendType = QueryBackendType.SELF_HOSTED,
                activeSlot = candidateSlot,
            )
            // journal 清理失败可在下次启动根据活动指针确认后重试，不影响新活动项。
            preferences.edit().remove(ACTIVATION_JOURNAL_KEY).commit()
            processBlockedReason = null
            backendSelectionBlocked = false
            _connectionState.value = SelfHostedConnectionState.Ready(config)
            clearInactiveCandidatesAfterRecovery(candidateSlot)
            Result.success(Unit)
        } catch (exception: Exception) {
            // 普通存储命令失败不能撤销当前可用 Backend，也不能采用 commit=false 后的内存指针。
            processSelectionOverride = previousSelection
            if (journalWriteAttempted) {
                // 指针写入是否已生效存在歧义，绝不能删除 journal 或候选项；留待重启裁决。
            } else {
                clearUnjournaledCandidate(
                    candidateSlot,
                    candidateRecordWriteAttempted,
                    candidateCredentialWriteAttempted,
                )
            }
            Result.failure(exception)
        }
    }

    /**
     * 持久阻止当前自建配置。
     *
     * 独立 AtomicFile 哨兵与活动记录任一可靠落盘即可跨重启恢复；若两者都失败，立即销毁
     * Keystore 主密钥作为最后一道 Fail Closed 兜底，使旧密文在下次启动不可解密。
     */
    @Synchronized
    fun markBlocked(reason: SelfHostedBlockReason): Result<Unit> {
        val config = activeConfig() ?: lastKnownGoodConfig()
        val slot = activeSlot()
        val record = slot?.let(::readRecord)
        val sentinelResult = securityBlockStore.write(reason.toStoredValue())
        val recordPersisted = persistBlockedReason(slot, record, reason)
        val durable = sentinelResult.isSuccess || recordPersisted ||
            credentialStore.invalidateAllCredentials().isSuccess

        if (config != null) {
            failClosed(config, reason)
        } else {
            processBlockedReason = reason
            _connectionState.value = SelfHostedConnectionState.NotConfigured
        }

        return if (durable) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Unable to persist self-hosted security block"))
        }
    }

    /**
     * 仅允许完整重验证路径解除阻止。签名要求调用方同时提供重新验证后的配置和 Token，
     * 从类型边界避免无证据调用将 Blocked 直接恢复为 Ready。
     */
    @Synchronized
    internal fun clearBlockedState(
        config: VerifiedSelfHostedConfig,
        token: CharArray,
    ): Result<Unit> = commitVerified(config, token)

    /** 供后续构建已验证 Client 的内部调用读取 Token，调用方必须清空成功结果。 */
    @Synchronized
    internal fun loadToken(): Result<CharArray> {
        if (processBlockedReason != null) {
            return Result.failure(IllegalStateException("Self-hosted configuration is blocked"))
        }
        val record = readUsableActiveRecord()
            ?: return Result.failure(IllegalStateException("Self-hosted configuration is unavailable"))
        return credentialStore.load(record.credentialSlot)
    }

    /**
     * Blocked 状态只允许通过此边界读取重验证材料，不能用于构建普通运行期 Client。
     *
     * 配置损坏、槽位错配或凭据不可解密时直接失败，调用方不得发起网络请求。
     */
    @Synchronized
    internal fun loadRevalidationMaterial(): Result<SelfHostedRevalidationMaterial> = runCatching {
        val slot = activeSlot()
            ?: throw SelfHostedConfigurationException(SelfHostedBlockReason.Configuration)
        val record = readRecord(slot)
            ?: throw SelfHostedConfigurationException(SelfHostedBlockReason.Configuration)
        if (record.credentialSlot != slot) {
            throw SelfHostedConfigurationException(SelfHostedBlockReason.Configuration)
        }
        val config = record.toVerifiedConfig()
            ?: throw SelfHostedConfigurationException(SelfHostedBlockReason.Configuration)
        val token = credentialStore.load(slot).getOrElse { exception ->
            throw SelfHostedConfigurationException(SelfHostedBlockReason.Credentials).also {
                it.initCause(exception)
            }
        }
        SelfHostedRevalidationMaterial(config = config, token = token)
    }

    private fun readInitialState(): SelfHostedConnectionState {
        recoverBackendSelectionJournal()
        migrateCurrentBackendTypeIfNeeded()
        // 强制解析一次选择值，使未知或类型损坏的值在恢复 Client 前进入 fail-closed。
        readCurrentBackendType()
        recoverActivationJournal()
        val activeSlot = activeSlot()
        val record = activeSlot?.let(::readRecord)
        val activeConfig = record
            ?.takeIf { it.credentialSlot == activeSlot }
            ?.toVerifiedConfig()
        val lastKnownGood = lastKnownGoodConfig()
        readSecurityBlockReason()?.let { reason -> processBlockedReason = reason }
        val config = activeConfig ?: lastKnownGood ?: return SelfHostedConnectionState.NotConfigured

        processBlockedReason?.let { reason ->
            persistBlockedReason(activeSlot, record, reason)
            return SelfHostedConnectionState.Blocked(config, reason)
        }
        val storedReason = record?.blockedReason?.let { rawReason ->
            rawReason.toBlockReason() ?: SelfHostedBlockReason.Configuration
        }
        if (storedReason != null) {
            processBlockedReason = storedReason
            return SelfHostedConnectionState.Blocked(config, storedReason)
        }
        if (activeConfig == null) {
            processBlockedReason = SelfHostedBlockReason.Configuration
            persistBlockedReason(activeSlot, record, SelfHostedBlockReason.Configuration)
            return SelfHostedConnectionState.Blocked(config, SelfHostedBlockReason.Configuration)
        }
        if (!isSlotUsable(activeSlot, record)) {
            processBlockedReason = SelfHostedBlockReason.Credentials
            persistBlockedReason(activeSlot, record, SelfHostedBlockReason.Credentials)
            return SelfHostedConnectionState.Blocked(config, SelfHostedBlockReason.Credentials)
        }

        clearInactiveCandidatesAfterRecovery(activeSlot)
        return SelfHostedConnectionState.Ready(config)
    }

    /** 先验证 journal 与活动项，再决定保留、回滚或回收候选项。 */
    private fun recoverActivationJournal() {
        val journal = readActivationJournal() ?: run {
            // 没有活动指针的首次配置中断同样需要回收孤儿项。
            if (activeSlot() == null) clearInactiveCandidatesAfterRecovery(null)
            return
        }
        val activeSlot = activeSlot()
        val candidate = readRecord(journal.candidateSlot)
        val previous = journal.previousSlot?.let(::readRecord)

        when {
            activeSlot == journal.candidateSlot && isSlotUsable(journal.candidateSlot, candidate) -> {
                finalizeRecoveredActivation(journal.candidateSlot, candidate!!)
            }

            journal.previousSlot != null && activeSlot == journal.previousSlot &&
                isSlotUsable(journal.previousSlot, previous) -> {
                finalizeRecoveredRollback(
                    previousSlot = journal.previousSlot,
                    previousBackendType = journal.previousBackendType,
                    candidateSlot = journal.candidateSlot,
                )
            }

            journal.previousSlot == null && activeSlot == null -> {
                // 首次提交未完成，候选未被任何活动指针引用。
                preferences.edit().remove(ACTIVATION_JOURNAL_KEY).commit()
                clearInactiveCandidatesAfterRecovery(null)
            }

            journal.previousSlot != null && isSlotUsable(journal.previousSlot, previous) -> {
                // 指针异常时优先恢复旧的、已验证且可解密的配置。
                if (
                    preferences.edit()
                        .putString(ACTIVE_SLOT_KEY, journal.previousSlot)
                        .putString(CURRENT_BACKEND_TYPE_KEY, journal.previousBackendType)
                        .putString(LAST_KNOWN_GOOD_CONFIG_KEY, json.encodeToString(previous))
                        .commit()
                ) {
                    finalizeRecoveredRollback(
                        previousSlot = journal.previousSlot,
                        previousBackendType = journal.previousBackendType,
                        candidateSlot = journal.candidateSlot,
                    )
                }
            }

            else -> {
                // 未能可靠裁决时保留 journal 和所有引用项，并在状态层 fail-closed。
                val fallback = candidate?.toVerifiedConfig() ?: previous?.toVerifiedConfig() ?: lastKnownGoodConfig()
                if (fallback != null) processBlockedReason = SelfHostedBlockReason.Configuration
            }
        }
    }

    private fun finalizeRecoveredActivation(slot: String, record: StoredConfigRecord) {
        if (
            preferences.edit()
                .putString(ACTIVE_SLOT_KEY, slot)
                .putString(CURRENT_BACKEND_TYPE_KEY, BACKEND_TYPE_SELF_HOSTED)
                .putString(LAST_KNOWN_GOOD_CONFIG_KEY, json.encodeToString(record))
                .remove(ACTIVATION_JOURNAL_KEY)
                .commit()
        ) {
            clearInactiveCandidatesAfterRecovery(slot)
        }
    }

    private fun finalizeRecoveredRollback(
        previousSlot: String,
        previousBackendType: String,
        candidateSlot: String,
    ) {
        val previous = readRecord(previousSlot)
        if (
            previous != null && preferences.edit()
                .putString(ACTIVE_SLOT_KEY, previousSlot)
                .putString(CURRENT_BACKEND_TYPE_KEY, previousBackendType)
                .putString(LAST_KNOWN_GOOD_CONFIG_KEY, json.encodeToString(previous))
                .remove(ACTIVATION_JOURNAL_KEY)
                .commit()
        ) {
            preferences.edit().remove(recordKey(candidateSlot)).commit()
            credentialStore.clear(candidateSlot)
            clearInactiveCandidatesAfterRecovery(previousSlot)
        }
    }

    private fun activeConfig(): VerifiedSelfHostedConfig? = readUsableActiveRecord()?.toVerifiedConfig()

    private fun lastKnownGoodConfig(): VerifiedSelfHostedConfig? = preferences
        .getString(LAST_KNOWN_GOOD_CONFIG_KEY, null)
        ?.let { raw -> runCatching { json.decodeFromString<StoredConfigRecord>(raw) }.getOrNull() }
        ?.toVerifiedConfig()

    private fun readUsableActiveRecord(): StoredConfigRecord? {
        val slot = activeSlot() ?: return null
        val record = readRecord(slot) ?: return null
        return record.takeIf { it.credentialSlot == slot && it.blockedReason == null && it.toVerifiedConfig() != null }
    }

    private fun activeSlot(): String? {
        val override = processSelectionOverride
        return if (override != null) {
            override.activeSlot
        } else {
            preferences.getString(ACTIVE_SLOT_KEY, null)
        }
    }

    private fun readRecord(slot: String): StoredConfigRecord? {
        val raw = preferences.getString(recordKey(slot), null) ?: return null
        return runCatching { json.decodeFromString<StoredConfigRecord>(raw) }.getOrNull()
    }

    private fun readActivationJournal(): StoredActivationJournal? {
        val raw = preferences.getString(ACTIVATION_JOURNAL_KEY, null) ?: return null
        return runCatching { json.decodeFromString<StoredActivationJournal>(raw) }.getOrNull()
    }

    /** 启动时优先完成 Backend 选择 journal；恢复失败时保留 journal 并 Fail Closed。 */
    private fun recoverBackendSelectionJournal() {
        val raw = runCatching { preferences.getString(BACKEND_SELECTION_JOURNAL_KEY, null) }
            .getOrElse {
                backendSelectionBlocked = true
                processBlockedReason = SelfHostedBlockReason.Configuration
                return
            } ?: return
        val journal = runCatching { json.decodeFromString<StoredBackendSelectionJournal>(raw) }
            .getOrElse {
                backendSelectionBlocked = true
                processBlockedReason = SelfHostedBlockReason.Configuration
                return
            }
        if (journal.targetBackendType != BACKEND_TYPE_OFFICIAL) {
            backendSelectionBlocked = true
            processBlockedReason = SelfHostedBlockReason.Configuration
            return
        }
        if (
            preferences.edit()
                .putString(CURRENT_BACKEND_TYPE_KEY, BACKEND_TYPE_OFFICIAL)
                .remove(BACKEND_SELECTION_JOURNAL_KEY)
                .remove(ACTIVATION_JOURNAL_KEY)
                .commit()
        ) {
            backendSelectionBlocked = false
        } else {
            backendSelectionBlocked = true
            processBlockedReason = SelfHostedBlockReason.Configuration
        }
    }

    /** 仅当配置和凭据均可恢复时，才允许 journal 将其认定为可用活动项。 */
    private fun isSlotUsable(slot: String, record: StoredConfigRecord?): Boolean {
        if (record?.credentialSlot != slot || record.toVerifiedConfig() == null) return false
        val token = credentialStore.load(slot).getOrNull() ?: return false
        token.fill('\u0000')
        return true
    }

    private fun failClosed(config: VerifiedSelfHostedConfig, reason: SelfHostedBlockReason) {
        processBlockedReason = reason
        _connectionState.value = SelfHostedConnectionState.Blocked(config, reason)
    }

    private fun persistBlockedReason(
        slot: String?,
        record: StoredConfigRecord?,
        reason: SelfHostedBlockReason,
    ): Boolean {
        if (slot == null || record == null) return false
        val updated = record.copy(blockedReason = reason.toStoredValue())
        return runCatching {
            preferences.edit().putString(recordKey(slot), json.encodeToString(updated)).commit()
        }.getOrDefault(false)
    }

    /** 哨兵缺失返回 null；哨兵损坏或读取异常必须按配置安全错误阻止。 */
    private fun readSecurityBlockReason(): SelfHostedBlockReason? = securityBlockStore.read().fold(
        onSuccess = { storedValue ->
            storedValue?.toBlockReason() ?: storedValue?.let { SelfHostedBlockReason.Configuration }
        },
        onFailure = { SelfHostedBlockReason.Configuration },
    )

    /** 仅回收无 journal 保护且未被活动指针引用的候选项。 */
    private fun clearInactiveCandidatesAfterRecovery(activeSlot: String?) {
        if (readActivationJournal() != null) return
        preferences.all.keys
            .asSequence()
            .filter { it.startsWith(RECORD_KEY_PREFIX) }
            .map { it.removePrefix(RECORD_KEY_PREFIX) }
            .filter { it != activeSlot }
            .forEach { slot -> preferences.edit().remove(recordKey(slot)).commit() }
        credentialStore.clearInactiveSlots(activeSlot)
    }

    private fun clearUnjournaledCandidate(
        slot: String,
        recordWriteAttempted: Boolean,
        credentialWriteAttempted: Boolean,
    ) {
        if (recordWriteAttempted) preferences.edit().remove(recordKey(slot)).commit()
        if (credentialWriteAttempted) credentialStore.clear(slot)
        clearInactiveCandidatesAfterRecovery(activeSlot())
    }

    private fun readCurrentBackendType(): QueryBackendType {
        processSelectionOverride?.let { return it.backendType }
        if (backendSelectionBlocked) return QueryBackendType.SELF_HOSTED
        val storedValue = runCatching { preferences.getString(CURRENT_BACKEND_TYPE_KEY, null) }
            .getOrElse {
                processBlockedReason = SelfHostedBlockReason.Configuration
                return QueryBackendType.SELF_HOSTED
            }
        return when (storedValue) {
            BACKEND_TYPE_OFFICIAL -> QueryBackendType.OFFICIAL
            BACKEND_TYPE_SELF_HOSTED -> QueryBackendType.SELF_HOSTED
            null -> if (hasLegacySelfHostedSelectionEvidence()) {
                processBlockedReason = SelfHostedBlockReason.Configuration
                QueryBackendType.SELF_HOSTED
            } else {
                QueryBackendType.OFFICIAL
            }
            else -> {
                processBlockedReason = SelfHostedBlockReason.Configuration
                QueryBackendType.SELF_HOSTED
            }
        }
    }

    /** 为引入顶层 Backend 选择的升级路径保留任何旧自建选择证据。 */
    private fun migrateCurrentBackendTypeIfNeeded() {
        if (preferences.contains(CURRENT_BACKEND_TYPE_KEY)) return

        val hasSelfHostedEvidence = hasLegacySelfHostedSelectionEvidence()
        val slot = runCatching(::activeSlot).getOrNull()
        val record = slot?.let(::readRecord)
        val migratedType = if (hasSelfHostedEvidence) {
            if (slot == null || record?.toVerifiedConfig() == null) {
                processBlockedReason = SelfHostedBlockReason.Configuration
            } else if (!isSlotUsable(slot, record)) {
                processBlockedReason = SelfHostedBlockReason.Credentials
            }
            QueryBackendType.SELF_HOSTED
        } else {
            QueryBackendType.OFFICIAL
        }
        if (!preferences.edit().putString(CURRENT_BACKEND_TYPE_KEY, migratedType.toStoredValue()).commit()) {
            // 有旧自建证据时，即使迁移提交失败也必须保持自建选择并阻止联网。
            if (hasSelfHostedEvidence) processBlockedReason = SelfHostedBlockReason.Configuration
        }
    }

    private fun hasLegacySelfHostedSelectionEvidence(): Boolean =
        preferences.contains(ACTIVE_SLOT_KEY) || preferences.contains(LAST_KNOWN_GOOD_CONFIG_KEY)

    private fun currentProcessSelection(): ProcessSelection = ProcessSelection(
        backendType = readCurrentBackendType(),
        activeSlot = activeSlot(),
    )

    private fun QueryBackendType.toStoredValue(): String = when (this) {
        QueryBackendType.OFFICIAL -> BACKEND_TYPE_OFFICIAL
        QueryBackendType.SELF_HOSTED -> BACKEND_TYPE_SELF_HOSTED
    }

    private fun recordKey(slot: String): String = "$RECORD_KEY_PREFIX$slot"

    @Serializable
    private data class StoredActivationJournal(
        val previousSlot: String?,
        val previousBackendType: String = BACKEND_TYPE_OFFICIAL,
        val candidateSlot: String,
    )

    @Serializable
    private data class StoredBackendSelectionJournal(
        val targetBackendType: String,
    )

    private data class ProcessSelection(
        val backendType: QueryBackendType,
        val activeSlot: String?,
    )

    @Serializable
    private data class StoredConfigRecord(
        val credentialSlot: String,
        val config: StoredVerifiedConfig,
        val blockedReason: String?,
    ) {
        fun toVerifiedConfig(): VerifiedSelfHostedConfig? = runCatching { config.toVerifiedConfig() }.getOrNull()
    }

    @Serializable
    private data class StoredVerifiedConfig(
        val baseUrl: String,
        val tlsMode: String,
        val spkiPin: String,
        val instanceId: String,
        val version: String,
        val apiVersion: Int,
        val capabilities: List<String>,
        val verifiedAtEpochMillis: Long,
    ) {
        fun toVerifiedConfig(): VerifiedSelfHostedConfig = VerifiedSelfHostedConfig(
            baseUrl = baseUrl,
            tlsMode = when (tlsMode) {
                TLS_MODE_SYSTEM -> SelfHostedTlsMode.SYSTEM
                TLS_MODE_SPKI_PIN -> SelfHostedTlsMode.SPKI_PIN
                else -> throw IllegalArgumentException("Unknown TLS mode")
            },
            spkiPin = spkiPin,
            instanceId = instanceId,
            version = version,
            apiVersion = apiVersion,
            capabilities = capabilities,
            verifiedAtEpochMillis = verifiedAtEpochMillis,
        ).also {
            require(SELF_HOSTED_QUERY_CAPABILITY in it.capabilities) {
                "Required self-hosted capability is missing"
            }
        }

        companion object {
            fun from(config: VerifiedSelfHostedConfig) = StoredVerifiedConfig(
                baseUrl = config.baseUrl,
                tlsMode = when (config.tlsMode) {
                    SelfHostedTlsMode.SYSTEM -> TLS_MODE_SYSTEM
                    SelfHostedTlsMode.SPKI_PIN -> TLS_MODE_SPKI_PIN
                },
                spkiPin = config.spkiPin,
                instanceId = config.instanceId,
                version = config.version,
                apiVersion = config.apiVersion,
                capabilities = config.capabilities,
                verifiedAtEpochMillis = config.verifiedAtEpochMillis,
            )
        }
    }

    private fun SelfHostedBlockReason.toStoredValue(): String = when (this) {
        SelfHostedBlockReason.Configuration -> "configuration"
        SelfHostedBlockReason.Credentials -> "credentials"
        SelfHostedBlockReason.Tls -> "tls"
        SelfHostedBlockReason.SpkiPin -> "spki_pin"
        SelfHostedBlockReason.ServerVersion -> "server_version"
        SelfHostedBlockReason.ApiVersion -> "api_version"
        SelfHostedBlockReason.InstanceChanged -> "instance_changed"
        SelfHostedBlockReason.IdentityHeaders -> "identity_headers"
    }

    private fun String.toBlockReason(): SelfHostedBlockReason? = when (this) {
        "configuration" -> SelfHostedBlockReason.Configuration
        "credentials" -> SelfHostedBlockReason.Credentials
        "tls" -> SelfHostedBlockReason.Tls
        "spki_pin" -> SelfHostedBlockReason.SpkiPin
        "server_version" -> SelfHostedBlockReason.ServerVersion
        "api_version" -> SelfHostedBlockReason.ApiVersion
        "instance_changed" -> SelfHostedBlockReason.InstanceChanged
        "identity_headers" -> SelfHostedBlockReason.IdentityHeaders
        else -> null
    }

    private companion object {
        const val CONFIG_PREFERENCES_NAME = "self_hosted_config"
        const val ACTIVE_SLOT_KEY = "active_slot"
        const val CURRENT_BACKEND_TYPE_KEY = "current_backend_type"
        const val LAST_KNOWN_GOOD_CONFIG_KEY = "last_known_good_config"
        const val ACTIVATION_JOURNAL_KEY = "activation_journal"
        const val BACKEND_SELECTION_JOURNAL_KEY = "backend_selection_journal"
        const val RECORD_KEY_PREFIX = "verified_config_"
        const val BACKEND_TYPE_OFFICIAL = "official"
        const val BACKEND_TYPE_SELF_HOSTED = "self_hosted"
        const val TLS_MODE_SYSTEM = "system"
        const val TLS_MODE_SPKI_PIN = "spki_pin"
    }
}

/** 独立于 SharedPreferences 活动记录的安全阻止哨兵，文件不参与系统备份。 */
private class SecurityBlockStore(context: Context) {
    private val baseFile = File(context.noBackupFilesDir, FILE_NAME)
    private val atomicFile = AtomicFile(baseFile)
    private val sentinelFiles = listOf(
        baseFile,
        File(baseFile.path + NEW_SUFFIX),
        File(baseFile.path + BACKUP_SUFFIX),
    )

    @Synchronized
    fun write(value: String): Result<Unit> = runCatching {
        val output = atomicFile.startWrite()
        try {
            output.write(value.toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
            atomicFile.finishWrite(output)
            check(read().getOrThrow() == value) {
                "Unable to verify self-hosted security block sentinel"
            }
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw exception
        }
    }

    @Synchronized
    fun read(): Result<String?> = try {
        val bytes = atomicFile.readFully()
        check(bytes.size in 1..MAX_VALUE_BYTES) { "Invalid self-hosted security block sentinel" }
        Result.success(String(bytes, StandardCharsets.UTF_8))
    } catch (exception: FileNotFoundException) {
        val noSentinelFiles = sentinelFiles.all { file -> Files.notExists(file.toPath()) }
        if (noSentinelFiles) Result.success(null) else Result.failure(exception)
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    @Synchronized
    fun clear(): Result<Unit> = runCatching {
        atomicFile.delete()
        check(sentinelFiles.all { file -> Files.notExists(file.toPath()) }) {
            "Unable to clear self-hosted security block sentinel"
        }
    }

    private companion object {
        const val FILE_NAME = "self_hosted_security_block"
        const val NEW_SUFFIX = ".new"
        const val BACKUP_SUFFIX = ".bak"
        const val MAX_VALUE_BYTES = 64
    }
}
