package vip.mystery0.pixel.telo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import vip.mystery0.pixel.telo.data.entity.BlockedCall

@Dao
interface BlockedCallDao {
    @Query("SELECT * FROM blocked_calls ORDER BY blockTime DESC")
    fun getAll(): Flow<List<BlockedCall>>

    @Insert
    suspend fun insert(blockedCall: BlockedCall)
}
