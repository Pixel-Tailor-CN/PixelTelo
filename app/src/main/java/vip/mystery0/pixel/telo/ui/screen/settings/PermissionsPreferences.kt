package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.zhanghai.compose.preference.Preference
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.ui.util.PermissionUtils

/** 权限分类下的设置项。 */
@Composable
fun PermissionsPreferences(
    overlayPermissionGranted: Boolean,
    permissionsState: Map<String, Boolean>,
    onRequestOverlayPermission: () -> Unit,
    onRequestPermission: (String) -> Unit,
) {
    Preference(
        title = { Text(stringResource(R.string.permission_overlay_name)) },
        summary = { Text(stringResource(R.string.permission_overlay_desc)) },
        icon = {
            Icon(
                if (overlayPermissionGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (overlayPermissionGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        },
        onClick = {
            if (!overlayPermissionGranted) {
                onRequestOverlayPermission()
            }
        }
    )

    PermissionUtils.allPermissions.forEach { item ->
        val isGranted = permissionsState[item.permission] == true
        Preference(
            title = { Text(stringResource(item.nameResId)) },
            summary = { Text(stringResource(item.descriptionResId)) },
            icon = {
                Icon(
                    if (isGranted) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            },
            onClick = {
                if (!isGranted) {
                    onRequestPermission(item.permission)
                }
            }
        )
    }
}
