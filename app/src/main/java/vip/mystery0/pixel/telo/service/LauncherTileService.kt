package vip.mystery0.pixel.telo.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import vip.mystery0.pixel.telo.MainActivity

/**
 * 快捷设置磁贴：不承载任何状态，仅作为启动器使用，点击后启动应用主界面。
 */
class LauncherTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        // 磁贴无状态语义，固定为激活样式，避免呈现为置灰的“未启用”外观
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14 起必须通过 PendingIntent 收起快捷设置面板并启动 Activity
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
