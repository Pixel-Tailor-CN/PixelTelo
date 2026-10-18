package vip.mystery0.pixel.telo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.entity.LocalNumberLabel
import vip.mystery0.pixel.telo.data.repository.LocalNumberLabelRepository

/** 统一管理页的显示状态；标签集合首次发射前保留加载态。 */
data class LocalNumberLabelsUiState(
    val hasLoadedLabels: Boolean = false,
    val hasAnyLabels: Boolean = false,
    val items: List<LocalNumberLabel> = emptyList(),
    val searchQuery: String = "",
)

/**
 * 统一本地号码标签管理页的列表与搜索状态。
 *
 * 仓库已按 `updatedAt DESC` 返回；过滤后保持原排序。不提供任意号码新增。
 */
class LocalNumberLabelsViewModel : ViewModel(), KoinComponent {
    private val repository: LocalNumberLabelRepository by inject()

    private val query = MutableStateFlow("")
    private val allLabels: Flow<List<LocalNumberLabel>?> = repository.observeAll()
        .map<List<LocalNumberLabel>, List<LocalNumberLabel>?> { it }
        .onStart { emit(null) }

    val uiState: StateFlow<LocalNumberLabelsUiState> = combine(
        allLabels,
        query,
    ) { labels, text ->
        val normalizedQuery = text.trim()
        val availableLabels = labels.orEmpty()
        val filteredLabels = if (normalizedQuery.isEmpty()) {
            availableLabels
        } else {
            availableLabels.filter { entry ->
                entry.normalizedPhoneNumber.contains(normalizedQuery, ignoreCase = true) ||
                    entry.label.contains(normalizedQuery, ignoreCase = true)
            }
        }
        LocalNumberLabelsUiState(
            hasLoadedLabels = labels != null,
            hasAnyLabels = labels?.isNotEmpty() == true,
            items = filteredLabels,
            searchQuery = text,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000, replayExpirationMillis = 0),
        LocalNumberLabelsUiState(),
    )

    /** 更新搜索词；匹配时再 trim，输入框保留用户原文。 */
    fun updateQuery(text: String) {
        query.value = text
    }
}
