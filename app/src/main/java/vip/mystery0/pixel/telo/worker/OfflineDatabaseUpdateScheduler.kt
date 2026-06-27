package vip.mystery0.pixel.telo.worker

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object OfflineDatabaseUpdateScheduler {
    const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"
    const val KEY_AUTO_CHECK_UPDATE_INTERVAL_HOURS = "auto_check_update_interval_hours"
    const val KEY_LAST_AUTO_UPDATE_CHECK_AT = "last_auto_update_check_at"
    const val KEY_LAST_NOTIFIED_UPDATE_VERSION = "last_notified_update_version"

    const val DEFAULT_UPDATE_INTERVAL_HOURS = 24
    const val MIN_UPDATE_INTERVAL_HOURS = 1
    const val MAX_UPDATE_INTERVAL_HOURS = 720

    private const val TAG = "OfflineDbUpdateScheduler"
    private const val UNIQUE_WORK_NAME = "offline_database_update_check"

    fun normalizeIntervalHours(hours: Int): Int =
        hours.coerceIn(MIN_UPDATE_INTERVAL_HOURS, MAX_UPDATE_INTERVAL_HOURS)

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun ensureScheduled(context: Context, prefs: SharedPreferences) {
        if (prefs.getBoolean(KEY_AUTO_CHECK_UPDATE, false)) {
            if (!hasNotificationPermission(context)) {
                prefs.edit { putBoolean(KEY_AUTO_CHECK_UPDATE, false) }
                cancel(context)
                Log.i(TAG, "Notification permission missing, auto update check disabled")
                return
            }
            val intervalHours = prefs.getInt(
                KEY_AUTO_CHECK_UPDATE_INTERVAL_HOURS,
                DEFAULT_UPDATE_INTERVAL_HOURS
            )
            enqueue(context, intervalHours, ExistingPeriodicWorkPolicy.KEEP)
        } else {
            cancel(context)
        }
    }

    fun scheduleFromNow(context: Context, intervalHours: Int) {
        enqueue(
            context = context,
            intervalHours = intervalHours,
            policy = ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        Log.i(TAG, "Offline database update check canceled")
    }

    private fun enqueue(
        context: Context,
        intervalHours: Int,
        policy: ExistingPeriodicWorkPolicy,
    ) {
        val safeIntervalHours = normalizeIntervalHours(intervalHours).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<OfflineDatabaseUpdateCheckWorker>(
            safeIntervalHours,
            TimeUnit.HOURS
        )
            // 从开启或修改间隔的时间点开始计算首次检查时间，避免刚开启就立刻联网。
            .setInitialDelay(safeIntervalHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            policy,
            request
        )
        Log.i(TAG, "Offline database update check scheduled every ${safeIntervalHours}h")
    }
}
