package vip.mystery0.pixel.telo.viewmodel

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vip.mystery0.pixel.telo.data.repository.EngagementPromptRepository
import vip.mystery0.pixel.telo.data.preferences.EngagementPromptPolicy
import java.util.concurrent.TimeUnit

class EngagementPromptViewModel : ViewModel(), KoinComponent {
    private val repository: EngagementPromptRepository by inject()
    private val _entryToken = MutableStateFlow(0)
    val entryToken = _entryToken.asStateFlow()
    private val _visible = MutableStateFlow(false)
    val visible = _visible.asStateFlow()
    private var eligibleForEntry = false
    private var prepareJob: Job? = null
    private var expiryJob: Job? = null
    private var actionInProgress = false
    private var generation = 0
    private var exposureConfirmed = false

    fun prepareEntry(allowed: Boolean): Int {
        val entryGeneration = ++generation
        prepareJob?.cancel()
        expiryJob?.cancel()
        eligibleForEntry = false
        exposureConfirmed = false
        _visible.value = false
        _entryToken.value = 0
        prepareJob = viewModelScope.launch {
            repository.awaitLoaded()
            if (entryGeneration != generation) return@launch
            eligibleForEntry = allowed && EngagementPromptPolicy.canShow(repository.state.value, System.currentTimeMillis())
            _entryToken.value = entryGeneration
        }
        return entryGeneration
    }

    fun showIfEligible() {
        if (!eligibleForEntry || actionInProgress) return
        eligibleForEntry = false
        val now = System.currentTimeMillis()
        val state = repository.state.value
        _visible.value = EngagementPromptPolicy.canShow(state, now)
        scheduleExpiry(state.firstShownAt, now)
    }

    /** 列表真正把卡片显示到可见区域后才开始首次展示期限。 */
    fun confirmShown() {
        if (!_visible.value || exposureConfirmed) return
        exposureConfirmed = true
        val entryGeneration = generation
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.markShown(now)
            if (entryGeneration != generation) return@launch
            val state = repository.state.value
            _visible.value = EngagementPromptPolicy.canShow(state, now)
            scheduleExpiry(state.firstShownAt, now)
        }
    }

    private fun scheduleExpiry(firstShownAt: Long, now: Long) {
        expiryJob?.cancel()
        if (!_visible.value || firstShownAt <= 0) return
        expiryJob = viewModelScope.launch {
            delay((TimeUnit.DAYS.toMillis(7) - (now - firstShownAt)).coerceAtLeast(0))
            _visible.value = false
        }
    }

    /** 先记住退出，再进行外部跳转；跳转结果不等同于评价或付款结果。 */
    fun dismissThen(action: () -> Unit = {}) {
        if (actionInProgress) return
        actionInProgress = true
        generation++
        prepareJob?.cancel()
        eligibleForEntry = false
        _visible.value = false
        expiryJob?.cancel()
        viewModelScope.launch {
            try {
                repository.dismiss()
                action()
            } catch (e: CancellationException) {
                throw e
            } finally {
                actionInProgress = false
            }
        }
    }

    fun suspendEntry(entryGeneration: Int) {
        // 旋转或字体变化时，旧页面的清理不能取消新页面已经取得的展示资格。
        if (entryGeneration != generation) return
        generation++
        prepareJob?.cancel()
        expiryJob?.cancel()
        eligibleForEntry = false
        _visible.value = false
        _entryToken.value = 0
    }
}
