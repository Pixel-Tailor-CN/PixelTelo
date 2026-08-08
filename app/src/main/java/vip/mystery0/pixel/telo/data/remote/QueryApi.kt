package vip.mystery0.pixel.telo.data.remote

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@Serializable
data class PhoneLocationInfo(
    @SerialName("cardType")
    val cardType: String = "",
    @SerialName("province")
    val province: String = "",
    @SerialName("city")
    val city: String = "",
)

@Serializable
data class QueryRequest(
    val number: String,
    val sources: List<String>,
)

@Serializable
data class QueryResponse(
    val phone: String,
    @SerialName("is_spam")
    val isSpam: Boolean,
    val tag: String = "",
    val confidence: Int,
    val source: String,
    val data: PhoneLocationInfo? = null,
    @SerialName("feedback_token")
    val feedbackToken: String? = null,
    @SerialName("query_mode")
    val queryMode: String = "v1",
    @SerialName("requested_sources")
    val requestedSources: List<String> = emptyList(),
    @SerialName("effective_sources")
    val effectiveSources: List<String> = emptyList(),
    val warnings: List<QueryWarning> = emptyList(),
)

@Serializable
data class QueryWarning(
    val code: String,
    val message: String = "",
    @SerialName("invalid_sources")
    val invalidSources: List<String> = emptyList(),
)

@Serializable
data class QuerySource(
    val id: String,
    val priority: Int = Int.MAX_VALUE,
)

@Serializable
data class QuerySourcesResponse(
    @SerialName("default_sources")
    val defaultSources: List<String> = emptyList(),
    @SerialName("available_sources")
    val availableSources: List<QuerySource> = emptyList(),
)

/** v2 接口出错时的响应体，字段用于稳定分类而非直接展示原始服务端错误。 */
@Serializable
data class QueryErrorResponse(
    val error: String = "",
    val code: String? = null,
    @SerialName("request_id")
    val requestId: String? = null,
)

@Serializable
data class FeedbackRequest(
    val token: String,
    val positive: Boolean,
)

@Serializable
@Keep
data class FeedbackResponse(
    val status: String,
)

/** 实时号码查询与 source 配置接口，供官方和自建 Backend 共用。 */
interface QueryApi {
    @GET("api/v2/sources")
    suspend fun getSources(): QuerySourcesResponse

    @POST("api/v2/query")
    suspend fun queryNumber(@Body request: QueryRequest): QueryResponse
}

/** 官方实时查询服务专用的反馈接口。 */
interface OfficialFeedbackApi {
    @POST("api/v2/query/feedback")
    suspend fun submitFeedback(@Body request: FeedbackRequest): FeedbackResponse
}
