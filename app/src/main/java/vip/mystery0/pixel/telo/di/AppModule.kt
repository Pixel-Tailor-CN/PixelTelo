package vip.mystery0.pixel.telo.di

import android.content.Context
import androidx.room.Room
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import vip.mystery0.pixel.telo.data.AppDatabase
import vip.mystery0.pixel.telo.data.MIGRATION_1_2
import vip.mystery0.pixel.telo.data.MIGRATION_2_3
import vip.mystery0.pixel.telo.data.MIGRATION_3_4
import vip.mystery0.pixel.telo.data.MIGRATION_4_5
import vip.mystery0.pixel.telo.data.MIGRATION_5_6
import vip.mystery0.pixel.telo.data.MIGRATION_6_7
import vip.mystery0.pixel.telo.data.MIGRATION_7_8
import vip.mystery0.pixel.telo.data.MIGRATION_8_9
import vip.mystery0.pixel.telo.data.remote.OfficialFeedbackApi
import vip.mystery0.pixel.telo.data.remote.QueryApi
import vip.mystery0.pixel.telo.data.remote.SyncApi
import vip.mystery0.pixel.telo.data.query.QueryBackendProvider
import vip.mystery0.pixel.telo.data.query.SelfHostedCredentialStore
import vip.mystery0.pixel.telo.data.query.SelfHostedQueryClientFactory
import vip.mystery0.pixel.telo.data.repository.BackupRepository
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.ContactRepository
import vip.mystery0.pixel.telo.data.repository.QueryRepository
import vip.mystery0.pixel.telo.data.repository.SelfHostedConfigRepository
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository
import vip.mystery0.pixel.telo.data.repository.SyncRepository
import vip.mystery0.pixel.telo.data.repository.UserListRepository
import vip.mystery0.pixel.telo.service.IncomingCallOverlay
import vip.mystery0.pixel.telo.smartspacer.SmartspacerInterceptRepository

private const val OFFICIAL_SYNC = "officialSync"
private const val OFFICIAL_QUERY = "officialQuery"
private const val OFFICIAL_FEEDBACK = "officialFeedback"
private const val OFFICIAL_BASE_URL = "https://pixeltelo.api.mystery0.vip/"

val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "app-database"
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9
            )
            .build()
    }

    single { get<AppDatabase>().blockedCallDao() }
    single { get<AppDatabase>().userListDao() }

    single { BlockedCallRepository() }
    single { ContactRepository(androidContext()) }
    single { UserListRepository(get()) }
    single { BackupRepository(get(), get()) }  // 第二个 get() 注入 UserListDao
    single { SpamNumberRepository() }
    single { SmartspacerInterceptRepository() }
    single { IncomingCallOverlay(androidContext(), get()) }

    single {
        Json { ignoreUnknownKeys = true }
    }

    single<OkHttpClient>(named(OFFICIAL_SYNC)) {
        OkHttpClient.Builder()
            .build()
    }

    single<Retrofit>(named(OFFICIAL_SYNC)) {
        Retrofit.Builder()
            .baseUrl(OFFICIAL_BASE_URL)
            .client(get(named(OFFICIAL_SYNC)))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single<SyncApi>(named(OFFICIAL_SYNC)) {
        get<Retrofit>(named(OFFICIAL_SYNC)).create(SyncApi::class.java)
    }

    single<OkHttpClient>(named(OFFICIAL_QUERY)) {
        OkHttpClient.Builder()
            .build()
    }

    single<Retrofit>(named(OFFICIAL_QUERY)) {
        Retrofit.Builder()
            .baseUrl(OFFICIAL_BASE_URL)
            .client(get(named(OFFICIAL_QUERY)))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single<QueryApi>(named(OFFICIAL_QUERY)) {
        get<Retrofit>(named(OFFICIAL_QUERY)).create(QueryApi::class.java)
    }
    single<OfficialFeedbackApi>(named(OFFICIAL_FEEDBACK)) {
        get<Retrofit>(named(OFFICIAL_QUERY)).create(OfficialFeedbackApi::class.java)
    }

    single { SelfHostedCredentialStore(androidContext()) }
    single { SelfHostedConfigRepository(androidContext(), get(), get()) }
    single { SelfHostedQueryClientFactory(get()) }
    single {
        QueryBackendProvider(
            officialQueryApi = get(named(OFFICIAL_QUERY)),
            configRepository = get(),
            clientFactory = get(),
        )
    }

    single {
        QueryRepository(
            backendProvider = get(),
            officialFeedbackApi = get(named(OFFICIAL_FEEDBACK)),
            preferences = get(),
        )
    }

    single {
        SyncRepository(
            context = androidContext(),
            syncApi = get(named(OFFICIAL_SYNC)),
            okHttpClient = get(named(OFFICIAL_SYNC)),
        )
    }

    single { androidContext().getSharedPreferences("pixel_telo", Context.MODE_PRIVATE) }
}
