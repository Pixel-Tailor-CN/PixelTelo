package vip.mystery0.pixel.telo.data.preferences

import android.content.SharedPreferences
import androidx.core.content.edit

/** 已识别为应拦截号码时的常规处置；强制规则和重复来电策略由决策层处理。 */
enum class CallHandlingStrategy {
    NOTIFY,
    SILENCE,
    BLOCK,
}

/** 陌生来电模式使用独立配置；代次隔离切换模式或修改策略前的重复来电记录。 */
data class StrangerCallPolicy(
    val firstCall: CallHandlingStrategy,
    val repeatCall: CallHandlingStrategy,
    val windowMinutes: Int,
    val generation: Long,
)

/** 设置页和来电筛查共用同一配置读取规则，避免旧设置迁移后两侧行为不一致。 */
class CallHandlingPreferences(private val preferences: SharedPreferences) {
    companion object {
        const val KEY_CALL_HANDLING_STRATEGY = "call_handling_strategy"
        private const val KEY_STRANGER_ENABLED = "stranger_calls_enabled"
        private const val KEY_STRANGER_FIRST = "stranger_first_call_strategy"
        private const val KEY_STRANGER_REPEAT = "stranger_repeat_call_strategy"
        private const val KEY_STRANGER_WINDOW = "stranger_repeat_call_window_minutes"
        private const val KEY_STRANGER_GENERATION = "stranger_call_policy_generation"
        private const val LEGACY_KEY_NOTIFY_ONLY = "notify_only"
    }

    fun strangerEnabled(): Boolean = preferences.getBoolean(KEY_STRANGER_ENABLED, false)

    fun readStrangerPolicy(): StrangerCallPolicy = StrangerCallPolicy(
        firstCall = readStrangerStrategy(KEY_STRANGER_FIRST, CallHandlingStrategy.BLOCK),
        repeatCall = readStrangerStrategy(KEY_STRANGER_REPEAT, CallHandlingStrategy.NOTIFY),
        windowMinutes = preferences.getInt(KEY_STRANGER_WINDOW, 3).coerceIn(1, 30),
        generation = preferences.getLong(KEY_STRANGER_GENERATION, 0),
    )

    private fun readStrangerStrategy(key: String, default: CallHandlingStrategy): CallHandlingStrategy =
        CallHandlingStrategy.entries.firstOrNull { it.name == preferences.getString(key, null) } ?: default

    fun setStrangerEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_STRANGER_ENABLED, enabled)
            putLong(KEY_STRANGER_GENERATION, preferences.getLong(KEY_STRANGER_GENERATION, 0) + 1)
        }
    }

    fun setStrangerPolicy(policy: StrangerCallPolicy) {
        preferences.edit {
            putString(KEY_STRANGER_FIRST, policy.firstCall.name)
            putString(KEY_STRANGER_REPEAT, policy.repeatCall.name)
            putInt(KEY_STRANGER_WINDOW, policy.windowMinutes.coerceIn(1, 30))
            putLong(KEY_STRANGER_GENERATION, preferences.getLong(KEY_STRANGER_GENERATION, 0) + 1)
        }
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
