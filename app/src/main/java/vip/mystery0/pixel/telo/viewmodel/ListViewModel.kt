package vip.mystery0.pixel.telo.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import vip.mystery0.pixel.telo.data.repository.UserListRepository

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

    /** 添加表单：号码输入 */
    var inputPhone by mutableStateOf("")

    /** 添加表单：是否前缀匹配 */
    var inputIsPrefix by mutableStateOf(false)

    /** 添加表单：是否标签匹配 */
    var inputTagMatch by mutableStateOf(false)

    /** 添加表单：是否归属地匹配 */
    var inputLocationMatch by mutableStateOf(false)

    /** 当前是否启用了不联网查询，用于提示归属地规则是否失效 */
    var noNetworkQuery by mutableStateOf(prefs.getBoolean("no_network_query", false))
        private set

    /** 添加表单：备注 */
    var inputRemark by mutableStateOf("")

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

    fun openAddSheet() {
        editingEntry = null
        inputPhone = ""
        inputIsPrefix = false
        inputTagMatch = false
        inputLocationMatch = false
        inputRemark = ""
        addErrorMessage = null
        showAddSheet = true
    }

    fun openEditSheet(entry: UserListEntry) {
        editingEntry = entry
        inputPhone = entry.phoneNumber
        inputIsPrefix = entry.isPrefix
        inputTagMatch = entry.tagMatch
        inputLocationMatch = entry.locationMatch
        inputRemark = entry.remark ?: ""
        addErrorMessage = null
        showAddSheet = true
    }

    fun closeAddSheet() {
        showAddSheet = false
    }

    fun clearToast() {
        toastMessage = null
    }

    fun refreshNoNetworkQuery() {
        noNetworkQuery = prefs.getBoolean("no_network_query", false)
    }

    fun updateTagMatch(enabled: Boolean) {
        inputTagMatch = enabled
        if (enabled) {
            inputIsPrefix = false
            inputLocationMatch = false
        }
    }

    fun updateLocationMatch(enabled: Boolean) {
        inputLocationMatch = enabled
        if (enabled) {
            inputIsPrefix = false
            inputTagMatch = false
        }
    }

    fun confirmAdd() {
        val phone = inputPhone.trim()
        if (phone.isBlank()) {
            addErrorMessage = context.getString(R.string.error_phone_empty)
            return
        }
        viewModelScope.launch {
            val oldEntry = editingEntry
            if (oldEntry != null) {
                userListRepository.delete(oldEntry)
            }
            val success =
                userListRepository.add(
                    phone,
                    inputIsPrefix,
                    currentTab,
                    inputRemark,
                    inputTagMatch,
                    inputLocationMatch
                )
            if (success) {
                showAddSheet = false
                editingEntry = null
                toastMessage = context.getString(if (oldEntry == null) R.string.msg_added_to_list else R.string.msg_updated_in_list)
            } else {
                if (oldEntry != null) {
                    userListRepository.add(
                        oldEntry.phoneNumber,
                        oldEntry.isPrefix,
                        oldEntry.listType,
                        oldEntry.remark,
                        oldEntry.tagMatch,
                        oldEntry.locationMatch
                    )
                }
                addErrorMessage = context.getString(R.string.error_phone_already_exists)
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
     * 从首页快捷添加，不校验重复（由 Repository 的 IGNORE 策略处理）。
     * @return true 表示成功插入，false 表示已存在
     */
    suspend fun quickAdd(phoneNumber: String, listType: ListType): Boolean {
        return userListRepository.add(phoneNumber, false, listType, null)
    }
}
