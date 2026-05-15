package vip.mystery0.pixel.telo.ui.util

import android.Manifest

import vip.mystery0.pixel.telo.R

data class PermissionItem(
    val permission: String,
    val nameResId: Int,
    val descriptionResId: Int,
    val isCritical: Boolean = true
)

object PermissionUtils {
    val allPermissions: List<PermissionItem>
        get() {
            val list = mutableListOf(
                PermissionItem(
                    Manifest.permission.READ_CONTACTS,
                    R.string.permission_read_contacts_name,
                    R.string.permission_read_contacts_desc,
                    true
                ),
                PermissionItem(
                    Manifest.permission.WRITE_CONTACTS,
                    R.string.permission_write_contacts_name,
                    R.string.permission_write_contacts_desc,
                    true
                ),
                PermissionItem(
                    Manifest.permission.READ_CALL_LOG,
                    R.string.permission_read_call_log_name,
                    R.string.permission_read_call_log_desc,
                    true
                )
            )
            return list
        }
}
