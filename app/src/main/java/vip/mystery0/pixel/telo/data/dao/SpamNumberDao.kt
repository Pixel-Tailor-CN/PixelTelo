package vip.mystery0.pixel.telo.data.dao

import androidx.room.Dao
import androidx.room.Query
import vip.mystery0.pixel.telo.data.entity.SpamNumberEntity

@Dao
interface SpamNumberDao {
    @Query("SELECT * FROM spam_numbers WHERE phone_number = :phoneNumber LIMIT 1")
    fun search(phoneNumber: String): SpamNumberEntity?
}
