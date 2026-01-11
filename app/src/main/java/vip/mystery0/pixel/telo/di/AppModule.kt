package vip.mystery0.pixel.telo.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import vip.mystery0.pixel.telo.data.AppDatabase
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository

val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "pixel_telo_db"
        ).build()
    }

    single { get<AppDatabase>().blockedCallDao() }

    single { BlockedCallRepository(get()) }
}
