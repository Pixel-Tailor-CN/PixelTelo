package vip.mystery0.pixel.telo.ui.screen

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsPhone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.ui.components.SwipeToDeleteContainer
import vip.mystery0.pixel.telo.ui.components.WarningCard
import vip.mystery0.pixel.telo.ui.util.PermissionUtils
import vip.mystery0.pixel.telo.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val blockedCalls by viewModel.blockedCalls.collectAsState()
    val isDatabaseReady by viewModel.isDatabaseReady.collectAsState()
    val missingPermissions by viewModel.missingPermissions.collectAsState()
    val isDefaultApp by viewModel.isDefaultApp.collectAsState()

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
            onDelete = { call ->
                itemToDelete = call
                showDeleteDialog = true
            },
        )
    }

    if (showDeleteDialog && itemToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                itemToDelete = null
            },
            title = { Text("删除记录") },
            text = { Text("确定要删除这条拦截记录吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToDelete?.let { viewModel.delete(it) }
                        showDeleteDialog = false
                        itemToDelete = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        itemToDelete = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun DefaultAppWarningCard(onClick: () -> Unit) {
    WarningCard(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        icon = Icons.Default.SettingsPhone,
        iconColor = MaterialTheme.colorScheme.tertiary,
        title = "未设置为默认应用",
        message = "Pixel Telo 需要成为默认的“来电显示与骚扰拦截应用”才能生效。",
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Text("设为默认")
        }
    }
}

@Composable
fun PermissionWarningCard(onClick: () -> Unit) {
    WarningCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        icon = Icons.Default.Warning,
        iconColor = MaterialTheme.colorScheme.error,
        title = "缺少必要权限",
        message = "为了正常拦截骚扰电话，请授予相关权限。",
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text("去授权")
        }
    }
}

@Composable
fun DatabaseWarningCard(onClick: () -> Unit) {
    WarningCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        icon = Icons.Default.Warning,
        iconColor = MaterialTheme.colorScheme.error,
        title = "离线数据库缺失",
        message = "为了实现最佳拦截效果，请下载离线数据库。",
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text("去下载")
        }
    }
}

private fun LazyListScope.blockedCallsList(
    calls: List<BlockedCall>,
    onDelete: (BlockedCall) -> Unit,
) {
    if (calls.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            ) {
                Text(
                    "暂无拦截记录",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    } else {
        items(calls, key = { it.id }) { call ->
            SwipeToDeleteContainer(
                onDelete = { onDelete(call) },
                contentVerticalPadding = 4.dp,
            ) {
                BlockedCallItem(call)
            }
        }
    }
}

@Composable
fun BlockedCallItem(call: BlockedCall) {
    Card(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Number + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = call.phoneNumber,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = SimpleDateFormat(
                        "MM-dd HH:mm",
                        Locale.getDefault()
                    ).format(Date(call.blockTime)),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Row 2: Result Type
            val resultText = when (call.resultType) {
                ResultType.INTERCEPT -> "拦截原因: 骚扰电话"
                ResultType.PASS_BUT_NOTIFY -> "拦截原因: 提示(未拦截)"
                ResultType.NETWORK_TIMEOUT -> "拦截原因: 联网查询超时(已放行)"
            }
            val resultColor = if (call.resultType == ResultType.NETWORK_TIMEOUT) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
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
                    text = "备注: ${call.remark}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Row 4: Duration
            val durationText = buildString {
                append("处理耗时: 本地 ${call.localDuration}ms")
                if (call.networkDuration > 0) {
                    append(" | 网络 ${call.networkDuration}ms")
                }
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