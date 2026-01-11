package vip.mystery0.pixel.telo.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spam_numbers")
data class SpamNumberEntity(
    @PrimaryKey
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,
    @ColumnInfo(name = "tag", defaultValue = "")
    val tag: String,
    @ColumnInfo(name = "source", defaultValue = "")
    val source: String,
)
