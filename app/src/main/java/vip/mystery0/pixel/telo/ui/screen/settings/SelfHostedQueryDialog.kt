package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.query.SelfHostedBlockReason
import vip.mystery0.pixel.telo.data.query.SelfHostedErrorCategory
import vip.mystery0.pixel.telo.data.query.VerifiedSelfHostedConfig

/** 已配置自建服务的管理弹窗；编辑操作转到独立配对页。 */
@Composable
fun SelfHostedQueryDialog(
    currentConfig: VerifiedSelfHostedConfig?,
    blockedReason: SelfHostedBlockReason?,
    validationInProgress: Boolean,
    validationError: SelfHostedErrorCategory?,
    onRevalidate: () -> Unit,
    onEdit: () -> Unit,
    onUseOfficial: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!validationInProgress) onDismiss() },
        title = { Text(stringResource(R.string.title_self_hosted_management)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (currentConfig != null) {
                    SelfHostedBackendSummary(currentConfig)
                } else {
                    Text(stringResource(R.string.setting_query_backend_self_hosted_unavailable_summary))
                }
                Text(stringResource(
                    if (blockedReason == null) R.string.msg_self_hosted_management_hint
                    else R.string.msg_self_hosted_blocked_hint,
                ))
                OutlinedButton(onClick = onRevalidate, enabled = !validationInProgress,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_revalidate_self_hosted))
                }
                OutlinedButton(onClick = onEdit, enabled = !validationInProgress,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_modify_self_hosted))
                }
                TextButton(onClick = onUseOfficial, enabled = !validationInProgress,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_use_official_backend))
                }
                val errorRes = validationError?.messageRes() ?: blockedReason?.messageRes()
                if (errorRes != null) {
                    Text(stringResource(errorRes), color = MaterialTheme.colorScheme.error)
                }
                if (validationInProgress) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.msg_self_hosted_validating))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, enabled = !validationInProgress) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

/** 分行展示已验证配置中的服务地址、版本和验证时间。 */
@Composable
fun SelfHostedBackendSummary(config: VerifiedSelfHostedConfig) {
    val verifiedAt = remember(config.verifiedAtEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(config.verifiedAtEpochMillis))
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(
                R.string.setting_query_backend_self_hosted_address,
                config.baseUrl,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.setting_query_backend_self_hosted_version,
                config.version,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.setting_query_backend_self_hosted_verified_at,
                verifiedAt,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@StringRes
internal fun SelfHostedErrorCategory.messageRes(): Int = when (this) {
    SelfHostedErrorCategory.CONFIGURATION -> R.string.error_self_hosted_url
    SelfHostedErrorCategory.CREDENTIALS -> R.string.error_self_hosted_token
    SelfHostedErrorCategory.TLS -> R.string.error_self_hosted_tls
    SelfHostedErrorCategory.SPKI_PIN -> R.string.error_self_hosted_spki_pin
    SelfHostedErrorCategory.SERVER_VERSION -> R.string.error_self_hosted_server_version
    SelfHostedErrorCategory.API_VERSION,
    SelfHostedErrorCategory.SERVICE,
    SelfHostedErrorCategory.CAPABILITY,
    SelfHostedErrorCategory.SERVER_RESPONSE,
    -> R.string.error_self_hosted_protocol
    SelfHostedErrorCategory.INSTANCE_CHANGED,
    SelfHostedErrorCategory.IDENTITY_HEADERS,
    -> R.string.error_self_hosted_identity
    SelfHostedErrorCategory.NETWORK -> R.string.error_self_hosted_network
    SelfHostedErrorCategory.STORAGE -> R.string.error_self_hosted_storage
    SelfHostedErrorCategory.CANCELLED -> R.string.error_self_hosted_cancelled
}

@StringRes
internal fun SelfHostedBlockReason.messageRes(): Int = when (this) {
    SelfHostedBlockReason.Configuration -> R.string.error_self_hosted_configuration_unavailable
    SelfHostedBlockReason.Credentials -> R.string.error_self_hosted_token
    SelfHostedBlockReason.Tls -> R.string.error_self_hosted_tls
    SelfHostedBlockReason.SpkiPin -> R.string.error_self_hosted_spki_pin
    SelfHostedBlockReason.ServerVersion -> R.string.error_self_hosted_server_version
    SelfHostedBlockReason.ApiVersion -> R.string.error_self_hosted_protocol
    SelfHostedBlockReason.InstanceChanged,
    SelfHostedBlockReason.IdentityHeaders,
    -> R.string.error_self_hosted_identity
}
