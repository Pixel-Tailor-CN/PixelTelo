package vip.mystery0.pixel.telo.data

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 随机安装身份不是硬件标识；只允许 Repository 取得原值。 */
class ReportDeviceIdentity internal constructor(internal val value: String) {
    override fun toString(): String = "ReportDeviceIdentity(redacted)"
}

class ReportIdentityUnavailableException : Exception("Report identity unavailable")

class ReportDeviceIdentityStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "community_report_device_id"))
    private val mutex = Mutex()

    internal suspend fun getOrCreate(): ReportDeviceIdentity = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val base = file.baseFile
                val existing = base.exists() || File(base.path + ".bak").exists() ||
                    File(base.path + ".new").exists()
                val bytes = if (existing) file.readFully() else createIdentity()
                require(bytes.size == 32)
                encode(bytes)
            } catch (_: Exception) {
                throw ReportIdentityUnavailableException()
            }
        }
    }

    /** 仅在用户确认失去旧记录管理权后调用，写失败保留原身份。 */
    internal suspend fun resetAfterConfirmation(): ReportDeviceIdentity = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                encode(createIdentity())
            } catch (_: Exception) {
                throw ReportIdentityUnavailableException()
            }
        }
    }

    private fun createIdentity(): ByteArray {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
        check(file.readFully().contentEquals(bytes))
        return bytes
    }

    private fun encode(bytes: ByteArray) = ReportDeviceIdentity(
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
    )
}
