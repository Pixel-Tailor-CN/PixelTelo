package vip.mystery0.pixel.telo.ui.screen.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
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
import vip.mystery0.pixel.telo.data.query.MAX_PAIRING_JSON_LENGTH
import vip.mystery0.pixel.telo.data.query.SelfHostedPairingError
import vip.mystery0.pixel.telo.data.query.SelfHostedPairingResult
import vip.mystery0.pixel.telo.data.query.SelfHostedTlsMode
import vip.mystery0.pixel.telo.data.query.parseSelfHostedPairingJson
import vip.mystery0.pixel.telo.viewmodel.SelfHostedDraftUiState
import vip.mystery0.pixel.telo.viewmodel.SelfHostedPairingOrigin
import vip.mystery0.pixel.telo.viewmodel.SelfHostedPairingPage
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Token 与原始 JSON 只存在于页面普通状态；ViewModel 只保存非敏感导航和表单草稿。 */
@Composable
fun SelfHostedPairingScreen(
    viewModel: SettingViewModel,
    initialEntry: SelfHostedPairingEntry,
    backRequest: Int,
    onBackToManagement: () -> Unit,
    onTitleChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val initialDraft = remember {
        viewModel.selfHostedDraft ?: SelfHostedDraftUiState()
    }
    val baselineDraft = viewModel.selfHostedPairingInitialDraft ?: initialDraft
    var draft by remember { mutableStateOf(viewModel.selfHostedDraft ?: initialDraft) }
    var step by remember { mutableStateOf(viewModel.selfHostedPairingPage) }
    var source by remember { mutableStateOf(viewModel.selfHostedPairingOrigin) }
    var tokenInput by remember { mutableStateOf(TextFieldValue()) }
    var pasteInput by remember { mutableStateOf(TextFieldValue()) }
    var candidate by remember { mutableStateOf<SelfHostedPairingResult.Success?>(null) }
    var fieldError by remember { mutableStateOf<SelfHostedPairingError?>(null) }
    var inputError by remember { mutableStateOf<Int?>(null) }
    var scanError by remember { mutableStateOf<Int?>(null) }
    var scanSession by remember { mutableStateOf(0) }
    var showDiscard by remember { mutableStateOf(false) }
    var pendingTarget by remember { mutableStateOf<SelfHostedPairingPage?>(null) }
    var pendingManagement by remember { mutableStateOf(false) }
    var handledBackRequest by remember { mutableStateOf(backRequest) }
    val busy = viewModel.selfHostedValidationInProgress
    val latestCandidate by rememberUpdatedState(candidate)
    val retryRequired = viewModel.selfHostedTokenRetryRequired

    fun titleFor(current: SelfHostedPairingPage): Int = when (current) {
        SelfHostedPairingPage.MANUAL -> R.string.title_pairing_manual
        SelfHostedPairingPage.PASTE -> R.string.title_pairing_paste
        SelfHostedPairingPage.SCAN -> R.string.title_pairing_scan
        SelfHostedPairingPage.CONFIRM -> R.string.title_pairing_confirm
    }

    fun isDirty(): Boolean = when (step) {
        SelfHostedPairingPage.MANUAL -> draft != baselineDraft || tokenInput.text.isNotEmpty()
        SelfHostedPairingPage.PASTE -> pasteInput.text.isNotEmpty()
        SelfHostedPairingPage.SCAN,
        SelfHostedPairingPage.CONFIRM -> false
    }

    fun discardPageInputs() {
        candidate?.close()
        candidate = null
        tokenInput = TextFieldValue()
        pasteInput = TextFieldValue()
        fieldError = null
        inputError = null
        scanError = null
        draft = baselineDraft
        viewModel.updateSelfHostedPairingDraft(baselineDraft)
        viewModel.clearSelfHostedTokenRetryRequired()
    }

    fun changePage(target: SelfHostedPairingPage, origin: SelfHostedPairingOrigin) {
        discardPageInputs()
        source = origin
        step = target
        viewModel.setSelfHostedPairingPage(target, origin)
        if (target == SelfHostedPairingPage.SCAN) scanSession++
    }

    fun navigateToPage(target: SelfHostedPairingPage, origin: SelfHostedPairingOrigin) {
        if (busy || target == step) return
        if (isDirty()) {
            pendingTarget = target
            pendingManagement = false
            showDiscard = true
        } else {
            changePage(target, origin)
        }
    }

    fun leavePairing() {
        if (busy) return
        candidate?.close()
        candidate = null
        tokenInput = TextFieldValue()
        pasteInput = TextFieldValue()
        onBackToManagement()
    }

    fun navigateToManagement() {
        if (busy) return
        if (isDirty()) {
            pendingTarget = null
            pendingManagement = true
            showDiscard = true
        } else {
            leavePairing()
        }
    }

    fun returnToSource() {
        val ready = candidate
        when (source) {
            SelfHostedPairingOrigin.MANUAL -> {
                if (ready != null) {
                    draft = SelfHostedDraftUiState(ready.baseUrl, ready.tlsMode, ready.spkiPin)
                    tokenInput = TextFieldValue(ready.token.concatToString())
                    viewModel.updateSelfHostedPairingDraft(draft)
                }
                ready?.close()
                candidate = null
                fieldError = null
                viewModel.clearSelfHostedTokenRetryRequired()
                step = SelfHostedPairingPage.MANUAL
                viewModel.setSelfHostedPairingPage(step)
            }
            SelfHostedPairingOrigin.PASTE -> {
                tokenInput = TextFieldValue()
                if (ready != null) {
                    val token = ready.token.concatToString()
                    pasteInput = TextFieldValue(
                        buildJsonObject {
                            put("url", ready.baseUrl)
                            put("token", token)
                            if (ready.tlsMode == SelfHostedTlsMode.SPKI_PIN) {
                                put("spki_pin", ready.spkiPin)
                            }
                        }.toString(),
                    )
                    ready.close()
                    candidate = null
                }
                inputError = null
                viewModel.clearSelfHostedTokenRetryRequired()
                step = SelfHostedPairingPage.PASTE
                viewModel.setSelfHostedPairingPage(step)
            }
            SelfHostedPairingOrigin.SCAN -> {
                tokenInput = TextFieldValue()
                pasteInput = TextFieldValue()
                ready?.close()
                candidate = null
                scanError = null
                scanSession++
                viewModel.clearSelfHostedTokenRetryRequired()
                step = SelfHostedPairingPage.SCAN
                viewModel.setSelfHostedPairingPage(step)
            }
        }
    }

    fun requestBack() {
        if (busy) return
        when (step) {
            SelfHostedPairingPage.CONFIRM -> returnToSource()
            SelfHostedPairingPage.MANUAL,
            SelfHostedPairingPage.PASTE,
            SelfHostedPairingPage.SCAN -> navigateToManagement()
        }
    }

    fun acceptCandidate(value: SelfHostedPairingResult.Success, from: SelfHostedPairingOrigin) {
        candidate?.close()
        candidate = value
        source = from
        draft = SelfHostedDraftUiState(value.baseUrl, value.tlsMode, value.spkiPin)
        tokenInput = TextFieldValue()
        pasteInput = TextFieldValue()
        fieldError = null
        inputError = null
        scanError = null
        viewModel.updateSelfHostedPairingDraft(draft)
        viewModel.clearSelfHostedValidationError()
        viewModel.clearSelfHostedTokenRetryRequired()
        step = SelfHostedPairingPage.CONFIRM
        viewModel.setSelfHostedPairingPage(step, from)
    }

    fun parseInput(raw: String, from: SelfHostedPairingOrigin) {
        when (val result = parseSelfHostedPairingJson(raw)) {
            is SelfHostedPairingResult.Failure -> {
                val message = result.error.messageRes()
                if (from == SelfHostedPairingOrigin.SCAN) scanError = message else inputError = message
            }
            is SelfHostedPairingResult.Success -> {
                if (from == SelfHostedPairingOrigin.PASTE) pasteInput = TextFieldValue()
                acceptCandidate(result, from)
            }
        }
    }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        val item = if (clip?.description?.hasMimeType("text/plain") == true) {
            clip.getItemAt(0)
        } else {
            null
        }
        val pasted = item?.text?.toString()
        when {
            pasted == null -> inputError = R.string.error_pairing_clipboard
            pasted.length > MAX_PAIRING_JSON_LENGTH -> inputError = R.string.error_pairing_too_large
            else -> {
                pasteInput = TextFieldValue(pasted)
                inputError = null
            }
        }
    }

    LaunchedEffect(initialEntry) {
        if (viewModel.selfHostedPairingPage != step) {
            val page = when (initialEntry) {
                SelfHostedPairingEntry.SCAN -> SelfHostedPairingPage.SCAN
                SelfHostedPairingEntry.PASTE -> SelfHostedPairingPage.PASTE
                SelfHostedPairingEntry.MANUAL -> SelfHostedPairingPage.MANUAL
            }
            step = page
            source = viewModel.selfHostedPairingOrigin
        }
    }
    LaunchedEffect(step) { onTitleChange(titleFor(step)) }
    LaunchedEffect(backRequest) {
        if (backRequest != handledBackRequest) {
            handledBackRequest = backRequest
            requestBack()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            latestCandidate?.close()
            viewModel.requireSelfHostedTokenRetry()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (step == SelfHostedPairingPage.SCAN) {
            val session = scanSession
            androidx.compose.runtime.key(session) {
                SelfHostedQrScanner(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onDecoded = { value ->
                        if (step == SelfHostedPairingPage.SCAN && scanSession == session) {
                            parseInput(value, SelfHostedPairingOrigin.SCAN)
                        }
                    },
                    onFailure = { error ->
                        if (step == SelfHostedPairingPage.SCAN && scanSession == session) {
                            scanError = error
                        }
                    },
                    onRetryCamera = {
                        if (step == SelfHostedPairingPage.SCAN && scanSession == session) {
                            scanError = null
                            scanSession++
                        }
                    },
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                when (step) {
                    SelfHostedPairingPage.MANUAL -> {
                        Text(
                            text = stringResource(R.string.msg_pairing_manual_hint),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        SelfHostedEditor(
                            draft = draft,
                            tokenValue = tokenInput,
                            enabled = !busy,
                            fieldError = fieldError,
                            onDraftChange = {
                                draft = it
                                fieldError = null
                                inputError = null
                                viewModel.updateSelfHostedPairingDraft(it)
                                viewModel.clearSelfHostedValidationError()
                            },
                            onTokenChange = {
                                tokenInput = it
                                fieldError = null
                                inputError = null
                                viewModel.clearSelfHostedValidationError()
                            },
                        )
                        inputError?.let {
                            Text(
                                stringResource(it),
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            TextButton(
                                onClick = {
                                    navigateToPage(SelfHostedPairingPage.SCAN, SelfHostedPairingOrigin.SCAN)
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.action_pairing_scan)) }
                            TextButton(
                                onClick = {
                                    navigateToPage(SelfHostedPairingPage.PASTE, SelfHostedPairingOrigin.PASTE)
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.action_pairing_paste)) }
                        }
                    }
                    SelfHostedPairingPage.PASTE -> {
                        val pasteLabel = stringResource(R.string.action_pairing_paste)
                        val pasteFocusRequester = remember { FocusRequester() }
                        val pasteError = inputError?.let { stringResource(it) }
                        SettingsSection(title = stringResource(R.string.section_pairing_connection)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.msg_pairing_paste_hint))
                                OutlinedTextField(
                                    value = pasteInput,
                                    onValueChange = {
                                        if (it.text.length <= MAX_PAIRING_JSON_LENGTH) {
                                            pasteInput = it
                                            inputError = null
                                        } else {
                                            inputError = R.string.error_pairing_too_large
                                        }
                                    },
                                    label = { Text(pasteLabel) },
                                    supportingText = pasteError?.let { message ->
                                        { Text(message, color = MaterialTheme.colorScheme.error) }
                                    },
                                    isError = inputError != null,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        autoCorrectEnabled = false,
                                    ),
                                    minLines = 4,
                                    maxLines = 8,
                                    enabled = !busy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(pasteFocusRequester)
                                        .clearAndSetSemantics {
                                            contentDescription = pasteLabel
                                            password()
                                            isEditable = !busy
                                            if (pasteError != null) error(pasteError)
                                            if (!busy) {
                                                onClick {
                                                    pasteFocusRequester.requestFocus()
                                                    true
                                                }
                                            } else {
                                                disabled()
                                            }
                                        },
                                )
                                OutlinedButton(
                                    onClick = ::pasteFromClipboard,
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.action_pairing_clipboard)) }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            TextButton(
                                onClick = {
                                    navigateToPage(SelfHostedPairingPage.SCAN, SelfHostedPairingOrigin.SCAN)
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.action_pairing_scan)) }
                            TextButton(
                                onClick = {
                                    navigateToPage(SelfHostedPairingPage.MANUAL, SelfHostedPairingOrigin.MANUAL)
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.action_pairing_manual)) }
                        }
                    }
                    SelfHostedPairingPage.CONFIRM -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(stringResource(R.string.msg_pairing_confirm))
                            val preview = candidate
                            val tlsMode = preview?.tlsMode ?: draft.tlsMode
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.label_pairing_address,
                                            preview?.baseUrl ?: draft.baseUrl,
                                        ),
                                    )
                                    Text(
                                        stringResource(
                                            R.string.label_pairing_tls,
                                            stringResource(
                                                if (tlsMode == SelfHostedTlsMode.SYSTEM) {
                                                    R.string.tls_mode_system
                                                } else {
                                                    R.string.tls_mode_spki_pin
                                                },
                                            ),
                                        ),
                                    )
                                    if (tlsMode == SelfHostedTlsMode.SPKI_PIN) {
                                        Text(
                                            stringResource(
                                                R.string.label_pairing_pin,
                                                preview?.spkiPin ?: draft.spkiPin,
                                            ),
                                        )
                                    }
                                    if (preview != null) {
                                        Text(stringResource(R.string.label_pairing_token_hidden))
                                    }
                                }
                            }
                            viewModel.selfHostedValidationError?.let {
                                Text(
                                    stringResource(it.messageRes()),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (retryRequired) {
                                Text(stringResource(R.string.msg_pairing_retry_token))
                                RetryTokenField(
                                    value = tokenInput,
                                    enabled = !busy,
                                    errorMessage = viewModel.selfHostedValidationError
                                        ?.messageRes()?.let { stringResource(it) },
                                    onValueChange = {
                                        tokenInput = it
                                        viewModel.clearSelfHostedValidationError()
                                    },
                                )
                            }
                            if (busy) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.msg_self_hosted_validating))
                            }
                        }
                    }
                    SelfHostedPairingPage.SCAN -> Unit
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (step) {
                SelfHostedPairingPage.MANUAL -> {
                    Button(
                        onClick = {
                            when (val result = viewModel.prepareManualSelfHostedPairing(draft, tokenInput.text)) {
                                is SelfHostedPairingResult.Failure -> {
                                    if (result.error == SelfHostedPairingError.TOO_LARGE) {
                                        fieldError = null
                                        inputError = R.string.error_pairing_too_large
                                    } else {
                                        inputError = null
                                        fieldError = result.error
                                    }
                                }
                                is SelfHostedPairingResult.Success -> {
                                    acceptCandidate(result, SelfHostedPairingOrigin.MANUAL)
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_pairing_next)) }
                }
                SelfHostedPairingPage.PASTE -> {
                    Button(
                        onClick = { parseInput(pasteInput.text, SelfHostedPairingOrigin.PASTE) },
                        enabled = !busy && pasteInput.text.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_pairing_next)) }
                }
                SelfHostedPairingPage.SCAN -> {
                    scanError?.let {
                        Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(
                            onClick = { scanError = null; scanSession++ },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.action_pairing_retry_scan)) }
                    }
                    OutlinedButton(
                        onClick = {
                            navigateToPage(SelfHostedPairingPage.PASTE, SelfHostedPairingOrigin.PASTE)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_pairing_paste)) }
                    TextButton(
                        onClick = {
                            navigateToPage(
                                SelfHostedPairingPage.MANUAL,
                                SelfHostedPairingOrigin.MANUAL,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_pairing_manual)) }
                }
                SelfHostedPairingPage.CONFIRM -> {
                    Button(
                        onClick = {
                            val ready = candidate
                            val submitted = ready?.token?.copyOf() ?: tokenInput.text.toCharArray()
                            if (submitted.isNotEmpty()) {
                                ready?.close()
                                candidate = null
                                tokenInput = TextFieldValue()
                                viewModel.updateSelfHostedPairingDraft(draft)
                                viewModel.validateAndEnableSelfHosted(draft, submitted)
                            }
                        },
                        enabled = !busy && (candidate != null || tokenInput.text.isNotEmpty()),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (retryRequired) R.string.action_revalidate_and_enable_self_hosted
                                else R.string.action_test_and_enable_self_hosted,
                            ),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            candidate?.let { ready ->
                                tokenInput = TextFieldValue(ready.token.concatToString())
                                ready.close()
                            }
                            candidate = null
                            source = SelfHostedPairingOrigin.MANUAL
                            fieldError = null
                            viewModel.updateSelfHostedPairingDraft(draft)
                            viewModel.clearSelfHostedTokenRetryRequired()
                            viewModel.clearSelfHostedValidationError()
                            step = SelfHostedPairingPage.MANUAL
                            viewModel.setSelfHostedPairingPage(step)
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_modify_self_hosted)) }
                }
            }
        }
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = {
                showDiscard = false
                pendingTarget = null
                pendingManagement = false
            },
            title = { Text(stringResource(R.string.title_pairing_discard)) },
            text = { Text(stringResource(R.string.msg_pairing_discard)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscard = false
                        discardPageInputs()
                        if (pendingManagement) {
                            pendingManagement = false
                            pendingTarget = null
                            leavePairing()
                        } else {
                            val target = pendingTarget ?: SelfHostedPairingPage.MANUAL
                            pendingTarget = null
                            changePage(target, when (target) {
                                SelfHostedPairingPage.MANUAL -> SelfHostedPairingOrigin.MANUAL
                                SelfHostedPairingPage.PASTE -> SelfHostedPairingOrigin.PASTE
                                SelfHostedPairingPage.SCAN -> SelfHostedPairingOrigin.SCAN
                                SelfHostedPairingPage.CONFIRM -> source
                            })
                        }
                    },
                ) { Text(stringResource(R.string.action_pairing_discard)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDiscard = false
                    pendingTarget = null
                    pendingManagement = false
                }) {
                    Text(stringResource(R.string.action_pairing_continue_editing))
                }
            },
        )
    }
}

@Composable
private fun RetryTokenField(
    value: TextFieldValue,
    enabled: Boolean,
    errorMessage: String?,
    onValueChange: (TextFieldValue) -> Unit,
) {
    val label = stringResource(R.string.label_self_hosted_token)
    val focusRequester = remember { FocusRequester() }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        enabled = enabled,
        singleLine = true,
        supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        isError = errorMessage != null,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .clearAndSetSemantics {
                contentDescription = label
                password()
                isEditable = enabled
                if (errorMessage != null) error(errorMessage)
                if (enabled) {
                    onClick {
                        focusRequester.requestFocus()
                        true
                    }
                } else {
                    disabled()
                }
            },
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
