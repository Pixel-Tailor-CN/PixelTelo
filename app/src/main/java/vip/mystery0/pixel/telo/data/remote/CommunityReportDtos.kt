package vip.mystery0.pixel.telo.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 仅供官方管理接口使用，不把凭据模型传入 UI。 */
@Serializable
class ReportSubmitRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("feedback_token") val feedbackToken: String,
)

@Serializable
class ReportListRequest(
    @SerialName("device_id") val deviceId: String,
    val cursor: String? = null,
    val limit: Int = 20,
)

@Serializable
class ReportActionRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("report_id") val reportId: String,
)

@Serializable
data class CommunityReportDto(
    @SerialName("report_id") val reportId: String,
    val phone: String,
    val status: String,
    @SerialName("reported_at") val reportedAt: String,
    @SerialName("withdrawn_at") val withdrawnAt: String? = null,
    @SerialName("invalidated_at") val invalidatedAt: String? = null,
    @SerialName("community_status") val communityStatus: String,
)

@Serializable
data class ReportSubmitResponse(val report: CommunityReportDto, val created: Boolean)
@Serializable
data class ReportListResponse(
    val items: List<CommunityReportDto>,
    @SerialName("next_cursor") val nextCursor: String? = null,
)
@Serializable
data class ReportWithdrawResponse(val report: CommunityReportDto)
@Serializable
data class ReportDeleteResponse(val status: String)
