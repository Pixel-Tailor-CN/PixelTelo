package vip.mystery0.pixel.telo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 骚扰拦截记录实体
 */
@Entity(tableName = "blocked_calls")
data class BlockedCall(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val blockTime: Long,
    val remark: String?
)
