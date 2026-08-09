package vip.mystery0.pixel.telo.data.query

import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
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
) {
    init {
        requireSupportedSelfHostedApiVersion(apiVersion)
    }
}

/**
 * 单次操作使用的不可变 Backend 快照。
 *
 * 快照只保存已经构造完成的 API 与不可变身份信息，绝不引用仍可能被用户编辑的自建配置草稿。
 */
data class QueryBackendSnapshot(
    val backendId: String,
    /** Provider 生命周期内单调变化的激活标识，用于隔离相同 Backend ID 的不同运行代次。 */
    val activationId: Long,
    val type: QueryBackendType,
    val queryApi: QueryApi,
    val feedbackSupported: Boolean,
    val selfHostedIdentity: SelfHostedIdentity? = null,
) {
    init {
        require(activationId >= 0L) { "Backend activation ID must not be negative" }
        when (type) {
            QueryBackendType.OFFICIAL -> {
                require(backendId == OFFICIAL_BACKEND_ID) {
                    "Official backend ID must be $OFFICIAL_BACKEND_ID"
                }
                require(selfHostedIdentity == null) {
                    "Official backend must not have self-hosted identity"
                }
                require(feedbackSupported) {
                    "Official backend must support feedback"
                }
            }

            QueryBackendType.SELF_HOSTED -> {
                val identity = requireNotNull(selfHostedIdentity) {
                    "Self-hosted backend must have identity"
                }
                require(backendId == selfHostedBackendId(identity.instanceId)) {
                    "Self-hosted backend ID must be derived from instance ID"
                }
                require(!feedbackSupported) {
                    "Self-hosted backend must not support feedback"
                }
            }
        }
    }
}

/**
 * 一次 Backend 操作持有的快照租约。
 *
 * 普通 Backend 切换只会退役旧 Client；只有最后一个租约释放后才会清理其凭据与网络资源。
 * 安全阻止可以越过租约立即撤销 Client，因此持有租约不代表安全错误后仍可继续请求。
 */
class QueryBackendLease internal constructor(
    val snapshot: QueryBackendSnapshot,
    private val releaseAction: () -> Unit,
    private val usableAction: () -> Boolean = { true },
) : AutoCloseable {
    private val released = AtomicBoolean(false)

    override fun close() {
        if (released.compareAndSet(false, true)) {
            releaseAction()
        }
    }

    /** 安全撤销后拒绝继续消费并发请求已经取得但尚未发布的结果。 */
    fun ensureUsable() {
        if (!usableAction()) throw QueryBackendRevokedException()
    }
}

/** 自建 Backend 租约在安全阻止期间被撤销；异常不携带服务地址或响应内容。 */
class QueryBackendRevokedException : IOException("Query backend lease was revoked")

/** 携带可信 Backend 归属的实时查询响应。 */
@ConsistentCopyVisibility
data class BackendQueryResponse private constructor(
    private val backendSnapshot: QueryBackendSnapshot,
    val response: QueryResponse,
) {
    /** 响应所属 Backend 的稳定标识，由 [backendSnapshot] 派生。 */
    val backendId: String
        get() = backendSnapshot.backendId

    /** 是否允许反馈，由已验证的 Backend 快照派生，调用方不能自行指定。 */
    val feedbackSupported: Boolean
        get() = backendSnapshot.feedbackSupported

    init {
        require(backendSnapshot.feedbackSupported || response.feedbackToken == null) {
            "Self-hosted response must not contain feedback token"
        }
    }

    companion object {
        /**
         * 从单次请求开始时读取的 Backend 快照创建响应。
         *
         * 自建服务即使错误返回反馈凭据，也会在此边界清除，避免流入官方反馈接口。
         */
        fun from(
            backendSnapshot: QueryBackendSnapshot,
            response: QueryResponse,
        ): BackendQueryResponse {
            val safeResponse = if (backendSnapshot.feedbackSupported) {
                response
            } else {
                response.copy(feedbackToken = null)
            }
            return BackendQueryResponse(backendSnapshot, safeResponse)
        }
    }
}
