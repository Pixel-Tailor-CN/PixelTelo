package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.entity.isNetworkFailure
import vip.mystery0.pixel.telo.ui.util.formatMills
import vip.mystery0.pixel.telo.util.queryFailureSummaryRes
import vip.mystery0.pixel.telo.viewmodel.CurrentListState

private data class RecordStatus(
    val icon: ImageVector,
    val tint: Color,
    val label: Int,
    val description: Int,
)

/** 单条来电记录卡片。状态、本地标签与识别来源保持独立呈现。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockedCallItem(
    call: BlockedCall,
    contactName: String? = null,
    localLabel: String? = null,
    currentListState: CurrentListState = CurrentListState.NONE,
    onRetry: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val failed = call.resultType.isNetworkFailure()
    val status = when {
        failed -> RecordStatus(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.onSurfaceVariant,
            R.string.record_result_query_failed_open,
            R.string.cd_record_query_failed,
        )
        call.resultType == ResultType.PASS_BUT_NOTIFY -> RecordStatus(
            Icons.Default.Notifications,
            MaterialTheme.colorScheme.primary,
            R.string.record_result_notify_allowed,
            R.string.cd_record_notify,
        )
        call.resultType == ResultType.PASS || call.resultType == ResultType.WHITE_LIST -> RecordStatus(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.tertiary,
            R.string.record_result_allowed,
            R.string.cd_record_allowed,
        )
        else -> RecordStatus(
            Icons.Default.Block,
            MaterialTheme.colorScheme.error,
            R.string.record_result_blocked,
            R.string.cd_record_blocked,
        )
    }
    val time = formatMills(call.blockTime)
    val largeText = LocalDensity.current.fontScale > 1.3f
    val location = call.recordLocation()
    val phoneAndLocation = listOfNotNull(call.phoneNumber, location)
        .joinToString(stringResource(R.string.label_compact_separator))
    val fullFailure = call.resultType.queryFailureSummaryRes()

    Card(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(),
        enabled = onClick != null,
        onClick = { onClick?.invoke() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = status.icon,
                contentDescription = stringResource(status.description),
                tint = status.tint,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(24.dp),
            )
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!contactName.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = contactName,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(time, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (contactName.isNullOrBlank() && largeText) {
                        Text(
                            text = phoneAndLocation,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(time, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = phoneAndLocation,
                                modifier = Modifier.weight(1f),
                                style = if (contactName.isNullOrBlank()) {
                                    MaterialTheme.typography.titleSmall
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                            )
                            if (contactName.isNullOrBlank()) {
                                Text(time, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RecordBadge(
                            icon = status.icon,
                            text = stringResource(status.label),
                            description = stringResource(status.description),
                            tint = status.tint,
                        )
                        call.label?.takeIf(String::isNotBlank)?.let { sourceLabel ->
                            RecordBadge(
                                icon = Icons.Default.Cloud,
                                text = sourceLabel,
                                description = stringResource(R.string.cd_source_label_value, sourceLabel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        localLabel?.takeIf(String::isNotBlank)?.let { value ->
                            RecordBadge(
                                icon = Icons.Default.Edit,
                                text = value,
                                description = stringResource(R.string.cd_local_label_value, value),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (fullFailure != null) {
                        Text(
                            text = stringResource(fullFailure),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    currentListState.takeIf { it != CurrentListState.NONE }?.let { state ->
                        Text(
                            text = stringResource(when (state) {
                                CurrentListState.BLACK -> R.string.current_list_black
                                CurrentListState.WHITE -> R.string.current_list_white
                                CurrentListState.BOTH -> R.string.current_list_both
                                CurrentListState.NONE -> R.string.current_list_both
                            }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (onRetry != null) {
                IconButton(onClick = onRetry, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.action_retry_query),
                    )
                }
            }
        }
    }
}

/** 显示记录中保存的实际处理结果；失败记录同时说明 Fail Open 与失败分类。 */
@Composable
fun CallResultSummary(
    resultType: ResultType,
    modifier: Modifier = Modifier,
) {
    val failed = resultType.isNetworkFailure()
    val status = when {
        failed -> RecordStatus(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.onSurfaceVariant,
            R.string.record_result_query_failed_open,
            R.string.cd_record_query_failed,
        )
        resultType == ResultType.PASS_BUT_NOTIFY -> RecordStatus(
            Icons.Default.Notifications,
            MaterialTheme.colorScheme.primary,
            R.string.record_result_notify_allowed,
            R.string.cd_record_notify,
        )
        resultType == ResultType.PASS || resultType == ResultType.WHITE_LIST -> RecordStatus(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.tertiary,
            R.string.record_result_allowed,
            R.string.cd_record_allowed,
        )
        else -> RecordStatus(
            Icons.Default.Block,
            MaterialTheme.colorScheme.error,
            R.string.record_result_blocked,
            R.string.cd_record_blocked,
        )
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RecordBadge(
            icon = status.icon,
            text = stringResource(status.label),
            description = stringResource(status.description),
            tint = status.tint,
        )
        resultType.queryFailureSummaryRes()?.let { failure ->
            Text(
                text = stringResource(failure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecordBadge(
    icon: ImageVector,
    text: String,
    description: String,
    tint: Color,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun BlockedCall.recordLocation(): String? = listOfNotNull(
    province?.trim()?.takeIf(String::isNotEmpty),
    city?.trim()?.takeIf(String::isNotEmpty),
).distinct().joinToString(" ").takeIf(String::isNotEmpty)
