package vip.mystery0.pixel.telo.data.preferences

import vip.mystery0.pixel.telo.data.model.EngagementPromptState

/** 首次使用门槛、有效会话间隔与提醒有效期均在本机判断。 */
object EngagementPromptPolicy {
    const val MIN_SESSION_MILLIS = 10_000L
    private const val SESSION_INTERVAL_MILLIS = 30 * 60 * 1000L
    private const val SEVEN_DAYS_MILLIS = 7 * 24 * 60 * 60 * 1000L
    private const val REQUIRED_SESSIONS = 5
    private const val REQUIRED_ACTIVE_DAYS = 3

    fun recordSession(
        state: EngagementPromptState,
        nowMillis: Long,
        epochDay: Long,
    ): EngagementPromptState {
        if (state.dismissed || nowMillis <= 0) return state
        // 达到数量门槛后不再记录后续使用，只等待时间门槛和一次性展示决定。
        if (state.qualifyingSessions >= REQUIRED_SESSIONS && state.activeDays.size >= REQUIRED_ACTIVE_DAYS) return state
        val initialized = if (state.firstUsedAt == 0L) state.copy(firstUsedAt = nowMillis) else state
        if (nowMillis < initialized.firstUsedAt || nowMillis < initialized.lastQualifiedAt ||
            nowMillis < initialized.firstShownAt) return initialized
        if (initialized.lastQualifiedAt > 0 &&
            nowMillis - initialized.lastQualifiedAt < SESSION_INTERVAL_MILLIS) return initialized
        return initialized.copy(
            qualifyingSessions = (initialized.qualifyingSessions.coerceIn(0, REQUIRED_SESSIONS) + 1).coerceAtMost(REQUIRED_SESSIONS),
            activeDays = (initialized.activeDays + epochDay).sorted().take(REQUIRED_ACTIVE_DAYS).toSet(),
            lastQualifiedAt = nowMillis,
        )
    }

    fun canShow(state: EngagementPromptState, nowMillis: Long): Boolean {
        if (state.dismissed || state.firstUsedAt <= 0 || nowMillis < state.firstUsedAt ||
            nowMillis < state.lastQualifiedAt || nowMillis < state.firstShownAt) return false
        if (nowMillis - state.firstUsedAt < SEVEN_DAYS_MILLIS ||
            state.qualifyingSessions < REQUIRED_SESSIONS || state.activeDays.size < REQUIRED_ACTIVE_DAYS) return false
        return state.firstShownAt == 0L || nowMillis - state.firstShownAt < SEVEN_DAYS_MILLIS
    }

    fun markShown(state: EngagementPromptState, nowMillis: Long): EngagementPromptState =
        if (state.firstShownAt == 0L && canShow(state, nowMillis)) state.copy(firstShownAt = nowMillis) else state
}
