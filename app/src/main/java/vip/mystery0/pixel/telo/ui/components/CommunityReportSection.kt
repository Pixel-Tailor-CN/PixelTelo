package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.util.displayText
import vip.mystery0.pixel.telo.viewmodel.CallReportUiState

@Composable
fun CommunityReportSection(
    state: CallReportUiState,
    onSubmit: () -> Unit,
    onOpenReports: () -> Unit,
) {
    val context = LocalContext.current
    var confirming by remember(state.callId) { mutableStateOf(false) }
    Column {
        if (state.accepted) Text(stringResource(R.string.community_report_sent))
        else state.eligibility?.let { Text(it.displayText(context), style = MaterialTheme.typography.bodySmall) }
        state.failure?.let { Text(it.displayText(context), color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { confirming = true },
            enabled = state.callId != null && !state.loading && !state.submitting && !state.accepted && state.eligibility == null,
        ) {
            Text(stringResource(if (state.submitting) R.string.community_report_pending else R.string.community_report_spam))
        }
        TextButton(onClick = onOpenReports) { Text(stringResource(R.string.community_my_reports)) }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.community_report_spam)) },
        text = { Text(stringResource(R.string.community_report_confirm)) },
        confirmButton = {
            TextButton(onClick = { confirming = false; onSubmit() }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
