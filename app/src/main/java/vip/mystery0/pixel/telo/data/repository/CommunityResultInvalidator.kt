package vip.mystery0.pixel.telo.data.repository

import android.content.Context
import androidx.core.net.toUri
import android.util.Log
import vip.mystery0.pixel.telo.BuildConfig
import vip.mystery0.pixel.telo.data.query.CommunityQueryEpoch

/** 本地修改先撤销旧代次，再通知系统重新读取；通知不意味着拨号器必定即时刷新。 */
class CommunityResultInvalidator(
    private val context: Context,
    private val epoch: CommunityQueryEpoch,
    private val spamRepository: SpamNumberRepository,
) {
    suspend fun onOfficialReportChanged() {
        epoch.advance()
        spamRepository.invalidateOfficialQueryReuse()
        try {
            context.contentResolver.notifyChange(
                "content://${BuildConfig.APPLICATION_ID}.provider".toUri(), null,
            )
        } catch (_: Exception) {
            Log.w("CommunityReports", "Directory refresh notification failed")
        }
    }
}
