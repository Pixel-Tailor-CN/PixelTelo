package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.model.CommunityStatus
import vip.mystery0.pixel.telo.data.model.parseCommunityStatus

/** 历史字段保持直接可读；窄屏和大字体改为上下排列，不压缩原始内容。 */
@Composable
fun CallHistorySection(call: BlockedCall) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val community = parseCommunityStatus(call.communityStatus)
        // UNKNOWN 仍遵循原显示门禁；真实社区拦截原因不能被状态文案替代。
        if (community != CommunityStatus.UNKNOWN || call.communityBlockApplied) {
            val status = stringResource(when (community) {
                CommunityStatus.ACTIVE -> R.string.history_community_active
                CommunityStatus.INACTIVE -> R.string.history_community_inactive
                CommunityStatus.UNAVAILABLE -> R.string.history_community_unavailable
                CommunityStatus.UNKNOWN -> R.string.history_community_unknown
            })
            val value = if (call.communityBlockApplied) {
                "$status\n${stringResource(R.string.community_block_applied)}"
            } else status
            DetailField(stringResource(R.string.detail_community_at_call), value)
        }
        call.remark?.takeIf(String::isNotBlank)?.let {
            DetailField(stringResource(R.string.history_remark_label), it)
        }
        DetailField(
            stringResource(R.string.detail_local_query_duration),
            stringResource(R.string.detail_duration_ms, call.localDuration),
        )
        // 旧记录的 0 无法区分未联网与缺失耗时，不把它展示为一次 0ms 网络查询。
        if (call.networkDuration > 0) {
            DetailField(
                stringResource(R.string.detail_network_query_duration),
                stringResource(R.string.detail_duration_ms, call.networkDuration),
            )
        }
    }
}

@Composable
internal fun DetailField(label: String, value: String) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (largeText || maxWidth < 320.dp || value.length > 40 || '\n' in value) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, modifier = Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
