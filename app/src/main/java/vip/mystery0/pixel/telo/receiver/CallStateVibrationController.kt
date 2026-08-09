package vip.mystery0.pixel.telo.receiver

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/**
 * 处理可可靠识别的来电状态转换，并在接通、挂断时提供短促震动反馈。
 *
 * 普通第三方应用无法区分去电拨号与对方接通，因此只有先观察到 [TelephonyManager.EXTRA_STATE_RINGING]
 * 的来电才进入状态机，避免把去电误判为已经接通。
 */
class CallStateVibrationController(
    context: Context,
    private val prefs: SharedPreferences,
) {
    companion object {
        private const val TAG = "CallStateVibration"
        private const val KEY_CALL_STATE_PREFIX = "call_state_vibration_transient_state_"
        private const val KEY_WALL_TIME_PREFIX = "call_state_vibration_wall_time_"
        private const val KEY_ELAPSED_TIME_PREFIX = "call_state_vibration_elapsed_time_"
        private const val STATE_WAITING_FOR_ANSWER = "waiting_for_answer"
        private const val STATE_ANSWERED = "answered"
        private const val MAX_STATE_AGE_MILLIS = 12L * 60 * 60 * 1_000
        private const val MAX_CLOCK_DRIFT_MILLIS = 5L * 60 * 1_000

        /** 关闭功能时清除未完成的来电状态，防止再次开启后消费旧事件。 */
        fun clearState(prefs: SharedPreferences) {
            val transientKeys = prefs.all.keys.filter { key ->
                key.startsWith(KEY_CALL_STATE_PREFIX) ||
                    key.startsWith(KEY_WALL_TIME_PREFIX) ||
                    key.startsWith(KEY_ELAPSED_TIME_PREFIX)
            }
            prefs.edit {
                transientKeys.forEach(::remove)
            }
        }

        /** 应用启动时校验运行时权限，撤权后立即关闭功能并清除瞬时状态。 */
        fun normalizePreference(context: Context, prefs: SharedPreferences) {
            val enabled = prefs.getBoolean(SettingViewModel.KEY_CALL_STATE_VIBRATION, false)
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            if (enabled && !permissionGranted) {
                prefs.edit { putBoolean(SettingViewModel.KEY_CALL_STATE_VIBRATION, false) }
                clearState(prefs)
            }
        }
    }

    private val appContext = context.applicationContext

    fun onPhoneStateChanged(state: String?, subscriptionId: Int) {
        if (!prefs.getBoolean(SettingViewModel.KEY_CALL_STATE_VIBRATION, false)) {
            clearState(prefs)
            return
        }

        val currentState = readValidState(subscriptionId)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (currentState == null) {
                    saveState(subscriptionId, STATE_WAITING_FOR_ANSWER)
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (currentState == STATE_WAITING_FOR_ANSWER) {
                    saveState(subscriptionId, STATE_ANSWERED)
                    vibrate()
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                clearState(subscriptionId)
                if (currentState == STATE_ANSWERED) {
                    vibrate()
                }
            }
        }
    }

    /**
     * 同时校验 wall clock 与 elapsed realtime，过期、跨重启或系统时间明显跳变时丢弃旧状态。
     */
    private fun readValidState(subscriptionId: Int): String? {
        val suffix = subscriptionId.toString()
        val state = prefs.getString(KEY_CALL_STATE_PREFIX + suffix, null) ?: return null
        val savedWallTime = prefs.getLong(KEY_WALL_TIME_PREFIX + suffix, -1L)
        val savedElapsedTime = prefs.getLong(KEY_ELAPSED_TIME_PREFIX + suffix, -1L)
        val wallDelta = System.currentTimeMillis() - savedWallTime
        val elapsedDelta = SystemClock.elapsedRealtime() - savedElapsedTime
        val maxAge = if (state == STATE_WAITING_FOR_ANSWER) {
            MAX_STATE_AGE_MILLIS
        } else {
            Long.MAX_VALUE
        }
        val valid = savedWallTime >= 0L &&
            savedElapsedTime >= 0L &&
            wallDelta in 0..maxAge &&
            elapsedDelta in 0..maxAge &&
            kotlin.math.abs(wallDelta - elapsedDelta) <= MAX_CLOCK_DRIFT_MILLIS
        if (!valid) {
            clearState(subscriptionId)
            return null
        }
        return state
    }

    private fun saveState(subscriptionId: Int, state: String) {
        val suffix = subscriptionId.toString()
        prefs.edit {
            putString(KEY_CALL_STATE_PREFIX + suffix, state)
            putLong(KEY_WALL_TIME_PREFIX + suffix, System.currentTimeMillis())
            putLong(KEY_ELAPSED_TIME_PREFIX + suffix, SystemClock.elapsedRealtime())
        }
    }

    private fun clearState(subscriptionId: Int) {
        val suffix = subscriptionId.toString()
        prefs.edit {
            remove(KEY_CALL_STATE_PREFIX + suffix)
            remove(KEY_WALL_TIME_PREFIX + suffix)
            remove(KEY_ELAPSED_TIME_PREFIX + suffix)
        }
    }

    private fun vibrate() {
        runCatching {
            val vibrator = getVibrator()
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            }
        }.onFailure {
            Log.w(TAG, "Failed to vibrate for call state change", it)
        }
    }

    @Suppress("DEPRECATION")
    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
