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
 * 持久化已验证的自建服务非敏感配置，并维护其安全状态。
 *
 * Token 仅由 [SelfHostedCredentialStore] 加密保存。本类通过候选记录和活动指针进行
 * 分阶段提交：新密文、完整记录、活动指针依次同步落盘，避免失败覆盖旧活动配置。
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
    private val _connectionState = MutableStateFlow(readInitialState())

    val connectionState: StateFlow<SelfHostedConnectionState> = _connectionState.asStateFlow()

    /** 返回当前活动的已验证配置；损坏、缺失或不完整的记录会被视为无配置。 */
    @Synchronized
    fun loadVerifiedConfig(): VerifiedSelfHostedConfig? = readUsableActiveRecord()?.toVerifiedConfig()

    /**
     * 原子启用一份新的已验证配置。
     *
     * SharedPreferences 无法跨文件提供事务，因此候选凭据和候选配置会在活动指针更新前
     * 同步提交。任何前置步骤失败时，旧指针保持不变，并清理未被引用的候选密文。
     */
    @Synchronized
    fun commitVerified(
        config: VerifiedSelfHostedConfig,
        token: CharArray,
    ): Result<Unit> {
        val previousSlot = activeSlot()
        val candidateSlot = UUID.randomUUID().toString()
        var candidateCredentialWriteAttempted = false
        var candidateRecordWriteAttempted = false

        return try {
            candidateCredentialWriteAttempted = true
            credentialStore.save(candidateSlot, token).getOrThrow()

            val candidateRecord = StoredConfigRecord(
                credentialSlot = candidateSlot,
                backendType = QueryBackendType.SELF_HOSTED.name,
                config = StoredVerifiedConfig.from(config),
                blockedReason = null,
            )
            candidateRecordWriteAttempted = true
            check(
                preferences.edit()
                    .putString(recordKey(candidateSlot), json.encodeToString(candidateRecord))
                    .commit(),
            ) { "Unable to persist self-hosted configuration" }

            check(preferences.edit().putString(ACTIVE_SLOT_KEY, candidateSlot).commit()) {
                "Unable to activate self-hosted configuration"
            }
            _connectionState.value = SelfHostedConnectionState.Ready(config)

            if (previousSlot != null && previousSlot != candidateSlot) {
                clearInactiveCandidate(previousSlot)
            }
            Result.success(Unit)
        } catch (exception: Exception) {
            clearUncommittedCandidate(
                candidateSlot,
                candidateRecordWriteAttempted,
                candidateCredentialWriteAttempted,
            )
            Result.failure(exception)
        }
    }

    /** 将当前配置标记为安全阻止状态，不保存底层异常文本。 */
    @Synchronized
    fun markBlocked(reason: SelfHostedBlockReason) {
        val slot = activeSlot() ?: return
        val record = readRecord(slot) ?: return
        val config = record.toVerifiedConfig() ?: return
        val updated = record.copy(blockedReason = reason.toStoredValue())
        if (preferences.edit().putString(recordKey(slot), json.encodeToString(updated)).commit()) {
            _connectionState.value = SelfHostedConnectionState.Blocked(config, reason)
        }
    }

    /**
     * 清除当前配置的安全阻止状态。
     *
     * 没有携带完整验证结果的调用无法安全解除阻止，因此真正恢复只能通过
     * [commitVerified] 完成。本方法仅保留给无阻止状态的幂等清理调用。
     */
    @Synchronized
    fun clearBlockedState() {
        val slot = activeSlot() ?: return
        val record = readRecord(slot) ?: return
        val config = record.toVerifiedConfig() ?: return
        val token = credentialStore.load(slot).getOrNull()
        if (record.blockedReason == null && token != null) {
            _connectionState.value = SelfHostedConnectionState.Ready(config)
        }
        token?.fill('\u0000')
    }

    /** 供后续构建已验证 Client 的内部调用读取 Token，调用方必须清空成功结果。 */
    internal fun loadToken(): Result<CharArray> {
        val record = readUsableActiveRecord()
            ?: return Result.failure(IllegalStateException("Self-hosted configuration is unavailable"))
        return credentialStore.load(record.credentialSlot)
    }

    private fun readInitialState(): SelfHostedConnectionState {
        val activeSlot = activeSlot() ?: return SelfHostedConnectionState.NotConfigured
        clearInactiveCandidates(activeSlot)
        val record = readRecord(activeSlot)
            ?.takeIf { it.credentialSlot == activeSlot }
            ?: return SelfHostedConnectionState.BlockedWithoutConfig(SelfHostedBlockReason.Configuration)
        val config = record.toVerifiedConfig()
            ?: return SelfHostedConnectionState.BlockedWithoutConfig(SelfHostedBlockReason.Configuration)
        val blockedReason = record.blockedReason?.toBlockReason()
        if (blockedReason != null) {
            return SelfHostedConnectionState.Blocked(config, blockedReason)
        }

        val token = credentialStore.load(record.credentialSlot).getOrElse {
            return SelfHostedConnectionState.Blocked(config, SelfHostedBlockReason.Credentials)
        }
        token.fill('\u0000')
        return SelfHostedConnectionState.Ready(config)
    }

    private fun readActiveRecord(): StoredConfigRecord? {
        val slot = activeSlot() ?: return null
        return readRecord(slot)?.takeIf { it.credentialSlot == slot }
    }

    private fun readUsableActiveRecord(): StoredConfigRecord? {
        val record = readActiveRecord() ?: return null
        return record.takeIf { it.blockedReason == null && it.toVerifiedConfig() != null }
    }

    private fun activeSlot(): String? = preferences.getString(ACTIVE_SLOT_KEY, null)

    private fun readRecord(slot: String): StoredConfigRecord? {
        val raw = preferences.getString(recordKey(slot), null) ?: return null
        return runCatching { json.decodeFromString<StoredConfigRecord>(raw) }.getOrNull()
    }

    private fun recordKey(slot: String): String = "$RECORD_KEY_PREFIX$slot"

    /** 活动指针切换成功后，旧候选项清理失败不能影响已启用的新配置。 */
    private fun clearInactiveCandidate(slot: String) {
        val recordCleared = preferences.edit().remove(recordKey(slot)).commit()
        val credentialCleared = credentialStore.clear(slot).isSuccess
        if (!recordCleared || !credentialCleared) {
            clearInactiveCandidates(activeSlot())
        }
    }

    private fun clearUncommittedCandidate(
        slot: String,
        recordWriteAttempted: Boolean,
        credentialWriteAttempted: Boolean,
    ) {
        val recordCleared = !recordWriteAttempted || preferences.edit().remove(recordKey(slot)).commit()
        val credentialCleared = !credentialWriteAttempted || credentialStore.clear(slot).isSuccess
        if (!recordCleared || !credentialCleared) {
            clearInactiveCandidates(activeSlot())
        }
    }

    /** 每次恢复与失败回滚时回收未被活动指针引用的临时记录和密文。 */
    private fun clearInactiveCandidates(activeSlot: String?) {
        preferences.all.keys
            .asSequence()
            .filter { it.startsWith(RECORD_KEY_PREFIX) }
            .map { it.removePrefix(RECORD_KEY_PREFIX) }
            .filter { it != activeSlot }
            .forEach { slot -> preferences.edit().remove(recordKey(slot)).commit() }
        credentialStore.clearInactiveSlots(activeSlot)
    }

    @Serializable
    private data class StoredConfigRecord(
        val credentialSlot: String,
        val backendType: String,
        val config: StoredVerifiedConfig,
        val blockedReason: String?,
    ) {
        fun toVerifiedConfig(): VerifiedSelfHostedConfig? = runCatching {
            require(backendType == QueryBackendType.SELF_HOSTED.name)
            config.toVerifiedConfig()
        }.getOrNull()
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
            tlsMode = SelfHostedTlsMode.valueOf(tlsMode),
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
                tlsMode = config.tlsMode.name,
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
        const val RECORD_KEY_PREFIX = "verified_config_"
    }
}
