package vip.mystery0.pixel.telo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.model.*
import vip.mystery0.pixel.telo.data.repository.CommunityReportRepository

/** 云端列表只在内存保留一个有界窗口，不从本地通话历史推导。 */
data class MyReportsUiState(
    val items: List<CommunityReport> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val operationId: String? = null,
    val initialized: Boolean = false,
    val failure: ReportFailure? = null,
)

class MyReportsViewModel : ViewModel(), KoinComponent {
    private val repository: CommunityReportRepository by inject()
    private val _state = MutableStateFlow(MyReportsUiState())
    val state = _state.asStateFlow()
    val repositoryBusy = repository.busy
    private var generation = 0L
    private var loadingJob: Job? = null
    private var actionJob: Job? = null
    // 只保留本次分页会话的游标指纹，检测非相邻循环；刷新或离页即释放。
    private val seenCursors = mutableSetOf<String>()

    fun onEnter() = refresh()

    fun onLeave() {
        seenCursors.clear()
        generation++
        loadingJob?.cancel()
        actionJob?.cancel()
        _state.value = _state.value.copy(loading = false, loadingMore = false, operationId = null)
    }

    fun refresh() {
        if (_state.value.operationId != null) return
        generation++
        loadingJob?.cancel()
        seenCursors.clear()
        load(null, false)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.operationId != null) return
        val cursor = current.nextCursor ?: return
        load(cursor, true)
    }

    private fun load(cursor: String?, append: Boolean) {
        val target = generation
        _state.value = _state.value.copy(loading = !append, loadingMore = append, failure = null)
        loadingJob = viewModelScope.launch {
            val result = repository.list(cursor)
            if (target != generation) return@launch
            _state.value = when (result) {
                is ReportResult.Success -> {
                    val page = result.value
                    val nextFingerprint = page.nextCursor?.let {
                        java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.UTF_8))
                            .joinToString("") { byte -> "%02x".format(byte) }
                    }
                    if (nextFingerprint != null && !seenCursors.add(nextFingerprint)) {
                        _state.value.copy(loading = false, loadingMore = false, nextCursor = null,
                            failure = ReportFailure(ReportFailureKind.INVALID_RESPONSE))
                    } else {
                        val merged = if (append) (_state.value.items + page.items).distinctBy { it.reportId }
                            else page.items.distinctBy { it.reportId }
                        _state.value.copy(
                            items = merged.takeLast(100), nextCursor = page.nextCursor,
                            loading = false, loadingMore = false, initialized = true, failure = null,
                        )
                    }
                }
                is ReportResult.Failure -> _state.value.copy(
                    loading = false, loadingMore = false, failure = result.reason,
                )
            }
        }
    }

    fun withdraw(reportId: String) = change(reportId, false)
    fun delete(reportId: String) = change(reportId, true)

    private fun change(reportId: String, delete: Boolean) {
        val current = _state.value
        if (current.operationId != null) return
        val report = current.items.firstOrNull { it.reportId == reportId } ?: return
        if (delete && !report.canDelete) return
        if (!delete && !report.canWithdraw) return
        generation++
        val target = generation
        loadingJob?.cancel()
        _state.value = current.copy(operationId = reportId, loading = false, loadingMore = false, failure = null)
        actionJob = viewModelScope.launch {
            val result: ReportResult<CommunityReport?> = if (delete) {
                when (val deletion = repository.delete(report)) {
                    is ReportResult.Success -> ReportResult.Success(null)
                    is ReportResult.Failure -> deletion
                }
            } else repository.withdraw(report)
            if (generation != target) return@launch
            when (result) {
                is ReportResult.Success -> {
                    val updated = _state.value.items.mapNotNull {
                        if (it.reportId == reportId) result.value else it
                    }
                    _state.value = _state.value.copy(items = updated, operationId = null, nextCursor = null)
                    refresh()
                }
                is ReportResult.Failure -> {
                    _state.value = _state.value.copy(operationId = null, failure = result.reason)
                }
            }
        }
    }

    fun resetIdentityAfterConfirmation() {
        if (_state.value.operationId != null || repository.busy.value) return
        generation++
        val target = generation
        loadingJob?.cancel()
        _state.value = _state.value.copy(operationId = "reset", loading = false, loadingMore = false)
        actionJob = viewModelScope.launch {
            when (val result = repository.resetIdentityAfterConfirmation()) {
                is ReportResult.Success -> if (generation == target) {
                    _state.value = MyReportsUiState()
                    refresh()
                }
                is ReportResult.Failure -> if (generation == target) {
                    _state.value = _state.value.copy(operationId = null, failure = result.reason)
                }
            }
        }
    }
}
