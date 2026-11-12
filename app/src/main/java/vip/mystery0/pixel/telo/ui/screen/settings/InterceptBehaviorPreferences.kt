package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.query.QueryBackendState
import vip.mystery0.pixel.telo.data.query.QueryBackendType
import vip.mystery0.pixel.telo.viewmodel.RepeatCallStrategy
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/** 来电识别与拦截分组；只组合现有设置状态和事件。 */
@Composable
fun InterceptBehaviorPreferences(
    viewModel: SettingViewModel,
    backendState: QueryBackendState,
) {
    var showRepeatWindowDialog by remember { mutableStateOf(false) }
    val blockCommunity by viewModel.blockCommunityReports.collectAsState()
    val selfHostedSelected = when (backendState) {
        is QueryBackendState.Ready -> backendState.type == QueryBackendType.SELF_HOSTED
        is QueryBackendState.Blocked -> true
    }
    val onlineQueryEnabled = !viewModel.noNetworkQuery
    val communityBlockEnabled = onlineQueryEnabled && !selfHostedSelected
    val communitySummary = stringResource(
        if (communityBlockEnabled) {
            R.string.community_block_summary
        } else {
            R.string.community_block_disabled_summary
        }
    )

    SettingsSection(title = stringResource(R.string.setting_group_intercept_behavior)) {
        Text(stringResource(R.string.rule_priority_explanation), style = MaterialTheme.typography.bodySmall)
        SettingsGroup(
            rows = listOf(
            SettingsRowItem("notify-only") { modifier ->
                SettingsSwitchRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_notify_only),
                    summary = stringResource(R.string.setting_notify_only_summary),
                    checked = viewModel.notifyOnly,
                    icon = Icons.Default.NotificationsNone,
                    onCheckedChange = viewModel::updateNotifyOnly,
                )
            },
            SettingsRowItem("community-block") { modifier ->
                SettingsSwitchRow(
                    modifier = modifier,
                    title = stringResource(R.string.community_block_setting),
                    summary = communitySummary,
                    checked = blockCommunity,
                    icon = Icons.Default.Block,
                    enabled = communityBlockEnabled,
                    onCheckedChange = viewModel::updateBlockCommunityReports,
                )
            },
            ),
        )
    }

    SettingsSection(title = stringResource(R.string.setting_group_repeat_calls)) {
        SettingsGroup(
            rows = listOf(
            SettingsRowItem("repeat-strategy") { modifier ->
                SettingsChoiceRow(
                    modifier = modifier,
                    value = viewModel.repeatCallStrategy,
                    options = RepeatCallStrategy.entries,
                    onValueChange = viewModel::updateRepeatCallStrategy,
                    title = stringResource(R.string.setting_repeat_call_strategy),
                    summary = stringResource(viewModel.repeatCallStrategy.labelRes()),
                    icon = Icons.Default.Repeat,
                    optionContent = { strategy ->
                        Text(stringResource(strategy.labelRes()))
                    },
                )
            },
            SettingsRowItem("repeat-window") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_repeat_call_window),
                    summary = stringResource(
                        R.string.setting_repeat_call_window_summary,
                        viewModel.repeatCallWindowMinutes,
                    ),
                    icon = Icons.Default.Schedule,
                    onClick = { showRepeatWindowDialog = true },
                )
            },
            ),
        )
    }

    SettingsSection(title = stringResource(R.string.setting_group_records_and_checks)) {
        SettingsGroup(
            rows = listOf(
            SettingsRowItem("always-record") { modifier ->
                SettingsSwitchRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_always_record),
                    summary = stringResource(R.string.setting_always_record_summary),
                    checked = viewModel.alwaysRecord,
                    icon = Icons.AutoMirrored.Filled.FactCheck,
                    onCheckedChange = viewModel::updateAlwaysRecord,
                )
            },
            SettingsRowItem("test-intercept") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.title_test_intercept),
                    summary = stringResource(R.string.summary_test_intercept),
                    icon = Icons.Default.Science,
                    onClick = viewModel::showTestDialog,
                )
            },
            ),
        )
    }

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
                            sliderValue.toInt(),
                        )
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 1f..30f,
                        steps = 28,
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
            },
        )
    }
}

private fun RepeatCallStrategy.labelRes(): Int = when (this) {
    RepeatCallStrategy.UNCHANGED -> R.string.repeat_call_strategy_unchanged
    RepeatCallStrategy.SILENCE -> R.string.repeat_call_strategy_silence
    RepeatCallStrategy.ALLOW -> R.string.repeat_call_strategy_allow
}
