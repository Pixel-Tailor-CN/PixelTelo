package vip.mystery0.pixel.telo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.ui.res.stringResource
import vip.mystery0.pixel.telo.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics

/** 主操作直接可见，箭头只展开同组操作；菜单点击先关闭再执行业务回调。 */
@Composable
fun DetailSplitButton(
    label: String,
    icon: ImageVector,
    menuDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    actionDescription: String = label,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "detailMenuArrow")
    Box(modifier) {
        SplitButtonLayout(
            modifier = Modifier.fillMaxWidth(),
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(
                    onClick = onClick,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = actionDescription },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Icon(icon, null, Modifier.size(SplitButtonDefaults.LeadingIconSize))
                    Spacer(Modifier.width(6.dp))
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            trailingButton = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(menuDescription) } },
                    state = rememberTooltipState(),
                ) {
                    SplitButtonDefaults.TonalTrailingButton(
                        checked = expanded,
                        onCheckedChange = { expanded = it },
                        enabled = enabled,
                        modifier = Modifier.width(48.dp).semantics { contentDescription = menuDescription },
                    ) {
                        Icon(Icons.Default.ArrowDropDown, null, Modifier.rotate(rotation))
                    }
                }
            },
        )
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            menu { expanded = false }
        }
    }
}

/** 底部只有保存和删除两个操作，直接展示，避免额外菜单层级。 */
@Composable
fun CallRecordActions(onSaveContact: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onSaveContact, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_save_to_contacts))
        }
        TextButton(
            onClick = onDelete,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_delete_record))
        }
    }
}
