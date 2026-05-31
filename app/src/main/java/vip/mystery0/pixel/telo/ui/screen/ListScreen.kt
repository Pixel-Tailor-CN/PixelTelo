package vip.mystery0.pixel.telo.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import vip.mystery0.pixel.telo.ui.util.formatMills
import vip.mystery0.pixel.telo.viewmodel.ListViewModel

/**
 * 黑白名单管理页面。
 * 包含两个 Tab（黑名单 / 白名单），通过 HorizontalPager 切换内容。
 * FAB 弹出 BottomSheet 添加条目，支持精确匹配与前缀匹配。
 */
@Composable
fun ListScreen(viewModel: ListViewModel) {
    val context = LocalContext.current
    val blackList by viewModel.blackList.collectAsState()
    val whiteList by viewModel.whiteList.collectAsState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    // 监听 viewModel.showAddSheet 变化
    LaunchedEffect(viewModel.showAddSheet) {
        if (viewModel.showAddSheet) {
            showSheet = true
        } else if (showSheet) {
            sheetState.hide()
            showSheet = false
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

    val tabs = listOf(ListType.BLACK, ListType.WHITE)
    val tabLabels =
        listOf(stringResource(R.string.tab_blacklist), stringResource(R.string.tab_whitelist))
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // Pager 页面变化时同步 ViewModel 的 currentTab 状态
    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectTab(tabs[pagerState.currentPage])
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNoNetworkQuery()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openAddSheet() }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_add_entry)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
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
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val list = if (page == 0) blackList else whiteList
                UserListContent(
                    entries = list,
                    viewModel = viewModel,
                    noNetworkQuery = viewModel.noNetworkQuery
                )
            }
        }
    }

    // 添加条目 BottomSheet
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeAddSheet() },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 根据当前 Tab 显示对应标题
                Text(
                    if (viewModel.editingEntry != null) {
                        if (viewModel.currentTab == ListType.BLACK) stringResource(R.string.title_add_to_blacklist).replace(
                            "加入",
                            "编辑"
                        ).replace(
                            "Add to",
                            "Edit"
                        ) else stringResource(R.string.title_add_to_whitelist).replace(
                            "加入",
                            "编辑"
                        ).replace("Add to", "Edit")
                    } else {
                        if (viewModel.currentTab == ListType.BLACK) stringResource(R.string.title_add_to_blacklist) else stringResource(
                            R.string.title_add_to_whitelist
                        )
                    },
                    style = MaterialTheme.typography.titleLarge
                )

                // 号码输入框：根据是否前缀匹配展示不同的提示文字
                OutlinedTextField(
                    value = viewModel.inputPhone,
                    onValueChange = {
                        viewModel.inputPhone = it
                    },
                    label = {
                        Text(
                            if (viewModel.inputTagMatch) stringResource(R.string.label_tag_name)
                            else if (viewModel.inputLocationMatch) stringResource(R.string.label_location_name)
                            else stringResource(R.string.label_phone_number)
                        )
                    },
                    placeholder = {
                        Text(
                            when {
                                viewModel.inputTagMatch -> stringResource(R.string.hint_tag_example)
                                viewModel.inputLocationMatch -> stringResource(R.string.hint_location_example)
                                viewModel.inputIsPrefix -> stringResource(R.string.hint_prefix_example)
                                else -> stringResource(R.string.hint_exact_example)
                            }
                        )
                    },
                    isError = viewModel.addErrorMessage != null,
                    supportingText = viewModel.addErrorMessage?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 前缀匹配开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            stringResource(R.string.label_prefix_match),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.summary_prefix_match),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.inputIsPrefix,
                        onCheckedChange = { viewModel.inputIsPrefix = it },
                        enabled = !viewModel.inputTagMatch && !viewModel.inputLocationMatch
                    )
                }

                // 标签匹配开关：白名单用于按标签放行，黑名单用于按标签强制挂断。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            stringResource(R.string.label_tag_match),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(
                                if (viewModel.currentTab == ListType.BLACK) {
                                    R.string.summary_black_tag_match
                                } else {
                                    R.string.summary_tag_match
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.inputTagMatch,
                        onCheckedChange = { viewModel.updateTagMatch(it) }
                    )
                }

                if (viewModel.currentTab == ListType.BLACK && viewModel.inputTagMatch) {
                    Text(
                        stringResource(R.string.msg_black_tag_match_force_block),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                    ) {
                        Text(
                            stringResource(R.string.label_location_match),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(
                                if (viewModel.currentTab == ListType.BLACK) {
                                    R.string.summary_black_location_match
                                } else {
                                    R.string.summary_location_match
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.inputLocationMatch,
                        onCheckedChange = { viewModel.updateLocationMatch(it) }
                    )
                }

                if (viewModel.inputLocationMatch) {
                    Text(
                        stringResource(R.string.msg_location_match_requires_network),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // 备注输入框（可选）
                OutlinedTextField(
                    value = viewModel.inputRemark,
                    onValueChange = { viewModel.inputRemark = it },
                    label = { Text(stringResource(R.string.label_remark_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 取消 / 删除 / 确认 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (viewModel.editingEntry != null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.requestDelete(viewModel.editingEntry!!)
                                viewModel.closeAddSheet()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error
                            )
                        ) { Text(stringResource(R.string.action_delete)) }
                    }
                    OutlinedButton(
                        onClick = { viewModel.closeAddSheet() },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = { viewModel.confirmAdd() },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_confirm)) }
                }
            }
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

/**
 * 列表内容区域：无条目时显示占位文字，有条目时渲染可滑动删除的卡片列表。
 */
@Composable
private fun UserListContent(
    entries: List<UserListEntry>,
    viewModel: ListViewModel,
    noNetworkQuery: Boolean,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .height(360.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.list_no_entries),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries, key = { it.id }) { entry ->
                UserListEntryItem(
                    entry = entry,
                    invalid = entry.locationMatch && noNetworkQuery,
                    onClick = { viewModel.openEditSheet(entry) }
                )
            }
        }
    }
}

/**
 * 单条名单条目卡片：显示号码（前缀加 * 后缀）、添加时间、备注及前缀/标签标签。
 */
@Composable
private fun UserListEntryItem(entry: UserListEntry, invalid: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标签匹配显示标签名，前缀匹配号码显示为 "400*"，精确匹配显示原始号码
            val displayNumber = when {
                entry.locationMatch -> entry.phoneNumber
                entry.tagMatch -> entry.phoneNumber
                entry.isPrefix -> "${entry.phoneNumber}*"
                else -> entry.phoneNumber
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(displayNumber, style = MaterialTheme.typography.titleMedium)
                    if (entry.locationMatch) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.label_location_match),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    } else if (entry.tagMatch) {
                        val isBlackTagRule = entry.listType == ListType.BLACK
                        Surface(
                            color = if (isBlackTagRule) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer
                            },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = stringResource(R.string.label_tag_match),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isBlackTagRule) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (entry.isPrefix) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = stringResource(R.string.label_prefix_match),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (invalid) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = stringResource(R.string.label_rule_invalid),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    formatMills(entry.addedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 备注不为空时显示备注文字
            if (!entry.remark.isNullOrBlank()) {
                Text(
                    text = "${stringResource(R.string.label_remark)}${entry.remark}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
