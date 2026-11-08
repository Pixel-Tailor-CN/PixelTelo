package vip.mystery0.pixel.telo.data.model

enum class SupportChannelId(val value: String) {
    ALIPAY("alipay"),
    WECHAT("wechat"),
}

data class SupportChannel(
    val id: SupportChannelId,
    val qrCodeUrl: String,
    val paymentUrl: String? = null,
)

data class DeveloperSupportConfig(
    val enabled: Boolean,
    val channels: List<SupportChannel>,
)
