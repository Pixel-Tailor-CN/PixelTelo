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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.dao.QuerySourceQuality
import vip.mystery0.pixel.telo.data.query.QueryBackendState
import vip.mystery0.pixel.telo.data.query.SelfHostedConnectionState
import vip.mystery0.pixel.telo.data.repository.QuerySourceItem
import vip.mystery0.pixel.telo.service.IncomingCallOverlay
import vip.mystery0.pixel.telo.ui.screen.settings.AboutPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.AppFeaturesPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.BackupRestorePreferences
import vip.mystery0.pixel.telo.ui.screen.settings.DebugPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.InterceptBehaviorPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.LocationOverlayPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.OnlineQueryPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.PermissionsPreferences
import vip.mystery0.pixel.telo.ui.screen.settings.SelfHostedQueryDialog
import vip.mystery0.pixel.telo.ui.util.PermissionUtils
import vip.mystery0.pixel.telo.ui.util.backupDateTimeFormatter
import vip.mystery0.pixel.telo.viewmodel.BackupRestoreState
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel
import vip.mystery0.pixel.telo.worker.OfflineDatabaseUpdateScheduler
import java.time.LocalDateTime

@Composable
fun SettingsScreen(
    viewModel: SettingViewModel,
    onNavigateToLocalNumberLabels: () -> Unit,
) {
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

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsState = permissionsState.toMutableMap().apply {
            result.forEach { (permission, isGranted) ->
                this[permission] = isGranted
            }
        }
    }

    val feedbackPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsState = permissionsState.toMutableMap().apply {
            result.forEach { (permission, isGranted) ->
                this[permission] = isGranted
            }
        }
        val allGranted = feedbackPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
        viewModel.updateFeedbackNotification(allGranted)
        if (!allGranted) {
            Toast.makeText(
                context,
                feedbackPermissionDeniedMessage,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        overlayPermissionGranted = Settings.canDrawOverlays(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionsState = permissionsState.toMutableMap().apply {
            this[Manifest.permission.POST_NOTIFICATIONS] = isGranted
        }
        if (isGranted) {
            viewModel.updateAutoCheckUpdate(true)
        } else {
            viewModel.updateAutoCheckUpdate(false)
            Toast.makeText(
                context,
                autoCheckPermissionDeniedMessage,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val callStateVibrationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionsState = permissionsState.toMutableMap().apply {
            this[Manifest.permission.READ_PHONE_STATE] = isGranted
        }
        viewModel.updateCallStateVibrationEnabled(isGranted)
        if (!isGranted) {
            Toast.makeText(
                context,
                callStateVibrationPermissionDeniedMessage,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 备份：通过系统文件保存对话框选择保存位置
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            val stream = context.contentResolver.openOutputStream(it)
            if (stream != null) viewModel.performBackupWithOptions(stream)
        }
    }

    // 恢复：通过系统文件选择对话框选择备份文件，先解析预览
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            if (stream != null) viewModel.parseBackupFile(stream)
        }
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
            viewModel.hideLocationOverlayAdjuster()
        }
    }

    val permissionOverlayDesc = stringResource(R.string.permission_overlay_desc)
    LaunchedEffect(
        viewModel.showLocationOverlayAdjuster,
        viewModel.locationOverlayStyle
    ) {
        if (viewModel.showLocationOverlayAdjuster) {
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
        if (viewModel.debugUnlocked) {
            Toast.makeText(context, msgDebugModeEnabled, Toast.LENGTH_SHORT).show()
        }
    }

    // Handling Toast for Sync Status
    if (viewModel.syncStatusMessage != null) {
        Toast.makeText(context, viewModel.syncStatusMessage, Toast.LENGTH_SHORT).show()
        viewModel.clearStatusMessage()
    }

    // 备份/恢复结果 Bottom Sheet
    val backupState = viewModel.backupRestoreState
    if (backupState is BackupRestoreState.Success) {
        ModalBottomSheet(onDismissRequest = { viewModel.dismissBackupRestoreResult() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
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
        ModalBottomSheet(onDismissRequest = { viewModel.dismissBackupRestoreResult() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
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
        ModalBottomSheet(onDismissRequest = { viewModel.closeBackupOptionsSheet() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
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
                        enabled = opts.includeBlockedCalls || opts.includeBlackList || opts.includeWhiteList,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_backup)) }
                }
            }
        }
    }

    // 恢复内容选择 Sheet
    val preview = viewModel.backupPreview
    if (preview != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.closeRestoreOptionsSheet() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.title_restore_select),
                    style = MaterialTheme.typography.titleLarge
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.closeRestoreOptionsSheet() },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = { viewModel.performRestoreWithOptions() },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_restore)) }
                }
            }
        }
    }

    // 更新离线数据 Bottom Sheet：确认与下载进度共用同一个 Sheet，用 AnimatedContent 切换
    if (viewModel.showUpdateDialog != null || viewModel.isDownloading) {
        ModalBottomSheet(
            onDismissRequest = { if (!viewModel.isDownloading) viewModel.cancelUpdate() }
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
                            "${(viewModel.downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearWavyProgressIndicator(
                            progress = { viewModel.downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
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
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.action_check)) }
                        }
                    } else {
                        Text(
                            stringResource(R.string.title_test_intercept),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            viewModel.testPhoneNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(stringResource(if (result.shouldBlock) R.string.label_should_block_yes else R.string.label_should_block_no))
                        Text(stringResource(R.string.label_tag_info) + result.label.ifEmpty {
                            stringResource(
                                R.string.label_none
                            )
                        })
                        Text(stringResource(R.string.label_result_type) + result.resultType.name)
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
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.action_record)) }
                        }
                    }
                }
            }
        }
    }

    val selfHostedDraft = viewModel.selfHostedDraft
    if (viewModel.showSelfHostedConfigDialog && selfHostedDraft != null) {
        val currentConfig = when (val state = selfHostedConnectionState) {
            is SelfHostedConnectionState.Ready -> state.config
            is SelfHostedConnectionState.Blocked -> state.config
            SelfHostedConnectionState.NotConfigured -> null
        }
        SelfHostedQueryDialog(
            initialDraft = selfHostedDraft,
            editing = viewModel.selfHostedConfigEditing,
            currentConfig = currentConfig,
            blockedReason = (queryBackendState as? QueryBackendState.Blocked)?.reason,
            validationInProgress = viewModel.selfHostedValidationInProgress,
            validationError = viewModel.selfHostedValidationError,
            onValidateAndEnable = viewModel::validateAndEnableSelfHosted,
            onRevalidate = viewModel::revalidateSelfHosted,
            onEdit = viewModel::editSelfHostedConfig,
            onUseOfficial = viewModel::useOfficialBackend,
            onDismiss = viewModel::closeSelfHostedConfig,
        )
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

    ProvidePreferenceLocals {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                PreferenceCategory(title = { Text(stringResource(R.string.category_app_features)) })
                AppFeaturesPreferences(
                    viewModel = viewModel,
                    onRequestNotificationPermission = {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    },
                    onRequestPhoneStatePermission = {
                        callStateVibrationPermissionLauncher.launch(
                            Manifest.permission.READ_PHONE_STATE
                        )
                    },
                    onNavigateToLocalNumberLabels = onNavigateToLocalNumberLabels,
                )

                PreferenceCategory(title = { Text(stringResource(R.string.category_permissions)) })
                PermissionsPreferences(
                    overlayPermissionGranted = overlayPermissionGranted,
                    permissionsState = permissionsState,
                    onRequestOverlayPermission = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri()
                        )
                        overlayPermissionLauncher.launch(intent)
                    },
                    onRequestPermission = { permission ->
                        launcher.launch(arrayOf(permission))
                    }
                )

                PreferenceCategory(title = { Text(stringResource(R.string.category_intercept_behavior)) })
                InterceptBehaviorPreferences(viewModel)

                PreferenceCategory(title = { Text(stringResource(R.string.category_online_query)) })
                OnlineQueryPreferences(
                    viewModel = viewModel,
                    backendState = queryBackendState,
                    selfHostedConnectionState = selfHostedConnectionState,
                    feedbackPermissions = feedbackPermissions,
                    onRequestFeedbackPermissions = feedbackPermissionLauncher::launch
                )

                PreferenceCategory(title = { Text(stringResource(R.string.category_location_overlay)) })
                LocationOverlayPreferences(viewModel)

                PreferenceCategory(title = { Text(stringResource(R.string.category_backup_restore)) })
                BackupRestorePreferences(
                    viewModel = viewModel,
                    onRestore = { restoreLauncher.launch(arrayOf("application/zip", "*/*")) }
                )

                PreferenceCategory(title = { Text(stringResource(R.string.category_about)) })
                AboutPreferences(viewModel)

                if (viewModel.debugUnlocked) {
                    PreferenceCategory(title = { Text(stringResource(R.string.category_debug_mode)) })
                    DebugPreferences(viewModel)
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
