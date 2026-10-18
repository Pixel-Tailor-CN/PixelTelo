package vip.mystery0.pixel.telo.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.LocalNumberLabel
import vip.mystery0.pixel.telo.ui.components.EmptyState
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
    onNavigateToCallHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val editorState by editorViewModel.state.collectAsState()
    val hasSearchQuery = uiState.searchQuery.trim().isNotEmpty()
    val showSearchField = !uiState.hasLoadedLabels || uiState.hasAnyLabels ||
        uiState.searchQuery.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        if (showSearchField) {
            LocalNumberLabelSearchField(
                query = uiState.searchQuery,
                onQueryChange = viewModel::updateQuery,
                onClear = { viewModel.updateQuery("") },
            )
        }

        when {
            !uiState.hasLoadedLabels -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = if (hasSearchQuery) Icons.Default.Search else
                            Icons.AutoMirrored.Filled.Label,
                        title = stringResource(
                            if (hasSearchQuery) {
                                R.string.msg_no_matching_local_number_labels
                            } else {
                                R.string.msg_no_local_number_labels
                            },
                        ),
                        description = stringResource(
                            if (hasSearchQuery) {
                                R.string.msg_no_matching_local_number_labels_hint
                            } else {
                                R.string.msg_no_local_number_labels_hint
                            },
                        ),
                        modifier = Modifier.align(Alignment.Center),
                        action = {
                            TextButton(
                                onClick = if (hasSearchQuery) {
                                    { viewModel.updateQuery("") }
                                } else {
                                    onNavigateToCallHistory
                                },
                            ) {
                                Text(
                                    stringResource(
                                        if (hasSearchQuery) {
                                            R.string.action_clear_search
                                        } else {
                                            R.string.action_view_call_history
                                        },
                                    ),
                                )
                            }
                        },
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = uiState.items,
                        key = { it.normalizedPhoneNumber },
                    ) { entry ->
                        LocalNumberLabelCard(
                            entry = entry,
                            onClick = {
                                editorViewModel.observe(entry.normalizedPhoneNumber)
                                editorViewModel.openEditor()
                            },
                        )
                    }
                }
            }
        }
    }

    LocalNumberLabelEditorDialogs(
        state = editorState,
        onDraftChange = editorViewModel::updateDraft,
        onSave = editorViewModel::save,
        onDismissEditor = editorViewModel::close,
        onRequestDelete = editorViewModel::requestDelete,
        onConfirmDelete = editorViewModel::confirmDelete,
        onDismissDelete = editorViewModel::close,
    )
}

@Composable
private fun LocalNumberLabelSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        placeholder = { Text(stringResource(R.string.hint_search_local_number_labels)) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_clear_search),
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun LocalNumberLabelCard(
    entry: LocalNumberLabel,
    onClick: () -> Unit,
) {
    val editActionLabel = stringResource(R.string.cd_edit_local_label)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { onClick(label = editActionLabel, action = null) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = entry.normalizedPhoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
