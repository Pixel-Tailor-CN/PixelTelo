package vip.mystery0.pixel.telo.ui.screen.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.query.QueryBackendState
import vip.mystery0.pixel.telo.data.query.QueryBackendType
import vip.mystery0.pixel.telo.data.query.SelfHostedConnectionState
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

private const val SELF_HOSTED_DOCUMENTATION_URL =
    "https://github.com/Pixel-Tailor-CN/pixel-telo-mast-selfhost"

/** 联网查询分组，继续使用既有 Backend/source 状态及联网门禁。 */
@Composable
fun OnlineQueryPreferences(
    viewModel: SettingViewModel,
    backendState: QueryBackendState,
    selfHostedConnectionState: SelfHostedConnectionState,
) {
    val context = LocalContext.current
    val externalLinkUnavailableMessage = stringResource(R.string.error_external_link_unavailable)
    var showTimeoutDialog by remember { mutableStateOf(false) }
    val querySourceState by viewModel.querySourceState.collectAsState()
    val selfHostedSelected = when (backendState) {
        is QueryBackendState.Ready -> backendState.type == QueryBackendType.SELF_HOSTED
        is QueryBackendState.Blocked -> true
    }
    val selfHostedConfig = when (selfHostedConnectionState) {
        is SelfHostedConnectionState.Ready -> selfHostedConnectionState.config
        is SelfHostedConnectionState.Blocked -> selfHostedConnectionState.config
        SelfHostedConnectionState.NotConfigured -> null
    }
    val sourceMatchesBackend = backendState is QueryBackendState.Ready &&
        querySourceState.backendId == backendState.backendId
    val onlineQueryEnabled = !viewModel.noNetworkQuery
    val disabledSummary = stringResource(R.string.setting_requires_network_enabled)

    SettingsSection(title = stringResource(R.string.setting_group_query_service)) {
        SettingsGroup(
            rows = listOf(
            SettingsRowItem("no-network-query") { modifier ->
                SettingsSwitchRow(
                    modifier = modifier,
                    checked = viewModel.noNetworkQuery,
                    title = stringResource(R.string.setting_no_network_query),
                    summary = stringResource(R.string.setting_no_network_query_summary),
                    icon = Icons.Default.WifiOff,
                    onCheckedChange = viewModel::updateNoNetworkQuery,
                )
            },
            SettingsRowItem("query-backend") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_query_backend),
                    summaryContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (!onlineQueryEnabled) {
                                Text(
                                    disabledSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selfHostedSelected) {
                                if (selfHostedConfig != null) {
                                    SelfHostedBackendSummary(selfHostedConfig)
                                } else {
                                    Text(
                                        stringResource(
                                            R.string.setting_query_backend_self_hosted_unavailable_summary
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (backendState is QueryBackendState.Blocked) {
                                    Text(
                                        stringResource(backendState.reason.messageRes()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            } else {
                                Text(
                                    stringResource(R.string.setting_query_backend_official_summary),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    icon = Icons.Default.Cloud,
                    enabled = onlineQueryEnabled,
                    onClick = viewModel::openSelfHostedConfig,
                )
            },
            SettingsRowItem("query-sources") { modifier ->
                val sourceSummary = when {
                    !onlineQueryEnabled -> disabledSummary
                    backendState is QueryBackendState.Blocked ->
                        stringResource(R.string.setting_query_sources_requires_available_backend)
                    backendState is QueryBackendState.Ready && !sourceMatchesBackend ->
                        stringResource(R.string.msg_query_sources_loading)
                    else -> stringResource(R.string.setting_query_sources_summary)
                }
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_query_sources),
                    summary = sourceSummary,
                    icon = Icons.Default.Dns,
                    enabled = onlineQueryEnabled && sourceMatchesBackend,
                    onClick = viewModel::openQuerySourceSettings,
                )
            },
            SettingsRowItem("network-timeout") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_network_timeout),
                    summary = if (onlineQueryEnabled) {
                        stringResource(R.string.setting_network_timeout_summary, viewModel.networkTimeout)
                    } else {
                        disabledSummary
                    },
                    icon = Icons.Default.NetworkCheck,
                    enabled = onlineQueryEnabled,
                    onClick = { showTimeoutDialog = true },
                )
            },
            ),
        )
    }

    SettingsSection(title = stringResource(R.string.setting_group_help)) {
        SettingsGroup(
            rows = listOf(
            SettingsRowItem("self-hosted-documentation") { modifier ->
                SettingsItemRow(
                    modifier = modifier,
                    title = stringResource(R.string.setting_self_hosted_documentation),
                    summary = stringResource(R.string.setting_self_hosted_documentation_summary),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    trailing = SettingsRowTrailing.EXTERNAL_LINK,
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, SELF_HOSTED_DOCUMENTATION_URL.toUri())
                            )
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(
                                context,
                                externalLinkUnavailableMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            },
            ),
        )
    }

    if (showTimeoutDialog) {
        var sliderValue by remember {
            mutableFloatStateOf(viewModel.networkTimeout.toFloat())
        }
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text(stringResource(R.string.setting_network_timeout)) },
            text = {
                Column {
                    Text(stringResource(R.string.query_timeout_explanation), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(
                            R.string.setting_network_timeout_summary,
                            sliderValue.toInt(),
                        )
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 1f..10f,
                        steps = 8,
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
            },
        )
    }
}
