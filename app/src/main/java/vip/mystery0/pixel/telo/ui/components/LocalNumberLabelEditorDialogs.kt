package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.viewmodel.LOCAL_LABEL_MAX_LENGTH
import vip.mystery0.pixel.telo.viewmodel.LocalLabelEditorError
import vip.mystery0.pixel.telo.viewmodel.LocalLabelEditorState

/**
 * 共用的本地标签编辑与删除确认 Dialog。
 *
 * 只消费稳定 UI 状态和事件，不直接访问 Repository。
 */
@Composable
fun LocalNumberLabelEditorDialogs(
    state: LocalLabelEditorState,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    if (state.editorVisible) {
        LocalNumberLabelEditDialog(
            state = state,
            onDraftChange = onDraftChange,
            onSave = onSave,
            onDismiss = onDismissEditor,
        )
    }
    if (state.deleteConfirmationVisible) {
        LocalNumberLabelDeleteDialog(
            state = state,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
        )
    }
}

/**
 * 详情中始终可用的本地标签管理区域。
 *
 * 不读取显示开关：无标签时提供设置，有标签时提供修改和删除。
 */
@Composable
fun LocalNumberLabelManagementSection(
    state: LocalLabelEditorState,
    onSet: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLabel = state.currentLabel?.takeIf { it.isNotBlank() }
    val enabled = !state.saving && !state.observing && !state.phoneNumber.isNullOrBlank()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(enabled = enabled, role = Role.Button) { if (currentLabel == null) onSet() else onEdit() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(currentLabel ?: stringResource(R.string.action_set_local_label), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (currentLabel == null) stringResource(R.string.msg_local_label_not_set)
                    else stringResource(R.string.action_edit_local_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (currentLabel != null) {
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onDelete)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LocalNumberLabelEditDialog(
    state: LocalLabelEditorState,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tooLong = state.draft.length > LOCAL_LABEL_MAX_LENGTH
    val errorText = when {
        tooLong || state.error == LocalLabelEditorError.LABEL_TOO_LONG ->
            stringResource(R.string.error_local_label_too_long)

        state.error == LocalLabelEditorError.INVALID_NUMBER ->
            stringResource(R.string.error_local_label_invalid_number)

        state.error == LocalLabelEditorError.SAVE_FAILED ->
            stringResource(R.string.error_local_label_save_failed)

        else -> null
    }
    val canSave = !state.saving &&
        !tooLong &&
        !state.phoneNumber.isNullOrBlank()
    val charCountDescription = stringResource(
        R.string.cd_local_label_char_count,
        state.draft.length,
    )

    AlertDialog(
        onDismissRequest = {
            if (!state.saving) onDismiss()
        },
        title = {
            Text(
                stringResource(
                    if (state.currentLabel.isNullOrBlank()) {
                        R.string.title_set_local_label
                    } else {
                        R.string.title_edit_local_label
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.phoneNumber.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving,
                    singleLine = true,
                    isError = errorText != null,
                    label = { Text(stringResource(R.string.title_local_number_label)) },
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(errorText.orEmpty())
                            Text(
                                text = stringResource(
                                    R.string.label_local_label_char_count,
                                    state.draft.length,
                                ),
                                modifier = Modifier.semantics {
                                    contentDescription = charCountDescription
                                },
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canSave) onSave()
                        },
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = canSave,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.saving,
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun LocalNumberLabelDeleteDialog(
    state: LocalLabelEditorState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val errorText = when (state.error) {
        LocalLabelEditorError.INVALID_NUMBER ->
            stringResource(R.string.error_local_label_invalid_number)

        LocalLabelEditorError.SAVE_FAILED ->
            stringResource(R.string.error_local_label_save_failed)

        else -> null
    }
    AlertDialog(
        onDismissRequest = {
            if (!state.saving) onDismiss()
        },
        title = { Text(stringResource(R.string.title_delete_local_label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.msg_delete_local_label_confirm))
                if (errorText != null) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.saving && !state.phoneNumber.isNullOrBlank(),
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.saving,
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
