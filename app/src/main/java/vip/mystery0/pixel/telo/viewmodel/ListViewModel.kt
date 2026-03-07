package vip.mystery0.pixel.telo.viewmodel

import android.content.Context
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

    /** 当前选中的 Tab：BLACK 或 WHITE */
    var currentTab by mutableStateOf(ListType.BLACK)
        private set

    /** 是否展示"添加条目" BottomSheet */
    var showAddSheet by mutableStateOf(false)
        private set

    /** 添加表单：号码输入 */
    var inputPhone by mutableStateOf("")

    /** 添加表单：是否前缀匹配 */
    var inputIsPrefix by mutableStateOf(false)

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
        inputPhone = ""
        inputIsPrefix = false
        inputRemark = ""
        addErrorMessage = null
        showAddSheet = true
    }

    fun closeAddSheet() {
        showAddSheet = false
    }

    fun clearToast() {
        toastMessage = null
    }

    fun confirmAdd() {
        val phone = inputPhone.trim()
        if (phone.isBlank()) {
            addErrorMessage = context.getString(R.string.error_phone_empty)
            return
        }
        viewModelScope.launch {
            val success = userListRepository.add(phone, inputIsPrefix, currentTab, inputRemark)
            if (success) {
                showAddSheet = false
                toastMessage = context.getString(R.string.msg_added_to_list)
            } else {
                addErrorMessage = context.getString(R.string.error_phone_already_exists)
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
