package vip.mystery0.pixel.telo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.entity.FeedbackStatus
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository

/**
 * 监听通话状态变化。
 * 来电筛查阶段写入的“待反馈提醒”标记，在通话结束（回到 IDLE）时兑现为反馈询问通知。
 * 需要 READ_PHONE_STATE 权限；未授权时收不到广播，功能自动失效，不影响拦截链路。
 */
class CallStateReceiver : BroadcastReceiver(), KoinComponent {
    companion object {
        private const val TAG = "CallStateReceiver"
    }

    private val prefs: SharedPreferences by inject()
    private val blockedCallRepository: BlockedCallRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state != TelephonyManager.EXTRA_STATE_IDLE) return

        // 读取即清除标记，重复到达的 IDLE 广播不会二次提醒
        val recordId = QueryFeedbackNotifier.consumePendingFeedback(context, prefs) ?: return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val call = blockedCallRepository.findById(recordId)
                if (call != null && call.feedbackStatus == FeedbackStatus.PENDING) {
                    QueryFeedbackNotifier.showFeedbackPrompt(context, call)
                    Log.i(TAG, "Feedback prompt shown: recordId=$recordId")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to show feedback prompt", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
