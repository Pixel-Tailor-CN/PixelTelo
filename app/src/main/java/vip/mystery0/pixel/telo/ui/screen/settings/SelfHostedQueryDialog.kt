package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.isEditable
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.password
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import java.net.URI
import java.text.DateFormat
import java.util.Date
import vip.mystery0.pixel.telo.BuildConfig
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.query.SelfHostedBlockReason
import vip.mystery0.pixel.telo.data.query.SelfHostedErrorCategory
import vip.mystery0.pixel.telo.data.query.SelfHostedTlsMode
import vip.mystery0.pixel.telo.data.query.VerifiedSelfHostedConfig
import vip.mystery0.pixel.telo.viewmodel.SelfHostedDraftUiState

/**
 * 自建查询服务的配置与已启用服务管理 Dialog。
 *
 * Base URL、Token 与 Pin 草稿只由普通 `remember` 持有，不接入 `rememberSaveable`；所有关闭路径都会先清空
 * Token `TextFieldValue`，再通知 ViewModel 释放草稿引用。
 */
@Composable
fun SelfHostedQueryDialog(
    initialDraft: SelfHostedDraftUiState,
    editing: Boolean,
    currentConfig: VerifiedSelfHostedConfig?,
    blockedReason: SelfHostedBlockReason?,
    validationInProgress: Boolean,
    validationError: SelfHostedErrorCategory?,
    onUpdateDraft: (SelfHostedDraftUiState) -> Unit,
    onValidateAndEnable: () -> Unit,
    onRevalidate: () -> Unit,
    onEdit: () -> Unit,
    onUseOfficial: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initialDraft.copy(token = "")) }
    var tokenValue by remember { mutableStateOf(TextFieldValue("")) }
    val showEditor = editing

    DisposableEffect(Unit) {
        onDispose {
            // 成功验证等父级关闭路径同样主动断开本地明文 Token 与草稿引用。
            tokenValue = TextFieldValue("")
            draft = draft.copy(token = "")
        }
    }

    fun dismissAndClearToken() {
        tokenValue = TextFieldValue("")
        draft = draft.copy(token = "")
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = {
            if (!validationInProgress) dismissAndClearToken()
        },
        title = {
            Text(
                stringResource(
                    if (showEditor) {
                        R.string.title_self_hosted_config
                    } else {
                        R.string.title_self_hosted_management
                    },
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showEditor) {
                    SelfHostedEditor(
                        draft = draft,
                        tokenValue = tokenValue,
                        enabled = !validationInProgress,
                        onDraftChange = { draft = it.copy(token = "") },
                        onTokenChange = { tokenValue = it },
                    )
                } else {
                    if (currentConfig != null) {
                        SelfHostedBackendSummary(currentConfig)
                    } else {
                        Text(stringResource(R.string.setting_query_backend_self_hosted_unavailable_summary))
                    }
                    Text(
                        stringResource(
                            if (blockedReason == null) {
                                R.string.msg_self_hosted_management_hint
                            } else {
                                R.string.msg_self_hosted_blocked_hint
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onRevalidate,
                        enabled = !validationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_revalidate_self_hosted))
                    }
                    OutlinedButton(
                        onClick = onEdit,
                        enabled = !validationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_modify_self_hosted))
                    }
                    TextButton(
                        onClick = onUseOfficial,
                        enabled = !validationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_use_official_backend))
                    }
                }

                val errorRes = validationError?.messageRes()
                    ?: blockedReason?.messageRes()?.takeIf { !showEditor }
                if (errorRes != null) {
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (validationInProgress) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.msg_self_hosted_validating))
                    }
                }
            }
        },
        confirmButton = {
            if (showEditor) {
                val canSubmit = draft.baseUrl.isNotBlank() &&
                    tokenValue.text.isNotBlank() &&
                    (draft.tlsMode != SelfHostedTlsMode.SPKI_PIN || draft.spkiPin.isNotBlank())
                Button(
                    onClick = {
                        onUpdateDraft(draft.copy(token = tokenValue.text))
                        onValidateAndEnable()
                    },
                    enabled = canSubmit && !validationInProgress,
                ) {
                    Text(stringResource(R.string.action_test_and_enable_self_hosted))
                }
            } else {
                Button(
                    onClick = ::dismissAndClearToken,
                    enabled = !validationInProgress,
                ) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
        dismissButton = {
            if (showEditor) {
                OutlinedButton(
                    onClick = ::dismissAndClearToken,
                    enabled = !validationInProgress,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun SelfHostedEditor(
    draft: SelfHostedDraftUiState,
    tokenValue: TextFieldValue,
    enabled: Boolean,
    onDraftChange: (SelfHostedDraftUiState) -> Unit,
    onTokenChange: (TextFieldValue) -> Unit,
) {
    val tokenLabel = stringResource(R.string.label_self_hosted_token)
    val tokenFocusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = draft.baseUrl,
        onValueChange = { onDraftChange(draft.copy(baseUrl = it)) },
        label = { Text(stringResource(R.string.label_self_hosted_base_url)) },
        supportingText = { Text(stringResource(R.string.hint_self_hosted_base_url)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = tokenValue,
        onValueChange = onTokenChange,
        label = { Text(tokenLabel) },
        supportingText = { Text(stringResource(R.string.hint_self_hosted_token)) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(tokenFocusRequester)
            .clearAndSetSemantics {
                // 清除 CoreTextField 自动生成的 Autofill 原文，只重建不含输入内容的安全语义。
                contentDescription = tokenLabel
                password()
                isEditable = enabled
                if (enabled) {
                    onClick {
                        tokenFocusRequester.requestFocus()
                        true
                    }
                } else {
                    disabled()
                }
            },
    )
    Text(
        stringResource(R.string.label_self_hosted_tls_mode),
        style = MaterialTheme.typography.titleSmall,
    )
    TlsModeRow(
        selected = draft.tlsMode == SelfHostedTlsMode.SYSTEM,
        title = stringResource(R.string.tls_mode_system),
        summary = stringResource(R.string.tls_mode_system_summary),
        enabled = enabled,
        onClick = {
            onDraftChange(draft.copy(tlsMode = SelfHostedTlsMode.SYSTEM, spkiPin = ""))
        },
    )
    TlsModeRow(
        selected = draft.tlsMode == SelfHostedTlsMode.SPKI_PIN,
        title = stringResource(R.string.tls_mode_spki_pin),
        summary = stringResource(R.string.tls_mode_spki_pin_summary),
        enabled = enabled,
        onClick = { onDraftChange(draft.copy(tlsMode = SelfHostedTlsMode.SPKI_PIN)) },
    )
    if (draft.tlsMode == SelfHostedTlsMode.SPKI_PIN) {
        OutlinedTextField(
            value = draft.spkiPin,
            onValueChange = { onDraftChange(draft.copy(spkiPin = it)) },
            label = { Text(stringResource(R.string.label_self_hosted_spki_pin)) },
            supportingText = { Text(stringResource(R.string.hint_self_hosted_spki_pin)) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (BuildConfig.DEBUG) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) {
                    onDraftChange(draft.copy(allowPreRelease = !draft.allowPreRelease))
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.setting_allow_prerelease_self_hosted))
                Text(
                    stringResource(R.string.setting_allow_prerelease_self_hosted_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = draft.allowPreRelease,
                onCheckedChange = {
                    onDraftChange(draft.copy(allowPreRelease = it))
                },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun TlsModeRow(
    selected: Boolean,
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 仅展示已验证配置中的脱敏 Host、版本和验证时间。 */
@Composable
fun SelfHostedBackendSummary(config: VerifiedSelfHostedConfig) {
    val host = remember(config.baseUrl) { selfHostedDisplayHost(config.baseUrl) }
        ?: stringResource(R.string.label_self_hosted_host_unavailable)
    val verifiedAt = remember(config.verifiedAtEpochMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(config.verifiedAtEpochMillis))
    }
    Text(
        text = stringResource(
            R.string.setting_query_backend_self_hosted_summary,
            host,
            config.version,
            verifiedAt,
        ),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 解析规范化 URL 时只返回 Host；任何失败都不会回退展示原始 URL。 */
internal fun selfHostedDisplayHost(baseUrl: String): String? = runCatching {
    URI(baseUrl).host?.takeIf { it.isNotBlank() }
}.getOrNull()

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
