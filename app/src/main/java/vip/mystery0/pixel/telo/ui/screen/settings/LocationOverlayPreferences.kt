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
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.viewmodel.LocationOverlayDisplayMode
import vip.mystery0.pixel.telo.viewmodel.LocationOverlayStyle
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/** 悬浮窗分组；参数仍只受联网设置约束，不受显示开关约束。 */
@Composable
fun LocationOverlayPreferences(viewModel: SettingViewModel) {
    var showOverlayDurationDialog by remember { mutableStateOf(false) }
    val locationOverlayEnabled = !viewModel.noNetworkQuery
    val disabledSummary = stringResource(R.string.setting_requires_network_enabled)

    val rows = buildList {
        add(SettingsRowItem("show-location-overlay") { modifier ->
            SettingsSwitchRow(
                modifier = modifier,
                checked = viewModel.showLocationOverlay,
                title = stringResource(R.string.setting_show_location_overlay),
                summary = stringResource(
                    if (locationOverlayEnabled) {
                        R.string.setting_show_location_overlay_summary
                    } else {
                        R.string.setting_show_location_overlay_disabled_summary
                    }
                ),
                icon = Icons.Default.Map,
                enabled = locationOverlayEnabled,
                onCheckedChange = viewModel::updateShowLocationOverlay,
            )
        })
        add(SettingsRowItem("location-display-mode") { modifier ->
            SettingsChoiceRow(
                modifier = modifier,
                value = viewModel.locationOverlayDisplayMode,
                options = LocationOverlayDisplayMode.entries,
                onValueChange = viewModel::updateLocationOverlayDisplayMode,
                title = stringResource(R.string.setting_location_overlay_display_mode),
                summary = if (locationOverlayEnabled) {
                    stringResource(viewModel.locationOverlayDisplayMode.labelRes())
                } else {
                    "${stringResource(viewModel.locationOverlayDisplayMode.labelRes())} · $disabledSummary"
                },
                icon = Icons.Default.Visibility,
                enabled = locationOverlayEnabled,
                optionContent = { mode -> Text(stringResource(mode.labelRes())) },
            )
        })
        if (viewModel.locationOverlayDisplayMode == LocationOverlayDisplayMode.FIXED_DURATION) {
            add(SettingsRowItem("location-duration") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    enabled = locationOverlayEnabled,
                    title = stringResource(R.string.setting_location_overlay_duration),
                    summary = if (locationOverlayEnabled) {
                        stringResource(
                            R.string.setting_location_overlay_duration_summary,
                            viewModel.locationOverlayDurationSeconds,
                        )
                    } else {
                        disabledSummary
                    },
                    icon = Icons.Default.Timer,
                    onClick = { showOverlayDurationDialog = true },
                )
            })
        }
        add(SettingsRowItem("location-style") { modifier ->
            SettingsChoiceRow(
                modifier = modifier,
                value = viewModel.locationOverlayStyle,
                options = LocationOverlayStyle.entries,
                onValueChange = viewModel::updateLocationOverlayStyle,
                title = stringResource(R.string.setting_location_overlay_style),
                summary = if (locationOverlayEnabled) {
                    stringResource(viewModel.locationOverlayStyle.labelRes())
                } else {
                    "${stringResource(viewModel.locationOverlayStyle.labelRes())} · $disabledSummary"
                },
                icon = Icons.Default.Palette,
                enabled = locationOverlayEnabled,
                optionContent = { style -> Text(stringResource(style.labelRes())) },
            )
        })
        add(SettingsRowItem("adjust-location-overlay") { modifier ->
            SettingsItemRow(
                modifier = modifier,
                title = stringResource(R.string.setting_adjust_location_overlay),
                summary = stringResource(
                    if (viewModel.showLocationOverlayAdjuster) {
                        R.string.setting_adjust_location_overlay_active_summary
                    } else {
                        R.string.setting_adjust_location_overlay_summary
                    }
                ),
                icon = Icons.Default.OpenWith,
                onClick = viewModel::toggleLocationOverlayAdjuster,
            )
        })
    }

    SettingsSection(title = stringResource(R.string.setting_group_floating_window)) {
        SettingsGroup(rows)
    }

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
                            sliderValue.toInt(),
                        )
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange =
                            SettingViewModel.MIN_LOCATION_OVERLAY_DURATION_SECONDS.toFloat()..
                                SettingViewModel.MAX_LOCATION_OVERLAY_DURATION_SECONDS.toFloat(),
                        steps = SettingViewModel.MAX_LOCATION_OVERLAY_DURATION_SECONDS -
                            SettingViewModel.MIN_LOCATION_OVERLAY_DURATION_SECONDS - 1,
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
            },
        )
    }
}

private fun LocationOverlayDisplayMode.labelRes(): Int = when (this) {
    LocationOverlayDisplayMode.FIXED_DURATION -> R.string.location_overlay_display_mode_fixed
    LocationOverlayDisplayMode.UNTIL_CALL_END -> R.string.location_overlay_display_mode_until_call_end
}

private fun LocationOverlayStyle.labelRes(): Int = when (this) {
    LocationOverlayStyle.CARD -> R.string.location_overlay_style_card
    LocationOverlayStyle.MINIMAL -> R.string.location_overlay_style_minimal
}
