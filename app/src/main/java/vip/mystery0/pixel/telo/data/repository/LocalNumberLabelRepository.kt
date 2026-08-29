package vip.mystery0.pixel.telo.data.repository

import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import vip.mystery0.pixel.telo.data.AppDatabase
import vip.mystery0.pixel.telo.data.PhoneNumberNormalizer
import vip.mystery0.pixel.telo.data.dao.LocalNumberLabelDao
import vip.mystery0.pixel.telo.data.entity.LocalNumberLabel

/** 本地标签写入的稳定结果，供 UI 映射提示，不直接展示异常正文。 */
sealed interface LocalLabelWriteResult {
    data object Created : LocalLabelWriteResult
    data object Updated : LocalLabelWriteResult
    data object Deleted : LocalLabelWriteResult
    data object Unchanged : LocalLabelWriteResult
    data object InvalidNumber : LocalLabelWriteResult
    data object LabelTooLong : LocalLabelWriteResult
    data class Failure(val cause: Throwable) : LocalLabelWriteResult
}

/** 备份恢复时的单条本地标签输入，号码仍需重新归一化。 */
data class LocalNumberLabelRestoreEntry(
    val phoneNumber: String,
    val label: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 本地标签批量恢复统计：新增、覆盖和跳过的条数。 */
data class RestoreLocalLabelsResult(
    val inserted: Int,
    val overwritten: Int,
    val skipped: Int,
)

/**
 * 本地号码标签的单一事实来源。
 *
 * 集中处理号码归一化、标签校验、删除语义和恢复冲突；日志不得包含号码或标签正文。
 */
class LocalNumberLabelRepository(
    private val dao: LocalNumberLabelDao,
    private val database: AppDatabase,
) {
    companion object {
        private const val TAG = "LocalNumberLabelRepository"
        /** 标签 trim 后的最大字符数，超过则拒绝且不自动截断。 */
        private const val MAX_LABEL_LENGTH = 40
        /** 本地查询超过该耗时只记录英文警告。 */
        private const val SLOW_LOOKUP_THRESHOLD_MS = 100L
    }

    /**
     * 按归一化号码查询本地标签。
     *
     * 无效号码或读取异常按无标签处理；超过 100ms 只记录耗时警告。
     */
    suspend fun find(phoneNumber: String): LocalNumberLabel? {
        val normalized = normalize(phoneNumber) ?: return null
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            dao.findByNumber(normalized)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            Log.w(TAG, "Local label lookup failed")
            null
        } finally {
            val costMs = SystemClock.elapsedRealtime() - startedAt
            if (costMs > SLOW_LOOKUP_THRESHOLD_MS) {
                Log.w(TAG, "Local label lookup too slow: cost=${costMs}ms")
            }
        }
    }

    /** 观察单个号码的当前标签；无效号码立即发出 null。 */
    fun observe(phoneNumber: String): Flow<LocalNumberLabel?> {
        val normalized = normalize(phoneNumber) ?: return flowOf(null)
        return dao.observeByNumber(normalized)
    }

    /**
     * 观察原始号码集合对应的标签映射。
     *
     * 先归一化并去重后再查询，结果按归一化号码关联后映射回原始号码；空输入不访问数据库。
     */
    fun observeLabels(phoneNumbers: Set<String>): Flow<Map<String, String>> {
        if (phoneNumbers.isEmpty()) {
            return flowOf(emptyMap())
        }
        val originalToNormalized = phoneNumbers.mapNotNull { original ->
            normalize(original)?.let { normalized -> original to normalized }
        }
        if (originalToNormalized.isEmpty()) {
            return flowOf(emptyMap())
        }
        val normalizedNumbers = originalToNormalized.map { it.second }.distinct()
        return dao.observeByNumbers(normalizedNumbers)
            .map { labels ->
                val labelByNormalized = labels.associate { entry ->
                    entry.normalizedPhoneNumber to entry.label
                }
                buildMap {
                    originalToNormalized.forEach { (original, normalized) ->
                        val label = labelByNormalized[normalized]
                        if (label != null) {
                            put(original, label)
                        }
                    }
                }
            }
            .catch {
                Log.w(TAG, "Local label observation failed")
                emit(emptyMap())
            }
    }

    /** 按更新时间倒序观察全部本地标签，供管理页使用。 */
    fun observeAll(): Flow<List<LocalNumberLabel>> = dao.observeAll()

    /** 导出备份用的全量快照，按更新时间倒序。 */
    suspend fun getAllSnapshot(): List<LocalNumberLabel> = dao.getAllSnapshot()

    /**
     * 设置或更新标签。
     *
     * 空标签按删除处理；超过 40 字符拒绝；内容未变不写入；新建使用同一当前时间。
     */
    suspend fun set(phoneNumber: String, label: String): LocalLabelWriteResult {
        val normalized = normalize(phoneNumber) ?: return LocalLabelWriteResult.InvalidNumber
        val trimmedLabel = label.trim()
        if (trimmedLabel.isEmpty()) {
            return delete(phoneNumber)
        }
        if (trimmedLabel.length > MAX_LABEL_LENGTH) {
            return LocalLabelWriteResult.LabelTooLong
        }
        return try {
            val existing = dao.findByNumber(normalized)
            if (existing?.label == trimmedLabel) {
                LocalLabelWriteResult.Unchanged
            } else {
                val now = System.currentTimeMillis()
                dao.upsert(
                    LocalNumberLabel(
                        normalizedPhoneNumber = normalized,
                        label = trimmedLabel,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                    )
                )
                if (existing == null) {
                    LocalLabelWriteResult.Created
                } else {
                    LocalLabelWriteResult.Updated
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LocalLabelWriteResult.Failure(exception)
        }
    }

    /** 删除指定号码的本地标签；号码无效或本就没有标签时不视为成功删除。 */
    suspend fun delete(phoneNumber: String): LocalLabelWriteResult {
        val normalized = normalize(phoneNumber) ?: return LocalLabelWriteResult.InvalidNumber
        return try {
            val deletedCount = dao.deleteByNumber(normalized)
            if (deletedCount > 0) {
                LocalLabelWriteResult.Deleted
            } else {
                LocalLabelWriteResult.Unchanged
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LocalLabelWriteResult.Failure(exception)
        }
    }

    /**
     * 按备份条目恢复本地标签。
     *
     * 内存中按归一化号码去重且最后一条有效记录生效；非法条目计入 skipped。
     * 读取现有集合、统计并写入均在同一 Room 事务中完成。
     */
    suspend fun restore(entries: List<LocalNumberLabelRestoreEntry>): RestoreLocalLabelsResult {
        val restoreTime = System.currentTimeMillis()
        var skipped = 0
        val uniqueEntries = linkedMapOf<String, LocalNumberLabel>()
        entries.forEach { entry ->
            val normalized = normalize(entry.phoneNumber)
            val trimmedLabel = entry.label.trim()
            if (normalized == null || trimmedLabel.isEmpty() || trimmedLabel.length > MAX_LABEL_LENGTH) {
                skipped++
                return@forEach
            }
            uniqueEntries[normalized] = LocalNumberLabel(
                normalizedPhoneNumber = normalized,
                label = trimmedLabel,
                createdAt = entry.createdAt.takeIf { timestamp -> timestamp > 0L } ?: restoreTime,
                updatedAt = entry.updatedAt.takeIf { timestamp -> timestamp > 0L } ?: restoreTime,
            )
        }
        if (uniqueEntries.isEmpty()) {
            return RestoreLocalLabelsResult(inserted = 0, overwritten = 0, skipped = skipped)
        }
        return database.withTransaction {
            val existingNumbers = dao.findByNumbers(uniqueEntries.keys.toList())
                .map { it.normalizedPhoneNumber }
                .toSet()
            val inserted = uniqueEntries.keys.count { number -> number !in existingNumbers }
            val overwritten = uniqueEntries.keys.count { number -> number in existingNumbers }
            dao.upsertAll(uniqueEntries.values.toList())
            RestoreLocalLabelsResult(
                inserted = inserted,
                overwritten = overwritten,
                skipped = skipped,
            )
        }
    }

    /**
     * 生成可作为主键的归一化号码。
     *
     * 空结果或不含数字的结果视为无效，避免把占位符写入标签表。
     */
    private fun normalize(phoneNumber: String): String? =
        PhoneNumberNormalizer.normalizeForLookup(phoneNumber)
            .trim()
            .takeIf { normalized -> normalized.isNotEmpty() && normalized.any(Char::isDigit) }
}
