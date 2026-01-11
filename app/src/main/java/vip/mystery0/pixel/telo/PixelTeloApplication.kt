package vip.mystery0.pixel.telo

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import vip.mystery0.pixel.telo.di.appModule

class PixelTeloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PixelTeloApplication)
            modules(appModule)
        }
    }
}