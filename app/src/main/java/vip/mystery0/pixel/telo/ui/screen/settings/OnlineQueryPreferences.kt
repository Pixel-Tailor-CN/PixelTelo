package vip.mystery0.pixel.telo.ui.screen.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.ThumbsUpDown
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/** 在线查询分类下的设置项。 */
@Composable
fun OnlineQueryPreferences(
    viewModel: SettingViewModel,
    feedbackPermissions: List<String>,
    onRequestFeedbackPermissions: (Array<String>) -> Unit,
) {
    val context = LocalContext.current
    var showTimeoutDialog by remember { mutableStateOf(false) }

    Preference(
        title = { Text(stringResource(R.string.setting_query_sources)) },
        summary = { Text(stringResource(R.string.setting_query_sources_summary)) },
        icon = { Icon(Icons.Default.Dns, contentDescription = null) },
        onClick = viewModel::openQuerySourceSettings
    )

    SwitchPreference(
        value = viewModel.feedbackNotification,
        onValueChange = { enabled ->
            if (!enabled) {
                viewModel.updateFeedbackNotification(false)
            } else {
                val missingPermissions = feedbackPermissions.filter { permission ->
                    ContextCompat.checkSelfPermission(context, permission) !=
                            PackageManager.PERMISSION_GRANTED
                }
                if (missingPermissions.isEmpty()) {
                    viewModel.updateFeedbackNotification(true)
                } else {
                    onRequestFeedbackPermissions(missingPermissions.toTypedArray())
                }
            }
        },
        title = { Text(stringResource(R.string.setting_feedback_notification)) },
        summary = { Text(stringResource(R.string.setting_feedback_notification_summary)) },
        icon = { Icon(Icons.Default.ThumbsUpDown, contentDescription = null) }
    )

    Preference(
        title = { Text(stringResource(R.string.setting_network_timeout)) },
        summary = {
            Text(
                stringResource(
                    R.string.setting_network_timeout_summary,
                    viewModel.networkTimeout
                )
            )
        },
        icon = { Icon(Icons.Default.NetworkCheck, contentDescription = null) },
        onClick = { showTimeoutDialog = true }
    )

    if (showTimeoutDialog) {
        var sliderValue by remember {
            mutableFloatStateOf(viewModel.networkTimeout.toFloat())
        }
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text(stringResource(R.string.setting_network_timeout)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.setting_network_timeout_summary,
                            sliderValue.toInt()
                        )
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateNetworkTimeout(sliderValue.toInt())
                        showTimeoutDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTimeoutDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    SwitchPreference(
        value = viewModel.noNetworkQuery,
        onValueChange = viewModel::updateNoNetworkQuery,
        title = { Text(stringResource(R.string.setting_no_network_query)) },
        summary = { Text(stringResource(R.string.setting_no_network_query_summary)) },
        icon = { Icon(Icons.Default.WifiOff, contentDescription = null) }
    )
}
