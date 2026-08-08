package vip.mystery0.pixel.telo.data.query

/** 当前 App 支持的自建查询协议版本。 */
const val SELF_HOSTED_API_VERSION = 2

/** 自建服务的 TLS 验证模式。 */
enum class SelfHostedTlsMode {
    /** 使用 Android 系统信任链和主机名校验。 */
    SYSTEM,

    /** 使用精确 SPKI SHA-256 Pin 进行自建证书配对。 */
    SPKI_PIN,
}

/** 用户编辑中的自建服务草稿，仅在验证阶段短暂保存明文 Token。 */
data class SelfHostedDraft(
    val baseUrl: String,
    val token: String,
    val tlsMode: SelfHostedTlsMode,
    val spkiPin: String = "",
    val allowPreRelease: Boolean = false,
)

/**
 * 已通过完整连接验证、可持久化的自建服务配置。
 *
 * [baseUrl] 与 [spkiPin] 都必须是规范化后的值；该模型刻意不包含明文 Token。
 */
data class VerifiedSelfHostedConfig(
    val baseUrl: String,
    val tlsMode: SelfHostedTlsMode,
    val spkiPin: String,
    val instanceId: String,
    val version: String,
    val apiVersion: Int,
    val capabilities: List<String>,
    val verifiedAtEpochMillis: Long,
) {
    init {
        requireSupportedSelfHostedApiVersion(apiVersion)
    }
}

/**
 * 阻止自建 Backend 的安全分类。
 *
 * 这些值面向 UI 与持久化状态，不能存放异常文本、Token、URL 或服务端响应正文。
 */
sealed interface SelfHostedBlockReason {
    /** 活动配置记录缺失、损坏或与其凭据槽位不一致。 */
    data object Configuration : SelfHostedBlockReason

    /** 凭据缺失、损坏或被服务端拒绝。 */
    data object Credentials : SelfHostedBlockReason

    /** 系统证书链、证书有效期或主机名校验失败。 */
    data object Tls : SelfHostedBlockReason

    /** 配置的 SPKI Pin 与服务端叶子证书不匹配。 */
    data object SpkiPin : SelfHostedBlockReason

    /** 服务端版本不满足最低版本要求或不符合 SemVer。 */
    data object ServerVersion : SelfHostedBlockReason

    /** 服务端 API Version 与客户端不兼容。 */
    data object ApiVersion : SelfHostedBlockReason

    /** 服务端的 Instance ID 与已验证配置不一致。 */
    data object InstanceChanged : SelfHostedBlockReason

    /** 必需身份 Header 缺失、非法或存在冲突值。 */
    data object IdentityHeaders : SelfHostedBlockReason
}

/**
 * 自建配置构造期的稳定失败类型。
 *
 * 后续验证流程应读取 [reason] 更新 UI 状态，而不是展示异常文本。
 */
class SelfHostedConfigurationException(
    val reason: SelfHostedBlockReason,
) : IllegalArgumentException()

/** 仅接受当前受支持的自建查询协议版本，并保留可安全展示的失败分类。 */
internal fun requireSupportedSelfHostedApiVersion(apiVersion: Int) {
    if (apiVersion != SELF_HOSTED_API_VERSION) {
        throw SelfHostedConfigurationException(SelfHostedBlockReason.ApiVersion)
    }
}

/** 当前自建服务的可用状态。 */
sealed interface SelfHostedConnectionState {
    /** 用户尚未完成一份可用的自建服务配置。 */
    data object NotConfigured : SelfHostedConnectionState

    /** 已验证且可用于构建自建查询客户端。 */
    data class Ready(val config: VerifiedSelfHostedConfig) : SelfHostedConnectionState

    /** 安全校验失败，必须重新完整验证后才能继续使用。 */
    data class Blocked(
        val config: VerifiedSelfHostedConfig,
        val reason: SelfHostedBlockReason,
    ) : SelfHostedConnectionState

}
