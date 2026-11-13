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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import vip.mystery0.pixel.telo.data.entity.isQueryFailure
import vip.mystery0.pixel.telo.util.queryFailureSummaryRes
import vip.mystery0.pixel.telo.viewmodel.CurrentListState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val failed = call.resultType.isQueryFailure()
    val status = when {
        failed -> RecordStatus(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.onSurfaceVariant,
            R.string.record_result_query_failed_open,
            R.string.cd_record_query_failed,
        )
        call.resultType == ResultType.PASS_SILENT -> RecordStatus(
            Icons.Default.NotificationsOff,
            MaterialTheme.colorScheme.primary,
            R.string.record_result_silenced_allowed,
            R.string.cd_record_silenced,
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
    val time = Instant.ofEpochMilli(call.blockTime).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    val largeText = LocalDensity.current.fontScale > 1.3f
    val location = call.recordLocation()
    val sourceLabel = call.label?.takeIf(String::isNotBlank)
    val visibleLocalLabel = localLabel?.takeIf(String::isNotBlank)
    val fullFailure = call.resultType.queryFailureSummaryRes()

    Card(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        enabled = onClick != null,
        onClick = { onClick?.invoke() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (largeText) {
                        Text(contactName?.takeIf(String::isNotBlank) ?: call.phoneNumber,
                            style = MaterialTheme.typography.titleMedium)
                        Text(time, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top) {
                            Text(contactName?.takeIf(String::isNotBlank) ?: call.phoneNumber,
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(time, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!contactName.isNullOrBlank()) {
                        Text(call.phoneNumber, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (location != null || sourceLabel != null || visibleLocalLabel != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    location?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    sourceLabel?.let { value ->
                        RecordLabel(icon = Icons.Default.Cloud, text = value,
                            description = stringResource(R.string.cd_source_label_value, value),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    visibleLocalLabel?.let { value ->
                        RecordLabel(icon = Icons.Default.Edit, text = value,
                            description = stringResource(R.string.cd_local_label_value, value),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // 大字体下结果和原因先占满整行，避免尾部操作挤压历史放行结论。
                Column(
                    modifier = if (largeText && failed) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StatusBadge(
                        icon = status.icon, text = stringResource(status.label),
                        description = stringResource(status.description), tint = status.tint)
                    fullFailure?.let {
                        Text(stringResource(it), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (failed && onRetry != null) {
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                } else if (!failed) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            currentListState.takeIf { it != CurrentListState.NONE }?.let { state ->
                Text(stringResource(when (state) {
                    CurrentListState.BLACK -> R.string.current_list_black
                    CurrentListState.WHITE -> R.string.current_list_white
                    CurrentListState.BOTH -> R.string.current_list_both
                    CurrentListState.NONE -> R.string.current_list_both
                }), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 来源和本地标签使用无底色的次要行，保持独立语义与强调色。 */
@Composable
private fun RecordLabel(
    icon: ImageVector,
    text: String,
    description: String,
    tint: Color,
) {
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

/** 显示记录中保存的实际处理结果；失败记录同时说明 Fail Open 与失败分类。 */
@Composable
fun CallResultSummary(
    resultType: ResultType,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    val failed = resultType.isQueryFailure()
    val status = when {
        failed -> RecordStatus(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.onSurfaceVariant,
            R.string.record_result_query_failed_open,
            R.string.cd_record_query_failed,
        )
        resultType == ResultType.PASS_SILENT -> RecordStatus(
            Icons.Default.NotificationsOff,
            MaterialTheme.colorScheme.primary,
            R.string.record_result_silenced_allowed,
            R.string.cd_record_silenced,
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
        if (prominent) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(status.icon, null, tint = status.tint)
                Text(stringResource(status.label), style = MaterialTheme.typography.titleLarge, color = status.tint)
            }
        } else {
            StatusBadge(
                icon = status.icon,
                text = stringResource(status.label),
                description = stringResource(status.description),
                tint = status.tint,
            )
        }
        resultType.queryFailureSummaryRes()?.let { failure ->
            Text(
                text = stringResource(failure),
                style = if (prominent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 记录卡与名单条目的共用状态徽章：低强调表面 + 单色图标 + labelMedium 文案。 */
@Composable
internal fun StatusBadge(
    icon: ImageVector,
    text: String,
    description: String,
    tint: Color,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
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
