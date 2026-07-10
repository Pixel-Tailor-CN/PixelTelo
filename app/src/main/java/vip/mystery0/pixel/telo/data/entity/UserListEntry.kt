package vip.mystery0.pixel.telo.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户自定义黑白名单条目。
 * phoneNumber 可以是具体号码（精确匹配）或前缀（前缀匹配）。
 * tagMatch 为 true 时，phoneNumber 字段存储标签名称而非号码。
 */
@Entity(
    tableName = "user_list",
    indices = [Index(
        value = ["phoneNumber", "listType", "tagMatch", "locationMatch"],
        unique = true
    )]
)
data class UserListEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 号码或前缀，如 "13800138000" 或 "400"；tagMatch=true 时为标签名 */
    val phoneNumber: String,
    /** true = 前缀匹配，false = 精确匹配 */
    val isPrefix: Boolean,
    /** BLACK 或 WHITE */
    val listType: ListType,
    /** 可选备注 */
    val remark: String?,
    /** 添加时间戳，默认为当前时间 */
    val addedAt: Long = System.currentTimeMillis(),
    /** true = 标签匹配，false = 号码匹配 */
    val tagMatch: Boolean = false,
    /** true = 归属地匹配，依赖联网查询结果 */
    val locationMatch: Boolean = false,
    /**
     * 仅黑名单有效：true 表示命中时忽略“仅提示不拦截”与“短时间重复来电”设置，
     * 直接挂断来电；false 表示遵循全局设置。白名单条目恒为 false。
     */
    val forceBlock: Boolean = false,
)

enum class ListType { BLACK, WHITE }
