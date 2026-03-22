package vip.mystery0.pixel.telo.ui.screen

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsPhone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.ui.components.SwipeToDeleteContainer
import vip.mystery0.pixel.telo.ui.components.WarningCard
import vip.mystery0.pixel.telo.ui.util.PermissionUtils
import vip.mystery0.pixel.telo.ui.util.formatMills
import vip.mystery0.pixel.telo.viewmodel.HomeViewModel
import vip.mystery0.pixel.telo.viewmodel.RetryQueryState

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val blockedCalls by viewModel.blockedCalls.collectAsState()
    val isDatabaseReady by viewModel.isDatabaseReady.collectAsState()
    val missingPermissions by viewModel.missingPermissions.collectAsState()
    val isDefaultApp by viewModel.isDefaultApp.collectAsState()
    val retryQueryState by viewModel.retryQueryState.collectAsState()

    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check after returning from role request
        viewModel.updateDefaultAppState(roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<BlockedCall?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Check Permissions
        val missing = PermissionUtils.allPermissions
            .filter { it.isCritical }
            .filter {
                ContextCompat.checkSelfPermission(
                    context,
                    it.permission
                ) != PackageManager.PERMISSION_GRANTED
            }
            .map { it.permission }
        viewModel.updateMissingPermissions(missing)

        // Check Default App Role
        viewModel.updateDefaultAppState(roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item {
            AnimatedVisibility(!isDefaultApp) {
                DefaultAppWarningCard {
                    val intent =
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    roleLauncher.launch(intent)
                }
            }
        }
        item {
            AnimatedVisibility(missingPermissions.isNotEmpty()) {
                PermissionWarningCard(onNavigateToSettings)
            }
        }
        item {
            AnimatedVisibility(!isDatabaseReady) {
                DatabaseWarningCard(onNavigateToSettings)
            }
        }
        blockedCallsList(
            calls = blockedCalls,
            onRetry = { call -> viewModel.retryNetworkQuery(call) },
            onClick = { call -> viewModel.openQuickAdd(call) },
        )
    }

    // 联网重查 Bottom Sheet：单实例 + AnimatedContent 避免状态切换时的闪烁
    if (retryQueryState !is RetryQueryState.Idle) {
        ModalBottomSheet(onDismissRequest = { viewModel.dismissRetry() }) {
            AnimatedContent(
                targetState = retryQueryState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "RetryQueryContent"
            ) { state ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (state) {
                        is RetryQueryState.Loading -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(
                                    stringResource(R.string.msg_querying, state.call.phoneNumber),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.dismissRetry() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.action_cancel)) }
                        }

                        is RetryQueryState.Success -> {
                            val resp = state.response
                            val remarkText = if (resp.isSpam) {
                                stringResource(
                                    R.string.msg_retry_spam,
                                    resp.tag,
                                    resp.confidence,
                                    resp.source
                                )
                            } else {
                                stringResource(R.string.msg_retry_not_spam, resp.source)
                            }
                            Text(
                                stringResource(R.string.title_query_result),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                state.call.phoneNumber,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(stringResource(if (resp.isSpam) R.string.label_is_spam_yes else R.string.label_is_spam_no))
                            if (resp.isSpam) Text("${stringResource(R.string.label_tag)}${resp.tag}")
                            Text("${stringResource(R.string.label_confidence)}${resp.confidence}%")
                            Text("${stringResource(R.string.label_source)}${resp.source}")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.dismissRetry() },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.action_close)) }
                                Button(
                                    onClick = {
                                        viewModel.writeQueryResultToRemark(
                                            state.call,
                                            remarkText
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.action_write_remark)) }
                            }
                        }

                        is RetryQueryState.Failure -> {
                            Text(
                                stringResource(R.string.title_query_failed),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { viewModel.dismissRetry() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(R.string.action_close)) }
                        }

                        RetryQueryState.Idle -> Unit
                    }
                }
            }
        }
    }

    if (showDeleteDialog && itemToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                itemToDelete = null
            },
            title = { Text(stringResource(R.string.title_delete_record)) },
            text = { Text(stringResource(R.string.msg_delete_record_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let { viewModel.delete(it) }
                        showDeleteDialog = false
                        itemToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        itemToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // 快捷添加黑白名单 BottomSheet
    viewModel.quickAddCall?.let { call ->
        val phone = call.phoneNumber
        ModalBottomSheet(onDismissRequest = { viewModel.closeQuickAdd() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = phone,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val success = viewModel.quickAddToBlackList(phone)
                            val msg: String = if (success) "已加入黑名单" else "该号码已在黑名单中"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            viewModel.closeQuickAdd()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("加入黑名单") }
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val success = viewModel.quickAddToWhiteList(phone)
                            val msg: String = if (success) "已加入白名单" else "该号码已在白名单中"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            viewModel.closeQuickAdd()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("加入白名单") }
                OutlinedButton(
                    onClick = {
                        itemToDelete = call
                        showDeleteDialog = true
                        viewModel.closeQuickAdd()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.action_delete)) }
                OutlinedButton(
                    onClick = { viewModel.closeQuickAdd() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}

@Composable
fun DefaultAppWarningCard(onClick: () -> Unit) {
    WarningCard(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        icon = Icons.Default.SettingsPhone,
        iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
        title = stringResource(R.string.warning_not_default_title),
        message = stringResource(R.string.warning_not_default_message),
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Text(stringResource(R.string.action_set_default))
        }
    }
}

@Composable
fun PermissionWarningCard(onClick: () -> Unit) {
    WarningCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        icon = Icons.Default.Warning,
        iconColor = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.warning_missing_permissions_title),
        message = stringResource(R.string.warning_missing_permissions_message),
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(stringResource(R.string.action_grant_permissions))
        }
    }
}

@Composable
fun DatabaseWarningCard(onClick: () -> Unit) {
    WarningCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        icon = Icons.Default.Warning,
        iconColor = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.warning_missing_db_title),
        message = stringResource(R.string.warning_missing_db_message),
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(stringResource(R.string.action_download_db))
        }
    }
}

private fun LazyListScope.blockedCallsList(
    calls: List<BlockedCall>,
    onRetry: (BlockedCall) -> Unit,
    onClick: (BlockedCall) -> Unit,
) {
    if (calls.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            ) {
                Text(
                    stringResource(R.string.home_no_records),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    } else {
        items(calls, key = { it.id }) { call ->
            BlockedCallItem(
                call = call,
                onRetry = if (call.resultType == ResultType.NETWORK_TIMEOUT) {
                    { onRetry(call) }
                } else null,
                onClick = { onClick(call) },
            )
        }
    }
}

@Composable
fun BlockedCallItem(
    call: BlockedCall,
    onRetry: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(),
        onClick = { onClick?.invoke() }
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isPass = call.resultType == ResultType.PASS || call.resultType == ResultType.WHITE_LIST || call.resultType == ResultType.PASS_BUT_NOTIFY
                val icon = if (isPass) Icons.Default.CheckCircle else Icons.Default.Block
                val iconColor = if (isPass) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )

                SelectionContainer(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Column {
                        // Row 1: Number + (Retry) + Time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = call.phoneNumber,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onRetry != null) {
                                    IconButton(onClick = onRetry) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = stringResource(R.string.action_retry_query),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    formatMills(call.blockTime),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Row 2: Result Type
                        val resultText = when (call.resultType) {
                            ResultType.INTERCEPT -> stringResource(R.string.result_intercept_spam)
                            ResultType.PASS_BUT_NOTIFY -> stringResource(R.string.result_pass_but_notify)
                            ResultType.NETWORK_TIMEOUT -> stringResource(R.string.result_network_timeout)
                            ResultType.PASS -> stringResource(R.string.result_pass_always_record)
                            ResultType.BLACK_LIST -> stringResource(R.string.result_black_list)
                            ResultType.WHITE_LIST -> stringResource(R.string.result_white_list)
                        }
                        val resultColor = when (call.resultType) {
                            ResultType.PASS -> MaterialTheme.colorScheme.secondary
                            ResultType.NETWORK_TIMEOUT -> MaterialTheme.colorScheme.error
                            ResultType.BLACK_LIST -> MaterialTheme.colorScheme.error
                            ResultType.WHITE_LIST -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = resultColor,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        // Row 3: Remark
                        if (!call.remark.isNullOrEmpty()) {
                            Text(
                                text = "${stringResource(R.string.label_remark)}${call.remark}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Row 4: Duration
                        val durationText = if (call.networkDuration > 0) {
                            stringResource(
                                R.string.label_duration_with_network,
                                call.localDuration,
                                call.networkDuration
                            )
                        } else {
                            stringResource(R.string.label_duration_local, call.localDuration)
                        }
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
    }
}