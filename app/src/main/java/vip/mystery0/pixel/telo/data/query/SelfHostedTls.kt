package vip.mystery0.pixel.telo.data.query

import java.net.Socket
import java.security.MessageDigest
import java.security.Principal
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSessionContext
import javax.net.ssl.X509ExtendedTrustManager
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

private const val HTTPS_SCHEME = "https"
private const val SPKI_SHA256_PREFIX = "sha256/"
private const val SHA256_SIZE_BYTES = 32

/**
 * 将用户输入规范化为唯一的自建服务根 URL。
 *
 * 该边界只接受绝对 HTTPS URL，并拒绝 userinfo、业务子路径、query 与 fragment。
 * 返回的 [HttpUrl] 始终包含根路径 `/`，可直接作为 Retrofit Base URL。
 */
fun normalizeSelfHostedBaseUrl(raw: String): Result<HttpUrl> = runCatching {
    val candidate = raw.trim()
    require(candidate.startsWith("$HTTPS_SCHEME://", ignoreCase = true)) {
        "Self-hosted URL must be an absolute HTTPS URL"
    }
    // OkHttp 会丢弃空 userinfo，因此同时检查原始分隔符，拒绝 https://@host/。
    require('@' !in candidate) { "Self-hosted URL must not contain userinfo" }

    val url = requireNotNull(candidate.toHttpUrlOrNull()) {
        "Invalid self-hosted URL"
    }
    require(url.scheme == HTTPS_SCHEME) { "Self-hosted URL must use HTTPS" }
    require(url.username.isEmpty() && url.password.isEmpty()) {
        "Self-hosted URL must not contain userinfo"
    }
    require(url.encodedPath == "/") { "Self-hosted URL must use the root path" }
    require(url.encodedQuery == null) { "Self-hosted URL must not contain a query" }
    require(url.encodedFragment == null) { "Self-hosted URL must not contain a fragment" }
    url
}

/**
 * 校验并规范化叶子证书 SPKI SHA-256 Pin。
 *
 * 仅接受 `sha256/<Base64>`，Base64 解码结果必须正好为 32 bytes；返回值使用标准、
 * 带 padding 且无换行的 Base64，避免同一 Pin 出现多种持久化形式。
 */
fun normalizeSpkiPin(raw: String): Result<String> = runCatching {
    val candidate = raw.trim()
    require(candidate.startsWith(SPKI_SHA256_PREFIX)) {
        "SPKI pin must use SHA-256"
    }
    val encodedDigest = candidate.removePrefix(SPKI_SHA256_PREFIX)
    require(encodedDigest.isNotEmpty()) { "SPKI pin digest is missing" }
    val digest = Base64.getDecoder().decode(encodedDigest)
    require(digest.size == SHA256_SIZE_BYTES) {
        "SPKI pin digest must contain 32 bytes"
    }
    SPKI_SHA256_PREFIX + Base64.getEncoder().encodeToString(digest)
}

/** 自建证书失败的稳定分类；异常文本不得包含 Host、证书或 Pin。 */
internal class SelfHostedCertificateException(
    val reason: SelfHostedBlockReason,
    cause: Throwable? = null,
) : CertificateException("Self-hosted certificate validation failed", cause)

/**
 * 只信任当前目标 Host 和当前叶子 SPKI Pin 的 TrustManager。
 *
 * 自签名模式不读取或修改系统 Trust Store，也不会接受同一链中的其他证书 Pin。
 */
internal class SelfHostedPinnedTrustManager(
    private val targetHost: String,
    expectedSpkiSha256: ByteArray,
    private val hostnameVerifier: HostnameVerifier = STANDARD_OKHTTP_HOSTNAME_VERIFIER,
) : X509ExtendedTrustManager() {
    private val expectedSpkiSha256 = expectedSpkiSha256.copyOf()

    init {
        require(this.expectedSpkiSha256.size == SHA256_SIZE_BYTES) {
            "SPKI pin digest must contain 32 bytes"
        }
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        validateServer(chain)
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) {
        validateServer(chain)
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) {
        validateServer(chain)
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        throw CertificateException("Client certificate validation is unsupported")
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        socket: Socket?,
    ) {
        throw CertificateException("Client certificate validation is unsupported")
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
        engine: SSLEngine?,
    ) {
        throw CertificateException("Client certificate validation is unsupported")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    /** 仅在 Client 构造失败且尚未暴露任何调用时清除 Pin 副本。 */
    fun clearPin() {
        expectedSpkiSha256.fill(0)
    }

    private fun validateServer(chain: Array<out X509Certificate>?) {
        val certificateChain = chain?.takeIf { it.isNotEmpty() }
            ?: throw SelfHostedCertificateException(SelfHostedBlockReason.Tls)
        val leaf = certificateChain.first()

        try {
            leaf.checkValidity()
        } catch (exception: CertificateException) {
            throw SelfHostedCertificateException(SelfHostedBlockReason.Tls, exception)
        }

        val session = CertificateChainSslSession(targetHost, certificateChain)
        if (!hostnameVerifier.verify(targetHost, session)) {
            throw SelfHostedCertificateException(SelfHostedBlockReason.Tls)
        }

        val actualSpkiSha256 = MessageDigest.getInstance("SHA-256")
            .digest(leaf.publicKey.encoded)
        try {
            if (!MessageDigest.isEqual(expectedSpkiSha256, actualSpkiSha256)) {
                throw SelfHostedCertificateException(SelfHostedBlockReason.SpkiPin)
            }
        } finally {
            actualSpkiSha256.fill(0)
        }
    }
}

/**
 * 取得 OkHttp 5 默认 HostnameVerifier 的稳定引用。
 *
 * 临时 Client 不发起请求，因此不会创建 Dispatcher 线程或连接；这里只复用 OkHttp 官方
 * DNS/IP SAN 与 wildcard 规则，避免在应用侧重新实现主机名匹配。
 */
private val STANDARD_OKHTTP_HOSTNAME_VERIFIER: HostnameVerifier =
    OkHttpClient.Builder().build().hostnameVerifier

/** 仅向 OkHttp HostnameVerifier 提供目标 Host 和服务端证书链。 */
@Suppress("DEPRECATION")
private class CertificateChainSslSession(
    private val targetHost: String,
    chain: Array<out X509Certificate>,
) : SSLSession {
    private val peerCertificateChain: Array<Certificate> =
        Array(chain.size) { index -> chain[index] }

    override fun getId(): ByteArray = ByteArray(0)

    override fun getSessionContext(): SSLSessionContext? = null

    override fun getCreationTime(): Long = 0L

    override fun getLastAccessedTime(): Long = 0L

    override fun invalidate() = Unit

    override fun isValid(): Boolean = true

    override fun putValue(name: String?, value: Any?) = Unit

    override fun getValue(name: String?): Any? = null

    override fun removeValue(name: String?) = Unit

    override fun getValueNames(): Array<String> = emptyArray()

    override fun getPeerCertificates(): Array<Certificate> = peerCertificateChain.copyOf()

    override fun getLocalCertificates(): Array<Certificate>? = null

    override fun getPeerCertificateChain(): Array<javax.security.cert.X509Certificate> {
        throw SSLPeerUnverifiedException("Legacy peer certificate chain is unavailable")
    }

    override fun getPeerPrincipal(): Principal =
        (peerCertificateChain.first() as X509Certificate).subjectX500Principal

    override fun getLocalPrincipal(): Principal? = null

    override fun getCipherSuite(): String = "SSL_NULL_WITH_NULL_NULL"

    override fun getProtocol(): String = "NONE"

    override fun getPeerHost(): String = targetHost

    override fun getPeerPort(): Int = -1

    override fun getPacketBufferSize(): Int = 0

    override fun getApplicationBufferSize(): Int = 0
}
