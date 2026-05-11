package vip.mystery0.pixel.telo.di

import android.content.Context
import androidx.room.Room
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import vip.mystery0.pixel.telo.data.AppDatabase
import vip.mystery0.pixel.telo.data.MIGRATION_1_2
import vip.mystery0.pixel.telo.data.MIGRATION_2_3
import vip.mystery0.pixel.telo.data.MIGRATION_3_4
import vip.mystery0.pixel.telo.data.remote.SyncApi
import vip.mystery0.pixel.telo.data.repository.BackupRepository
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository
import vip.mystery0.pixel.telo.data.repository.SyncRepository
import vip.mystery0.pixel.telo.data.repository.UserListRepository

val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "app-database"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    single { get<AppDatabase>().blockedCallDao() }
    single { get<AppDatabase>().userListDao() }

    single { BlockedCallRepository() }
    single { UserListRepository(get()) }
    single { BackupRepository(get(), get()) }  // 第二个 get() 注入 UserListDao
    single { SpamNumberRepository() }

    single {
        OkHttpClient.Builder()
            .build()
    }

    single {
        val json = Json { ignoreUnknownKeys = true }
        Retrofit.Builder()
            .baseUrl("https://pixeltelo.api.mystery0.vip/")
            .client(get())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SyncApi::class.java)
    }

    single { SyncRepository(androidContext(), get(), get()) }

    single { androidContext().getSharedPreferences("pixel_telo", Context.MODE_PRIVATE) }
}
