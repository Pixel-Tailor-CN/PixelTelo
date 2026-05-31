package vip.mystery0.pixel.telo.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.BuildConfig
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.remote.SyncResponse
import vip.mystery0.pixel.telo.data.repository.BackupOptions
import vip.mystery0.pixel.telo.data.repository.BackupPreview
import vip.mystery0.pixel.telo.data.repository.BackupRepository
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.CheckResult
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository
import vip.mystery0.pixel.telo.data.repository.SyncRepository
import java.io.InputStream
import java.io.OutputStream

/**
 * 备份/恢复操作的结果状态
 */
sealed interface BackupRestoreState {
    data object Idle : BackupRestoreState
    data object Processing : BackupRestoreState
    data class Success(val message: String) : BackupRestoreState
    data class Failure(val message: String) : BackupRestoreState
}

class SettingViewModel : ViewModel(), KoinComponent {
    companion object {
        private const val TAG = "SettingViewModel"
        const val KEY_NOTIFY_ONLY = "notify_only"
        const val KEY_NO_NETWORK_QUERY = "no_network_query"
        const val KEY_ALWAYS_RECORD = "always_record"
        const val KEY_NETWORK_TIMEOUT = "network_timeout"
        const val KEY_SHOW_LOCATION_OVERLAY = "show_location_overlay"
        const val KEY_ALLOW_REPEAT_CALL = "allow_repeat_call"
        const val KEY_REPEAT_CALL_WINDOW_MINUTES = "repeat_call_window_minutes"
        const val DEFAULT_REPEAT_CALL_WINDOW_MINUTES = 3
    }

    private val syncRepository: SyncRepository by inject()
    private val blockedCallRepository: BlockedCallRepository by inject()
    private val backupRepository: BackupRepository by inject()
    private val spamNumberRepository: SpamNumberRepository by inject()
    private val prefs: SharedPreferences by inject()
    private val context: Context by inject()

    // Backup / Restore State
    var backupRestoreState by mutableStateOf<BackupRestoreState>(BackupRestoreState.Idle)
        private set

    var showTestDialog by mutableStateOf(false)
        private set

    var testPhoneNumber by mutableStateOf("")

    var testResult by mutableStateOf<CheckResult?>(null)
        private set

    // 调试模式解锁状态（每次启动默认隐藏，不持久化）
    var debugUnlocked by mutableStateOf(false)
        private set

    private var versionClickCount = 0

    fun onVersionClick() {
        if (debugUnlocked) return
        versionClickCount++
        if (versionClickCount >= 5) {
            debugUnlocked = true
        }
    }

    // Debug Options
    var forceDownload by mutableStateOf(false)

    // App Features
    var notifyOnly by mutableStateOf(prefs.getBoolean(KEY_NOTIFY_ONLY, true))

    fun updateNotifyOnly(enabled: Boolean) {
        notifyOnly = enabled
        prefs.edit { putBoolean(KEY_NOTIFY_ONLY, enabled) }
    }

    var noNetworkQuery by mutableStateOf(prefs.getBoolean(KEY_NO_NETWORK_QUERY, false))

    fun updateNoNetworkQuery(enabled: Boolean) {
        noNetworkQuery = enabled
        prefs.edit {
            putBoolean(KEY_NO_NETWORK_QUERY, enabled)
            if (enabled) {
                putBoolean(KEY_SHOW_LOCATION_OVERLAY, false)
            }
        }
        if (enabled) {
            showLocationOverlay = false
        }
    }

    var alwaysRecord by mutableStateOf(prefs.getBoolean(KEY_ALWAYS_RECORD, false))

    fun updateAlwaysRecord(enabled: Boolean) {
        alwaysRecord = enabled
        prefs.edit { putBoolean(KEY_ALWAYS_RECORD, enabled) }
    }

    var networkTimeout by mutableIntStateOf(prefs.getInt(KEY_NETWORK_TIMEOUT, 3))

    fun updateNetworkTimeout(timeout: Int) {
        networkTimeout = timeout
        prefs.edit { putInt(KEY_NETWORK_TIMEOUT, timeout) }
    }

    var showLocationOverlay by mutableStateOf(
        prefs.getBoolean(KEY_SHOW_LOCATION_OVERLAY, false) && !noNetworkQuery
    )

    fun updateShowLocationOverlay(enabled: Boolean) {
        val effectiveEnabled = enabled && !noNetworkQuery
        showLocationOverlay = effectiveEnabled
        prefs.edit { putBoolean(KEY_SHOW_LOCATION_OVERLAY, effectiveEnabled) }
    }

    var allowRepeatCall by mutableStateOf(prefs.getBoolean(KEY_ALLOW_REPEAT_CALL, false))

    fun updateAllowRepeatCall(enabled: Boolean) {
        allowRepeatCall = enabled
        prefs.edit { putBoolean(KEY_ALLOW_REPEAT_CALL, enabled) }
    }

    var repeatCallWindowMinutes by mutableIntStateOf(
        prefs.getInt(KEY_REPEAT_CALL_WINDOW_MINUTES, DEFAULT_REPEAT_CALL_WINDOW_MINUTES)
    )

    fun updateRepeatCallWindowMinutes(minutes: Int) {
        repeatCallWindowMinutes = minutes
        prefs.edit { putInt(KEY_REPEAT_CALL_WINDOW_MINUTES, minutes) }
    }

    // Sync State
    var offlineDbVersion by mutableStateOf("")
        private set
    var showUpdateDialog by mutableStateOf<SyncResponse?>(null)
        private set
    var localRowCount by mutableStateOf(0L)
        private set
    var syncStatusMessage by mutableStateOf<String?>(null)
        private set

    // Download State
    var isDownloading by mutableStateOf(false)
        private set
    var downloadProgress by mutableFloatStateOf(0f)
        private set

    init {
        offlineDbVersion = context.getString(R.string.msg_checking)
        viewModelScope.launch {
            syncRepository.versionFlow.collect { version ->
                offlineDbVersion = version.ifBlank {
                    val v = syncRepository.getCurrentVersion()
                    v.ifBlank { context.getString(R.string.msg_not_found) }
                }
            }
        }
    }

    fun checkUpdate() {
        viewModelScope.launch {
            syncStatusMessage = context.getString(R.string.msg_checking_update)
            try {
                val currentVersion = if (forceDownload) {
                    ""
                } else {
                    syncRepository.getCurrentVersion()
                }
                localRowCount = syncRepository.getLocalRowCount()
                val response = syncRepository.checkUpdate(currentVersion)

                if (response.hasUpdate) {
                    showUpdateDialog = response
                    syncStatusMessage = null
                } else {
                    syncStatusMessage = context.getString(R.string.msg_already_latest_version)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking update", e)
                syncStatusMessage = context.getString(R.string.msg_check_update_failed, e.message)
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
                context.getString(R.string.msg_update_success)
            } else {
                context.getString(R.string.msg_update_failed)
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
                testResult =
                    spamNumberRepository.checkSpam(testPhoneNumber, forceNetworkQuery = true)
            } catch (e: Exception) {
                Log.e(TAG, "Test block failed", e)
                syncStatusMessage = context.getString(R.string.msg_test_failed, e.message)
            }
        }
    }

    fun saveTestResult() {
        val result = testResult ?: return
        viewModelScope.launch {
            blockedCallRepository.insert(
                testPhoneNumber,
                remark = result.label.ifBlank { context.getString(R.string.label_manual_test) },
                result.resultType,
                result.localCost,
                result.networkCost,
                label = result.label.takeIf { it.isNotBlank() }
            )
            hideTestDialog()
            syncStatusMessage = context.getString(R.string.msg_recorded_to_intercept_list)
        }
    }

    fun dismissBackupRestoreResult() {
        backupRestoreState = BackupRestoreState.Idle
    }

    // ---- 备份选项 Sheet ----
    /** 是否展示备份内容选择 Sheet */
    var showBackupOptionsSheet by mutableStateOf(false)
        private set

    /** 备份选项（默认全选） */
    var backupOptions by mutableStateOf(BackupOptions())

    fun openBackupOptionsSheet() {
        backupOptions = BackupOptions()
        showBackupOptionsSheet = true
    }

    fun closeBackupOptionsSheet() {
        showBackupOptionsSheet = false
    }

    // ---- 恢复：解析预览 + 恢复选项 Sheet ----
    /** 已解析的备份预览，非 null 时展示恢复选项 Sheet */
    var backupPreview by mutableStateOf<BackupPreview?>(null)
        private set

    /** 恢复选项 */
    var restoreOptions by mutableStateOf(BackupOptions())

    fun closeRestoreOptionsSheet() {
        backupPreview = null
    }

    /** 解析备份文件（不执行写入），成功后展示恢复选项 Sheet */
    fun parseBackupFile(inputStream: InputStream) {
        viewModelScope.launch {
            backupRestoreState = BackupRestoreState.Processing
            try {
                val preview = withContext(Dispatchers.IO) {
                    backupRepository.parseBackup(inputStream)
                }
                backupPreview = preview
                // 根据备份内容默认勾选有数据的部分
                restoreOptions = BackupOptions(
                    includeBlockedCalls = preview.blockedCallCount > 0,
                    includeBlackList = preview.blackListCount > 0,
                    includeWhiteList = preview.whiteListCount > 0,
                )
                backupRestoreState = BackupRestoreState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Parse backup failed", e)
                backupRestoreState = BackupRestoreState.Failure(
                    context.getString(R.string.msg_restore_failed, e.message)
                )
            }
        }
    }

    /** 使用选定的选项执行备份 */
    fun performBackupWithOptions(outputStream: OutputStream) {
        val options = backupOptions
        showBackupOptionsSheet = false
        viewModelScope.launch {
            backupRestoreState = BackupRestoreState.Processing
            try {
                withContext(Dispatchers.IO) { backupRepository.backup(outputStream, options) }
                backupRestoreState = BackupRestoreState.Success(
                    context.getString(R.string.msg_backup_exported)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                backupRestoreState = BackupRestoreState.Failure(
                    context.getString(R.string.msg_backup_failed, e.message)
                )
            }
        }
    }

    /** 使用选定的选项执行恢复 */
    fun performRestoreWithOptions() {
        val preview = backupPreview ?: return
        val options = restoreOptions
        backupPreview = null
        viewModelScope.launch {
            backupRestoreState = BackupRestoreState.Processing
            try {
                val result = withContext(Dispatchers.IO) {
                    backupRepository.restore(preview, options)
                }
                backupRestoreState = BackupRestoreState.Success(
                    context.getString(
                        R.string.msg_restored_summary,
                        result.insertedCalls,
                        result.insertedBlack,
                        result.insertedWhite
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                backupRestoreState = BackupRestoreState.Failure(
                    context.getString(R.string.msg_restore_failed, e.message)
                )
            }
        }
    }

    fun deleteDatabase() {
        viewModelScope.launch {
            syncRepository.deleteDatabase()
            syncStatusMessage = context.getString(R.string.msg_database_deleted)
        }
    }

    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE
}
