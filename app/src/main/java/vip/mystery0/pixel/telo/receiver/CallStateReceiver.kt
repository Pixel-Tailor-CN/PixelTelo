package vip.mystery0.pixel.telo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import android.telephony.SubscriptionManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.service.IncomingCallOverlay
import vip.mystery0.pixel.telo.service.CallPostProcessingCoordinator

/**
 * 监听通话状态变化。
 * 通话结束（回到 IDLE）时先移除来电归属地悬浮窗，再处理查询结果反馈提醒。
 * 已落库的记录在 IDLE 时兑现反馈；晚于 IDLE 才落库的记录由同一通话代次立即兑现。
 * 需要 READ_PHONE_STATE 权限；未授权时收不到广播，功能自动失效，不影响拦截链路。
 */
class CallStateReceiver : BroadcastReceiver(), KoinComponent {
    companion object {
        private const val TAG = "CallStateReceiver"
    }

    private val prefs: SharedPreferences by inject()
    private val blockedCallRepository: BlockedCallRepository by inject()
    private val incomingCallOverlay: IncomingCallOverlay by inject()
    private val callStateVibrationController: CallStateVibrationController by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val subscriptionId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )
        runCatching {
            callStateVibrationController.onPhoneStateChanged(state, subscriptionId)
        }.onFailure {
            Log.w(TAG, "Failed to handle call state vibration", it)
        }
        if (state != TelephonyManager.EXTRA_STATE_IDLE) return

        // 先封闭通话代次，确保稍后才落库的任务立即兑现且不能再添加 Overlay。
        val recordIds = CallPostProcessingCoordinator.endCalls(context, prefs)
        incomingCallOverlay.hideEndedCall()
        if (recordIds.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                recordIds.forEach { recordId ->
                    blockedCallRepository.findById(recordId)?.let { call ->
                        QueryFeedbackNotifier.showFeedbackPromptIfEligible(context, prefs, call)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to show feedback prompt", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
