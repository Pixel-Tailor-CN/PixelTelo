package vip.mystery0.pixel.telo.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

/** 所有操作固定发送至官方，JSON body 中的安装 ID 不能进入 URL 或日志。 */
interface OfficialCommunityReportApi {
    @POST("api/v2/community/reports")
    suspend fun submit(@Body request: ReportSubmitRequest): ReportSubmitResponse

    @POST("api/v2/community/reports/list")
    suspend fun list(@Body request: ReportListRequest): ReportListResponse

    @POST("api/v2/community/reports/withdraw")
    suspend fun withdraw(@Body request: ReportActionRequest): ReportWithdrawResponse

    @POST("api/v2/community/reports/delete")
    suspend fun delete(@Body request: ReportActionRequest): ReportDeleteResponse
}
