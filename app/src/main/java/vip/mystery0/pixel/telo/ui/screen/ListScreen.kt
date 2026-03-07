package vip.mystery0.pixel.telo.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import vip.mystery0.pixel.telo.ui.components.SwipeToDeleteContainer
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

    // 监听 Toast 消息，展示后立即清空
    LaunchedEffect(viewModel.toastMessage) {
        viewModel.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    val tabs = listOf(ListType.BLACK, ListType.WHITE)
    val tabLabels =
        listOf(stringResource(R.string.tab_blacklist), stringResource(R.string.tab_whitelist))
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // Pager 页面变化时同步 ViewModel 的 currentTab 状态
    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectTab(tabs[pagerState.currentPage])
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
                        text = { Text(tabLabels[index]) }
                    )
                }
            }

            // 禁用用户手势滑动（userScrollEnabled = false），防止与底部导航的左右滑动手势冲突
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val list = if (page == 0) blackList else whiteList
                UserListContent(
                    entries = list,
                    onDelete = { viewModel.delete(it) }
                )
            }
        }
    }

    // 添加条目 BottomSheet
    if (viewModel.showAddSheet) {
        ModalBottomSheet(onDismissRequest = { viewModel.closeAddSheet() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 根据当前 Tab 显示对应标题
                Text(
                    if (viewModel.currentTab == ListType.BLACK) stringResource(R.string.title_add_to_blacklist) else stringResource(
                        R.string.title_add_to_whitelist
                    ),
                    style = MaterialTheme.typography.titleLarge
                )

                // 号码输入框：根据是否前缀匹配展示不同的提示文字
                OutlinedTextField(
                    value = viewModel.inputPhone,
                    onValueChange = {
                        viewModel.inputPhone = it
                    },
                    label = { Text(stringResource(R.string.label_phone_number)) },
                    placeholder = {
                        Text(
                            if (viewModel.inputIsPrefix) stringResource(R.string.hint_prefix_example)
                            else stringResource(R.string.hint_exact_example)
                        )
                    },
                    isError = viewModel.addErrorMessage != null,
                    supportingText = viewModel.addErrorMessage?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 前缀匹配开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
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
                        onCheckedChange = { viewModel.inputIsPrefix = it }
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

                // 取消 / 确认 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
        }
    }
}

/**
 * 列表内容区域：无条目时显示占位文字，有条目时渲染可滑动删除的卡片列表。
 */
@Composable
private fun UserListContent(
    entries: List<UserListEntry>,
    onDelete: (UserListEntry) -> Unit,
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
                // 复用 SwipeToDeleteContainer 实现左右滑动删除
                SwipeToDeleteContainer(
                    onDelete = { onDelete(entry) },
                    contentVerticalPadding = 4.dp
                ) {
                    UserListEntryItem(entry)
                }
            }
        }
    }
}

/**
 * 单条名单条目卡片：显示号码（前缀加 * 后缀）、添加时间、备注及前缀标签。
 */
@Composable
private fun UserListEntryItem(entry: UserListEntry) {
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 前缀匹配号码显示为 "400*"，精确匹配显示原始号码
            val displayNumber = if (entry.isPrefix) "${entry.phoneNumber}*" else entry.phoneNumber
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(displayNumber, style = MaterialTheme.typography.titleMedium)
                Text(
                    formatMills(entry.addedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 备注不为空时显示备注文字
            if (!entry.remark.isNullOrBlank()) {
                Text(
                    entry.remark,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // 前缀匹配条目显示标签
            if (entry.isPrefix) {
                Text(
                    stringResource(R.string.label_prefix_match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
