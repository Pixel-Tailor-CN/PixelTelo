package vip.mystery0.pixel.telo.ui.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.MainActivity

class NotificationHelper(private val context: Context) : KoinComponent {

    companion object {
        const val CHANNEL_ID_DOWNLOAD = "download_channel"
        const val NOTIFICATION_ID_DOWNLOAD = 1001
        const val NOTIFICATION_ID_COMPLETE = 1002
    }

    private val notificationManager: NotificationManager by inject()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val name = "Database Download"
        val descriptionText = "Shows progress of database updates"
        val group = NotificationChannelGroup(CHANNEL_ID_DOWNLOAD, name)
        notificationManager.createNotificationChannelGroup(group)
        val channel = NotificationChannel(
            CHANNEL_ID_DOWNLOAD,
            name,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = descriptionText
            setShowBadge(true)
            setGroup(CHANNEL_ID_DOWNLOAD)
            setSound(null, null)
        }

        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(channel)
    }

    fun getDownloadNotification(progress: Int): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Updating Database")
            .setContentText("Downloading... $progress%")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            builder.setRequestPromotedOngoing(true)
        }

        return builder.build()
    }

    fun showCompleteNotification(version: String, size: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_DOWNLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update Complete")
            .setContentText("DB Version: $version • Size: $size")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }
}
