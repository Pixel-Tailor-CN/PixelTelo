package vip.mystery0.pixel.telo.service

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import androidx.annotation.MainThread
import vip.mystery0.pixel.telo.receiver.QueryFeedbackNotifier

/**
 * 进程内关联筛查回调、通话结束和迟到的后处理，只保存代次与记录 ID。
 *
 * PHONE_STATE 没有筛查请求 ID，因此沿用原有 IDLE 结束当前通话窗口的语义；
 * 不把旧窗口的落库结果写入下一次通话的待提醒槽位。
 */
object CallPostProcessingCoordinator {
    private const val MAX_TRACKED_SESSIONS = 64
    private var nextGeneration = 0L
    private var currentSession: Session? = null
    private val activeSessions = linkedSetOf<Session>()

    class Session internal constructor(val generation: Long) {
        internal var ended = false
        internal var feedbackRegistered = false
        internal var feedbackRecordId: Long? = null
        internal var feedbackMarkedAtElapsedMs = 0L
    }

    /** 回调进入时登记，完全不读取 DI、偏好或数据库。 */
    @MainThread
    @Synchronized
    fun beginCall(): Session {
        val session = Session(++nextGeneration)
        activeSessions.add(session)
        currentSession = session
        // 未授权 PHONE_STATE 时也不能无限保留已过时的通话窗口。
        while (activeSessions.size > MAX_TRACKED_SESSIONS) {
            val oldest = activeSessions.first()
            oldest.ended = true
            activeSessions.remove(oldest)
        }
        return session
    }

    /** 仅最新且尚未结束的通话可展示 Overlay；旧异步任务不得移除或覆盖新视图。 */
    @Synchronized
    fun mayShowOverlay(session: Session): Boolean =
        currentSession?.generation == session.generation && !session.ended

    /**
     * 在与 IDLE 相同的短锁内关联记录与通话。
     * true 表示所属通话已经结束，调用方应立即兑现，绝不再写全局待提醒标记。
     */
    fun registerFeedback(
        session: Session,
        context: Context,
        prefs: SharedPreferences,
        recordId: Long,
    ): Boolean {
        // 权限检查可能经过 Binder，必须在代次锁外执行，不能拖住下一通筛查登记。
        val enabled = QueryFeedbackNotifier.isFeedbackEnabled(context, prefs)
        return synchronized(this) {
            if (session.feedbackRegistered) return@synchronized false
            session.feedbackRegistered = true
            if (!enabled) return@synchronized false
            if (session.ended) return@synchronized true
            session.feedbackRecordId = recordId
            session.feedbackMarkedAtElapsedMs = SystemClock.elapsedRealtime()
            QueryFeedbackNotifier.markPendingFeedback(prefs, recordId)
            false
        }
    }

    /** 结束事件先封闭窗口，再取出记录；迟到落库因此只能走立即兑现路径。 */
    @MainThread
    fun endCalls(context: Context, prefs: SharedPreferences): List<Long> {
        val records = synchronized(this) {
            val ids = linkedSetOf<Long>()
            activeSessions.forEach { session ->
                session.ended = true
                val age = SystemClock.elapsedRealtime() - session.feedbackMarkedAtElapsedMs
                if (age in 0..QueryFeedbackNotifier.MARKER_VALID_MILLIS) {
                    session.feedbackRecordId?.let(ids::add)
                }
                session.feedbackRecordId = null
            }
            activeSessions.clear()
            currentSession = null
            ids
        }
        // 与 beginCall 同在主线程；窗口已封闭，旧落库任务此后只会走立即兑现路径。
        // 权限和持久标记读取位于锁外，保留进程重建及旧版本标记兼容。
        QueryFeedbackNotifier.consumePendingFeedback(context, prefs)?.let(records::add)
        return records.toList()
    }
}
