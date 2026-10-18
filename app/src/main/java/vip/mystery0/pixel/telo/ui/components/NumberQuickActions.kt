package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R

/** 将常用名单和标签命令放在自适应操作栏中；各操作互相独立。 */
@OptIn(ExperimentalLayoutApi::class)
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
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (userListsLoaded) {
            QuickAction(
                icon = if (isBlacklisted) Icons.Default.CheckCircle else Icons.Default.Block,
                label = if (isBlacklisted) R.string.action_already_in_blacklist
                else R.string.action_add_blacklist_short,
                description = if (isBlacklisted) R.string.cd_open_blacklist_rule
                else R.string.cd_add_number_to_blacklist,
                onClick = if (isBlacklisted) onOpenBlacklist else onAddToBlacklist,
            )
            QuickAction(
                icon = Icons.Default.CheckCircle,
                label = if (isWhitelisted) R.string.action_already_in_whitelist
                else R.string.action_add_whitelist_short,
                description = if (isWhitelisted) R.string.cd_open_whitelist_rule
                else R.string.cd_add_number_to_whitelist,
                onClick = if (isWhitelisted) onOpenWhitelist else onAddToWhitelist,
            )
        } else {
            Text(stringResource(R.string.msg_checking_user_lists))
        }
        QuickAction(
            icon = Icons.Default.Edit,
            label = if (hasLocalLabel) R.string.action_edit_local_label_short
            else R.string.action_set_local_label_short,
            description = if (hasLocalLabel) R.string.cd_edit_local_label
            else R.string.cd_set_local_label,
            enabled = labelEnabled,
            onClick = onEditLocalLabel,
        )
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    description: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val accessibleDescription = stringResource(description)
    // AssistChip 保持约 32dp 的可见高度，Material3 提供至少 48dp 的触摸区域。
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = accessibleDescription },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        label = { Text(stringResource(label)) },
    )
}
