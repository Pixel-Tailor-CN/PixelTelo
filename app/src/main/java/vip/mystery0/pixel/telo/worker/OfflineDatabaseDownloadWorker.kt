package vip.mystery0.pixel.telo.worker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.repository.SyncRepository

/** 普通持久化下载任务：手动下载或显式授权的自动更新，不申请额外前台服务权限。 */
class OfflineDatabaseDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val repository: SyncRepository by inject()
    private val prefs: SharedPreferences by inject()

    override suspend fun doWork(): Result {
        val url = inputData.getString(OfflineDatabaseDownloadScheduler.KEY_DOWNLOAD_URL) ?: return Result.failure()
        val checksum = inputData.getString(OfflineDatabaseDownloadScheduler.KEY_CHECKSUM) ?: return Result.failure()
        val version = inputData.getString(OfflineDatabaseDownloadScheduler.KEY_VERSION) ?: return Result.failure()
        return try {
            if (inputData.getBoolean(OfflineDatabaseDownloadScheduler.KEY_AUTOMATIC, false)) {
                // 排队期间可能关闭开关或由手动更新安装新库；开始下载前再次核对。
                val currentVersion = repository.getCurrentVersion()
                if (!OfflineDatabaseUpdateScheduler.isAutomaticDownloadEnabled(prefs) ||
                    (currentVersion == version && repository.isDatabaseReady.value)
                ) {
                    Log.i("OfflineDbDownloadWorker", "Automatic database download skipped")
                    return Result.success(workDataOf(OfflineDatabaseDownloadScheduler.KEY_SKIPPED to true))
                }
            }
            setProgress(workDataOf(OfflineDatabaseDownloadScheduler.KEY_PROGRESS to 0))
            val installed = repository.downloadAndInstallWithProgress(
                downloadUrl = url,
                expectedChecksum = checksum,
                expectedSize = inputData.getLong(OfflineDatabaseDownloadScheduler.KEY_SIZE_BYTES, 0),
                expectedVersion = version,
                expectedRowCount = inputData.getLong(OfflineDatabaseDownloadScheduler.KEY_ROW_COUNT, 0),
                onStage = { stage ->
                    setProgress(workDataOf(
                        OfflineDatabaseDownloadScheduler.KEY_STAGE to stage,
                        OfflineDatabaseDownloadScheduler.KEY_PROGRESS to 99,
                    ))
                },
            ) { progress ->
                setProgressAsync(workDataOf(OfflineDatabaseDownloadScheduler.KEY_PROGRESS to progress))
            }
            if (installed) Result.success(workDataOf(OfflineDatabaseDownloadScheduler.KEY_PROGRESS to 100))
            else Result.failure()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.w("OfflineDbDownloadWorker", "Database download failed: category=${exception.javaClass.simpleName}")
            Result.failure()
        }
    }
}
