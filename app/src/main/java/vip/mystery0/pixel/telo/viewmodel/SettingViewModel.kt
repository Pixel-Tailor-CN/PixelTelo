package vip.mystery0.pixel.telo.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import vip.mystery0.pixel.telo.BuildConfig

class SettingViewModel : ViewModel(), KoinComponent {
    var showTestDialog by mutableStateOf(false)
        private set

    var testPhoneNumber by mutableStateOf("")

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