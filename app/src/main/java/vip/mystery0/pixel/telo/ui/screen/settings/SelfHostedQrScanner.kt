package vip.mystery0.pixel.telo.ui.screen.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import vip.mystery0.pixel.telo.R

/** 内嵌预览只识别 QR；暂停、离页或切到后台时停止相机。 */
@Composable
internal fun SelfHostedQrScanner(
    modifier: Modifier = Modifier,
    onDecoded: (String) -> Unit,
    onFailure: (Int) -> Unit,
    onRetryCamera: () -> Unit,
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
    var deniedPermanently by remember { mutableStateOf(false) }
    var retryingPriorDenial by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<Int?>(null) }
    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    val activity = generateSequence(context as Context?) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        denied = !granted
        deniedPermanently = !granted && retryingPriorDenial &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
    }

    fun refreshPermission() {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            denied = false
            deniedPermanently = false
        } else if (requested) {
            denied = true
        }
    }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermission()
        }
        owner.lifecycle.addObserver(observer)
        refreshPermission()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.msg_pairing_scan_hint))
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !hasCamera -> CameraStatus(
                    message = stringResource(R.string.error_pairing_no_camera),
                )
                !granted -> CameraStatus(
                    message = stringResource(
                        if (denied) R.string.error_pairing_permission
                        else R.string.msg_pairing_permission,
                    ),
                    supportingMessage = if (deniedPermanently) {
                        stringResource(R.string.msg_pairing_permission_settings)
                    } else {
                        null
                    },
                    actionLabel = stringResource(
                        if (deniedPermanently) R.string.action_pairing_app_settings
                        else R.string.action_pairing_permission,
                    ),
                    onAction = {
                        if (deniedPermanently) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    "package:${context.packageName}".toUri(),
                                ),
                            )
                        } else {
                            retryingPriorDenial = denied || requested
                            requested = true
                            permissionHistory.edit { putBoolean(CAMERA_PERMISSION_REQUESTED, true) }
                            launcher.launch(Manifest.permission.CAMERA)
                        }
                    },
                )
                else -> cameraError?.let { error ->
                    CameraStatus(
                        message = stringResource(error),
                        actionLabel = stringResource(R.string.action_pairing_retry_scan),
                        onAction = onRetryCamera,
                    )
                } ?: EmbeddedQrPreview(
                    modifier = Modifier.fillMaxSize(),
                    onDecoded = onDecoded,
                    onFailure = { error ->
                        if (error == R.string.error_pairing_camera) {
                            cameraError = error
                        } else {
                            onFailure(error)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CameraStatus(
    message: String,
    supportingMessage: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(message, textAlign = TextAlign.Center)
        supportingMessage?.let { Text(it, textAlign = TextAlign.Center) }
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun EmbeddedQrPreview(
    modifier: Modifier = Modifier,
    onDecoded: (String) -> Unit,
    onFailure: (Int) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember(context) {
        DecoratedBarcodeView(context).apply {
            barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            statusView.text = ""
            viewFinder.setLaserVisibility(false)
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
                PackageManager.PERMISSION_GRANTED
            ) {
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
    AndroidView(factory = { view }, modifier = modifier.fillMaxSize())
}

private const val CAMERA_PERMISSION_REQUESTED = "self_hosted_camera_permission_requested"
