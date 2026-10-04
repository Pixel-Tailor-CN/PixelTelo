package vip.mystery0.pixel.telo.data.repository

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.query.QueryBackendRevokedException
import vip.mystery0.pixel.telo.data.query.SelfHostedCompatibilityException

/** 稳定错误码优先，旧服务端、代理 HTML 与未知错误码按 HTTP 状态兜底，不匹配异常正文。 */
fun classifyNetworkFailure(exception: Throwable): ResultType = when (exception) {
    is TimeoutCancellationException,
    is SocketTimeoutException -> ResultType.NETWORK_TIMEOUT
    is BackendBlockedException,
    is QueryBackendRevokedException,
    is SelfHostedCompatibilityException -> ResultType.QUERY_SERVICE_UNAVAILABLE
    is QueryResponseFormatException -> ResultType.NETWORK_INVALID_RESPONSE
    is QueryApiException -> when (exception.errorCode) {
        QueryErrorCode.INVALID_REQUEST -> ResultType.NETWORK_INVALID_REQUEST
        QueryErrorCode.UPSTREAM_RATE_LIMITED -> ResultType.NETWORK_RATE_LIMITED
        QueryErrorCode.UPSTREAM_TIMEOUT -> ResultType.NETWORK_UPSTREAM_TIMEOUT
        QueryErrorCode.UPSTREAM_UNAVAILABLE -> ResultType.NETWORK_UPSTREAM_UNAVAILABLE
        QueryErrorCode.FEEDBACK_TOKEN_FAILED -> ResultType.NETWORK_FEEDBACK_ERROR
        QueryErrorCode.INTERNAL_ERROR -> ResultType.NETWORK_SERVER_ERROR
        null -> when (exception.code) {
            400, 422 -> ResultType.NETWORK_INVALID_REQUEST
            401, 403 -> ResultType.NETWORK_AUTH_ERROR
            408 -> ResultType.NETWORK_TIMEOUT
            429 -> ResultType.NETWORK_RATE_LIMITED
            502, 503 -> ResultType.NETWORK_UPSTREAM_UNAVAILABLE
            504 -> ResultType.NETWORK_UPSTREAM_TIMEOUT
            else -> ResultType.NETWORK_SERVER_ERROR
        }
    }
    is BackendQueryException -> ResultType.NETWORK_SERVER_ERROR
    is IOException -> ResultType.NETWORK_CONNECTION_ERROR
    else -> ResultType.NETWORK_SERVER_ERROR
}
