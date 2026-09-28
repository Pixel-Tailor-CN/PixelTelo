package vip.mystery0.pixel.telo.data.query

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 限制粘贴输入的体积；二维码和手动粘贴共用同一解析边界。 */
const val MAX_PAIRING_JSON_LENGTH = 16 * 1024

enum class SelfHostedPairingError {
    TOO_LARGE, INVALID_JSON, MISSING_FIELDS, INVALID_URL, INVALID_TOKEN, INVALID_PIN,
}

sealed interface SelfHostedPairingResult {
    class Failure(val error: SelfHostedPairingError) : SelfHostedPairingResult

    /** 不使用 data class，避免自动生成的 toString 泄露配对材料。调用方使用后必须关闭。 */
    class Success(
        val baseUrl: String,
        val tlsMode: SelfHostedTlsMode,
        val spkiPin: String,
        val token: CharArray,
    ) : SelfHostedPairingResult, AutoCloseable {
        override fun close() = token.fill('\u0000')
    }
}

/**
 * 只解析服务端的配置 JSON，不访问其中的 URL，也不持久化或启用配置。
 * 页面有效期不代表凭据有效期；凭据仍由既有的完整连接验证流程确认。
 * 解析异常可能包含输入片段，只返回稳定分类，不携带原始异常。
 */
fun parseSelfHostedPairingJson(raw: String): SelfHostedPairingResult {
    fun failure(error: SelfHostedPairingError) = SelfHostedPairingResult.Failure(error)
    if (raw.length > MAX_PAIRING_JSON_LENGTH) return failure(SelfHostedPairingError.TOO_LARGE)
    val json = try {
        Json.parseToJsonElement(raw.trim()) as? JsonObject
            ?: return failure(SelfHostedPairingError.INVALID_JSON)
    } catch (_: SerializationException) {
        return failure(SelfHostedPairingError.INVALID_JSON)
    } catch (_: IllegalArgumentException) {
        return failure(SelfHostedPairingError.INVALID_JSON)
    }
    fun stringField(name: String): String? =
        (json[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    val url = stringField("url") ?: return failure(SelfHostedPairingError.MISSING_FIELDS)
    val token = stringField("token") ?: return failure(SelfHostedPairingError.MISSING_FIELDS)
    // 缺省 Pin 与空字符串都使用系统信任；非字符串 Pin 必须报错，不能静默降级。
    val pin = if ("spki_pin" in json) {
        stringField("spki_pin") ?: return failure(SelfHostedPairingError.INVALID_PIN)
    } else {
        ""
    }
    return validateSelfHostedPairingFields(url, token, pin)
}

/** 手动填写与 JSON 导入使用同一字段校验，确认之前不会创建网络 Client。 */
fun validateSelfHostedPairingFields(url: String, token: String, pin: String): SelfHostedPairingResult {
    fun failure(error: SelfHostedPairingError) = SelfHostedPairingResult.Failure(error)
    if (url.length + token.length + pin.length > MAX_PAIRING_JSON_LENGTH) {
        return failure(SelfHostedPairingError.TOO_LARGE)
    }
    if (url.any { it.isISOControl() }) return failure(SelfHostedPairingError.INVALID_URL)
    val normalizedUrl = normalizeSelfHostedBaseUrl(url).getOrNull()
        ?: return failure(SelfHostedPairingError.INVALID_URL)
    if (token.isBlank() || token.any { it.code !in 0x21..0x7e }) {
        return failure(SelfHostedPairingError.INVALID_TOKEN)
    }
    if (pin != pin.trim() || pin.any { it.isISOControl() }) return failure(SelfHostedPairingError.INVALID_PIN)
    val normalizedPin = if (pin.isEmpty()) "" else normalizeSpkiPin(pin).getOrNull()
        ?: return failure(SelfHostedPairingError.INVALID_PIN)
    return SelfHostedPairingResult.Success(
        baseUrl = normalizedUrl.toString(),
        tlsMode = if (normalizedPin.isEmpty()) SelfHostedTlsMode.SYSTEM else SelfHostedTlsMode.SPKI_PIN,
        spkiPin = normalizedPin,
        token = token.toCharArray(),
    )
}
