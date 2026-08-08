package vip.mystery0.pixel.telo.data.repository

import android.content.SharedPreferences
import android.util.Log
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.PhoneNumberNormalizer
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.query.BackendQueryResponse
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
    }

    private val syncRepository: SyncRepository by inject()
    private val queryRepository: QueryRepository by inject()
    private val prefs: SharedPreferences by inject()
    private val userListRepository: UserListRepository by inject()

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

        val networkStart = System.currentTimeMillis()
        return withContext(Dispatchers.IO) {
            try {
                val backendResponse = withTimeout(networkTimeoutMs().milliseconds) {
                    queryRepository.queryNumber(phone)
                }
                networkCost = System.currentTimeMillis() - networkStart
                Log.i(TAG, "Network query succeeded: cost=${networkCost}ms")

                buildNetworkResult(backendResponse, localCost, networkCost)
            } catch (exception: TimeoutCancellationException) {
                networkCost = System.currentTimeMillis() - networkStart
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
                networkCost = System.currentTimeMillis() - networkStart
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
}
