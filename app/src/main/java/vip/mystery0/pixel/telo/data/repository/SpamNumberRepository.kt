package vip.mystery0.pixel.telo.data.repository

import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.PhoneNumberNormalizer
import vip.mystery0.pixel.telo.data.PhoneNumberRuleMatcher
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.model.CommunityStatus
import vip.mystery0.pixel.telo.data.preferences.CommunityReportPreferences
import vip.mystery0.pixel.telo.data.query.CommunityQueryEpoch
import vip.mystery0.pixel.telo.data.query.OFFICIAL_BACKEND_ID
import vip.mystery0.pixel.telo.data.query.decodeCommunityQueryMetadata
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
    /** 离线身份资料仅提供名称，Directory 展示不依赖是否拦截。 */
    val isIdentityProfile: Boolean = false,
    val communityStatus: CommunityStatus = CommunityStatus.UNKNOWN,
    val communityBlockCandidate: Boolean = false,
    val feedbackPhone: String? = null,
    val feedbackTokenExpiresAt: Long? = null,
    val communityEpoch: Long = -1,
    val communityReceivedAtElapsedMs: Long = -1,
)

class SpamNumberRepository : KoinComponent {
    companion object {
        private const val TAG = "SpamNumberRepository"
        private const val HUB_IDENTITY_SOURCE = "pixel_text_hub"
        private const val LOCAL_LOOKUP_TIMEOUT_MS = 3_000L
        private const val QUERY_REUSE_TTL_MS = 60_000L
        private const val QUERY_REUSE_MAX_ENTRIES = 32
    }

    private val syncRepository: SyncRepository by inject()
    private val queryRepository: QueryRepository by inject()
    private val queryBackendProvider: QueryBackendProvider by inject()
    private val prefs: SharedPreferences by inject()
    private val communityPreferences: CommunityReportPreferences by inject()
    private val communityEpoch: CommunityQueryEpoch by inject()
    private val userListRepository: UserListRepository by inject()
    private val contactRepository: ContactRepository by inject()
    // 独立等待可让调用方按时返回；最多四个本地任务，避免非协作 Binder/SQLite 占满线程。
    private val localLookupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(4))
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
        // 历史记录重试也必须复用统一国际号码语义，并在任何标准化或 Backend 访问前拦截。
        if (PhoneNumberRuleMatcher.isExplicitInternational(phoneNumber)) {
            Log.i(TAG, "Explicit international retry skipped")
            throw BackendBlockedException()
        }
        val phone = PhoneNumberNormalizer.normalizeForLookup(phoneNumber)
        return withContext(Dispatchers.IO) {
            withTimeout(networkTimeoutMs().milliseconds) {
                queryRepository.queryNumber(phone)
            }
        }
    }

    suspend fun checkSpam(phoneNumber: String, forceNetworkQuery: Boolean = false): CheckResult {
        val startedAt = SystemClock.elapsedRealtime()
        val phone = PhoneNumberNormalizer.normalizeForLookup(phoneNumber)
        val localAttempt = try {
            runLocalStage(LOCAL_LOOKUP_TIMEOUT_MS - (SystemClock.elapsedRealtime() - startedAt)) {
                checkLocalSpam(phoneNumber, phone)
            }
        } finally {
            val cost = SystemClock.elapsedRealtime() - startedAt
            if (cost > 100L) Log.w(TAG, "Local lookup too slow: ${cost}ms")
        }
        val localCost = SystemClock.elapsedRealtime() - startedAt
        if (localAttempt == null) {
            Log.w(TAG, "Local lookup timed out: cost=${localCost}ms")
            return CheckResult(false, "", ResultType.LOCAL_TIMEOUT, localCost, 0)
        }
        localAttempt.value?.let { return it.copy(localCost = localCost) }

        val noNetworkQuery = prefs.getBoolean("no_network_query", false)
        if (noNetworkQuery && !forceNetworkQuery) {
            Log.i(TAG, "Offline query only enabled")
            return CheckResult(false, "", ResultType.PASS_BUT_NOTIFY, localCost, 0)
        }

        var networkCost: Long
        var networkAttempt: NetworkAttempt? = null
        return withContext(Dispatchers.IO) {
            val backendResponse = try {
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
                reusedAttempt.attempt.result.getOrThrow().also {
                    Log.i(
                        TAG,
                        if (reusedAttempt.reused) {
                            "Network query result reused: cost=${networkCost}ms"
                        } else {
                            "Network query succeeded: cost=${networkCost}ms"
                        },
                    )
                }
            } catch (exception: TimeoutCancellationException) {
                networkCost = networkAttempt?.costMs ?: 0L
                return@withContext buildNetworkFailureResult(
                    exception = exception,
                    localCost = localCost,
                    networkCost = networkCost,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                networkCost = networkAttempt?.costMs ?: 0L
                return@withContext buildNetworkFailureResult(
                    exception = exception,
                    localCost = localCost,
                    networkCost = networkCost,
                )
            }

            // 联网后的归属地/标签规则仍是本地工作，共用剩余本地预算，不能无限等待。
            val postStartedAt = SystemClock.elapsedRealtime()
            val completed = try {
                runLocalStage(LOCAL_LOOKUP_TIMEOUT_MS - localCost) {
                    buildNetworkResult(backendResponse, localCost, networkCost)
                }
            } finally {
                val cost = SystemClock.elapsedRealtime() - postStartedAt
                if (localCost + cost > 100L) {
                    Log.w(TAG, "Local lookup too slow: ${localCost + cost}ms")
                }
            }
            val totalLocalCost = localCost + SystemClock.elapsedRealtime() - postStartedAt
            completed?.value?.copy(localCost = totalLocalCost)
                ?: CheckResult(false, "", ResultType.LOCAL_TIMEOUT, totalLocalCost, networkCost)
        }
    }

    /** 本地阶段只返回已确定的结果；null 表示门禁均通过，调用方才可考虑联网。 */
    private suspend fun checkLocalSpam(phoneNumber: String, phone: String): CheckResult? {
        // 通讯录、号码白名单、号码黑名单和国际门禁沿用既有顺序。
        if (contactRepository.isContact(phoneNumber)) {
            Log.i(TAG, "Contact found, allowing call")
            return CheckResult(false, "", ResultType.PASS, 0, 0)
        }
        currentCoroutineContext().ensureActive()
        val whiteMatch = userListRepository.findWhiteListMatch(phoneNumber)
        if (whiteMatch != null) {
            Log.i(TAG, "White list hit")
            return CheckResult(false, whiteMatch.remark.orEmpty(), ResultType.WHITE_LIST, 0, 0)
        }
        currentCoroutineContext().ensureActive()
        val blackMatch = userListRepository.findBlackListMatch(phoneNumber)
        if (blackMatch != null) {
            Log.i(TAG, "Black list hit")
            return CheckResult(true, blackMatch.remark.orEmpty(), ResultType.BLACK_LIST, 0, 0,
                forceBlock = blackMatch.forceBlock)
        }
        currentCoroutineContext().ensureActive()
        if (PhoneNumberRuleMatcher.isExplicitInternational(phoneNumber)) {
            Log.i(TAG, "Explicit international number skipped")
            return CheckResult(false, "", ResultType.PASS_BUT_NOTIFY, 0, 0)
        }
        val spamNumber = try {
            syncRepository.lookupSpamNumber(phone)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            Log.w(TAG, "Local lookup failed")
            null
        } ?: return null
        currentCoroutineContext().ensureActive()
        val tagBlackMatch = userListRepository.findBlackListTagMatch(spamNumber.tag)
        if (tagBlackMatch != null) {
            Log.i(TAG, "Black list tag hit")
            return CheckResult(true, spamNumber.tag, ResultType.BLACK_LIST, 0, 0,
                forceBlock = tagBlackMatch.forceBlock)
        }
        currentCoroutineContext().ensureActive()
        val tagWhiteMatch = userListRepository.findWhiteListTagMatch(spamNumber.tag)
        if (tagWhiteMatch != null) {
            Log.i(TAG, "White list tag hit")
            return CheckResult(false, spamNumber.tag, ResultType.WHITE_LIST, 0, 0)
        }
        Log.i(TAG, "Local hit")
        if (spamNumber.source == HUB_IDENTITY_SOURCE) {
            return CheckResult(false, spamNumber.tag, ResultType.PASS_BUT_NOTIFY, 0, 0,
                isIdentityProfile = true)
        }
        return CheckResult(true, spamNumber.tag, ResultType.INTERCEPT, 0, 0)
    }

    /** 超时只取消独立本地任务，不等待其同步调用结束，也不消费迟到结果。 */
    private suspend fun <T> runLocalStage(
        budgetMs: Long,
        block: suspend () -> T,
    ): LocalStageCompleted<T>? {
        if (budgetMs <= 0L) return null
        val startedAt = SystemClock.elapsedRealtime()
        val lookup = localLookupScope.async { block() }
        return try {
            withTimeoutOrNull((budgetMs - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L)) {
                LocalStageCompleted(lookup.await())
            }
        } finally {
            lookup.cancel()
        }
    }

    private data class LocalStageCompleted<T>(val value: T)

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
            communityEpoch = if (snapshot?.backendId == OFFICIAL_BACKEND_ID) communityEpoch.current() else 0,
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

    /** 失效时不取消共享请求，保留其 Provider 结论，仅阻止旧社区增强回灌。 */
    suspend fun invalidateOfficialQueryReuse() {
        queryReuseMutex.withLock {
            completedNetworkQueries.keys.removeAll { it.backendId == OFFICIAL_BACKEND_ID }
            inFlightNetworkQueries.keys.removeAll { it.backendId == OFFICIAL_BACKEND_ID }
        }
    }

    /** 系统最终展示／处置前再次核对代次，撤回或关闭开关不能留下旧拦截决定。 */
    fun currentCommunityResult(result: CheckResult): CheckResult {
        val fresh = communityEpoch.isCurrent(result.communityEpoch, result.communityReceivedAtElapsedMs)
        val cancelCommunityBlock = result.communityBlockCandidate &&
            (!fresh || !communityPreferences.blockEnabled.value)
        return result.copy(
            communityStatus = if (fresh) result.communityStatus else CommunityStatus.UNKNOWN,
            shouldBlock = if (cancelCommunityBlock) false else result.shouldBlock,
            resultType = if (cancelCommunityBlock) ResultType.PASS_BUT_NOTIFY else result.resultType,
            communityBlockCandidate = result.communityBlockCandidate && !cancelCommunityBlock,
        )
    }

    private suspend fun buildNetworkResult(
        backendResponse: BackendQueryResponse,
        localCost: Long,
        networkCost: Long,
    ): CheckResult {
        val base = buildProviderNetworkResult(backendResponse, localCost, networkCost)
        val metadata = decodeCommunityQueryMetadata(
            backendResponse.response, backendResponse.backendId == OFFICIAL_BACKEND_ID,
        )
        val status = if (communityEpoch.isCurrent(
                backendResponse.communityEpoch, backendResponse.receivedAtElapsedMs,
            )) metadata.status else CommunityStatus.UNKNOWN
        val block = status == CommunityStatus.ACTIVE && communityPreferences.blockEnabled.value &&
            !base.shouldBlock && base.resultType == ResultType.PASS_BUT_NOTIFY
        return currentCommunityResult(base.copy(
            communityStatus = status,
            communityBlockCandidate = block,
            shouldBlock = base.shouldBlock || block,
            resultType = if (block) ResultType.INTERCEPT else base.resultType,
            label = if (status == CommunityStatus.ACTIVE && base.label.isBlank())
                backendResponse.response.tag else base.label,
            feedbackPhone = metadata.feedbackPhone,
            feedbackTokenExpiresAt = metadata.feedbackTokenExpiresAt,
            communityEpoch = backendResponse.communityEpoch,
            communityReceivedAtElapsedMs = backendResponse.receivedAtElapsedMs,
        ))
    }

    private suspend fun buildProviderNetworkResult(
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

    private fun buildNetworkFailureResult(
        exception: Exception,
        localCost: Long,
        networkCost: Long,
    ): CheckResult {
        val resultType = classifyNetworkFailure(exception)
        val apiError = exception as? QueryApiException
        Log.w(
            TAG,
            "Network query failed: category=${networkFailureCategory(resultType)}, " +
                "http=${apiError?.code}, code=${apiError?.errorCode}, " +
                "request_id=${apiError?.requestId}, cost=${networkCost}ms",
        )
        return CheckResult(
            shouldBlock = false,
            label = "",
            resultType = resultType,
            localCost = localCost,
            networkCost = networkCost,
            locationLookupAttempted = true,
        )
    }

    /** 只返回稳定错误分类，禁止把 URL、Token、Header 或响应正文写入日志。 */
    private fun networkFailureCategory(resultType: ResultType): String = when (resultType) {
        ResultType.NETWORK_TIMEOUT -> "timeout"
        ResultType.NETWORK_RATE_LIMITED -> "rate_limited"
        ResultType.NETWORK_SERVER_ERROR -> "server_response"
        ResultType.NETWORK_INVALID_REQUEST -> "invalid_request"
        ResultType.NETWORK_AUTH_ERROR -> "authentication"
        ResultType.NETWORK_INVALID_RESPONSE -> "invalid_response"
        ResultType.NETWORK_UPSTREAM_TIMEOUT -> "upstream_timeout"
        ResultType.NETWORK_UPSTREAM_UNAVAILABLE -> "upstream_unavailable"
        ResultType.NETWORK_FEEDBACK_ERROR -> "feedback_token"
        ResultType.NETWORK_CONNECTION_ERROR -> "network"
        ResultType.QUERY_SERVICE_UNAVAILABLE -> "backend_blocked"
        else -> "unexpected"
    }

    private data class QueryReuseKey(
        val phone: String,
        val backendId: String?,
        val activationId: Long?,
        val timeoutMs: Long,
        val communityEpoch: Long,
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
