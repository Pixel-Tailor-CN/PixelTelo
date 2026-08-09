package vip.mystery0.pixel.telo

import android.app.Application
import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import vip.mystery0.pixel.telo.di.appModule
import vip.mystery0.pixel.telo.receiver.CallStateVibrationController
import vip.mystery0.pixel.telo.worker.OfflineDatabaseUpdateScheduler

class PixelTeloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PixelTeloApplication)
            modules(appModule)
        }
        val prefs = getSharedPreferences("pixel_telo", Context.MODE_PRIVATE)
        CallStateVibrationController.normalizePreference(this, prefs)
        OfflineDatabaseUpdateScheduler.ensureScheduled(this, prefs)
    }
}
