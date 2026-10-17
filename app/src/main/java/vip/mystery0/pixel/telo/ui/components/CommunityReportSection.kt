package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.util.displayText
import vip.mystery0.pixel.telo.viewmodel.CallReportUiState

/** 按社区上报资格与提交状态精简显示动作，保留原有隐私确认。 */
@Composable
fun CommunityReportSection(
    state: CallReportUiState,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    var confirming by remember(state.callId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.loading -> ReportProgress(R.string.community_report_checking)
            state.submitting -> ReportProgress(R.string.community_report_pending)
            state.accepted -> Text(
                stringResource(R.string.community_report_sent),
                color = MaterialTheme.colorScheme.primary,
            )
            state.eligibility != null -> Text(
                state.eligibility.displayText(context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                state.failure?.let { failure ->
                    Text(
                        text = failure.displayText(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = { confirming = true },
                    enabled = state.callId != null,
                ) {
                    Text(stringResource(
                        if (state.failure == null) R.string.community_report_spam
                        else R.string.community_report_retry,
                    ))
                }
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.community_report_spam)) },
            text = { Text(stringResource(R.string.community_report_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onSubmit() }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReportProgress(message: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = androidx.compose.ui.Modifier.size(20.dp))
        Text(stringResource(message), style = MaterialTheme.typography.bodySmall)
    }
}
