package vip.mystery0.pixel.telo.data.query

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/** 撤回等显式修改使在途社区增强失效；不影响原有 Provider 结论。 */
class CommunityQueryEpoch {
    private val generation = AtomicLong(0)
    private val _changes = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes: kotlinx.coroutines.flow.StateFlow<Long> = _changes
    fun current(): Long = generation.get()
    @Synchronized
    fun advance(): Long = generation.incrementAndGet().also { _changes.value = it }
    fun isCurrent(epoch: Long, receivedAtElapsedMs: Long): Boolean {
        val age = SystemClock.elapsedRealtime() - receivedAtElapsedMs
        return epoch == current() && receivedAtElapsedMs >= 0 && age in 0 until MAX_AGE_MS
    }

    companion object {
        const val MAX_AGE_MS = 60_000L
    }
}
