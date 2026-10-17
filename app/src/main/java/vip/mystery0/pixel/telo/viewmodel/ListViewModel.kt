package vip.mystery0.pixel.telo.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.PhoneNumberRuleMatcher
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import vip.mystery0.pixel.telo.data.repository.UserListRepository

/**
 * 添加/编辑名单规则时的类型。
 * UI 层的单选概念，保存时映射回 UserListEntry 的互斥布尔字段。
 */
enum class RuleType {
    /** 号码匹配（精确或前缀） */
    NUMBER,

    /** 标签匹配 */
    TAG,

    /** 归属地匹配 */
    LOCATION,
}

private data class RuleDraft(
    val value: String = "",
    val isPrefix: Boolean = false,
    val tagContains: Boolean = false,
    val remark: String = "",
)

data class RuleSaveRequest(
    val ruleType: RuleType,
    val listType: ListType,
    val value: String,
    val isPrefix: Boolean,
    val tagContains: Boolean,
    val forceBlock: Boolean,
    val remark: String,
    val existingEntry: UserListEntry?,
) {
    /** 仅用于说明确认范围；保存仍将原始输入交给 Repository 归一化一次。 */
    val scopeDisplayValue: String
        get() = if (ruleType == RuleType.NUMBER && isPrefix) {
            PhoneNumberRuleMatcher.normalizeRuleForStorage(value)
        } else {
            value
        }
}

class ListViewModel : ViewModel(), KoinComponent {
    companion object {
        private const val TAG = "ListViewModel"
    }

    private val userListRepository: UserListRepository by inject()
    private val context: Context by inject()
    private val prefs: SharedPreferences by inject()

    /** 当前选中的 Tab：BLACK 或 WHITE */
    var currentTab by mutableStateOf(ListType.BLACK)
        private set

    /** 是否展示"添加条目" BottomSheet */
    var showAddSheet by mutableStateOf(false)
        private set

    /** 当前正在编辑的条目（null表示新增） */
    var editingEntry by mutableStateOf<UserListEntry?>(null)
        private set

    /** 是否展示删除确认对话框 */
    var showDeleteDialog by mutableStateOf(false)
        private set

    /** 待删除的条目 */
    var pendingDeleteEntry by mutableStateOf<UserListEntry?>(null)
        private set

    /** 当前编辑器规则类型；新增时默认选择号码，编辑时固定为已有条目类型。 */
    var inputRuleType by mutableStateOf(RuleType.NUMBER)
        private set

    private var numberDraft by mutableStateOf(RuleDraft())
    private var tagDraft by mutableStateOf(RuleDraft())
    private var locationDraft by mutableStateOf(RuleDraft())

    /** 各匹配类型独立保留输入、匹配方式和备注。 */
    var inputPhone: String
        get() = currentDraft().value
        set(value) = updateCurrentDraft { it.copy(value = value) }

    var inputIsPrefix: Boolean
        get() = currentDraft().isPrefix
        set(value) = updateCurrentDraft { it.copy(isPrefix = value) }

    var inputTagContains: Boolean
        get() = currentDraft().tagContains
        set(value) = updateCurrentDraft { it.copy(tagContains = value) }

    /** 强制拦截是当前规则共用设置，新建默认开启。 */
    var inputForceBlock by mutableStateOf(true)

    var inputRemark: String
        get() = currentDraft().remark
        set(value) = updateCurrentDraft { it.copy(remark = value) }

    /** 保存进行中时锁定表单并忽略重复提交。 */
    var isSaving by mutableStateOf(false)
        private set

    /** 多号码范围规则待用户确认的保存快照。 */
    var pendingSaveConfirmation by mutableStateOf<RuleSaveRequest?>(null)
        private set

    /** 当前是否启用了不联网查询，用于提示归属地规则是否失效 */
    var noNetworkQuery by mutableStateOf(prefs.getBoolean("no_network_query", false))
        private set

    /** 添加错误提示（null 表示无错误） */
    var addErrorMessage by mutableStateOf<String?>(null)
        private set

    /** Toast 消息（消费后清空） */
    var toastMessage by mutableStateOf<String?>(null)
        private set

    val blackList: StateFlow<List<UserListEntry>> = userListRepository.observeBlackList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val whiteList: StateFlow<List<UserListEntry>> = userListRepository.observeWhiteList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(type: ListType) {
        currentTab = type
    }

    /** 打开新增流程时清空各类型草稿，并默认选择号码规则。 */
    fun openAddSheet() {
        if (isSaving) return
        editingEntry = null
        inputRuleType = RuleType.NUMBER
        numberDraft = RuleDraft()
        tagDraft = RuleDraft()
        locationDraft = RuleDraft()
        inputForceBlock = true
        addErrorMessage = null
        pendingSaveConfirmation = null
        refreshNoNetworkQuery()
        showAddSheet = true
    }

    /** 打开编辑流程时恢复已有值，规则类型固定。 */
    fun openEditSheet(entry: UserListEntry) {
        if (isSaving) return
        editingEntry = entry
        inputRuleType = when {
            entry.tagMatch -> RuleType.TAG
            entry.locationMatch -> RuleType.LOCATION
            else -> RuleType.NUMBER
        }
        numberDraft = RuleDraft()
        tagDraft = RuleDraft()
        locationDraft = RuleDraft()
        updateCurrentDraft {
            RuleDraft(
                value = entry.phoneNumber,
                isPrefix = entry.isPrefix,
                tagContains = entry.tagContains,
                remark = entry.remark.orEmpty(),
            )
        }
        inputForceBlock = entry.forceBlock
        addErrorMessage = null
        pendingSaveConfirmation = null
        refreshNoNetworkQuery()
        showAddSheet = true
    }

    /** 新增时切换规则类型；编辑中的类型保持固定。 */
    fun selectRuleType(type: RuleType) {
        if (isSaving || editingEntry != null) return
        inputRuleType = type
        addErrorMessage = null
    }

    private fun currentDraft(): RuleDraft = when (inputRuleType) {
        RuleType.NUMBER -> numberDraft
        RuleType.TAG -> tagDraft
        RuleType.LOCATION -> locationDraft
    }

    private fun updateCurrentDraft(update: (RuleDraft) -> RuleDraft) {
        when (inputRuleType) {
            RuleType.NUMBER -> numberDraft = update(numberDraft)
            RuleType.TAG -> tagDraft = update(tagDraft)
            RuleType.LOCATION -> locationDraft = update(locationDraft)
        }
        addErrorMessage = null
    }

    fun closeAddSheet() {
        if (isSaving) return
        pendingSaveConfirmation = null
        showAddSheet = false
    }

    fun clearToast() {
        toastMessage = null
    }

    fun refreshNoNetworkQuery() {
        noNetworkQuery = prefs.getBoolean("no_network_query", false)
    }

    /** 校验当前草稿；多号码范围规则先展示影响确认，再执行写入。 */
    fun requestSave() {
        if (isSaving || pendingSaveConfirmation != null) return
        val type = inputRuleType
        val phone = inputPhone.trim()
        if (phone.isBlank()) {
            addErrorMessage = context.getString(
                when (type) {
                    RuleType.NUMBER -> R.string.error_phone_empty
                    RuleType.TAG -> R.string.error_tag_empty
                    RuleType.LOCATION -> R.string.error_location_empty
                }
            )
            return
        }
        if (type == RuleType.NUMBER && !PhoneNumberRuleMatcher.isValidRule(phone)) {
            addErrorMessage = context.getString(R.string.error_phone_invalid)
            return
        }
        val request = RuleSaveRequest(
            ruleType = type,
            listType = editingEntry?.listType ?: currentTab,
            value = phone,
            isPrefix = type == RuleType.NUMBER && inputIsPrefix,
            tagContains = type == RuleType.TAG && inputTagContains,
            forceBlock = (editingEntry?.listType
                ?: currentTab) == ListType.BLACK && inputForceBlock,
            remark = inputRemark,
            existingEntry = editingEntry,
        )
        if (request.ruleType != RuleType.NUMBER || request.isPrefix) {
            pendingSaveConfirmation = request
        } else {
            save(request)
        }
    }

    fun cancelPendingSave() {
        pendingSaveConfirmation = null
    }

    fun confirmPendingSave() {
        val request = pendingSaveConfirmation ?: return
        if (isSaving) return
        pendingSaveConfirmation = null
        save(request)
    }

    private fun save(request: RuleSaveRequest) {
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            try {
                val success = request.existingEntry?.let { existing ->
                    userListRepository.update(
                        existing = existing,
                        phoneNumber = request.value,
                        isPrefix = request.isPrefix,
                        remark = request.remark,
                        tagMatch = request.ruleType == RuleType.TAG,
                        locationMatch = request.ruleType == RuleType.LOCATION,
                        forceBlock = request.forceBlock,
                        tagContains = request.tagContains,
                    )
                } ?: userListRepository.add(
                    phoneNumber = request.value,
                    isPrefix = request.isPrefix,
                    listType = request.listType,
                    remark = request.remark,
                    tagMatch = request.ruleType == RuleType.TAG,
                    locationMatch = request.ruleType == RuleType.LOCATION,
                    forceBlock = request.forceBlock,
                    tagContains = request.tagContains,
                )
                if (success) {
                    showAddSheet = false
                    editingEntry = null
                    toastMessage = context.getString(
                        if (request.existingEntry == null) {
                            R.string.msg_added_to_list
                        } else {
                            R.string.msg_updated_in_list
                        }
                    )
                } else {
                    addErrorMessage = context.getString(R.string.error_phone_already_exists)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Save failed (${error.javaClass.simpleName})")
                addErrorMessage = context.getString(R.string.error_list_save_failed)
            } finally {
                isSaving = false
            }
        }
    }

    fun requestDelete(entry: UserListEntry) {
        pendingDeleteEntry = entry
        showDeleteDialog = true
    }

    fun cancelDelete() {
        showDeleteDialog = false
        pendingDeleteEntry = null
    }

    fun confirmDelete() {
        val entry = pendingDeleteEntry ?: return
        viewModelScope.launch {
            try {
                userListRepository.delete(entry)
                showDeleteDialog = false
                pendingDeleteEntry = null
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
            }
        }
    }

    fun delete(entry: UserListEntry) {
        viewModelScope.launch {
            try {
                userListRepository.delete(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
            }
        }
    }

    /**
     * 从首页快捷添加，由 Repository 校验号码并处理重复。
     * @return true 表示成功插入，false 表示输入无效或已存在
     */
    suspend fun quickAdd(phoneNumber: String, listType: ListType): Boolean {
        return userListRepository.add(phoneNumber, false, listType, null)
    }
}
