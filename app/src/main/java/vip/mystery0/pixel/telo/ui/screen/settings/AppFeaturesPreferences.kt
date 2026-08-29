package vip.mystery0.pixel.telo.ui.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.core.content.ContextCompat
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel
import vip.mystery0.pixel.telo.worker.OfflineDatabaseUpdateScheduler

/** 应用功能分类下的设置项。 */
@Composable
fun AppFeaturesPreferences(
    viewModel: SettingViewModel,
    onRequestNotificationPermission: () -> Unit,
    onRequestPhoneStatePermission: () -> Unit,
    onNavigateToLocalNumberLabels: () -> Unit,
) {
    val context = LocalContext.current
    var showAutoCheckIntervalDialog by remember { mutableStateOf(false) }

    Preference(
        title = { Text(stringResource(R.string.setting_update_offline_data)) },
        summary = {
            Text(stringResource(R.string.summary_current_version) + viewModel.offlineDbVersion)
        },
        icon = { Icon(Icons.Default.Update, contentDescription = null) },
        onClick = viewModel::checkUpdate
    )

    SwitchPreference(
        value = viewModel.autoCheckUpdate,
        onValueChange = { enabled ->
            when {
                !enabled -> viewModel.updateAutoCheckUpdate(false)
                OfflineDatabaseUpdateScheduler.hasNotificationPermission(context) ->
                    viewModel.updateAutoCheckUpdate(true)

                else -> onRequestNotificationPermission()
            }
        },
        title = { Text(stringResource(R.string.setting_auto_check_update)) },
        summary = { Text(stringResource(R.string.setting_auto_check_update_summary)) },
        icon = { Icon(Icons.Default.NotificationsNone, contentDescription = null) }
    )

    Preference(
        enabled = viewModel.autoCheckUpdate,
        title = { Text(stringResource(R.string.setting_auto_check_update_interval)) },
        summary = {
            Text(
                stringResource(
                    R.string.setting_auto_check_update_interval_summary,
                    viewModel.autoCheckUpdateIntervalHours
                )
            )
        },
        icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
        onClick = {
            if (viewModel.autoCheckUpdate) {
                showAutoCheckIntervalDialog = true
            }
        }
    )

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
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (intervalText.isNotBlank() && !intervalValid) {
                        Text(
                            stringResource(R.string.error_auto_check_update_interval),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
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
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAutoCheckIntervalDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    SwitchPreference(
        value = viewModel.showLocalNumberLabels,
        onValueChange = viewModel::updateShowLocalNumberLabels,
        title = { Text(stringResource(R.string.setting_show_local_number_labels)) },
        summary = { Text(stringResource(R.string.setting_show_local_number_labels_summary)) },
        icon = { Icon(Icons.Default.Label, contentDescription = null) },
    )

    Preference(
        title = { Text(stringResource(R.string.setting_local_number_labels)) },
        summary = { Text(stringResource(R.string.setting_local_number_labels_summary)) },
        icon = { Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null) },
        onClick = onNavigateToLocalNumberLabels,
    )

    Preference(
        title = { Text(stringResource(R.string.title_test_intercept)) },
        summary = { Text(stringResource(R.string.summary_test_intercept)) },
        icon = { Icon(Icons.Default.PhoneInTalk, contentDescription = null) },
        onClick = viewModel::showTestDialog
    )

    SwitchPreference(
        value = viewModel.callStateVibrationEnabled,
        onValueChange = { enabled ->
            when {
                !enabled -> viewModel.updateCallStateVibrationEnabled(false)
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED ->
                    viewModel.updateCallStateVibrationEnabled(true)

                else -> onRequestPhoneStatePermission()
            }
        },
        title = { Text(stringResource(R.string.setting_call_state_vibration)) },
        summary = { Text(stringResource(R.string.setting_call_state_vibration_summary)) },
        icon = { Icon(Icons.Default.Vibration, contentDescription = null) }
    )
}
