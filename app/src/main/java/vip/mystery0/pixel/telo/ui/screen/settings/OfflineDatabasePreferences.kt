package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel
import vip.mystery0.pixel.telo.worker.OfflineDatabaseUpdateScheduler

/** 离线号码库分组，沿用原有检查更新和下载事件。 */
@Composable
fun OfflineDatabasePreferences(
    viewModel: SettingViewModel,
    onRequestNotificationPermission: () -> Unit,
) {
    val context = LocalContext.current
    var showAutoCheckIntervalDialog by remember { mutableStateOf(false) }
    val notFoundText = stringResource(R.string.msg_not_found)
    val offlineDatabaseVersion = if (viewModel.offlineDbVersion == notFoundText) {
        stringResource(R.string.setting_offline_database_not_downloaded)
    } else {
        viewModel.offlineDbVersion
    }
    val intervalSummary = if (viewModel.autoCheckUpdate) {
        stringResource(
            R.string.setting_auto_check_update_interval_summary,
            viewModel.autoCheckUpdateIntervalHours,
        )
    } else {
        stringResource(R.string.setting_auto_check_update_interval_requires_enabled)
    }

    SettingsSection(title = stringResource(R.string.setting_group_number_database)) {
        SettingsGroup(
            rows = listOf(
            SettingsRowItem("offline-database") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_offline_database),
                    summary = stringResource(
                        R.string.setting_offline_database_summary,
                        offlineDatabaseVersion,
                    ),
                    icon = Icons.Default.Update,
                    onClick = viewModel::checkUpdate,
                )
            },
            ),
        )
    }

    SettingsSection(title = stringResource(R.string.setting_group_update_checks)) {
        SettingsGroup(
            rows = listOf(
            SettingsRowItem("auto-check-update") { modifier ->
                SettingsSwitchRow(
                    modifier = modifier,
                    checked = viewModel.autoCheckUpdate,
                    title = stringResource(R.string.setting_auto_check_update),
                    summary = stringResource(R.string.setting_auto_check_update_summary),
                    icon = Icons.Default.NotificationsNone,
                    onCheckedChange = { enabled ->
                        when {
                            !enabled -> viewModel.updateAutoCheckUpdate(false)
                            OfflineDatabaseUpdateScheduler.hasNotificationPermission(context) ->
                                viewModel.updateAutoCheckUpdate(true)
                            else -> onRequestNotificationPermission()
                        }
                    },
                )
            },
            SettingsRowItem("auto-check-interval") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    enabled = viewModel.autoCheckUpdate,
                    title = stringResource(R.string.setting_auto_check_update_interval),
                    summary = intervalSummary,
                    icon = Icons.Default.Schedule,
                    onClick = { showAutoCheckIntervalDialog = true },
                )
            },
            ),
        )
    }

    if (showAutoCheckIntervalDialog) {
        var intervalText by remember(showAutoCheckIntervalDialog) {
            mutableStateOf(viewModel.autoCheckUpdateIntervalHours.toString())
        }
        val intervalHours = intervalText.toIntOrNull()
        val minInterval = OfflineDatabaseUpdateScheduler.MIN_UPDATE_INTERVAL_HOURS
        val maxInterval = OfflineDatabaseUpdateScheduler.MAX_UPDATE_INTERVAL_HOURS
        val intervalValid = intervalHours != null && intervalHours in minInterval..maxInterval

        AlertDialog(
            onDismissRequest = { showAutoCheckIntervalDialog = false },
            title = { Text(stringResource(R.string.title_auto_check_update_interval)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { value ->
                            intervalText = value.filter { it.isDigit() }.take(3)
                        },
                        label = {
                            Text(stringResource(R.string.hint_auto_check_update_interval))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = intervalText.isNotBlank() && !intervalValid,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (intervalText.isNotBlank() && !intervalValid) {
                        Text(
                            stringResource(R.string.error_auto_check_update_interval),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = intervalValid,
                    onClick = {
                        viewModel.updateAutoCheckUpdateIntervalHours(intervalHours!!)
                        showAutoCheckIntervalDialog = false
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAutoCheckIntervalDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
