package vip.mystery0.pixel.telo.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** 普通权限与永久拒绝共用恢复入口；只在用户点击时启动系统页面。 */
class PermissionRequester internal constructor(
    private val context: Context,
    private val activity: Activity?,
) {
    private val history = context.getSharedPreferences("permission_request_history", Context.MODE_PRIVATE)
    var blocked by mutableStateOf(emptySet<String>())
        private set
    internal var pending: Array<String> = emptyArray()
    internal var result: (Map<String, Boolean>) -> Unit = {}
    internal var launchPermissions: (Array<String>) -> Unit = {}
    internal var launchSettings: (Intent) -> Unit = {}

    fun refresh() {
        blocked = PermissionUtils.allPermissions.map { it.permission }.filter { permission ->
            !granted(permission) && history.getBoolean(permission, false) &&
                activity?.shouldShowRequestPermissionRationale(permission) == false
        }.toSet()
    }

    fun launch(permissions: Array<String>) {
        if (pending.isNotEmpty()) return
        refresh()
        pending = permissions
        try {
            if (permissions.any { it in blocked }) {
                launchSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri()))
            } else {
                launchPermissions(permissions)
            }
        } catch (_: Exception) {
            complete(false)
        }
    }

    internal fun complete(recordDenial: Boolean) {
        val status = pending.associateWith(::granted)
        if (recordDenial) {
            history.edit {
                status.forEach { (permission, allowed) -> putBoolean(permission, !allowed) }
            }
        }
        pending = emptyArray()
        refresh()
        if (status.isNotEmpty()) result(status)
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun rememberPermissionRequester(onResult: (Map<String, Boolean>) -> Unit): PermissionRequester {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context as Context?) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }
    val requester = rememberSaveable(context, saver = Saver<PermissionRequester, List<String>>(
        save = { it.pending.toList() },
        restore = { PermissionRequester(context, activity).apply { pending = it.toTypedArray() } },
    )) { PermissionRequester(context, activity) }
    requester.result = onResult
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requester.complete(true)
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        requester.complete(false)
    }
    requester.launchPermissions = { permissions.launch(it) }
    requester.launchSettings = { settings.launch(it) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { requester.refresh() }
    return requester
}
