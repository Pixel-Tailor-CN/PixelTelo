package vip.mystery0.pixel.telo.smartspacer

import android.content.Context
import android.util.Log
import com.kieronquinn.app.smartspacer.sdk.provider.SmartspacerComplicationProvider

private const val TAG = "SmartspacerIntegration"

/**
 * Smartspacer 集成入口：数据变化后调用 [notifyChanged] 推送刷新。
 * 用户未安装 Smartspacer 时调用会失败，静默跳过即可。
 */
object SmartspacerIntegration {
    fun notifyChanged(context: Context) {
        runCatching {
            SmartspacerComplicationProvider.notifyChange(
                context,
                SilentInterceptComplicationProvider::class.java
            )
        }.onFailure {
            Log.d(TAG, "complication notify skipped error=${it.javaClass.simpleName}")
        }
    }
}
