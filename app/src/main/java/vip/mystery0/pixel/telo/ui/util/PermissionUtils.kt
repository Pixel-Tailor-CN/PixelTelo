package vip.mystery0.pixel.telo.ui.util

import android.Manifest

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
                    Manifest.permission.READ_CONTACTS,
                    "联系人",
                    "用于在来电界面显示信息",
                    true
                )
            )
            return list
        }
}
