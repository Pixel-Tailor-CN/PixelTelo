package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import vip.mystery0.pixel.telo.R

/** 搜索栏旁保留上报历史与记录管理入口，清空操作仍交由原确认流程处理。 */
@Composable
fun CallHistoryAppBarActions(
    hasRecords: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onOpenReports: () -> Unit,
    onClearRecords: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(stringResource(R.string.community_my_reports)) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onOpenReports) {
            Icon(Icons.AutoMirrored.Outlined.Assignment, stringResource(R.string.community_my_reports))
        }
    }
    if (hasRecords) {
        Box {
            IconButton(onClick = { onMenuExpandedChange(true) }) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuExpandedChange(false) }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_clear_all_records)) },
                    onClick = {
                        onMenuExpandedChange(false)
                        onClearRecords()
                    },
                )
            }
        }
    }
}
