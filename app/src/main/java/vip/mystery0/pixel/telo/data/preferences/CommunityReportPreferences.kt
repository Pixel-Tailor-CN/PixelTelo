package vip.mystery0.pixel.telo.data.preferences

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 独立社区拦截开关；不改变自动联网或常规处理策略。 */
class CommunityReportPreferences(private val preferences: SharedPreferences) {
    private val state = MutableStateFlow(preferences.getBoolean(KEY, false))
    val blockEnabled: StateFlow<Boolean> = state.asStateFlow()

    fun setBlockEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY, enabled) }
        state.value = enabled
    }

    companion object { const val KEY = "block_community_reports" }
}
