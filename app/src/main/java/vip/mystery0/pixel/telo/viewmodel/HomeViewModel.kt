package vip.mystery0.pixel.telo.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.FeedbackStatus
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import vip.mystery0.pixel.telo.data.remote.QueryResponse
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.FeedbackSubmitResult
import vip.mystery0.pixel.telo.data.repository.QueryRepository
import vip.mystery0.pixel.telo.data.repository.QuerySourceState
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository
import vip.mystery0.pixel.telo.data.repository.SyncRepository
import vip.mystery0.pixel.telo.data.repository.UserListRepository

/** 联网重查的 UI 状态 */
sealed interface RetryQueryState {
    data object Idle : RetryQueryState
    data class Loading(val call: BlockedCall) : RetryQueryState
    data class Success(val call: BlockedCall, val response: QueryResponse) : RetryQueryState
    data class Failure(val call: BlockedCall, val message: String) : RetryQueryState
}

/** 查询结果反馈提交的 UI 状态 */
sealed interface FeedbackSubmissionState {
    data object Idle : FeedbackSubmissionState
    data class Submitting(val callId: Long) : FeedbackSubmissionState
    data class Failure(val callId: Long, val message: String) : FeedbackSubmissionState
}

enum class CurrentListState {
    NONE,
    BLACK,
    WHITE,
    BOTH,
}

data class BlockedCallListItem(
    val call: BlockedCall,
    val currentListState: CurrentListState,
)

class HomeViewModel() : ViewModel(), KoinComponent {
    private val repository: BlockedCallRepository by inject()
    private val syncRepository: SyncRepository by inject()
    private val spamNumberRepository: SpamNumberRepository by inject()
    private val userListRepository: UserListRepository by inject()
    private val queryRepository: QueryRepository by inject()
    private val context: Context by inject()

    /** 联网查询 source 配置状态，用于首页“已启用 source 下线”提示 */
    val sourceState: StateFlow<QuerySourceState> = queryRepository.sourceState

    init {
        // 应用启动时后台刷新一次 source 清单，不阻塞首页首帧；失败沿用缓存
        viewModelScope.launch {
            queryRepository.refreshSources()
        }
    }

    val blockedCalls: StateFlow<List<BlockedCall>> = repository.allBlockedCalls
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val blockedCallItems: StateFlow<List<BlockedCallListItem>> = combine(
        repository.allBlockedCalls,
        userListRepository.observeBlackList(),
        userListRepository.observeWhiteList()
    ) { calls, blackList, whiteList ->
        buildBlockedCallListItems(calls, blackList, whiteList)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
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
                // 查询成功后立即写回 source 与反馈 token，用户不写备注也不丢失反馈凭证
                val updated = repository.attachQueryResult(call, response)
                _retryQueryState.value = RetryQueryState.Success(updated, response)
            } catch (e: Exception) {
                _retryQueryState.value = RetryQueryState.Failure(
                    call,
                    e.message ?: context.getString(R.string.title_query_failed)
                )
            }
        }
    }

    /** 将重查结果写入备注并关闭对话框 */
    fun writeQueryResultToRemark(call: BlockedCall, remark: String, label: String) {
        viewModelScope.launch {
            repository.update(call.copy(remark = remark, label = label))
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

    /** 点击记录卡片弹出的目标（非 null 时展示 BottomSheet） */
    var quickAddCall by mutableStateOf<BlockedCall?>(null)
        private set

    fun openQuickAdd(call: BlockedCall) {
        quickAddCall = call
        feedbackSubmissionState = FeedbackSubmissionState.Idle
    }

    fun closeQuickAdd() {
        quickAddCall = null
        feedbackSubmissionState = FeedbackSubmissionState.Idle
    }

    /** 反馈提交状态，驱动记录详情中的反馈按钮 */
    var feedbackSubmissionState by mutableStateOf<FeedbackSubmissionState>(
        FeedbackSubmissionState.Idle
    )
        private set

    /**
     * 提交查询结果反馈；positive=true 表示“结果准确”。
     * 终态写入 Room；网络异常等可重试错误只更新 UI 状态，保持 PENDING 允许重试。
     */
    fun submitFeedback(call: BlockedCall, positive: Boolean) {
        val token = call.feedbackToken ?: return
        if (feedbackSubmissionState is FeedbackSubmissionState.Submitting) return
        viewModelScope.launch {
            feedbackSubmissionState = FeedbackSubmissionState.Submitting(call.id)
            val result = queryRepository.submitFeedback(token, positive)
            val newStatus = when (result) {
                FeedbackSubmitResult.Accepted ->
                    if (positive) FeedbackStatus.POSITIVE else FeedbackStatus.NEGATIVE

                FeedbackSubmitResult.AlreadySubmitted -> FeedbackStatus.ALREADY_SUBMITTED
                FeedbackSubmitResult.Expired -> FeedbackStatus.EXPIRED
                FeedbackSubmitResult.Invalid -> FeedbackStatus.INVALID
                is FeedbackSubmitResult.RetryableFailure -> {
                    feedbackSubmissionState = FeedbackSubmissionState.Failure(
                        call.id,
                        result.message ?: context.getString(R.string.msg_feedback_failed_retry)
                    )
                    return@launch
                }
            }
            val updated = repository.updateFeedbackStatus(call, newStatus)
            // 详情 BottomSheet 正展示同一条记录时同步替换，避免旧对象覆盖新状态
            if (quickAddCall?.id == updated.id) {
                quickAddCall = updated
            }
            feedbackSubmissionState = FeedbackSubmissionState.Idle
        }
    }

    /** 快捷加入黑名单。@return true=成功插入，false=已存在 */
    suspend fun quickAddToBlackList(phone: String): Boolean =
        userListRepository.add(phone, false, ListType.BLACK, null)

    /** 快捷加入白名单。@return true=成功插入，false=已存在 */
    suspend fun quickAddToWhiteList(phone: String): Boolean =
        userListRepository.add(phone, false, ListType.WHITE, null)

    /** 快捷加入标签白名单。@return true=成功插入，false=已存在 */
    suspend fun quickAddTagToWhiteList(tag: String): Boolean =
        userListRepository.add(tag, false, ListType.WHITE, null, tagMatch = true)
}

fun buildBlockedCallListItems(
    calls: List<BlockedCall>,
    blackList: List<UserListEntry>,
    whiteList: List<UserListEntry>,
): List<BlockedCallListItem> {
    return calls.map { call ->
        val inBlackList = blackList.any { it.matchesPhone(call.phoneNumber) }
        val inWhiteList = whiteList.any { it.matchesPhone(call.phoneNumber) }
        val currentListState = when {
            inBlackList && inWhiteList -> CurrentListState.BOTH
            inBlackList -> CurrentListState.BLACK
            inWhiteList -> CurrentListState.WHITE
            else -> CurrentListState.NONE
        }
        BlockedCallListItem(call, currentListState)
    }
}

private fun UserListEntry.matchesPhone(phoneNumber: String): Boolean {
    if (tagMatch || locationMatch) return false
    val rule = this.phoneNumber.trim()
    val phone = phoneNumber.trim()
    if (rule.isBlank() || phone.isBlank()) return false
    return if (isPrefix) {
        phone.startsWith(rule)
    } else {
        phone == rule
    }
}
