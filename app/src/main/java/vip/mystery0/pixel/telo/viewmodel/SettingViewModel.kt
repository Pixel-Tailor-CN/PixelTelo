package vip.mystery0.pixel.telo.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.BuildConfig
import vip.mystery0.pixel.telo.data.remote.SyncResponse
import vip.mystery0.pixel.telo.data.repository.SyncRepository

class SettingViewModel : ViewModel(), KoinComponent {
    private val syncRepository: SyncRepository by inject()

    var showTestDialog by mutableStateOf(false)
        private set

    var testPhoneNumber by mutableStateOf("")

    // Sync State
    var offlineDbVersion by mutableStateOf<String>("Loading...")
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
            offlineDbVersion = if (version == 0) "Not Found" else version.toString()
        }
    }

    fun checkUpdate() {
        viewModelScope.launch {
            syncStatusMessage = "Checking for updates..."
            try {
                val currentVersion = syncRepository.getCurrentVersion()
                val response = syncRepository.checkUpdate()

                if (response.latestVersion > currentVersion) {
                    showUpdateDialog = response
                    syncStatusMessage = null
                } else {
                    syncStatusMessage = "Already latest version."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                syncStatusMessage = "Check failed: ${e.message}"
            }
        }
    }

    fun confirmUpdate() {
        val updateInfo = showUpdateDialog ?: return
        showUpdateDialog = null // Dismiss dialog

        viewModelScope.launch {
            syncStatusMessage = "Downloading..."
            val success = syncRepository.downloadAndInstall(
                updateInfo.downloadUrl,
                updateInfo.checksum,
                updateInfo.sizeBytes
            )
            if (success) {
                syncStatusMessage = "Update successful!"
                refreshOfflineVersion()
            } else {
                syncStatusMessage = "Update failed."
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