package vip.mystery0.pixel.telo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.entity.FeedbackStatus
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.FeedbackSubmitResult
import vip.mystery0.pixel.telo.data.repository.QueryRepository

/**
 * 处理反馈通知上的“结果准确/结果不准确”动作。
 * 提交前先把通知切换为“提交中”并复查记录状态，避免重复提交；
 * 终态写回 Room 并更新通知，可重试失败保持 PENDING 并提示稍后在记录详情中重试。
 */
class FeedbackActionReceiver : BroadcastReceiver(), KoinComponent {
    companion object {
        private const val TAG = "FeedbackActionReceiver"
        const val ACTION_SUBMIT_FEEDBACK = "vip.mystery0.pixel.telo.action.SUBMIT_QUERY_FEEDBACK"
        const val EXTRA_RECORD_ID = "record_id"
        const val EXTRA_POSITIVE = "positive"

        /** 广播接收器必须在 ANR 窗口内完成，提交请求限制在 8 秒内 */
        private const val SUBMIT_TIMEOUT_MILLIS = 8_000L
    }

    private val blockedCallRepository: BlockedCallRepository by inject()
    private val queryRepository: QueryRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SUBMIT_FEEDBACK) return
        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
        if (recordId < 0) return
        val positive = intent.getBooleanExtra(EXTRA_POSITIVE, true)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                submit(context, recordId, positive)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to handle feedback action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun submit(context: Context, recordId: Long, positive: Boolean) {
        val call = blockedCallRepository.findById(recordId) ?: return
        // 用户可能已在记录详情中提交过，非 PENDING 时直接展示当前状态
        if (call.feedbackStatus != FeedbackStatus.PENDING) {
            QueryFeedbackNotifier.showFeedbackResult(context, call)
            return
        }
        val token = call.feedbackToken
        if (token.isNullOrBlank()) {
            QueryFeedbackNotifier.showFeedbackResult(context, call)
            return
        }

        QueryFeedbackNotifier.showSubmitting(context, call)
        val result = try {
            withTimeout(SUBMIT_TIMEOUT_MILLIS) {
                queryRepository.submitFeedback(token, positive)
            }
        } catch (e: TimeoutCancellationException) {
            FeedbackSubmitResult.RetryableFailure(null)
        }

        val newStatus = when (result) {
            FeedbackSubmitResult.Accepted ->
                if (positive) FeedbackStatus.POSITIVE else FeedbackStatus.NEGATIVE

            FeedbackSubmitResult.AlreadySubmitted -> FeedbackStatus.ALREADY_SUBMITTED
            FeedbackSubmitResult.Expired -> FeedbackStatus.EXPIRED
            FeedbackSubmitResult.Invalid -> FeedbackStatus.INVALID
            is FeedbackSubmitResult.RetryableFailure -> {
                Log.w(TAG, "Feedback submit failed, keeping PENDING: recordId=$recordId")
                QueryFeedbackNotifier.showRetryableFailure(context, call)
                return
            }
        }
        val updated = blockedCallRepository.updateFeedbackStatus(call, newStatus)
        QueryFeedbackNotifier.showFeedbackResult(context, updated)
        Log.i(TAG, "Feedback submitted from notification: recordId=$recordId")
    }
}
