package vip.mystery0.pixel.telo.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class SyncResponse(
    @SerialName("has_update")
    val hasUpdate: Boolean,
    @SerialName("latest_version")
    val latestVersion: String,
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("size_bytes")
    val sizeBytes: Long,
    @SerialName("checksum")
    val checksum: String,
    @SerialName("row_count")
    val rowCount: Long = 0
)

@Serializable
data class PhoneLocationInfo(
    @SerialName("cardType")
    val cardType: String = "",
    @SerialName("province")
    val province: String = "",
    @SerialName("city")
    val city: String = ""
)

@Serializable
data class QueryResponse(
    @SerialName("phone")
    val phone: String,
    @SerialName("is_spam")
    val isSpam: Boolean,
    @SerialName("tag")
    val tag: String = "",
    @SerialName("confidence")
    val confidence: Int,
    @SerialName("source")
    val source: String,
    @SerialName("data")
    val data: PhoneLocationInfo? = null
)

interface SyncApi {
    @GET("api/v1/sync/check")
    suspend fun checkUpdate(@Query("current_version") currentVersion: String): SyncResponse

    @GET("api/v1/query")
    suspend fun queryNumber(@Query("number") number: String): QueryResponse
}
