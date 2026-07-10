package vip.mystery0.pixel.telo.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
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
    fun getAll(): Flow<List<BlockedCall>>

    @Query("SELECT * FROM blocked_calls")
    suspend fun getAllSnapshot(): List<BlockedCall>

    @Query("SELECT * FROM blocked_calls WHERE phoneNumber = :phoneNumber AND blockTime = :blockTime LIMIT 1")
    suspend fun findByKey(phoneNumber: String, blockTime: Long): BlockedCall?

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

    @Insert
    suspend fun insert(blockedCall: BlockedCall)

    @Update
    suspend fun update(blockedCall: BlockedCall)

    @Delete
    suspend fun delete(blockedCall: BlockedCall)
}
