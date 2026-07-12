package vip.mystery0.pixel.telo.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.dao.QuerySourceQuality
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.FeedbackStatus
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.remote.QueryResponse
import vip.mystery0.pixel.telo.smartspacer.SmartspacerIntegration

class BlockedCallRepository : KoinComponent {
    private val blockedCallDao: BlockedCallDao by inject()
    private val context: Context by inject()

    val allBlockedCalls: Flow<List<BlockedCall>> = blockedCallDao.getAll()

    /** 插入拦截记录，返回新记录的自增 id */
    suspend fun insert(
        phoneNumber: String,
        remark: String?,
        resultType: ResultType = ResultType.INTERCEPT,
        localDuration: Long = 0,
        networkDuration: Long = 0,
        label: String? = null,
        querySource: String? = null,
        feedbackToken: String? = null,
    ): Long {
        val token = feedbackToken?.takeIf { it.isNotBlank() }
        val blockedCall = BlockedCall(
            phoneNumber = phoneNumber,
            blockTime = System.currentTimeMillis(),
            remark = remark,
            resultType = resultType,
            localDuration = localDuration,
            networkDuration = networkDuration,
            label = label,
            querySource = querySource?.takeIf { it.isNotBlank() },
            feedbackToken = token,
            feedbackStatus = if (token != null) FeedbackStatus.PENDING else FeedbackStatus.UNAVAILABLE,
        )
        val id = blockedCallDao.insert(blockedCall)
        // 静默拦截（直接挂断且无任何提醒）需要刷新 Smartspacer 计数；
        // notifyChange 仅向系统发送异步通知，不会阻塞来电响应路径
        if (resultType == ResultType.INTERCEPT || resultType == ResultType.BLACK_LIST) {
            SmartspacerIntegration.notifyChanged(context)
        }
        return id
    }

    suspend fun findById(id: Long): BlockedCall? {
        return blockedCallDao.findById(id)
    }

    /**
     * 把联网查询结果的 source 与反馈 token 立即写回记录。
     * 返回更新后的实体，调用方必须基于返回值继续操作，避免旧对象覆盖新字段。
     */
    suspend fun attachQueryResult(call: BlockedCall, response: QueryResponse): BlockedCall {
        val token = response.feedbackToken.takeIf { it.isNotBlank() }
        val updated = call.copy(
            querySource = response.source.takeIf { it.isNotBlank() },
            feedbackToken = token,
            feedbackStatus = if (token != null) FeedbackStatus.PENDING else FeedbackStatus.UNAVAILABLE,
        )
        blockedCallDao.update(updated)
        return updated
    }

    /**
     * 更新记录的反馈状态。
     * 返回更新后的实体，调用方必须基于返回值继续操作，避免旧对象覆盖新字段。
     */
    suspend fun updateFeedbackStatus(call: BlockedCall, status: FeedbackStatus): BlockedCall {
        val updated = call.copy(feedbackStatus = status)
        blockedCallDao.update(updated)
        return updated
    }

    /** 按 source 统计自 since 以来的查询质量数据，key 为 source ID */
    suspend fun getSourceQualityStats(since: Long): Map<String, QuerySourceQuality> {
        return blockedCallDao.getSourceQualityStats(since, FeedbackStatus.NEGATIVE.name)
            .associateBy { it.source }
    }

    suspend fun update(blockedCall: BlockedCall) {
        blockedCallDao.update(blockedCall)
    }

    suspend fun delete(blockedCall: BlockedCall) {
        blockedCallDao.delete(blockedCall)
    }
}
