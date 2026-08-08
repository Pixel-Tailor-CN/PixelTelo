package vip.mystery0.pixel.telo.data.query

import java.util.UUID
import vip.mystery0.pixel.telo.data.remote.QueryApi
import vip.mystery0.pixel.telo.data.remote.QueryResponse

/** 实时查询服务的类型。 */
enum class QueryBackendType {
    OFFICIAL,
    SELF_HOSTED,
}

/** 官方实时查询服务的稳定标识。 */
const val OFFICIAL_BACKEND_ID = "official"

/**
 * 将服务端 Instance ID 转换为稳定的自建服务标识。
 *
 * UUID 会先被解析并重新格式化，以避免服务端返回不同大小写时产生两份 source 配置。
 */
fun selfHostedBackendId(instanceId: String): String =
    "selfhost:${UUID.fromString(instanceId).toString()}"

/** 已验证自建服务的身份信息，不包含 URL、Pin 或凭据。 */
data class SelfHostedIdentity(
    val instanceId: String,
    val version: String,
    val apiVersion: Int,
)

/**
 * 单次操作使用的不可变 Backend 快照。
 *
 * 快照只保存已经构造完成的 API 与不可变身份信息，绝不引用仍可能被用户编辑的自建配置草稿。
 */
data class QueryBackendSnapshot(
    val backendId: String,
    val type: QueryBackendType,
    val queryApi: QueryApi,
    val feedbackSupported: Boolean,
    val selfHostedIdentity: SelfHostedIdentity? = null,
) {
    init {
        require(backendId.isNotBlank()) { "Backend ID must not be blank" }
        require((type == QueryBackendType.SELF_HOSTED) == (selfHostedIdentity != null)) {
            "Self-hosted identity must match backend type"
        }
        require(feedbackSupported == (type == QueryBackendType.OFFICIAL)) {
            "Only official backend supports feedback"
        }
    }
}

/** 携带可信 Backend 归属的实时查询响应。 */
data class BackendQueryResponse(
    val backendId: String,
    val response: QueryResponse,
    val feedbackSupported: Boolean,
)
