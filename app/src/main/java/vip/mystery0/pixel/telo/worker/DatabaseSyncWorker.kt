package vip.mystery0.pixel.telo.worker

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.repository.SyncRepository
import vip.mystery0.pixel.telo.ui.util.NotificationHelper

class DatabaseSyncWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters), KoinComponent {
    companion object {
        private const val TAG = "DatabaseSyncWorker"
    }

    private val notificationHelper: NotificationHelper by inject()
    private val syncRepository: SyncRepository by inject()

    private val notificationManager: NotificationManager by inject()

    override suspend fun doWork(): Result {
        val initialNotification = notificationHelper.getDownloadNotification(0)

        val foregroundInfo =
            ForegroundInfo(
                NotificationHelper.NOTIFICATION_ID_DOWNLOAD,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )

        try {
            setForeground(foregroundInfo)

            // 1. Get Params from InputData
            val downloadUrl = inputData.getString("downloadUrl") ?: return Result.failure()
            val checksum = inputData.getString("checksum") ?: return Result.failure()
            val sizeBytes = inputData.getLong("sizeBytes", 0L)
            val latestVersion = inputData.getString("latestVersion") ?: "Unknown"

            // 2. Download and Install
            val success = syncRepository.downloadAndInstallWithProgress(
                downloadUrl,
                checksum,
                sizeBytes
            ) { progress ->
                // Update Progress
                val notification = notificationHelper.getDownloadNotification(progress)
                notificationManager.notify(
                    NotificationHelper.NOTIFICATION_ID_DOWNLOAD,
                    notification
                )
            }

            if (success) {
                notificationHelper.showCompleteNotification(
                    latestVersion,
                    formatSize(sizeBytes)
                )
                return Result.success()
            } else {
                return Result.retry()
            }

        } catch (e: Exception) {
            Log.w(TAG, "do work failed", e)
            return Result.failure()
        }
    }

    private fun formatSize(size: Long): String {
        val kb = size / 1024.0
        if (kb < 1024) {
            return "%.2f KB".format(kb)
        }
        val mb = kb / 1024.0
        return "%.2f MB".format(mb)
    }
}
