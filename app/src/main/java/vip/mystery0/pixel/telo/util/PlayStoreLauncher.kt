package vip.mystery0.pixel.telo.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Debug 版本也评价正式应用，不把本地构建包名拼入商店地址。 */
fun Context.openAppReview(): Boolean {
    val packageId = "vip.mystery0.pixel.telo"
    try {
        startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$packageId".toUri()).apply {
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    } catch (_: Exception) {
        // 未安装或无法打开 Play 商店时，使用网页详情页。
    }
    return try {
        startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageId".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (_: Exception) {
        false
    }
}
