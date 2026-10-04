package vip.mystery0.pixel.telo.util

import androidx.annotation.StringRes
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.ResultType

/** 历史记录、悬浮窗与手动测试共享稳定分类，禁止展示服务端原始异常。 */
@StringRes
fun ResultType.queryFailureSummaryRes(): Int? = when (this) {
    ResultType.NETWORK_TIMEOUT -> R.string.query_failure_timeout
    ResultType.NETWORK_RATE_LIMITED -> R.string.overlay_query_rate_limited
    ResultType.NETWORK_SERVER_ERROR -> R.string.overlay_query_server_error
    ResultType.NETWORK_CONNECTION_ERROR -> R.string.overlay_network_connection_error
    ResultType.QUERY_SERVICE_UNAVAILABLE -> R.string.overlay_query_service_unavailable
    ResultType.NETWORK_INVALID_REQUEST -> R.string.query_failure_invalid_request
    ResultType.NETWORK_AUTH_ERROR -> R.string.query_failure_auth
    ResultType.NETWORK_INVALID_RESPONSE -> R.string.query_failure_invalid_response
    ResultType.NETWORK_UPSTREAM_TIMEOUT -> R.string.query_failure_upstream_timeout
    ResultType.NETWORK_UPSTREAM_UNAVAILABLE -> R.string.query_failure_upstream_unavailable
    ResultType.NETWORK_FEEDBACK_ERROR -> R.string.query_failure_feedback
    else -> null
}

@StringRes
fun ResultType.queryFailureAdviceRes(): Int = when (this) {
    ResultType.NETWORK_TIMEOUT -> R.string.retry_error_network_timeout
    ResultType.NETWORK_RATE_LIMITED -> R.string.retry_error_rate_limited
    ResultType.NETWORK_SERVER_ERROR -> R.string.retry_error_server
    ResultType.NETWORK_CONNECTION_ERROR -> R.string.retry_error_connection
    ResultType.QUERY_SERVICE_UNAVAILABLE -> R.string.retry_error_service_unavailable
    ResultType.NETWORK_INVALID_REQUEST -> R.string.query_advice_invalid_request
    ResultType.NETWORK_AUTH_ERROR -> R.string.query_advice_auth
    ResultType.NETWORK_INVALID_RESPONSE -> R.string.query_advice_invalid_response
    ResultType.NETWORK_UPSTREAM_TIMEOUT -> R.string.query_advice_upstream_timeout
    ResultType.NETWORK_UPSTREAM_UNAVAILABLE -> R.string.query_advice_upstream_unavailable
    ResultType.NETWORK_FEEDBACK_ERROR -> R.string.query_advice_feedback
    else -> R.string.title_query_failed
}
