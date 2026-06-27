package vip.mystery0.pixel.telo.worker

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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.MainActivity
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.repository.SyncRepository

class OfflineDatabaseUpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    companion object {
        private const val TAG = "OfflineDbUpdateWorker"
        private const val CHANNEL_ID = "offline_database_updates"
        private const val NOTIFICATION_ID = 1001
    }

    private val syncRepository: SyncRepository by inject()
    private val prefs: SharedPreferences by inject()

    override suspend fun doWork(): Result {
        if (!prefs.getBoolean(OfflineDatabaseUpdateScheduler.KEY_AUTO_CHECK_UPDATE, false)) {
            Log.i(TAG, "Auto update check is disabled")
            return Result.success()
        }

        return try {
            val currentVersion = syncRepository.getCurrentVersion()
            val response = syncRepository.checkUpdate(currentVersion)
            prefs.edit {
                putLong(
                    OfflineDatabaseUpdateScheduler.KEY_LAST_AUTO_UPDATE_CHECK_AT,
                    System.currentTimeMillis()
                )
            }

            if (response.hasUpdate) {
                notifyOncePerVersion(response.latestVersion)
            } else {
                Log.i(TAG, "Offline database is already up to date")
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Auto update check failed", e)
            Result.success()
        }
    }

    private fun notifyOncePerVersion(latestVersion: String) {
        val lastNotifiedVersion = prefs.getString(
            OfflineDatabaseUpdateScheduler.KEY_LAST_NOTIFIED_UPDATE_VERSION,
            ""
        )
        if (lastNotifiedVersion == latestVersion) {
            Log.i(TAG, "Update notification already shown for version $latestVersion")
            return
        }

        if (showUpdateNotification(latestVersion)) {
            prefs.edit {
                putString(
                    OfflineDatabaseUpdateScheduler.KEY_LAST_NOTIFIED_UPDATE_VERSION,
                    latestVersion
                )
            }
        }
    }

    private fun showUpdateNotification(latestVersion: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Notification permission not granted, skipping update notification")
            return false
        }

        createNotificationChannel()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = applicationContext.getString(
            R.string.notification_offline_update_text,
            latestVersion
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(applicationContext.getString(R.string.notification_offline_update_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            NotificationManagerCompat.from(applicationContext).notify(
                NOTIFICATION_ID,
                notification
            )
            Log.i(TAG, "Update notification shown for version $latestVersion")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission changed before notify", e)
            false
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.notification_offline_update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
