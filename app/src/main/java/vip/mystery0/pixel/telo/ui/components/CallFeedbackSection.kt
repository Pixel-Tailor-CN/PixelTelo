package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.FeedbackStatus
import vip.mystery0.pixel.telo.data.query.OFFICIAL_BACKEND_ID
import vip.mystery0.pixel.telo.viewmodel.FeedbackSubmissionState

/** 只用持久化成功结果回显方向；终态保留选中样式但不可重提，未知方向不推测。 */
@Composable
fun CallFeedbackSection(
    call: BlockedCall,
    submissionState: FeedbackSubmissionState,
    onSubmit: (Boolean) -> Unit,
) {
    if (call.queryBackendId != OFFICIAL_BACKEND_ID || call.feedbackStatus == FeedbackStatus.UNAVAILABLE) return
    val pending = call.feedbackStatus == FeedbackStatus.PENDING
    val submitting = submissionState is FeedbackSubmissionState.Submitting && submissionState.callId == call.id
    val known = call.feedbackStatus == FeedbackStatus.POSITIVE || call.feedbackStatus == FeedbackStatus.NEGATIVE
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.label_call_feedback), style = MaterialTheme.typography.titleSmall)
        if (pending || known) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(true, false).forEachIndexed { index, positive ->
                    SegmentedButton(
                        selected = if (positive) call.feedbackStatus == FeedbackStatus.POSITIVE else call.feedbackStatus == FeedbackStatus.NEGATIVE,
                        onClick = { onSubmit(positive) },
                        enabled = pending && !submitting,
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        colors = SegmentedButtonDefaults.colors(
                            disabledActiveContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledActiveContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledActiveBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                    ) {
                        Text(stringResource(if (positive) R.string.action_feedback_positive else R.string.action_feedback_negative))
                    }
                }
            }
        }
        if (submitting) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.detail_feedback_submitting), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (pending && submissionState is FeedbackSubmissionState.Failure && submissionState.callId == call.id) {
            Text(stringResource(R.string.msg_feedback_failed, submissionState.message),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        val status = when (call.feedbackStatus) {
            FeedbackStatus.POSITIVE, FeedbackStatus.NEGATIVE -> R.string.detail_feedback_saved
            FeedbackStatus.ALREADY_SUBMITTED -> R.string.detail_feedback_unknown
            FeedbackStatus.EXPIRED -> R.string.feedback_status_expired
            FeedbackStatus.INVALID -> R.string.feedback_status_invalid
            else -> null
        }
        status?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
