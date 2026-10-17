package vip.mystery0.pixel.telo.ui.screen

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.SettingsPhone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.util.queryFailureSummaryRes
import vip.mystery0.pixel.telo.util.queryFailureAdviceRes
import vip.mystery0.pixel.telo.data.PhoneNumberRuleMatcher
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.FeedbackStatus
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.entity.isNetworkFailure
import vip.mystery0.pixel.telo.data.query.OFFICIAL_BACKEND_ID
import vip.mystery0.pixel.telo.ui.components.LocalNumberLabelEditorDialogs
import vip.mystery0.pixel.telo.ui.components.LocalNumberLabelManagementSection
import vip.mystery0.pixel.telo.ui.components.EmptyState
import vip.mystery0.pixel.telo.ui.components.WarningCard
import vip.mystery0.pixel.telo.ui.util.PermissionUtils
import vip.mystery0.pixel.telo.ui.util.formatMills
import vip.mystery0.pixel.telo.viewmodel.BlockedCallListItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import vip.mystery0.pixel.telo.util.textRes
import vip.mystery0.pixel.telo.data.model.parseCommunityStatus
import vip.mystery0.pixel.telo.data.model.CommunityStatus
import vip.mystery0.pixel.telo.data.query.decodeCommunityQueryMetadata
import vip.mystery0.pixel.telo.viewmodel.CurrentListState
import vip.mystery0.pixel.telo.viewmodel.FeedbackSubmissionState
import vip.mystery0.pixel.telo.viewmodel.HomeViewModel
import vip.mystery0.pixel.telo.viewmodel.LocalNumberLabelEditorViewModel
import vip.mystery0.pixel.telo.viewmodel.RetryQueryState
import vip.mystery0.pixel.telo.viewmodel.SelfHostedWarning

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    localLabelEditorViewModel: LocalNumberLabelEditorViewModel,
    callReportViewModel: vip.mystery0.pixel.telo.viewmodel.CallReportViewModel,
    onNavigateToMyReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSourceSettings: () -> Unit,
    onNavigateToSelfHostedSettings: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val blockedCallItems = viewModel.blockedCallItems.collectAsLazyPagingItems()
    val contactNames by viewModel.contactNames.collectAsState()
    val localLabels by viewModel.localLabels.collectAsState()
    val showLocalNumberLabels by viewModel.showLocalNumberLabels.collectAsState()
    val localLabelEditorState by localLabelEditorViewModel.state.collectAsState()
    val isDatabaseReady by viewModel.isDatabaseReady.collectAsState()
    val missingPermissions by viewModel.missingPermissions.collectAsState()
    val isDefaultApp by viewModel.isDefaultApp.collectAsState()
    val retryQueryState by viewModel.retryQueryState.collectAsState()
    val unavailableSourceWarning by viewModel.unavailableSourceWarning.collectAsState()
    val selfHostedWarning by viewModel.selfHostedWarning.collectAsState()

    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check after returning from role request
        viewModel.updateDefaultAppState(roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING))
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<BlockedCall?>(null) }

    LaunchedEffect(blockedCallItems) {
        snapshotFlow {
            blockedCallItems.itemSnapshotList.items
                .map { it.call.phoneNumber }
                .toSet()
        }
            .distinctUntilChanged()
            .collect(viewModel::updateLoadedPhoneNumbers)
    }

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
        viewModel.refreshContactNames()
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
        item {
            AnimatedVisibility(unavailableSourceWarning.isNotEmpty()) {
                SourceUnavailableWarningCard(
                    sources = unavailableSourceWarning,
                    onClick = onNavigateToSourceSettings
                )
            }
        }
        selfHostedWarning?.let { warning ->
            item {
                SelfHostedWarningCard(
                    warning = warning,
                    onClick = onNavigateToSelfHostedSettings,
                )
            }
        }
        blockedCallsList(
            callItems = blockedCallItems,
            contactNames = contactNames,
            localLabels = localLabels,
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
                            Text("${stringResource(R.string.label_source)}${resp.source}")
                            val community = decodeCommunityQueryMetadata(resp, state.call.queryBackendId == "official").status
                            if (community != CommunityStatus.UNKNOWN) {
                                Text(stringResource(R.string.community_query_snapshot, stringResource(community.textRes())))
                            }
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
                                            remarkText,
                                            resp.tag
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
                                stringResource(state.resultType?.queryFailureAdviceRes() ?: R.string.title_query_failed),
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
        val reportState by callReportViewModel.state.collectAsState()
        DisposableEffect(call.id) {
            callReportViewModel.open(call.id)
            onDispose { callReportViewModel.close() }
        }
        val phone = call.phoneNumber
        val label = call.label?.takeIf { it.isNotBlank() }
        val contactName = contactNames[phone]
        val location = call.locationText()
        val noContactsAppMessage = stringResource(R.string.msg_no_contacts_app)
        val addedToBlacklistMessage = stringResource(R.string.msg_added_to_blacklist)
        val alreadyInBlacklistMessage = stringResource(R.string.msg_already_in_blacklist)
        val addedToWhitelistMessage = stringResource(R.string.msg_added_to_whitelist)
        val alreadyInWhitelistMessage = stringResource(R.string.msg_already_in_whitelist)
        val addedTagToWhitelistMessage = stringResource(R.string.msg_added_tag_to_whitelist)
        val tagAlreadyInWhitelistMessage = stringResource(R.string.msg_tag_already_in_whitelist)
        val identityLocalLabel = localLabelEditorState.currentLabel?.takeIf { it.isNotBlank() }
        var showMoreActions by remember(call.id) { mutableStateOf(false) }
        var showRecordInfo by remember(call.id) { mutableStateOf(false) }
        DisposableEffect(Unit) {
            onDispose {
                // 只在详情真正离开组合时清空，避免误伤管理页对同一 Editor 的复用
                localLabelEditorViewModel.clearTarget()
            }
        }
        LaunchedEffect(call.phoneNumber) {
            localLabelEditorViewModel.observe(call.phoneNumber)
        }
        ModalBottomSheet(onDismissRequest = { viewModel.closeQuickAdd() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!contactName.isNullOrBlank()) {
                        Text(contactName, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (showLocalNumberLabels && identityLocalLabel != null) {
                        Text(
                            stringResource(R.string.label_local_number_label, identityLocalLabel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    location?.let {
                        Text(stringResource(R.string.label_phone_location, it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    call.querySource?.let {
                        Text(stringResource(R.string.label_query_source, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                ExpandableDetailRow(
                    title = stringResource(R.string.title_record_info),
                    expanded = showRecordInfo,
                    onClick = { showRecordInfo = !showRecordInfo },
                )
                AnimatedVisibility(showRecordInfo) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (!call.remark.isNullOrBlank()) {
                            Text("${stringResource(R.string.label_remark)}${call.remark}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = if (call.networkDuration > 0) stringResource(R.string.label_duration_with_network, call.localDuration, call.networkDuration)
                            else stringResource(R.string.label_duration_local, call.localDuration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (call.queryBackendId == OFFICIAL_BACKEND_ID && call.feedbackStatus != FeedbackStatus.UNAVAILABLE) {
                    Text(stringResource(R.string.label_call_feedback), style = MaterialTheme.typography.titleSmall)
                    FeedbackSection(
                        call = call,
                        submissionState = viewModel.feedbackSubmissionState,
                        onSubmit = { positive -> viewModel.submitFeedback(call, positive) }
                    )
                }
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.label_community_report_section), style = MaterialTheme.typography.titleMedium)
                        CommunityHistorySummary(call)
                        vip.mystery0.pixel.telo.ui.components.CommunityReportSection(
                            state = reportState,
                            onSubmit = callReportViewModel::submit,
                            onOpenReports = onNavigateToMyReports,
                        )
                    }
                }
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(stringResource(R.string.label_number_rules), style = MaterialTheme.typography.titleMedium)
                        LocalNumberLabelManagementSection(
                            state = localLabelEditorState,
                            onSet = localLabelEditorViewModel::openEditor,
                            onEdit = localLabelEditorViewModel::openEditor,
                            onDelete = localLabelEditorViewModel::requestDelete,
                        )
                        HorizontalDivider()
                        DetailActionRow(
                            icon = Icons.Default.Block,
                            title = stringResource(R.string.action_add_to_blacklist),
                            onClick = {
                                coroutineScope.launch {
                                    val success = viewModel.quickAddToBlackList(phone)
                                    Toast.makeText(context, if (success) addedToBlacklistMessage else alreadyInBlacklistMessage, Toast.LENGTH_SHORT).show()
                                    viewModel.closeQuickAdd()
                                }
                            },
                        )
                        HorizontalDivider()
                        DetailActionRow(
                            icon = Icons.Default.CheckCircle,
                            title = stringResource(R.string.action_add_to_whitelist),
                            onClick = {
                                coroutineScope.launch {
                                    val success = viewModel.quickAddToWhiteList(phone)
                                    Toast.makeText(context, if (success) addedToWhitelistMessage else alreadyInWhitelistMessage, Toast.LENGTH_SHORT).show()
                                    viewModel.closeQuickAdd()
                                }
                            },
                        )
                        if (label != null) {
                            HorizontalDivider()
                            DetailActionRow(
                                icon = Icons.Default.CheckCircle,
                                title = stringResource(R.string.action_add_tag_to_whitelist, label),
                                onClick = {
                                    coroutineScope.launch {
                                        val success = viewModel.quickAddTagToWhiteList(label)
                                        Toast.makeText(context, if (success) addedTagToWhitelistMessage else tagAlreadyInWhitelistMessage, Toast.LENGTH_SHORT).show()
                                        viewModel.closeQuickAdd()
                                    }
                                },
                            )
                        }
                    }
                }
                ExpandableDetailRow(
                    title = stringResource(R.string.title_more_actions),
                    expanded = showMoreActions,
                    onClick = { showMoreActions = !showMoreActions },
                )
                AnimatedVisibility(showMoreActions) {
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        DetailActionRow(
                            icon = Icons.Default.PersonAdd,
                            title = stringResource(R.string.action_save_to_contacts),
                            onClick = {
                                val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                    type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                    putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                                    if (label != null) putExtra(ContactsContract.Intents.Insert.NAME, label)
                                }
                                try {
                                    context.startActivity(intent)
                                    viewModel.closeQuickAdd()
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, noContactsAppMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                        DetailActionRow(
                            icon = Icons.Default.DeleteOutline,
                            title = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                itemToDelete = call
                                showDeleteDialog = true
                                viewModel.closeQuickAdd()
                            },
                        )
                    }
                }
            }
        }
    }

    LocalNumberLabelEditorDialogs(
        state = localLabelEditorState,
        onDraftChange = localLabelEditorViewModel::updateDraft,
        onSave = localLabelEditorViewModel::save,
        onDismissEditor = localLabelEditorViewModel::close,
        onConfirmDelete = localLabelEditorViewModel::confirmDelete,
        onDismissDelete = localLabelEditorViewModel::close,
    )
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

/**
 * 已启用的联网查询 source 被服务端下线时的提示卡片。
 * 不提供忽略操作，用户修正配置后自动消失。
 */
@Composable
fun SourceUnavailableWarningCard(sources: List<String>, onClick: () -> Unit) {
    WarningCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        icon = Icons.Default.Warning,
        iconColor = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.warning_source_unavailable_title),
        message = stringResource(
            R.string.warning_source_unavailable_message,
            sources.joinToString(", ")
        ),
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(stringResource(R.string.action_adjust_sources))
        }
    }
}

/**
 * 自建查询服务被安全阻止时的提示卡片。
 *
 * 卡片只显示本地化的安全分类；地址、Token、Pin、Header、服务端正文及异常堆栈均不进入 UI。
 */
@Composable
fun SelfHostedWarningCard(warning: SelfHostedWarning, onClick: () -> Unit) {
    WarningCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        icon = Icons.Default.Warning,
        iconColor = MaterialTheme.colorScheme.error,
        title = stringResource(R.string.warning_self_hosted_title),
        message = stringResource(warning.messageResId()),
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(stringResource(R.string.action_open_query_backend_settings))
        }
    }
}

/** 返回首页安全卡片使用的非敏感分类文案。 */
private fun SelfHostedWarning.messageResId(): Int = when (this) {
    SelfHostedWarning.CONFIGURATION -> R.string.warning_self_hosted_configuration
    SelfHostedWarning.CREDENTIALS -> R.string.warning_self_hosted_credentials
    SelfHostedWarning.TLS -> R.string.warning_self_hosted_tls
    SelfHostedWarning.SPKI_PIN -> R.string.warning_self_hosted_spki_pin
    SelfHostedWarning.SERVER_VERSION -> R.string.warning_self_hosted_server_version
    SelfHostedWarning.API_VERSION -> R.string.warning_self_hosted_api_version
    SelfHostedWarning.INSTANCE_CHANGED -> R.string.warning_self_hosted_instance_changed
    SelfHostedWarning.IDENTITY_HEADERS -> R.string.warning_self_hosted_identity_headers
}

/**
 * 记录详情中的反馈区域。
 * PENDING 时提供“结果准确/结果不准确”操作，提交中禁用；
 * 终态只展示状态文字；UNAVAILABLE 不展示任何内容。
 */
@Composable
private fun FeedbackSection(
    call: BlockedCall,
    submissionState: FeedbackSubmissionState,
    onSubmit: (Boolean) -> Unit,
) {
    if (call.queryBackendId != OFFICIAL_BACKEND_ID) return
    when (call.feedbackStatus) {
        FeedbackStatus.UNAVAILABLE -> Unit

        FeedbackStatus.PENDING -> {
            if (submissionState is FeedbackSubmissionState.Failure &&
                submissionState.callId == call.id
            ) {
                Text(
                    stringResource(R.string.msg_feedback_failed, submissionState.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            val submitting = submissionState is FeedbackSubmissionState.Submitting &&
                submissionState.callId == call.id
            DetailActionRow(
                icon = Icons.Default.ThumbUp,
                title = stringResource(R.string.action_feedback_positive),
                enabled = !submitting,
                onClick = { onSubmit(true) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
            DetailActionRow(
                icon = Icons.Default.ThumbDown,
                title = stringResource(R.string.action_feedback_negative),
                enabled = !submitting,
                onClick = { onSubmit(false) },
            )
        }

        FeedbackStatus.POSITIVE -> FeedbackStatusText(R.string.feedback_status_positive)
        FeedbackStatus.NEGATIVE -> FeedbackStatusText(R.string.feedback_status_negative)
        FeedbackStatus.ALREADY_SUBMITTED ->
            FeedbackStatusText(R.string.feedback_status_already_submitted)

        FeedbackStatus.EXPIRED -> FeedbackStatusText(R.string.feedback_status_expired)
        FeedbackStatus.INVALID -> FeedbackStatusText(R.string.feedback_status_invalid)
    }
}

@Composable
private fun ExpandableDetailRow(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val stateDescription = stringResource(
        if (expanded) R.string.state_expanded else R.string.state_collapsed,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.stateDescription = stateDescription }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 反馈终态的状态文字 */
@Composable
private fun FeedbackStatusText(textResId: Int) {
    Text(
        stringResource(textResId),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
    callItems: LazyPagingItems<BlockedCallListItem>,
    contactNames: Map<String, String>,
    localLabels: Map<String, String>,
    onRetry: (BlockedCall) -> Unit,
    onClick: (BlockedCall) -> Unit,
) {
    when {
        callItems.itemCount == 0 && callItems.loadState.refresh is LoadState.Loading -> {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        callItems.itemCount == 0 && callItems.loadState.refresh is LoadState.Error -> {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.home_records_load_failed))
                    Button(onClick = callItems::retry) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }

        callItems.itemCount == 0 -> {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                ) {
                    EmptyState(
                        icon = Icons.Default.SettingsPhone,
                        title = stringResource(R.string.home_no_records),
                        description = stringResource(R.string.home_no_records_hint),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        else -> {
            if (callItems.loadState.refresh is LoadState.Loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.Center),
                        )
                    }
                }
            }

            if (callItems.loadState.refresh is LoadState.Error) {
                item {
                    TextButton(
                        onClick = callItems::retry,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.home_records_load_failed))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }

            items(
                count = callItems.itemCount,
                key = callItems.itemKey { it.call.id },
            ) { index ->
                callItems[index]?.let { item ->
                    val call = item.call
                    BlockedCallItem(
                        call = call,
                        contactName = contactNames[call.phoneNumber],
                        localLabel = localLabels[call.phoneNumber],
                        currentListState = item.currentListState,
                        // 明确的非中国国际号码不支持联网重试，避免用户进入不可用的重试流程。
                        onRetry = if (
                            call.resultType.isNetworkFailure() &&
                            !PhoneNumberRuleMatcher.isExplicitInternational(call.phoneNumber)
                        ) {
                            { onRetry(call) }
                        } else {
                            null
                        },
                        onClick = { onClick(call) },
                    )
                }
            }
        }
    }

    if (callItems.loadState.append is LoadState.Loading) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center),
                )
            }
        }
    }

    if (callItems.loadState.append is LoadState.Error) {
        item {
            TextButton(
                onClick = callItems::retry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun CommunityHistorySummary(call: BlockedCall) {
    val community = parseCommunityStatus(call.communityStatus)
    if (community != CommunityStatus.UNKNOWN || call.communityBlockApplied) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SnapshotBadge(
                text = stringResource(R.string.community_snapshot, stringResource(community.textRes())),
            )
            if (call.communityBlockApplied) {
                SnapshotBadge(
                    text = stringResource(R.string.community_block_applied),
                    warning = true,
                )
            }
        }
    }
}

@Composable
private fun SnapshotBadge(text: String, warning: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (warning) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun BlockedCall.locationText(): String? = listOfNotNull(
    province?.trim()?.takeIf { it.isNotEmpty() },
    city?.trim()?.takeIf { it.isNotEmpty() },
).distinct().joinToString(" ").takeIf { it.isNotEmpty() }

@Composable
fun BlockedCallItem(
    call: BlockedCall,
    contactName: String? = null,
    localLabel: String? = null,
    currentListState: CurrentListState = CurrentListState.NONE,
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
                val icon = when {
                    call.resultType.isNetworkFailure() -> Icons.Default.Warning
                    call.resultType == ResultType.PASS_BUT_NOTIFY -> Icons.Default.Warning
                    call.resultType == ResultType.PASS ||
                        call.resultType == ResultType.WHITE_LIST -> Icons.Default.CheckCircle
                    else -> Icons.Default.Block
                }
                val iconColor = when {
                    call.resultType.isNetworkFailure() -> MaterialTheme.colorScheme.error
                    call.resultType == ResultType.PASS_BUT_NOTIFY -> MaterialTheme.colorScheme.primary
                    call.resultType == ResultType.PASS ||
                        call.resultType == ResultType.WHITE_LIST -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }

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
                        val timeText = formatMills(call.blockTime)
                        // 默认字号下将号码与时间并排，大字号时拆行以保留号码完整性。
                        if (LocalDensity.current.fontScale <= 1.15f) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (!contactName.isNullOrBlank()) Text(contactName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = call.phoneNumber,
                                        style = if (contactName.isNullOrBlank()) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                if (onRetry != null) {
                                    IconButton(onClick = onRetry) {
                                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_retry_query), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(timeText, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (!contactName.isNullOrBlank()) Text(contactName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = call.phoneNumber,
                                    style = if (contactName.isNullOrBlank()) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                )
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (onRetry != null) {
                                        IconButton(onClick = onRetry) {
                                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_retry_query), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Text(timeText, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        call.locationText()?.let { location ->
                            Text(
                                text = location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }

                        // 独立本地标签行，不与数据源标签拼接
                        if (!localLabel.isNullOrBlank()) {
                            Text(
                                text = stringResource(
                                    R.string.label_local_number_label,
                                    localLabel,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }

                        CommunityHistorySummary(call)
                        val sourceLabel = call.label?.takeIf { it.isNotBlank() }
                        if (sourceLabel != null) {
                            Text(
                                text = stringResource(
                                    R.string.label_source_number_label,
                                    sourceLabel,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }

                        // 第二部分：拦截结果
                        val failureSummary = call.resultType.queryFailureSummaryRes()
                        val resultText = if (failureSummary != null) {
                            stringResource(R.string.result_query_failure, stringResource(failureSummary))
                        } else when (call.resultType) {
                            ResultType.INTERCEPT -> stringResource(R.string.result_intercept_spam)
                            ResultType.PASS_BUT_NOTIFY -> stringResource(R.string.result_pass_but_notify)
                            ResultType.PASS -> stringResource(R.string.result_pass_always_record)
                            ResultType.BLACK_LIST -> stringResource(R.string.result_black_list)
                            ResultType.WHITE_LIST -> stringResource(R.string.result_white_list)
                            else -> stringResource(R.string.title_query_failed)
                        }
                        val resultColor = if (call.resultType.isNetworkFailure()) {
                            MaterialTheme.colorScheme.error
                        } else when (call.resultType) {
                            ResultType.PASS -> MaterialTheme.colorScheme.secondary
                            ResultType.BLACK_LIST -> MaterialTheme.colorScheme.error
                            ResultType.WHITE_LIST -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = resultColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        if (currentListState != CurrentListState.NONE) {
                            val currentListText = when (currentListState) {
                                CurrentListState.BLACK -> stringResource(R.string.current_list_black)
                                CurrentListState.WHITE -> stringResource(R.string.current_list_white)
                                CurrentListState.BOTH -> stringResource(R.string.current_list_both)
                            }
                            val currentListColor = when (currentListState) {
                                CurrentListState.BLACK, CurrentListState.BOTH -> MaterialTheme.colorScheme.error
                                CurrentListState.WHITE -> MaterialTheme.colorScheme.tertiary
                            }
                            Text(
                                text = currentListText,
                                style = MaterialTheme.typography.labelLarge,
                                color = currentListColor,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

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
