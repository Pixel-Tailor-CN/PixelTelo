package vip.mystery0.pixel.telo.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.repository.SyncRepository

/** 普通持久化下载任务：不自动更新，不依赖页面存活，也不申请额外前台服务权限。 */
class OfflineDatabaseDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val repository: SyncRepository by inject()

    override suspend fun doWork(): Result {
        val url = inputData.getString(OfflineDatabaseDownloadScheduler.KEY_DOWNLOAD_URL) ?: return Result.failure()
        val checksum = inputData.getString(OfflineDatabaseDownloadScheduler.KEY_CHECKSUM) ?: return Result.failure()
        val version = inputData.getString(OfflineDatabaseDownloadScheduler.KEY_VERSION) ?: return Result.failure()
        return try {
            setProgress(workDataOf(OfflineDatabaseDownloadScheduler.KEY_PROGRESS to 0))
            val installed = repository.downloadAndInstallWithProgress(
                downloadUrl = url,
                expectedChecksum = checksum,
                expectedSize = inputData.getLong(OfflineDatabaseDownloadScheduler.KEY_SIZE_BYTES, 0),
                expectedVersion = version,
                expectedRowCount = inputData.getLong(OfflineDatabaseDownloadScheduler.KEY_ROW_COUNT, 0),
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
