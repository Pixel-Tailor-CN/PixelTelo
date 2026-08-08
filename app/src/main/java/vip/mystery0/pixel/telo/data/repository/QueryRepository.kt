package vip.mystery0.pixel.telo.data.repository

import android.content.SharedPreferences
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import vip.mystery0.pixel.telo.data.query.BackendQueryResponse
import vip.mystery0.pixel.telo.data.query.OFFICIAL_BACKEND_ID
import vip.mystery0.pixel.telo.data.query.QueryBackendProvider
import vip.mystery0.pixel.telo.data.query.QueryBackendSnapshot
import vip.mystery0.pixel.telo.data.query.QueryBackendState
import vip.mystery0.pixel.telo.data.query.QueryBackendType
import vip.mystery0.pixel.telo.data.remote.FeedbackRequest
import vip.mystery0.pixel.telo.data.remote.OfficialFeedbackApi
import vip.mystery0.pixel.telo.data.remote.QueryErrorResponse
import vip.mystery0.pixel.telo.data.remote.QueryRequest
import vip.mystery0.pixel.telo.data.remote.QueryResponse
import vip.mystery0.pixel.telo.data.remote.QuerySource

data class QuerySourceItem(
    val id: String,
    val enabled: Boolean,
    val available: Boolean,
)

data class QuerySourceState(
    /** 当前状态所属的 Backend；安全阻止状态下为 null。 */
    val backendId: String? = null,
    val initialized: Boolean = false,
    val items: List<QuerySourceItem> = emptyList(),
    val defaultSources: List<String> = emptyList(),
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
) {
    val unavailableEnabledSources: List<String>
        get() = items.filter { it.enabled && !it.available }.map { it.id }
}

sealed interface FeedbackSubmitResult {
    data object Accepted : FeedbackSubmitResult
    data object AlreadySubmitted : FeedbackSubmitResult
    data object Expired : FeedbackSubmitResult
    data object Invalid : FeedbackSubmitResult
    data class RetryableFailure(val message: String?) : FeedbackSubmitResult
}

/** v2 接口返回的 HTTP 错误，message 优先携带官方服务响应体中的错误说明。 */
class QueryApiException(val code: Int, message: String) : Exception(message)

/** 当前选择的 Backend 没有可用 Snapshot，调用方应直接 Fail Open。 */
class BackendBlockedException : IOException("Query backend is unavailable")

/** 自建 Backend 查询失败的稳定脱敏异常。 */
class BackendQueryException : IOException("Query backend request failed")

@Serializable
private data class StoredSourceConfig(
    val initialized: Boolean = false,
    val orderedIds: List<String> = emptyList(),
    val enabledIds: List<String> = emptyList(),
    val defaultSources: List<String> = emptyList(),
    val availableIds: List<String> = emptyList(),
)

private class SourceConfigStorageException : IOException("Source configuration update failed")

class QueryRepository(
    private val backendProvider: QueryBackendProvider,
    private val officialFeedbackApi: OfficialFeedbackApi,
    private val preferences: SharedPreferences,
) {
    companion object {
        private const val LEGACY_SOURCE_CONFIG_KEY = "query_source_config"
        private const val SOURCE_CONFIGS_KEY = "query_source_configs"
    }

    private val configMutex = Mutex()
    private val refreshMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var ignoreUnverifiedSourceConfigs = false
    private val _sourceState = MutableStateFlow(readInitialState())
    val sourceState: StateFlow<QuerySourceState> = _sourceState.asStateFlow()

    init {
        repositoryScope.launch {
            backendProvider.state.collectLatest { state ->
                handleBackendState(state)
            }
        }
    }

    /** 使用调用开始时的一次 Backend Snapshot 刷新对应 source 配置。 */
    suspend fun refreshSources(expectedBackendId: String? = null): Result<Unit> {
        val snapshot = backendProvider.snapshot()
            ?: return Result.failure(BackendBlockedException())
        if (expectedBackendId != null && snapshot.backendId != expectedBackendId) {
            return Result.failure(BackendBlockedException())
        }
        return refreshMutex.withLock {
            refreshSourcesLocked(snapshot)
        }
    }

    private suspend fun refreshSourcesLocked(
        snapshot: QueryBackendSnapshot,
        publishRefreshing: Boolean = true,
    ): Result<Unit> {
        val backendId = snapshot.backendId
        if (publishRefreshing) {
            updateStateForBackend(backendId) {
                it.copy(refreshing = true, refreshFailed = false)
            }
        }

        return try {
            val response = snapshot.queryApi.getSources()
            configMutex.withLock {
                val current = readStoredConfig(backendId)
                val refreshed = mergeSourceConfig(
                    current,
                    response.availableSources,
                    response.defaultSources,
                )
                if (!persistAndPublish(backendId, refreshed, refreshing = false)) {
                    throw SourceConfigStorageException()
                }
            }
            Result.success(Unit)
        } catch (exception: CancellationException) {
            withContext(NonCancellable) {
                updateStateForBackend(backendId) { it.copy(refreshing = false) }
            }
            throw exception
        } catch (exception: Exception) {
            updateStateForBackend(backendId) {
                it.copy(refreshing = false, refreshFailed = true)
            }
            Result.failure(exception)
        }
    }

    /**
     * 保存指定 Backend 的 source 草稿。
     *
     * [backendId] 必须与调用开始时的 Snapshot 一致，避免切换瞬间把旧草稿写入新 Backend。
     */
    fun saveSourceSelection(items: List<QuerySourceItem>, backendId: String?): Boolean {
        val snapshot = backendProvider.snapshot() ?: return false
        if (backendId == null || snapshot.backendId != backendId) return false

        return runBlocking {
            configMutex.withLock {
                if (items.none { it.enabled && it.available }) {
                    return@withLock false
                }

                val current = readStoredConfig(backendId)
                val saved = StoredSourceConfig(
                    initialized = current.initialized,
                    orderedIds = items.map { it.id }.distinct(),
                    enabledIds = items.filter { it.enabled }.map { it.id }.distinct(),
                    defaultSources = current.defaultSources,
                    availableIds = items.filter { it.available }.map { it.id }.distinct(),
                )
                persistAndPublish(backendId, saved)
            }
        }
    }

    /** 使用一次 Snapshot 及其 Backend 专属 source 发起查询，不在来电链路刷新 source。 */
    suspend fun queryNumber(phone: String): BackendQueryResponse {
        val snapshot = backendProvider.snapshot() ?: throw BackendBlockedException()
        val sources = configMutex.withLock {
            val config = readStoredConfig(snapshot.backendId)
            if (!config.initialized) {
                emptyList()
            } else {
                config.orderedIds.filter { it in config.enabledIds && it in config.availableIds }
            }
        }
        val response = try {
            snapshot.queryApi.queryNumber(QueryRequest(phone, sources))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            if (snapshot.type == QueryBackendType.SELF_HOSTED) {
                throw QueryApiException(exception.code(), "Query backend request failed")
            }
            // 官方接口保留既有可读错误说明；自建响应正文绝不进入异常消息。
            throw QueryApiException(
                exception.code(),
                serverErrorMessage(exception) ?: exception.message(),
            )
        } catch (exception: Exception) {
            if (snapshot.type == QueryBackendType.SELF_HOSTED) {
                throw BackendQueryException()
            }
            throw exception
        }
        updateInvalidSources(snapshot.backendId, response)
        return BackendQueryResponse.from(snapshot, response)
    }

    /** 反馈凭据只由官方查询签发，因此始终使用独立的官方反馈 API。 */
    suspend fun submitFeedback(token: String, positive: Boolean): FeedbackSubmitResult {
        return try {
            officialFeedbackApi.submitFeedback(FeedbackRequest(token, positive))
            FeedbackSubmitResult.Accepted
        } catch (exception: HttpException) {
            when (exception.code()) {
                409 -> FeedbackSubmitResult.AlreadySubmitted
                410 -> FeedbackSubmitResult.Expired
                400 -> FeedbackSubmitResult.Invalid
                else -> FeedbackSubmitResult.RetryableFailure(
                    serverErrorMessage(exception) ?: exception.message(),
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            FeedbackSubmitResult.RetryableFailure(exception.message)
        }
    }

    /** Backend 切换先发布目标缓存或空加载态，再使用同一 Snapshot 异步刷新。 */
    private suspend fun handleBackendState(state: QueryBackendState) {
        when (state) {
            is QueryBackendState.Blocked -> {
                configMutex.withLock {
                    _sourceState.value = QuerySourceState()
                }
            }

            is QueryBackendState.Ready -> {
                val snapshot = backendProvider.snapshot()
                if (snapshot == null || snapshot.backendId != state.backendId) {
                    configMutex.withLock {
                        _sourceState.value = QuerySourceState()
                    }
                    return
                }
                val backendChanged = configMutex.withLock {
                    if (_sourceState.value.backendId == snapshot.backendId) {
                        false
                    } else {
                        _sourceState.value = readStoredConfig(snapshot.backendId).toState(
                            backendId = snapshot.backendId,
                            refreshing = true,
                        )
                        true
                    }
                }
                if (backendChanged) {
                    refreshMutex.withLock {
                        refreshSourcesLocked(snapshot, publishRefreshing = false)
                    }
                }
            }
        }
    }

    private fun readInitialState(): QuerySourceState {
        val snapshot = backendProvider.snapshot() ?: return QuerySourceState()
        return readStoredConfig(snapshot.backendId).toState(snapshot.backendId)
    }

    private suspend fun updateStateForBackend(
        backendId: String,
        transform: (QuerySourceState) -> QuerySourceState,
    ) {
        configMutex.withLock {
            if (isPublishedBackend(backendId)) {
                _sourceState.value = transform(_sourceState.value)
            }
        }
    }

    private fun readStoredConfig(backendId: String): StoredSourceConfig =
        readStoredConfigs()[backendId] ?: StoredSourceConfig()

    private fun readStoredConfigs(): Map<String, StoredSourceConfig> {
        if (ignoreUnverifiedSourceConfigs) {
            return uninitializedOfficialConfig()
        }
        val rawConfigs = preferences.getString(SOURCE_CONFIGS_KEY, null)
        if (rawConfigs != null) {
            return decodeStoredConfigs(rawConfigs) ?: emptyMap()
        }
        return migrateLegacySourceConfig()
    }

    /**
     * 旧单配置只迁移到官方 Backend。
     *
     * 新 Map 写入后必须重新从 SharedPreferences 读取并反序列化确认，成功后才删除旧 Key。
     */
    private fun migrateLegacySourceConfig(): Map<String, StoredSourceConfig> {
        val legacyRaw = preferences.getString(LEGACY_SOURCE_CONFIG_KEY, null) ?: return emptyMap()
        val legacyConfig = runCatching {
            json.decodeFromString<StoredSourceConfig>(legacyRaw)
        }.getOrNull() ?: return markLegacyMigrationFailed()
        val migrated = mapOf(OFFICIAL_BACKEND_ID to legacyConfig)
        val encoded = runCatching {
            json.encodeToString<Map<String, StoredSourceConfig>>(migrated)
        }.getOrNull() ?: return markLegacyMigrationFailed()
        val written = runCatching {
            preferences.edit().putString(SOURCE_CONFIGS_KEY, encoded).commit()
        }.getOrDefault(false)
        if (!written) {
            return markLegacyMigrationFailed()
        }

        val verified = preferences.getString(SOURCE_CONFIGS_KEY, null)
            ?.let(::decodeStoredConfigs)
        if (verified?.get(OFFICIAL_BACKEND_ID) != legacyConfig) {
            return markLegacyMigrationFailed()
        }

        ignoreUnverifiedSourceConfigs = false
        preferences.edit().remove(LEGACY_SOURCE_CONFIG_KEY).commit()
        return verified
    }

    /**
     * 迁移失败时立即回滚新 Key 的进程内可见值，并在成功重写前强制读取未初始化官方配置。
     *
     * `SharedPreferences.commit()` 即使返回 false 也可能已经更新内存 Map，因此不能只依赖返回值。
     */
    private fun markLegacyMigrationFailed(): Map<String, StoredSourceConfig> {
        ignoreUnverifiedSourceConfigs = true
        runCatching {
            preferences.edit().remove(SOURCE_CONFIGS_KEY).apply()
        }
        return uninitializedOfficialConfig()
    }

    private fun uninitializedOfficialConfig(): Map<String, StoredSourceConfig> =
        mapOf(OFFICIAL_BACKEND_ID to StoredSourceConfig())

    private fun decodeStoredConfigs(raw: String): Map<String, StoredSourceConfig>? =
        runCatching {
            json.decodeFromString<Map<String, StoredSourceConfig>>(raw)
        }.getOrNull()

    private fun writeStoredConfig(backendId: String, config: StoredSourceConfig): Boolean {
        val configs = readStoredConfigs().toMutableMap().apply {
            put(backendId, config)
        }
        val encoded = runCatching {
            json.encodeToString<Map<String, StoredSourceConfig>>(configs)
        }.getOrNull() ?: return false
        // 普通更新沿用异步落盘，避免 invalid source 回写拖慢来电查询链路。
        preferences.edit().putString(SOURCE_CONFIGS_KEY, encoded).apply()

        val verified = preferences.getString(SOURCE_CONFIGS_KEY, null)
            ?.let(::decodeStoredConfigs)
        val succeeded = verified?.get(backendId) == config
        if (succeeded) {
            ignoreUnverifiedSourceConfigs = false
        }
        return succeeded
    }

    private fun mergeSourceConfig(
        current: StoredSourceConfig,
        remoteSources: List<QuerySource>,
        remoteDefaults: List<String>,
    ): StoredSourceConfig {
        val remoteIds = remoteSources
            .sortedBy { it.priority }
            .map { it.id }
            .distinct()
        val defaultSources = remoteDefaults.filter { it in remoteIds }.distinct()

        if (!current.initialized) {
            val orderedIds = (defaultSources + remoteIds.filterNot { it in defaultSources }).distinct()
            return StoredSourceConfig(
                initialized = true,
                orderedIds = orderedIds,
                enabledIds = defaultSources,
                defaultSources = defaultSources,
                availableIds = remoteIds,
            )
        }

        val orderedIds = (current.orderedIds + remoteIds.filterNot { it in current.orderedIds }).distinct()
        return current.copy(
            orderedIds = orderedIds,
            defaultSources = defaultSources,
            availableIds = remoteIds,
        )
    }

    /** 从官方 HTTP 错误响应体中解析错误说明，响应体缺失或格式不符时返回 null。 */
    private fun serverErrorMessage(exception: HttpException): String? {
        return runCatching {
            val body = exception.response()?.errorBody()?.string()
            if (body.isNullOrBlank()) {
                null
            } else {
                json.decodeFromString<QueryErrorResponse>(body).error.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private suspend fun updateInvalidSources(backendId: String, response: QueryResponse) {
        val invalidIds = response.warnings
            .flatMap { it.invalidSources }
            .toSet()
        if (invalidIds.isEmpty()) return

        configMutex.withLock {
            val current = readStoredConfig(backendId)
            val updated = current.copy(
                availableIds = current.availableIds.filterNot { it in invalidIds },
            )
            persistAndPublish(backendId, updated)
        }
    }

    private fun persistAndPublish(
        backendId: String,
        config: StoredSourceConfig,
        refreshing: Boolean = _sourceState.value.refreshing,
        refreshFailed: Boolean = _sourceState.value.refreshFailed,
    ): Boolean {
        if (!writeStoredConfig(backendId, config)) return false
        if (isPublishedBackend(backendId)) {
            _sourceState.value = config.toState(backendId, refreshing, refreshFailed)
        }
        return true
    }

    private fun isPublishedBackend(backendId: String): Boolean =
        _sourceState.value.backendId == backendId &&
            backendProvider.snapshot()?.backendId == backendId

    private fun StoredSourceConfig.toState(
        backendId: String,
        refreshing: Boolean = false,
        refreshFailed: Boolean = false,
    ): QuerySourceState {
        return QuerySourceState(
            backendId = backendId,
            initialized = initialized,
            items = orderedIds.map { id ->
                QuerySourceItem(
                    id = id,
                    enabled = id in enabledIds,
                    available = id in availableIds,
                )
            },
            defaultSources = defaultSources,
            refreshing = refreshing,
            refreshFailed = refreshFailed,
        )
    }
}
