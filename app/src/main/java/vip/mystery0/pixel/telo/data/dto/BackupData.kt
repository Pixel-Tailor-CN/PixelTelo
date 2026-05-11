package vip.mystery0.pixel.telo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 备份文件根结构，序列化为 ZIP 内的 backup.json。
 * version 2 起包含黑白名单数据。
 */
@Serializable
data class BackupData(
    val version: Int = 2,
    @SerialName("exported_at") val exportedAt: Long,
    val records: List<BlockedCallDto> = emptyList(),
    @SerialName("black_list") val blackList: List<UserListEntryDto> = emptyList(),
    @SerialName("white_list") val whiteList: List<UserListEntryDto> = emptyList(),
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
)
