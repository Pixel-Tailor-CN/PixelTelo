package vip.mystery0.pixel.telo.service

import vip.mystery0.pixel.telo.data.remote.PhoneLocationInfo

object IncomingCallOverlayFormatter {
    private const val UNKNOWN_LOCATION = "未知归属地"
    private const val LOCATION_TIMEOUT = "归属地查询超时"

    fun formatLocation(info: PhoneLocationInfo?, isNetworkTimeout: Boolean): String {
        if (isNetworkTimeout) return LOCATION_TIMEOUT

        val region = listOfNotNull(
            info?.province?.trim()?.takeIf { it.isNotEmpty() },
            info?.city?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" ")
        val cardType = info?.cardType?.trim()?.takeIf { it.isNotEmpty() }

        return when {
            region.isNotEmpty() && cardType != null -> "$region · $cardType"
            region.isNotEmpty() -> region
            cardType != null -> cardType
            else -> UNKNOWN_LOCATION
        }
    }
}
