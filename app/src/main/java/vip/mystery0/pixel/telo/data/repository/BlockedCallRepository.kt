package vip.mystery0.pixel.telo.data.repository

import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.FeedbackStatus
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.remote.QueryResponse

class BlockedCallRepository : KoinComponent {
    private val blockedCallDao: BlockedCallDao by inject()

    val allBlockedCalls: Flow<List<BlockedCall>> = blockedCallDao.getAll()

    suspend fun insert(
        phoneNumber: String,
        remark: String?,
        resultType: ResultType = ResultType.INTERCEPT,
        localDuration: Long = 0,
        networkDuration: Long = 0,
        label: String? = null,
        querySource: String? = null,
        feedbackToken: String? = null,
    ) {
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
        blockedCallDao.insert(blockedCall)
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

    suspend fun update(blockedCall: BlockedCall) {
        blockedCallDao.update(blockedCall)
    }

    suspend fun delete(blockedCall: BlockedCall) {
        blockedCallDao.delete(blockedCall)
    }
}
