package vip.mystery0.pixel.telo.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

@Composable
fun SettingsScreen(viewModel: SettingViewModel) {
    val context = LocalContext.current

    // Handling Toast for Sync Status
    if (viewModel.syncStatusMessage != null) {
        Toast.makeText(context, viewModel.syncStatusMessage, Toast.LENGTH_SHORT).show()
        viewModel.clearStatusMessage()
    }

    // Update Confirmation Dialog
    if (viewModel.showUpdateDialog != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelUpdate() },
            title = { Text("检测到离线数据存在新版本") },
            text = { Text("新版本: ${viewModel.showUpdateDialog?.latestVersion}\n下载更新吗？") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmUpdate() }) {
                    Text("下载更新")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelUpdate() }) {
                    Text("取消")
                }
            }
        )
    }

    if (viewModel.showTestDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideTestDialog() },
            title = { Text("测试拦截") },
            text = {
                Column {
                    Text("输入需要检查的电话号码")
                    OutlinedTextField(
                        value = viewModel.testPhoneNumber,
                        onValueChange = { viewModel.updateTestPhoneNumber(it) },
                        label = { Text("电话号码") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.testBlock() }) {
                    Text("检查")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideTestDialog() }) {
                    Text("取消")
                }
            }
        )
    }

    ProvidePreferenceLocals {
        Column(modifier = Modifier.fillMaxSize()) {
            Preference(
                title = { Text("更新离线数据") },
                summary = { Text("当前版本: ${viewModel.offlineDbVersion}") },
                icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                onClick = {
                    viewModel.checkUpdate()
                }
            )

            Preference(
                title = { Text("测试拦截") },
                summary = { Text("输入号码模拟拦截检查") },
                icon = { Icon(Icons.Default.PhonelinkSetup, contentDescription = null) },
                onClick = { viewModel.showTestDialog() }
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

            PreferenceCategory(title = { Text("关于") })
            Preference(
                title = { Text("版本名称") },
                summary = { Text(viewModel.versionName) },
                icon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
            Preference(
                title = { Text("版本号") },
                summary = { Text(viewModel.versionCode.toString()) },
                icon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
        }
    }
}
