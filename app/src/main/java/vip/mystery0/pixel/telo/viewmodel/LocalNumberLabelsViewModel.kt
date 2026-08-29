package vip.mystery0.pixel.telo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.entity.LocalNumberLabel
import vip.mystery0.pixel.telo.data.repository.LocalNumberLabelRepository

/**
 * 统一本地号码标签管理页的列表与搜索状态。
 *
 * 仓库已按 `updatedAt DESC` 返回；过滤后保持原排序。不提供任意号码新增。
 */
class LocalNumberLabelsViewModel : ViewModel(), KoinComponent {
    private val repository: LocalNumberLabelRepository by inject()

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    val items: StateFlow<List<LocalNumberLabel>> = combine(
        repository.observeAll(),
        query,
    ) { labels, text ->
        val normalizedQuery = text.trim()
        if (normalizedQuery.isEmpty()) {
            labels
        } else {
            labels.filter { entry ->
                entry.normalizedPhoneNumber.contains(normalizedQuery, ignoreCase = true) ||
                    entry.label.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 更新搜索词；匹配时再 trim，输入框保留用户原文。 */
    fun updateQuery(text: String) {
        query.value = text
    }
}
