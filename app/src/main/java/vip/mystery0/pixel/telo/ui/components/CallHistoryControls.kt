package vip.mystery0.pixel.telo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.model.CallHistoryFilter

@Composable
fun CallHistoryControls(query: String, filter: CallHistoryFilter,
    onQuery: (String) -> Unit, onFilter: (CallHistoryFilter) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.history_search_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {{
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Default.Close, stringResource(R.string.action_clear_search))
                }
            }} else null,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CallHistoryFilter.entries.forEach { item ->
                FilterChip(selected = filter == item, onClick = { onFilter(item) },
                    label = { Text(stringResource(when (item) {
                        CallHistoryFilter.ALL -> R.string.history_filter_all
                        CallHistoryFilter.BLOCKED -> R.string.history_filter_blocked
                        CallHistoryFilter.ALLOWED -> R.string.history_filter_allowed
                        CallHistoryFilter.FAILED -> R.string.history_filter_failed
                    })) })
            }
        }
    }
}

/** 未完成初始化时只给出下一步，所有系统操作仍由用户点击触发。 */
@Composable
fun SetupStepsCard(defaultApp: Boolean, permissionsGranted: Boolean, databaseReady: Boolean,
    onDefaultApp: () -> Unit, onPermissions: () -> Unit, onDatabase: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
            val completed = listOf(defaultApp, permissionsGranted, databaseReady)
            stringResource(R.string.setup_steps).lines().forEachIndexed { index, line ->
                Text((if (completed.getOrElse(index) { false }) "✓ " else "") + line)
            }
            Button(onClick = when {
                !defaultApp -> onDefaultApp
                !permissionsGranted -> onPermissions
                else -> onDatabase
            }) {
                Text(stringResource(when {
                    !defaultApp -> R.string.setup_next_role
                    !permissionsGranted -> R.string.setup_next_permissions
                    else -> R.string.setup_next_database
                }))
            }
        }
    }
}
