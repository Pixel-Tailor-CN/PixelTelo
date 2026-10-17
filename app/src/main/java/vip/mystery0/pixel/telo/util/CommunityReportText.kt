package vip.mystery0.pixel.telo.util

import android.content.Context
import androidx.annotation.StringRes
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.model.CommunityStatus
import vip.mystery0.pixel.telo.data.model.ReportFailure
import vip.mystery0.pixel.telo.data.model.ReportFailureKind

/** 仅用于来电详情的资格摘要，不改变提交失败或上报管理页的完整错误含义。 */
fun ReportFailure.detailEligibilityText(context: Context): String {
    val text = context.getString(when (kind) {
        ReportFailureKind.INVALID_TOKEN -> R.string.detail_report_reason_token
        ReportFailureKind.EXPIRED_TOKEN -> R.string.detail_report_reason_expired
        ReportFailureKind.USED_TOKEN -> R.string.detail_report_reason_used
        ReportFailureKind.DEVICE_BLOCKED -> R.string.detail_report_reason_device
        ReportFailureKind.INVALID_REQUEST -> R.string.detail_report_reason_request
        ReportFailureKind.NOT_WITHDRAWN -> R.string.detail_report_reason_withdraw
        ReportFailureKind.NOT_FOUND -> R.string.detail_report_reason_missing
        ReportFailureKind.RATE_LIMITED -> R.string.detail_report_reason_limited
        ReportFailureKind.UNAVAILABLE -> R.string.detail_report_reason_unavailable
        ReportFailureKind.UNSUPPORTED -> R.string.detail_report_reason_unsupported
        ReportFailureKind.NETWORK -> R.string.detail_report_reason_network
        ReportFailureKind.INVALID_RESPONSE -> R.string.detail_report_reason_response
        ReportFailureKind.IDENTITY_UNAVAILABLE -> R.string.detail_report_reason_identity
        ReportFailureKind.LOCAL_STATE -> R.string.detail_report_reason_local
        ReportFailureKind.INTERNAL -> R.string.detail_report_reason_internal
    })
    return if (mayHaveCommitted) "$text\n${context.getString(R.string.community_result_uncertain)}" else text
}

fun ReportFailure.displayText(context: Context): String {
    val text = if (kind == ReportFailureKind.RATE_LIMITED) {
        val seconds = (((retryAfterMillis ?: 1000L) + 999) / 1000).coerceIn(1, 3600).toInt()
        context.resources.getQuantityString(R.plurals.community_error_limited, seconds, seconds)
    } else context.getString(when (kind) {
        ReportFailureKind.INVALID_TOKEN -> R.string.community_error_token
        ReportFailureKind.EXPIRED_TOKEN -> R.string.community_error_expired
        ReportFailureKind.USED_TOKEN -> R.string.community_error_used
        ReportFailureKind.DEVICE_BLOCKED -> R.string.community_error_device_blocked
        ReportFailureKind.INVALID_REQUEST -> R.string.community_error_request
        ReportFailureKind.NOT_WITHDRAWN -> R.string.community_error_withdraw_first
        ReportFailureKind.NOT_FOUND -> R.string.community_error_missing
        ReportFailureKind.UNAVAILABLE -> R.string.community_error_unavailable
        ReportFailureKind.UNSUPPORTED -> R.string.community_error_unsupported
        ReportFailureKind.NETWORK -> R.string.community_error_network
        ReportFailureKind.INVALID_RESPONSE -> R.string.community_error_response
        ReportFailureKind.IDENTITY_UNAVAILABLE -> R.string.community_error_identity
        ReportFailureKind.LOCAL_STATE -> R.string.community_error_local
        else -> R.string.community_error_internal
    })
    return if (mayHaveCommitted) "$text\n${context.getString(R.string.community_result_uncertain)}" else text
}

@StringRes
fun CommunityStatus.textRes(): Int = when (this) {
    CommunityStatus.ACTIVE -> R.string.community_active
    CommunityStatus.INACTIVE -> R.string.community_inactive
    CommunityStatus.UNAVAILABLE -> R.string.community_unavailable
    CommunityStatus.UNKNOWN -> R.string.community_unknown
}
