package vip.mystery0.pixel.telo.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.model.CommunityReportStatus
import vip.mystery0.pixel.telo.data.model.ReportFailureKind
import vip.mystery0.pixel.telo.util.displayText
import vip.mystery0.pixel.telo.util.textRes
import vip.mystery0.pixel.telo.viewmodel.MyReportsViewModel

@Composable
fun MyReportsScreen(viewModel: MyReportsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val repositoryBusy by viewModel.repositoryBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var targetId by remember { mutableStateOf<String?>(null) }
    var resetting by remember { mutableStateOf(false) }
    val target = state.items.firstOrNull { it.reportId == targetId }
    val busy = state.operationId != null
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()) }
    DisposableEffect(viewModel) {
        viewModel.onEnter()
        onDispose { viewModel.onLeave() }
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.community_official_history))
                Text(stringResource(R.string.community_identity_notice), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = viewModel::refresh, enabled = !busy && !state.loading) {
                    Text(stringResource(R.string.community_refresh))
                }
                state.failure?.let {
                    Text(it.displayText(context), color = MaterialTheme.colorScheme.error)
                    if (state.items.isNotEmpty()) Text(stringResource(R.string.community_stale_list),
                        style = MaterialTheme.typography.bodySmall)
                    if (it.kind == ReportFailureKind.IDENTITY_UNAVAILABLE) {
                        TextButton(onClick = { resetting = true }, enabled = !busy && !repositoryBusy) {
                            Text(stringResource(R.string.community_reset_identity))
                        }
                    }
                }
                if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (state.initialized && state.items.isEmpty() && !state.loading && state.failure == null) {
                    Text(stringResource(R.string.community_empty))
                }
            }
        }
        items(state.items, key = { "report:${it.reportId}" }) { report ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(report.phone, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(when (report.status) {
                        CommunityReportStatus.ACTIVE -> R.string.community_active_report
                        CommunityReportStatus.WITHDRAWN -> R.string.community_withdrawn_report
                        CommunityReportStatus.INVALIDATED -> R.string.community_invalidated_report
                        CommunityReportStatus.UNKNOWN -> R.string.community_unknown_report
                    }))
                    Text(stringResource(report.communityStatus.textRes()))
                    Text(stringResource(R.string.community_reported_at, dateFormatter.format(report.reportedAt)),
                        style = MaterialTheme.typography.bodySmall)
                    report.withdrawnAt?.let {
                        Text(stringResource(R.string.community_withdrawn_at, dateFormatter.format(it)),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    report.invalidatedAt?.let {
                        Text(stringResource(R.string.community_invalidated_at, dateFormatter.format(it)),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.status == CommunityReportStatus.INVALIDATED) {
                        Text(stringResource(R.string.community_invalidated_notice),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (report.canWithdraw || report.canDelete) {
                        TextButton(onClick = { targetId = report.reportId }, enabled = !busy) {
                            Text(stringResource(if (report.canDelete)
                                R.string.community_delete_history else R.string.community_withdraw))
                        }
                    }
                    if (state.operationId == report.reportId) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        if (state.nextCursor != null) item(key = "more") {
            OutlinedButton(onClick = viewModel::loadMore,
                enabled = !state.loading && !state.loadingMore && !busy,
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.community_load_more))
            }
        }
        if (state.loadingMore) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
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
