package vip.mystery0.pixel.telo.data.repository

import kotlinx.serialization.json.Json
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.dao.UserListDao
import vip.mystery0.pixel.telo.data.dto.BackupData
import vip.mystery0.pixel.telo.data.dto.CURRENT_BACKUP_VERSION
import vip.mystery0.pixel.telo.data.dto.BlockedCallDto
import vip.mystery0.pixel.telo.data.dto.LocalNumberLabelDto
import vip.mystery0.pixel.telo.data.dto.UserListEntryDto
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.LocalNumberLabel
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份选项：控制哪些数据参与备份或恢复
 */
data class BackupOptions(
    val includeBlockedCalls: Boolean = true,
    val includeBlackList: Boolean = true,
    val includeWhiteList: Boolean = true,
    val includeLocalNumberLabels: Boolean = true,
)

/**
 * 从备份文件解析出的预览信息，在恢复时展示给用户
 */
data class BackupPreview(
    val data: BackupData,
    val blockedCallCount: Int,
    val blackListCount: Int,
    val whiteListCount: Int,
    val localNumberLabelCount: Int,
)

/**
 * 恢复结果，包含拦截记录/黑白名单插入数量，以及本地标签新增、覆盖、跳过统计。
 */
data class RestoreResult(
    val insertedCalls: Int,
    val insertedBlack: Int,
    val insertedWhite: Int,
    val localLabels: RestoreLocalLabelsResult,
)

/**
 * 负责拦截记录、黑白名单与本地号码标签的备份与恢复操作。
 * 备份格式：ZIP 压缩包，内含 backup.json。
 * 显示开关不属于备份范围，本仓库不读取、不写入该偏好。
 */
class BackupRepository(
    private val blockedCallDao: BlockedCallDao,
    private val userListDao: UserListDao,
    private val localNumberLabelRepository: LocalNumberLabelRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 将选定的数据导出为 ZIP 备份文件。
     */
    suspend fun backup(outputStream: OutputStream, options: BackupOptions = BackupOptions()) {
        val records = if (options.includeBlockedCalls) {
            blockedCallDao.getAllSnapshot().map { it.toDto() }
        } else emptyList()

        val blackList = if (options.includeBlackList) {
            userListDao.getAllByType(ListType.BLACK).map { it.toDto() }
        } else emptyList()

        val whiteList = if (options.includeWhiteList) {
            userListDao.getAllByType(ListType.WHITE).map { it.toDto() }
        } else emptyList()

        val localNumberLabels = if (options.includeLocalNumberLabels) {
            localNumberLabelRepository.getAllSnapshot().map { it.toDto() }
        } else {
            emptyList()
        }

        val backupData = BackupData(
            version = CURRENT_BACKUP_VERSION,
            exportedAt = System.currentTimeMillis(),
            records = records,
            blackList = blackList,
            whiteList = whiteList,
            localNumberLabels = localNumberLabels,
        )
        val jsonString = json.encodeToString(BackupData.serializer(), backupData)

        ZipOutputStream(outputStream).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(jsonString.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    /**
     * 解析 ZIP 备份文件，返回预览信息（不执行写入）。
     * 用于在恢复确认 Sheet 中展示数据量。
     */
    fun parseBackup(inputStream: InputStream): BackupPreview {
        val jsonString = readJsonFromZip(inputStream)
        val data = json.decodeFromString(BackupData.serializer(), jsonString)
        return BackupPreview(
            data = data,
            blockedCallCount = data.records.size,
            blackListCount = data.blackList.size,
            whiteListCount = data.whiteList.size,
            localNumberLabelCount = data.localNumberLabels.size,
        )
    }

    /**
     * 恢复数据，按 options 决定恢复哪些部分。
     * 去重策略：BlockedCall 按 (phoneNumber, blockTime)，UserListEntry 按 (phoneNumber, listType)。
     * 本地标签仅在选中该范围时委托 [LocalNumberLabelRepository.restore]；未选择时不读取、不校验、不修改标签表。
     */
    suspend fun restore(preview: BackupPreview, options: BackupOptions): RestoreResult {
        var insertedCalls = 0
        var insertedBlack = 0
        var insertedWhite = 0

        if (options.includeBlockedCalls) {
            for (dto in preview.data.records) {
                val existing = blockedCallDao.findByKey(dto.phoneNumber, dto.blockTime)
                if (existing == null) {
                    blockedCallDao.insert(dto.toEntity())
                    insertedCalls++
                }
            }
        }

        if (options.includeBlackList) {
            for (dto in preview.data.blackList) {
                var entry = dto.toEntity(ListType.BLACK)
                // v3 之前的备份没有 force_block 字段：标签/归属地黑名单当时始终强制拦截，
                // 恢复时补齐以保持行为一致
                if (preview.data.version < 3 && (entry.tagMatch || entry.locationMatch)) {
                    entry = entry.copy(forceBlock = true)
                }
                if (userListDao.insert(entry) != -1L) {
                    insertedBlack++
                }
            }
        }

        if (options.includeWhiteList) {
            for (dto in preview.data.whiteList) {
                if (userListDao.insert(dto.toEntity(ListType.WHITE)) != -1L) {
                    insertedWhite++
                }
            }
        }

        val localLabels = if (options.includeLocalNumberLabels) {
            localNumberLabelRepository.restore(
                preview.data.localNumberLabels.map { dto ->
                    LocalNumberLabelRestoreEntry(
                        phoneNumber = dto.phoneNumber,
                        label = dto.label,
                        createdAt = dto.createdAt,
                        updatedAt = dto.updatedAt,
                    )
                }
            )
        } else {
            RestoreLocalLabelsResult(inserted = 0, overwritten = 0, skipped = 0)
        }

        return RestoreResult(insertedCalls, insertedBlack, insertedWhite, localLabels)
    }

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
        networkDuration = networkDuration,
        label = label,
        province = province,
        city = city,
    )

    private fun BlockedCallDto.toEntity() = BlockedCall(
        phoneNumber = phoneNumber,
        blockTime = blockTime,
        remark = remark,
        resultType = runCatching { ResultType.valueOf(resultType) }.getOrDefault(ResultType.INTERCEPT),
        localDuration = localDuration,
        networkDuration = networkDuration,
        label = label,
        province = province,
        city = city,
    )

    private fun UserListEntry.toDto() = UserListEntryDto(
        phoneNumber = phoneNumber,
        isPrefix = isPrefix,
        remark = remark,
        addedAt = addedAt,
        tagMatch = tagMatch,
        locationMatch = locationMatch,
        forceBlock = forceBlock,
    )

    private fun UserListEntryDto.toEntity(listType: ListType) = UserListEntry(
        phoneNumber = phoneNumber,
        isPrefix = isPrefix,
        listType = listType,
        remark = remark,
        addedAt = addedAt,
        tagMatch = tagMatch,
        locationMatch = locationMatch,
        forceBlock = forceBlock && listType == ListType.BLACK,
    )

    private fun LocalNumberLabel.toDto() = LocalNumberLabelDto(
        phoneNumber = normalizedPhoneNumber,
        label = label,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
