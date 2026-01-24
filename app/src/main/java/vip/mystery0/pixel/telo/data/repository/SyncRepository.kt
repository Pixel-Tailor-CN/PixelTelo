package vip.mystery0.pixel.telo.data.repository

import android.content.Context
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import vip.mystery0.pixel.telo.data.MastDatabase
import vip.mystery0.pixel.telo.data.remote.SyncApi
import vip.mystery0.pixel.telo.data.remote.SyncResponse
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

class SyncRepository(
    private val context: Context,
    private val syncApi: SyncApi,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "SyncRepository"
        private const val DB_FILE_NAME = "mast.db"
    }

    private var database: MastDatabase? = null

    private val _versionFlow = MutableStateFlow("")
    val versionFlow = _versionFlow.asStateFlow()

    fun getDb(): MastDatabase? {
        val dbFile = context.getDatabasePath(DB_FILE_NAME)
        if (!dbFile.exists()) {
            Log.i(TAG, "$DB_FILE_NAME does not exist, skipping")
            return null
        }
        val db = Room.databaseBuilder(
            context,
            MastDatabase::class.java,
            DB_FILE_NAME
        ).build()
        database = db
        return database
    }

    private suspend fun reloadDb() {
        try {
            val dbFile = context.getDatabasePath(DB_FILE_NAME)
            if (!dbFile.exists()) {
                Log.i(TAG, "$DB_FILE_NAME does not exist")
                database = null
                return
            }
            val db = Room.databaseBuilder(
                context,
                MastDatabase::class.java,
                DB_FILE_NAME
            ).build()
            database = db

            _versionFlow.value = getCurrentVersion()
        } catch (e: Exception) {
            Log.w(TAG, "reloadDb error", e)
        }
    }

    suspend fun checkUpdate(currentVersion: String): SyncResponse {
        val version = currentVersion.ifBlank { "" }
        return syncApi.checkUpdate(version)
    }

    suspend fun getCurrentVersion(): String {
        val db = getDb() ?: return ""
        return try {
            val versionStr = try {
                db.mastDao().getVersion()
            } finally {
                db.close()
            }
            versionStr ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Error getting current version", e)
            ""
        }
    }

    suspend fun downloadAndInstallWithProgress(
        downloadUrl: String,
        expectedChecksum: String,
        expectedSize: Long,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val tempZipFile = File(context.cacheDir, "mast_update.zip")
        try {
            // 1. Download
            val request = Request.Builder().url(downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false
            val body = response.body

            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(tempZipFile)

            var bytesCopied: Long = 0
            val buffer = ByteArray(8 * 1024)
            var bytes = inputStream.read(buffer)
            while (bytes >= 0) {
                outputStream.write(buffer, 0, bytes)
                bytesCopied += bytes
                if (contentLength > 0) {
                    val progress = ((bytesCopied * 100) / contentLength).toInt()
                    onProgress(progress)
                }
                bytes = inputStream.read(buffer)
            }
            outputStream.close()
            inputStream.close()

            // 2. Verify Size
            if (tempZipFile.length() != expectedSize) {
                Log.w(TAG, "Size mismatch: ${tempZipFile.length()} vs $expectedSize")
                return@withContext false
            }

            // 3. Verify Checksum (SHA-256)
            onProgress(100) // verifying
            val calculatedChecksum = calculateSha256(tempZipFile)
            if (calculatedChecksum != expectedChecksum) {
                Log.w(TAG, "Checksum mismatch: $calculatedChecksum vs $expectedChecksum")
                return@withContext false
            }

            // 4. Unzip & Find DB
            var tempDbFile: File? = null
            ZipFile(tempZipFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.startsWith("mast_") && entry.name.endsWith(
                            ".db"
                        )
                    ) {
                        // Found logic for DB file
                        // Extract to a temp file
                        val extractedFile = File(context.cacheDir, "temp_mast.db")
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(extractedFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        tempDbFile = extractedFile
                        break
                    }
                }
            }

            if (tempDbFile == null) return@withContext false

            // 5. Replace existing DB
            val mastDbFile = context.getDatabasePath(DB_FILE_NAME)
            if (mastDbFile.exists()) {
                mastDbFile.delete()
            }
            // Ensure parent dir exists
            mastDbFile.parentFile?.mkdirs()
            if (tempDbFile.renameTo(mastDbFile)) {
                // Clean up zip
                tempZipFile.delete()
                reloadDb()
                return@withContext true
            } else {
                // Rename failed, try copy and delete
                try {
                    tempDbFile.copyTo(mastDbFile, overwrite = true)
                    tempDbFile.delete()
                    tempZipFile.delete()
                    reloadDb()
                    return@withContext true
                } catch (e: Exception) {
                    Log.e(TAG, "Error during rename", e)
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during download", e)
            return@withContext false
        } finally {
            if (tempZipFile.exists()) tempZipFile.delete()
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
