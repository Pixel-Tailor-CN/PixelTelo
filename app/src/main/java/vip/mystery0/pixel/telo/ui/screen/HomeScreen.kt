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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.SettingsPhone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CancellationException
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.util.queryFailureAdviceRes
import vip.mystery0.pixel.telo.data.PhoneNumberRuleMatcher
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.FeedbackStatus
import vip.mystery0.pixel.telo.data.entity.isNetworkFailure
import vip.mystery0.pixel.telo.data.query.OFFICIAL_BACKEND_ID
import vip.mystery0.pixel.telo.ui.components.BlockedCallItem
import vip.mystery0.pixel.telo.ui.components.CallHistorySection
import vip.mystery0.pixel.telo.ui.components.CallResultSummary
import vip.mystery0.pixel.telo.ui.components.CommunityReportSection
import vip.mystery0.pixel.telo.ui.components.LocalNumberLabelEditorDialogs
import vip.mystery0.pixel.telo.ui.components.NumberRuleAction
import vip.mystery0.pixel.telo.ui.components.NumberRuleConfirmation
import vip.mystery0.pixel.telo.ui.components.NumberQuickActions
import vip.mystery0.pixel.telo.ui.components.EmptyState
import vip.mystery0.pixel.telo.ui.components.WarningCard
import vip.mystery0.pixel.telo.ui.util.PermissionUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import vip.mystery0.pixel.telo.ui.util.formatMills
import vip.mystery0.pixel.telo.viewmodel.BlockedCallListItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import vip.mystery0.pixel.telo.util.textRes
import vip.mystery0.pixel.telo.data.model.CommunityStatus
import vip.mystery0.pixel.telo.data.query.decodeCommunityQueryMetadata
import vip.mystery0.pixel.telo.viewmodel.FeedbackSubmissionState
import vip.mystery0.pixel.telo.viewmodel.HomeViewModel
import vip.mystery0.pixel.telo.viewmodel.LocalNumberLabelEditorViewModel
import vip.mystery0.pixel.telo.viewmodel.RetryQueryState
import vip.mystery0.pixel.telo.viewmodel.SelfHostedWarning

@OptIn(ExperimentalLayoutApi::class)
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
                                Text(stringResource(R.string.community_query_snapshot,
                                    stringResource(R.string.community_status_prefixed, stringResource(community.textRes()))))
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
        val labelEnabled = !localLabelEditorState.saving &&
            !localLabelEditorState.observing && !phone.isBlank()
        DisposableEffect(Unit) {
            onDispose { localLabelEditorViewModel.clearTarget() }
        }
        LaunchedEffect(call.phoneNumber) {
            localLabelEditorViewModel.observe(call.phoneNumber)
        }
        val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        NumberRuleConfirmation(
            callId = call.id,
            phone = phone,
            label = label,
            onAdd = { action ->
                val current = viewModel.quickAddCall
                if (current?.id != call.id || current.phoneNumber != phone ||
                    current.label?.takeIf(String::isNotBlank) != label
                ) throw CancellationException("Call target changed")
                when (action) {
                    NumberRuleAction.BLACKLIST -> viewModel.quickAddToBlackList(phone)
                    NumberRuleAction.WHITELIST -> viewModel.quickAddToWhiteList(phone)
                    NumberRuleAction.TAG_WHITELIST -> viewModel.quickAddTagToWhiteList(requireNotNull(label))
                }
            },
            onComplete = { action, added ->
                val message = when (action) {
                    NumberRuleAction.BLACKLIST -> if (added) addedToBlacklistMessage else alreadyInBlacklistMessage
                    NumberRuleAction.WHITELIST -> if (added) addedToWhitelistMessage else alreadyInWhitelistMessage
                    NumberRuleAction.TAG_WHITELIST -> if (added) addedTagToWhitelistMessage else tagAlreadyInWhitelistMessage
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.closeQuickAdd()
            },
        ) { requestRule ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeQuickAdd() },
                sheetState = detailSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 16.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!contactName.isNullOrBlank()) {
                                Text(contactName, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(phone, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                if (location != null) {
                                    stringResource(R.string.record_location_and_time, location, formatMills(call.blockTime))
                                } else {
                                    formatMills(call.blockTime)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                label?.let {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Cloud, contentDescription = stringResource(R.string.cd_source_label),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                call.querySource?.trim()?.takeIf(String::isNotBlank)?.let {
                                    RecordSourceChip(it)
                                }
                                CallResultSummary(resultType = call.resultType)
                            }
                            if (showLocalNumberLabels && identityLocalLabel != null) {
                                RecordDetailValue(
                                    icon = Icons.Default.Edit,
                                    description = stringResource(R.string.cd_local_label_source),
                                    value = identityLocalLabel,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        NumberQuickActions(
                            hasLocalLabel = identityLocalLabel != null,
                            labelEnabled = labelEnabled,
                            onAddToBlacklist = { requestRule(NumberRuleAction.BLACKLIST) },
                            onAddToWhitelist = { requestRule(NumberRuleAction.WHITELIST) },
                            onEditLocalLabel = localLabelEditorViewModel::openEditor,
                        )
                    }
                    HorizontalDivider()
                    if (label != null) {
                        TextButton(
                            onClick = { requestRule(NumberRuleAction.TAG_WHITELIST) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.action_add_tag_to_whitelist, label),
                                    style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.tag_whitelist_scope),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }

                    if (label != null) HorizontalDivider()
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        CallHistorySection(call)
                    }
                    HorizontalDivider()
                    val showingSubmittedReport = reportState.accepted && !reportState.loading && !reportState.submitting
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 12.dp, bottom = if (showingSubmittedReport) 4.dp else 12.dp),
                            // 已提交行自带48dp触摸高度，中心20dp文字上下各有14dp布局留白。
                            verticalArrangement = Arrangement.spacedBy(if (showingSubmittedReport) 0.dp else 4.dp),
                        ) {
                            if (call.queryBackendId == OFFICIAL_BACKEND_ID &&
                                call.feedbackStatus != FeedbackStatus.UNAVAILABLE
                            ) {
                                // 按钮可见40dp、触摸48dp，上下各4dp；14dp外距合成约18dp视觉间隔。
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(stringResource(R.string.label_call_feedback),
                                        style = MaterialTheme.typography.titleSmall)
                                    FeedbackSection(
                                        call = call,
                                        submissionState = viewModel.feedbackSubmissionState,
                                        onSubmit = { positive -> viewModel.submitFeedback(call, positive) },
                                    )
                                }
                            }
                            CommunityReportSection(
                                state = reportState,
                                onSubmit = callReportViewModel::submit,
                                onViewReports = {
                                    viewModel.closeQuickAdd()
                                    onNavigateToMyReports()
                                },
                            )
                        }
                    }
                    HorizontalDivider()

                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                                    type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                                    putExtra(ContactsContract.Intents.Insert.PHONE, phone)
                                    if (label != null) putExtra(ContactsContract.Intents.Insert.NAME, label)
                                }
                                try {
                                    context.startActivity(intent)
                                    viewModel.closeQuickAdd()
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, noContactsAppMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_save_to_contacts))
                        }
                        TextButton(
                            onClick = {
                                itemToDelete = call
                                showDeleteDialog = true
                                viewModel.closeQuickAdd()
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_delete_record))
                        }
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
        onRequestDelete = localLabelEditorViewModel::requestDelete,
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
 * PENDING 时提供“准确/不准确”操作，提交中禁用；
 * 终态只展示状态文字；UNAVAILABLE 不展示任何内容。
 */
@OptIn(ExperimentalLayoutApi::class)
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(
                    onClick = { onSubmit(true) },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_feedback_positive))
                }
                OutlinedButton(
                    onClick = { onSubmit(false) },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_feedback_negative))
                }
            }
        }

        FeedbackStatus.POSITIVE -> FeedbackStatusText(R.string.feedback_status_positive)
        FeedbackStatus.NEGATIVE -> FeedbackStatusText(R.string.feedback_status_negative)
        FeedbackStatus.ALREADY_SUBMITTED ->
            FeedbackStatusText(R.string.feedback_status_already_submitted)

        FeedbackStatus.EXPIRED -> FeedbackStatusText(R.string.feedback_status_expired)
        FeedbackStatus.INVALID -> FeedbackStatusText(R.string.feedback_status_invalid)
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
        inlineActionWhenPossible = true,
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
                    val date = call.recordDate()
                    // 对比整个已加载窗口的前一条记录，避免跨页同日重复分组，也不额外触发加载。
                    val previousDate = if (index > 0) callItems.peek(index - 1)?.call?.recordDate() else null
                    if (date != previousDate) {
                        val today = LocalDate.now()
                        val dateText = when (date) {
                            today -> stringResource(R.string.record_date_today)
                            today.minusDays(1) -> stringResource(R.string.record_date_yesterday)
                            else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        }
                        Text(dateText, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
private fun RecordSourceChip(source: String) {
    val description = stringResource(R.string.label_query_source, source)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = source,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordDetailValue(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    value: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = description, tint = tint)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun BlockedCall.locationText(): String? = listOfNotNull(
    province?.trim()?.takeIf { it.isNotEmpty() },
    city?.trim()?.takeIf { it.isNotEmpty() },
).distinct().joinToString(" ").takeIf { it.isNotEmpty() }

private fun BlockedCall.recordDate(): LocalDate =
    Instant.ofEpochMilli(blockTime).atZone(ZoneId.systemDefault()).toLocalDate()
