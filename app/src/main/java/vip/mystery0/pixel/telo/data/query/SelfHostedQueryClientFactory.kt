package vip.mystery0.pixel.telo.data.query

import java.io.IOException
import java.security.SecureRandom
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import vip.mystery0.pixel.telo.data.remote.QueryApi
import vip.mystery0.pixel.telo.data.remote.SelfHostedApi

private const val AUTHORIZATION_HEADER = "Authorization"
private const val BEARER_PREFIX = "Bearer "
private const val SPKI_SHA256_PREFIX = "sha256/"

/** 自建查询专用 API 与资源释放入口。 */
data class SelfHostedClientBundle(
    val queryApi: QueryApi,
    val selfHostedApi: SelfHostedApi,
    val bindIdentity: (SelfHostedIdentity) -> Unit,
    val close: () -> Unit,
)

/**
 * 按草稿或已验证配置创建完全独立的自建 OkHttp/Retrofit Client。
 *
 * Factory 从不读取或复用官方 Client 的 Dispatcher、ConnectionPool、CookieJar、
 * Authenticator 或 TLS 状态。所有自动 Redirect 均关闭，Bearer Token 只发送到精确 Origin。
 */
class SelfHostedQueryClientFactory(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** 创建仅用于完整连接验证的临时 Client。 */
    fun createDraftClient(draft: SelfHostedDraft): Result<SelfHostedClientBundle> {
        val token = draft.token.toCharArray()
        return try {
            createDraftClient(
                baseUrl = draft.baseUrl,
                tlsMode = draft.tlsMode,
                spkiPin = draft.spkiPin,
                allowPreRelease = draft.allowPreRelease,
                token = token,
            )
        } finally {
            token.fill('\u0000')
        }
    }

    /**
     * 使用调用方持有的可清零 Token 创建临时 Client。
     *
     * Factory 只在 Client 内复制 Token，不接管 [token]；调用方必须在完整验证结束后清空它。
     */
    internal fun createDraftClient(
        baseUrl: String,
        tlsMode: SelfHostedTlsMode,
        spkiPin: String,
        allowPreRelease: Boolean,
        token: CharArray,
    ): Result<SelfHostedClientBundle> = runCatching {
        val normalizedBaseUrl = normalizeSelfHostedBaseUrl(baseUrl)
            .getOrElse { throw configurationFailure(SelfHostedBlockReason.Configuration, it) }
        val normalizedPin = normalizePinForMode(tlsMode, spkiPin)
        createClient(
            baseUrl = normalizedBaseUrl,
            tlsMode = tlsMode,
            normalizedPin = normalizedPin,
            token = token,
            allowPreRelease = allowPreRelease,
            verifiedIdentity = null,
            onBlocked = null,
        )
    }

    /** 创建运行期 Client；TLS 安全失败会触发 [onBlocked]，后续流程据此 Fail Closed。 */
    fun createVerifiedClient(
        config: VerifiedSelfHostedConfig,
        token: CharArray,
        onBlocked: (SelfHostedBlockReason) -> Unit,
    ): Result<SelfHostedClientBundle> = runCatching {
        val baseUrl = normalizeSelfHostedBaseUrl(config.baseUrl)
            .getOrElse { throw configurationFailure(SelfHostedBlockReason.Configuration, it) }
        if (baseUrl.toString() != config.baseUrl) {
            throw SelfHostedConfigurationException(SelfHostedBlockReason.Configuration)
        }
        val normalizedPin = normalizePinForMode(config.tlsMode, config.spkiPin)
        if (normalizedPin != config.spkiPin) {
            throw SelfHostedConfigurationException(SelfHostedBlockReason.Configuration)
        }
        createClient(
            baseUrl = baseUrl,
            tlsMode = config.tlsMode,
            normalizedPin = normalizedPin,
            token = token,
            allowPreRelease = false,
            verifiedIdentity = SelfHostedIdentity(
                instanceId = config.instanceId,
                version = config.version,
                apiVersion = config.apiVersion,
            ),
            onBlocked = onBlocked,
        )
    }

    private fun createClient(
        baseUrl: HttpUrl,
        tlsMode: SelfHostedTlsMode,
        normalizedPin: String,
        token: CharArray,
        allowPreRelease: Boolean,
        verifiedIdentity: SelfHostedIdentity?,
        onBlocked: ((SelfHostedBlockReason) -> Unit)?,
    ): SelfHostedClientBundle {
        if (token.isEmpty()) {
            throw SelfHostedConfigurationException(SelfHostedBlockReason.Credentials)
        }

        val bearerInterceptor = ScopedBearerInterceptor(baseUrl, token)
        var pinnedTrustManager: SelfHostedPinnedTrustManager? = null
        var client: OkHttpClient? = null
        try {
            val compatibilityInterceptor = if (verifiedIdentity == null) {
                SelfHostedCompatibilityInterceptor.bootstrap(allowPreRelease)
            } else {
                SelfHostedCompatibilityInterceptor.runtime(
                    identity = verifiedIdentity,
                    onBlocked = requireNotNull(onBlocked),
                    runIfOpen = bearerInterceptor::runIfOpen,
                )
            }
            val builder = OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .authenticator(Authenticator.NONE)
                .proxyAuthenticator(Authenticator.NONE)
                .cookieJar(CookieJar.NO_COOKIES)
                .addInterceptor(bearerInterceptor)
                .addInterceptor(compatibilityInterceptor)

            if (onBlocked != null) {
                builder.addInterceptor(
                    TlsFailureBlockingInterceptor(
                        onBlocked = onBlocked,
                        runIfOpen = bearerInterceptor::runIfOpen,
                    ),
                )
            }

            if (tlsMode == SelfHostedTlsMode.SPKI_PIN) {
                val expectedDigest = decodeNormalizedPin(normalizedPin)
                try {
                    pinnedTrustManager = SelfHostedPinnedTrustManager(
                        targetHost = baseUrl.host,
                        expectedSpkiSha256 = expectedDigest,
                    )
                } finally {
                    expectedDigest.fill(0)
                }
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(pinnedTrustManager), SecureRandom())
                }
                builder.sslSocketFactory(sslContext.socketFactory, pinnedTrustManager)
            }

            client = builder.build()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(
                    json.asConverterFactory("application/json".toMediaType()),
                )
                .build()
            val closeAction = createCloseAction(client, bearerInterceptor)
            return SelfHostedClientBundle(
                queryApi = retrofit.create(QueryApi::class.java),
                selfHostedApi = retrofit.create(SelfHostedApi::class.java),
                bindIdentity = compatibilityInterceptor::bind,
                close = closeAction,
            )
        } catch (exception: Exception) {
            bearerInterceptor.close()
            pinnedTrustManager?.clearPin()
            client?.closeResources()
            throw exception
        }
    }

    private fun normalizePinForMode(mode: SelfHostedTlsMode, rawPin: String): String = when (mode) {
        SelfHostedTlsMode.SYSTEM -> {
            if (rawPin.isNotEmpty()) {
                throw SelfHostedConfigurationException(SelfHostedBlockReason.Configuration)
            }
            ""
        }

        SelfHostedTlsMode.SPKI_PIN -> normalizeSpkiPin(rawPin)
            .getOrElse { throw configurationFailure(SelfHostedBlockReason.SpkiPin, it) }
    }

    private fun decodeNormalizedPin(pin: String): ByteArray =
        Base64.getDecoder().decode(pin.removePrefix(SPKI_SHA256_PREFIX))

    private fun configurationFailure(
        reason: SelfHostedBlockReason,
        cause: Throwable,
    ): SelfHostedConfigurationException =
        SelfHostedConfigurationException(reason).also { it.initCause(cause) }
}

/** 仅对完全匹配 scheme、host 与有效端口的请求附加 Bearer Token。 */
private class ScopedBearerInterceptor(
    private val origin: HttpUrl,
    token: CharArray,
) : Interceptor {
    private val token = token.copyOf()
    private var closed = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.hasSameOrigin(origin)) return chain.proceed(request)

        val tokenValue = synchronized(token) {
            if (closed || token.isEmpty() || token.all { it == '\u0000' }) {
                throw IOException("Self-hosted credentials are unavailable")
            }
            token.concatToString()
        }
        val authenticatedRequest = request.newBuilder()
            .header(AUTHORIZATION_HEADER, BEARER_PREFIX + tokenValue)
            .build()
        return chain.proceed(authenticatedRequest)
    }

    /** 先阻止后续请求读取 Token，再清空可控副本。 */
    fun close() {
        synchronized(token) {
            closed = true
            token.fill('\u0000')
        }
    }

    /**
     * 与 [close] 共用线性化边界：回调要么在关闭前完整执行，要么在关闭后被永久抑制。
     */
    fun runIfOpen(action: () -> Unit) {
        synchronized(token) {
            if (!closed) action()
        }
    }
}

/** 将运行期 TLS 失败转换为持久安全阻止；不处理超时、HTTP 状态或普通 I/O 错误。 */
private class TlsFailureBlockingInterceptor(
    private val onBlocked: (SelfHostedBlockReason) -> Unit,
    private val runIfOpen: (() -> Unit) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = try {
        chain.proceed(chain.request())
    } catch (exception: IOException) {
        exception.tlsBlockReason()?.let { reason ->
            runIfOpen {
                runCatching { onBlocked(reason) }
            }
        }
        throw exception
    }
}

internal fun Throwable.tlsBlockReason(): SelfHostedBlockReason? {
    var current: Throwable? = this
    val visited = HashSet<Throwable>()
    var foundStructuredTlsFailure = false
    while (current != null && visited.add(current)) {
        when (current) {
            is SelfHostedCertificateException -> return current.reason
            is SSLPeerUnverifiedException,
            is CertificateException,
            is CertPathValidatorException,
            -> foundStructuredTlsFailure = true
        }
        current = current.cause
    }
    return if (foundStructuredTlsFailure) SelfHostedBlockReason.Tls else null
}

private fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

private fun createCloseAction(
    client: OkHttpClient,
    bearerInterceptor: ScopedBearerInterceptor,
): () -> Unit {
    val resourcesClosed = AtomicBoolean(false)
    return {
        // 每个调用都必须等待鉴权边界完成关闭，不能让 CAS 失败的并发 close 提前返回。
        bearerInterceptor.close()
        if (resourcesClosed.compareAndSet(false, true)) {
            // Dispatcher.cancelAll() 不等待在途调用结束，因此不能把它作为线性化边界。
            client.closeResources()
        }
    }
}

private fun OkHttpClient.closeResources() {
    dispatcher.cancelAll()
    connectionPool.evictAll()
    cache?.close()
    dispatcher.executorService.shutdown()
}
