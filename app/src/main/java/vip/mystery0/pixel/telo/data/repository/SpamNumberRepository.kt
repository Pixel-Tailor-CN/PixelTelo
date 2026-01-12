package vip.mystery0.pixel.telo.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SpamNumberRepository : KoinComponent {
    companion object {
        private const val TAG = "SpamNumberRepository"
    }

    private val syncRepository: SyncRepository by inject()

    suspend fun checkSpam(phoneNumber: String): Pair<Boolean, String> {
        val db = syncRepository.getDb() ?: return false to ""
        val spamNumber = withContext(Dispatchers.IO) {
            try {
                db.spamNumberDao().search(phoneNumber)
            } catch (e: Exception) {
                Log.w(TAG, "checkSpam failed", e)
                null
            } finally {
                db.close()
            }
        }
        if (spamNumber == null) return false to ""
        return true to spamNumber.tag
    }
}
