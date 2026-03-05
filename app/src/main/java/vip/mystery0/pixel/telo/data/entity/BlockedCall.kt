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
    val remark: String?,
    val resultType: ResultType = ResultType.INTERCEPT,
    val localDuration: Long = 0,
    val networkDuration: Long = 0,
)

enum class ResultType {
    INTERCEPT, // 拦截
    PASS_BUT_NOTIFY, // 提示但不拦截 (暂未实现完全逻辑，可用于白名单或低风险)
    NETWORK_TIMEOUT, // 联网查询超时
    PASS // 正常放行并通过设置强制记录
}
