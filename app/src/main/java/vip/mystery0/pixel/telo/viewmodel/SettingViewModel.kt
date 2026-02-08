package vip.mystery0.pixel.telo.viewmodel

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.BuildConfig
import vip.mystery0.pixel.telo.data.remote.SyncResponse
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.CheckResult
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository
import vip.mystery0.pixel.telo.data.repository.SyncRepository

class SettingViewModel : ViewModel(), KoinComponent {
    companion object {
        private const val TAG = "SettingViewModel"
        const val KEY_NOTIFY_ONLY = "notify_only"
    }

    private val syncRepository: SyncRepository by inject()
    private val blockedCallRepository: BlockedCallRepository by inject()
    private val spamNumberRepository: SpamNumberRepository by inject()
    private val prefs: SharedPreferences by inject()

    var showTestDialog by mutableStateOf(false)
        private set

    var testPhoneNumber by mutableStateOf("")

    var testResult by mutableStateOf<CheckResult?>(null)
        private set

    // Debug Options
    var forceDownload by mutableStateOf(false)

    // App Features
    var notifyOnly by mutableStateOf(prefs.getBoolean(KEY_NOTIFY_ONLY, true))

    fun updateNotifyOnly(enabled: Boolean) {
        notifyOnly = enabled
        prefs.edit { putBoolean(KEY_NOTIFY_ONLY, enabled) }
    }

    // Sync State
    var offlineDbVersion by mutableStateOf("检查中...")
        private set
    var showUpdateDialog by mutableStateOf<SyncResponse?>(null)
        private set
    var syncStatusMessage by mutableStateOf<String?>(null)
        private set

    // Download State
    var isDownloading by mutableStateOf(false)
        private set
    var downloadProgress by mutableFloatStateOf(0f)
        private set

    init {
        viewModelScope.launch {
            syncRepository.versionFlow.collect { version ->
                offlineDbVersion = version.ifBlank {
                    val v = syncRepository.getCurrentVersion()
                    v.ifBlank { "Not Found" }
                }
            }
        }
    }

    fun checkUpdate() {
        viewModelScope.launch {
            syncStatusMessage = "检查更新中..."
            try {
                val currentVersion = if (forceDownload) {
                    ""
                } else {
                    syncRepository.getCurrentVersion()
                }
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
        viewModelScope.launch {
            // 在同一帧内完成状态切换，避免 BottomSheet 短暂关闭后重开
            isDownloading = true
            showUpdateDialog = null
            downloadProgress = 0f
            syncStatusMessage = null

            val success = syncRepository.downloadAndInstallWithProgress(
                updateInfo.downloadUrl,
                updateInfo.checksum,
                updateInfo.sizeBytes
            ) { progress ->
                downloadProgress = progress / 100f
            }

            isDownloading = false
            syncStatusMessage = if (success) {
                "更新成功！"
            } else {
                "离线数据更新失败"
            }
        }
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
        testResult = null
    }

    fun updateTestPhoneNumber(number: String) {
        testPhoneNumber = number
    }

    fun testBlock() {
        if (testPhoneNumber.isBlank()) return
        viewModelScope.launch {
            try {
                testResult = spamNumberRepository.checkSpam(testPhoneNumber)
            } catch (e: Exception) {
                Log.e(TAG, "Test block failed", e)
                syncStatusMessage = "测试失败: ${e.message}"
            }
        }
    }

    fun saveTestResult() {
        val result = testResult ?: return
        viewModelScope.launch {
            blockedCallRepository.insert(
                testPhoneNumber,
                result.label.ifBlank { "手动测试" },
                result.resultType,
                result.localCost,
                result.networkCost
            )
            hideTestDialog()
            syncStatusMessage = "已记录到拦截列表"
        }
    }

    fun deleteDatabase() {
        viewModelScope.launch {
            syncRepository.deleteDatabase()
            syncStatusMessage = "离线数据库已删除"
        }
    }

    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE
}