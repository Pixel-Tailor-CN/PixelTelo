package vip.mystery0.pixel.telo.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

/** 自建服务返回的实例与协议能力信息。 */
@Serializable
data class SelfHostedInfoResponse(
    @SerialName("service")
    val service: String,
    @SerialName("version")
    val version: String,
    @SerialName("api_version")
    val apiVersion: Int,
    @SerialName("instance_id")
    val instanceId: String,
    @SerialName("build_commit")
    val buildCommit: String,
    @SerialName("capabilities")
    val capabilities: List<String>,
)

/** 自建服务专用的连接验证接口。 */
interface SelfHostedApi {
    @GET("api/selfhost/v1/info")
    suspend fun getInfo(): Response<SelfHostedInfoResponse>
}
