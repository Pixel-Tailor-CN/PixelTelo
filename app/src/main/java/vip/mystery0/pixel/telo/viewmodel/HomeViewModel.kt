package vip.mystery0.pixel.telo.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import vip.mystery0.pixel.telo.data.PhoneNumberRuleMatcher
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.FeedbackStatus
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import vip.mystery0.pixel.telo.data.query.OFFICIAL_BACKEND_ID
import vip.mystery0.pixel.telo.data.query.QueryBackendProvider
import vip.mystery0.pixel.telo.data.query.QueryBackendState
import vip.mystery0.pixel.telo.data.query.SelfHostedBlockReason
import vip.mystery0.pixel.telo.data.remote.QueryResponse
import vip.mystery0.pixel.telo.data.preferences.LocalNumberLabelPreferences
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.ContactRepository
import vip.mystery0.pixel.telo.data.repository.FeedbackSubmitResult
import vip.mystery0.pixel.telo.data.repository.LocalNumberLabelRepository
import vip.mystery0.pixel.telo.data.repository.QueryRepository
import vip.mystery0.pixel.telo.data.repository.QuerySourceState
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository
import vip.mystery0.pixel.telo.data.repository.SyncRepository
import vip.mystery0.pixel.telo.data.repository.UserListRepository
import vip.mystery0.pixel.telo.data.repository.classifyNetworkFailure

/** 联网重查的 UI 状态 */
sealed interface RetryQueryState {
    data object Idle : RetryQueryState
    data class Loading(val call: BlockedCall) : RetryQueryState
    data class Success(val call: BlockedCall, val response: QueryResponse) : RetryQueryState
    data class Failure(val call: BlockedCall, val resultType: ResultType?) : RetryQueryState
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

/**
 * 首页可安全展示的自建查询服务告警分类。
 *
 * 该模型只保留稳定的安全分类，避免 UI 持有 URL、Token、Pin、响应正文或异常堆栈。
 */
enum class SelfHostedWarning {
    CONFIGURATION,
    CREDENTIALS,
    TLS,
    SPKI_PIN,
    SERVER_VERSION,
    API_VERSION,
    INSTANCE_CHANGED,
    IDENTITY_HEADERS,
}

data class BlockedCallListItem(
    val call: BlockedCall,
    val currentListState: CurrentListState,
)

private data class UserLists(
    val black: List<UserListEntry>,
    val white: List<UserListEntry>,
)

class HomeViewModel() : ViewModel(), KoinComponent {
    private val repository: BlockedCallRepository by inject()
    private val syncRepository: SyncRepository by inject()
    private val spamNumberRepository: SpamNumberRepository by inject()
    private val userListRepository: UserListRepository by inject()
    private val queryRepository: QueryRepository by inject()
    private val queryBackendProvider: QueryBackendProvider by inject()
    private val contactRepository: ContactRepository by inject()
    private val localNumberLabelRepository: LocalNumberLabelRepository by inject()
    private val localNumberLabelPreferences: LocalNumberLabelPreferences by inject()
    private val context: Context by inject()

    /** 本地号码标签普通展示开关；关闭时列表不订阅窗口标签。 */
    val showLocalNumberLabels: StateFlow<Boolean> = localNumberLabelPreferences.enabled

    private val _localLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val localLabels: StateFlow<Map<String, String>> = _localLabels.asStateFlow()
    private var localLabelLookupJob: Job? = null
    private var loadedPhoneNumbers: Set<String> = emptySet()

    /** 联网查询 source 配置状态，用于首页“已启用 source 下线”提示 */
    val sourceState: StateFlow<QuerySourceState> = queryRepository.sourceState

    /** 只暴露与当前 Ready Backend ID 匹配的 source 告警，切换窗口不会展示旧 Backend 数据。 */
    val unavailableSourceWarning: StateFlow<List<String>> = combine(
        queryBackendProvider.state,
        sourceState,
    ) { backendState, sourceState ->
        val readyState = backendState as? QueryBackendState.Ready
        if (readyState != null && sourceState.backendId == readyState.backendId) {
            sourceState.unavailableEnabledSources
        } else {
            emptyList()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    /**
     * 自建服务被安全阻止时的首页告警。
     *
     * 仅消费 Provider 已发布的持久阻止状态，不进行网络探测或健康轮询。普通网络故障、超时和
     * 服务端瞬时响应错误不会进入该状态，因此不会形成常驻首页告警。
     */
    val selfHostedWarning: StateFlow<SelfHostedWarning?> = queryBackendProvider.state
        .map { state ->
            (state as? QueryBackendState.Blocked)?.reason?.toHomeWarning()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = (queryBackendProvider.state.value as? QueryBackendState.Blocked)
                ?.reason
                ?.toHomeWarning(),
        )

    init {
        // 应用启动时后台刷新一次 source 清单，不阻塞首页首帧；失败沿用缓存
        viewModelScope.launch {
            queryRepository.refreshSources()
        }
        viewModelScope.launch {
            contactRepository.changes.collect {
                resolveLoadedContacts()
            }
        }
        viewModelScope.launch {
            localNumberLabelPreferences.enabled.collect {
                resolveLoadedLocalLabels()
            }
        }
    }

    private val userLists: Flow<UserLists> = combine(
        userListRepository.observeBlackList(),
        userListRepository.observeWhiteList(),
    ) { blackList, whiteList ->
        UserLists(blackList, whiteList)
    }

    private val cachedBlockedCalls = repository.blockedCallsPager.cachedIn(viewModelScope)

    val blockedCallItems: Flow<PagingData<BlockedCallListItem>> =
        cachedBlockedCalls.combine(userLists) { pagingData, lists ->
            pagingData.map { call ->
                buildBlockedCallListItem(call, lists.black, lists.white)
            }
        }.cachedIn(viewModelScope)

    val hasBlockedCallRecords: StateFlow<Boolean> = repository.recordCount
        .map { it > 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()

    private var contactLookupJob: Job? = null

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

    fun updateLoadedPhoneNumbers(phoneNumbers: Set<String>) {
        val sanitized = phoneNumbers.filter { it.isNotBlank() }.toSet()
        if (sanitized == loadedPhoneNumbers) return
        loadedPhoneNumbers = sanitized
        resolveLoadedContacts()
        resolveLoadedLocalLabels()
    }

    fun refreshContactNames() {
        contactRepository.restartObservation()
        resolveLoadedContacts()
    }

    private fun resolveLoadedContacts() {
        contactLookupJob?.cancel()
        if (loadedPhoneNumbers.isEmpty()) {
            _contactNames.value = emptyMap()
            return
        }
        contactLookupJob = viewModelScope.launch {
            delay(100)
            _contactNames.value = contactRepository.resolveNames(loadedPhoneNumbers)
        }
    }

    /** 仅在显示开关开启时观察当前 Paging 窗口的本地标签。 */
    private fun resolveLoadedLocalLabels() {
        localLabelLookupJob?.cancel()
        if (!localNumberLabelPreferences.enabled.value || loadedPhoneNumbers.isEmpty()) {
            _localLabels.value = emptyMap()
            return
        }
        val numbers = loadedPhoneNumbers
        localLabelLookupJob = viewModelScope.launch {
            localNumberLabelRepository.observeLabels(numbers).collect { labels ->
                if (localNumberLabelPreferences.enabled.value && loadedPhoneNumbers == numbers) {
                    _localLabels.value = labels
                }
            }
        }
    }

    private val _retryQueryState = MutableStateFlow<RetryQueryState>(RetryQueryState.Idle)
    val retryQueryState: StateFlow<RetryQueryState> = _retryQueryState.asStateFlow()

    /** 对联网失败记录发起联网重查。 */
    fun retryNetworkQuery(call: BlockedCall) {
        viewModelScope.launch {
            _retryQueryState.value = RetryQueryState.Loading(call)
            val backendResponse = try {
                spamNumberRepository.queryNetwork(call.phoneNumber)
            } catch (exception: TimeoutCancellationException) {
                _retryQueryState.value = RetryQueryState.Failure(
                    call = call,
                    resultType = classifyNetworkFailure(exception),
                )
                return@launch
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _retryQueryState.value = RetryQueryState.Failure(
                    call = call,
                    resultType = classifyNetworkFailure(exception),
                )
                return@launch
            }

            try {
                val response = backendResponse.response
                // 立即写回可信 Backend 归属、source 与反馈 token，避免用户关闭对话框后丢失凭证
                val updated = repository.attachQueryResult(call, backendResponse)
                _retryQueryState.value = RetryQueryState.Success(updated, response)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // 本地持久化失败不归因给远端服务，使用通用失败提示。
                _retryQueryState.value = RetryQueryState.Failure(call = call, resultType = null)
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

    fun deleteAll() {
        viewModelScope.launch {
            closeQuickAdd()
            loadedPhoneNumbers = emptySet()
            _contactNames.value = emptyMap()
            // 只清空当前窗口映射，不得删除本地号码标签
            resolveLoadedLocalLabels()
            repository.deleteAll()
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
        if (call.queryBackendId != OFFICIAL_BACKEND_ID) {
            markFeedbackUnavailableIfNeeded(call)
            return
        }
        if (call.feedbackStatus != FeedbackStatus.PENDING) return
        val token = call.feedbackToken?.takeIf { it.isNotBlank() }
        if (token == null) {
            markFeedbackUnavailableIfNeeded(call)
            return
        }
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

    /** 清理不满足官方反馈门禁的异常旧记录，并同步当前详情实体。 */
    private fun markFeedbackUnavailableIfNeeded(call: BlockedCall) {
        if (call.feedbackStatus == FeedbackStatus.UNAVAILABLE && call.feedbackToken == null) return
        viewModelScope.launch {
            val updated = repository.updateFeedbackStatus(call, FeedbackStatus.UNAVAILABLE)
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

/** 将 Provider 的持久安全阻止原因转换为不含敏感信息的首页展示分类。 */
private fun SelfHostedBlockReason.toHomeWarning(): SelfHostedWarning = when (this) {
    SelfHostedBlockReason.Configuration -> SelfHostedWarning.CONFIGURATION
    SelfHostedBlockReason.Credentials -> SelfHostedWarning.CREDENTIALS
    SelfHostedBlockReason.Tls -> SelfHostedWarning.TLS
    SelfHostedBlockReason.SpkiPin -> SelfHostedWarning.SPKI_PIN
    SelfHostedBlockReason.ServerVersion -> SelfHostedWarning.SERVER_VERSION
    SelfHostedBlockReason.ApiVersion -> SelfHostedWarning.API_VERSION
    SelfHostedBlockReason.InstanceChanged -> SelfHostedWarning.INSTANCE_CHANGED
    SelfHostedBlockReason.IdentityHeaders -> SelfHostedWarning.IDENTITY_HEADERS
}

fun buildBlockedCallListItem(
    call: BlockedCall,
    blackList: List<UserListEntry>,
    whiteList: List<UserListEntry>,
): BlockedCallListItem {
    val inBlackList = blackList.any { it.matchesPhone(call.phoneNumber) }
    val inWhiteList = whiteList.any { it.matchesPhone(call.phoneNumber) }
    val currentListState = when {
        inBlackList && inWhiteList -> CurrentListState.BOTH
        inBlackList -> CurrentListState.BLACK
        inWhiteList -> CurrentListState.WHITE
        else -> CurrentListState.NONE
    }
    return BlockedCallListItem(call, currentListState)
}

private fun UserListEntry.matchesPhone(incomingPhoneNumber: String): Boolean {
    if (tagMatch || locationMatch) return false
    return PhoneNumberRuleMatcher.ruleMatches(
        rule = this.phoneNumber,
        isPrefix = isPrefix,
        phoneNumber = incomingPhoneNumber,
    )
}
