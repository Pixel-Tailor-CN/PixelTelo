package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/** 调试分组仅在解锁后出现；删除入口保留事件并增加二次确认。 */
@Composable
fun DebugPreferences(viewModel: SettingViewModel) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    SettingsGroup(
        rows = listOf(
            SettingsRowItem("force-download") { modifier ->
                SettingsSwitchRow(
                    modifier = modifier,
                    checked = viewModel.forceDownload,
                    title = stringResource(R.string.setting_force_download),
                    summary = stringResource(R.string.setting_force_download_summary),
                    icon = Icons.Default.DownloadForOffline,
                    onCheckedChange = { viewModel.forceDownload = it },
                )
            },
            SettingsRowItem("delete-database") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_delete_database),
                    summary = stringResource(R.string.setting_delete_database_summary),
                    icon = Icons.Default.DeleteSweep,
                    iconTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                    trailing = SettingsRowTrailing.NONE,
                    onClick = { showDeleteConfirmation = true },
                )
            },
        )
    )

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.title_confirm_delete_database)) },
            text = { Text(stringResource(R.string.msg_confirm_delete_database)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteDatabase()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.setting_delete_database))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
