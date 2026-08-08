package vip.mystery0.pixel.telo.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.dao.QuerySourceQuality
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.FeedbackStatus
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.query.BackendQueryResponse
import vip.mystery0.pixel.telo.data.query.OFFICIAL_BACKEND_ID
import vip.mystery0.pixel.telo.smartspacer.SmartspacerIntegration

class BlockedCallRepository : KoinComponent {
    private val blockedCallDao: BlockedCallDao by inject()
    private val context: Context by inject()

    val blockedCallsPager: Flow<PagingData<BlockedCall>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            initialLoadSize = 30,
            prefetchDistance = 10,
            maxSize = 90,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = blockedCallDao::getPagingSource,
    ).flow

    /** 插入拦截记录，返回新记录的自增 id */
    suspend fun insert(
        phoneNumber: String,
        remark: String?,
        resultType: ResultType = ResultType.INTERCEPT,
        localDuration: Long = 0,
        networkDuration: Long = 0,
        label: String? = null,
        province: String? = null,
        city: String? = null,
        querySource: String? = null,
        queryBackendId: String? = null,
        feedbackToken: String? = null,
    ): Long {
        val backendId = queryBackendId?.takeIf { it.isNotBlank() }
        val token = feedbackToken?.takeIf {
            backendId == OFFICIAL_BACKEND_ID && it.isNotBlank()
        }
        val blockedCall = BlockedCall(
            phoneNumber = phoneNumber,
            blockTime = System.currentTimeMillis(),
            remark = remark,
            resultType = resultType,
            localDuration = localDuration,
            networkDuration = networkDuration,
            label = label,
            province = province.cleanLocationPart(),
            city = city.cleanLocationPart(),
            querySource = querySource?.takeIf { it.isNotBlank() },
            queryBackendId = backendId,
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
     * 把联网查询结果的 Backend、source 与反馈 token 立即写回记录。
     * 只有明确归属于官方且快照声明支持反馈的结果才能保留 token；自建结果无条件清除反馈能力。
     * 返回更新后的实体，调用方必须基于返回值继续操作，避免旧对象覆盖新字段。
     */
    suspend fun attachQueryResult(
        call: BlockedCall,
        result: BackendQueryResponse,
    ): BlockedCall {
        val response = result.response
        val feedbackAllowed = result.backendId == OFFICIAL_BACKEND_ID && result.feedbackSupported
        val token = response.feedbackToken?.takeIf { feedbackAllowed && it.isNotBlank() }
        val updated = call.copy(
            province = response.data?.province.cleanLocationPart() ?: call.province,
            city = response.data?.city.cleanLocationPart() ?: call.city,
            querySource = response.source.takeIf { it.isNotBlank() },
            queryBackendId = result.backendId,
            feedbackToken = token,
            feedbackStatus = if (token != null) FeedbackStatus.PENDING else FeedbackStatus.UNAVAILABLE,
        ).enforceFeedbackOwnership()
        blockedCallDao.update(updated)
        return updated
    }

    /**
     * 以调用方读取时的 Backend、Token 与状态为期望值，原子更新记录的反馈字段。
     * 若 retry 已写入新的 Backend 归属，条件更新会失败并返回数据库中的最新实体，
     * 从而避免旧官方反馈请求整行恢复已经失效的官方归属。
     */
    suspend fun updateFeedbackStatus(call: BlockedCall, status: FeedbackStatus): BlockedCall {
        val updated = call.copy(
            feedbackToken = call.feedbackToken.takeUnless { status == FeedbackStatus.UNAVAILABLE },
            feedbackStatus = status,
        ).enforceFeedbackOwnership()
        blockedCallDao.compareAndSetFeedbackState(
            id = call.id,
            expectedQueryBackendId = call.queryBackendId,
            expectedFeedbackToken = call.feedbackToken,
            expectedFeedbackStatus = call.feedbackStatus.name,
            newFeedbackToken = updated.feedbackToken,
            newFeedbackStatus = updated.feedbackStatus.name,
        )
        return blockedCallDao.findById(call.id) ?: updated
    }

    /** 按指定 Backend 和 source 统计自 since 以来的查询质量数据，key 为 source ID。 */
    suspend fun getSourceQualityStats(
        since: Long,
        backendId: String,
    ): Map<String, QuerySourceQuality> {
        return blockedCallDao.getSourceQualityStats(
            since = since,
            backendId = backendId,
            officialBackendId = OFFICIAL_BACKEND_ID,
            negativeStatus = FeedbackStatus.NEGATIVE.name,
        )
            .associateBy { it.source }
    }

    suspend fun update(blockedCall: BlockedCall) {
        blockedCallDao.update(blockedCall.enforceFeedbackOwnership())
    }

    suspend fun delete(blockedCall: BlockedCall) {
        blockedCallDao.delete(blockedCall)
    }
}

private fun String?.cleanLocationPart(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** 所有通用更新路径都必须维持“仅官方记录可反馈”的持久化不变量。 */
private fun BlockedCall.enforceFeedbackOwnership(): BlockedCall {
    if (queryBackendId != OFFICIAL_BACKEND_ID) {
        return copy(
            feedbackToken = null,
            feedbackStatus = FeedbackStatus.UNAVAILABLE,
        )
    }
    if (feedbackStatus == FeedbackStatus.PENDING && feedbackToken.isNullOrBlank()) {
        return copy(
            feedbackToken = null,
            feedbackStatus = FeedbackStatus.UNAVAILABLE,
        )
    }
    return this
}
