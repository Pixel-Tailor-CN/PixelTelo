package vip.mystery0.pixel.telo.data.repository

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.remote.PhoneLocationInfo
import vip.mystery0.pixel.telo.data.remote.QueryResponse
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
    suspend fun queryNetwork(phoneNumber: String): QueryResponse {
        val phone = phoneNumber.removePrefix("+86")
        return withContext(Dispatchers.IO) {
            withTimeout(networkTimeoutMs().milliseconds) {
                queryRepository.queryNumber(phone)
            }
        }
    }

    suspend fun checkSpam(phoneNumber: String, forceNetworkQuery: Boolean = false): CheckResult {
        val start = System.currentTimeMillis()
        val phone = phoneNumber.removePrefix("+86")
        var localCost: Long
        var networkCost: Long

        val whiteMatch = userListRepository.findWhiteListMatch(phone)
        if (whiteMatch != null) {
            Log.i(TAG, "White list hit")
            return CheckResult(false, whiteMatch.remark ?: "", ResultType.WHITE_LIST, 0, 0)
        }

        val blackMatch = userListRepository.findBlackListMatch(phone)
        if (blackMatch != null) {
            Log.i(TAG, "Black list hit")
            return CheckResult(true, blackMatch.remark ?: "", ResultType.BLACK_LIST, 0, 0)
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
                        forceBlock = true
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
                val response = withTimeout(networkTimeoutMs().milliseconds) {
                    queryRepository.queryNumber(phone)
                }
                networkCost = System.currentTimeMillis() - networkStart
                Log.i(TAG, "Network result: source=${response.source}, cost=${networkCost}ms")

                buildNetworkResult(response, localCost, networkCost)
            } catch (exception: TimeoutCancellationException) {
                networkCost = System.currentTimeMillis() - networkStart
                Log.w(TAG, "Network query timed out: cost=${System.currentTimeMillis() - start}ms")
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
                    "Network query failed or timed out: cost=${System.currentTimeMillis() - start}ms"
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
        response: QueryResponse,
        localCost: Long,
        networkCost: Long,
    ): CheckResult {
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
                forceBlock = true,
                querySource = response.source,
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
                forceBlock = true,
                querySource = response.source,
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
            feedbackToken = response.feedbackToken,
        )
    }

    private fun networkTimeoutMs(): Long {
        return prefs.getInt(
            SettingViewModel.KEY_NETWORK_TIMEOUT,
            SettingViewModel.DEFAULT_NETWORK_TIMEOUT_SECONDS
        ).coerceIn(1, 3) * 1000L
    }

    private fun locationRuleLabel(value: String): String {
        return "Location: $value"
    }
}
