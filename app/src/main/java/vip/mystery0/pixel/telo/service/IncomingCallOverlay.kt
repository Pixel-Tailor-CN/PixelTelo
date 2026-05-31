package vip.mystery0.pixel.telo.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.repository.CheckResult

class IncomingCallOverlay(private val context: Context) {
    companion object {
        private const val TAG = "IncomingCallOverlay"
        private const val DISPLAY_DURATION_MS = 6_000L
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    @Volatile
    private var currentView: View? = null

    fun show(phoneNumber: String, result: CheckResult) {
        if (!Settings.canDrawOverlays(appContext) || !result.locationLookupAttempted) return

        val locationText = IncomingCallOverlayFormatter.formatLocation(
            result.locationInfo,
            result.resultType == ResultType.NETWORK_TIMEOUT
        )
        mainHandler.post {
            removeCurrentView()
            val view = createView(phoneNumber, locationText)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(56)
            }

            runCatching {
                windowManager.addView(view, params)
                currentView = view
                mainHandler.postDelayed({ removeCurrentView() }, DISPLAY_DURATION_MS)
            }.onFailure {
                android.util.Log.w(TAG, "Failed to show incoming call overlay", it)
            }
        }
    }

    private fun createView(phoneNumber: String, locationText: String): View {
        return LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setBackgroundColor(Color.argb(235, 31, 31, 31))

            addView(TextView(appContext).apply {
                text = phoneNumber
                setTextColor(Color.WHITE)
                textSize = 18f
                setSingleLine(true)
            })
            addView(TextView(appContext).apply {
                text = locationText
                setTextColor(Color.argb(230, 255, 255, 255))
                textSize = 15f
                setSingleLine(true)
            })
        }
    }

    private fun removeCurrentView() {
        val view = currentView ?: return
        runCatching {
            windowManager.removeView(view)
        }.onFailure {
            android.util.Log.w(TAG, "Failed to remove incoming call overlay", it)
        }
        currentView = null
    }

    private fun dp(value: Int): Int {
        return (value * appContext.resources.displayMetrics.density).toInt()
    }
}
