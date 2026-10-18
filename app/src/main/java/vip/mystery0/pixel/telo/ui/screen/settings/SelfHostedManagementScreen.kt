package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.query.QueryBackendState
import vip.mystery0.pixel.telo.data.query.QueryBackendType
import vip.mystery0.pixel.telo.data.query.SelfHostedConnectionState
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

enum class SelfHostedPairingEntry { SCAN, PASTE, MANUAL }

@Composable
fun SelfHostedManagementScreen(
    viewModel: SettingViewModel,
    onPairing: (SelfHostedPairingEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backend by viewModel.queryBackendState.collectAsStateWithLifecycle()
    val connection by viewModel.selfHostedConnectionState.collectAsStateWithLifecycle()
    val config = when (val state = connection) {
        is SelfHostedConnectionState.Ready -> state.config
        is SelfHostedConnectionState.Blocked -> state.config
        SelfHostedConnectionState.NotConfigured -> null
    }
    val activeBackend = backend as? QueryBackendState.Ready
    val officialActive = activeBackend?.type == QueryBackendType.OFFICIAL
    val selfHostedActive = activeBackend?.type == QueryBackendType.SELF_HOSTED
    val selectedSelfHosted = when (val currentBackend = backend) {
        is QueryBackendState.Blocked -> true
        is QueryBackendState.Ready -> currentBackend.type == QueryBackendType.SELF_HOSTED
    }
    val blockedReason = (backend as? QueryBackendState.Blocked)?.reason
        ?: (connection as? SelfHostedConnectionState.Blocked)?.reason
    val selfHostedStatus = when {
        blockedReason != null && selectedSelfHosted -> R.string.status_self_hosted_blocked
        blockedReason != null -> R.string.status_self_hosted_saved_blocked
        selfHostedActive -> R.string.status_self_hosted_active
        config != null -> R.string.status_self_hosted_saved
        else -> R.string.status_self_hosted_not_configured
    }
    val actionEnabled = !viewModel.selfHostedValidationInProgress

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsSection(title = stringResource(R.string.section_official_service)) {
            SettingsGroup(
                listOf(
                    SettingsRowItem("official-status") { row ->
                        SettingsItemRow(
                            modifier = row,
                            title = stringResource(
                                if (officialActive) R.string.status_official_active
                                else R.string.status_official_not_enabled,
                            ),
                            summary = if (officialActive) {
                                stringResource(R.string.msg_self_hosted_manager_intro)
                            } else null,
                            icon = Icons.Filled.Cloud,
                            titleColor = if (officialActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            iconTint = if (officialActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ),
            )
        }

        SettingsSection(title = stringResource(R.string.section_self_hosted_service)) {
            val rows = buildList {
                add(SettingsRowItem("self-hosted-status") { row ->
                    val summary = when {
                        blockedReason != null -> stringResource(blockedReason.messageRes())
                        config == null -> stringResource(R.string.msg_self_hosted_unconfigured)
                        else -> null
                    }
                    SettingsItemRow(
                        modifier = row,
                        title = stringResource(selfHostedStatus),
                        summary = summary,
                        icon = Icons.Filled.Dns,
                        titleColor = if (selfHostedActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        iconTint = if (selfHostedActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        summaryColor = if (blockedReason != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                })

                if (config != null) {
                    add(SettingsRowItem("self-hosted-details") { row ->
                        SettingsItemRow(
                            modifier = row,
                            title = stringResource(R.string.label_self_hosted_config_details),
                            summaryContent = { SelfHostedBackendSummary(config) },
                            icon = Icons.Filled.NetworkCheck,
                        )
                    })
                    add(SettingsRowItem("revalidate") { row ->
                        SettingsItemRow(
                            modifier = row,
                            title = stringResource(
                                if (selectedSelfHosted) R.string.action_revalidate_self_hosted
                                else R.string.action_test_and_enable_self_hosted,
                            ),
                            icon = Icons.Filled.NetworkCheck,
                            enabled = actionEnabled,
                            onClick = viewModel::revalidateSelfHosted,
                        )
                    })
                    add(SettingsRowItem("modify") { row ->
                        SettingsItemRow(
                            modifier = row,
                            title = stringResource(R.string.action_modify_self_hosted),
                            icon = Icons.Filled.Edit,
                            enabled = actionEnabled,
                            onClick = { onPairing(SelfHostedPairingEntry.MANUAL) },
                        )
                    })
                } else {
                    add(SettingsRowItem("scan") { row ->
                        SettingsItemRow(
                            modifier = row,
                            title = stringResource(R.string.action_pairing_scan),
                            icon = Icons.Filled.QrCodeScanner,
                            enabled = actionEnabled,
                            onClick = { onPairing(SelfHostedPairingEntry.SCAN) },
                        )
                    })
                    add(SettingsRowItem("paste") { row ->
                        SettingsItemRow(
                            modifier = row,
                            title = stringResource(R.string.action_pairing_paste),
                            icon = Icons.Filled.ContentPaste,
                            enabled = actionEnabled,
                            onClick = { onPairing(SelfHostedPairingEntry.PASTE) },
                        )
                    })
                    add(SettingsRowItem("manual") { row ->
                        SettingsItemRow(
                            modifier = row,
                            title = stringResource(R.string.action_pairing_manual),
                            icon = Icons.Filled.Edit,
                            enabled = actionEnabled,
                            onClick = { onPairing(SelfHostedPairingEntry.MANUAL) },
                        )
                    })
                }
                if (!officialActive) add(SettingsRowItem("official") { row ->
                    SettingsItemRow(
                        modifier = row,
                        title = stringResource(R.string.action_use_official_backend),
                        icon = Icons.Filled.Cloud,
                        enabled = actionEnabled,
                        onClick = viewModel::useOfficialBackend,
                    )
                })
            }
            SettingsGroup(rows)
        }

        viewModel.selfHostedValidationError?.let { error ->
            Text(
                text = stringResource(error.messageRes()),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (viewModel.selfHostedValidationInProgress) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.msg_self_hosted_validating))
            }
        }
    }
}
