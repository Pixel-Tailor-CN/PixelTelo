package vip.mystery0.pixel.telo.data.repository

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import vip.mystery0.pixel.telo.data.model.ReportFailure
import vip.mystery0.pixel.telo.data.model.ReportFailureKind

/** 错误正文最多解析 4 KiB；不保存 Throwable、原始 body 或服务器消息。 */
internal fun classifyReportHttpFailure(exception: HttpException, mutation: Boolean): ReportFailure {
    val response = exception.response()
    val code = runCatching {
        response?.errorBody()?.use { body ->
            val source = body.source()
            val bytes = source.readByteArray(4097.coerceAtMost(
                if (source.request(4097)) 4097 else source.buffer.size.toInt(),
            ).toLong())
            if (bytes.size > 4096) null else {
                val obj = Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
                (obj?.get("code") as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
        }
    }.getOrNull()
    val kind = when (exception.code()) {
        400 -> when (code) {
            "INVALID_FEEDBACK_TOKEN" -> ReportFailureKind.INVALID_TOKEN
            else -> ReportFailureKind.INVALID_REQUEST
        }
        403 -> if (code == "REPORT_DEVICE_BLOCKED") ReportFailureKind.DEVICE_BLOCKED
            else ReportFailureKind.INTERNAL
        410 -> if (code == "FEEDBACK_TOKEN_EXPIRED") ReportFailureKind.EXPIRED_TOKEN
            else ReportFailureKind.INVALID_RESPONSE
        409 -> when (code) {
            "REPORT_TOKEN_ALREADY_USED" -> ReportFailureKind.USED_TOKEN
            "REPORT_NOT_WITHDRAWN" -> ReportFailureKind.NOT_WITHDRAWN
            else -> ReportFailureKind.INVALID_RESPONSE
        }
        404 -> if (code == "REPORT_NOT_FOUND") ReportFailureKind.NOT_FOUND else ReportFailureKind.UNSUPPORTED
        429 -> ReportFailureKind.RATE_LIMITED
        503 -> ReportFailureKind.UNAVAILABLE
        else -> ReportFailureKind.INTERNAL
    }
    return ReportFailure(
        kind,
        retryAfterMillis = if (kind == ReportFailureKind.RATE_LIMITED)
            retryAfterMillis(response?.headers()?.get("Retry-After")) else null,
        mayHaveCommitted = mutation && exception.code() >= 500,
    )
}

private fun retryAfterMillis(header: String?): Long {
    val seconds = header?.trim()?.toLongOrNull() ?: runCatching {
        val deadline = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        java.time.Duration.between(Instant.now(), deadline).seconds
    }.getOrDefault(1)
    return seconds.coerceIn(1, 3600) * 1000L
}
