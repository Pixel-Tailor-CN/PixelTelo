package vip.mystery0.pixel.telo.ui.screen

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import vip.mystery0.pixel.telo.ui.util.PermissionUtils
import vip.mystery0.pixel.telo.viewmodel.BackupRestoreState
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

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
            if (stream != null) viewModel.performBackup(stream)
        }
    }

    // 恢复：通过系统文件选择对话框选择备份文件
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            if (stream != null) viewModel.performRestore(stream)
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
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("操作成功", style = MaterialTheme.typography.titleLarge)
                Text(
                    backupState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { viewModel.dismissBackupRestoreResult() },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) { Text("确定") }
            }
        }
    } else if (backupState is BackupRestoreState.Failure) {
        ModalBottomSheet(onDismissRequest = { viewModel.dismissBackupRestoreResult() }) {
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("操作失败", style = MaterialTheme.typography.titleLarge)
                Text(
                    backupState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { viewModel.dismissBackupRestoreResult() },
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                ) { Text("确定") }
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
                        Text("正在下载离线数据", style = MaterialTheme.typography.titleLarge)
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
                        Text("检测到新版本", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "新版本: ${viewModel.showUpdateDialog?.latestVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.cancelUpdate() },
                                modifier = Modifier.weight(1f)
                            ) { Text("取消") }
                            Button(
                                onClick = { viewModel.confirmUpdate() },
                                modifier = Modifier.weight(1f)
                            ) { Text("下载更新") }
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
                        Text("测试拦截", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "输入需要检查的电话号码",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = viewModel.testPhoneNumber,
                            onValueChange = { viewModel.updateTestPhoneNumber(it) },
                            label = { Text("电话号码") },
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
                            ) { Text("取消") }
                            Button(
                                onClick = { viewModel.testBlock() },
                                modifier = Modifier.weight(1f)
                            ) { Text("检查") }
                        }
                    } else {
                        Text("测试拦截", style = MaterialTheme.typography.titleLarge)
                        Text(
                            viewModel.testPhoneNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("是否拦截: ${if (result.shouldBlock) "是" else "否"}")
                        Text("标签信息: ${result.label.ifEmpty { "无" }}")
                        Text("结果类型: ${result.resultType}")
                        Text("本地耗时: ${result.localCost}ms")
                        Text("网络耗时: ${result.networkCost}ms")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.hideTestDialog() },
                                modifier = Modifier.weight(1f)
                            ) { Text("关闭") }
                            Button(
                                onClick = { viewModel.saveTestResult() },
                                modifier = Modifier.weight(1f)
                            ) { Text("记录") }
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
            PreferenceCategory(title = { Text("应用功能") })
            Preference(
                title = { Text("更新离线数据") },
                summary = { Text("当前版本: ${viewModel.offlineDbVersion}") },
                icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                onClick = {
                    viewModel.checkUpdate()
                }
            )

            SwitchPreference(
                value = viewModel.notifyOnly,
                onValueChange = { viewModel.updateNotifyOnly(it) },
                title = { Text("仅提示不拦截") },
                summary = { Text("识别到骚扰电话时仅记录，不挂断") },
                icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) }
            )

            Preference(
                title = { Text("测试拦截") },
                summary = { Text("输入号码模拟拦截检查") },
                icon = { Icon(Icons.Default.PhonelinkSetup, contentDescription = null) },
                onClick = { viewModel.showTestDialog() }
            )

            Preference(
                title = { Text("备份拦截记录") },
                summary = { Text("将拦截记录导出为 ZIP 文件") },
                icon = { Icon(Icons.Default.Save, contentDescription = null) },
                onClick = {
                    val date =
                        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                            .format(java.util.Date())
                    backupLauncher.launch("pixeltelo_backup_$date.zip")
                }
            )

            Preference(
                title = { Text("恢复拦截记录") },
                summary = { Text("从备份文件恢复拦截记录") },
                icon = { Icon(Icons.Default.Restore, contentDescription = null) },
                onClick = { restoreLauncher.launch(arrayOf("application/zip", "*/*")) }
            )

            PreferenceCategory(title = { Text("权限申请") })
            PermissionUtils.allPermissions.forEach { item ->
                val isGranted = permissionsState[item.permission] == true
                Preference(
                    title = { Text(item.name) },
                    summary = { Text(item.description) },
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

            PreferenceCategory(title = { Text("关于") })
            Preference(
                title = { Text("版本名称") },
                summary = { Text(viewModel.versionName) },
                icon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
            Preference(
                title = { Text("版本号") },
                summary = { Text(viewModel.versionCode.toString()) },
                icon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) }
            )

            PreferenceCategory(title = { Text("调试模式") })
            SwitchPreference(
                value = viewModel.forceDownload,
                onValueChange = { viewModel.forceDownload = it },
                title = { Text("始终下载离线数据库") },
                summary = { Text("忽略版本检查，强制下载最新库") },
                icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) }
            )
            Preference(
                title = { Text("删除离线数据库") },
                summary = { Text("删除已下载的本地数据库文件") },
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                onClick = { viewModel.deleteDatabase() }
            )
        }
    }
}
