package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

data class SettingsRowItem(
    val key: String,
    val content: @Composable (Modifier) -> Unit,
)

enum class SettingsRowTrailing {
    NONE,
    CHEVRON,
    EXTERNAL_LINK,
}

/** 设置页分组标题及内容，页面横向留白统一为 16dp。 */
@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

/** 按列表顺序为首尾行设置外角，条件显示的条目也能保持圆角连续。 */
@Composable
fun SettingsGroup(
    rows: List<SettingsRowItem>,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEachIndexed { index, item ->
            key(item.key) {
                val first = index == 0
                val last = index == rows.lastIndex
                val shape = RoundedCornerShape(
                    topStart = if (first) 24.dp else 4.dp,
                    topEnd = if (first) 24.dp else 4.dp,
                    bottomStart = if (last) 24.dp else 4.dp,
                    bottomEnd = if (last) 24.dp else 4.dp,
                )
                item.content(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(backgroundColor)
                )
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(
    modifier: Modifier,
    title: String,
    summary: String,
    checked: Boolean,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .defaultMinSize(minHeight = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsLeadingIcon(icon = icon, enabled = enabled)
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else 0.38f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
fun SettingsItemRow(
    modifier: Modifier,
    title: String,
    summary: String? = null,
    summaryContent: (@Composable () -> Unit)? = null,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailing: SettingsRowTrailing? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    summaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    val hasSummary = !summary.isNullOrEmpty() || summaryContent != null
    val effectiveTrailing = trailing ?: if (onClick != null) {
        SettingsRowTrailing.CHEVRON
    } else {
        SettingsRowTrailing.NONE
    }
    val opacity = if (enabled) 1f else 0.38f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .defaultMinSize(minHeight = if (hasSummary) 72.dp else 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsLeadingIcon(
            icon = icon,
            enabled = enabled,
            leadingContent = leadingContent,
            tint = iconTint,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .alpha(opacity),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (!summary.isNullOrEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = summaryColor,
                )
            }
            summaryContent?.invoke()
        }
        if (trailingContent != null) {
            Box(modifier = Modifier.alpha(opacity)) {
                trailingContent()
            }
        } else {
            SettingsTrailingIcon(
                trailing = effectiveTrailing,
                enabled = enabled,
            )
        }
    }
}

@Composable
fun <T> SettingsChoiceRow(
    modifier: Modifier,
    value: T,
    options: List<T>,
    onValueChange: (T) -> Unit,
    title: String,
    summary: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    optionContent: @Composable (T) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsItemRow(
        modifier = modifier,
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = { showDialog = true },
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        val selectOption = {
                            onValueChange(option)
                            showDialog = false
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = selectOption)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = value == option,
                                onClick = selectOption,
                            )
                            optionContent(option)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun SettingsLeadingIcon(
    icon: ImageVector?,
    enabled: Boolean,
    leadingContent: (@Composable () -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            leadingContent != null -> leadingContent()
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(if (enabled) 1f else 0.38f),
                tint = tint,
            )
        }
    }
}

@Composable
private fun SettingsTrailingIcon(
    trailing: SettingsRowTrailing,
    enabled: Boolean,
) {
    val opacity = if (enabled) 1f else 0.38f
    when (trailing) {
        SettingsRowTrailing.NONE -> Unit
        SettingsRowTrailing.CHEVRON -> Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .alpha(opacity),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsRowTrailing.EXTERNAL_LINK -> Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .alpha(opacity),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
