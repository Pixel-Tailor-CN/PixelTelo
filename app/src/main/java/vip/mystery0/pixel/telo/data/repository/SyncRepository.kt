package vip.mystery0.pixel.telo.data.repository

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.system.Os
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import vip.mystery0.pixel.telo.data.MastDatabase
import vip.mystery0.pixel.telo.data.PreservingMastOpenHelperFactory
import vip.mystery0.pixel.telo.data.entity.SpamNumberEntity
import vip.mystery0.pixel.telo.data.remote.SyncApi
import vip.mystery0.pixel.telo.data.remote.SyncResponse

class SyncRepository(
    private val context: Context,
    private val syncApi: SyncApi,
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "SyncRepository"
        private const val DB_FILE_NAME = "mast.db"
        private const val MAX_ZIP_BYTES = 512L * 1024 * 1024
        private const val MAX_DATABASE_BYTES = 2L * 1024 * 1024 * 1024
        private val CHECKSUM_PATTERN = Regex("[a-fA-F0-9]{64}")
        private val DATABASE_SIDECARS = listOf("-wal", "-shm", "-journal")
    }

    // 连接只由仓库持有；读取、关闭、替换和删除共用同一锁，不把连接交给调用方。
    private val databaseMutex = Mutex()
    // 下载期间不占用查询锁；安装与删除命令按完整操作串行，避免删除后又安装迟到结果。
    private val updateMutex = Mutex()
    private var database: MastDatabase? = null
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _versionFlow = MutableStateFlow("")
    val versionFlow = _versionFlow.asStateFlow()
    private val _isDatabaseReady = MutableStateFlow(false)
    val isDatabaseReady = _isDatabaseReady.asStateFlow()

    init {
        repositoryScope.launch { refreshDatabaseState() }
    }

    suspend fun lookupSpamNumber(phoneNumber: String): SpamNumberEntity? = withContext(Dispatchers.IO) {
        databaseMutex.withLock {
            try {
                val db = openDatabaseLocked() ?: return@withLock null
                db.spamNumberDao().search(phoneNumber)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                invalidateDatabaseLocked()
                Log.w(TAG, "Local database lookup failed")
                null
            }
        }
    }

    suspend fun checkUpdate(currentVersion: String): SyncResponse = syncApi.checkUpdate(currentVersion)

    suspend fun getCurrentVersion(): String = withContext(Dispatchers.IO) {
        databaseMutex.withLock {
            readDatabaseStateLocked().version
        }
    }

    suspend fun getLocalRowCount(): Long = withContext(Dispatchers.IO) {
        databaseMutex.withLock {
            readDatabaseStateLocked().rowCount
        }
    }

    private suspend fun refreshDatabaseState() = databaseMutex.withLock {
        readDatabaseStateLocked()
    }

    private fun openDatabaseLocked(): MastDatabase? {
        database?.let { return it }
        val file = context.getDatabasePath(DB_FILE_NAME)
        recoverInterruptedReplacementLocked(file)
        if (!file.isFile) return null
        return createDatabase(DB_FILE_NAME).also { database = it }
    }

    private fun createDatabase(name: String): MastDatabase = Room.databaseBuilder(
        context, MastDatabase::class.java, name,
    ).openHelperFactory(PreservingMastOpenHelperFactory)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .build()

    private suspend fun readDatabaseStateLocked(): DatabaseState {
        val state = try {
            val db = openDatabaseLocked()
            if (db == null) DatabaseState() else DatabaseState(
                version = db.mastDao().getVersion().orEmpty(),
                rowCount = db.spamNumberDao().getRowCount(),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            invalidateDatabaseLocked()
            Log.w(TAG, "Local database validation failed")
            DatabaseState()
        }
        _versionFlow.value = state.version
        _isDatabaseReady.value = state.ready
        return state
    }

    private fun invalidateDatabaseLocked() {
        database?.close()
        database = null
        _versionFlow.value = ""
        _isDatabaseReady.value = false
    }

    suspend fun downloadAndInstallWithProgress(
        downloadUrl: String,
        expectedChecksum: String,
        expectedSize: Long,
        expectedVersion: String? = null,
        expectedRowCount: Long = 0,
        onProgress: (Int) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        updateMutex.withLock {
            var archive: File? = null
            var candidate: File? = null
            try {
                require(expectedSize in 1..MAX_ZIP_BYTES)
                require(CHECKSUM_PATTERN.matches(expectedChecksum))
                val databaseFile = context.getDatabasePath(DB_FILE_NAME)
                val parent = requireNotNull(databaseFile.parentFile)
                check(parent.isDirectory || parent.mkdirs())
                val archiveFile = File.createTempFile("mast-update-", ".zip", context.cacheDir)
                archive = archiveFile
                // 候选和正式库位于同一文件系统，最终 rename 不经过跨卷复制窗口。
                val candidateFile = File.createTempFile("mast-candidate-", ".db", parent)
                candidate = candidateFile
                downloadArchive(downloadUrl, archiveFile, expectedSize, onProgress)
                currentCoroutineContext().ensureActive()
                check(calculateSha256(archiveFile).equals(expectedChecksum, ignoreCase = true))
                extractDatabase(archiveFile, candidateFile)
                val state = validateCandidate(candidateFile)
                check(expectedVersion.isNullOrBlank() || state.version == expectedVersion)
                check(expectedRowCount <= 0 || state.rowCount == expectedRowCount)
                currentCoroutineContext().ensureActive()

                databaseMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    database?.close()
                    database = null
                    installValidatedCandidateLocked(candidateFile, databaseFile)
                    // rename 成功才切换状态；候选已通过 Room schema、非空数据和完整性校验。
                    _versionFlow.value = state.version
                    _isDatabaseReady.value = true
                }
                onProgress(100)
                true
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // 不输出异常正文，网络异常可能包含带签名参数的完整下载地址。
                Log.w(TAG, "Database update failed: category=${exception.javaClass.simpleName}")
                false
            } finally {
                archive?.delete()
                candidate?.let { file ->
                    file.delete()
                    File(file.path + "-journal").delete()
                    File(file.path + "-wal").delete()
                    File(file.path + "-shm").delete()
                }
            }
        }
    }

    /** 取消直接关闭 OkHttp Call，下载不依赖 ViewModel 或阻塞线程自然读完。 */
    private suspend fun downloadArchive(
        downloadUrl: String,
        target: File,
        expectedSize: Long,
        onProgress: (Int) -> Unit,
    ) = suspendCancellableCoroutine { continuation ->
        val call = okHttpClient.newCall(Request.Builder().url(downloadUrl).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                try {
                    response.use {
                        check(it.isSuccessful) { "Download request failed" }
                        val body = it.body
                        var total = 0L
                        var previousProgress = -1
                        body.byteStream().use { input ->
                            FileOutputStream(target).use { output ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    if (!continuation.isActive) return
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    check(total <= expectedSize) { "Archive size exceeds expected size" }
                                    output.write(buffer, 0, count)
                                    val progress = ((total * 99) / expectedSize).toInt()
                                    if (progress != previousProgress) {
                                        previousProgress = progress
                                        onProgress(progress)
                                    }
                                }
                                output.fd.sync()
                            }
                        }
                        check(total == expectedSize) { "Archive size mismatch" }
                    }
                    if (continuation.isActive) continuation.resume(Unit)
                } catch (exception: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                } finally {
                    // 取消可能先触发仓库清理、后进入回调；最后关闭流的一方再次清理自身文件。
                    if (call.isCanceled()) target.delete()
                }
            }
        })
    }

    private suspend fun extractDatabase(archive: File, target: File) {
        val coroutineContext = currentCoroutineContext()
        ZipFile(archive).use { zip ->
            val matches = zip.entries().asSequence().filter {
                !it.isDirectory && it.name.startsWith("mast_") && it.name.endsWith(".db") &&
                    '/' !in it.name && '\\' !in it.name
            }.toList()
            check(matches.size == 1) { "Archive must contain one database" }
            val entry = matches.single()
            check(entry.size in 1..MAX_DATABASE_BYTES) { "Invalid database size" }
            zip.getInputStream(entry).use { input ->
                FileOutputStream(target).use { output ->
                    var total = 0L
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= MAX_DATABASE_BYTES) { "Database size limit exceeded" }
                        output.write(buffer, 0, count)
                    }
                    check(total == entry.size) { "Database size mismatch" }
                    output.fd.sync()
                }
            }
        }
    }

    private suspend fun validateCandidate(file: File): DatabaseState {
        // Room 实际打开候选，复用正式 schema 校验；不允许把任意 SQLite 文件装成号码库。
        val db = createDatabase(file.absolutePath)
        val state = try {
            DatabaseState(
                version = db.mastDao().getVersion().orEmpty(),
                rowCount = db.spamNumberDao().getRowCount(),
            ).also { check(it.ready) { "Database has no usable data" } }
        } finally {
            db.close()
        }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok") { "Database integrity check failed" }
            }
        }
        return state
    }

    private suspend fun installValidatedCandidateLocked(candidate: File, target: File) {
        recoverInterruptedReplacementLocked(target)
        try {
            prepareExistingDatabaseForReplacement(target)
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            // 损坏的旧库不能 checkpoint 时，连同日志保存在独立目录，失败或重启可整组恢复。
            installWithRecoverableBackupLocked(candidate, target)
            return
        }
        currentCoroutineContext().ensureActive()
        Os.rename(candidate.absolutePath, target.absolutePath)
    }

    private fun prepareExistingDatabaseForReplacement(file: File) {
        val wal = File(file.path + "-wal")
        val journal = File(file.path + "-journal")
        if (file.isFile && (wal.exists() || journal.exists())) {
            // Android 默认损坏处理器会删除旧库；此处仅探测/合并日志，绝不允许隐式删除。
            val preserveCorruptFile = DatabaseErrorHandler {
                Log.w(TAG, "Previous database journal cannot be recovered")
            }
            SQLiteDatabase.openDatabase(
                file.path, null, SQLiteDatabase.OPEN_READWRITE, preserveCorruptFile,
            ).use { sqlite ->
                sqlite.rawQuery("PRAGMA journal_mode=DELETE", null).use { cursor ->
                    check(cursor.moveToFirst() && cursor.getString(0).equals("delete", true)) {
                        "Unable to finish previous database journal"
                    }
                }
            }
        }
        // 主文件不存在时只可能留下不可独立使用的旧日志，不能将其配给新主文件。
        check(!file.isFile || !wal.exists() || wal.length() == 0L) { "Previous database WAL is still active" }
        check(!file.isFile || !journal.exists() || journal.length() == 0L) { "Previous database journal is still active" }
        check(!wal.exists() || wal.delete())
        val shm = File(file.path + "-shm")
        check(!shm.exists() || shm.delete())
        check(!journal.exists() || journal.delete())
    }

    private suspend fun installWithRecoverableBackupLocked(candidate: File, target: File) {
        // 不复制大库；同文件系统重命名保留旧主文件及其日志，只在新主文件装好后清理。
        val backup = replacementBackupDirectory(target)
        check(backup.mkdir()) { "Unable to preserve previous database" }
        val previous = File(backup, DB_FILE_NAME)
        try {
            if (target.exists()) Os.rename(target.path, previous.path)
            for (suffix in DATABASE_SIDECARS) {
                val sidecar = File(target.path + suffix)
                if (sidecar.exists()) Os.rename(sidecar.path, File(backup, DB_FILE_NAME + suffix).path)
            }
            currentCoroutineContext().ensureActive()
            Os.rename(candidate.path, target.path)
        } catch (exception: Exception) {
            try {
                recoverInterruptedReplacementLocked(target)
            } catch (recoveryException: Exception) {
                // 恢复失败时保留整个备份，下一次打开前继续恢复，不删除仍需恢复的旧文件。
                exception.addSuppressed(recoveryException)
            }
            throw exception
        }
        // 新主文件已原子安装；清理失败仅留待下次打开重试，不把已完成安装伪报成失败。
        runCatching { recoverInterruptedReplacementLocked(target) }.onFailure {
            Log.w(TAG, "Previous database cleanup deferred")
        }
    }

    /** 打开/删除前恢复被终止的替换；旧主文件最后恢复，避免把半恢复状态误认作新库已提交。 */
    private fun recoverInterruptedReplacementLocked(target: File) {
        val backup = replacementBackupDirectory(target)
        if (!backup.exists()) return
        val previous = File(backup, DB_FILE_NAME)
        if (previous.exists() && !target.exists()) {
            for (suffix in DATABASE_SIDECARS) {
                val saved = File(backup, DB_FILE_NAME + suffix)
                if (saved.exists()) Os.rename(saved.path, target.path + suffix)
            }
            Os.rename(previous.path, target.path)
        }
        // target 存在且 previous 仍在，表示候选 rename 已提交；否则旧库未移动或已整组恢复。
        check(!previous.exists() || previous.delete()) { "Previous database cleanup failed" }
        for (suffix in DATABASE_SIDECARS) {
            val saved = File(backup, DB_FILE_NAME + suffix)
            check(!saved.exists() || saved.delete()) { "Previous database journal cleanup failed" }
        }
        check(backup.delete()) { "Previous database directory cleanup failed" }
    }

    private fun replacementBackupDirectory(target: File): File =
        File(target.parentFile, "mast-replacement-backup")

    private suspend fun calculateSha256(file: File): String {
        val coroutineContext = currentCoroutineContext()
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun deleteDatabase() = withContext(Dispatchers.IO) {
        updateMutex.withLock {
            databaseMutex.withLock {
                val file = context.getDatabasePath(DB_FILE_NAME)
                database?.close()
                database = null
                recoverInterruptedReplacementLocked(file)
                check(!file.exists() || file.delete()) { "Database deletion failed" }
                File(file.path + "-wal").delete()
                File(file.path + "-shm").delete()
                File(file.path + "-journal").delete()
                _versionFlow.value = ""
                _isDatabaseReady.value = false
                Log.i(TAG, "Database deleted successfully")
            }
        }
    }

    private data class DatabaseState(val version: String = "", val rowCount: Long = 0) {
        val ready: Boolean get() = version.isNotBlank() && rowCount > 0
    }
}
