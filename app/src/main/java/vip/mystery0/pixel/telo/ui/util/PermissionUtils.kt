package vip.mystery0.pixel.telo.ui.util

import android.Manifest
import android.os.Build

data class PermissionItem(
    val permission: String,
    val name: String,
    val description: String,
    val isCritical: Boolean = true
)

object PermissionUtils {
    val allPermissions: List<PermissionItem>
        get() {
            val list = mutableListOf(
                PermissionItem(
                    Manifest.permission.READ_CALL_LOG,
                    "通话记录",
                    "用于识别和拦截来电",
                    true
                ),
                PermissionItem(
                    Manifest.permission.READ_CONTACTS,
                    "联系人",
                    "用于在来电界面显示信息",
                    true
                ),
                PermissionItem(
                    Manifest.permission.READ_PHONE_STATE,
                    "电话状态",
                    "用于检测来电状态",
                    true
                ),
                PermissionItem(
                    Manifest.permission.MANAGE_OWN_CALLS,
                    "管理通话",
                    "用于执行挂断操作",
                    true
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add(
                    PermissionItem(
                        Manifest.permission.POST_NOTIFICATIONS,
                        "通知",
                        "显示数据库更新进度",
                        false
                    )
                )
            }
            return list
        }
}
