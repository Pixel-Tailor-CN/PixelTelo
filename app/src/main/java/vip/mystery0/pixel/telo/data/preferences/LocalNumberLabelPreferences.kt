package vip.mystery0.pixel.telo.data.preferences

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程级本地号码标签显示开关。
 *
 * 新安装默认关闭；键只声明在此处，不在 SettingViewModel 中重复定义。
 */
class LocalNumberLabelPreferences(private val preferences: SharedPreferences) {
    companion object {
        const val KEY_SHOW_LOCAL_NUMBER_LABELS = "show_local_number_labels"
    }

    private val _enabled = MutableStateFlow(
        preferences.getBoolean(KEY_SHOW_LOCAL_NUMBER_LABELS, false)
    )
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SHOW_LOCAL_NUMBER_LABELS, enabled) }
        _enabled.value = enabled
    }
}
