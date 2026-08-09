package vip.mystery0.pixel.telo.data.repository

import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.PhoneNumberNormalizer
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.query.BackendQueryResponse
import vip.mystery0.pixel.telo.data.query.QueryBackendProvider
import vip.mystery0.pixel.telo.data.query.QueryBackendSnapshot
import vip.mystery0.pixel.telo.data.remote.PhoneLocationInfo
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel
import kotlin.time.Duration.Companion.milliseconds

data class CheckResult(
    val shouldBlock: Boolean,
    val label: String,
    val resultType: ResultType,
    val localCost: Long,
    val networkCost: Long,
    val locationInfo: PhoneLocationInfo? = null,
    val locationLookupAttempted: Boolean = false,
    /** true 表示用户规则要求强制拦截，不受“仅提示”等全局策略影响。 */
    val forceBlock: Boolean = false,
    val querySource: String? = null,
    /** 联网结果所属的稳定 Backend ID；纯本地结果或联网失败时为 null。 */
    val queryBackendId: String? = null,
    val feedbackToken: String? = null,
)

class SpamNumberRepository : KoinComponent {
    companion object {
        private const val TAG = "SpamNumberRepository"
        private const val QUERY_REUSE_TTL_MS = 60_000L
        private const val QUERY_REUSE_MAX_ENTRIES = 32
    }

    private val syncRepository: SyncRepository by inject()
    private val queryRepository: QueryRepository by inject()
    private val queryBackendProvider: QueryBackendProvider by inject()
    private val prefs: SharedPreferences by inject()
    private val userListRepository: UserListRepository by inject()
    private val queryReuseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queryReuseMutex = Mutex()
    private val completedNetworkQueries = LinkedHashMap<QueryReuseKey, CachedNetworkAttempt>(
        QUERY_REUSE_MAX_ENTRIES,
        0.75f,
        true,
    )
    private val inFlightNetworkQueries = mutableMapOf<QueryReuseKey, Deferred<NetworkAttempt>>()

    /**
     * 仅发起联网查询，跳过本地数据库检查。
     * 用于手动重试联网查询超时的记录。超时限制使用用户设置。
     */
    suspend fun queryNetwork(phoneNumber: String): BackendQueryResponse {
        val phone = PhoneNumberNormalizer.normalizeForLookup(phoneNumber)
        return withContext(Dispatchers.IO) {
            withTimeout(networkTimeoutMs().milliseconds) {
                queryRepository.queryNumber(phone)
            }
        }
    }

    suspend fun checkSpam(phoneNumber: String, forceNetworkQuery: Boolean = false): CheckResult {
        val start = System.currentTimeMillis()
        val phone = PhoneNumberNormalizer.normalizeForLookup(phoneNumber)
        var localCost: Long
        var networkCost: Long

        val whiteMatch = userListRepository.findWhiteListMatch(phoneNumber)
        if (whiteMatch != null) {
            Log.i(TAG, "White list hit")
            return CheckResult(false, whiteMatch.remark ?: "", ResultType.WHITE_LIST, 0, 0)
        }

        val blackMatch = userListRepository.findBlackListMatch(phoneNumber)
        if (blackMatch != null) {
            Log.i(TAG, "Black list hit")
            return CheckResult(
                shouldBlock = true,
                label = blackMatch.remark ?: "",
                resultType = ResultType.BLACK_LIST,
                localCost = 0,
                networkCost = 0,
                forceBlock = blackMatch.forceBlock
            )
        }

        val db = syncRepository.getDb()
        if (db != null) {
            val spamNumber = withContext(Dispatchers.IO) {
                try {
                    db.spamNumberDao().search(phone)
                } catch (_: Exception) {
                    Log.w(TAG, "Local lookup failed")
                    null
                } finally {
                    db.close()
                }
            }
            localCost = System.currentTimeMillis() - start
            if (spamNumber != null) {
                val tagBlackMatch = userListRepository.findBlackListTagMatch(spamNumber.tag)
                if (tagBlackMatch != null) {
                    Log.i(TAG, "Black list tag hit")
                    return CheckResult(
                        shouldBlock = true,
                        label = spamNumber.tag,
                        resultType = ResultType.BLACK_LIST,
                        localCost = localCost,
                        networkCost = 0,
                        forceBlock = tagBlackMatch.forceBlock
                    )
                }

                val tagWhiteMatch = userListRepository.findWhiteListTagMatch(spamNumber.tag)
                if (tagWhiteMatch != null) {
                    Log.i(TAG, "White list tag hit")
                    return CheckResult(false, spamNumber.tag, ResultType.WHITE_LIST, localCost, 0)
                }
                Log.i(TAG, "Local hit: cost=${localCost}ms")
                return CheckResult(true, spamNumber.tag, ResultType.INTERCEPT, localCost, 0)
            }
        } else {
            localCost = System.currentTimeMillis() - start
        }

        if (localCost > 100) {
            Log.w(TAG, "Local lookup too slow: ${localCost}ms")
        }

        val noNetworkQuery = prefs.getBoolean("no_network_query", false)
        if (noNetworkQuery && !forceNetworkQuery) {
            Log.i(TAG, "Offline query only enabled")
            return CheckResult(false, "", ResultType.PASS_BUT_NOTIFY, localCost, 0)
        }

        var networkAttempt: NetworkAttempt? = null
        return withContext(Dispatchers.IO) {
            try {
                val reusedAttempt = if (forceNetworkQuery) {
                    ReusedNetworkAttempt(
                        attempt = executeNetworkQuery(
                            phone = phone,
                            snapshot = null,
                            timeoutMs = networkTimeoutMs(),
                        ),
                        reused = false,
                    )
                } else {
                    queryNetworkForCheck(phone)
                }
                networkAttempt = reusedAttempt.attempt
                networkCost = reusedAttempt.attempt.costMs
                if (reusedAttempt.reused) {
                    Log.d(TAG, "Network query result reused: cost=${networkCost}ms")
                }
                val backendResponse = reusedAttempt.attempt.result.getOrThrow()
                Log.i(
                    TAG,
                    if (reusedAttempt.reused) {
                        "Network query result reused: cost=${networkCost}ms"
                    } else {
                        "Network query succeeded: cost=${networkCost}ms"
                    },
                )

                buildNetworkResult(backendResponse, localCost, networkCost)
            } catch (exception: TimeoutCancellationException) {
                networkCost = networkAttempt?.costMs ?: 0L
                Log.w(TAG, "Network query failed: category=timeout, cost=${networkCost}ms")
                CheckResult(
                    shouldBlock = false,
                    label = "Timeout/Error",
                    resultType = ResultType.NETWORK_TIMEOUT,
                    localCost = localCost,
                    networkCost = networkCost,
                    locationLookupAttempted = true
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                networkCost = networkAttempt?.costMs ?: 0L
                Log.w(
                    TAG,
                    "Network query failed: category=${networkFailureCategory(exception)}, " +
                        "cost=${networkCost}ms",
                )
                CheckResult(
                    shouldBlock = false,
                    label = "Timeout/Error",
                    resultType = ResultType.NETWORK_TIMEOUT,
                    localCost = localCost,
                    networkCost = networkCost,
                    locationLookupAttempted = true
                )
            }
        }
    }

    /**
     * 复用同一来电窗口内的联网结果，并合并尚未完成的相同请求。
     *
     * 缓存键绑定 Backend 激活代次与超时设置，避免 Backend 切换后误用旧结果。
     */
    private suspend fun queryNetworkForCheck(phone: String): ReusedNetworkAttempt {
        val timeoutMs = networkTimeoutMs()
        val snapshot = queryBackendProvider.snapshot()
            ?: return ReusedNetworkAttempt(
                attempt = NetworkAttempt(
                    result = Result.failure(BackendBlockedException()),
                    costMs = 0L,
                ),
                reused = false,
            )
        val key = currentReuseKey(phone, snapshot, timeoutMs)
        val now = SystemClock.elapsedRealtime()
        val lookup = queryReuseMutex.withLock {
            removeExpiredNetworkQueries(now)
            completedNetworkQueries[key]?.let { cached ->
                return@withLock ReuseLookup.Completed(cached.attempt)
            }
            inFlightNetworkQueries[key]?.let { deferred ->
                return@withLock ReuseLookup.InFlight(deferred, joined = true)
            }
            if (inFlightNetworkQueries.size >= QUERY_REUSE_MAX_ENTRIES) {
                return@withLock ReuseLookup.Direct(snapshot, timeoutMs)
            }

            val deferred = queryReuseScope.async {
                executeNetworkQuery(phone, snapshot, timeoutMs)
            }
            inFlightNetworkQueries[key] = deferred
            observeNetworkQuery(key, deferred)
            ReuseLookup.InFlight(deferred, joined = false)
        }

        return when (lookup) {
            is ReuseLookup.Completed -> ReusedNetworkAttempt(
                attempt = lookup.attempt,
                reused = true,
            )

            is ReuseLookup.InFlight -> {
                if (lookup.joined) {
                    Log.d(TAG, "Joining in-flight network query")
                }
                ReusedNetworkAttempt(
                    attempt = lookup.deferred.await(),
                    reused = lookup.joined,
                )
            }

            is ReuseLookup.Direct -> {
                Log.w(TAG, "Network query reuse capacity reached")
                ReusedNetworkAttempt(
                    attempt = executeNetworkQuery(phone, lookup.snapshot, lookup.timeoutMs),
                    reused = false,
                )
            }
        }
    }

    /** 独立观察共享请求，确保所有调用方取消后仍能清理进行中状态并发布缓存。 */
    private fun observeNetworkQuery(
        key: QueryReuseKey,
        deferred: Deferred<NetworkAttempt>,
    ) {
        queryReuseScope.launch {
            val attempt = try {
                deferred.await()
            } catch (_: CancellationException) {
                queryReuseMutex.withLock {
                    if (inFlightNetworkQueries[key] === deferred) {
                        inFlightNetworkQueries.remove(key)
                    }
                }
                return@launch
            }

            queryReuseMutex.withLock {
                if (inFlightNetworkQueries[key] !== deferred) return@withLock
                inFlightNetworkQueries.remove(key)
                completedNetworkQueries[key] = CachedNetworkAttempt(
                    attempt = attempt,
                    completedAtElapsedMs = SystemClock.elapsedRealtime(),
                )
                trimCompletedNetworkQueries()
            }
        }
    }

    private suspend fun executeNetworkQuery(
        phone: String,
        snapshot: QueryBackendSnapshot?,
        timeoutMs: Long,
    ): NetworkAttempt {
        val startedAt = SystemClock.elapsedRealtime()
        val result = try {
            Result.success(
                withTimeout(timeoutMs.milliseconds) {
                    if (snapshot == null) {
                        queryRepository.queryNumber(phone)
                    } else {
                        queryRepository.queryNumber(phone, snapshot)
                    }
                },
            )
        } catch (exception: TimeoutCancellationException) {
            Result.failure(exception)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
        return NetworkAttempt(
            result = result,
            costMs = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    private fun currentReuseKey(
        phone: String,
        snapshot: QueryBackendSnapshot?,
        timeoutMs: Long,
    ): QueryReuseKey {
        return QueryReuseKey(
            phone = phone,
            backendId = snapshot?.backendId,
            activationId = snapshot?.activationId,
            timeoutMs = timeoutMs,
        )
    }

    private fun removeExpiredNetworkQueries(now: Long) {
        val iterator = completedNetworkQueries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.completedAtElapsedMs >= QUERY_REUSE_TTL_MS) {
                iterator.remove()
            }
        }
    }

    private fun trimCompletedNetworkQueries() {
        while (completedNetworkQueries.size > QUERY_REUSE_MAX_ENTRIES) {
            val eldestKey = completedNetworkQueries.entries.firstOrNull()?.key ?: return
            completedNetworkQueries.remove(eldestKey)
        }
    }

    private suspend fun buildNetworkResult(
        backendResponse: BackendQueryResponse,
        localCost: Long,
        networkCost: Long,
    ): CheckResult {
        val response = backendResponse.response
        val backendId = backendResponse.backendId
        val locationWhiteMatch = userListRepository.findWhiteListLocationMatch(response.data)
        if (locationWhiteMatch != null) {
            val label = locationWhiteMatch.remark
                ?: locationRuleLabel(locationWhiteMatch.phoneNumber)
            Log.i(TAG, "White list location hit")
            return CheckResult(
                shouldBlock = false,
                label = label,
                resultType = ResultType.WHITE_LIST,
                localCost = localCost,
                networkCost = networkCost,
                locationInfo = response.data,
                locationLookupAttempted = true,
                querySource = response.source,
                queryBackendId = backendId,
                feedbackToken = response.feedbackToken,
            )
        }

        val locationBlackMatch = userListRepository.findBlackListLocationMatch(response.data)
        if (locationBlackMatch != null) {
            val label = locationBlackMatch.remark
                ?: locationRuleLabel(locationBlackMatch.phoneNumber)
            Log.i(TAG, "Black list location hit")
            return CheckResult(
                shouldBlock = true,
                label = label,
                resultType = ResultType.BLACK_LIST,
                localCost = localCost,
                networkCost = networkCost,
                locationInfo = response.data,
                locationLookupAttempted = true,
                forceBlock = locationBlackMatch.forceBlock,
                querySource = response.source,
                queryBackendId = backendId,
                feedbackToken = response.feedbackToken,
            )
        }

        val tagBlackMatch = if (response.tag.isNotBlank()) {
            userListRepository.findBlackListTagMatch(response.tag)
        } else {
            null
        }
        if (tagBlackMatch != null) {
            Log.i(TAG, "Black list tag hit")
            return CheckResult(
                shouldBlock = true,
                label = response.tag,
                resultType = ResultType.BLACK_LIST,
                localCost = localCost,
                networkCost = networkCost,
                locationInfo = response.data,
                locationLookupAttempted = true,
                forceBlock = tagBlackMatch.forceBlock,
                querySource = response.source,
                queryBackendId = backendId,
                feedbackToken = response.feedbackToken,
            )
        }

        if (response.isSpam) {
            val tagWhiteMatch = userListRepository.findWhiteListTagMatch(response.tag)
            if (tagWhiteMatch != null) {
                Log.i(TAG, "White list tag hit")
                return CheckResult(
                    shouldBlock = false,
                    label = response.tag,
                    resultType = ResultType.WHITE_LIST,
                    localCost = localCost,
                    networkCost = networkCost,
                    locationInfo = response.data,
                    locationLookupAttempted = true,
                    querySource = response.source,
                    queryBackendId = backendId,
                    feedbackToken = response.feedbackToken,
                )
            }
            return CheckResult(
                shouldBlock = true,
                label = response.tag,
                resultType = ResultType.INTERCEPT,
                localCost = localCost,
                networkCost = networkCost,
                locationInfo = response.data,
                locationLookupAttempted = true,
                querySource = response.source,
                queryBackendId = backendId,
                feedbackToken = response.feedbackToken,
            )
        }

        return CheckResult(
            shouldBlock = false,
            label = "",
            resultType = ResultType.PASS_BUT_NOTIFY,
            localCost = localCost,
            networkCost = networkCost,
            locationInfo = response.data,
            locationLookupAttempted = true,
            querySource = response.source,
            queryBackendId = backendId,
            feedbackToken = response.feedbackToken,
        )
    }

    private fun networkTimeoutMs(): Long {
        return prefs.getInt(
            SettingViewModel.KEY_NETWORK_TIMEOUT,
            SettingViewModel.DEFAULT_NETWORK_TIMEOUT_SECONDS
        ).coerceIn(
            SettingViewModel.MIN_NETWORK_TIMEOUT_SECONDS,
            SettingViewModel.MAX_NETWORK_TIMEOUT_SECONDS
        ) * 1000L
    }

    private fun locationRuleLabel(value: String): String {
        return "Location: $value"
    }

    /** 只返回稳定错误分类，禁止把 URL、Token、Header 或响应正文写入日志。 */
    private fun networkFailureCategory(exception: Exception): String = when (exception) {
        is BackendBlockedException -> "backend_blocked"
        is BackendQueryException -> "backend_request"
        is QueryApiException -> "server_response"
        is IOException -> "network"
        else -> "unexpected"
    }

    private data class QueryReuseKey(
        val phone: String,
        val backendId: String?,
        val activationId: Long?,
        val timeoutMs: Long,
    )

    private data class NetworkAttempt(
        val result: Result<BackendQueryResponse>,
        val costMs: Long,
    )

    private data class ReusedNetworkAttempt(
        val attempt: NetworkAttempt,
        val reused: Boolean,
    )

    private data class CachedNetworkAttempt(
        val attempt: NetworkAttempt,
        val completedAtElapsedMs: Long,
    )

    private sealed interface ReuseLookup {
        data class Completed(val attempt: NetworkAttempt) : ReuseLookup
        data class InFlight(
            val deferred: Deferred<NetworkAttempt>,
            val joined: Boolean,
        ) : ReuseLookup
        data class Direct(
            val snapshot: QueryBackendSnapshot?,
            val timeoutMs: Long,
        ) : ReuseLookup
    }
}
