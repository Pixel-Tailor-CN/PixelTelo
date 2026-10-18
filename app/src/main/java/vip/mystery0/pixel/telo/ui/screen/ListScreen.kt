package vip.mystery0.pixel.telo.ui.screen

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import vip.mystery0.pixel.telo.ui.components.EmptyState
import vip.mystery0.pixel.telo.ui.components.ListEntryItem
import vip.mystery0.pixel.telo.ui.util.EmptyPagerNestedScrollConnection
import vip.mystery0.pixel.telo.ui.util.rememberPagerBoundaryHandoffConnection
import vip.mystery0.pixel.telo.viewmodel.ListViewModel
import vip.mystery0.pixel.telo.viewmodel.RuleType

/**
 * 黑白名单管理页面。
 * 包含两个 Tab（黑名单 / 白名单），通过 HorizontalPager 切换内容。
 * 标题栏下方提供共享搜索框（仅在名单全部为空时隐藏），FAB 打开单页 BottomSheet，
 * 支持号码、识别标签与归属地规则。
 */
@Composable
fun ListScreen(
    viewModel: ListViewModel,
    parentPagerState: PagerState,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    fun clearListSearchFocus() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    val blackList by viewModel.blackList.collectAsState()
    val whiteList by viewModel.whiteList.collectAsState()
    val scope = rememberCoroutineScope()
    val canHideSheet = rememberUpdatedState(
        !viewModel.isSaving || !viewModel.showAddSheet,
    )
    val confirmSheetValueChange = remember {
        { targetValue: SheetValue ->
            targetValue != SheetValue.Hidden || canHideSheet.value
        }
    }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        confirmValueChange = confirmSheetValueChange,
    )
    var showSheet by remember { mutableStateOf(false) }

    // 监听 viewModel.showAddSheet 变化
    LaunchedEffect(viewModel.showAddSheet) {
        if (viewModel.showAddSheet) {
            clearListSearchFocus()
            showSheet = true
        } else if (showSheet) {
            clearListSearchFocus()
            try {
                sheetState.hide()
            } finally {
                // 遮罩点击、返回或拖动可能取消 hide 动画；关闭请求仍须移除模态窗口。
                // 若取消来自重新打开，则不能让旧协程关闭新表单。
                if (!viewModel.showAddSheet) {
                    clearListSearchFocus()
                    showSheet = false
                }
            }
        }
    }

    // 监听 Toast 消息，展示后立即清空
    LaunchedEffect(viewModel.toastMessage) {
        viewModel.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // 删除确认对话框
    if (viewModel.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(R.string.title_delete_entry)) },
            text = { Text(stringResource(R.string.msg_delete_entry_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    viewModel.pendingSaveConfirmation?.let { confirmation ->
        val scopeDescription = when (confirmation.ruleType) {
            RuleType.NUMBER -> stringResource(
                R.string.msg_confirm_prefix_scope,
                confirmation.scopeDisplayValue,
            )

            RuleType.TAG -> stringResource(
                if (confirmation.tagContains) {
                    R.string.msg_confirm_tag_contains_scope
                } else {
                    R.string.msg_confirm_tag_exact_scope
                },
                confirmation.value,
            )

            RuleType.LOCATION -> stringResource(
                R.string.msg_confirm_location_scope,
                confirmation.value,
            )
        }
        val effectDescription = when {
            confirmation.listType == ListType.WHITE ->
                stringResource(R.string.msg_confirm_whitelist_effect)

            confirmation.forceBlock ->
                stringResource(R.string.msg_confirm_force_block_effect)

            else -> stringResource(R.string.msg_confirm_global_block_effect)
        }
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingSave() },
            title = { Text(stringResource(R.string.title_confirm_rule_scope)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(scopeDescription)
                    Text(effectDescription)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmPendingSave() },
                    enabled = !viewModel.isSaving,
                ) {
                    Text(
                        stringResource(
                            when {
                                confirmation.existingEntry != null -> R.string.action_save
                                confirmation.listType == ListType.BLACK ->
                                    R.string.action_add_to_blacklist

                                else -> R.string.action_add_to_whitelist
                            }
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelPendingSave() },
                    enabled = !viewModel.isSaving,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val tabs = listOf(ListType.BLACK, ListType.WHITE)
    val tabLabels =
        listOf(stringResource(R.string.tab_blacklist), stringResource(R.string.tab_whitelist))
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(viewModel.currentTab).coerceAtLeast(0),
        pageCount = { tabs.size },
    )

    val pendingTabNavigationRequest = viewModel.pendingTabNavigationRequest
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (viewModel.pendingTabNavigationRequest == null) {
                viewModel.selectTab(tabs[page])
            }
        }
    }
    LaunchedEffect(pagerState, pendingTabNavigationRequest?.id) {
        val request = pendingTabNavigationRequest ?: return@LaunchedEffect
        val targetPage = tabs.indexOf(request.listType).coerceAtLeast(0)
        if (pagerState.settledPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
        viewModel.completeTabNavigationRequest(request.id)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNoNetworkQuery()
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    Scaffold(
        floatingActionButton = {
            // 搜索框弹出键盘时把悬浮按钮抬到键盘上方，避免添加入口被输入法遮挡。
            FloatingActionButton(
                onClick = {
                    clearListSearchFocus()
                    viewModel.openAddSheet()
                },
                modifier = Modifier.imePadding(),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_entry)
                )
            }
        }
    ) { _ ->
        // 搜索框只在两个名单都为空时隐藏，避免切换 Tab 时 TabRow 上下跳动。
        val showSearchField = blackList.isNotEmpty() || whiteList.isNotEmpty()
        val hasSearchQuery = viewModel.searchQuery.isNotBlank()
        Column(
            modifier = Modifier.imePadding(),
        ) {
            if (showSearchField) {
                UserListSearchField(
                    query = viewModel.searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    onClear = viewModel::clearSearchQuery,
                )
            }
            // Tab 行：点击时通过动画滚动 Pager
            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, _ ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (tabs[index] == ListType.BLACK) Icons.Default.Block else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(tabLabels[index])
                            }
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(
                        rememberPagerBoundaryHandoffConnection(
                            parentState = parentPagerState,
                            childState = pagerState
                        )
                    ),
                pageNestedScrollConnection = EmptyPagerNestedScrollConnection
            ) { page ->
                val list = if (page == 0) blackList else whiteList
                val query = viewModel.searchQuery
                val visibleEntries = remember(list, query) {
                    if (query.isBlank()) list else list.filter {
                        viewModel.matchesSearch(
                            it,
                            query
                        )
                    }
                }
                UserListContent(
                    entries = visibleEntries,
                    isBlack = page == 0,
                    hasSearchQuery = hasSearchQuery,
                    noNetworkQuery = viewModel.noNetworkQuery,
                    onEntryClick = {
                        clearListSearchFocus()
                        viewModel.openEditSheet(it)
                    },
                    onClearSearch = viewModel::clearSearchQuery,
                )
            }
        }
    }

    // Material3 BottomSheet 已处理导航栏安全区；表单统一避让 IME，正文独立滚动。
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!viewModel.isSaving) {
                    clearListSearchFocus()
                    viewModel.closeAddSheet()
                }
            },
            sheetState = sheetState,
            sheetGesturesEnabled = !viewModel.isSaving,
        ) {
            RuleFormContent(viewModel)
        }
    }
}

/** 单页创建或编辑名单规则；草稿与匹配模式由 ViewModel 按类型分别保存。 */
@Composable
private fun RuleFormContent(viewModel: ListViewModel) {
    val ruleType = viewModel.inputRuleType
    val isBlack = (viewModel.editingEntry?.listType ?: viewModel.currentTab) == ListType.BLACK
    val isEditing = viewModel.editingEntry != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 12.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (isEditing) {
                        stringResource(
                            R.string.title_edit_rule_type,
                            stringResource(ruleType.labelResource()),
                        )
                    } else {
                        stringResource(
                            if (isBlack) R.string.title_add_to_blacklist
                            else R.string.title_add_to_whitelist
                        )
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                if (!isEditing) RuleTypeSelector(viewModel)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = viewModel.inputPhone,
                    onValueChange = { viewModel.inputPhone = it },
                    label = {
                        Text(
                            stringResource(
                                when (ruleType) {
                                    RuleType.NUMBER -> R.string.label_phone_number
                                    RuleType.TAG -> R.string.label_tag_name
                                    RuleType.LOCATION -> R.string.label_location_name
                                }
                            )
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(
                                when {
                                    ruleType == RuleType.TAG -> R.string.hint_tag_example
                                    ruleType == RuleType.LOCATION -> R.string.hint_location_example
                                    viewModel.inputIsPrefix -> R.string.hint_prefix_example
                                    else -> R.string.hint_exact_example
                                }
                            )
                        )
                    },
                    isError = viewModel.addErrorMessage != null,
                    supportingText = viewModel.addErrorMessage?.let { message ->
                        { Text(message) }
                    },
                    singleLine = true,
                    enabled = !viewModel.isSaving,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (ruleType == RuleType.NUMBER) {
                            KeyboardType.Phone
                        } else {
                            KeyboardType.Text
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                when (ruleType) {
                    RuleType.NUMBER -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !viewModel.inputIsPrefix,
                                onClick = { viewModel.inputIsPrefix = false },
                                enabled = !viewModel.isSaving,
                                label = { Text(stringResource(R.string.label_number_mode_full)) },
                            )
                            FilterChip(
                                selected = viewModel.inputIsPrefix,
                                onClick = { viewModel.inputIsPrefix = true },
                                enabled = !viewModel.isSaving,
                                label = { Text(stringResource(R.string.label_number_mode_prefix)) },
                            )
                        }
                        if (viewModel.inputIsPrefix) {
                            Text(
                                stringResource(R.string.summary_prefix_match),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    RuleType.TAG -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !viewModel.inputTagContains,
                                onClick = { viewModel.inputTagContains = false },
                                enabled = !viewModel.isSaving,
                                label = { Text(stringResource(R.string.label_tag_mode_exact)) },
                            )
                            FilterChip(
                                selected = viewModel.inputTagContains,
                                onClick = { viewModel.inputTagContains = true },
                                enabled = !viewModel.isSaving,
                                label = { Text(stringResource(R.string.label_tag_mode_contains)) },
                            )
                        }
                        Text(
                            stringResource(
                                if (viewModel.inputTagContains) R.string.summary_tag_contains
                                else R.string.summary_tag_exact
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    RuleType.LOCATION -> {
                        Text(
                            stringResource(
                                if (viewModel.noNetworkQuery) {
                                    R.string.msg_location_match_disabled
                                } else {
                                    R.string.msg_location_match_requires_network
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (isBlack) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                        ) {
                            Text(
                                stringResource(R.string.label_force_block),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(
                                    if (viewModel.inputForceBlock) {
                                        R.string.summary_force_block_on
                                    } else {
                                        R.string.summary_force_block_off
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = viewModel.inputForceBlock,
                            onCheckedChange = { viewModel.inputForceBlock = it },
                            enabled = !viewModel.isSaving,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = viewModel.inputRemark,
                onValueChange = { viewModel.inputRemark = it },
                label = { Text(stringResource(R.string.label_remark_optional)) },
                singleLine = true,
                enabled = !viewModel.isSaving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )

        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { viewModel.closeAddSheet() },
                enabled = !viewModel.isSaving,
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isEditing) {
                    TextButton(
                        onClick = {
                            viewModel.editingEntry?.let { entry ->
                                viewModel.requestDelete(entry)
                                viewModel.closeAddSheet()
                            }
                        },
                        enabled = !viewModel.isSaving,
                    ) {
                        Text(
                            stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Button(
                    onClick = { viewModel.requestSave() },
                    enabled = viewModel.inputPhone.isNotBlank() &&
                            !viewModel.isSaving &&
                            viewModel.pendingSaveConfirmation == null,
                ) {
                    Text(
                        stringResource(
                            when {
                                isEditing -> R.string.action_save
                                isBlack -> R.string.action_add_to_blacklist
                                else -> R.string.action_add_to_whitelist
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleTypeSelector(viewModel: ListViewModel) {
    val types = listOf(RuleType.NUMBER, RuleType.TAG, RuleType.LOCATION)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = viewModel.inputRuleType == type,
                onClick = { viewModel.selectRuleType(type) },
                enabled = !viewModel.isSaving,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        stringResource(type.labelResource()),
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

private fun RuleType.labelResource(): Int = when (this) {
    RuleType.NUMBER -> R.string.label_rule_type_number
    RuleType.TAG -> R.string.label_rule_type_tag
    RuleType.LOCATION -> R.string.label_rule_type_location
}

/**
 * 列表内容区域：无条目时区分“名单为空”和“搜索无结果”，有条目时渲染名单卡片。
 */
@Composable
private fun UserListContent(
    entries: List<UserListEntry>,
    isBlack: Boolean,
    hasSearchQuery: Boolean,
    noNetworkQuery: Boolean,
    onEntryClick: (UserListEntry) -> Unit,
    onClearSearch: () -> Unit,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .height(360.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasSearchQuery) {
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = stringResource(R.string.msg_no_matching_user_list),
                    description = stringResource(R.string.msg_no_matching_user_list_hint),
                    action = {
                        TextButton(onClick = onClearSearch) {
                            Text(stringResource(R.string.action_clear_search))
                        }
                    },
                )
            } else {
                EmptyState(
                    icon = if (isBlack) Icons.Default.Block else Icons.Default.CheckCircle,
                    title = stringResource(R.string.list_no_entries),
                    description = stringResource(
                        if (isBlack)
                            R.string.list_black_empty_hint else R.string.list_white_empty_hint
                    ),
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 底部留出悬浮按钮的高度，滚动到末尾时最后一条不会被「添加条目」遮挡。
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                ListEntryItem(
                    entry = entry,
                    invalid = entry.locationMatch && noNetworkQuery,
                    onClick = { onEntryClick(entry) }
                )
            }
        }
    }
}

/**
 * 名单搜索框：位于标题栏与 Tab 之间，查询词在两个名单之间共享。
 * 前缀规则在列表里带 * 后缀，这里允许输入星号后再清空，保持与列表显示一致。
 */
@Composable
private fun UserListSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        placeholder = { Text(stringResource(R.string.hint_search_user_list)) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_clear_search),
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}
