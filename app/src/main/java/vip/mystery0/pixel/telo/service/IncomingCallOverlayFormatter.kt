package vip.mystery0.pixel.telo.service

import android.content.Context
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.remote.PhoneLocationInfo

data class IncomingCallOverlayContent(
    val phoneNumber: String,
    val locationText: String,
    val localLabelText: String?,
    val sourceLabelText: String?,
)

object IncomingCallOverlayFormatter {
    fun formatLocation(
        context: Context,
        info: PhoneLocationInfo?,
        isNetworkTimeout: Boolean
    ): String {
        if (isNetworkTimeout) return context.getString(R.string.overlay_location_timeout)

        val region = listOfNotNull(
            info?.province?.trim()?.takeIf { it.isNotEmpty() },
            info?.city?.trim()?.takeIf { it.isNotEmpty() }
        ).joinToString(" ")

        return region.ifEmpty { context.getString(R.string.overlay_unknown_location) }
    }

    /** 分别整理本地标签与数据源标签，trim 后为空则丢弃，不拼接。 */
    fun buildContent(
        phoneNumber: String,
        locationText: String,
        localLabel: String?,
        sourceLabel: String?,
    ): IncomingCallOverlayContent {
        return IncomingCallOverlayContent(
            phoneNumber = phoneNumber.trim().ifEmpty { phoneNumber },
            locationText = locationText.trim().ifEmpty { locationText },
            localLabelText = localLabel?.trim()?.takeIf { it.isNotEmpty() },
            sourceLabelText = sourceLabel?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}
