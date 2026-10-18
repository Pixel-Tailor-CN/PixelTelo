package vip.mystery0.pixel.telo.ui.screen.settings

import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ThumbsUpDown
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.query.QueryBackendState
import vip.mystery0.pixel.telo.data.query.QueryBackendType
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

/** 显示与提醒分组；保留震动、通知与当前服务状态的原有门禁。 */
@Composable
fun DisplayPreferences(
    viewModel: SettingViewModel,
    backendState: QueryBackendState,
    feedbackPermissions: List<String>,
    onRequestFeedbackPermissions: (Array<String>) -> Unit,
    onRequestPhoneStatePermission: () -> Unit,
) {
    val context = LocalContext.current
    val selfHostedSelected = when (backendState) {
        is QueryBackendState.Ready -> backendState.type == QueryBackendType.SELF_HOSTED
        is QueryBackendState.Blocked -> true
    }
    val onlineQueryEnabled = !viewModel.noNetworkQuery
    val feedbackEnabled = onlineQueryEnabled && !selfHostedSelected
    val feedbackSummary = when {
        !onlineQueryEnabled -> stringResource(
            R.string.setting_feedback_notification_no_network_summary
        )
        selfHostedSelected -> stringResource(
            R.string.setting_feedback_notification_self_hosted_summary
        )
        else -> stringResource(R.string.setting_feedback_notification_summary)
    }

    SettingsSection(title = stringResource(R.string.setting_group_display_and_feedback)) {
        SettingsGroup(
            rows = listOf(
            SettingsRowItem("local-number-labels") { modifier ->
                SettingsSwitchRow(
                    modifier = modifier,
                    checked = viewModel.showLocalNumberLabels,
                    title = stringResource(R.string.setting_show_local_number_labels),
                    summary = stringResource(R.string.setting_show_local_number_labels_summary),
                    icon = Icons.AutoMirrored.Filled.Label,
                    onCheckedChange = viewModel::updateShowLocalNumberLabels,
                )
            },
            SettingsRowItem("call-state-vibration") { modifier ->
                SettingsSwitchRow(
                    modifier = modifier,
                    checked = viewModel.callStateVibrationEnabled,
                    title = stringResource(R.string.setting_call_state_vibration),
                    summary = stringResource(R.string.setting_call_state_vibration_summary),
                    icon = Icons.Default.Vibration,
                    onCheckedChange = { enabled ->
                        when {
                            !enabled -> viewModel.updateCallStateVibrationEnabled(false)
                            ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.READ_PHONE_STATE,
                            ) == PackageManager.PERMISSION_GRANTED ->
                                viewModel.updateCallStateVibrationEnabled(true)
                            else -> onRequestPhoneStatePermission()
                        }
                    },
                )
            },
            SettingsRowItem("feedback-reminder") { modifier ->
                SettingsSwitchRow(
                    modifier = modifier,
                    checked = viewModel.feedbackNotification,
                    title = stringResource(R.string.setting_feedback_notification),
                    summary = feedbackSummary,
                    icon = Icons.Default.ThumbsUpDown,
                    enabled = feedbackEnabled,
                    onCheckedChange = { enabled ->
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
                )
            },
            ),
        )
    }
}
