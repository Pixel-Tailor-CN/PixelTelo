package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R

/** 名单和本地标签各为一组，主按钮保持可见；当前名单状态不代表历史命中原因。 */
@Composable
fun NumberQuickActions(
    hasLocalLabel: Boolean,
    labelEnabled: Boolean,
    userListsLoaded: Boolean,
    isBlacklisted: Boolean,
    isWhitelisted: Boolean,
    onAddToBlacklist: () -> Unit,
    onAddToWhitelist: () -> Unit,
    onOpenBlacklist: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onEditLocalLabel: () -> Unit,
    onDeleteLocalLabel: () -> Unit,
    sourceLabel: String?,
    onAddTagToWhitelist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 两组操作始终等宽同行，菜单尾按钮占用各自半区内的空间。
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (userListsLoaded) {
            DetailSplitButton(
                modifier = Modifier.weight(1f),
                label = stringResource(if (isBlacklisted) R.string.detail_rule_short else R.string.detail_block_short),
                actionDescription = stringResource(if (isBlacklisted) R.string.detail_view_blacklist else R.string.action_add_blacklist_short),
                icon = if (isBlacklisted) Icons.Default.CheckCircle else Icons.Default.Block,
                menuDescription = stringResource(R.string.detail_list_actions),
                onClick = if (isBlacklisted) onOpenBlacklist else onAddToBlacklist,
            ) { close ->
                DropdownMenuItem(
                    text = { Text(stringResource(if (isWhitelisted) R.string.detail_view_whitelist else R.string.action_add_whitelist_short)) },
                    onClick = { close(); if (isWhitelisted) onOpenWhitelist() else onAddToWhitelist() },
                )
                if (sourceLabel != null) {
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.action_add_tag_to_whitelist, sourceLabel))
                                Text(stringResource(R.string.tag_whitelist_scope), style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = { close(); onAddTagToWhitelist() },
                    )
                }
            }
        } else {
            Text(stringResource(R.string.msg_checking_user_lists), modifier = Modifier.weight(1f))
        }
        val label = stringResource(if (hasLocalLabel) R.string.detail_edit_label else R.string.detail_set_label)
        if (hasLocalLabel) {
            DetailSplitButton(
                modifier = Modifier.weight(1f),
                label = label,
                icon = Icons.Default.Edit,
                menuDescription = stringResource(R.string.detail_label_actions),
                onClick = onEditLocalLabel,
                enabled = labelEnabled,
            ) { close ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cd_delete_local_label)) },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                    onClick = { close(); onDeleteLocalLabel() },
                )
            }
        } else {
            FilledTonalButton(
                onClick = onEditLocalLabel,
                enabled = labelEnabled,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
