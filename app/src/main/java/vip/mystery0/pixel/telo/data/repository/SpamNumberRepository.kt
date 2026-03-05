package vip.mystery0.pixel.telo.data.repository

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.remote.QueryResponse
import vip.mystery0.pixel.telo.data.remote.SyncApi

data class CheckResult(
    val shouldBlock: Boolean,
    val label: String,
    val resultType: ResultType,
    val localCost: Long,
    val networkCost: Long
)

class SpamNumberRepository : KoinComponent {
    companion object {
        private const val TAG = "SpamNumberRepository"
    }

    private val syncRepository: SyncRepository by inject()
    private val syncApi: SyncApi by inject()
    private val prefs: SharedPreferences by inject()

    /**
     * 仅发起联网查询，跳过本地数据库检查。
     * 用于手动重试联网查询超时的记录。超时限制同为 3s。
     */
    suspend fun queryNetwork(phoneNumber: String): QueryResponse {
        val phone = phoneNumber.removePrefix("+86")
        return withContext(Dispatchers.IO) {
            withTimeout(3000L) {
                syncApi.queryNumber(phone)
            }
        }
    }

    suspend fun checkSpam(phoneNumber: String, forceNetworkQuery: Boolean = false): CheckResult {
        val start = System.currentTimeMillis()
        val phone = phoneNumber.removePrefix("+86")
        var localCost: Long
        var networkCost: Long

        // 1. Local Lookup
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
                Log.i(TAG, "Local hit: $phone, cost: ${localCost}ms")
                return CheckResult(true, spamNumber.tag, ResultType.INTERCEPT, localCost, 0)
            }
        } else {
            localCost = System.currentTimeMillis() - start
        }

        if (localCost > 100) {
            Log.w(TAG, "Local lookup too slow: ${localCost}ms")
        }

        // Check if user disabled network query
        val noNetworkQuery = prefs.getBoolean("no_network_query", false)
        if (noNetworkQuery && !forceNetworkQuery) {
            Log.i(TAG, "Offline query only enabled, skipping network query for $phone")
            return CheckResult(false, "", ResultType.PASS_BUT_NOTIFY, localCost, 0)
        }

        // 2. Online Fallback
        val networkStart = System.currentTimeMillis()
        return withContext(Dispatchers.IO) {
            try {
                // Total timeout 3s includes network latency
                withTimeout(3000L) {
                    val response = syncApi.queryNumber(phone)
                    networkCost = System.currentTimeMillis() - networkStart
                    Log.i(TAG, "Network hit: $phone, result: $response, cost: ${networkCost}ms")

                    if (response.isSpam) {
                        CheckResult(
                            true,
                            response.tag,
                            ResultType.INTERCEPT,
                            localCost,
                            networkCost
                        )
                    } else {
                        CheckResult(false, "", ResultType.PASS_BUT_NOTIFY, localCost, networkCost)
                    }
                }
            } catch (e: Exception) {
                networkCost = System.currentTimeMillis() - networkStart
                Log.w(
                    TAG,
                    "checkSpam remote failed or timed out: ${e.message}, total cost: ${System.currentTimeMillis() - start}ms"
                )
                // Fallback to allow call on error/timeout, but mark as TIMEOUT
                CheckResult(
                    false,
                    "Timeout/Error",
                    ResultType.NETWORK_TIMEOUT,
                    localCost,
                    networkCost
                )
            }
        }
    }
}
