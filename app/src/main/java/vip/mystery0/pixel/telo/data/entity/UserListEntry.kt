package vip.mystery0.pixel.telo.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户自定义黑白名单条目。
 * phoneNumber 可以是具体号码（精确匹配）或前缀（前缀匹配）。
 */
@Entity(
    tableName = "user_list",
    indices = [Index(value = ["phoneNumber", "listType"], unique = true)]
)
data class UserListEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 号码或前缀，如 "13800138000" 或 "400" */
    val phoneNumber: String,
    /** true = 前缀匹配，false = 精确匹配 */
    val isPrefix: Boolean,
    /** BLACK 或 WHITE */
    val listType: ListType,
    /** 可选备注 */
    val remark: String?,
    /** 添加时间戳，默认为当前时间 */
    val addedAt: Long = System.currentTimeMillis(),
)

enum class ListType { BLACK, WHITE }
