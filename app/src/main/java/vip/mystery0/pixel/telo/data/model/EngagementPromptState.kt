package vip.mystery0.pixel.telo.data.model

/** 仅用于本机决定一次性支持提醒的时机，不包含通话内容或用户身份。 */
data class EngagementPromptState(
    val firstUsedAt: Long = 0,
    val qualifyingSessions: Int = 0,
    val activeDays: Set<Long> = emptySet(),
    val lastQualifiedAt: Long = 0,
    val firstShownAt: Long = 0,
    val dismissed: Boolean = false,
)
