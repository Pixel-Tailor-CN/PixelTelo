package vip.mystery0.pixel.telo.ui.components

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.entity.FeedbackStatus
import vip.mystery0.pixel.telo.data.query.OFFICIAL_BACKEND_ID
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 详情只组织展示层级，操作通过插槽连接原有业务流程。 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun CallDetailContent(
    call: BlockedCall,
    contactName: String?,
    localLabel: String?,
    quickActions: @Composable () -> Unit,
    feedback: @Composable () -> Unit,
    reporting: @Composable () -> Unit,
    reportingAction: @Composable () -> Unit,
    recordActions: @Composable () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    val timestamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(locale).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(call.blockTime))
    val location = listOfNotNull(call.province?.trim()?.takeIf(String::isNotEmpty), call.city?.trim()?.takeIf(String::isNotEmpty))
        .distinct().joinToString(" ")
    val label = call.label?.takeIf(String::isNotBlank)
    val source = call.querySource?.trim()?.takeIf(String::isNotBlank)
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!contactName.isNullOrBlank()) Text(contactName, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(call.phoneNumber, style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("phone", call.phoneNumber))) }
                }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.detail_copy_number)) }
            }
            if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(timestamp, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DetailSection(stringResource(R.string.detail_result), emphasized = true) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        CallResultSummary(call.resultType, prominent = true)
                    }
                    reportingAction()
                }
                if (label != null || source != null || localLabel != null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        label?.let { DetailIdentityChip(stringResource(R.string.detail_source_label), it) }
                        source?.let { DetailIdentityChip(stringResource(R.string.detail_query_source), it) }
                        localLabel?.let { DetailIdentityChip(stringResource(R.string.detail_current_local_label), it, local = true) }
                    }
                }
            }
            when (call.resultType) {
                ResultType.BLACK_LIST -> Text(stringResource(R.string.detail_blacklist_reason), style = MaterialTheme.typography.bodyMedium)
                ResultType.WHITE_LIST -> Text(stringResource(R.string.detail_whitelist_reason), style = MaterialTheme.typography.bodyMedium)
                else -> Unit
            }
            reporting()
        }
        quickActions()
        val showFeedback = call.queryBackendId == OFFICIAL_BACKEND_ID && call.feedbackStatus != FeedbackStatus.UNAVAILABLE
        // 与设置分组一致：外侧 24dp、连接处 4dp；无反馈时恢复完整圆角，避免空卡片。
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DetailSection(
                shape = RoundedCornerShape(
                    topStart = 24.dp, topEnd = 24.dp,
                    bottomStart = if (showFeedback) 4.dp else 24.dp,
                    bottomEnd = if (showFeedback) 4.dp else 24.dp,
                ),
            ) { CallHistorySection(call) }
            if (showFeedback) {
                DetailSection(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                ) { feedback() }
            }
        }
        recordActions()
    }
}

/** 只读 chip 不注册点击行为，读屏仍区分历史识别、来源与当前本地标签。 */
@Composable
private fun DetailIdentityChip(title: String, value: String, local: Boolean = false) {
    val description = stringResource(R.string.detail_identity_chip_description, title, value)
    Surface(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        shape = MaterialTheme.shapes.small,
        color = if (local) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = if (local) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = if (local) description else value,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun DetailSection(
    title: String? = null,
    emphasized: Boolean = false,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = if (emphasized) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
