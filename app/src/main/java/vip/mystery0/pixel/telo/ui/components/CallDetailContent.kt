package vip.mystery0.pixel.telo.ui.components

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.ResultType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** 详情只组织展示层级，操作通过插槽连接原有业务流程。 */
@Composable
fun CallDetailContent(
    call: BlockedCall,
    contactName: String?,
    localLabel: String?,
    quickActions: @Composable () -> Unit,
    feedback: @Composable () -> Unit,
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
            Text(stringResource(R.string.detail_title), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            CallResultSummary(call.resultType, prominent = true)
            when (call.resultType) {
                ResultType.BLACK_LIST -> Text(stringResource(R.string.detail_blacklist_reason), style = MaterialTheme.typography.bodyMedium)
                ResultType.WHITE_LIST -> Text(stringResource(R.string.detail_whitelist_reason), style = MaterialTheme.typography.bodyMedium)
                else -> Unit
            }
        }
        quickActions()
        if (label != null || source != null || localLabel != null) {
            DetailSection(stringResource(R.string.detail_identity)) {
                label?.let { DetailField(stringResource(R.string.detail_source_label), it) }
                source?.let { DetailField(stringResource(R.string.detail_query_source), it) }
                localLabel?.let { DetailField(stringResource(R.string.detail_current_local_label), it) }
            }
        }
        DetailSection(stringResource(R.string.detail_record)) { CallHistorySection(call) }
        DetailSection(stringResource(R.string.detail_feedback_reports)) { feedback() }
        recordActions()
    }
}

@Composable
private fun DetailSection(title: String, emphasized: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (emphasized) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
