package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.isEditable
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.password
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.query.SelfHostedPairingError
import vip.mystery0.pixel.telo.data.query.SelfHostedTlsMode
import vip.mystery0.pixel.telo.viewmodel.SelfHostedDraftUiState

/** 配对页的手动配置字段；凭据仅由调用方的临时输入控件持有。 */
@Composable
internal fun SelfHostedEditor(
    draft: SelfHostedDraftUiState,
    tokenValue: TextFieldValue,
    enabled: Boolean,
    fieldError: SelfHostedPairingError?,
    onDraftChange: (SelfHostedDraftUiState) -> Unit,
    onTokenChange: (TextFieldValue) -> Unit,
) {
    val tokenLabel = stringResource(R.string.label_self_hosted_token)
    val tokenError = if (fieldError == SelfHostedPairingError.INVALID_TOKEN) {
        stringResource(R.string.error_self_hosted_token)
    } else {
        null
    }
    val urlFocusRequester = remember { FocusRequester() }
    val tokenFocusRequester = remember { FocusRequester() }
    val pinFocusRequester = remember { FocusRequester() }
    var showTlsHelp by remember { mutableStateOf(false) }

    LaunchedEffect(fieldError) {
        when (fieldError) {
            SelfHostedPairingError.INVALID_URL -> urlFocusRequester.requestFocus()
            SelfHostedPairingError.INVALID_TOKEN -> tokenFocusRequester.requestFocus()
            SelfHostedPairingError.INVALID_PIN -> pinFocusRequester.requestFocus()
            else -> Unit
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsSection(title = stringResource(R.string.section_pairing_connection)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.baseUrl,
                    onValueChange = { onDraftChange(draft.copy(baseUrl = it)) },
                    label = { Text(stringResource(R.string.label_self_hosted_base_url)) },
                    supportingText = {
                        Text(
                            stringResource(
                                if (fieldError == SelfHostedPairingError.INVALID_URL) {
                                    R.string.error_pairing_manual_url
                                } else {
                                    R.string.hint_self_hosted_base_url
                                },
                            ),
                        )
                    },
                    isError = fieldError == SelfHostedPairingError.INVALID_URL,
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().focusRequester(urlFocusRequester),
                )
                when {
                    draft.baseUrl.trim().startsWith("http://", ignoreCase = true) -> Text(
                        stringResource(R.string.self_hosted_http_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    draft.baseUrl.trim().startsWith("https://", ignoreCase = true) -> Text(
                        stringResource(R.string.self_hosted_https_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedTextField(
                    value = tokenValue,
                    onValueChange = onTokenChange,
                    label = { Text(tokenLabel) },
                    supportingText = {
                        Text(tokenError ?: stringResource(R.string.hint_self_hosted_token))
                    },
                    isError = fieldError == SelfHostedPairingError.INVALID_TOKEN,
                    singleLine = true,
                    enabled = enabled,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(tokenFocusRequester)
                        .clearAndSetSemantics {
                            // 清除 CoreTextField 自动生成的 Autofill 原文，只公开不含输入值的安全语义。
                            contentDescription = tokenLabel
                            password()
                            isEditable = enabled
                            tokenError?.let { error(it) }
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
            }
        }

        SettingsSection(title = stringResource(R.string.section_pairing_certificate)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.msg_self_hosted_tls_short),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showTlsHelp = true }, enabled = enabled) {
                    Text(stringResource(R.string.action_self_hosted_tls_help))
                }
                TlsModeRow(
                    selected = draft.tlsMode == SelfHostedTlsMode.SYSTEM,
                    title = stringResource(R.string.tls_mode_system),
                    summary = stringResource(R.string.tls_mode_system_summary_short),
                    enabled = enabled,
                    onClick = {
                        onDraftChange(draft.copy(tlsMode = SelfHostedTlsMode.SYSTEM, spkiPin = ""))
                    },
                )
                TlsModeRow(
                    selected = draft.tlsMode == SelfHostedTlsMode.SPKI_PIN,
                    title = stringResource(R.string.tls_mode_spki_pin),
                    summary = stringResource(R.string.tls_mode_spki_pin_summary_short),
                    enabled = enabled,
                    onClick = { onDraftChange(draft.copy(tlsMode = SelfHostedTlsMode.SPKI_PIN)) },
                )
                if (draft.tlsMode == SelfHostedTlsMode.SPKI_PIN) {
                    OutlinedTextField(
                        value = draft.spkiPin,
                        onValueChange = { onDraftChange(draft.copy(spkiPin = it)) },
                        label = { Text(stringResource(R.string.label_self_hosted_spki_pin)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    if (fieldError == SelfHostedPairingError.INVALID_PIN) {
                                        R.string.error_self_hosted_spki_pin
                                    } else {
                                        R.string.hint_self_hosted_spki_pin
                                    },
                                ),
                            )
                        },
                        isError = fieldError == SelfHostedPairingError.INVALID_PIN,
                        singleLine = true,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().focusRequester(pinFocusRequester),
                    )
                }
            }
        }
    }

    if (showTlsHelp) {
        AlertDialog(
            onDismissRequest = { showTlsHelp = false },
            title = { Text(stringResource(R.string.title_self_hosted_tls_help)) },
            text = { Text(stringResource(R.string.msg_self_hosted_tls_help)) },
            confirmButton = {
                TextButton(onClick = { showTlsHelp = false }) {
                    Text(stringResource(R.string.action_close_tls_help))
                }
            },
        )
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
