package vip.mystery0.pixel.telo.data.source

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.JsonArray
import vip.mystery0.pixel.telo.data.model.DeveloperSupportConfig
import vip.mystery0.pixel.telo.data.model.SupportChannel
import vip.mystery0.pixel.telo.data.model.SupportChannelId
import java.io.IOException
import java.net.URI

/** 配置只决定受限渠道资源，不接受远程包名、任意 Intent 或界面文案。 */
class SupportConfigParser {
    fun parse(json: String): DeveloperSupportConfig {
        val root = Json.parseToJsonElement(json) as? JsonObject
            ?: throw IOException("Invalid support config root")
        val version = root["schemaVersion"] as? JsonPrimitive
        if (version?.isString != false || version.intOrNull != 1) {
            throw IOException("Unsupported support config version")
        }
        val enabled = root.boolean("enabled") ?: throw IOException("Missing support enabled")
        if (!enabled) return DeveloperSupportConfig(false, emptyList())
        val items = root["channels"] as? JsonArray ?: throw IOException("Missing support channels")
        // 单个坏渠道不影响其他渠道，重复渠道只保留首个有效项。
        val channels = items.take(20).mapNotNull { item ->
            val fields = item as? JsonObject ?: return@mapNotNull null
            if (fields.boolean("enabled") != true) return@mapNotNull null
            val id = SupportChannelId.entries.firstOrNull { it.value == fields.string("id") }
                ?: return@mapNotNull null
            val qr = fields.string("qrCodeUrl")?.takeIf(::isTrustedQrUrl) ?: return@mapNotNull null
            val payment = fields.string("paymentUrl")?.takeIf { isTrustedPaymentUrl(id, it) }
            SupportChannel(id, qr, payment)
        }.distinctBy { it.id }
        if (channels.isEmpty()) throw IOException("No valid support channels")
        return DeveloperSupportConfig(true, channels)
    }

    private fun JsonObject.boolean(key: String): Boolean? =
        (get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        fun isTrustedQrUrl(value: String): Boolean {
            val uri = safeHttpsUri(value) ?: return false
            return uri.host == "pixel.mystery0.app" && uri.rawPath.startsWith("/support/") &&
                !uri.rawPath.contains('%') && uri.normalize().rawPath == uri.rawPath && uri.rawQuery == null
        }

        fun isTrustedPaymentUrl(id: SupportChannelId, value: String): Boolean {
            val uri = safeHttpsUri(value) ?: return false
            return uri.host == when (id) {
                SupportChannelId.ALIPAY -> "qr.alipay.com"
                SupportChannelId.WECHAT -> "payapp.weixin.qq.com"
            }
        }

        private fun safeHttpsUri(value: String): URI? = try {
            if (value.length > 2048) null else URI(value).takeIf {
                it.scheme == "https" && !it.host.isNullOrBlank() && it.rawUserInfo == null &&
                    it.port == -1 && it.rawFragment == null
            }
        } catch (_: Exception) {
            null
        }
    }
}
