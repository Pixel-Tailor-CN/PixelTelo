package vip.mystery0.pixel.telo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.model.*
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.CommunityReportRepository
import vip.mystery0.pixel.telo.data.repository.reportEligibility

/** 详情上报状态不包含认证信息，与原质量反馈状态机分离。 */
data class CallReportUiState(
    val callId: Long? = null,
    val phone: String = "",
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val accepted: Boolean = false,
    val eligibility: ReportFailure? = null,
    val failure: ReportFailure? = null,
)

class CallReportViewModel : ViewModel(), KoinComponent {
    private val calls: BlockedCallRepository by inject()
    private val reports: CommunityReportRepository by inject()
    private val _state = MutableStateFlow(CallReportUiState())
    val state = _state.asStateFlow()
    private var observer: Job? = null
    private var submission: Job? = null
    private var generation = 0L
    private var token: String? = null

    fun open(callId: Long) {
        if (_state.value.callId == callId && observer?.isActive == true) return
        close()
        val targetGeneration = generation
        _state.value = CallReportUiState(callId = callId, loading = true)
        observer = viewModelScope.launch {
            try {
                calls.observeById(callId).collect { call ->
                    if (generation != targetGeneration) return@collect
                    if (call == null) {
                        submission?.cancel()
                        _state.value = CallReportUiState(eligibility = ReportFailure(ReportFailureKind.NOT_FOUND))
                    } else {
                        val changed = token != call.feedbackToken
                        if (changed) submission?.cancel()
                        token = call.feedbackToken
                        _state.value = _state.value.copy(
                            callId = callId, phone = call.phoneNumber, loading = false,
                            submitting = if (changed) false else _state.value.submitting,
                            accepted = parseReportTokenState(call.reportTokenState) == ReportTokenState.ACCEPTED,
                            eligibility = reportEligibility(call),
                            failure = if (changed) null else _state.value.failure,
                        )
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (generation == targetGeneration) _state.value = _state.value.copy(
                    loading = false, eligibility = ReportFailure(ReportFailureKind.LOCAL_STATE),
                )
            }
        }
    }

    fun submit() {
        val current = _state.value
        val id = current.callId ?: return
        if (current.loading || current.submitting || current.eligibility != null) return
        val target = generation
        val expectedToken = token
        _state.value = current.copy(submitting = true, failure = null)
        submission = viewModelScope.launch {
            try {
                val result = reports.submit(id)
                if (generation != target || token != expectedToken) return@launch
                _state.value = when (result) {
                    is ReportResult.Success -> _state.value.copy(submitting = false, accepted = true)
                    is ReportResult.Failure -> _state.value.copy(submitting = false, failure = result.reason)
                }
            } finally {
                if (generation == target && token == expectedToken) {
                    _state.value = _state.value.copy(submitting = false)
                }
            }
        }
    }

    fun close() {
        generation++
        observer?.cancel()
        submission?.cancel()
        token = null
        _state.value = CallReportUiState()
    }
}
