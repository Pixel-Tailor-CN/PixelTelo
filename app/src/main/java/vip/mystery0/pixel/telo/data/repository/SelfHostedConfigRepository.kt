package vip.mystery0.pixel.telo.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import vip.mystery0.pixel.telo.data.query.QueryBackendType
import vip.mystery0.pixel.telo.data.query.SelfHostedBlockReason
import vip.mystery0.pixel.telo.data.query.SelfHostedConnectionState
import vip.mystery0.pixel.telo.data.query.SelfHostedCredentialStore
import vip.mystery0.pixel.telo.data.query.SelfHostedTlsMode
import vip.mystery0.pixel.telo.data.query.VerifiedSelfHostedConfig

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
    private var processBlockedReason: SelfHostedBlockReason? = null
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

    /** 仅供后续已验证的 Backend 切换流程选择官方 Backend，保留自建配置和凭据。 */
    @Synchronized
    internal fun selectOfficialBackend(): Result<Unit> = runCatching {
        check(
            preferences.edit()
                .putString(CURRENT_BACKEND_TYPE_KEY, BACKEND_TYPE_OFFICIAL)
                .commit(),
        ) { "Unable to select official backend" }
    }

    /**
     * 提交新配置时先保存候选密文、候选记录与激活 journal，最后写入活动指针。
     *
     * 最后一步返回 false 时，SharedPreferences 的内存状态无法可靠判定；此时保留
     * journal、候选和旧项，进程内立即阻止访问，等待下次恢复按活动指针安全裁决。
     */
    @Synchronized
    fun commitVerified(
        config: VerifiedSelfHostedConfig,
        token: CharArray,
    ): Result<Unit> {
        val previousSlot = activeSlot()
        val previousBackendType = readCurrentBackendType()
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
                .commit()
            if (!activated) {
                failClosed(config, SelfHostedBlockReason.Configuration)
                throw IllegalStateException("Unable to activate self-hosted configuration")
            }

            // journal 清理失败可在下次启动根据活动指针确认后重试，不影响新活动项。
            preferences.edit().remove(ACTIVATION_JOURNAL_KEY).commit()
            processBlockedReason = null
            _connectionState.value = SelfHostedConnectionState.Ready(config)
            clearInactiveCandidatesAfterRecovery(candidateSlot)
            Result.success(Unit)
        } catch (exception: Exception) {
            if (journalWriteAttempted) {
                // 指针写入是否已生效存在歧义，绝不能删除 journal 或候选项。
                if (processBlockedReason == null) {
                    failClosed(lastKnownGoodConfig() ?: config, SelfHostedBlockReason.Configuration)
                }
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

    /** 立即在进程内阻止后续自建访问；持久化失败也不会解除该阻止。 */
    @Synchronized
    fun markBlocked(reason: SelfHostedBlockReason) {
        val config = activeConfig() ?: lastKnownGoodConfig()
        if (config != null) {
            failClosed(config, reason)
        } else {
            processBlockedReason = reason
            _connectionState.value = SelfHostedConnectionState.NotConfigured
        }

        val slot = activeSlot() ?: return
        val record = readRecord(slot) ?: return
        val updated = record.copy(blockedReason = reason.toStoredValue())
        preferences.edit().putString(recordKey(slot), json.encodeToString(updated)).commit()
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

    private fun readInitialState(): SelfHostedConnectionState {
        migrateCurrentBackendTypeIfNeeded()
        recoverActivationJournal()
        val activeSlot = activeSlot()
        val record = activeSlot?.let(::readRecord)
        val activeConfig = record
            ?.takeIf { it.credentialSlot == activeSlot }
            ?.toVerifiedConfig()
        val lastKnownGood = lastKnownGoodConfig()
        val config = activeConfig ?: lastKnownGood ?: return SelfHostedConnectionState.NotConfigured

        processBlockedReason?.let { reason ->
            return SelfHostedConnectionState.Blocked(config, reason)
        }
        val storedReason = record?.blockedReason?.toBlockReason()
        if (storedReason != null) {
            processBlockedReason = storedReason
            return SelfHostedConnectionState.Blocked(config, storedReason)
        }
        if (activeConfig == null) {
            processBlockedReason = SelfHostedBlockReason.Configuration
            return SelfHostedConnectionState.Blocked(config, SelfHostedBlockReason.Configuration)
        }
        if (!isSlotUsable(activeSlot, record)) {
            processBlockedReason = SelfHostedBlockReason.Credentials
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

    private fun activeSlot(): String? = preferences.getString(ACTIVE_SLOT_KEY, null)

    private fun readRecord(slot: String): StoredConfigRecord? {
        val raw = preferences.getString(recordKey(slot), null) ?: return null
        return runCatching { json.decodeFromString<StoredConfigRecord>(raw) }.getOrNull()
    }

    private fun readActivationJournal(): StoredActivationJournal? {
        val raw = preferences.getString(ACTIVATION_JOURNAL_KEY, null) ?: return null
        return runCatching { json.decodeFromString<StoredActivationJournal>(raw) }.getOrNull()
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

    private fun readCurrentBackendType(): QueryBackendType = when (
        preferences.getString(CURRENT_BACKEND_TYPE_KEY, BACKEND_TYPE_OFFICIAL)
    ) {
        BACKEND_TYPE_SELF_HOSTED -> QueryBackendType.SELF_HOSTED
        else -> QueryBackendType.OFFICIAL
    }

    /** 为引入顶层 Backend 选择的升级路径保留既有可用自建状态。 */
    private fun migrateCurrentBackendTypeIfNeeded() {
        if (preferences.contains(CURRENT_BACKEND_TYPE_KEY)) return

        val slot = activeSlot()
        val record = slot?.let(::readRecord)
        val migratedType = if (slot != null && isSlotUsable(slot, record)) {
            QueryBackendType.SELF_HOSTED
        } else {
            if (slot != null) {
                processBlockedReason = if (record?.toVerifiedConfig() == null) {
                    SelfHostedBlockReason.Configuration
                } else {
                    SelfHostedBlockReason.Credentials
                }
            }
            QueryBackendType.OFFICIAL
        }
        if (!preferences.edit().putString(CURRENT_BACKEND_TYPE_KEY, migratedType.toStoredValue()).commit()) {
            // 迁移提交失败时禁止将存量自建配置当作可安全访问的 Backend。
            if (slot != null) processBlockedReason = SelfHostedBlockReason.Configuration
        }
    }

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
        )

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
        const val RECORD_KEY_PREFIX = "verified_config_"
        const val BACKEND_TYPE_OFFICIAL = "official"
        const val BACKEND_TYPE_SELF_HOSTED = "self_hosted"
        const val TLS_MODE_SYSTEM = "system"
        const val TLS_MODE_SPKI_PIN = "spki_pin"
    }
}
