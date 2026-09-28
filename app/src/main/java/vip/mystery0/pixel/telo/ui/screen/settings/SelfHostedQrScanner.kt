package vip.mystery0.pixel.telo.ui.screen.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.CameraPreview
import vip.mystery0.pixel.telo.R

/** 内嵌预览只识别 QR；暂停/离开即停机，不向 Activity result 传配对材料。 */
@Composable
internal fun SelfHostedQrScanner(
    onDecoded: (String) -> Unit,
    onFailure: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val permissionHistory = remember {
        context.getSharedPreferences("pixel_telo", Context.MODE_PRIVATE)
    }
    var requested by remember {
        mutableStateOf(permissionHistory.getBoolean(CAMERA_PERMISSION_REQUESTED, false))
    }
    var denied by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(false) }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val activity = generateSequence(context as Context?) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>().firstOrNull()
    val permanentlyDenied = denied && requested &&
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        denied = !granted
    }
    fun refreshPermission() {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && requested) denied = true
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermission()
        }
        owner.lifecycle.addObserver(observer)
        refreshPermission()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler(onBack = onCancel)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.msg_pairing_scan_hint))
        when {
            !hasCamera -> Text(stringResource(R.string.error_pairing_no_camera))
            !granted -> {
                Text(stringResource(if (denied) R.string.error_pairing_permission
                    else R.string.msg_pairing_permission))
                if (permanentlyDenied) {
                    Text(stringResource(R.string.msg_pairing_permission_settings))
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_pairing_app_settings))
                    }
                } else {
                    OutlinedButton(onClick = {
                        requested = true
                        permissionHistory.edit()
                            .putBoolean(CAMERA_PERMISSION_REQUESTED, true)
                            .apply()
                        launcher.launch(Manifest.permission.CAMERA)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_pairing_permission))
                    }
                }
            }
            else -> EmbeddedQrPreview(onDecoded, onFailure)
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
}

@Composable
private fun EmbeddedQrPreview(onDecoded: (String) -> Unit, onFailure: (Int) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember(context) {
        DecoratedBarcodeView(context).apply {
            barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            statusView.text = ""
        }
    }
    DisposableEffect(view, owner) {
        var active = false
        var consumed = false
        val callback = object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                if (!active || consumed) return
                val value = result?.text ?: return
                consumed = true
                active = false
                view.pause()
                onDecoded(value)
            }
            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) = Unit
        }
        val listener = object : CameraPreview.StateListener {
            override fun previewSized() = Unit
            override fun previewStarted() = Unit
            override fun previewStopped() = Unit
            override fun cameraClosed() = Unit
            override fun cameraError(error: Exception?) {
                if (!active) return
                active = false
                view.pause()
                onFailure(R.string.error_pairing_camera)
            }
        }
        view.barcodeView.addStateListener(listener)
        fun resumeIfAllowed() {
            if (consumed || active || owner.lifecycle.currentState < Lifecycle.State.RESUMED) return
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED) {
                onFailure(R.string.error_pairing_permission)
                return
            }
            try {
                active = true
                view.decodeSingle(callback)
                view.resume()
            } catch (_: Exception) {
                active = false
                view.pause()
                onFailure(R.string.error_pairing_camera)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumeIfAllowed()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    active = false
                    view.pause()
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        resumeIfAllowed()
        onDispose {
            active = false
            owner.lifecycle.removeObserver(observer)
            view.pauseAndWait()
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(300.dp))
}

private const val CAMERA_PERMISSION_REQUESTED = "self_hosted_camera_permission_requested"
