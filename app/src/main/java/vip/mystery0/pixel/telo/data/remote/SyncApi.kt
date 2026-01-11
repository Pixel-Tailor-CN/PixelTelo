package vip.mystery0.pixel.telo.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

@Serializable
data class SyncResponse(
    @SerialName("latest_version")
    val latestVersion: Int,
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("size_bytes")
    val sizeBytes: Long,
    @SerialName("checksum")
    val checksum: String
)

interface SyncApi {
    @GET("api/v1/sync/check")
    suspend fun checkUpdate(): SyncResponse
}
