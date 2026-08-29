package vip.mystery0.pixel.telo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 当前备份格式版本。新导出必须显式写入该值，不能依赖 data class 缺省。 */
const val CURRENT_BACKUP_VERSION = 5

/**
 * 备份文件根结构，序列化为 ZIP 内的 backup.json。
 * version 2 起包含黑白名单数据；version 3 起黑名单条目包含 force_block 字段；
 * version 4 起拦截记录包含省份和城市；version 5 起包含本地号码标签。
 * 旧版 v1–v4 缺少 local_number_labels 时按空列表处理。
 *
 * [version] 的 Kotlin 缺省值为 1：旧归档若省略 version 按 v1 解码，以保留
 * v1/v2 黑名单 force_block 回填。新导出必须显式写入 [CURRENT_BACKUP_VERSION]；
 * Json 未开启 encodeDefaults，等于缺省值的字段会被省略。
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    @SerialName("exported_at") val exportedAt: Long,
    val records: List<BlockedCallDto> = emptyList(),
    @SerialName("black_list") val blackList: List<UserListEntryDto> = emptyList(),
    @SerialName("white_list") val whiteList: List<UserListEntryDto> = emptyList(),
    @SerialName("local_number_labels") val localNumberLabels: List<LocalNumberLabelDto> = emptyList(),
)

/**
 * 拦截记录的数据传输对象
 */
@Serializable
data class BlockedCallDto(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("block_time") val blockTime: Long,
    val remark: String? = null,
    @SerialName("result_type") val resultType: String,
    @SerialName("local_duration") val localDuration: Long = 0,
    @SerialName("network_duration") val networkDuration: Long = 0,
    val label: String? = null,
    val province: String? = null,
    val city: String? = null,
)

/**
 * 黑白名单条目的数据传输对象
 */
@Serializable
data class UserListEntryDto(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("is_prefix") val isPrefix: Boolean,
    val remark: String? = null,
    @SerialName("added_at") val addedAt: Long,
    @SerialName("tag_match") val tagMatch: Boolean = false,
    @SerialName("location_match") val locationMatch: Boolean = false,
    @SerialName("force_block") val forceBlock: Boolean = false,
)

/**
 * 本地号码标签的数据传输对象。
 * phone_number 写入归一化号码；恢复时仍会重新归一化。
 */
@Serializable
data class LocalNumberLabelDto(
    @SerialName("phone_number") val phoneNumber: String,
    val label: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)
