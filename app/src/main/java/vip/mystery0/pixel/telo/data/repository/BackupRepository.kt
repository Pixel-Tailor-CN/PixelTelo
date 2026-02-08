package vip.mystery0.pixel.telo.data.repository

import kotlinx.serialization.json.Json
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.dto.BackupData
import vip.mystery0.pixel.telo.data.dto.BlockedCallDto
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.ResultType
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 负责拦截记录的备份与恢复操作。
 * 备份格式：ZIP 压缩包，内含 backup.json。
 */
class BackupRepository(private val blockedCallDao: BlockedCallDao) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 将所有拦截记录导出为 ZIP 备份文件，写入给定的输出流。
     */
    suspend fun backup(outputStream: OutputStream) {
        val records = blockedCallDao.getAllSnapshot().map { it.toDto() }
        val backupData = BackupData(
            exportedAt = System.currentTimeMillis(),
            records = records
        )
        val jsonString = json.encodeToString(BackupData.serializer(), backupData)

        ZipOutputStream(outputStream).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(jsonString.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    /**
     * 从 ZIP 备份文件恢复拦截记录，跳过已存在的重复记录（按 phoneNumber + blockTime 去重）。
     * @return 实际插入的新记录数量
     */
    suspend fun restore(inputStream: InputStream): Int {
        val jsonString = readJsonFromZip(inputStream)
        val backupData = json.decodeFromString(BackupData.serializer(), jsonString)

        var insertedCount = 0
        for (dto in backupData.records) {
            // 智能合并：相同 (phoneNumber, blockTime) 视为重复，跳过
            val existing = blockedCallDao.findByKey(dto.phoneNumber, dto.blockTime)
            if (existing == null) {
                blockedCallDao.insert(dto.toEntity())
                insertedCount++
            }
        }
        return insertedCount
    }

    /**
     * 从 ZIP 输入流中读取 backup.json 的内容
     */
    private fun readJsonFromZip(inputStream: InputStream): String {
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "backup.json") {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        error("backup.json not found in ZIP archive")
    }

    private fun BlockedCall.toDto() = BlockedCallDto(
        phoneNumber = phoneNumber,
        blockTime = blockTime,
        remark = remark,
        resultType = resultType.name,
        localDuration = localDuration,
        networkDuration = networkDuration
    )

    private fun BlockedCallDto.toEntity() = BlockedCall(
        phoneNumber = phoneNumber,
        blockTime = blockTime,
        remark = remark,
        resultType = runCatching { ResultType.valueOf(resultType) }.getOrDefault(ResultType.INTERCEPT),
        localDuration = localDuration,
        networkDuration = networkDuration
    )
}
