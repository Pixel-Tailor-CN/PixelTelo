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
    /** 来电标签（如"快递送餐"），来自本地/网络查询 */
    val label: String? = null,
    /** 联网查询最终命中的服务端 source，纯本地结果为 null */
    val querySource: String? = null,
    /** 服务端签发的一次性反馈 token，不写入备份与日志 */
    val feedbackToken: String? = null,
    /** 本地反馈状态 */
    val feedbackStatus: FeedbackStatus = FeedbackStatus.UNAVAILABLE,
)

/**
 * 查询结果反馈状态
 */
enum class FeedbackStatus {
    /** 没有可用 token，不能反馈 */
    UNAVAILABLE,

    /** 持有有效 token，可提交反馈 */
    PENDING,

    /** 已提交“结果准确” */
    POSITIVE,

    /** 已提交“结果不准确” */
    NEGATIVE,

    /** 服务端返回 token 已消费，原反馈方向未知 */
    ALREADY_SUBMITTED,

    /** token 已过期 */
    EXPIRED,

    /** token 无效 */
    INVALID,
}

enum class ResultType {
    INTERCEPT, // 拦截
    PASS_BUT_NOTIFY, // 提示但不拦截 (暂未实现完全逻辑，可用于白名单或低风险)
    NETWORK_TIMEOUT, // 联网查询超时
    PASS, // 正常放行并通过设置强制记录
    BLACK_LIST, // 用户黑名单拦截
    WHITE_LIST, // 用户白名单放行
}
