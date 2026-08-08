package vip.mystery0.pixel.telo.data.query

import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import vip.mystery0.pixel.telo.BuildConfig

internal const val SELF_HOSTED_SERVER_VERSION_HEADER = "X-Pixel-Telo-Server-Version"
internal const val SELF_HOSTED_API_VERSION_HEADER = "X-Pixel-Telo-API-Version"
internal const val SELF_HOSTED_INSTANCE_ID_HEADER = "X-Pixel-Telo-Instance-ID"

/** 已完成格式与兼容性校验的响应身份。 */
internal data class SelfHostedResponseIdentity(
    val version: String,
    val semanticVersion: SemanticVersion,
    val apiVersion: Int,
    val instanceId: String,
)

/** 运行期身份校验失败时抛出的稳定安全异常，不携带响应值或服务地址。 */
internal class SelfHostedCompatibilityException(
    val reason: SelfHostedBlockReason,
) : IOException("Self-hosted response identity verification failed")

/**
 * 校验自建服务每个鉴权响应的版本与实例身份。
 *
 * bootstrap 模式用于尚未取得可信 Instance ID 的 `info` 请求：仍会完整检查三个 Header
 * 的存在性、唯一值、格式、最低版本与 API Version。`info` 正文验证完成后，调用方必须通过
 * [bind] 绑定身份，后续 `sources` 请求即按运行期规则校验。
 */
internal class SelfHostedCompatibilityInterceptor private constructor(
    private val allowPreRelease: Boolean,
    initialIdentity: SelfHostedIdentity?,
    private val onBlocked: ((SelfHostedBlockReason) -> Unit)?,
    private val runIfOpen: ((() -> Unit) -> Unit),
) : Interceptor {
    private val minimumServerVersion = checkNotNull(
        SemanticVersion.parse(BuildConfig.MIN_SELFHOST_SERVER_VERSION, allowPreRelease = false),
    ) { "Invalid minimum self-hosted server version" }
    private val binding = AtomicReference(initialIdentity?.toBinding())

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.request.header(AUTHORIZATION_HEADER) == null) return response

        val failure = runCatching {
            val identity = validateSelfHostedIdentityHeaders(
                headers = response.headers,
                allowPreRelease = allowPreRelease,
                minimumVersion = minimumServerVersion,
            )
            binding.get()?.validate(identity)
            if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) {
                throw SelfHostedCompatibilityException(SelfHostedBlockReason.Credentials)
            }
        }.exceptionOrNull() ?: return response

        val compatibilityFailure = failure as? SelfHostedCompatibilityException
            ?: SelfHostedCompatibilityException(SelfHostedBlockReason.IdentityHeaders)
        response.close()
        onBlocked?.let { callback ->
            runIfOpen {
                runCatching { callback(compatibilityFailure.reason) }
            }
        }
        throw compatibilityFailure
    }

    /** 将通过 `info` 正文与 Header 双重验证的身份绑定到后续响应。 */
    fun bind(identity: SelfHostedIdentity) {
        val candidate = identity.toBinding()
        val existing = binding.get()
        if (existing == null) {
            check(binding.compareAndSet(null, candidate)) {
                "Self-hosted identity was concurrently bound"
            }
        } else {
            check(existing == candidate) { "Self-hosted identity is already bound" }
        }
    }

    private fun SelfHostedIdentity.toBinding(): BoundIdentity {
        if (apiVersion != SELF_HOSTED_API_VERSION) {
            throw SelfHostedCompatibilityException(SelfHostedBlockReason.ApiVersion)
        }
        val parsedVersion = SemanticVersion.parse(version, allowPreRelease)
            ?: throw SelfHostedCompatibilityException(SelfHostedBlockReason.ServerVersion)
        if (parsedVersion < minimumServerVersion) {
            throw SelfHostedCompatibilityException(SelfHostedBlockReason.ServerVersion)
        }
        return BoundIdentity(
            instanceId = canonicalUuid(instanceId),
            minimumVersion = parsedVersion,
        )
    }

    private data class BoundIdentity(
        val instanceId: String,
        val minimumVersion: SemanticVersion,
    ) {
        fun validate(identity: SelfHostedResponseIdentity) {
            if (identity.instanceId != instanceId) {
                throw SelfHostedCompatibilityException(SelfHostedBlockReason.InstanceChanged)
            }
            if (identity.semanticVersion < minimumVersion) {
                throw SelfHostedCompatibilityException(SelfHostedBlockReason.ServerVersion)
            }
        }
    }

    companion object {
        /** 创建 `info` 请求使用的 bootstrap 校验器。 */
        fun bootstrap(allowPreRelease: Boolean): SelfHostedCompatibilityInterceptor =
            SelfHostedCompatibilityInterceptor(
                allowPreRelease = BuildConfig.DEBUG && allowPreRelease,
                initialIdentity = null,
                onBlocked = null,
                runIfOpen = { action -> action() },
            )

        /** 创建从首个响应起就绑定已验证身份的运行期校验器。 */
        fun runtime(
            identity: SelfHostedIdentity,
            onBlocked: (SelfHostedBlockReason) -> Unit,
            runIfOpen: ((() -> Unit) -> Unit),
        ): SelfHostedCompatibilityInterceptor = SelfHostedCompatibilityInterceptor(
            allowPreRelease = BuildConfig.DEBUG &&
                SemanticVersion.parse(identity.version, allowPreRelease = false) == null,
            initialIdentity = identity,
            onBlocked = onBlocked,
            runIfOpen = runIfOpen,
        )
    }
}

/** 校验并规范化三个身份 Header；重复但值完全一致的 Header 可安全合并。 */
internal fun validateSelfHostedIdentityHeaders(
    headers: Headers,
    allowPreRelease: Boolean,
    minimumVersion: SemanticVersion = checkNotNull(
        SemanticVersion.parse(BuildConfig.MIN_SELFHOST_SERVER_VERSION, allowPreRelease = false),
    ),
): SelfHostedResponseIdentity {
    val version = headers.requireSingleIdentityValue(SELF_HOSTED_SERVER_VERSION_HEADER)
    val parsedVersion = SemanticVersion.parse(version, allowPreRelease)
        ?: throw SelfHostedCompatibilityException(SelfHostedBlockReason.ServerVersion)
    if (parsedVersion < minimumVersion) {
        throw SelfHostedCompatibilityException(SelfHostedBlockReason.ServerVersion)
    }

    val rawApiVersion = headers.requireSingleIdentityValue(SELF_HOSTED_API_VERSION_HEADER)
    if (rawApiVersion != SELF_HOSTED_API_VERSION.toString()) {
        throw SelfHostedCompatibilityException(SelfHostedBlockReason.ApiVersion)
    }

    val instanceId = canonicalUuid(
        headers.requireSingleIdentityValue(SELF_HOSTED_INSTANCE_ID_HEADER),
    )
    return SelfHostedResponseIdentity(
        version = version,
        semanticVersion = parsedVersion,
        apiVersion = SELF_HOSTED_API_VERSION,
        instanceId = instanceId,
    )
}

private fun Headers.requireSingleIdentityValue(name: String): String {
    val values = values(name)
    if (values.isEmpty() || values.any { it.isEmpty() } || values.any { it != values.first() }) {
        throw SelfHostedCompatibilityException(SelfHostedBlockReason.IdentityHeaders)
    }
    return values.first()
}

internal fun canonicalUuid(value: String): String {
    val canonical = runCatching { UUID.fromString(value).toString() }
        .getOrElse {
            throw SelfHostedCompatibilityException(SelfHostedBlockReason.IdentityHeaders)
        }
    if (canonical != value.lowercase(Locale.ROOT)) {
        throw SelfHostedCompatibilityException(SelfHostedBlockReason.IdentityHeaders)
    }
    return canonical
}

private const val AUTHORIZATION_HEADER = "Authorization"
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
