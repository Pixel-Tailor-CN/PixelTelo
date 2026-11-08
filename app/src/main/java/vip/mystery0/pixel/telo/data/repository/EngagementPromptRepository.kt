package vip.mystery0.pixel.telo.data.repository

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vip.mystery0.pixel.telo.data.model.EngagementPromptState
import vip.mystery0.pixel.telo.data.preferences.EngagementPromptPolicy
import java.time.LocalDate

class EngagementPromptRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences("engagement_prompt", Context.MODE_PRIVATE) }
    private val mutex = Mutex()
    private var loaded = false
    private var storageFailed = false
    // 初始化完成前不允许提示，页面可通过 awaitLoaded 在进入时取得准确资格。
    private val _state = MutableStateFlow(EngagementPromptState(dismissed = true))
    val state = _state.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            mutex.withLock { ensureLoaded() }
        }
    }

    suspend fun awaitLoaded() = withContext(Dispatchers.IO) {
        mutex.withLock { ensureLoaded() }
    }

    suspend fun noteForegroundStarted(nowMillis: Long) = update { current ->
        if (!current.dismissed && current.firstUsedAt == 0L && nowMillis > 0) {
            current.copy(firstUsedAt = nowMillis)
        } else current
    }

    suspend fun recordQualifiedSession(nowMillis: Long, epochDay: Long) = update {
        EngagementPromptPolicy.recordSession(it, nowMillis, epochDay)
    }

    suspend fun markShown(nowMillis: Long) = update {
        EngagementPromptPolicy.markShown(it, nowMillis)
    }

    suspend fun dismiss() = update { it.copy(dismissed = true) }

    private suspend fun update(transform: (EngagementPromptState) -> EngagementPromptState) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoaded()
            if (storageFailed) return@withLock
            try {
                val next = transform(_state.value)
                if (next == _state.value) return@withLock
                if (!writeState(next)) {
                    failClosed()
                    return@withLock
                }
                _state.value = next
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failClosed()
            }
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        try {
            _state.value = readState()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failClosed()
        }
        loaded = true
    }

    private fun readState(): EngagementPromptState {
        if (prefs.all.isEmpty()) return EngagementPromptState()
        val requiredKeys = setOf("first_used_at", "qualifying_sessions", "active_days", "last_qualified_at", "first_shown_at", "dismissed")
        check(requiredKeys.all(prefs::contains))
        val days = checkNotNull(prefs.getStringSet("active_days", null)).map { value ->
            value.toLong().also { LocalDate.ofEpochDay(it) }
        }.toSet()
        val result = EngagementPromptState(
            firstUsedAt = prefs.getLong("first_used_at", -1),
            qualifyingSessions = prefs.getInt("qualifying_sessions", -1),
            activeDays = days,
            lastQualifiedAt = prefs.getLong("last_qualified_at", -1),
            firstShownAt = prefs.getLong("first_shown_at", -1),
            dismissed = prefs.getBoolean("dismissed", false),
        )
        check(result.firstUsedAt >= 0 && result.lastQualifiedAt >= 0 && result.firstShownAt >= 0)
        check(result.qualifyingSessions in 0..5 && days.size <= 3 && days.size <= result.qualifyingSessions)
        if (result.qualifyingSessions == 0) {
            check(result.lastQualifiedAt == 0L && days.isEmpty())
        } else {
            check(result.firstUsedAt > 0 && result.lastQualifiedAt >= result.firstUsedAt && days.isNotEmpty())
        }
        if (result.firstShownAt > 0) {
            check(result.firstUsedAt > 0 && result.firstShownAt >= result.firstUsedAt && result.qualifyingSessions == 5 && days.size == 3)
        }
        return result
    }

    @SuppressLint("UseKtx") // 需要检查 commit 的结果，失败时停止自动提示。
    private fun writeState(value: EngagementPromptState): Boolean = prefs.edit()
        .putLong("first_used_at", value.firstUsedAt)
        .putInt("qualifying_sessions", value.qualifyingSessions)
        .putStringSet("active_days", value.activeDays.map(Long::toString).toSet())
        .putLong("last_qualified_at", value.lastQualifiedAt)
        .putLong("first_shown_at", value.firstShownAt)
        .putBoolean("dismissed", value.dismissed)
        .commit()

    private fun failClosed() {
        storageFailed = true
        _state.value = EngagementPromptState(dismissed = true)
        try {
            // 尽力保留停用标记；即便再次写入失败，本进程也不再展示提示。
            writeState(_state.value)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 存储不可用时不能让低优先级提醒影响来电功能。
        }
    }
}
