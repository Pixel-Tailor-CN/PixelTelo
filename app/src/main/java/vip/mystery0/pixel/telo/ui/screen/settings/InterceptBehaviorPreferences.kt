package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
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
import vip.mystery0.pixel.telo.viewmodel.RepeatCallStrategy
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/** 拦截行为分类下的设置项。 */
@Composable
fun InterceptBehaviorPreferences(viewModel: SettingViewModel) {
    var showRepeatWindowDialog by remember { mutableStateOf(false) }

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
                Column {
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
