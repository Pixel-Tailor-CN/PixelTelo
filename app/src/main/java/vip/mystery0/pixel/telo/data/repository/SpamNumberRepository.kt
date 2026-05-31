package vip.mystery0.pixel.telo.data.repository

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.remote.PhoneLocationInfo
import vip.mystery0.pixel.telo.data.remote.QueryResponse
import vip.mystery0.pixel.telo.data.remote.SyncApi
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
    val forceBlock: Boolean = false
)

class SpamNumberRepository : KoinComponent {
    companion object {
        private const val TAG = "SpamNumberRepository"
    }

    private val syncRepository: SyncRepository by inject()
    private val syncApi: SyncApi by inject()
    private val prefs: SharedPreferences by inject()
    private val userListRepository: UserListRepository by inject()

    /**
     * 仅发起联网查询，跳过本地数据库检查。
     * 用于手动重试联网查询超时的记录。超时限制使用用户设置。
     */
    suspend fun queryNetwork(phoneNumber: String): QueryResponse {
        val phone = phoneNumber.removePrefix("+86")
        val timeoutMs = prefs.getInt("network_timeout", 5) * 1000L
        return withContext(Dispatchers.IO) {
            withTimeout(timeoutMs.milliseconds) {
                syncApi.queryNumber(phone)
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
            Log.i(TAG, "White list hit: $phone, entry: ${whiteMatch.phoneNumber}")
            return CheckResult(false, whiteMatch.remark ?: "", ResultType.WHITE_LIST, 0, 0)
        }

        val blackMatch = userListRepository.findBlackListMatch(phone)
        if (blackMatch != null) {
            Log.i(TAG, "Black list hit: $phone, entry: ${blackMatch.phoneNumber}")
            return CheckResult(true, blackMatch.remark ?: "", ResultType.BLACK_LIST, 0, 0)
        }

        val db = syncRepository.getDb()
        if (db != null) {
            val spamNumber = withContext(Dispatchers.IO) {
                try {
                    db.spamNumberDao().search(phone)
                } catch (e: Exception) {
                    Log.w(TAG, "checkSpam local failed", e)
                    null
                } finally {
                    db.close()
                }
            }
            localCost = System.currentTimeMillis() - start
            if (spamNumber != null) {
                val tagBlackMatch = userListRepository.findBlackListTagMatch(spamNumber.tag)
                if (tagBlackMatch != null) {
                    Log.i(TAG, "Black list tag hit: $phone, tag: ${spamNumber.tag}")
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
                    Log.i(TAG, "White list tag hit: $phone, tag: ${spamNumber.tag}")
                    return CheckResult(false, spamNumber.tag, ResultType.WHITE_LIST, localCost, 0)
                }
                Log.i(TAG, "Local hit: $phone, cost: ${localCost}ms")
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
            Log.i(TAG, "Offline query only enabled, skipping network query for $phone")
            return CheckResult(false, "", ResultType.PASS_BUT_NOTIFY, localCost, 0)
        }

        val networkStart = System.currentTimeMillis()
        val timeoutMs = prefs.getInt("network_timeout", 5) * 1000L
        return withContext(Dispatchers.IO) {
            try {
                val response = withTimeout(timeoutMs.milliseconds) {
                    syncApi.queryNumber(phone)
                }
                networkCost = System.currentTimeMillis() - networkStart
                Log.i(TAG, "Network hit: $phone, result: $response, cost: ${networkCost}ms")

                buildNetworkResult(response, localCost, networkCost, phone)
            } catch (e: Exception) {
                networkCost = System.currentTimeMillis() - networkStart
                Log.w(
                    TAG,
                    "checkSpam remote failed or timed out: ${e.message}, total cost: ${System.currentTimeMillis() - start}ms"
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
        phone: String
    ): CheckResult {
        val locationWhiteMatch = userListRepository.findWhiteListLocationMatch(response.data)
        if (locationWhiteMatch != null) {
            val label = locationWhiteMatch.remark
                ?: locationRuleLabel(locationWhiteMatch.phoneNumber)
            Log.i(
                TAG,
                "White list location hit: $phone, location: ${locationWhiteMatch.phoneNumber}"
            )
            return CheckResult(
                shouldBlock = false,
                label = label,
                resultType = ResultType.WHITE_LIST,
                localCost = localCost,
                networkCost = networkCost,
                locationInfo = response.data,
                locationLookupAttempted = true
            )
        }

        val locationBlackMatch = userListRepository.findBlackListLocationMatch(response.data)
        if (locationBlackMatch != null) {
            val label = locationBlackMatch.remark
                ?: locationRuleLabel(locationBlackMatch.phoneNumber)
            Log.i(
                TAG,
                "Black list location hit: $phone, location: ${locationBlackMatch.phoneNumber}"
            )
            return CheckResult(
                shouldBlock = true,
                label = label,
                resultType = ResultType.BLACK_LIST,
                localCost = localCost,
                networkCost = networkCost,
                locationInfo = response.data,
                locationLookupAttempted = true,
                forceBlock = true
            )
        }

        val tagBlackMatch = if (response.tag.isNotBlank()) {
            userListRepository.findBlackListTagMatch(response.tag)
        } else {
            null
        }
        if (tagBlackMatch != null) {
            Log.i(TAG, "Black list tag hit: $phone, tag: ${response.tag}")
            return CheckResult(
                shouldBlock = true,
                label = response.tag,
                resultType = ResultType.BLACK_LIST,
                localCost = localCost,
                networkCost = networkCost,
                locationInfo = response.data,
                locationLookupAttempted = true,
                forceBlock = true
            )
        }

        if (response.isSpam) {
            val tagWhiteMatch = userListRepository.findWhiteListTagMatch(response.tag)
            if (tagWhiteMatch != null) {
                Log.i(TAG, "White list tag hit: $phone, tag: ${response.tag}")
                return CheckResult(
                    shouldBlock = false,
                    label = response.tag,
                    resultType = ResultType.WHITE_LIST,
                    localCost = localCost,
                    networkCost = networkCost,
                    locationInfo = response.data,
                    locationLookupAttempted = true
                )
            }
            return CheckResult(
                shouldBlock = true,
                label = response.tag,
                resultType = ResultType.INTERCEPT,
                localCost = localCost,
                networkCost = networkCost,
                locationInfo = response.data,
                locationLookupAttempted = true
            )
        }

        return CheckResult(
            shouldBlock = false,
            label = "",
            resultType = ResultType.PASS_BUT_NOTIFY,
            localCost = localCost,
            networkCost = networkCost,
            locationInfo = response.data,
            locationLookupAttempted = true
        )
    }

    private fun locationRuleLabel(value: String): String {
        return "Location: $value"
    }
}
