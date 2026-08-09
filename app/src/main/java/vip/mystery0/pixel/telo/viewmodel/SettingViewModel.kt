package vip.mystery0.pixel.telo.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.BuildConfig
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.dao.QuerySourceQuality
import vip.mystery0.pixel.telo.data.query.QueryBackendProvider
import vip.mystery0.pixel.telo.data.query.QueryBackendState
import vip.mystery0.pixel.telo.data.query.QueryBackendType
import vip.mystery0.pixel.telo.data.query.SelfHostedConnectionState
import vip.mystery0.pixel.telo.data.query.SelfHostedDraft
import vip.mystery0.pixel.telo.data.query.SelfHostedErrorCategory
import vip.mystery0.pixel.telo.data.query.SelfHostedTlsMode
import vip.mystery0.pixel.telo.data.query.SelfHostedValidationResult
import vip.mystery0.pixel.telo.data.query.VerifiedSelfHostedConfig
import vip.mystery0.pixel.telo.data.remote.SyncResponse
import vip.mystery0.pixel.telo.data.repository.BackupOptions
import vip.mystery0.pixel.telo.data.repository.BackupPreview
import vip.mystery0.pixel.telo.data.repository.BackupRepository
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.CheckResult
import vip.mystery0.pixel.telo.data.repository.QueryRepository
import vip.mystery0.pixel.telo.data.repository.QuerySourceItem
import vip.mystery0.pixel.telo.data.repository.QuerySourceState
import vip.mystery0.pixel.telo.data.repository.SelfHostedConfigRepository
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository
import vip.mystery0.pixel.telo.data.repository.SyncRepository
import vip.mystery0.pixel.telo.worker.OfflineDatabaseUpdateScheduler
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

enum class RepeatCallStrategy {
    UNCHANGED,
    SILENCE,
    ALLOW,
}

/** 来电归属地悬浮窗的消失策略。 */
enum class LocationOverlayDisplayMode {
    FIXED_DURATION,
    UNTIL_CALL_END,
}

/** 来电归属地悬浮窗的视觉样式。 */
enum class LocationOverlayStyle {
    CARD,
    MINIMAL,
}

/**
 * 自建查询服务 Dialog 的非敏感草稿。
 *
 * 该公开可观察状态不接入 SavedState，也不包含明文 Token；Token 只通过一次性 `CharArray` 参数移交。
 */
data class SelfHostedDraftUiState(
    val baseUrl: String = "",
    val tlsMode: SelfHostedTlsMode = SelfHostedTlsMode.SYSTEM,
    val spkiPin: String = "",
    val allowPreRelease: Boolean = false,
)

class SettingViewModel : ViewModel(), KoinComponent {
    companion object {
        private const val TAG = "SettingViewModel"
        const val KEY_NOTIFY_ONLY = "notify_only"
        const val KEY_NO_NETWORK_QUERY = "no_network_query"
        const val KEY_ALWAYS_RECORD = "always_record"
        const val KEY_NETWORK_TIMEOUT = "network_timeout"
        const val KEY_SHOW_LOCATION_OVERLAY = "show_location_overlay"
        const val KEY_LOCATION_OVERLAY_OFFSET_DP = "location_overlay_offset_dp"
        const val KEY_LOCATION_OVERLAY_DISPLAY_MODE = "location_overlay_display_mode"
        const val KEY_LOCATION_OVERLAY_DURATION_SECONDS = "location_overlay_duration_seconds"
        const val KEY_LOCATION_OVERLAY_STYLE = "location_overlay_style"
        const val KEY_ALLOW_REPEAT_CALL = "allow_repeat_call"
        const val KEY_REPEAT_CALL_STRATEGY = "repeat_call_strategy"
        const val KEY_REPEAT_CALL_WINDOW_MINUTES = "repeat_call_window_minutes"
        const val KEY_FEEDBACK_NOTIFICATION = "feedback_notification"
        const val DEFAULT_NETWORK_TIMEOUT_SECONDS = 5
        const val MIN_NETWORK_TIMEOUT_SECONDS = 1
        const val MAX_NETWORK_TIMEOUT_SECONDS = 10
        const val DEFAULT_REPEAT_CALL_WINDOW_MINUTES = 3
        const val DEFAULT_LOCATION_OVERLAY_OFFSET_DP = 56
        const val DEFAULT_LOCATION_OVERLAY_DURATION_SECONDS = 6
        const val MIN_LOCATION_OVERLAY_DURATION_SECONDS = 3
        const val MAX_LOCATION_OVERLAY_DURATION_SECONDS = 30

        /** source 质量统计窗口：近 7 天 */
        const val QUALITY_STATS_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000
    }

    private val syncRepository: SyncRepository by inject()
    private val blockedCallRepository: BlockedCallRepository by inject()
    private val backupRepository: BackupRepository by inject()
    private val spamNumberRepository: SpamNumberRepository by inject()
    private val queryRepository: QueryRepository by inject()
    private val queryBackendProvider: QueryBackendProvider by inject()
    private val selfHostedConfigRepository: SelfHostedConfigRepository by inject()
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

    var networkTimeout by mutableIntStateOf(readNetworkTimeout())

    fun updateNetworkTimeout(timeout: Int) {
        val safeTimeout = timeout.coerceIn(
            MIN_NETWORK_TIMEOUT_SECONDS,
            MAX_NETWORK_TIMEOUT_SECONDS
        )
        networkTimeout = safeTimeout
        prefs.edit { putInt(KEY_NETWORK_TIMEOUT, safeTimeout) }
    }

    /** 读取超时配置并夹紧到允许范围，旧版本保存的超范围值会被自动修正 */
    private fun readNetworkTimeout(): Int {
        val storedTimeout = prefs.getInt(KEY_NETWORK_TIMEOUT, DEFAULT_NETWORK_TIMEOUT_SECONDS)
        val safeTimeout = storedTimeout.coerceIn(
            MIN_NETWORK_TIMEOUT_SECONDS,
            MAX_NETWORK_TIMEOUT_SECONDS
        )
        if (storedTimeout != safeTimeout) {
            prefs.edit { putInt(KEY_NETWORK_TIMEOUT, safeTimeout) }
        }
        return safeTimeout
    }

    var autoCheckUpdate by mutableStateOf(
        prefs.getBoolean(OfflineDatabaseUpdateScheduler.KEY_AUTO_CHECK_UPDATE, false)
    )

    fun updateAutoCheckUpdate(enabled: Boolean) {
        if (enabled && !OfflineDatabaseUpdateScheduler.hasNotificationPermission(context)) {
            autoCheckUpdate = false
            prefs.edit {
                putBoolean(OfflineDatabaseUpdateScheduler.KEY_AUTO_CHECK_UPDATE, false)
            }
            OfflineDatabaseUpdateScheduler.cancel(context)
            return
        }
        autoCheckUpdate = enabled
        prefs.edit {
            putBoolean(OfflineDatabaseUpdateScheduler.KEY_AUTO_CHECK_UPDATE, enabled)
        }
        if (enabled) {
            OfflineDatabaseUpdateScheduler.scheduleFromNow(
                context,
                autoCheckUpdateIntervalHours
            )
        } else {
            OfflineDatabaseUpdateScheduler.cancel(context)
        }
    }

    var autoCheckUpdateIntervalHours by mutableIntStateOf(
        OfflineDatabaseUpdateScheduler.normalizeIntervalHours(
            prefs.getInt(
                OfflineDatabaseUpdateScheduler.KEY_AUTO_CHECK_UPDATE_INTERVAL_HOURS,
                OfflineDatabaseUpdateScheduler.DEFAULT_UPDATE_INTERVAL_HOURS
            )
        )
    )
        private set

    fun updateAutoCheckUpdateIntervalHours(hours: Int) {
        val safeHours = OfflineDatabaseUpdateScheduler.normalizeIntervalHours(hours)
        autoCheckUpdateIntervalHours = safeHours
        prefs.edit {
            putInt(OfflineDatabaseUpdateScheduler.KEY_AUTO_CHECK_UPDATE_INTERVAL_HOURS, safeHours)
        }
        if (autoCheckUpdate) {
            OfflineDatabaseUpdateScheduler.scheduleFromNow(context, safeHours)
        }
    }

    var showLocationOverlay by mutableStateOf(
        prefs.getBoolean(KEY_SHOW_LOCATION_OVERLAY, false) && !noNetworkQuery
    )

    fun updateShowLocationOverlay(enabled: Boolean) {
        val effectiveEnabled = enabled && !noNetworkQuery
        showLocationOverlay = effectiveEnabled
        prefs.edit { putBoolean(KEY_SHOW_LOCATION_OVERLAY, effectiveEnabled) }
    }

    var locationOverlayDisplayMode by mutableStateOf(readLocationOverlayDisplayMode())
        private set

    fun updateLocationOverlayDisplayMode(mode: LocationOverlayDisplayMode) {
        locationOverlayDisplayMode = mode
        prefs.edit { putString(KEY_LOCATION_OVERLAY_DISPLAY_MODE, mode.name) }
    }

    var locationOverlayDurationSeconds by mutableIntStateOf(readLocationOverlayDurationSeconds())
        private set

    fun updateLocationOverlayDurationSeconds(seconds: Int) {
        val safeSeconds = seconds.coerceIn(
            MIN_LOCATION_OVERLAY_DURATION_SECONDS,
            MAX_LOCATION_OVERLAY_DURATION_SECONDS
        )
        locationOverlayDurationSeconds = safeSeconds
        prefs.edit { putInt(KEY_LOCATION_OVERLAY_DURATION_SECONDS, safeSeconds) }
    }

    var locationOverlayStyle by mutableStateOf(readLocationOverlayStyle())
        private set

    fun updateLocationOverlayStyle(style: LocationOverlayStyle) {
        locationOverlayStyle = style
        prefs.edit { putString(KEY_LOCATION_OVERLAY_STYLE, style.name) }
    }

    var locationOverlayOffsetDp by mutableIntStateOf(
        prefs.getInt(KEY_LOCATION_OVERLAY_OFFSET_DP, DEFAULT_LOCATION_OVERLAY_OFFSET_DP)
    )
        private set

    var showLocationOverlayAdjuster by mutableStateOf(false)
        private set

    fun toggleLocationOverlayAdjuster() {
        showLocationOverlayAdjuster = !showLocationOverlayAdjuster
    }

    fun hideLocationOverlayAdjuster() {
        showLocationOverlayAdjuster = false
    }

    fun updateLocationOverlayOffset(offsetDp: Int) {
        locationOverlayOffsetDp = offsetDp.coerceAtLeast(0)
        prefs.edit { putInt(KEY_LOCATION_OVERLAY_OFFSET_DP, locationOverlayOffsetDp) }
    }

    private fun readLocationOverlayDisplayMode(): LocationOverlayDisplayMode {
        val stored = prefs.getString(KEY_LOCATION_OVERLAY_DISPLAY_MODE, null)
        return stored?.let {
            runCatching { LocationOverlayDisplayMode.valueOf(it) }.getOrNull()
        } ?: LocationOverlayDisplayMode.FIXED_DURATION
    }

    private fun readLocationOverlayDurationSeconds(): Int {
        val stored = prefs.getInt(
            KEY_LOCATION_OVERLAY_DURATION_SECONDS,
            DEFAULT_LOCATION_OVERLAY_DURATION_SECONDS
        )
        val safeSeconds = stored.coerceIn(
            MIN_LOCATION_OVERLAY_DURATION_SECONDS,
            MAX_LOCATION_OVERLAY_DURATION_SECONDS
        )
        if (stored != safeSeconds) {
            prefs.edit { putInt(KEY_LOCATION_OVERLAY_DURATION_SECONDS, safeSeconds) }
        }
        return safeSeconds
    }

    private fun readLocationOverlayStyle(): LocationOverlayStyle {
        val stored = prefs.getString(KEY_LOCATION_OVERLAY_STYLE, null)
        return stored?.let {
            runCatching { LocationOverlayStyle.valueOf(it) }.getOrNull()
        } ?: LocationOverlayStyle.CARD
    }

    var feedbackNotification by mutableStateOf(readFeedbackNotification())

    fun updateFeedbackNotification(enabled: Boolean) {
        feedbackNotification = enabled
        prefs.edit { putBoolean(KEY_FEEDBACK_NOTIFICATION, enabled) }
    }

    var repeatCallStrategy by mutableStateOf(readRepeatCallStrategy())

    fun updateRepeatCallStrategy(strategy: RepeatCallStrategy) {
        repeatCallStrategy = strategy
        prefs.edit { putString(KEY_REPEAT_CALL_STRATEGY, strategy.name) }
    }

    /** 初始化时校验提醒所需权限，避免默认开启但实际不可用。 */
    private fun readFeedbackNotification(): Boolean {
        val enabled = prefs.getBoolean(KEY_FEEDBACK_NOTIFICATION, true)
        if (!enabled) return false
        val phoneStateGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        val effectiveEnabled = phoneStateGranted && notificationGranted
        if (!effectiveEnabled) {
            prefs.edit { putBoolean(KEY_FEEDBACK_NOTIFICATION, false) }
        }
        return effectiveEnabled
    }

    /** 兼容旧版布尔开关：开启映射为静音放行，关闭映射为不修改。 */
    private fun readRepeatCallStrategy(): RepeatCallStrategy {
        val stored = prefs.getString(KEY_REPEAT_CALL_STRATEGY, null)
        if (stored != null) {
            return runCatching { RepeatCallStrategy.valueOf(stored) }
                .getOrDefault(RepeatCallStrategy.UNCHANGED)
        }
        return if (prefs.getBoolean(KEY_ALLOW_REPEAT_CALL, false)) {
            RepeatCallStrategy.SILENCE
        } else {
            RepeatCallStrategy.UNCHANGED
        }
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
    var localRowCount by mutableLongStateOf(0L)
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
                label = result.label.takeIf { it.isNotBlank() },
                province = result.locationInfo?.province,
                city = result.locationInfo?.city,
                querySource = result.querySource,
                queryBackendId = result.queryBackendId,
                feedbackToken = result.feedbackToken
            )
            hideTestDialog()
            syncStatusMessage = context.getString(R.string.msg_recorded_to_intercept_list)
        }
    }

    // ---- 实时查询 Backend 设置 ----
    /** Provider 发布的 Backend 状态，设置页与 source 设置共同消费同一事实来源。 */
    val queryBackendState: StateFlow<QueryBackendState> = queryBackendProvider.state

    /** 已验证配置的非敏感状态，用于展示 Host、版本与最近验证时间。 */
    val selfHostedConnectionState: StateFlow<SelfHostedConnectionState> =
        selfHostedConfigRepository.connectionState

    /** 自建服务配置/管理 Dialog 是否展示。 */
    var showSelfHostedConfigDialog by mutableStateOf(false)
        private set

    /** 当前 Dialog 是否处于配置编辑模式；false 时展示已启用服务的管理动作。 */
    var selfHostedConfigEditing by mutableStateOf(false)
        private set

    /** 验证期间统一禁用所有会发起新命令的操作。 */
    var selfHostedValidationInProgress by mutableStateOf(false)
        private set

    /** 只保存稳定分类，不保存底层异常、URL 或响应正文。 */
    var selfHostedValidationError by mutableStateOf<SelfHostedErrorCategory?>(null)
        private set

    /** ViewModel 只持有不含 Token 的初始/提交草稿；Dialog 编辑状态由普通 `remember` 持有。 */
    var selfHostedDraft by mutableStateOf<SelfHostedDraftUiState?>(null)
        private set

    /** 打开自建 Backend 管理；当前未选择自建服务时直接进入配置编辑。 */
    fun openSelfHostedConfig() {
        if (selfHostedValidationInProgress) return
        selfHostedDraft = currentSelfHostedConfig()?.toDraftUiState() ?: SelfHostedDraftUiState()
        selfHostedConfigEditing = !queryBackendProvider.state.value.isSelfHostedSelected()
        selfHostedValidationError = null
        showSelfHostedConfigDialog = true
    }

    /** 从管理视图进入配置编辑，Token 始终重新输入，不从凭据存储回填。 */
    fun editSelfHostedConfig() {
        if (!showSelfHostedConfigDialog || selfHostedValidationInProgress) return
        selfHostedDraft = currentSelfHostedConfig()?.toDraftUiState() ?: SelfHostedDraftUiState()
        selfHostedConfigEditing = true
        selfHostedValidationError = null
    }

    /** 关闭 Dialog，并主动断开所有草稿引用。验证进行中时拒绝关闭，避免后台静默切换 Backend。 */
    fun closeSelfHostedConfig() {
        if (selfHostedValidationInProgress) return
        clearSelfHostedDialogState()
    }

    /**
     * 直接接收 Dialog 移交的一次性 Token 并完整验证；Token 不写入任何 Compose/ViewModel State。
     * 调用方交出 [token] 后不得再次读取，本方法覆盖拒绝、取消、失败和成功路径清零。
     */
    fun validateAndEnableSelfHosted(draft: SelfHostedDraftUiState, token: CharArray) {
        if (!showSelfHostedConfigDialog || selfHostedValidationInProgress) {
            token.fill('\u0000')
            return
        }
        selfHostedDraft = draft
        selfHostedValidationInProgress = true
        selfHostedValidationError = null
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    queryBackendProvider.validateAndEnable(
                        draft = SelfHostedDraft(
                            baseUrl = draft.baseUrl,
                            tlsMode = draft.tlsMode,
                            spkiPin = if (draft.tlsMode == SelfHostedTlsMode.SPKI_PIN) {
                                draft.spkiPin
                            } else {
                                ""
                            },
                            allowPreRelease = BuildConfig.DEBUG && draft.allowPreRelease,
                        ),
                        token = token,
                    )
                }
                when (result) {
                    is SelfHostedValidationResult.Success -> clearSelfHostedDialogState()
                    is SelfHostedValidationResult.Failure -> {
                        selfHostedValidationError = result.category
                    }
                }
            } finally {
                token.fill('\u0000')
                selfHostedValidationInProgress = false
            }
        }
    }

    /** 使用已保存凭据执行完整重验证，不从 Dialog 读取或回填 Token。 */
    fun revalidateSelfHosted() {
        if (selfHostedValidationInProgress) return
        selfHostedValidationInProgress = true
        selfHostedValidationError = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { queryBackendProvider.revalidate() }
            selfHostedValidationInProgress = false
            when (result) {
                is SelfHostedValidationResult.Success -> selfHostedValidationError = null
                is SelfHostedValidationResult.Failure -> {
                    selfHostedValidationError = result.category
                }
            }
        }
    }

    /** 切换到官方 Backend；保留自建配置与用户原有反馈通知偏好。 */
    fun useOfficialBackend() {
        if (selfHostedValidationInProgress) return
        selfHostedValidationInProgress = true
        selfHostedValidationError = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { queryBackendProvider.useOfficial() }
            selfHostedValidationInProgress = false
            val state = queryBackendProvider.state.value
            if (state is QueryBackendState.Ready && state.type == QueryBackendType.OFFICIAL) {
                clearSelfHostedDialogState()
            } else {
                selfHostedValidationError = SelfHostedErrorCategory.STORAGE
            }
        }
    }

    private fun clearSelfHostedDialogState() {
        selfHostedDraft = null
        selfHostedValidationError = null
        selfHostedConfigEditing = false
        showSelfHostedConfigDialog = false
    }

    private fun currentSelfHostedConfig(): VerifiedSelfHostedConfig? =
        when (val state = selfHostedConfigRepository.connectionState.value) {
            is SelfHostedConnectionState.Ready -> state.config
            is SelfHostedConnectionState.Blocked -> state.config
            SelfHostedConnectionState.NotConfigured -> null
        }

    private fun VerifiedSelfHostedConfig.toDraftUiState(): SelfHostedDraftUiState =
        SelfHostedDraftUiState(
            baseUrl = baseUrl,
            tlsMode = tlsMode,
            spkiPin = spkiPin,
        )

    private fun QueryBackendState.isSelfHostedSelected(): Boolean = when (this) {
        is QueryBackendState.Ready -> type == QueryBackendType.SELF_HOSTED
        is QueryBackendState.Blocked -> true
    }

    // ---- 联网查询数据源设置 ----
    /** source 配置状态，驱动 BottomSheet 的加载/失败展示 */
    val querySourceState: StateFlow<QuerySourceState> = queryRepository.sourceState

    /** source 设置 BottomSheet 是否展示 */
    var showQuerySourceSheet by mutableStateOf(false)
        private set

    /** BottomSheet 中的编辑草稿，保存前不影响真实配置 */
    var querySourceDraft by mutableStateOf<List<QuerySourceItem>>(emptyList())
        private set

    /** 编辑草稿所属 Backend，用于拒绝切换瞬间的跨 Backend 保存。 */
    private var querySourceDraftBackendId: String? = null

    /** 各 source 近 7 天的查询质量统计，key 为 source ID */
    var querySourceQuality by mutableStateOf<Map<String, QuerySourceQuality>>(emptyMap())
        private set

    /** 当前质量统计加载任务；Backend 切换或面板关闭时取消。 */
    private var querySourceQualityJob: Job? = null

    init {
        viewModelScope.launch {
            querySourceState.collect { state ->
                if (querySourceDraftBackendId == state.backendId) {
                    if (
                        showQuerySourceSheet &&
                        querySourceDraft.isEmpty() &&
                        state.initialized &&
                        !state.refreshing
                    ) {
                        querySourceDraft = state.items
                    }
                    return@collect
                }

                // Backend 切换时先清空旧草稿，再装载新 Backend 已发布的缓存或空状态。
                querySourceDraft = emptyList()
                querySourceDraftBackendId = if (showQuerySourceSheet) state.backendId else null
                if (showQuerySourceSheet) {
                    querySourceDraft = state.items
                    val targetBackendId = state.backendId
                    if (targetBackendId == null) {
                        clearQuerySourceQuality()
                    } else {
                        loadQuerySourceQuality(targetBackendId)
                    }
                }
            }
        }
    }

    /** 打开 source 设置 BottomSheet，无缓存时触发一次刷新 */
    fun openQuerySourceSettings() {
        val backendState = queryBackendProvider.state.value as? QueryBackendState.Ready ?: return
        val state = queryRepository.sourceState.value
        // Provider 已切换但 Repository 尚未发布目标 Backend 时拒绝打开，避免短暂展示旧 source 草稿。
        if (state.backendId != backendState.backendId) return
        querySourceDraftBackendId = state.backendId
        querySourceDraft = state.items
        showQuerySourceSheet = true
        val targetBackendId = state.backendId
        loadQuerySourceQuality(targetBackendId)
        if (querySourceDraft.isEmpty() && !state.refreshing) {
            retryQuerySourceRefresh()
        }
    }

    /** 加载指定 Backend 近 7 天各 source 的号码数与“结果不准确”标记数。 */
    private fun loadQuerySourceQuality(backendId: String) {
        querySourceQualityJob?.cancel()
        querySourceQuality = emptyMap()
        querySourceQualityJob = viewModelScope.launch {
            val since = System.currentTimeMillis() - QUALITY_STATS_WINDOW_MILLIS
            val quality = blockedCallRepository.getSourceQualityStats(since, backendId)
            if (
                showQuerySourceSheet &&
                querySourceDraftBackendId == backendId &&
                queryRepository.sourceState.value.backendId == backendId
            ) {
                querySourceQuality = quality
            }
        }
    }

    /** 清除旧 Backend 统计，避免加载期间短暂展示跨 Backend 数据。 */
    private fun clearQuerySourceQuality() {
        querySourceQualityJob?.cancel()
        querySourceQualityJob = null
        querySourceQuality = emptyMap()
    }

    fun closeQuerySourceSettings() {
        showQuerySourceSheet = false
        querySourceDraft = emptyList()
        querySourceDraftBackendId = null
        clearQuerySourceQuality()
    }

    /** 启停草稿中的 source；不可用 source 禁止重新启用 */
    fun toggleQuerySource(id: String, enabled: Boolean) {
        querySourceDraft = querySourceDraft.map { item ->
            when {
                item.id != id -> item
                enabled && !item.available -> item
                else -> item.copy(enabled = enabled)
            }
        }
    }

    /** 在草稿中把 source 上移（offset < 0）或下移（offset > 0） */
    fun moveQuerySource(id: String, offset: Int) {
        val items = querySourceDraft.toMutableList()
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = index + offset
        if (target < 0 || target >= items.size) return
        val item = items.removeAt(index)
        items.add(target, item)
        querySourceDraft = items
    }

    /** 使用最近一次服务端默认顺序重建草稿，只启用默认列表内的可用 source */
    fun restoreDefaultQuerySources() {
        val state = queryRepository.sourceState.value
        if (querySourceDraftBackendId == null || querySourceDraftBackendId != state.backendId) return
        val defaults = state.defaultSources
        if (defaults.isEmpty()) return
        val itemsById = querySourceDraft.associateBy { it.id }
        val head = defaults.mapNotNull { itemsById[it] }
        val tail = querySourceDraft.filterNot { it.id in defaults }
        querySourceDraft = (head + tail).map { item ->
            item.copy(enabled = item.id in defaults && item.available)
        }
    }

    /** 保存草稿。@return false 表示没有任何可用 source 处于启用状态，保存被拒绝 */
    fun saveQuerySources(): Boolean {
        val saved = queryRepository.saveSourceSelection(
            querySourceDraft,
            querySourceDraftBackendId,
        )
        if (saved) {
            closeQuerySourceSettings()
        }
        return saved
    }

    /** 重试拉取 source 清单；刷新成功且 BottomSheet 仍打开时把清单填入空草稿 */
    fun retryQuerySourceRefresh() {
        val backendId = querySourceDraftBackendId ?: return
        viewModelScope.launch {
            val result = queryRepository.refreshSources(backendId)
            val state = queryRepository.sourceState.value
            if (
                result.isSuccess &&
                showQuerySourceSheet &&
                querySourceDraft.isEmpty() &&
                querySourceDraftBackendId == backendId &&
                state.backendId == backendId
            ) {
                querySourceDraft = state.items
            }
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
