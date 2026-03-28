package vip.mystery0.pixel.telo.ui.screen

import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.ui.util.PermissionUtils
import vip.mystery0.pixel.telo.ui.util.backupDateTimeFormatter
import vip.mystery0.pixel.telo.viewmodel.BackupRestoreState
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel
import java.time.LocalDateTime

@Composable
fun SettingsScreen(viewModel: SettingViewModel) {
    val context = LocalContext.current
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
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        checkPermissions()
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

    ProvidePreferenceLocals {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            PreferenceCategory(title = { Text(stringResource(R.string.category_app_features)) })
            Preference(
                title = { Text(stringResource(R.string.setting_update_offline_data)) },
                summary = { Text(stringResource(R.string.summary_current_version) + viewModel.offlineDbVersion) },
                icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                onClick = {
                    viewModel.checkUpdate()
                }
            )

            Preference(
                title = { Text(stringResource(R.string.title_test_intercept)) },
                summary = { Text(stringResource(R.string.summary_test_intercept)) },
                icon = { Icon(Icons.Default.PhonelinkSetup, contentDescription = null) },
                onClick = { viewModel.showTestDialog() }
            )

            PreferenceCategory(title = { Text(stringResource(R.string.category_permissions)) })
            PermissionUtils.allPermissions.forEach { item ->
                val isGranted = permissionsState[item.permission] == true
                Preference(
                    title = { Text(stringResource(item.nameResId)) },
                    summary = { Text(stringResource(item.descriptionResId)) },
                    icon = {
                        Icon(
                            if (isGranted) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        if (!isGranted) {
                            launcher.launch(arrayOf(item.permission))
                        }
                    }
                )
            }

            PreferenceCategory(title = { Text(stringResource(R.string.category_intercept_behavior)) })

            var showTimeoutDialog by remember { mutableStateOf(false) }
            Preference(
                title = { Text(stringResource(R.string.setting_network_timeout)) },
                summary = {
                    Text(
                        stringResource(
                            R.string.setting_network_timeout_summary,
                            viewModel.networkTimeout
                        )
                    )
                },
                icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                onClick = { showTimeoutDialog = true }
            )

            if (showTimeoutDialog) {
                var sliderValue by remember { androidx.compose.runtime.mutableFloatStateOf(viewModel.networkTimeout.toFloat()) }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showTimeoutDialog = false },
                    title = { Text(stringResource(R.string.setting_network_timeout)) },
                    text = {
                        Column {
                            Text(
                                stringResource(
                                    R.string.setting_network_timeout_summary,
                                    sliderValue.toInt()
                                )
                            )
                            androidx.compose.material3.Slider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                valueRange = 1f..30f,
                                steps = 28
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.updateNetworkTimeout(sliderValue.toInt())
                            showTimeoutDialog = false
                        }) { Text(stringResource(R.string.action_confirm)) }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showTimeoutDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            SwitchPreference(
                value = viewModel.notifyOnly,
                onValueChange = { viewModel.updateNotifyOnly(it) },
                title = { Text(stringResource(R.string.setting_notify_only)) },
                summary = { Text(stringResource(R.string.setting_notify_only_summary)) },
                icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) }
            )

            SwitchPreference(
                value = viewModel.noNetworkQuery,
                onValueChange = { viewModel.updateNoNetworkQuery(it) },
                title = { Text(stringResource(R.string.setting_no_network_query)) },
                summary = { Text(stringResource(R.string.setting_no_network_query_summary)) },
                icon = { Icon(Icons.Default.CloudOff, contentDescription = null) }
            )

            SwitchPreference(
                value = viewModel.alwaysRecord,
                onValueChange = { viewModel.updateAlwaysRecord(it) },
                title = { Text(stringResource(R.string.setting_always_record)) },
                summary = { Text(stringResource(R.string.setting_always_record_summary)) },
                icon = { Icon(Icons.Default.FindInPage, contentDescription = null) }
            )

            PreferenceCategory(title = { Text(stringResource(R.string.category_backup_restore)) })
            Preference(
                title = { Text(stringResource(R.string.setting_backup_records)) },
                summary = { Text(stringResource(R.string.setting_backup_records_summary)) },
                icon = { Icon(Icons.Default.Save, contentDescription = null) },
                onClick = { viewModel.openBackupOptionsSheet() }
            )

            Preference(
                title = { Text(stringResource(R.string.setting_restore_records)) },
                summary = { Text(stringResource(R.string.setting_restore_records_summary)) },
                icon = { Icon(Icons.Default.Restore, contentDescription = null) },
                onClick = { restoreLauncher.launch(arrayOf("application/zip", "*/*")) }
            )

            PreferenceCategory(title = { Text(stringResource(R.string.category_about)) })
            Preference(
                title = { Text(stringResource(R.string.setting_version_name)) },
                summary = { Text(viewModel.versionName) },
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                onClick = { viewModel.onVersionClick() }
            )
            Preference(
                title = { Text(stringResource(R.string.setting_version_code)) },
                summary = { Text(viewModel.versionCode.toString()) },
                icon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) }
            )
            Preference(
                title = { Text(stringResource(R.string.setting_feedback)) },
                summary = { Text(stringResource(R.string.setting_feedback_summary)) },
                icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/PixelTelo/AppRelease/issues/new".toUri()
                    )
                    context.startActivity(intent)
                }
            )

            if (viewModel.debugUnlocked) {
                PreferenceCategory(title = { Text(stringResource(R.string.category_debug_mode)) })
                SwitchPreference(
                    value = viewModel.forceDownload,
                    onValueChange = { viewModel.forceDownload = it },
                    title = { Text(stringResource(R.string.setting_force_download)) },
                    summary = { Text(stringResource(R.string.setting_force_download_summary)) },
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) }
                )
                Preference(
                    title = { Text(stringResource(R.string.setting_delete_database)) },
                    summary = { Text(stringResource(R.string.setting_delete_database_summary)) },
                    icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                    onClick = { viewModel.deleteDatabase() }
                )
            }
        }
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
