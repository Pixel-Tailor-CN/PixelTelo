package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.res.stringResource
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.ui.components.SingleChoicePreference
import vip.mystery0.pixel.telo.viewmodel.LocationOverlayDisplayMode
import vip.mystery0.pixel.telo.viewmodel.LocationOverlayStyle
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/** 悬浮窗分类下的设置项。 */
@Composable
fun LocationOverlayPreferences(viewModel: SettingViewModel) {
    var showOverlayDurationDialog by remember { mutableStateOf(false) }
    val locationOverlayEnabled = !viewModel.noNetworkQuery

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
                Column {
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
