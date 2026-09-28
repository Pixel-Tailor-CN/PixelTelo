package vip.mystery0.pixel.telo.ui.screen.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isEditable
import androidx.compose.ui.semantics.password
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.query.MAX_PAIRING_JSON_LENGTH
import vip.mystery0.pixel.telo.data.query.SelfHostedPairingError
import vip.mystery0.pixel.telo.data.query.SelfHostedPairingResult
import vip.mystery0.pixel.telo.data.query.SelfHostedTlsMode
import vip.mystery0.pixel.telo.viewmodel.SelfHostedDraftUiState
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

private enum class PairingStep { START, MANUAL, CONFIRM, SCAN }

/** 页面级普通 remember 不参与 SavedState；离开或重建时释放尚未提交的凭据。 */
@Composable
fun SelfHostedPairingScreen(viewModel: SettingViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(PairingStep.START) }
    var draft by remember { mutableStateOf(viewModel.selfHostedDraft ?: SelfHostedDraftUiState()) }
    var tokenInput by remember { mutableStateOf(TextFieldValue()) }
    var candidate by remember { mutableStateOf<SelfHostedPairingResult.Success?>(null) }
    var importOpen by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Int?>(null) }
    val busy = viewModel.selfHostedValidationInProgress

    fun replaceCandidate(value: SelfHostedPairingResult.Success) {
        candidate?.close()
        candidate = value
        draft = SelfHostedDraftUiState(value.baseUrl, value.tlsMode, value.spkiPin)
        tokenInput = TextFieldValue()
        feedback = null
        viewModel.clearSelfHostedValidationError()
        step = PairingStep.CONFIRM
    }
    fun import(raw: String): Boolean {
        when (val result = viewModel.parseSelfHostedPairing(raw)) {
            is SelfHostedPairingResult.Failure -> feedback = result.error.messageRes()
            is SelfHostedPairingResult.Success -> {
                replaceCandidate(result)
                return true
            }
        }
        return false
    }
    DisposableEffect(Unit) {
        onDispose {
            candidate?.close()
            tokenInput = TextFieldValue()
        }
    }

    Column(
        modifier = modifier.imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (step) {
            PairingStep.START -> {
                Text(stringResource(R.string.msg_pairing_intro))
                Button(onClick = { feedback = null; step = PairingStep.SCAN }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_pairing_scan)) }
                OutlinedButton(onClick = { feedback = null; importOpen = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_pairing_paste)) }
                TextButton(onClick = { feedback = null; step = PairingStep.MANUAL }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_pairing_manual)) }
            }
            PairingStep.MANUAL -> {
                SelfHostedEditor(draft, tokenInput, !busy,
                    onDraftChange = { draft = it; feedback = null },
                    onTokenChange = { tokenInput = it; feedback = null })
                Button(onClick = {
                    when (val result = viewModel.prepareManualSelfHostedPairing(draft, tokenInput.text)) {
                        is SelfHostedPairingResult.Failure -> feedback = result.error.messageRes()
                        is SelfHostedPairingResult.Success -> replaceCandidate(result)
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_pairing_confirm))
                }
                TextButton(onClick = { tokenInput = TextFieldValue(); step = PairingStep.START },
                    enabled = !busy) { Text(stringResource(R.string.action_pairing_reimport)) }
            }
            PairingStep.CONFIRM -> {
                val ready = candidate
                if (ready != null) {
                    Text(stringResource(R.string.title_pairing_confirm), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.label_pairing_address, ready.baseUrl))
                    Text(stringResource(R.string.label_pairing_tls,
                        stringResource(if (ready.tlsMode == SelfHostedTlsMode.SYSTEM)
                            R.string.tls_mode_system else R.string.tls_mode_spki_pin)))
                    Text(stringResource(R.string.label_pairing_token_hidden))
                    Text(stringResource(R.string.msg_pairing_confirm))
                    Button(onClick = {
                        // 验证入口接管一次性数组；确认页立即失去明文及可提交状态。
                        val submitted = ready.token.copyOf()
                        ready.close()
                        candidate = null
                        tokenInput = TextFieldValue()
                        step = PairingStep.START
                        viewModel.validateAndEnableSelfHosted(draft, submitted)
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_pairing_validate))
                    }
                    OutlinedButton(onClick = {
                        tokenInput = TextFieldValue(ready.token.concatToString())
                        ready.close()
                        candidate = null
                        step = PairingStep.MANUAL
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_modify_self_hosted))
                    }
                    TextButton(onClick = { ready.close(); candidate = null; step = PairingStep.START },
                        enabled = !busy) { Text(stringResource(R.string.action_pairing_reimport)) }
                }
            }
            PairingStep.SCAN -> SelfHostedQrScanner(
                onDecoded = { step = PairingStep.START; import(it) },
                onFailure = { feedback = it; step = PairingStep.START },
                onCancel = { step = PairingStep.START },
            )
        }
        if (busy) {
            CircularProgressIndicator()
            Text(stringResource(R.string.msg_self_hosted_validating))
        }
        val error = viewModel.selfHostedValidationError
        if (error != null) {
            Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.msg_pairing_retry_token))
        }
        feedback?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    }

    if (importOpen && !busy) {
        SelfHostedPairingImport(
            onDismiss = { importOpen = false },
            onImport = { replaceCandidate(it); importOpen = false },
        )
    }
}

@Composable
private fun SelfHostedPairingImport(
    onDismiss: () -> Unit,
    onImport: (SelfHostedPairingResult.Success) -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(TextFieldValue()) }
    var error by remember { mutableStateOf<Int?>(null) }
    val label = stringResource(R.string.action_pairing_paste)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.msg_pairing_paste_hint))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.text.length <= MAX_PAIRING_JSON_LENGTH) {
                        text = it; error = null
                    } else error = R.string.error_pairing_too_large },
                    label = { Text(label) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                    ),
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                        contentDescription = label
                        password()
                        isEditable = true
                    },
                )
                TextButton(onClick = {
                    // 剪贴板只在明确点击时读取纯文本，不解引用 URI 或 Intent。
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    val item = if (clip?.description?.hasMimeType("text/plain") == true) clip.getItemAt(0) else null
                    val pasted = item?.text?.toString()
                    when {
                        pasted == null -> error = R.string.error_pairing_clipboard
                        pasted.length > MAX_PAIRING_JSON_LENGTH -> error = R.string.error_pairing_too_large
                        else -> { text = TextFieldValue(pasted); error = null }
                    }
                }) { Text(stringResource(R.string.action_pairing_clipboard)) }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                // 失败保留遮蔽输入，允许再次粘贴；不把异常或输入内容显示给用户。
                val result = vip.mystery0.pixel.telo.data.query.parseSelfHostedPairingJson(text.text)
                when (result) {
                    is SelfHostedPairingResult.Failure -> error = result.error.messageRes()
                    is SelfHostedPairingResult.Success -> {
                        onImport(result)
                        text = TextFieldValue()
                    }
                }
            }) { Text(stringResource(R.string.action_pairing_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun SelfHostedPairingError.messageRes(): Int = when (this) {
    SelfHostedPairingError.TOO_LARGE -> R.string.error_pairing_too_large
    SelfHostedPairingError.INVALID_JSON -> R.string.error_pairing_json
    SelfHostedPairingError.MISSING_FIELDS -> R.string.error_pairing_fields
    SelfHostedPairingError.INVALID_URL -> R.string.error_self_hosted_url
    SelfHostedPairingError.INVALID_TOKEN -> R.string.error_self_hosted_token
    SelfHostedPairingError.INVALID_PIN -> R.string.error_self_hosted_spki_pin
}
