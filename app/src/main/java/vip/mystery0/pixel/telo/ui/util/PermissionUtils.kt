package vip.mystery0.pixel.telo.ui.util

import android.Manifest
import android.os.Build

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
                ),
                PermissionItem(
                    Manifest.permission.READ_PHONE_STATE,
                    R.string.permission_read_phone_state_name,
                    R.string.permission_read_phone_state_desc,
                    false
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list += PermissionItem(
                    Manifest.permission.POST_NOTIFICATIONS,
                    R.string.permission_post_notifications_name,
                    R.string.permission_post_notifications_desc,
                    false
                )
            }
            return list
        }
}
