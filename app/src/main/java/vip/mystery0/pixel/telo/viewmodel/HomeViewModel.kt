package vip.mystery0.pixel.telo.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.remote.QueryResponse
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository
import vip.mystery0.pixel.telo.data.repository.SyncRepository

/** 联网重查的 UI 状态 */
sealed interface RetryQueryState {
    data object Idle : RetryQueryState
    data class Loading(val call: BlockedCall) : RetryQueryState
    data class Success(val call: BlockedCall, val response: QueryResponse) : RetryQueryState
    data class Failure(val call: BlockedCall, val message: String) : RetryQueryState
}

class HomeViewModel() : ViewModel(), KoinComponent {
    private val repository: BlockedCallRepository by inject()
    private val syncRepository: SyncRepository by inject()
    private val spamNumberRepository: SpamNumberRepository by inject()
    private val context: Context by inject()

    val blockedCalls: StateFlow<List<BlockedCall>> = repository.allBlockedCalls
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isDatabaseReady: StateFlow<Boolean> = syncRepository.versionFlow
        .map { it.isNotBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _missingPermissions = MutableStateFlow<List<String>>(emptyList())
    val missingPermissions: StateFlow<List<String>> = _missingPermissions.asStateFlow()

    private val _isDefaultApp = MutableStateFlow(true)
    val isDefaultApp: StateFlow<Boolean> = _isDefaultApp.asStateFlow()

    fun updateDefaultAppState(isDefault: Boolean) {
        _isDefaultApp.value = isDefault
    }

    fun updateMissingPermissions(permissions: List<String>) {
        _missingPermissions.value = permissions
    }

    private val _retryQueryState = MutableStateFlow<RetryQueryState>(RetryQueryState.Idle)
    val retryQueryState: StateFlow<RetryQueryState> = _retryQueryState.asStateFlow()

    /** 对超时记录发起联网重查 */
    fun retryNetworkQuery(call: BlockedCall) {
        viewModelScope.launch {
            _retryQueryState.value = RetryQueryState.Loading(call)
            try {
                val response = spamNumberRepository.queryNetwork(call.phoneNumber)
                _retryQueryState.value = RetryQueryState.Success(call, response)
            } catch (e: Exception) {
                _retryQueryState.value = RetryQueryState.Failure(
                    call,
                    e.message ?: context.getString(R.string.title_query_failed)
                )
            }
        }
    }

    /** 将重查结果写入备注并关闭对话框 */
    fun writeQueryResultToRemark(call: BlockedCall, remark: String) {
        viewModelScope.launch {
            repository.update(call.copy(remark = remark))
            _retryQueryState.value = RetryQueryState.Idle
        }
    }

    /** 关闭重查对话框 */
    fun dismissRetry() {
        _retryQueryState.value = RetryQueryState.Idle
    }

    fun delete(blockedCall: BlockedCall) {
        viewModelScope.launch {
            repository.delete(blockedCall)
        }
    }
}