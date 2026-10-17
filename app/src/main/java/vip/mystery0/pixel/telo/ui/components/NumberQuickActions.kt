package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
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
    onAddToBlacklist: () -> Unit,
    onAddToWhitelist: () -> Unit,
    onEditLocalLabel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickAction(
            icon = Icons.Default.Block,
            label = R.string.action_add_blacklist_short,
            description = R.string.cd_add_number_to_blacklist,
            onClick = onAddToBlacklist,
        )
        QuickAction(
            icon = Icons.Default.CheckCircle,
            label = R.string.action_add_whitelist_short,
            description = R.string.cd_add_number_to_whitelist,
            onClick = onAddToWhitelist,
        )
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
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = accessibleDescription },
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label))
    }
}
