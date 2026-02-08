package vip.mystery0.pixel.telo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 备份文件的根结构，序列化为 ZIP 内的 backup.json
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    @SerialName("exported_at") val exportedAt: Long,
    val records: List<BlockedCallDto>
)

/**
 * 拦截记录的数据传输对象，用于跨版本序列化
 */
@Serializable
data class BlockedCallDto(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("block_time") val blockTime: Long,
    val remark: String? = null,
    @SerialName("result_type") val resultType: String,
    @SerialName("local_duration") val localDuration: Long = 0,
    @SerialName("network_duration") val networkDuration: Long = 0
)
