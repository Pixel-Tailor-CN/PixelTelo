package vip.mystery0.pixel.telo.service

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.repository.CheckResult
import vip.mystery0.pixel.telo.ui.theme.PixelTeloTheme
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel
import kotlin.math.roundToInt

class IncomingCallOverlay(
    private val context: Context,
    private val prefs: SharedPreferences
) {
    companion object {
        private const val TAG = "IncomingCallOverlay"
        private const val DISPLAY_DURATION_MS = 6_000L
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    @Volatile
    private var currentView: View? = null
    private var currentParams: WindowManager.LayoutParams? = null
    private var currentLifecycleOwner: OverlayLifecycleOwner? = null

    fun show(phoneNumber: String, result: CheckResult) {
        if (!Settings.canDrawOverlays(appContext) || !result.locationLookupAttempted) return

        val locationText = IncomingCallOverlayFormatter.formatLocation(
            result.locationInfo,
            result.resultType == ResultType.NETWORK_TIMEOUT
        )
        val offsetDp = prefs.getInt(
            SettingViewModel.KEY_LOCATION_OVERLAY_OFFSET_DP,
            SettingViewModel.DEFAULT_LOCATION_OVERLAY_OFFSET_DP
        )
        showOverlay(
            phoneNumber = phoneNumber,
            locationText = locationText,
            offsetDp = offsetDp,
            draggable = false,
            autoDismiss = true,
            onOffsetChanged = {}
        )
    }

    fun showPreview(offsetDp: Int, onOffsetChanged: (Int) -> Unit): Boolean {
        if (!Settings.canDrawOverlays(appContext)) return false

        showOverlay(
            phoneNumber = appContext.getString(R.string.location_overlay_preview_phone),
            locationText = appContext.getString(R.string.location_overlay_preview_location),
            offsetDp = offsetDp,
            draggable = true,
            autoDismiss = false,
            onOffsetChanged = onOffsetChanged
        )
        return true
    }

    fun hide() {
        mainHandler.post { removeCurrentView() }
    }

    private fun showOverlay(
        phoneNumber: String,
        locationText: String,
        offsetDp: Int,
        draggable: Boolean,
        autoDismiss: Boolean,
        onOffsetChanged: (Int) -> Unit
    ) {
        mainHandler.post {
            removeCurrentView()
            val params = createLayoutParams(offsetDp)
            val view = createView(
                phoneNumber = phoneNumber,
                locationText = locationText,
                draggable = draggable,
                onDragDelta = { deltaY ->
                    val targetView = currentView ?: return@createView
                    val targetParams = currentParams ?: return@createView
                    val maxY = (appContext.resources.displayMetrics.heightPixels - targetView.height)
                        .coerceAtLeast(0)
                    val nextY = (targetParams.y + deltaY.roundToInt()).coerceIn(0, maxY)
                    targetParams.y = nextY
                    runCatching {
                        windowManager.updateViewLayout(targetView, targetParams)
                        onOffsetChanged(pxToDp(nextY))
                    }.onFailure {
                        Log.w(TAG, "Failed to update incoming call overlay", it)
                    }
                }
            )

            runCatching {
                val lifecycleOwner = OverlayLifecycleOwner().apply {
                    restore()
                    moveToResumed()
                }
                view.setViewTreeLifecycleOwner(lifecycleOwner)
                view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                windowManager.addView(view, params)
                currentView = view
                currentParams = params
                currentLifecycleOwner = lifecycleOwner
                if (autoDismiss) {
                    mainHandler.postDelayed({ removeCurrentView() }, DISPLAY_DURATION_MS)
                }
            }.onFailure {
                Log.w(TAG, "Failed to show incoming call overlay", it)
            }
        }
    }

    private fun createLayoutParams(offsetDp: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(offsetDp)
        }
    }

    private fun createView(
        phoneNumber: String,
        locationText: String,
        draggable: Boolean,
        onDragDelta: (Float) -> Unit
    ): View {
        return ComposeView(appContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                PixelTeloTheme {
                    val dragModifier = if (draggable) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.y)
                            }
                        }
                    } else {
                        Modifier
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.86f))
                            .then(dragModifier)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = phoneNumber,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = locationText,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    private fun removeCurrentView() {
        val view = currentView ?: return
        runCatching {
            windowManager.removeView(view)
        }.onFailure {
            Log.w(TAG, "Failed to remove incoming call overlay", it)
        }
        currentView = null
        currentParams = null
        currentLifecycleOwner?.moveToDestroyed()
        currentLifecycleOwner = null
    }

    private fun dp(value: Int): Int {
        return (value * appContext.resources.displayMetrics.density).toInt()
    }

    private fun pxToDp(value: Int): Int {
        return (value / appContext.resources.displayMetrics.density).roundToInt()
    }

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun restore() {
            savedStateController.performRestore(null)
        }

        fun moveToResumed() {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun moveToDestroyed() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
