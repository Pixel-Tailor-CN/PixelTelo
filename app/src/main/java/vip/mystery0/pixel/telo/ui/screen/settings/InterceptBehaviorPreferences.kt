package vip.mystery0.pixel.telo.ui.screen.settings

import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ThumbsUpDown
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
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
import vip.mystery0.pixel.telo.ui.components.SingleChoicePreference
import vip.mystery0.pixel.telo.viewmodel.LocationOverlayDisplayMode
import vip.mystery0.pixel.telo.viewmodel.LocationOverlayStyle
import vip.mystery0.pixel.telo.viewmodel.RepeatCallStrategy
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/** 拦截行为分类下的设置项。 */
@Composable
fun InterceptBehaviorPreferences(
    viewModel: SettingViewModel,
    feedbackPermissions: List<String>,
    onRequestFeedbackPermissions: (Array<String>) -> Unit,
) {
    val context = LocalContext.current
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showRepeatWindowDialog by remember { mutableStateOf(false) }
    var showOverlayDurationDialog by remember { mutableStateOf(false) }
    val locationOverlayEnabled = !viewModel.noNetworkQuery

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
                androidx.compose.foundation.layout.Column {
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
        value = viewModel.notifyOnly,
        onValueChange = viewModel::updateNotifyOnly,
        title = { Text(stringResource(R.string.setting_notify_only)) },
        summary = { Text(stringResource(R.string.setting_notify_only_summary)) },
        icon = { Icon(Icons.Default.NotificationsNone, contentDescription = null) }
    )

    SingleChoicePreference(
        value = viewModel.repeatCallStrategy,
        options = RepeatCallStrategy.entries,
        onValueChange = viewModel::updateRepeatCallStrategy,
        title = { Text(stringResource(R.string.setting_repeat_call_strategy)) },
        summary = {
            Text(stringResource(viewModel.repeatCallStrategy.labelRes()))
        },
        icon = { Icon(Icons.Default.Repeat, contentDescription = null) },
        optionContent = { strategy ->
            Text(stringResource(strategy.labelRes()))
        }
    )

    Preference(
        title = { Text(stringResource(R.string.setting_repeat_call_window)) },
        summary = {
            Text(
                stringResource(
                    R.string.setting_repeat_call_window_summary,
                    viewModel.repeatCallWindowMinutes
                )
            )
        },
        icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
        onClick = { showRepeatWindowDialog = true }
    )

    if (showRepeatWindowDialog) {
        var sliderValue by remember {
            mutableFloatStateOf(viewModel.repeatCallWindowMinutes.toFloat())
        }
        AlertDialog(
            onDismissRequest = { showRepeatWindowDialog = false },
            title = { Text(stringResource(R.string.setting_repeat_call_window)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        stringResource(
                            R.string.setting_repeat_call_window_summary,
                            sliderValue.toInt()
                        )
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 1f..30f,
                        steps = 28
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateRepeatCallWindowMinutes(sliderValue.toInt())
                        showRepeatWindowDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRepeatWindowDialog = false }) {
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

    SwitchPreference(
        value = viewModel.showLocationOverlay,
        onValueChange = viewModel::updateShowLocationOverlay,
        enabled = locationOverlayEnabled,
        title = { Text(stringResource(R.string.setting_show_location_overlay)) },
        summary = {
            Text(
                stringResource(
                    if (locationOverlayEnabled) {
                        R.string.setting_show_location_overlay_summary
                    } else {
                        R.string.setting_show_location_overlay_disabled_summary
                    }
                )
            )
        },
        icon = { Icon(Icons.Default.Map, contentDescription = null) }
    )

    SingleChoicePreference(
        value = viewModel.locationOverlayDisplayMode,
        options = LocationOverlayDisplayMode.entries,
        onValueChange = viewModel::updateLocationOverlayDisplayMode,
        enabled = locationOverlayEnabled,
        title = { Text(stringResource(R.string.setting_location_overlay_display_mode)) },
        summary = {
            Text(stringResource(viewModel.locationOverlayDisplayMode.labelRes()))
        },
        icon = { Icon(Icons.Default.Visibility, contentDescription = null) },
        optionContent = { mode ->
            Text(stringResource(mode.labelRes()))
        }
    )

    if (viewModel.locationOverlayDisplayMode == LocationOverlayDisplayMode.FIXED_DURATION) {
        Preference(
            enabled = locationOverlayEnabled,
            title = { Text(stringResource(R.string.setting_location_overlay_duration)) },
            summary = {
                Text(
                    stringResource(
                        R.string.setting_location_overlay_duration_summary,
                        viewModel.locationOverlayDurationSeconds
                    )
                )
            },
            icon = { Icon(Icons.Default.Timer, contentDescription = null) },
            onClick = { showOverlayDurationDialog = true }
        )
    }

    SingleChoicePreference(
        value = viewModel.locationOverlayStyle,
        options = LocationOverlayStyle.entries,
        onValueChange = viewModel::updateLocationOverlayStyle,
        enabled = locationOverlayEnabled,
        title = { Text(stringResource(R.string.setting_location_overlay_style)) },
        summary = {
            Text(stringResource(viewModel.locationOverlayStyle.labelRes()))
        },
        icon = { Icon(Icons.Default.Palette, contentDescription = null) },
        optionContent = { style ->
            Text(stringResource(style.labelRes()))
        }
    )

    Preference(
        title = { Text(stringResource(R.string.setting_adjust_location_overlay)) },
        summary = {
            Text(
                stringResource(
                    if (viewModel.showLocationOverlayAdjuster) {
                        R.string.setting_adjust_location_overlay_active_summary
                    } else {
                        R.string.setting_adjust_location_overlay_summary
                    }
                )
            )
        },
        icon = { Icon(Icons.Default.OpenWith, contentDescription = null) },
        onClick = viewModel::toggleLocationOverlayAdjuster
    )

    if (showOverlayDurationDialog) {
        var sliderValue by remember {
            mutableFloatStateOf(viewModel.locationOverlayDurationSeconds.toFloat())
        }
        AlertDialog(
            onDismissRequest = { showOverlayDurationDialog = false },
            title = { Text(stringResource(R.string.setting_location_overlay_duration)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        stringResource(
                            R.string.setting_location_overlay_duration_summary,
                            sliderValue.toInt()
                        )
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange =
                            SettingViewModel.MIN_LOCATION_OVERLAY_DURATION_SECONDS.toFloat()..
                                    SettingViewModel.MAX_LOCATION_OVERLAY_DURATION_SECONDS.toFloat(),
                        steps =
                            SettingViewModel.MAX_LOCATION_OVERLAY_DURATION_SECONDS -
                                    SettingViewModel.MIN_LOCATION_OVERLAY_DURATION_SECONDS -
                                    1
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateLocationOverlayDurationSeconds(sliderValue.toInt())
                        showOverlayDurationDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showOverlayDurationDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    SwitchPreference(
        value = viewModel.alwaysRecord,
        onValueChange = viewModel::updateAlwaysRecord,
        title = { Text(stringResource(R.string.setting_always_record)) },
        summary = { Text(stringResource(R.string.setting_always_record_summary)) },
        icon = { Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null) }
    )
}

private fun RepeatCallStrategy.labelRes(): Int = when (this) {
    RepeatCallStrategy.UNCHANGED -> R.string.repeat_call_strategy_unchanged
    RepeatCallStrategy.SILENCE -> R.string.repeat_call_strategy_silence
    RepeatCallStrategy.ALLOW -> R.string.repeat_call_strategy_allow
}

private fun LocationOverlayDisplayMode.labelRes(): Int = when (this) {
    LocationOverlayDisplayMode.FIXED_DURATION ->
        R.string.location_overlay_display_mode_fixed

    LocationOverlayDisplayMode.UNTIL_CALL_END ->
        R.string.location_overlay_display_mode_until_call_end
}

private fun LocationOverlayStyle.labelRes(): Int = when (this) {
    LocationOverlayStyle.CARD -> R.string.location_overlay_style_card
    LocationOverlayStyle.MINIMAL -> R.string.location_overlay_style_minimal
}
