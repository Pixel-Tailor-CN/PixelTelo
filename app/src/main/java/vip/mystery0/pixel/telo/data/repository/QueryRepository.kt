package vip.mystery0.pixel.telo.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import vip.mystery0.pixel.telo.data.remote.FeedbackRequest
import vip.mystery0.pixel.telo.data.remote.QueryApi
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

/** v2 接口返回的 HTTP 错误，message 优先携带服务端响应体中的错误说明 */
class QueryApiException(val code: Int, message: String) : Exception(message)

@Serializable
private data class StoredSourceConfig(
    val initialized: Boolean = false,
    val orderedIds: List<String> = emptyList(),
    val enabledIds: List<String> = emptyList(),
    val defaultSources: List<String> = emptyList(),
    val availableIds: List<String> = emptyList(),
)

class QueryRepository(
    private val queryApi: QueryApi,
    private val preferences: SharedPreferences,
) {
    companion object {
        private const val SOURCE_CONFIG_KEY = "query_source_config"
    }

    private val configMutex = Mutex()
    private val refreshMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val _sourceState = MutableStateFlow(readInitialState())
    val sourceState: StateFlow<QuerySourceState> = _sourceState.asStateFlow()

    suspend fun refreshSources(): Result<Unit> = refreshMutex.withLock {
        refreshSourcesLocked()
    }

    private suspend fun refreshSourcesLocked(): Result<Unit> {
        updateState { it.copy(refreshing = true, refreshFailed = false) }

        return try {
            val response = queryApi.getSources()
            configMutex.withLock {
                val current = readStoredConfig()
                val refreshed = mergeSourceConfig(
                    current,
                    response.availableSources,
                    response.defaultSources
                )
                persistAndPublish(refreshed, refreshing = false, refreshFailed = false)
            }
            Result.success(Unit)
        } catch (exception: CancellationException) {
            withContext(NonCancellable) {
                updateState { it.copy(refreshing = false) }
            }
            throw exception
        } catch (exception: Exception) {
            updateState { it.copy(refreshing = false, refreshFailed = true) }
            Result.failure(exception)
        }
    }

    fun saveSourceSelection(items: List<QuerySourceItem>): Boolean = runBlocking {
        configMutex.withLock {
            if (items.none { it.enabled && it.available }) {
                return@withLock false
            }

            val current = readStoredConfig()
            val saved = StoredSourceConfig(
                initialized = current.initialized,
                orderedIds = items.map { it.id }.distinct(),
                enabledIds = items.filter { it.enabled }.map { it.id }.distinct(),
                defaultSources = current.defaultSources,
                availableIds = items.filter { it.available }.map { it.id }.distinct(),
            )
            persistAndPublish(saved)
            true
        }
    }

    suspend fun queryNumber(phone: String): QueryResponse {
        val sources = configMutex.withLock {
            val config = readStoredConfig()
            if (!config.initialized) {
                emptyList()
            } else {
                config.orderedIds.filter { it in config.enabledIds && it in config.availableIds }
            }
        }
        val response = try {
            queryApi.queryNumber(QueryRequest(phone, sources))
        } catch (exception: HttpException) {
            // 解析服务端错误响应体（{"error": "..."}），把可读的错误说明带给上层展示
            throw QueryApiException(
                exception.code(),
                serverErrorMessage(exception) ?: exception.message()
            )
        }
        updateInvalidSources(response)
        return response
    }

    suspend fun submitFeedback(token: String, positive: Boolean): FeedbackSubmitResult {
        return try {
            queryApi.submitFeedback(FeedbackRequest(token, positive))
            FeedbackSubmitResult.Accepted
        } catch (exception: HttpException) {
            when (exception.code()) {
                409 -> FeedbackSubmitResult.AlreadySubmitted
                410 -> FeedbackSubmitResult.Expired
                400 -> FeedbackSubmitResult.Invalid
                else -> FeedbackSubmitResult.RetryableFailure(
                    serverErrorMessage(exception) ?: exception.message()
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            FeedbackSubmitResult.RetryableFailure(exception.message)
        }
    }

    private fun readInitialState(): QuerySourceState = runBlocking {
        configMutex.withLock {
            readStoredConfig().toState()
        }
    }

    private suspend fun updateState(transform: (QuerySourceState) -> QuerySourceState) {
        configMutex.withLock {
            _sourceState.value = transform(_sourceState.value)
        }
    }

    private fun readStoredConfig(): StoredSourceConfig {
        val rawConfig = preferences.getString(SOURCE_CONFIG_KEY, null) ?: return StoredSourceConfig()
        return runCatching { json.decodeFromString<StoredSourceConfig>(rawConfig) }
            .getOrDefault(StoredSourceConfig())
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

    /** 从 HTTP 错误响应体中解析服务端错误说明，响应体缺失或格式不符时返回 null */
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

    private suspend fun updateInvalidSources(response: QueryResponse) {
        val invalidIds = response.warnings
            .flatMap { it.invalidSources }
            .toSet()
        if (invalidIds.isEmpty()) return

        configMutex.withLock {
            val current = readStoredConfig()
            val updated = current.copy(availableIds = current.availableIds.filterNot { it in invalidIds })
            persistAndPublish(updated)
        }
    }

    private fun persistAndPublish(
        config: StoredSourceConfig,
        refreshing: Boolean = _sourceState.value.refreshing,
        refreshFailed: Boolean = _sourceState.value.refreshFailed,
    ) {
        preferences.edit { putString(SOURCE_CONFIG_KEY, json.encodeToString(config)) }
        _sourceState.value = config.toState(refreshing, refreshFailed)
    }

    private fun StoredSourceConfig.toState(
        refreshing: Boolean = false,
        refreshFailed: Boolean = false,
    ): QuerySourceState {
        return QuerySourceState(
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
