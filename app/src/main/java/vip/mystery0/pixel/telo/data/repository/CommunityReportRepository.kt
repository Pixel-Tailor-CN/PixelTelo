package vip.mystery0.pixel.telo.data.repository

import android.os.SystemClock
import android.util.Log
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import vip.mystery0.pixel.telo.data.PhoneNumberNormalizer
import vip.mystery0.pixel.telo.data.ReportDeviceIdentityStore
import vip.mystery0.pixel.telo.data.ReportIdentityUnavailableException
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.model.*
import vip.mystery0.pixel.telo.data.query.OFFICIAL_BACKEND_ID
import vip.mystery0.pixel.telo.data.remote.*

/** 官方上报及设备管理的单一入口，设备 ID 不进入 ViewModel 或 SavedState。 */
class CommunityReportRepository(
    private val api: OfficialCommunityReportApi,
    private val identityStore: ReportDeviceIdentityStore,
    private val calls: BlockedCallRepository,
    private val invalidator: CommunityResultInvalidator,
) {
    // 串行化设备管理和身份重建，避免请求执行期间改变请求所属身份。
    private val operationMutex = Mutex()
    private val cooldowns = mutableMapOf<String, Long>()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    suspend fun submit(callId: Long): ReportResult<CommunitySubmission> = operation("submit", true) { id ->
        val call = calls.findById(callId) ?: fail(ReportFailureKind.NOT_FOUND)
        reportEligibility(call)?.let { throw ReportOperationException(it) }
        val token = call.feedbackToken ?: fail(ReportFailureKind.INVALID_TOKEN)
        var dispatched = false
        try {
            dispatched = true
            val response = api.submit(ReportSubmitRequest(id, token))
            val report = response.report.toModel(mutation = true)
            val expectedPhone = call.feedbackPhone ?: PhoneNumberNormalizer.normalizeForLookup(call.phoneNumber)
            if (report.phone != expectedPhone || report.status != CommunityReportStatus.ACTIVE) {
                fail(ReportFailureKind.INVALID_RESPONSE, true)
            }
            saveTokenState(call, ReportTokenState.ACCEPTED)
            CommunitySubmission(report, response.created)
        } catch (e: HttpException) {
            val reason = classifyReportHttpFailure(e, true)
            val state = when (reason.kind) {
                ReportFailureKind.INVALID_TOKEN -> ReportTokenState.INVALID
                ReportFailureKind.EXPIRED_TOKEN -> ReportTokenState.EXPIRED
                ReportFailureKind.USED_TOKEN -> ReportTokenState.USED
                else -> null
            }
            if (state != null) saveTokenState(call, state)
            throw ReportOperationException(reason)
        } finally {
            // 响应丢失／取消也可能已经落库，保守失效本地增强而不声称云端成功。
            if (dispatched) invalidateAfterMutation()
        }
    }

    suspend fun list(cursor: String? = null, limit: Int = 20): ReportResult<CommunityReportPage> =
        operation("list", false) { id ->
            if (limit !in 1..100 || (cursor != null && (cursor.isBlank() || cursor.length > 256))) {
                fail(ReportFailureKind.INVALID_REQUEST)
            }
            val response = api.list(ReportListRequest(id, cursor, limit))
            if (response.items.size > limit || response.nextCursor?.let { it.isBlank() || it.length > 256 } == true) {
                fail(ReportFailureKind.INVALID_RESPONSE)
            }
            CommunityReportPage(response.items.map { it.toModel() }, response.nextCursor)
        }

    suspend fun withdraw(report: CommunityReport): ReportResult<CommunityReport> =
        operation("withdraw", true) { id ->
            try {
                val updated = api.withdraw(ReportActionRequest(id, report.reportId)).report.toModel(mutation = true)
                if (updated.reportId != report.reportId || updated.phone != report.phone ||
                    updated.status != CommunityReportStatus.WITHDRAWN) {
                    fail(ReportFailureKind.INVALID_RESPONSE, true)
                }
                updated
            } finally {
                invalidateAfterMutation()
            }
        }

    suspend fun delete(reportId: String): ReportResult<Unit> = operation("delete", true) { id ->
        if (api.delete(ReportActionRequest(id, reportId)).status != "deleted") {
            fail(ReportFailureKind.INVALID_RESPONSE, true)
        }
    }

    /** 无正在执行的设备请求才允许重建；重建失败不静默切换身份。 */
    suspend fun resetIdentityAfterConfirmation(): ReportResult<Unit> = withContext(Dispatchers.IO) {
        if (!operationMutex.tryLock()) return@withContext ReportResult.Failure(ReportFailure(ReportFailureKind.LOCAL_STATE))
        _busy.value = true
        try {
            // 先清除用途提示：重建失败只会多一次服务端校验，不会把新身份误当旧身份。
            calls.clearReportTokenStates()
            identityStore.resetAfterConfirmation()
            cooldowns.clear()
            ReportResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: ReportIdentityUnavailableException) {
            ReportResult.Failure(ReportFailure(ReportFailureKind.IDENTITY_UNAVAILABLE))
        } catch (_: Exception) {
            ReportResult.Failure(ReportFailure(ReportFailureKind.LOCAL_STATE))
        } finally {
            _busy.value = false
            operationMutex.unlock()
        }
    }

    private suspend fun <T> operation(
        name: String,
        mutation: Boolean,
        block: suspend (String) -> T,
    ): ReportResult<T> = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val remaining = (cooldowns[name] ?: 0) - SystemClock.elapsedRealtime()
            if (remaining > 0) return@withLock ReportResult.Failure(
                ReportFailure(ReportFailureKind.RATE_LIMITED, remaining),
            )
            _busy.value = true
            val result = try {
                val identity = identityStore.getOrCreate()
                ReportResult.Success(block(identity.value))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReportOperationException) {
                ReportResult.Failure(e.reason)
            } catch (_: ReportIdentityUnavailableException) {
                ReportResult.Failure(ReportFailure(ReportFailureKind.IDENTITY_UNAVAILABLE))
            } catch (e: HttpException) {
                ReportResult.Failure(classifyReportHttpFailure(e, mutation))
            } catch (_: SerializationException) {
                ReportResult.Failure(ReportFailure(ReportFailureKind.INVALID_RESPONSE, mayHaveCommitted = mutation))
            } catch (_: IOException) {
                ReportResult.Failure(ReportFailure(ReportFailureKind.NETWORK, mayHaveCommitted = mutation))
            } catch (_: Exception) {
                ReportResult.Failure(ReportFailure(ReportFailureKind.INTERNAL, mayHaveCommitted = mutation))
            } finally {
                _busy.value = false
            }
            if (result is ReportResult.Failure && result.reason.kind == ReportFailureKind.RATE_LIMITED) {
                cooldowns[name] = SystemClock.elapsedRealtime() + (result.reason.retryAfterMillis ?: 1000L)
            }
            result
        }
    }

    private suspend fun saveTokenState(call: BlockedCall, state: ReportTokenState) = withContext(NonCancellable) {
        try {
            calls.updateReportTokenState(call, state)
        } catch (_: Exception) {
            // 云端已接受时，本地落库失败不能伪报为未提交。
            Log.w("CommunityReports", "Local report token state update failed")
        }
    }

    private suspend fun invalidateAfterMutation() = withContext(NonCancellable) {
        invalidator.onOfficialReportChanged()
    }
}

/** 本地资格提示独立于质量反馈；最终期限和消费校验仍由服务端决定。 */
fun reportEligibility(call: BlockedCall): ReportFailure? {
    if (call.queryBackendId != OFFICIAL_BACKEND_ID || call.feedbackToken.isNullOrBlank()) {
        return ReportFailure(ReportFailureKind.INVALID_TOKEN)
    }
    val state = parseReportTokenState(call.reportTokenState)
    val kind = when (state) {
        ReportTokenState.ACCEPTED, ReportTokenState.USED -> ReportFailureKind.USED_TOKEN
        ReportTokenState.EXPIRED -> ReportFailureKind.EXPIRED_TOKEN
        ReportTokenState.INVALID -> ReportFailureKind.INVALID_TOKEN
        ReportTokenState.UNKNOWN -> if (call.feedbackTokenExpiresAt?.let { it <= System.currentTimeMillis() } == true)
            ReportFailureKind.EXPIRED_TOKEN else null
    }
    return kind?.let { ReportFailure(it) }
}

private class ReportOperationException(val reason: ReportFailure) : Exception("Community operation failed")
private fun fail(kind: ReportFailureKind, uncertain: Boolean = false): Nothing =
    throw ReportOperationException(ReportFailure(kind, mayHaveCommitted = uncertain))

private fun CommunityReportDto.toModel(mutation: Boolean = false): CommunityReport {
    if (reportId.isBlank() || reportId.length > 128 || phone.isBlank() || phone.length > 64) {
        fail(ReportFailureKind.INVALID_RESPONSE, mutation)
    }
    val reportStatus = when (status) {
        "active" -> CommunityReportStatus.ACTIVE
        "withdrawn" -> CommunityReportStatus.WITHDRAWN
        else -> CommunityReportStatus.UNKNOWN
    }
    fun parseTime(value: String): Instant = runCatching {
        // RFC3339 使用四位年份，同时保证展示时转换本地时区不会溢出。
        require(value.length >= 20 && value.take(4).all { it in '0'..'9' } && value[4] == '-')
        Instant.parse(value)
    }.getOrNull() ?: fail(ReportFailureKind.INVALID_RESPONSE, mutation)
    val created = parseTime(reportedAt)
    val withdrawn = withdrawnAt?.let(::parseTime)
    if (reportStatus == CommunityReportStatus.WITHDRAWN && withdrawn == null) {
        fail(ReportFailureKind.INVALID_RESPONSE, mutation)
    }
    val community = parseCommunityStatus(communityStatus).let {
        if (it == CommunityStatus.UNKNOWN) CommunityStatus.UNAVAILABLE else it
    }
    return CommunityReport(reportId, phone, reportStatus, created, withdrawn, community)
}
