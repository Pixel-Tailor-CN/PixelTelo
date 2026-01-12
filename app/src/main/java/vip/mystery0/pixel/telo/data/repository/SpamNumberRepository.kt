package vip.mystery0.pixel.telo.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.remote.SyncApi

class SpamNumberRepository : KoinComponent {
    companion object {
        private const val TAG = "SpamNumberRepository"
    }

    private val syncRepository: SyncRepository by inject()
    private val syncApi: SyncApi by inject()

    suspend fun checkSpam(phoneNumber: String): Pair<Boolean, String> {
        val phone = phoneNumber.removePrefix("+86")

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
            if (spamNumber != null) return true to spamNumber.tag
        }

        // 2. Online Fallback
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(3000L) {
                    val response = syncApi.queryNumber(phone)
                    Log.i(TAG, "checkSpam: $response")
                    if (response.isSpam) {
                        true to response.tag
                    } else {
                        false to ""
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "checkSpam remote failed: ${e.message}")
                false to ""
            }
        }
    }
}
