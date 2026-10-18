package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.model.CommunityReport
import vip.mystery0.pixel.telo.data.model.CommunityReportStatus
import vip.mystery0.pixel.telo.ui.util.formatMills
import vip.mystery0.pixel.telo.util.textRes

private data class ReportStatusUi(
    val icon: ImageVector,
    val tint: Color,
    val label: Int,
)

/**
 * 单条上报卡片。
 *
 * 上报状态是卡片唯一的语义徽章（作废是管理端裁决，沿用 error 红，与「删除历史」的描边红一致）；
 * 社区标记是读取时的快照，与更早的上报时间一起退成无底色的次要行。
 * 身份行右上恒为该上报最近一次状态变化时间，更早的上报时间在次要行补「上报于」。
 * 字体缩放超过 1.3 时身份行改为上下堆叠，避免时间挤压号码。
 */
@Composable
fun ReportItem(
    report: CommunityReport,
    busy: Boolean,
    actionsEnabled: Boolean,
    onAction: () -> Unit,
) {
    val status = when (report.status) {
        CommunityReportStatus.ACTIVE -> ReportStatusUi(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary,
            R.string.community_active_report,
        )

        CommunityReportStatus.WITHDRAWN -> ReportStatusUi(
            Icons.Default.History,
            MaterialTheme.colorScheme.onSurfaceVariant,
            R.string.community_withdrawn_report,
        )

        CommunityReportStatus.INVALIDATED -> ReportStatusUi(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.error,
            R.string.community_invalidated_report,
        )

        CommunityReportStatus.UNKNOWN -> ReportStatusUi(
            Icons.AutoMirrored.Filled.HelpOutline,
            MaterialTheme.colorScheme.onSurfaceVariant,
            R.string.community_unknown_report,
        )
    }
    val latestChange = listOfNotNull(report.reportedAt, report.withdrawnAt, report.invalidatedAt)
        .maxOrNull() ?: report.reportedAt
    val time = formatMills(latestChange.toEpochMilli())
    val reportedAt = formatMills(report.reportedAt.toEpochMilli())
    val largeText = LocalDensity.current.fontScale > 1.3f
    val actionLabel = if (report.canDelete) R.string.community_delete_history
    else R.string.community_withdraw
    val actionTint = if (report.canDelete) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 展示信息合并成一次朗读，撤回/删除保持独立焦点，避免逐个划过每个字段。
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (largeText) {
                    Text(report.phone, style = MaterialTheme.typography.titleMedium)
                    ReportTime(time)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            report.phone,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        ReportTime(time)
                    }
                }
                StatusBadge(
                    icon = status.icon,
                    text = stringResource(status.label),
                    description = stringResource(R.string.cd_report_status, stringResource(status.label)),
                    tint = status.tint,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ReportMetaLine(
                        icon = Icons.Default.Public,
                        text = stringResource(
                            R.string.community_status_inline,
                            stringResource(report.communityStatus.textRes()),
                        ),
                    )
                    if (latestChange != report.reportedAt) {
                        ReportMetaLine(
                            icon = Icons.Default.History,
                            text = stringResource(R.string.community_reported_at, reportedAt),
                        )
                    }
                    if (report.status == CommunityReportStatus.UNKNOWN) {
                        ReportMetaLine(
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            text = stringResource(R.string.community_unknown_hint),
                        )
                    }
                    // 作废的后果与可删除边界属于产品要求，保持无底色正文，不与状态徽章抢层级。
                    if (report.status == CommunityReportStatus.INVALIDATED) {
                        Text(
                            text = stringResource(R.string.community_invalidated_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (busy) {
                // 操作中的卡片用卡内小进度表达，不降低整卡对比度。
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (report.canWithdraw || report.canDelete) {
                // 胶囊描边按钮统一撤回与删除样式；Material3 保留最小触摸区域。
                OutlinedButton(
                    onClick = onAction,
                    enabled = actionsEnabled,
                    shape = CircleShape,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (actionsEnabled) actionTint else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = actionTint,
                        disabledContentColor = MaterialTheme.colorScheme.outline,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(actionLabel))
                }
            }
        }
    }
}

@Composable
private fun ReportTime(time: String) {
    Text(
        text = time,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 次要信息行与来电记录的来源/本地标签同一种弱强调：无底色、图标加正文。 */
@Composable
private fun ReportMetaLine(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(15.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
