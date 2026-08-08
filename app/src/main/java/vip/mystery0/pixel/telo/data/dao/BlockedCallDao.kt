package vip.mystery0.pixel.telo.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.paging.PagingSource
import vip.mystery0.pixel.telo.data.entity.BlockedCall

/** 单个 source 在统计窗口内的查询质量统计 */
data class QuerySourceQuality(
    val source: String,
    /** 联网查询命中的去重号码数 */
    val phoneCount: Int,
    /** 用户标记“结果不准确”的记录数 */
    val negativeCount: Int,
)

@Dao
interface BlockedCallDao {
    @Query("SELECT * FROM blocked_calls ORDER BY blockTime DESC")
    fun getPagingSource(): PagingSource<Int, BlockedCall>

    @Query("SELECT * FROM blocked_calls")
    suspend fun getAllSnapshot(): List<BlockedCall>

    @Query("SELECT * FROM blocked_calls WHERE phoneNumber = :phoneNumber AND blockTime = :blockTime LIMIT 1")
    suspend fun findByKey(phoneNumber: String, blockTime: Long): BlockedCall?

    @Query("SELECT * FROM blocked_calls WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): BlockedCall?

    /** 按 source 统计自 since 以来联网命中的去重号码数与指定反馈状态的记录数 */
    @Query(
        """
        SELECT querySource AS source,
               COUNT(DISTINCT phoneNumber) AS phoneCount,
               SUM(CASE WHEN feedbackStatus = :negativeStatus THEN 1 ELSE 0 END) AS negativeCount
        FROM blocked_calls
        WHERE blockTime >= :since AND querySource IS NOT NULL
        GROUP BY querySource
        """
    )
    suspend fun getSourceQualityStats(since: Long, negativeStatus: String): List<QuerySourceQuality>

    /** 统计自 since 之后、指定结果类型的拦截记录数量（resultType 按枚举 name 存储） */
    @Query("SELECT COUNT(*) FROM blocked_calls WHERE blockTime > :since AND resultType IN (:resultTypes)")
    suspend fun countByResultTypesSince(since: Long, resultTypes: List<String>): Int

    @Insert
    suspend fun insert(blockedCall: BlockedCall): Long

    @Update
    suspend fun update(blockedCall: BlockedCall)

    /**
     * 仅当反馈归属、Token 与状态仍和调用方读取时一致，才局部更新反馈字段。
     *
     * `IS` 提供 null-safe 比较，避免旧实体在 retry 写入新 Backend 后整行覆盖最新归属。
     */
    @Query(
        """
        UPDATE blocked_calls
        SET feedbackToken = :newFeedbackToken,
            feedbackStatus = :newFeedbackStatus
        WHERE id = :id
          AND queryBackendId IS :expectedQueryBackendId
          AND feedbackToken IS :expectedFeedbackToken
          AND feedbackStatus = :expectedFeedbackStatus
        """
    )
    suspend fun compareAndSetFeedbackState(
        id: Long,
        expectedQueryBackendId: String?,
        expectedFeedbackToken: String?,
        expectedFeedbackStatus: String,
        newFeedbackToken: String?,
        newFeedbackStatus: String,
    ): Int

    @Delete
    suspend fun delete(blockedCall: BlockedCall)
}
