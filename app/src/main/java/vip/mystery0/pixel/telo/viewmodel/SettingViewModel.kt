package vip.mystery0.pixel.telo.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.BuildConfig
import vip.mystery0.pixel.telo.data.remote.SyncResponse
import vip.mystery0.pixel.telo.data.repository.SyncRepository
import vip.mystery0.pixel.telo.worker.DatabaseSyncWorker

class SettingViewModel : ViewModel(), KoinComponent {
    companion object {
        private const val TAG = "SettingViewModel"
    }

    private val syncRepository: SyncRepository by inject()
    private val workManager: WorkManager by inject()

    var showTestDialog by mutableStateOf(false)
        private set

    var testPhoneNumber by mutableStateOf("")

    // Sync State
    var offlineDbVersion by mutableStateOf("检查中...")
        private set
    var showUpdateDialog by mutableStateOf<SyncResponse?>(null)
        private set
    var syncStatusMessage by mutableStateOf<String?>(null)
        private set

    init {
        refreshOfflineVersion()
    }

    private fun refreshOfflineVersion() {
        viewModelScope.launch {
            val version = syncRepository.getCurrentVersion()
            offlineDbVersion = if (version == "") "Not Found" else version
        }
    }

    fun checkUpdate() {
        viewModelScope.launch {
            syncStatusMessage = "检查更新中..."
            try {
                val currentVersion = syncRepository.getCurrentVersion()
                val response = syncRepository.checkUpdate(currentVersion)

                if (response.hasUpdate) {
                    showUpdateDialog = response
                    syncStatusMessage = null
                } else {
                    syncStatusMessage = "已经是最新版本了"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking update", e)
                syncStatusMessage = "检查更新失败: ${e.message}"
            }
        }
    }

    fun confirmUpdate() {
        val updateInfo = showUpdateDialog ?: return
        showUpdateDialog = null

        val inputData = workDataOf(
            "downloadUrl" to updateInfo.downloadUrl,
            "checksum" to updateInfo.checksum,
            "sizeBytes" to updateInfo.sizeBytes,
            "latestVersion" to updateInfo.latestVersion
        )

        val request = OneTimeWorkRequest.Builder(DatabaseSyncWorker::class.java)
            .setInputData(inputData)
            .build()
        workManager.enqueue(request)
        syncStatusMessage = "已在后台开始更新，请查看通知栏进度"
    }

    fun cancelUpdate() {
        showUpdateDialog = null
    }

    fun clearStatusMessage() {
        syncStatusMessage = null
    }

    fun showTestDialog() {
        showTestDialog = true
    }

    fun hideTestDialog() {
        showTestDialog = false
        testPhoneNumber = ""
    }

    fun updateTestPhoneNumber(number: String) {
        testPhoneNumber = number
    }

    fun testBlock() {
        viewModelScope.launch {
            println("Testing block for number: $testPhoneNumber")
            hideTestDialog()
        }
    }

    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE
}