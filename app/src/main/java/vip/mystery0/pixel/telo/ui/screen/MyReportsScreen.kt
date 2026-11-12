package vip.mystery0.pixel.telo.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.model.ReportFailure
import vip.mystery0.pixel.telo.data.model.ReportFailureKind
import vip.mystery0.pixel.telo.ui.components.EmptyState
import vip.mystery0.pixel.telo.ui.components.ReportItem
import vip.mystery0.pixel.telo.util.displayText
import vip.mystery0.pixel.telo.viewmodel.MyReportsViewModel

/** 列表为空时占满内容区的三种占位；null 表示已有记录，按列表渲染。 */
private sealed interface ReportsPlaceholder {
    data object Loading : ReportsPlaceholder
    data object Empty : ReportsPlaceholder
    data class Failed(val failure: ReportFailure) : ReportsPlaceholder
}

@Composable
fun MyReportsScreen(viewModel: MyReportsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val repositoryBusy by viewModel.repositoryBusy.collectAsStateWithLifecycle()
    var targetId by remember { mutableStateOf<String?>(null) }
    var resetting by remember { mutableStateOf(false) }
    val target = state.items.firstOrNull { it.reportId == targetId }
    val busy = state.operationId != null
    val hasItems = state.items.isNotEmpty()
    // 已有记录时刷新用下拉指示器，空列表的加载用居中环形，两种进度表达不同时出现。
    val refreshing = state.loading && hasItems
    val initialLoading = state.loading && !hasItems
    // 失败只在列表为空时占满内容区；已有记录时降级为列表顶部的提示条并保留内容。
    val loadFailure = state.failure?.takeIf { !hasItems && !state.loading }
    val placeholder = when {
        initialLoading -> ReportsPlaceholder.Loading
        loadFailure != null -> ReportsPlaceholder.Failed(loadFailure)
        state.initialized && !hasItems -> ReportsPlaceholder.Empty
        else -> null
    }
    DisposableEffect(viewModel) {
        viewModel.onEnter()
        onDispose { viewModel.onLeave() }
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        // 首次加载、写操作与增量加载期间不接受下拉，避免打断进行中的事务或混淆分页窗口。
        enabled = !busy && !state.loadingMore && !initialLoading,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
            verticalArrangement = if (placeholder == null) Arrangement.Top else Arrangement.Center,
        ) {
            when (placeholder) {
                ReportsPlaceholder.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                ReportsPlaceholder.Empty -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Default.CloudOff,
                        title = stringResource(R.string.community_empty),
                        description = stringResource(R.string.community_empty_hint),
                    )
                }

                is ReportsPlaceholder.Failed -> item(key = "failed") {
                    LoadFailureState(
                        failure = placeholder.failure,
                        enabled = !busy && !repositoryBusy,
                        onRetry = viewModel::refresh,
                        onResetIdentity = { resetting = true },
                    )
                }

                null -> {
                    if (state.windowTrimmed) item(key = "window-notice") {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(stringResource(R.string.reports_window_notice), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = viewModel::refresh,
                                enabled = !busy && !state.loading && !state.loadingMore) {
                                Text(stringResource(R.string.reports_latest))
                            }
                        }
                    }
                    state.failure?.let { failure ->
                        item(key = "stale") {
                            StaleReportsNotice(
                                failure = failure,
                                enabled = !busy && !repositoryBusy,
                                onResetIdentity = { resetting = true },
                            )
                        }
                    }
                    items(state.items, key = { "report:${it.reportId}" }) { report ->
                        ReportItem(
                            report = report,
                            busy = state.operationId == report.reportId,
                            actionsEnabled = !busy,
                            onAction = { targetId = report.reportId },
                        )
                    }
                    if (state.loadingMore) {
                        item(key = "loading-more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    } else if (state.nextCursor != null) {
                        item(key = "more") {
                            OutlinedButton(
                                onClick = viewModel::loadMore,
                                enabled = !state.loading && !busy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                Text(stringResource(R.string.community_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
    if (target != null && (target.canWithdraw || target.canDelete)) {
        val delete = target.canDelete
        AlertDialog(
            onDismissRequest = { targetId = null },
            title = { Text(stringResource(if (delete) R.string.community_delete_history else R.string.community_withdraw)) },
            text = { Text(stringResource(if (delete) R.string.community_delete_confirm else R.string.community_withdraw_confirm, target.phone)) },
            confirmButton = { TextButton(onClick = {
                targetId = null
                if (delete) viewModel.delete(target.reportId) else viewModel.withdraw(target.reportId)
            }, enabled = !busy) { Text(stringResource(R.string.action_confirm)) } },
            dismissButton = { TextButton(onClick = { targetId = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (resetting) AlertDialog(
        onDismissRequest = { resetting = false },
        title = { Text(stringResource(R.string.community_reset_identity)) },
        text = { Text(stringResource(R.string.community_reset_confirm)) },
        confirmButton = { TextButton(onClick = {
            resetting = false
            viewModel.resetIdentityAfterConfirmation()
        }, enabled = !repositoryBusy) { Text(stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton(onClick = { resetting = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** 列表为空时的失败态：占满内容区，按失败类型给出重试或建立新身份。 */
@Composable
private fun LoadFailureState(
    failure: ReportFailure,
    enabled: Boolean,
    onRetry: () -> Unit,
    onResetIdentity: () -> Unit,
) {
    val context = LocalContext.current
    EmptyState(
        icon = Icons.Default.CloudOff,
        title = stringResource(R.string.community_load_failed_title),
        description = failure.displayText(context),
        action = {
            if (failure.kind == ReportFailureKind.IDENTITY_UNAVAILABLE) {
                // 旧身份无法读取时不提供重试，避免把不可恢复的凭据问题表现成临时故障。
                OutlinedButton(onClick = onResetIdentity, enabled = enabled) {
                    Text(stringResource(R.string.community_reset_identity))
                }
            } else {
                Button(onClick = onRetry, enabled = enabled) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        },
    )
}

/**
 * 刷新失败但仍有可见记录时的提示条。
 * 保留内容不白屏，说明可能过时并指向下拉刷新；身份不可用时在同一处给出重新建立身份的入口。
 */
@Composable
private fun StaleReportsNotice(
    failure: ReportFailure,
    enabled: Boolean,
    onResetIdentity: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(18.dp),
                )
                Text(
                    text = stringResource(R.string.community_stale_list),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = failure.displayText(context),
                modifier = Modifier.padding(start = 28.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (failure.kind == ReportFailureKind.IDENTITY_UNAVAILABLE) {
                TextButton(
                    onClick = onResetIdentity,
                    enabled = enabled,
                    modifier = Modifier.padding(start = 16.dp),
                ) {
                    Text(stringResource(R.string.community_reset_identity))
                }
            }
        }
    }
}
