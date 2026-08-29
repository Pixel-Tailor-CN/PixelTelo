package vip.mystery0.pixel.telo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 用户为具体归一化号码设置的持久化本地标签。 */
@Entity(tableName = "local_number_labels")
data class LocalNumberLabel(
    @PrimaryKey val normalizedPhoneNumber: String,
    val label: String,
    val createdAt: Long,
    val updatedAt: Long,
)
