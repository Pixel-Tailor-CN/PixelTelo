package vip.mystery0.pixel.telo.service

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.PhoneNumberNormalizer
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.entity.isQueryFailure
import vip.mystery0.pixel.telo.data.model.CommunityStatus
import vip.mystery0.pixel.telo.data.preferences.LocalNumberLabelPreferences
import vip.mystery0.pixel.telo.data.query.OFFICIAL_BACKEND_ID
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.CheckResult
import vip.mystery0.pixel.telo.data.repository.LocalNumberLabelRepository
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository
import vip.mystery0.pixel.telo.receiver.QueryFeedbackNotifier
import vip.mystery0.pixel.telo.viewmodel.RepeatCallStrategy
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

class TeloCallScreeningService : CallScreeningService(), KoinComponent {
    companion object {
        private const val TAG = "TeloCallScreeningService"
        private const val SCREENING_RESPONSE_BUDGET_MS = 4_500L
        private const val LOCAL_LABEL_LOOKUP_TIMEOUT_MS = 100L
        private val recentMarkedCalls = mutableMapOf<String, Long>()

        // 系统响应之后的记录和展示不依赖筛查 Service 的存活，避免解绑时取消落库。
        private val postResponseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val blockedCallRepository: BlockedCallRepository by inject()
    private val spamNumberRepository: SpamNumberRepository by inject()
    private val localNumberLabelRepository: LocalNumberLabelRepository by inject()
    private val localNumberLabelPreferences: LocalNumberLabelPreferences by inject()
    private val prefs: SharedPreferences by inject()
    private val incomingCallOverlay: IncomingCallOverlay by inject()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // 系统回调、截止回调和最终响应均串行在主线程执行，只允许取得一次响应权。
    private val pendingCalls = linkedSetOf<PendingCall>()

    override fun onScreenCall(callDetails: Call.Details) {
        val startedAt = SystemClock.elapsedRealtime()
        val pending = PendingCall(callDetails, startedAt)
        pendingCalls.add(pending)
        if (pending.hasResponseDeadline) {
            mainHandler.postDelayed(pending.deadlineAction, remainingBudget(pending))
        }

        // 截止回调先注册，再在 IO 线程初始化依赖和查询，首次冷启动也计入系统预算。
        // 框架在去电回调返回后立即解绑；去电识别必须独立于 Service 生命周期。
        val scope = if (pending.hasResponseDeadline) queryScope else postResponseScope
        pending.queryJob = scope.launch {
            try {
                val number = pending.phoneNumber ?: return@launch
                val result = spamNumberRepository.checkSpam(number)
                mainHandler.post { finishCall(pending, result) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                Log.e(TAG, "Screening lookup failed, allowing call")
                mainHandler.post { finishCall(pending, null) }
            }
        }.also { job ->
            // 覆盖协程尚未进入查询便被取消、以及没有可用号码的路径。
            job.invokeOnCompletion { mainHandler.post { finishCall(pending, null) } }
        }
    }

    override fun onDestroy() {
        // 先响应尚未完成的来电，再取消查询；不等待非协作的 Binder 或 SQLite 返回。
        pendingCalls.filter { it.hasResponseDeadline }.forEach { finishCall(it, null) }
        queryScope.cancel()
        super.onDestroy()
    }

    private fun remainingBudget(pending: PendingCall): Long =
        (pending.deadlineAtElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    private fun finishCall(pending: PendingCall, checked: CheckResult?) {
        if (!pendingCalls.remove(pending)) return
        mainHandler.removeCallbacks(pending.deadlineAction)
        pending.queryJob?.cancel()
        val expired = pending.hasResponseDeadline && remainingBudget(pending) == 0L
        var decision = try {
            when {
                expired -> timeoutDecision()
                checked != null -> buildDecision(pending, spamNumberRepository.currentCommunityResult(checked))
                else -> failOpenDecision()
            }
        } catch (_: Exception) {
            Log.e(TAG, "Screening decision failed, allowing call")
            failOpenDecision()
        }
        if (decision.rejected && decision.result.communityBlockCandidate) {
            val current = spamNumberRepository.currentCommunityResult(decision.result)
            if (!current.shouldBlock) {
                decision = decision.copy(
                    result = current,
                    response = failOpenDecision().response,
                    rejected = false,
                    historyType = ResultType.PASS,
                    remark = getString(R.string.call_remark_normal),
                )
            }
        }
        // 依赖冷初始化或本地规则也计入预算；截止兜底不读取 DI、偏好或数据库。
        if (pending.hasResponseDeadline && remainingBudget(pending) == 0L &&
            decision.historyType != ResultType.SCREENING_TIMEOUT
        ) {
            decision = timeoutDecision()
        }
        try {
            // 去电由框架在 onScreenCall 返回时自动应答；其后只保留识别与后处理。
            if (pending.hasResponseDeadline) respondToCall(pending.details, decision.response)
        } catch (_: Exception) {
            // 无法确认系统接收到响应时，不把拟拒接决定写成已拒接历史。
            Log.e(TAG, "Screening response failed, skipping decision history")
            return
        }
        Log.i(TAG, "Screening processed: incoming=${pending.hasResponseDeadline}, rejected=${decision.rejected}, " +
            "resultType=${decision.historyType}, localCost=${decision.result.localCost}ms, " +
            "networkCost=${decision.result.networkCost}ms")
        val number = pending.phoneNumber ?: return
        postResponseScope.launch {
            try {
                persistDecision(number, decision, pending)
            } catch (_: Exception) {
                Log.w(TAG, "Screening decision history update failed")
            }
            try {
                showLocationOverlayIfNeeded(number, decision, pending.session)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                Log.w(TAG, "Screening overlay update failed")
            }
        }
    }

    private fun failOpenDecision(): ScreeningDecision = ScreeningDecision(
        result = CheckResult(false, "", ResultType.PASS, 0, 0),
        response = CallResponse.Builder().setDisallowCall(false).setRejectCall(false)
            .setSkipCallLog(false).build(),
        rejected = false,
        historyType = ResultType.PASS,
        remark = "Screening Failed (Allowed)",
        shouldRecord = false,
    )

    private fun timeoutDecision(): ScreeningDecision {
        Log.w(TAG, "Screening response budget exceeded, allowing call")
        return failOpenDecision().copy(
            result = CheckResult(false, "", ResultType.SCREENING_TIMEOUT, 0, 0),
            historyType = ResultType.SCREENING_TIMEOUT,
            remark = "Screening Response Timeout (Allowed)",
            shouldRecord = true,
        )
    }

    private fun buildDecision(pending: PendingCall, result: CheckResult): ScreeningDecision {
        val response = CallResponse.Builder().setDisallowCall(false)
            .setRejectCall(false).setSkipCallLog(false)
        val notifyOnly = prefs.getBoolean(SettingViewModel.KEY_NOTIFY_ONLY, true)
        val displayLabel = result.label.ifBlank {
            if (result.communityStatus == CommunityStatus.ACTIVE) getString(R.string.community_label)
            else ""
        }
        val repeated = pending.phoneNumber?.let {
            repeatCallStrategy(it, pending.callTime, result)
        }
        val rejected = result.shouldBlock && (result.forceBlock || (!notifyOnly && repeated == null))
        response.setDisallowCall(rejected).setRejectCall(rejected)
        if (repeated != null) response.setSilenceCall(repeated == RepeatCallStrategy.SILENCE)
        val historyType = when {
            rejected -> result.resultType
            repeated == RepeatCallStrategy.SILENCE -> ResultType.PASS_BUT_NOTIFY
            repeated == RepeatCallStrategy.ALLOW -> ResultType.PASS
            result.shouldBlock -> ResultType.PASS_BUT_NOTIFY
            result.resultType.isQueryFailure() -> result.resultType
            else -> ResultType.PASS
        }
        val remark = when {
            repeated != null -> getString(
                if (repeated == RepeatCallStrategy.SILENCE) R.string.call_remark_repeat_silence
                else R.string.call_remark_repeat_allow,
                displayLabel.ifBlank { getString(R.string.call_label_spam_default) },
            )
            rejected -> displayLabel
            result.shouldBlock -> getString(R.string.call_remark_notify_only, displayLabel)
            result.resultType.isQueryFailure() -> queryFailureRemark(result.resultType)
            else -> displayLabel.ifBlank { getString(R.string.call_remark_normal) }
        }
        return ScreeningDecision(
            result = result,
            response = response.build(),
            rejected = rejected,
            historyType = historyType,
            remark = remark,
            shouldRecord = result.shouldBlock || result.resultType.isQueryFailure() ||
                prefs.getBoolean(SettingViewModel.KEY_ALWAYS_RECORD, false),
        )
    }

    private suspend fun persistDecision(number: String, decision: ScreeningDecision, pending: PendingCall) {
        if (!decision.shouldRecord) return
        val result = decision.result
        val recordId = blockedCallRepository.insert(
            phoneNumber = number,
            remark = decision.remark,
            resultType = decision.historyType,
            localDuration = result.localCost,
            networkDuration = result.networkCost,
            label = result.label.takeIf(String::isNotBlank),
            province = result.locationInfo?.province,
            city = result.locationInfo?.city,
            querySource = result.querySource,
            queryBackendId = result.queryBackendId,
            feedbackToken = result.feedbackToken,
            feedbackPhone = result.feedbackPhone,
            feedbackTokenExpiresAt = result.feedbackTokenExpiresAt,
            communityStatus = result.communityStatus,
            communityBlockApplied = pending.hasResponseDeadline && decision.rejected && result.communityBlockCandidate,
        )
        if (!decision.rejected && result.queryBackendId == OFFICIAL_BACKEND_ID &&
            !result.feedbackToken.isNullOrBlank()
        ) {
            val alreadyEnded = CallPostProcessingCoordinator.registerFeedback(
                pending.session, applicationContext, prefs, recordId,
            )
            if (alreadyEnded) {
                blockedCallRepository.findById(recordId)?.let { call ->
                    QueryFeedbackNotifier.showFeedbackPromptIfEligible(applicationContext, prefs, call)
                }
            }
        }
    }

    private suspend fun showLocationOverlayIfNeeded(
        number: String,
        decision: ScreeningDecision,
        session: CallPostProcessingCoordinator.Session,
    ) {
        if (!CallPostProcessingCoordinator.mayShowOverlay(session) || decision.rejected ||
            !prefs.getBoolean(SettingViewModel.KEY_SHOW_LOCATION_OVERLAY, false) ||
            prefs.getBoolean(SettingViewModel.KEY_NO_NETWORK_QUERY, false) ||
            !Settings.canDrawOverlays(applicationContext)
        ) return
        val label = if (localNumberLabelPreferences.enabled.value) {
            withTimeoutOrNull(LOCAL_LABEL_LOOKUP_TIMEOUT_MS) {
                localNumberLabelRepository.find(number)?.label
            }
        } else null
        incomingCallOverlay.show(number, decision.result, label, session)
    }

    /** 所有失败历史使用稳定英文分类，不落库异常正文或服务端响应。 */
    private fun queryFailureRemark(resultType: ResultType): String = when (resultType) {
        ResultType.LOCAL_TIMEOUT -> "Local Lookup Timeout (Allowed)"
        ResultType.SCREENING_TIMEOUT -> "Screening Response Timeout (Allowed)"
        ResultType.NETWORK_TIMEOUT -> "Network Timeout (Allowed)"
        ResultType.NETWORK_RATE_LIMITED -> "Query Rate Limited (Allowed)"
        ResultType.NETWORK_SERVER_ERROR -> "Query Server Error (Allowed)"
        ResultType.NETWORK_INVALID_REQUEST -> "Invalid Query Request (Allowed)"
        ResultType.NETWORK_AUTH_ERROR -> "Query Authentication Error (Allowed)"
        ResultType.NETWORK_INVALID_RESPONSE -> "Invalid Query Response (Allowed)"
        ResultType.NETWORK_UPSTREAM_TIMEOUT -> "Upstream Timeout (Allowed)"
        ResultType.NETWORK_UPSTREAM_UNAVAILABLE -> "Upstream Unavailable (Allowed)"
        ResultType.NETWORK_FEEDBACK_ERROR -> "Feedback Token Error (Allowed)"
        ResultType.NETWORK_CONNECTION_ERROR -> "Network Connection Error (Allowed)"
        ResultType.QUERY_SERVICE_UNAVAILABLE -> "Query Service Unavailable (Allowed)"
        else -> "Query Failed (Allowed)"
    }

    private inner class PendingCall(val details: Call.Details, startedAt: Long) {
        val phoneNumber: String? = details.handle?.schemeSpecificPart
        // 去电仍执行原有查询和记录，不套用只适用于来电的系统响应期限。
        val hasResponseDeadline = details.callDirection != Call.Details.DIRECTION_OUTGOING
        val deadlineAtElapsedMs = if (hasResponseDeadline) startedAt + SCREENING_RESPONSE_BUDGET_MS
            else Long.MAX_VALUE
        val callTime = details.creationTimeMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val session = CallPostProcessingCoordinator.beginCall()
        val deadlineAction = Runnable { finishCall(this, null) }
        var queryJob: Job? = null
    }

    private data class ScreeningDecision(
        val result: CheckResult,
        val response: CallResponse,
        val rejected: Boolean,
        val historyType: ResultType,
        val remark: String,
        val shouldRecord: Boolean,
    )

    private fun repeatCallStrategy(
        phoneNumber: String,
        callTime: Long,
        result: CheckResult
    ): RepeatCallStrategy? {
        if (!result.shouldBlock || result.forceBlock) {
            return null
        }

        val strategy = prefs.getString(SettingViewModel.KEY_REPEAT_CALL_STRATEGY, null)
            ?.let { runCatching { RepeatCallStrategy.valueOf(it) }.getOrNull() }
            ?: if (prefs.getBoolean(SettingViewModel.KEY_ALLOW_REPEAT_CALL, false)) {
                RepeatCallStrategy.SILENCE
            } else {
                RepeatCallStrategy.UNCHANGED
            }
        if (strategy == RepeatCallStrategy.UNCHANGED) return null

        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        val lastMarkedCallTime = synchronized(recentMarkedCalls) {
            val previous = recentMarkedCalls[normalizedNumber] ?: 0L
            recentMarkedCalls[normalizedNumber] = callTime
            previous
        }

        if (lastMarkedCallTime <= 0L || callTime < lastMarkedCallTime) {
            return null
        }

        val windowMinutes = prefs.getInt(
            SettingViewModel.KEY_REPEAT_CALL_WINDOW_MINUTES,
            SettingViewModel.DEFAULT_REPEAT_CALL_WINDOW_MINUTES
        )
        val windowMillis = windowMinutes * 60_000L
        val isRepeatCall = callTime - lastMarkedCallTime <= windowMillis
        if (isRepeatCall) {
            Log.i(
                TAG,
                "Repeat marked call strategy applied: strategy=$strategy, " +
                    "window=${windowMinutes}min, interval=${callTime - lastMarkedCallTime}ms"
            )
        }
        return strategy.takeIf { isRepeatCall }
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        return PhoneNumberNormalizer.normalizeForLookup(phoneNumber)
    }
}
