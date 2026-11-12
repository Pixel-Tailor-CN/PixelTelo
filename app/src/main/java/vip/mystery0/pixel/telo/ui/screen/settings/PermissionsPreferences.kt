package vip.mystery0.pixel.telo.ui.screen.settings

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.ui.util.PermissionItem
import vip.mystery0.pixel.telo.ui.util.PermissionUtils

/** 权限状态始终保持可读；未授权行沿用原有系统授权回调。 */
@Composable
fun PermissionsPreferences(
    overlayPermissionGranted: Boolean,
    permissionsState: Map<String, Boolean>,
    settingsRequired: Set<String>,
    onRequestOverlayPermission: () -> Unit,
    onRequestPermission: (String) -> Unit,
) {
    val grantedLabel = stringResource(R.string.permission_status_granted)
    val requestLabel = stringResource(R.string.action_request_permission)
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        listOf(true, false).forEach { required ->
            SettingsSection(title = stringResource(
                if (required) R.string.permissions_required else R.string.permissions_optional
            )) {
                val permissionRows = buildList {
                    PermissionUtils.allPermissions.filter { it.isCritical == required }.forEach { item ->
                        add(SettingsRowItem("permission-${item.permission}") { modifier ->
                            PermissionRow(
                                modifier = modifier,
                                item = item,
                                granted = permissionsState[item.permission] == true,
                                grantedLabel = grantedLabel,
                                requestLabel = if (item.permission in settingsRequired)
                                    stringResource(R.string.permission_open_settings) else requestLabel,
                                onRequest = { onRequestPermission(item.permission) },
                            )
                        })
                    }
                    if (!required) add(SettingsRowItem("overlay-permission") { modifier ->
                        PermissionRow(
                            modifier = modifier,
                            icon = Icons.Default.PictureInPicture,
                            title = stringResource(R.string.permission_overlay_name),
                            description = stringResource(R.string.permission_overlay_desc),
                            granted = overlayPermissionGranted,
                            grantedLabel = grantedLabel,
                            requestLabel = requestLabel,
                            onRequest = onRequestOverlayPermission,
                        )
                    })
                }
                SettingsGroup(permissionRows)
            }
        }
    }
}

@Composable
private fun PermissionRow(
    modifier: androidx.compose.ui.Modifier,
    title: String,
    icon: ImageVector,
    description: String,
    granted: Boolean,
    grantedLabel: String,
    requestLabel: String,
    onRequest: () -> Unit,
) {
    SettingsItemRow(
        modifier = modifier,
        title = title,
        icon = icon,
        summary = description,
        trailing = SettingsRowTrailing.NONE,
        trailingContent = {
            PermissionStatus(
                granted = granted,
                grantedLabel = grantedLabel,
                requestLabel = requestLabel,
            )
        },
        onClick = if (granted) null else onRequest,
    )
}

@Composable
private fun PermissionRow(
    modifier: androidx.compose.ui.Modifier,
    item: PermissionItem,
    granted: Boolean,
    grantedLabel: String,
    requestLabel: String,
    onRequest: () -> Unit,
) {
    PermissionRow(
        modifier = modifier,
        title = stringResource(item.nameResId),
        icon = when (item.permission) {
            Manifest.permission.READ_CONTACTS -> Icons.Default.Contacts
            Manifest.permission.WRITE_CONTACTS -> Icons.Default.PersonAdd
            Manifest.permission.READ_CALL_LOG -> Icons.Default.History
            Manifest.permission.READ_PHONE_STATE -> Icons.Default.Phone
            Manifest.permission.POST_NOTIFICATIONS -> Icons.Default.NotificationsNone
            else -> Icons.Default.Check
        },
        description = stringResource(item.descriptionResId),
        granted = granted,
        grantedLabel = grantedLabel,
        requestLabel = requestLabel,
        onRequest = onRequest,
    )
}

@Composable
private fun PermissionStatus(
    granted: Boolean,
    grantedLabel: String,
    requestLabel: String,
) {
    if (granted) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                grantedLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                requestLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
