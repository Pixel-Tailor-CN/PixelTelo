package vip.mystery0.pixel.telo.data.preferences

import android.content.SharedPreferences
import androidx.core.content.edit

/** 已识别为应拦截号码时的常规处置；强制规则和重复来电策略由决策层处理。 */
enum class CallHandlingStrategy {
    NOTIFY,
    SILENCE,
    BLOCK,
}

/** 设置页和来电筛查共用同一配置读取规则，避免旧设置迁移后两侧行为不一致。 */
class CallHandlingPreferences(private val preferences: SharedPreferences) {
    companion object {
        const val KEY_CALL_HANDLING_STRATEGY = "call_handling_strategy"
        private const val LEGACY_KEY_NOTIFY_ONLY = "notify_only"
    }

    fun readStrategy(): CallHandlingStrategy {
        val stored = preferences.getString(KEY_CALL_HANDLING_STRATEGY, null)
        CallHandlingStrategy.entries.firstOrNull { it.name == stored }?.let { return it }
        // 未配置新策略时保留旧行为；读取不落盘，避免在来电响应路径执行迁移写入。
        return if (preferences.getBoolean(LEGACY_KEY_NOTIFY_ONLY, true)) {
            CallHandlingStrategy.NOTIFY
        } else {
            CallHandlingStrategy.BLOCK
        }
    }

    fun setStrategy(strategy: CallHandlingStrategy) {
        preferences.edit {
            putString(KEY_CALL_HANDLING_STRATEGY, strategy.name)
            remove(LEGACY_KEY_NOTIFY_ONLY)
        }
    }
}
