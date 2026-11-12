package vip.mystery0.pixel.telo.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vip.mystery0.pixel.telo.data.remote.SyncResponse

/** 手动确认或已授权的自动更新入队；KEEP 合并重复操作，任务和进度由 WorkManager 持久化。 */
object OfflineDatabaseDownloadScheduler {
    private const val UNIQUE_WORK_NAME = "offline_database_download"
    internal const val KEY_DOWNLOAD_URL = "download_url"
    internal const val KEY_CHECKSUM = "checksum"
    internal const val KEY_SIZE_BYTES = "size_bytes"
    internal const val KEY_VERSION = "version"
    internal const val KEY_ROW_COUNT = "row_count"
    internal const val KEY_AUTOMATIC = "automatic"
    const val KEY_SKIPPED = "skipped"
    const val KEY_PROGRESS = "progress"
    const val KEY_STAGE = "stage"
    private const val REQUEST_SEQUENCE_TAG = "offline_database_download_sequence:"
    private val commandMutex = Mutex()

    /** WorkInfo 列表没有时间排序保证；序号保存在 tag 中，页面重建后仍能识别最新请求。 */
    fun latestWork(workInfos: List<WorkInfo>): WorkInfo? = workInfos.maxWithOrNull(
        compareBy<WorkInfo> { !it.state.isFinished }
            .thenBy { requestSequence(it) }
            .thenBy { it.id.toString() },
    )

    private fun requestSequence(work: WorkInfo): Long = work.tags.firstNotNullOfOrNull { tag ->
        tag.takeIf { it.startsWith(REQUEST_SEQUENCE_TAG) }
            ?.removePrefix(REQUEST_SEQUENCE_TAG)?.toLongOrNull()
    } ?: 0L

    fun observe(context: Context): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)

    /** 删除号码库前撤销仍未完成的下载，避免排队任务在用户删除后重新安装。 */
    suspend fun cancel(context: Context) = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME).result.get()
        }
        Unit
    }

    suspend fun enqueue(context: Context, update: SyncResponse, automatic: Boolean = false) = withContext(Dispatchers.IO) {
        // WorkManager Data 总量受限，拒绝把异常服务端字段或任意正文写进任务参数。
        require(update.downloadUrl.length in 1..4096)
        require(update.checksum.length == 64)
        require(update.latestVersion.length in 1..128)
        commandMutex.withLock {
            val workManager = WorkManager.getInstance(context)
            val existing = workManager.getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
            if (existing.any { !it.state.isFinished }) return@withLock
            val sequence = Math.addExact(existing.maxOfOrNull(::requestSequence) ?: 0L, 1L)
            val request = OneTimeWorkRequestBuilder<OfflineDatabaseDownloadWorker>()
                .addTag("$REQUEST_SEQUENCE_TAG$sequence")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(
                    KEY_DOWNLOAD_URL to update.downloadUrl,
                    KEY_CHECKSUM to update.checksum,
                    KEY_SIZE_BYTES to update.sizeBytes,
                    KEY_VERSION to update.latestVersion,
                    KEY_ROW_COUNT to update.rowCount,
                    KEY_AUTOMATIC to automatic,
                ))
                .build()
            // 只等待本地入队落盘，不等待下载，避免任务入队失败后页面永久停在进行中。
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request).result.get()
        }
        Unit
    }
}
