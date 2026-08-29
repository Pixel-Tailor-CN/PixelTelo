package vip.mystery0.pixel.telo.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.repository.LocalLabelWriteResult
import vip.mystery0.pixel.telo.data.repository.LocalNumberLabelRepository

/** 输入框最多展示 41 个字符，便于用户看到超过 40 的错误。 */
internal const val LOCAL_LABEL_MAX_VISIBLE_INPUT = 41

/** 与 Repository 一致的标签最大长度，最终规则仍由 Repository 决定。 */
internal const val LOCAL_LABEL_MAX_LENGTH = 40

/** 观察尚未完成时排队的编辑动作，避免 Task 5 同步 observe 后丢失点击。 */
enum class PendingLocalLabelAction {
    NONE,
    EDIT,
    DELETE,
}

/** 单号码本地标签编辑的稳定 UI 状态。 */
data class LocalLabelEditorState(
    val phoneNumber: String? = null,
    val currentLabel: String? = null,
    val draft: String = "",
    val observing: Boolean = false,
    val editorVisible: Boolean = false,
    val deleteConfirmationVisible: Boolean = false,
    val saving: Boolean = false,
    val error: LocalLabelEditorError? = null,
    val pendingAction: PendingLocalLabelAction = PendingLocalLabelAction.NONE,
    /** 用户已改过草稿；为 true 时后续观察结果不得覆盖 draft。 */
    val draftDirty: Boolean = false,
)

/** 可映射到本地化文案的编辑错误，不直接展示异常正文。 */
enum class LocalLabelEditorError {
    INVALID_NUMBER,
    LABEL_TOO_LONG,
    SAVE_FAILED,
}

/**
 * 详情页与统一管理页共用的单号码标签编辑器。
 *
 * 观察与写入都走 [LocalNumberLabelRepository]；日志不得包含号码或标签正文。
 */
class LocalNumberLabelEditorViewModel : ViewModel(), KoinComponent {
    companion object {
        private const val TAG = "LocalNumberLabelEditor"
    }

    private val repository: LocalNumberLabelRepository by inject()

    private val _state = MutableStateFlow(LocalLabelEditorState())
    val state: StateFlow<LocalLabelEditorState> = _state.asStateFlow()

    private var observeJob: Job? = null

    /** 观察指定号码的当前标签；切换号码时关闭未完成的编辑弹窗并丢弃排队动作。 */
    fun observe(phoneNumber: String) {
        val switchingTarget = _state.value.phoneNumber != phoneNumber
        observeJob?.cancel()
        _state.update { current ->
            current.copy(
                phoneNumber = phoneNumber,
                observing = true,
                currentLabel = if (switchingTarget) null else current.currentLabel,
                draft = if (switchingTarget) "" else current.draft,
                editorVisible = if (switchingTarget) false else current.editorVisible,
                deleteConfirmationVisible = if (switchingTarget) {
                    false
                } else {
                    current.deleteConfirmationVisible
                },
                saving = if (switchingTarget) false else current.saving,
                error = if (switchingTarget) null else current.error,
                pendingAction = if (switchingTarget) {
                    PendingLocalLabelAction.NONE
                } else {
                    current.pendingAction
                },
                draftDirty = if (switchingTarget) false else current.draftDirty,
            )
        }
        observeJob = viewModelScope.launch {
            repository.observe(phoneNumber)
                .catch {
                    Log.w(TAG, "Local label observation failed")
                    emit(null)
                }
                .collect { entity ->
                    val label = entity?.label
                    _state.update { current ->
                        applyObserveEmission(current, label)
                    }
                }
        }
    }

    /** 打开编辑框并预填当前标签；观察未完成时排队，首发后再打开。 */
    fun openEditor() {
        _state.update { current ->
            if (current.observing) {
                current.copy(pendingAction = PendingLocalLabelAction.EDIT)
            } else {
                current.copy(
                    editorVisible = true,
                    deleteConfirmationVisible = false,
                    draft = current.currentLabel.orEmpty(),
                    saving = false,
                    error = null,
                    pendingAction = PendingLocalLabelAction.NONE,
                    draftDirty = false,
                )
            }
        }
    }

    /**
     * 更新草稿。
     *
     * 只截断到 41 个字符，允许用户看到超过 40 的错误；是否写入仍由 Repository 决定。
     */
    fun updateDraft(value: String) {
        val draft = value.take(LOCAL_LABEL_MAX_VISIBLE_INPUT)
        _state.update { current ->
            current.copy(
                draft = draft,
                draftDirty = true,
                error = if (draft.length > LOCAL_LABEL_MAX_LENGTH) {
                    LocalLabelEditorError.LABEL_TOO_LONG
                } else {
                    null
                },
            )
        }
    }

    /** 保存草稿；成功关闭编辑框，校验或写入失败则保持打开。 */
    fun save() {
        val current = _state.value
        val phoneNumber = current.phoneNumber ?: return
        if (current.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val result = repository.set(phoneNumber, current.draft)
            _state.update { state ->
                when (result) {
                    LocalLabelWriteResult.Created,
                    LocalLabelWriteResult.Updated,
                    LocalLabelWriteResult.Deleted,
                    LocalLabelWriteResult.Unchanged -> state.copy(
                        editorVisible = false,
                        saving = false,
                        error = null,
                        draft = "",
                        draftDirty = false,
                    )

                    LocalLabelWriteResult.InvalidNumber -> state.copy(
                        saving = false,
                        error = LocalLabelEditorError.INVALID_NUMBER,
                    )

                    LocalLabelWriteResult.LabelTooLong -> state.copy(
                        saving = false,
                        error = LocalLabelEditorError.LABEL_TOO_LONG,
                    )

                    is LocalLabelWriteResult.Failure -> {
                        Log.w(TAG, "Local label save failed")
                        state.copy(
                            saving = false,
                            error = LocalLabelEditorError.SAVE_FAILED,
                        )
                    }
                }
            }
        }
    }

    /** 打开删除确认框；观察未完成时排队，首发后再打开。 */
    fun requestDelete() {
        _state.update { current ->
            if (current.observing) {
                current.copy(pendingAction = PendingLocalLabelAction.DELETE)
            } else {
                current.copy(
                    editorVisible = false,
                    deleteConfirmationVisible = true,
                    error = null,
                    pendingAction = PendingLocalLabelAction.NONE,
                )
            }
        }
    }

    /** 确认删除当前号码的本地标签。 */
    fun confirmDelete() {
        val phoneNumber = _state.value.phoneNumber ?: return
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val result = repository.delete(phoneNumber)
            _state.update { state ->
                when (result) {
                    LocalLabelWriteResult.Deleted,
                    LocalLabelWriteResult.Unchanged -> state.copy(
                        deleteConfirmationVisible = false,
                        saving = false,
                        error = null,
                    )

                    LocalLabelWriteResult.InvalidNumber -> state.copy(
                        saving = false,
                        error = LocalLabelEditorError.INVALID_NUMBER,
                    )

                    is LocalLabelWriteResult.Failure -> {
                        Log.w(TAG, "Local label delete failed")
                        state.copy(
                            saving = false,
                            error = LocalLabelEditorError.SAVE_FAILED,
                        )
                    }

                    LocalLabelWriteResult.Created,
                    LocalLabelWriteResult.Updated,
                    LocalLabelWriteResult.LabelTooLong -> state.copy(saving = false)
                }
            }
        }
    }

    /** 关闭编辑框和删除确认，不取消当前号码观察。 */
    fun close() {
        _state.update { current ->
            current.copy(
                editorVisible = false,
                deleteConfirmationVisible = false,
                saving = false,
                error = null,
                pendingAction = PendingLocalLabelAction.NONE,
                draftDirty = false,
            )
        }
    }

    /** 详情真正关闭时取消观察并恢复初始状态，供管理页继续复用同一实例。 */
    fun clearTarget() {
        observeJob?.cancel()
        observeJob = null
        _state.value = LocalLabelEditorState()
    }

    /**
     * 处理观察结果。
     *
     * 首发结束 observing 并执行排队动作；之后仅在编辑框打开且草稿未脏时同步 draft。
     */
    private fun applyObserveEmission(
        current: LocalLabelEditorState,
        label: String?,
    ): LocalLabelEditorState {
        val pending = if (current.observing) {
            current.pendingAction
        } else {
            PendingLocalLabelAction.NONE
        }
        return when (pending) {
            PendingLocalLabelAction.EDIT -> current.copy(
                currentLabel = label,
                observing = false,
                editorVisible = true,
                deleteConfirmationVisible = false,
                draft = label.orEmpty(),
                saving = false,
                error = null,
                pendingAction = PendingLocalLabelAction.NONE,
                draftDirty = false,
            )

            PendingLocalLabelAction.DELETE -> current.copy(
                currentLabel = label,
                observing = false,
                editorVisible = false,
                deleteConfirmationVisible = true,
                error = null,
                pendingAction = PendingLocalLabelAction.NONE,
            )

            PendingLocalLabelAction.NONE -> {
                val shouldSyncDraft = current.editorVisible && !current.draftDirty
                current.copy(
                    currentLabel = label,
                    observing = false,
                    draft = if (shouldSyncDraft) label.orEmpty() else current.draft,
                    pendingAction = PendingLocalLabelAction.NONE,
                )
            }
        }
    }
}
