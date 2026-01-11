package vip.mystery0.pixel.telo.data.dao

import androidx.room.Dao
import androidx.room.Query

@Dao
interface MastDao {
    @Query("SELECT value FROM metadata WHERE `key` = 'version' LIMIT 1")
    suspend fun getVersion(): String?
}
