package vip.mystery0.pixel.telo.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import vip.mystery0.pixel.telo.data.source.SupportConfigParser
import vip.mystery0.pixel.telo.data.model.SupportChannel
import vip.mystery0.pixel.telo.data.model.SupportChannelId

/** 只打开内置的支付应用，不执行配置下发的任意 Intent。 */
fun Context.openSupportApp(channel: SupportChannel): Boolean {
    val packageName = when (channel.id) {
        SupportChannelId.ALIPAY -> "com.eg.android.AlipayGphone"
        SupportChannelId.WECHAT -> "com.tencent.mm"
    }
    val paymentUrl = channel.paymentUrl?.takeIf {
        SupportConfigParser.isTrustedPaymentUrl(channel.id, it)
    }
    if (paymentUrl != null) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, paymentUrl.toUri()).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return true
        } catch (_: Exception) {
            // 支付链接不可处理时，退回应用首页。
        }
    }
    return try {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
