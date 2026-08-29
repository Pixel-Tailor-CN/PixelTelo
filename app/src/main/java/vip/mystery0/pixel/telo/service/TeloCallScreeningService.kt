package vip.mystery0.pixel.telo.service

import android.content.SharedPreferences
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.PhoneNumberNormalizer
import vip.mystery0.pixel.telo.data.entity.ResultType
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
        private val recentMarkedCalls = mutableMapOf<String, Long>()

        /** 本地标签并行查询的有界超时，超时按无标签处理，不得拖慢 checkSpam。 */
        private const val LOCAL_LABEL_LOOKUP_TIMEOUT_MS = 100L
    }

    private val blockedCallRepository: BlockedCallRepository by inject()
    private val spamNumberRepository: SpamNumberRepository by inject()
    private val localNumberLabelRepository: LocalNumberLabelRepository by inject()
    private val localNumberLabelPreferences: LocalNumberLabelPreferences by inject()
    private val prefs: SharedPreferences by inject()
    private val incomingCallOverlay: IncomingCallOverlay by inject()

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: return

        Log.d(TAG, "Incoming call detected")

        val response = CallResponse.Builder()
        val notifyOnly = prefs.getBoolean(SettingViewModel.KEY_NOTIFY_ONLY, true)
        val callTime = callDetails.creationTimeMillis.takeIf { it > 0 }
            ?: System.currentTimeMillis()
        var callRejected = false

        runBlocking(Dispatchers.IO) {
            try {
                coroutineScope {
                    val shouldLoadOverlayLabel =
                        localNumberLabelPreferences.enabled.value &&
                            prefs.getBoolean(SettingViewModel.KEY_SHOW_LOCATION_OVERLAY, false) &&
                            !prefs.getBoolean(SettingViewModel.KEY_NO_NETWORK_QUERY, false) &&
                            Settings.canDrawOverlays(applicationContext)
                    val localLabelDeferred = if (shouldLoadOverlayLabel) {
                        async {
                            withTimeoutOrNull(LOCAL_LABEL_LOOKUP_TIMEOUT_MS) {
                                loadOverlayLocalLabel(phoneNumber)
                            }
                        }
                    } else {
                        null
                    }
                    val result = spamNumberRepository.checkSpam(phoneNumber)
                    Log.i(
                        TAG,
                        "Screen result: shouldBlock=${result.shouldBlock}, " +
                            "notifyOnly=$notifyOnly, resultType=${result.resultType}, " +
                            "forceBlock=${result.forceBlock}, " +
                            "localCost=${result.localCost}ms, networkCost=${result.networkCost}ms"
                    )
                    val repeatStrategy = repeatCallStrategy(phoneNumber, callTime, result)

                    if (result.shouldBlock && result.forceBlock) {
                        callRejected = true
                        response.setDisallowCall(true)
                        response.setRejectCall(true)
                        response.setSkipCallLog(false)

                        blockedCallRepository.insert(
                            phoneNumber,
                            remark = result.label,
                            ResultType.BLACK_LIST,
                            result.localCost,
                            result.networkCost,
                            label = result.label.takeIf { it.isNotBlank() },
                            province = result.locationInfo?.province,
                            city = result.locationInfo?.city,
                            querySource = result.querySource,
                            queryBackendId = result.queryBackendId,
                            feedbackToken = result.feedbackToken
                        )
                    } else if (repeatStrategy != null) {
                        val repeatLabel = result.label.ifBlank { "骚扰电话" }
                        response.setDisallowCall(false)
                        response.setRejectCall(false)
                        response.setSilenceCall(repeatStrategy == RepeatCallStrategy.SILENCE)
                        response.setSkipCallLog(false)

                        val recordId = blockedCallRepository.insert(
                            phoneNumber,
                            remark = when (repeatStrategy) {
                                RepeatCallStrategy.SILENCE -> "$repeatLabel（重复来电，静音放行）"
                                RepeatCallStrategy.ALLOW -> "$repeatLabel（重复来电，完全放行）"
                                RepeatCallStrategy.UNCHANGED -> error("Unreachable strategy")
                            },
                            if (repeatStrategy == RepeatCallStrategy.ALLOW) {
                                ResultType.PASS
                            } else {
                                ResultType.PASS_BUT_NOTIFY
                            },
                            result.localCost,
                            result.networkCost,
                            label = result.label.takeIf { it.isNotBlank() },
                            province = result.locationInfo?.province,
                            city = result.locationInfo?.city,
                            querySource = result.querySource,
                            queryBackendId = result.queryBackendId,
                            feedbackToken = result.feedbackToken
                        )
                        markFeedbackPromptIfEligible(recordId, result)
                    } else if (result.shouldBlock) {
                        if (notifyOnly) {
                            response.setDisallowCall(false)
                            response.setRejectCall(false)
                            response.setSkipCallLog(false)

                            val recordId = blockedCallRepository.insert(
                                phoneNumber,
                                remark = result.label + " (仅提示)",
                                ResultType.PASS_BUT_NOTIFY,
                                result.localCost,
                                result.networkCost,
                                label = result.label.takeIf { it.isNotBlank() },
                                province = result.locationInfo?.province,
                                city = result.locationInfo?.city,
                                querySource = result.querySource,
                                queryBackendId = result.queryBackendId,
                                feedbackToken = result.feedbackToken
                            )
                            markFeedbackPromptIfEligible(recordId, result)
                        } else {
                            callRejected = true
                            response.setDisallowCall(true)
                            response.setRejectCall(true)
                            response.setSkipCallLog(false)

                            // 记录类型沿用检查结果，号码黑名单拒接时忠实记为 BLACK_LIST
                            blockedCallRepository.insert(
                                phoneNumber,
                                remark = result.label,
                                result.resultType,
                                result.localCost,
                                result.networkCost,
                                label = result.label.takeIf { it.isNotBlank() },
                                province = result.locationInfo?.province,
                                city = result.locationInfo?.city,
                                querySource = result.querySource,
                                queryBackendId = result.queryBackendId,
                                feedbackToken = result.feedbackToken
                            )
                        }
                    } else {
                        response.setDisallowCall(false)
                        response.setRejectCall(false)
                        response.setSkipCallLog(false)

                        if (result.resultType == ResultType.NETWORK_TIMEOUT) {
                            blockedCallRepository.insert(
                                phoneNumber,
                                remark = "Network Timeout (Allowed)",
                                result.resultType,
                                result.localCost,
                                result.networkCost,
                                label = null,
                                queryBackendId = result.queryBackendId,
                            )
                        } else if (prefs.getBoolean(SettingViewModel.KEY_ALWAYS_RECORD, false)) {
                            val recordId = blockedCallRepository.insert(
                                phoneNumber,
                                remark = result.label.takeIf { it.isNotBlank() } ?: "正常来电",
                                ResultType.PASS,
                                result.localCost,
                                result.networkCost,
                                label = result.label.takeIf { it.isNotBlank() },
                                province = result.locationInfo?.province,
                                city = result.locationInfo?.city,
                                querySource = result.querySource,
                                queryBackendId = result.queryBackendId,
                                feedbackToken = result.feedbackToken
                            )
                            markFeedbackPromptIfEligible(recordId, result)
                        }
                    }
                    showLocationOverlayIfNeeded(
                        phoneNumber = phoneNumber,
                        result = result,
                        localLabel = awaitLocalLabelOrNull(localLabelDeferred),
                        callRejected = callRejected,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking spam, allowing call", e)
                response.setDisallowCall(false)
                response.setRejectCall(false)
                response.setSkipCallLog(false)
            } finally {
                respondToCall(callDetails, response.build())
            }
        }
    }

    /**
     * 放行且记录持有反馈 token 时写入待提醒标记，
     * 通话结束（回到 IDLE）后由 CallStateReceiver 兑现为反馈询问通知。
     * 被拒接的来电不响铃，不进入此路径。
     */
    private fun markFeedbackPromptIfEligible(recordId: Long, result: CheckResult) {
        if (result.queryBackendId != OFFICIAL_BACKEND_ID) return
        if (result.feedbackToken.isNullOrBlank()) return
        QueryFeedbackNotifier.markPendingFeedback(this, prefs, recordId)
    }

    /** 有界等待本地标签，超时返回 null，不进入外层拦截错误路径。 */
    private suspend fun awaitLocalLabelOrNull(deferred: Deferred<String?>?): String? {
        if (deferred == null) return null
        return try {
            withTimeoutOrNull(LOCAL_LABEL_LOOKUP_TIMEOUT_MS) {
                deferred.await()
            }
        } catch (exception: CancellationException) {
            throw exception
        } finally {
            if (deferred.isActive) {
                deferred.cancel()
            }
        }
    }

    /** 本地标签查询失败只隐藏 Overlay 标签，不得进入外层 catch 改变来电响应。 */
    private suspend fun loadOverlayLocalLabel(phoneNumber: String): String? {
        return try {
            localNumberLabelRepository.find(phoneNumber)?.label
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            Log.w(TAG, "Local label lookup failed")
            null
        }
    }

    private fun showLocationOverlayIfNeeded(
        phoneNumber: String,
        result: CheckResult,
        localLabel: String?,
        callRejected: Boolean
    ) {
        val enabled = prefs.getBoolean(SettingViewModel.KEY_SHOW_LOCATION_OVERLAY, false)
        val noNetworkQuery = prefs.getBoolean(SettingViewModel.KEY_NO_NETWORK_QUERY, false)
        if (enabled && !noNetworkQuery && !callRejected) {
            incomingCallOverlay.show(phoneNumber, result, localLabel)
        }
    }

    private fun repeatCallStrategy(
        phoneNumber: String,
        callTime: Long,
        result: CheckResult
    ): RepeatCallStrategy? {
        if (!result.shouldBlock || result.forceBlock || result.resultType == ResultType.BLACK_LIST) {
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
