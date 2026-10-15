package vip.mystery0.pixel.telo.data.model

import java.time.Instant

/** 社区结果独立于 Provider 标签与本地备注，未知值不能成为拦截依据。 */
enum class CommunityStatus(val storageValue: String) {
    UNKNOWN("unknown"), ACTIVE("active"), INACTIVE("inactive"), UNAVAILABLE("unavailable")
}

fun CommunityStatus.toStorageValue(): String = storageValue
fun parseCommunityStatus(value: String?): CommunityStatus =
    CommunityStatus.entries.firstOrNull { it.storageValue == value } ?: CommunityStatus.UNKNOWN

enum class ReportTokenState(val storageValue: String) {
    UNKNOWN("unknown"), ACCEPTED("accepted"), USED("used"), EXPIRED("expired"), INVALID("invalid")
}

fun ReportTokenState.toStorageValue(): String = storageValue
fun parseReportTokenState(value: String?): ReportTokenState =
    ReportTokenState.entries.firstOrNull { it.storageValue == value } ?: ReportTokenState.UNKNOWN

enum class CommunityReportStatus { ACTIVE, WITHDRAWN, UNKNOWN }

data class CommunityReport(
    val reportId: String,
    val phone: String,
    val status: CommunityReportStatus,
    val reportedAt: Instant,
    val withdrawnAt: Instant?,
    val communityStatus: CommunityStatus,
)

data class CommunityReportPage(val items: List<CommunityReport>, val nextCursor: String?)
data class CommunitySubmission(val report: CommunityReport, val created: Boolean)

sealed interface ReportResult<out T> {
    data class Success<T>(val value: T) : ReportResult<T>
    data class Failure(val reason: ReportFailure) : ReportResult<Nothing>
}

data class ReportFailure(
    val kind: ReportFailureKind,
    val retryAfterMillis: Long? = null,
    val mayHaveCommitted: Boolean = false,
)

enum class ReportFailureKind {
    INVALID_REQUEST, INVALID_TOKEN, EXPIRED_TOKEN, USED_TOKEN, NOT_WITHDRAWN, NOT_FOUND,
    RATE_LIMITED, UNAVAILABLE, UNSUPPORTED, NETWORK, INVALID_RESPONSE, IDENTITY_UNAVAILABLE,
    LOCAL_STATE, INTERNAL,
}
