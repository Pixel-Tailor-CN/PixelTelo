package vip.mystery0.pixel.telo.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import vip.mystery0.pixel.telo.data.entity.LocalNumberLabel

/** 持久化本地号码标签的 Room 访问接口。 */
@Dao
interface LocalNumberLabelDao {
    @Query("SELECT * FROM local_number_labels WHERE normalizedPhoneNumber = :number LIMIT 1")
    suspend fun findByNumber(number: String): LocalNumberLabel?

    @Query("SELECT * FROM local_number_labels WHERE normalizedPhoneNumber = :number LIMIT 1")
    fun observeByNumber(number: String): Flow<LocalNumberLabel?>

    @Query("SELECT * FROM local_number_labels WHERE normalizedPhoneNumber IN (:numbers)")
    fun observeByNumbers(numbers: List<String>): Flow<List<LocalNumberLabel>>

    @Query("SELECT * FROM local_number_labels ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LocalNumberLabel>>

    @Query("SELECT * FROM local_number_labels ORDER BY updatedAt DESC")
    suspend fun getAllSnapshot(): List<LocalNumberLabel>

    @Query("SELECT * FROM local_number_labels WHERE normalizedPhoneNumber IN (:numbers)")
    suspend fun findByNumbers(numbers: List<String>): List<LocalNumberLabel>

    @Upsert
    suspend fun upsert(entry: LocalNumberLabel)

    @Upsert
    suspend fun upsertAll(entries: List<LocalNumberLabel>)

    @Query("DELETE FROM local_number_labels WHERE normalizedPhoneNumber = :number")
    suspend fun deleteByNumber(number: String): Int
}
