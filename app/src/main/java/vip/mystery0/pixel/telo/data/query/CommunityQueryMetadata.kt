package vip.mystery0.pixel.telo.data.query

import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import vip.mystery0.pixel.telo.data.model.CommunityStatus
import vip.mystery0.pixel.telo.data.remote.QueryResponse

data class CommunityQueryMetadata(
    val status: CommunityStatus,
    val feedbackPhone: String?,
    val feedbackTokenExpiresAt: Long?,
)

/** 新增字段单独容错，不能因可选增强损坏而拒绝原本有效的查询结果。 */
fun decodeCommunityQueryMetadata(response: QueryResponse, official: Boolean): CommunityQueryMetadata {
    if (!official) return CommunityQueryMetadata(CommunityStatus.UNKNOWN, null, null)
    val community = response.community
    val rawStatus = ((community as? JsonObject)?.get("status") as? JsonPrimitive)
        ?.takeIf { it.isString }?.content
    val status = when {
        community == null || community == JsonNull -> CommunityStatus.UNKNOWN
        rawStatus == "active" -> CommunityStatus.ACTIVE
        rawStatus == "inactive" -> CommunityStatus.INACTIVE
        else -> CommunityStatus.UNAVAILABLE
    }
    val tokenPresent = !response.feedbackToken.isNullOrBlank()
    val expiry = (response.feedbackTokenExpiresAt as? JsonPrimitive)?.takeIf { it.isString }
        ?.content?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    return CommunityQueryMetadata(
        status,
        response.phone.takeIf { tokenPresent },
        expiry.takeIf { tokenPresent },
    )
}
