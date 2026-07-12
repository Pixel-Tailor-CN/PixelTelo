package vip.mystery0.pixel.telo.smartspacer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.entity.ResultType

/**
 * Smartspacer 静默拦截计数仓库。
 *
 * 计数口径：自用户上次进入应用（确认基线）以来，被直接挂断且没有任何提醒的来电数量。
 * 只统计 [ResultType.INTERCEPT] 与 [ResultType.BLACK_LIST]，
 * 仅提示（响铃/静音展示）与放行类记录用户自身可感知，不计入。
 */
class SmartspacerInterceptRepository : KoinComponent {
    private val blockedCallDao: BlockedCallDao by inject()
    private val prefs: SharedPreferences by inject()

    /** 静默拦截类型：来电被直接拒接，用户没有任何感知 */
    private val silentResultTypes = listOf(
        ResultType.INTERCEPT.name,
        ResultType.BLACK_LIST.name,
    )

    /** 查询自上次确认基线以来的静默拦截数量 */
    fun getUnseenSilentInterceptCount(): Int {
        val since = prefs.getLong(KEY_ACKNOWLEDGED_TIME, 0L)
        // Smartspacer 的查询在 binder 线程同步执行，count 查询为毫秒级，可安全阻塞
        return runBlocking(Dispatchers.IO) {
            blockedCallDao.countByResultTypesSince(since, silentResultTypes)
        }
    }

    /** 用户进入应用即视为已知晓，重置计数基线并刷新 Smartspacer */
    fun acknowledge(context: Context) {
        prefs.edit { putLong(KEY_ACKNOWLEDGED_TIME, System.currentTimeMillis()) }
        SmartspacerIntegration.notifyChanged(context)
    }

    companion object {
        private const val KEY_ACKNOWLEDGED_TIME = "smartspacer_intercept_acknowledged_time"
    }
}
