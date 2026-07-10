package vip.mystery0.pixel.telo.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import vip.mystery0.pixel.telo.MainActivity
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.FeedbackStatus
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/**
 * 通话结束后反馈提醒通知的统一入口。
 * 负责“待提醒”标记的读写、通知渠道创建与各状态通知的构建。
 * 反馈提醒不参与来电实时决策，任何失败只记录日志，不影响通话链路。
 */
object QueryFeedbackNotifier {
    private const val TAG = "QueryFeedbackNotifier"
    private const val CHANNEL_ID = "query_feedback"

    /** 待提醒记录 id 与写入时间的持久化 key */
    private const val KEY_PENDING_RECORD_ID = "pending_feedback_record_id"
    private const val KEY_PENDING_MARKED_AT = "pending_feedback_marked_at"

    /** 标记有效窗口：超过该时长的陈旧标记不再提醒 */
    private const val MARKER_VALID_MILLIS = 2 * 60 * 60 * 1000L

    /** 通知 id 偏移，避开其他通知（离线更新使用 1001） */
    private const val NOTIFICATION_ID_OFFSET = 20000L

    /**
     * 来电放行且记录持有反馈 token 时写入待提醒标记。
     * 开关关闭时不写入；后写入的标记覆盖旧标记。
     */
    fun markPendingFeedback(prefs: SharedPreferences, recordId: Long) {
        if (!prefs.getBoolean(SettingViewModel.KEY_FEEDBACK_NOTIFICATION, true)) return
        prefs.edit {
            putLong(KEY_PENDING_RECORD_ID, recordId)
            putLong(KEY_PENDING_MARKED_AT, System.currentTimeMillis())
        }
    }

    /**
     * 读取并清除待提醒标记。
     * 返回窗口期内的记录 id；无标记或标记过期返回 null。
     */
    fun consumePendingFeedback(prefs: SharedPreferences): Long? {
        val recordId = prefs.getLong(KEY_PENDING_RECORD_ID, -1L)
        if (recordId < 0) return null
        val markedAt = prefs.getLong(KEY_PENDING_MARKED_AT, 0L)
        prefs.edit {
            remove(KEY_PENDING_RECORD_ID)
            remove(KEY_PENDING_MARKED_AT)
        }
        val age = System.currentTimeMillis() - markedAt
        if (age !in 0..MARKER_VALID_MILLIS) {
            Log.i(TAG, "Stale feedback marker dropped: age=${age}ms")
            return null
        }
        return recordId
    }

    /** 展示反馈询问通知：查询结果概要 + 数据源 + “结果准确/结果不准确”按钮 */
    fun showFeedbackPrompt(context: Context, call: BlockedCall) {
        val text = promptText(context, call)
        val builder = baseBuilder(context, call)
            .setContentTitle(context.getString(R.string.notification_feedback_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .addAction(
                0,
                context.getString(R.string.action_feedback_positive),
                actionIntent(context, call.id, positive = true)
            )
            .addAction(
                0,
                context.getString(R.string.action_feedback_negative),
                actionIntent(context, call.id, positive = false)
            )
        notify(context, call.id, builder)
    }

    /** 展示“提交中”通知，替换按钮避免重复点击 */
    fun showSubmitting(context: Context, call: BlockedCall) {
        val builder = baseBuilder(context, call)
            .setContentTitle(context.getString(R.string.notification_feedback_title))
            .setContentText(context.getString(R.string.notification_feedback_submitting))
        notify(context, call.id, builder)
    }

    /** 按记录当前反馈状态展示终态通知 */
    fun showFeedbackResult(context: Context, call: BlockedCall) {
        val textResId = when (call.feedbackStatus) {
            FeedbackStatus.POSITIVE -> R.string.feedback_status_positive
            FeedbackStatus.NEGATIVE -> R.string.feedback_status_negative
            FeedbackStatus.ALREADY_SUBMITTED -> R.string.feedback_status_already_submitted
            FeedbackStatus.EXPIRED -> R.string.feedback_status_expired
            FeedbackStatus.INVALID -> R.string.feedback_status_invalid
            else -> {
                cancel(context, call.id)
                return
            }
        }
        val builder = baseBuilder(context, call)
            .setContentTitle(context.getString(R.string.notification_feedback_title))
            .setContentText(context.getString(textResId))
        notify(context, call.id, builder)
    }

    /** 展示可重试失败通知，记录保持 PENDING，可稍后在记录详情中重试 */
    fun showRetryableFailure(context: Context, call: BlockedCall) {
        val builder = baseBuilder(context, call)
            .setContentTitle(context.getString(R.string.notification_feedback_title))
            .setContentText(context.getString(R.string.notification_feedback_failed))
        notify(context, call.id, builder)
    }

    private fun promptText(context: Context, call: BlockedCall): String {
        val source = call.querySource ?: ""
        val label = call.label?.takeIf { it.isNotBlank() }
        return if (label != null) {
            context.getString(
                R.string.notification_feedback_text_marked,
                call.phoneNumber,
                label,
                source
            )
        } else {
            context.getString(
                R.string.notification_feedback_text_not_marked,
                call.phoneNumber,
                source
            )
        }
    }

    private fun baseBuilder(context: Context, call: BlockedCall): NotificationCompat.Builder {
        createChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            call.id.toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    private fun actionIntent(context: Context, recordId: Long, positive: Boolean): PendingIntent {
        val intent = Intent(context, FeedbackActionReceiver::class.java).apply {
            action = FeedbackActionReceiver.ACTION_SUBMIT_FEEDBACK
            putExtra(FeedbackActionReceiver.EXTRA_RECORD_ID, recordId)
            putExtra(FeedbackActionReceiver.EXTRA_POSITIVE, positive)
        }
        // requestCode 用记录 id 与方向区分，保证两个按钮的 PendingIntent 互不覆盖
        return PendingIntent.getBroadcast(
            context,
            (recordId * 2 + if (positive) 0 else 1).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notify(context: Context, recordId: Long, builder: NotificationCompat.Builder) {
        if (!hasNotificationPermission(context)) {
            Log.i(TAG, "Notification permission not granted, skipping feedback notification")
            return
        }
        try {
            NotificationManagerCompat.from(context)
                .notify(notificationId(recordId), builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission changed before notify", e)
        }
    }

    private fun cancel(context: Context, recordId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(recordId))
    }

    private fun notificationId(recordId: Long): Int {
        return (NOTIFICATION_ID_OFFSET + recordId).toInt()
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_feedback_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
