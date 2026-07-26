package vip.mystery0.pixel.telo.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.viewmodel.LocationOverlayDisplayMode
import vip.mystery0.pixel.telo.viewmodel.LocationOverlayStyle
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/**
 * 归属地悬浮窗设置区域。
 *
 * 仅负责偏好项与选择对话框，实际窗口生命周期由 [vip.mystery0.pixel.telo.service.IncomingCallOverlay]
 * 管理。
 */
@Composable
internal fun LocationOverlaySettings(viewModel: SettingViewModel) {
    var showDisplayModeDialog by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }
    val enabled = !viewModel.noNetworkQuery

    SwitchPreference(
        value = viewModel.showLocationOverlay,
        onValueChange = viewModel::updateShowLocationOverlay,
        enabled = enabled,
        title = { Text(stringResource(R.string.setting_show_location_overlay)) },
        summary = {
            Text(
                stringResource(
                    if (enabled) {
                        R.string.setting_show_location_overlay_summary
                    } else {
                        R.string.setting_show_location_overlay_disabled_summary
                    }
                )
            )
        },
        icon = { Icon(Icons.Default.Map, contentDescription = null) }
    )

    Preference(
        enabled = enabled,
        title = { Text(stringResource(R.string.setting_location_overlay_display_mode)) },
        summary = {
            Text(stringResource(viewModel.locationOverlayDisplayMode.labelRes()))
        },
        icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
        onClick = { showDisplayModeDialog = true }
    )

    if (viewModel.locationOverlayDisplayMode == LocationOverlayDisplayMode.FIXED_DURATION) {
        Preference(
            enabled = enabled,
            title = { Text(stringResource(R.string.setting_location_overlay_duration)) },
            summary = {
                Text(
                    stringResource(
                        R.string.setting_location_overlay_duration_summary,
                        viewModel.locationOverlayDurationSeconds
                    )
                )
            },
            icon = { Icon(Icons.Default.Schedule, contentDescription = null) },
            onClick = { showDurationDialog = true }
        )
    }

    Preference(
        enabled = enabled,
        title = { Text(stringResource(R.string.setting_location_overlay_style)) },
        summary = {
            Text(stringResource(viewModel.locationOverlayStyle.labelRes()))
        },
        icon = { Icon(Icons.Default.Palette, contentDescription = null) },
        onClick = { showStyleDialog = true }
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

    if (showDisplayModeDialog) {
        ChoiceDialog(
            titleRes = R.string.setting_location_overlay_display_mode,
            entries = LocationOverlayDisplayMode.entries,
            selected = viewModel.locationOverlayDisplayMode,
            labelRes = LocationOverlayDisplayMode::labelRes,
            onDismiss = { showDisplayModeDialog = false },
            onSelected = { mode ->
                viewModel.updateLocationOverlayDisplayMode(mode)
                showDisplayModeDialog = false
            }
        )
    }

    if (showStyleDialog) {
        ChoiceDialog(
            titleRes = R.string.setting_location_overlay_style,
            entries = LocationOverlayStyle.entries,
            selected = viewModel.locationOverlayStyle,
            labelRes = LocationOverlayStyle::labelRes,
            onDismiss = { showStyleDialog = false },
            onSelected = { style ->
                viewModel.updateLocationOverlayStyle(style)
                showStyleDialog = false
            }
        )
    }

    if (showDurationDialog) {
        var sliderValue by remember {
            mutableFloatStateOf(viewModel.locationOverlayDurationSeconds.toFloat())
        }
        AlertDialog(
            onDismissRequest = { showDurationDialog = false },
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
                        showDurationDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDurationDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    @StringRes titleRes: Int,
    entries: List<T>,
    selected: T,
    labelRes: (T) -> Int,
    onDismiss: () -> Unit,
    onSelected: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(entry) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == entry,
                            onClick = { onSelected(entry) }
                        )
                        Text(stringResource(labelRes(entry)))
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@StringRes
private fun LocationOverlayDisplayMode.labelRes(): Int = when (this) {
    LocationOverlayDisplayMode.FIXED_DURATION ->
        R.string.location_overlay_display_mode_fixed

    LocationOverlayDisplayMode.UNTIL_CALL_END ->
        R.string.location_overlay_display_mode_until_call_end
}

@StringRes
private fun LocationOverlayStyle.labelRes(): Int = when (this) {
    LocationOverlayStyle.CARD -> R.string.location_overlay_style_card
    LocationOverlayStyle.MINIMAL -> R.string.location_overlay_style_minimal
}
