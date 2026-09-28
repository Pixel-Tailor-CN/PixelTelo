package vip.mystery0.pixel.telo.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.query.SelfHostedTlsMode
import vip.mystery0.pixel.telo.viewmodel.SelfHostedDraftUiState

/** 配对页的手动配置字段；凭据仅由调用方的临时输入控件持有。 */
@Composable
internal fun SelfHostedEditor(
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
