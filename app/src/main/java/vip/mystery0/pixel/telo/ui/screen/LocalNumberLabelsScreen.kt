package vip.mystery0.pixel.telo.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.LocalNumberLabel
import vip.mystery0.pixel.telo.ui.components.LocalNumberLabelEditorDialogs
import vip.mystery0.pixel.telo.viewmodel.LocalNumberLabelEditorViewModel
import vip.mystery0.pixel.telo.viewmodel.LocalNumberLabelsViewModel

/**
 * 本地号码标签统一管理页。
 *
 * 只支持搜索、编辑和删除已有标签，不提供任意号码新增。
 */
@Composable
fun LocalNumberLabelsScreen(
    viewModel: LocalNumberLabelsViewModel,
    editorViewModel: LocalNumberLabelEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val entries by viewModel.items.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val editorState by editorViewModel.state.collectAsState()
    val hasSearchQuery = searchQuery.trim().isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::updateQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.hint_search_local_number_labels)) },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                )
            },
        )

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasSearchQuery) {
                        Text(
                            text = stringResource(R.string.msg_no_matching_local_number_labels),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.msg_no_local_number_labels),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.msg_no_local_number_labels_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(
                    items = entries,
                    key = { it.normalizedPhoneNumber },
                ) { entry ->
                    LocalNumberLabelRow(
                        entry = entry,
                        onEdit = {
                            editorViewModel.observe(entry.normalizedPhoneNumber)
                            editorViewModel.openEditor()
                        },
                        onDelete = {
                            editorViewModel.observe(entry.normalizedPhoneNumber)
                            editorViewModel.requestDelete()
                        },
                    )
                }
            }
        }
    }

    LocalNumberLabelEditorDialogs(
        state = editorState,
        onDraftChange = editorViewModel::updateDraft,
        onSave = editorViewModel::save,
        onDismissEditor = editorViewModel::close,
        onConfirmDelete = editorViewModel::confirmDelete,
        onDismissDelete = editorViewModel::close,
    )
}

@Composable
private fun LocalNumberLabelRow(
    entry: LocalNumberLabel,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.normalizedPhoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.cd_edit_local_label),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.cd_delete_local_label),
            )
        }
    }
}
