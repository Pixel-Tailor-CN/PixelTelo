package vip.mystery0.pixel.telo.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import vip.mystery0.pixel.telo.ui.components.LocalNavigationActive
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.util.queryFailureSummaryRes
import vip.mystery0.pixel.telo.util.queryFailureAdviceRes
import vip.mystery0.pixel.telo.data.dao.QuerySourceQuality
import vip.mystery0.pixel.telo.data.query.QueryBackendState
import vip.mystery0.pixel.telo.data.query.QueryBackendType
import vip.mystery0.pixel.telo.data.query.SelfHostedConnectionState
import vip.mystery0.pixel.telo.data.repository.QuerySourceItem
import vip.mystery0.pixel.telo.service.IncomingCallOverlay
import vip.mystery0.pixel.telo.ui.screen.settings.AboutPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.BackupRestorePreferences
import vip.mystery0.pixel.telo.ui.screen.settings.DebugPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.DisplayPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.InterceptBehaviorPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.LocationOverlayPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.OfflineDatabasePreferences
import vip.mystery0.pixel.telo.ui.screen.settings.OnlineQueryPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.PermissionsPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.SettingsSection
import vip.mystery0.pixel.telo.ui.screen.settings.SettingsCategory
import vip.mystery0.pixel.telo.ui.screen.settings.SettingsGroup
import vip.mystery0.pixel.telo.ui.screen.settings.SettingsItemRow
import vip.mystery0.pixel.telo.ui.screen.settings.SettingsRowItem
import vip.mystery0.pixel.telo.ui.util.PermissionUtils
import vip.mystery0.pixel.telo.ui.util.backupDateTimeFormatter
import vip.mystery0.pixel.telo.viewmodel.BackupRestoreState
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel
import vip.mystery0.pixel.telo.util.textRes
import vip.mystery0.pixel.telo.data.model.CommunityStatus
import vip.mystery0.pixel.telo.worker.OfflineDatabaseUpdateScheduler
import java.time.LocalDateTime

@Composable
fun SettingsScreen(
    viewModel: SettingViewModel,
    engagementPromptViewModel: vip.mystery0.pixel.telo.viewmodel.EngagementPromptViewModel,
    saveableStateHolder: SaveableStateHolder,
    settingsCategory: SettingsCategory?,
    onNavigateToCategory: (SettingsCategory) -> Unit,
    onNavigateToLocalNumberLabels: () -> Unit,
    onNavigateToMyReports: () -> Unit,
    onNavigateToDeveloperSupport: () -> Unit,
) {
    val active = LocalNavigationActive.current
    val latestActive by rememberUpdatedState(active)
    val context = LocalContext.current
    val queryBackendState by viewModel.queryBackendState.collectAsState()
    val selfHostedConnectionState by viewModel.selfHostedConnectionState.collectAsState()
    val autoCheckPermissionDeniedMessage = stringResource(
        R.string.msg_auto_check_update_requires_notification_permission
    )
    val feedbackPermissionDeniedMessage = stringResource(
        R.string.msg_feedback_notification_requires_permissions
    )
    val callStateVibrationPermissionDeniedMessage = stringResource(
        R.string.msg_call_state_vibration_requires_phone_state_permission
    )
    val feedbackPermissions = remember {
        buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val previewOverlay = remember {
        IncomingCallOverlay(
            context,
            context.getSharedPreferences("pixel_telo", Context.MODE_PRIVATE)
        )
    }
    var overlayPermissionGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var permissionsState by remember {
        mutableStateOf(
            PermissionUtils.allPermissions.associate {
                it.permission to (ContextCompat.checkSelfPermission(
                    context,
                    it.permission
                ) == PackageManager.PERMISSION_GRANTED)
            }
        )
    }

    var pendingPermissionAction by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val permissionRequester = vip.mystery0.pixel.telo.ui.util.rememberPermissionRequester { result ->
        permissionsState = permissionsState + result
        val granted = when (pendingPermissionAction) {
            "feedback" -> feedbackPermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            else -> result.values.all { it }
        }
        val denialMessage = when (pendingPermissionAction) {
            "feedback" -> {
                viewModel.updateFeedbackNotification(granted)
                feedbackPermissionDeniedMessage
            }
            "notification" -> {
                viewModel.updateAutoCheckUpdate(granted)
                autoCheckPermissionDeniedMessage
            }
            "vibration" -> {
                viewModel.updateCallStateVibrationEnabled(granted)
                callStateVibrationPermissionDeniedMessage
            }
            else -> null
        }
        if (!granted && denialMessage != null) Toast.makeText(context, denialMessage, Toast.LENGTH_SHORT).show()
        pendingPermissionAction = null
    }
    val launcher: (Array<String>) -> Unit = { permissions ->
        pendingPermissionAction = null
        permissionRequester.launch(permissions)
    }
    val feedbackPermissionLauncher: (Array<String>) -> Unit = { permissions ->
        pendingPermissionAction = "feedback"
        permissionRequester.launch(permissions)
    }
    val notificationPermissionLauncher: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingPermissionAction = "notification"
            permissionRequester.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        } else {
            viewModel.updateAutoCheckUpdate(true)
        }
    }
    val callStateVibrationPermissionLauncher: () -> Unit = {
        pendingPermissionAction = "vibration"
        permissionRequester.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
    }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayPermissionGranted = Settings.canDrawOverlays(context)
    }

    // 备份：通过系统文件保存对话框选择保存位置
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let(viewModel::backupToUri)
    }

    // 恢复：通过系统文件选择对话框选择备份文件，先解析预览
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::readBackupUri)
    }

    fun checkPermissions() {
        permissionsState = PermissionUtils.allPermissions.associate {
            it.permission to (ContextCompat.checkSelfPermission(
                context,
                it.permission
            ) == PackageManager.PERMISSION_GRANTED)
        }
        overlayPermissionGranted = Settings.canDrawOverlays(context)
        if (
            viewModel.autoCheckUpdate &&
            !OfflineDatabaseUpdateScheduler.hasNotificationPermission(context)
        ) {
            viewModel.updateAutoCheckUpdate(false)
        }
        if (
            viewModel.feedbackNotification &&
            feedbackPermissions.any { permission ->
                ContextCompat.checkSelfPermission(context, permission) !=
                        PackageManager.PERMISSION_GRANTED
            }
        ) {
            viewModel.updateFeedbackNotification(false)
        }
        if (
            viewModel.callStateVibrationEnabled &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.updateCallStateVibrationEnabled(false)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        checkPermissions()
    }

    DisposableEffect(Unit) {
        onDispose {
            previewOverlay.hide()
            if (latestActive) viewModel.hideLocationOverlayAdjuster()
        }
    }
    var wasActive by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (wasActive && !active) viewModel.hideLocationOverlayAdjuster()
        wasActive = active
    }

    val permissionOverlayDesc = stringResource(R.string.permission_overlay_desc)
    LaunchedEffect(
        active,
        viewModel.showLocationOverlayAdjuster,
        viewModel.locationOverlayStyle
    ) {
        if (active && viewModel.showLocationOverlayAdjuster) {
            val shown = previewOverlay.showPreview(
                viewModel.locationOverlayOffsetDp,
                viewModel::updateLocationOverlayOffset
            )
            if (!shown) {
                viewModel.hideLocationOverlayAdjuster()
                Toast.makeText(
                    context,
                    permissionOverlayDesc,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            previewOverlay.hide()
        }
    }

    val msgDebugModeEnabled = stringResource(R.string.msg_debug_mode_enabled)
    LaunchedEffect(viewModel.debugUnlocked) {
        if (active && viewModel.debugUnlocked) {
            Toast.makeText(context, msgDebugModeEnabled, Toast.LENGTH_SHORT).show()
        }
    }

    // 同步状态属于一次性 UI 事件，必须在副作用中消费，避免重组时重复显示或写状态。
    val syncStatusMessage = viewModel.syncStatusMessage
    LaunchedEffect(syncStatusMessage, active) {
        if (active && syncStatusMessage != null) {
            Toast.makeText(context, syncStatusMessage, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    // 动画保留的离场页面不再创建弹窗，避免同一 ViewModel 同时显示两份 Sheet。
    if (active) {
        // 备份/恢复结果 Bottom Sheet
        val backupState = viewModel.backupRestoreState
        val backupResultSheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        val backupOptionsSheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        val restoreOptionsSheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
        if (backupState is BackupRestoreState.Processing) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.operation_processing)) },
                text = { CircularProgressIndicator() },
                confirmButton = {},
            )
        }
        if (backupState is BackupRestoreState.Success) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissBackupRestoreResult() },
                sheetState = backupResultSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.title_operation_success),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        backupState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { viewModel.dismissBackupRestoreResult() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.action_ok)) }
                }
            }
        } else if (backupState is BackupRestoreState.Failure) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissBackupRestoreResult() },
                sheetState = backupResultSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.title_operation_failed),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        backupState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { viewModel.dismissBackupRestoreResult() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.action_ok)) }
                }
            }
        }

        // 备份内容选择 Sheet
        if (viewModel.showBackupOptionsSheet) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeBackupOptionsSheet() },
                sheetState = backupOptionsSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.title_backup_select),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        stringResource(R.string.msg_backup_select_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.community_backup_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BackupCheckboxRow(
                        checked = viewModel.backupOptions.includeBlockedCalls,
                        label = stringResource(R.string.label_backup_blocked_calls),
                        onCheckedChange = {
                            viewModel.backupOptions =
                                viewModel.backupOptions.copy(includeBlockedCalls = it)
                        }
                    )
                    BackupCheckboxRow(
                        checked = viewModel.backupOptions.includeBlackList,
                        label = stringResource(R.string.label_backup_blacklist),
                        onCheckedChange = {
                            viewModel.backupOptions =
                                viewModel.backupOptions.copy(includeBlackList = it)
                        }
                    )
                    BackupCheckboxRow(
                        checked = viewModel.backupOptions.includeWhiteList,
                        label = stringResource(R.string.label_backup_whitelist),
                        onCheckedChange = {
                            viewModel.backupOptions =
                                viewModel.backupOptions.copy(includeWhiteList = it)
                        }
                    )
                    BackupCheckboxRow(
                        checked = viewModel.backupOptions.includeLocalNumberLabels,
                        label = stringResource(R.string.label_backup_local_number_labels),
                        onCheckedChange = {
                            viewModel.backupOptions =
                                viewModel.backupOptions.copy(includeLocalNumberLabels = it)
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.closeBackupOptionsSheet() },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.action_cancel)) }
                        val opts = viewModel.backupOptions
                        val date = LocalDateTime.now().format(backupDateTimeFormatter)
                        Button(
                            onClick = { backupLauncher.launch("pixeltelo_backup_$date.zip") },
                            enabled = opts.includeBlockedCalls || opts.includeBlackList ||
                                opts.includeWhiteList || opts.includeLocalNumberLabels,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.action_backup)) }
                    }
                }
            }
        }

        // 恢复内容选择 Sheet
        val preview = viewModel.backupPreview
        if (preview != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeRestoreOptionsSheet() },
                sheetState = restoreOptionsSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.title_restore_select),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        stringResource(R.string.msg_restore_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BackupCheckboxRow(
                        checked = viewModel.restoreOptions.includeBlockedCalls,
                        label = stringResource(
                            R.string.label_restore_blocked_calls,
                            preview.blockedCallCount
                        ),
                        enabled = preview.blockedCallCount > 0,
                        onCheckedChange = {
                            viewModel.restoreOptions =
                                viewModel.restoreOptions.copy(includeBlockedCalls = it)
                        }
                    )
                    BackupCheckboxRow(
                        checked = viewModel.restoreOptions.includeBlackList,
                        label = stringResource(
                            R.string.label_restore_blacklist,
                            preview.blackListCount
                        ),
                        enabled = preview.blackListCount > 0,
                        onCheckedChange = {
                            viewModel.restoreOptions =
                                viewModel.restoreOptions.copy(includeBlackList = it)
                        }
                    )
                    BackupCheckboxRow(
                        checked = viewModel.restoreOptions.includeWhiteList,
                        label = stringResource(
                            R.string.label_restore_whitelist,
                            preview.whiteListCount
                        ),
                        enabled = preview.whiteListCount > 0,
                        onCheckedChange = {
                            viewModel.restoreOptions =
                                viewModel.restoreOptions.copy(includeWhiteList = it)
                        }
                    )
                    BackupCheckboxRow(
                        checked = viewModel.restoreOptions.includeLocalNumberLabels,
                        label = stringResource(
                            R.string.label_restore_local_number_labels,
                            preview.localNumberLabelCount,
                        ),
                        enabled = preview.localNumberLabelCount > 0,
                        onCheckedChange = {
                            viewModel.restoreOptions = viewModel.restoreOptions.copy(
                                includeLocalNumberLabels = it,
                            )
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.closeRestoreOptionsSheet() },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.action_cancel)) }
                        val opts = viewModel.restoreOptions
                        val canRestore =
                            (opts.includeBlockedCalls && preview.blockedCallCount > 0) ||
                                (opts.includeBlackList && preview.blackListCount > 0) ||
                                (opts.includeWhiteList && preview.whiteListCount > 0) ||
                                (opts.includeLocalNumberLabels && preview.localNumberLabelCount > 0)
                        Button(
                            onClick = { viewModel.performRestoreWithOptions() },
                            enabled = canRestore,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.action_restore)) }
                    }
                }
            }
        }

        // 更新离线数据 Bottom Sheet：确认与下载进度共用同一个 Sheet，用 AnimatedContent 切换
        if (viewModel.showUpdateDialog != null || (viewModel.isDownloading && viewModel.showDownloadProgress)) {
            ModalBottomSheet(
                onDismissRequest = {
                    viewModel.hideDownloadProgress()
                    viewModel.cancelUpdate()
                }
            ) {
                AnimatedContent(
                    targetState = viewModel.isDownloading,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "UpdateContent"
                ) { isDownloading ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isDownloading) {
                            Text(
                                stringResource(R.string.title_downloading_offline_data),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                when (viewModel.downloadStage) {
                                    "verifying" -> stringResource(R.string.download_verifying)
                                    "installing" -> stringResource(R.string.download_installing)
                                    "downloading" -> stringResource(R.string.download_running, (viewModel.downloadProgress * 100).toInt())
                                    else -> stringResource(R.string.download_waiting)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (viewModel.downloadStage == "downloading") {
                                LinearWavyProgressIndicator(
                                    progress = { viewModel.downloadProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                            TextButton(onClick = viewModel::hideDownloadProgress) {
                                Text(stringResource(R.string.download_background))
                            }
                        } else {
                            Text(
                                stringResource(R.string.title_new_version_detected),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                stringResource(
                                    R.string.label_new_version,
                                    viewModel.showUpdateDialog?.latestVersion ?: ""
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val rowCount = viewModel.showUpdateDialog?.rowCount ?: 0
                            if (rowCount > 0) {
                                Text(
                                    stringResource(R.string.label_row_count, rowCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (viewModel.localRowCount > 0) {
                                Text(
                                    stringResource(
                                        R.string.label_local_row_count,
                                        viewModel.localRowCount
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelUpdate() },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.action_cancel)) }
                                Button(
                                    onClick = { viewModel.confirmUpdate() },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.action_download_update)) }
                            }
                        }
                    }
                }
            }
        }

        // 测试拦截 Bottom Sheet：输入与结果两态，用 AnimatedContent 切换
        if (viewModel.showTestDialog) {
            ModalBottomSheet(onDismissRequest = { viewModel.hideTestDialog() }) {
                AnimatedContent(
                    targetState = viewModel.testResult,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "TestContent"
                ) { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                            .imePadding()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (viewModel.testBusy || viewModel.testSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(stringResource(R.string.operation_processing))
                        }
                        viewModel.testError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (result == null) {
                            Text(
                                stringResource(R.string.title_test_intercept),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                stringResource(R.string.msg_input_phone_number),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = viewModel.testPhoneNumber,
                                enabled = !viewModel.testBusy,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                                onValueChange = { viewModel.updateTestPhoneNumber(it) },
                                label = { Text(stringResource(R.string.label_phone_number)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.hideTestDialog() },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.action_cancel)) }
                                Button(
                                    onClick = { viewModel.testBlock() },
                                    enabled = !viewModel.testBusy && viewModel.testPhoneNumber.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.action_check)) }
                            }
                        } else {
                            Text(
                                stringResource(R.string.title_test_intercept),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                viewModel.testResultPhone,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(stringResource(if (result.shouldBlock) R.string.test_rule_block else R.string.test_rule_allow))
                            Text(stringResource(R.string.test_rule_explanation), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.label_tag_info) + result.label.ifEmpty {
                                stringResource(
                                    R.string.label_none
                                )
                            })
                            if (result.communityStatus != CommunityStatus.UNKNOWN) {
                                Text(stringResource(R.string.community_query_snapshot,
                                    stringResource(R.string.community_status_prefixed, stringResource(result.communityStatus.textRes()))))
                            }
                            val failureSummary = result.resultType.queryFailureSummaryRes()
                            if (failureSummary != null) {
                                Text(stringResource(R.string.label_result_type) + stringResource(failureSummary))
                                Text(
                                    stringResource(result.resultType.queryFailureAdviceRes()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Text(stringResource(R.string.label_local_cost) + "${result.localCost}ms")
                            Text(stringResource(R.string.label_network_cost) + "${result.networkCost}ms")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.hideTestDialog() },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.action_close)) }
                                Button(
                                    onClick = { viewModel.saveTestResult() },
                                    enabled = !viewModel.testSaving,
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.action_record)) }
                            }
                        }
                    }
                }
            }
        }

        // 联网查询数据源设置 BottomSheet：有草稿时可编辑，无缓存时展示加载/失败与重试
        if (viewModel.showQuerySourceSheet) {
            val sourceState by viewModel.querySourceState.collectAsState()
            val draft = viewModel.querySourceDraft
            ModalBottomSheet(onDismissRequest = { viewModel.closeQuerySourceSettings() }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.title_query_sources),
                        style = MaterialTheme.typography.titleLarge
                    )
                    when {
                        draft.isNotEmpty() -> {
                            Text(
                                stringResource(R.string.msg_query_sources_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            draft.forEachIndexed { index, item ->
                                QuerySourceRow(
                                    item = item,
                                    quality = viewModel.querySourceQuality[item.id],
                                    canMoveUp = index > 0,
                                    canMoveDown = index < draft.lastIndex,
                                    onMoveUp = { viewModel.moveQuerySource(item.id, -1) },
                                    onMoveDown = { viewModel.moveQuerySource(item.id, 1) },
                                    onToggle = { viewModel.toggleQuerySource(item.id, it) },
                                )
                            }
                            TextButton(onClick = { viewModel.restoreDefaultQuerySources() }) {
                                Text(stringResource(R.string.action_restore_default_sources))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.closeQuerySourceSettings() },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.action_cancel)) }
                                Button(
                                    onClick = { viewModel.saveQuerySources() },
                                    enabled = draft.any { it.enabled && it.available },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.action_save)) }
                            }
                        }

                        sourceState.refreshing -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text(
                                    stringResource(R.string.msg_query_sources_loading),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        else -> {
                            Text(
                                stringResource(R.string.msg_query_sources_load_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.closeQuerySourceSettings() },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.action_cancel)) }
                                Button(
                                    onClick = { viewModel.retryQuerySourceRefresh() },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.action_retry)) }
                            }
                        }
                    }
                }
            }
        }
    }

    val selfHostedConfig = when (val state = selfHostedConnectionState) {
        is SelfHostedConnectionState.Ready -> state.config
        is SelfHostedConnectionState.Blocked -> state.config
        SelfHostedConnectionState.NotConfigured -> null
    }
    val selfHostedBackendFallback = stringResource(
        R.string.setting_query_backend_self_hosted_unavailable_summary
    )
    val onlineBackendName = when (val backend = queryBackendState) {
        is QueryBackendState.Ready -> when (backend.type) {
            QueryBackendType.OFFICIAL ->
                stringResource(R.string.setting_query_backend_official_summary)

            QueryBackendType.SELF_HOSTED -> selfHostedConfig?.baseUrl
                ?.let { it.toUri().host }
                ?: selfHostedBackendFallback
        }

        is QueryBackendState.Blocked -> selfHostedConfig?.baseUrl
            ?.let { it.toUri().host }
            ?: selfHostedBackendFallback
    }
    val onlineSummary = if (viewModel.noNetworkQuery) {
        stringResource(R.string.setting_category_online_disabled_summary)
    } else {
        stringResource(
            R.string.setting_category_online_backend_summary,
            onlineBackendName,
            viewModel.networkTimeout,
        )
    }
    val notFoundText = stringResource(R.string.msg_not_found)
    val checkingText = stringResource(R.string.msg_checking)
    val offlineSummary = when {
        viewModel.offlineDbVersion.isBlank() || viewModel.offlineDbVersion == notFoundText ->
            stringResource(R.string.setting_category_offline_not_initialized_summary)
        viewModel.offlineDbVersion == checkingText ->
            stringResource(R.string.setting_category_offline_checking_summary)
        else -> stringResource(
            R.string.setting_category_offline_version_summary,
            viewModel.offlineDbVersion,
        )
    }
    val overlaySummary = when {
        viewModel.noNetworkQuery ->
            stringResource(R.string.setting_category_overlay_network_disabled_summary)
        !viewModel.showLocationOverlay ->
            stringResource(R.string.setting_category_overlay_disabled_summary)
        !overlayPermissionGranted ->
            stringResource(R.string.setting_category_overlay_permission_required_summary)
        else -> stringResource(R.string.setting_category_overlay_enabled_summary)
    }
    val displayFeatures = buildList {
        if (viewModel.showLocalNumberLabels) add(stringResource(R.string.setting_local_number_labels))
        if (viewModel.callStateVibrationEnabled) add(stringResource(R.string.setting_call_state_vibration))
        if (viewModel.feedbackNotification && !viewModel.noNetworkQuery &&
            (queryBackendState as? QueryBackendState.Ready)?.type == QueryBackendType.OFFICIAL) {
            add(stringResource(R.string.setting_feedback_notification))
        }
        if (viewModel.showLocationOverlay) add(overlaySummary)
    }
    val displaySummary = if (displayFeatures.isEmpty()) stringResource(R.string.display_summary_none)
        else stringResource(R.string.display_summary, displayFeatures.joinToString(" · "))
    val appPermissions = PermissionUtils.allPermissions
    val requiredPermissions = appPermissions.filter { it.isCritical }
    val optionalPermissions = appPermissions.filterNot { it.isCritical }
    val missingRequiredPermissionCount = requiredPermissions.count { item ->
        permissionsState[item.permission] != true
    }
    val grantedOptionalPermissionCount = optionalPermissions.count { item ->
        permissionsState[item.permission] == true
    } + if (overlayPermissionGranted) 1 else 0
    val optionalPermissionCount = optionalPermissions.size + 1
    val requiredPermissionSummary = if (missingRequiredPermissionCount == 0) {
        stringResource(R.string.setting_permissions_required_complete)
    } else {
        stringResource(
            R.string.setting_permissions_required_missing,
            missingRequiredPermissionCount,
        )
    }
    val permissionsSummary = stringResource(
        R.string.setting_category_permissions_summary,
        requiredPermissionSummary,
        grantedOptionalPermissionCount,
        optionalPermissionCount,
    )

    saveableStateHolder.SaveableStateProvider(settingsCategory?.name ?: "settings-home") {
        if (settingsCategory == null) {
            val categoryGroups = listOf(
                listOf(
                    Triple(
                        SettingsCategory.CALL_RECOGNITION,
                        stringResource(
                            if (viewModel.notifyOnly) {
                                R.string.setting_category_call_notify_summary
                            } else {
                                R.string.setting_category_call_block_summary
                            }
                        ),
                        Icons.Default.NotificationsNone,
                    ),
                    Triple(
                        SettingsCategory.ONLINE_QUERY,
                        onlineSummary,
                        Icons.Default.Cloud,
                    ),
                    Triple(
                        SettingsCategory.OFFLINE_DATABASE,
                        offlineSummary,
                        Icons.Default.Update,
                    ),
                ),
                listOf(
                    Triple(
                        SettingsCategory.DISPLAY_ALERTS,
                        displaySummary,
                        Icons.Default.Visibility,
                    ),
                    Triple(
                        SettingsCategory.RECORDS_BACKUP,
                        stringResource(R.string.setting_category_records_summary),
                        Icons.AutoMirrored.Outlined.Assignment,
                    ),
                ),
                listOf(
                    Triple(
                        SettingsCategory.PERMISSIONS,
                        permissionsSummary,
                        Icons.Default.Security,
                    ),
                    Triple(
                        SettingsCategory.ABOUT,
                        stringResource(
                            R.string.setting_app_version_summary,
                            viewModel.versionName,
                            viewModel.versionCode,
                        ),
                        Icons.Default.Info,
                    ),
                ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                categoryGroups.forEach { group ->
                    SettingsGroup(
                        rows = group.map { (category, summary, icon) ->
                            SettingsRowItem(category.name) { modifier ->
                                SettingsItemRow(
                                    modifier = modifier,
                                    title = stringResource(category.titleResId),
                                    summary = summary,
                                    icon = icon,
                                    onClick = { onNavigateToCategory(category) },
                                )
                            }
                        },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                when (settingsCategory) {
                    SettingsCategory.CALL_RECOGNITION ->
                        InterceptBehaviorPreferences(
                            viewModel = viewModel,
                            backendState = queryBackendState,
                        )

                    SettingsCategory.ONLINE_QUERY ->
                        OnlineQueryPreferences(
                            viewModel = viewModel,
                            backendState = queryBackendState,
                            selfHostedConnectionState = selfHostedConnectionState,
                        )

                    SettingsCategory.OFFLINE_DATABASE ->
                        OfflineDatabasePreferences(
                            viewModel = viewModel,
                            onRequestNotificationPermission = {
                                notificationPermissionLauncher()
                            },
                        )

                    SettingsCategory.DISPLAY_ALERTS -> {
                        DisplayPreferences(
                            viewModel = viewModel,
                            backendState = queryBackendState,
                            feedbackPermissions = feedbackPermissions,
                            onRequestFeedbackPermissions = feedbackPermissionLauncher,
                            onRequestPhoneStatePermission = {
                                callStateVibrationPermissionLauncher()
                            },
                        )
                        LocationOverlayPreferences(viewModel)
                    }

                    SettingsCategory.RECORDS_BACKUP ->
                        BackupRestorePreferences(
                            viewModel = viewModel,
                            onNavigateToMyReports = onNavigateToMyReports,
                            onNavigateToLocalNumberLabels = onNavigateToLocalNumberLabels,
                            onRestore = {
                                restoreLauncher.launch(arrayOf("application/zip", "*/*"))
                            },
                        )

                    SettingsCategory.PERMISSIONS ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            PermissionsPreferences(
                                overlayPermissionGranted = overlayPermissionGranted,
                                permissionsState = permissionsState,
                                settingsRequired = permissionRequester.blocked,
                                onRequestOverlayPermission = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        "package:${context.packageName}".toUri()
                                    )
                                    overlayPermissionLauncher.launch(intent)
                                },
                                onRequestPermission = { permission ->
                                    launcher(arrayOf(permission))
                                },
                            )
                        }

                    SettingsCategory.ABOUT -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            AboutPreferences(viewModel, onNavigateToDeveloperSupport, engagementPromptViewModel)
                        }
                        if (viewModel.debugUnlocked) {
                            SettingsSection(title = stringResource(R.string.category_debug_mode)) {
                                DebugPreferences(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * source 设置行：source ID + 近 7 天质量统计 + 可用状态 + 上下移动按钮 + 启停开关。
 * 不可用 source 保留展示，但开关只允许从启用改为停用。
 */
@Composable
private fun QuerySourceRow(
    item: QuerySourceItem,
    quality: QuerySourceQuality?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.id, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.label_source_quality_stats,
                    quality?.phoneCount ?: 0,
                    quality?.negativeCount ?: 0
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!item.available) {
                Text(
                    stringResource(R.string.label_source_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.action_move_up)
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.action_move_down)
            )
        }
        Switch(
            checked = item.enabled,
            onCheckedChange = onToggle,
            enabled = item.available || item.enabled,
        )
    }
}

/**
 * 备份/恢复内容选择行：标签 + Checkbox
 */
@Composable
private fun BackupCheckboxRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
