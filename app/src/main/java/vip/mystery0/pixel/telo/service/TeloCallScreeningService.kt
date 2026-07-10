package vip.mystery0.pixel.telo.service

import android.content.SharedPreferences
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.CheckResult
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

class TeloCallScreeningService : CallScreeningService(), KoinComponent {
    companion object {
        private const val TAG = "TeloCallScreeningService"
        private val recentMarkedCalls = mutableMapOf<String, Long>()
    }

    private val blockedCallRepository: BlockedCallRepository by inject()
    private val spamNumberRepository: SpamNumberRepository by inject()
    private val prefs: SharedPreferences by inject()
    private val incomingCallOverlay by lazy { IncomingCallOverlay(this, prefs) }

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: return

        Log.d(TAG, "Incoming call detected: $phoneNumber")

        val response = CallResponse.Builder()
        val notifyOnly = prefs.getBoolean(SettingViewModel.KEY_NOTIFY_ONLY, true)
        val callTime = callDetails.creationTimeMillis.takeIf { it > 0 }
            ?: System.currentTimeMillis()
        var callRejected = false

        runBlocking(Dispatchers.IO) {
            try {
                val result = spamNumberRepository.checkSpam(phoneNumber)
                Log.i(
                    TAG,
                    "Screen result: number=$phoneNumber, shouldBlock=${result.shouldBlock}, " +
                        "notifyOnly=$notifyOnly, resultType=${result.resultType}, " +
                        "forceBlock=${result.forceBlock}, label=${result.label}, " +
                        "localCost=${result.localCost}ms, networkCost=${result.networkCost}ms"
                )

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
                        querySource = result.querySource,
                        feedbackToken = result.feedbackToken
                    )
                } else if (shouldSilenceRepeatMarkedCall(phoneNumber, callTime, result)) {
                    val repeatLabel = result.label.ifBlank { "骚扰电话" }
                    response.setDisallowCall(false)
                    response.setRejectCall(false)
                    response.setSilenceCall(true)
                    response.setSkipCallLog(false)

                    blockedCallRepository.insert(
                        phoneNumber,
                        remark = "$repeatLabel（重复来电，静音展示）",
                        ResultType.PASS_BUT_NOTIFY,
                        result.localCost,
                        result.networkCost,
                        label = result.label.takeIf { it.isNotBlank() },
                        querySource = result.querySource,
                        feedbackToken = result.feedbackToken
                    )
                } else if (result.shouldBlock) {
                    if (notifyOnly) {
                        response.setDisallowCall(false)
                        response.setRejectCall(false)
                        response.setSkipCallLog(false)

                        blockedCallRepository.insert(
                            phoneNumber,
                            remark = result.label + " (仅提示)",
                            ResultType.PASS_BUT_NOTIFY,
                            result.localCost,
                            result.networkCost,
                            label = result.label.takeIf { it.isNotBlank() },
                            querySource = result.querySource,
                            feedbackToken = result.feedbackToken
                        )
                    } else {
                        callRejected = true
                        response.setDisallowCall(true)
                        response.setRejectCall(true)
                        response.setSkipCallLog(false)

                        blockedCallRepository.insert(
                            phoneNumber,
                            remark = result.label,
                            ResultType.INTERCEPT,
                            result.localCost,
                            result.networkCost,
                            label = result.label.takeIf { it.isNotBlank() },
                            querySource = result.querySource,
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
                            label = null
                        )
                    } else if (prefs.getBoolean(SettingViewModel.KEY_ALWAYS_RECORD, false)) {
                        blockedCallRepository.insert(
                            phoneNumber,
                            remark = result.label.takeIf { it.isNotBlank() } ?: "正常来电",
                            ResultType.PASS,
                            result.localCost,
                            result.networkCost,
                            label = result.label.takeIf { it.isNotBlank() },
                            querySource = result.querySource,
                            feedbackToken = result.feedbackToken
                        )
                    }
                }
                showLocationOverlayIfNeeded(phoneNumber, result, callRejected)
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

    private fun showLocationOverlayIfNeeded(
        phoneNumber: String,
        result: CheckResult,
        callRejected: Boolean
    ) {
        val enabled = prefs.getBoolean(SettingViewModel.KEY_SHOW_LOCATION_OVERLAY, false)
        val noNetworkQuery = prefs.getBoolean(SettingViewModel.KEY_NO_NETWORK_QUERY, false)
        if (enabled && !noNetworkQuery && !callRejected) {
            incomingCallOverlay.show(phoneNumber, result)
        }
    }

    private fun shouldSilenceRepeatMarkedCall(
        phoneNumber: String,
        callTime: Long,
        result: CheckResult
    ): Boolean {
        if (!result.shouldBlock || result.forceBlock || result.resultType == ResultType.BLACK_LIST) {
            return false
        }

        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        val lastMarkedCallTime = synchronized(recentMarkedCalls) {
            val previous = recentMarkedCalls[normalizedNumber] ?: 0L
            recentMarkedCalls[normalizedNumber] = callTime
            previous
        }

        val enabled = prefs.getBoolean(SettingViewModel.KEY_ALLOW_REPEAT_CALL, false)
        if (!enabled || lastMarkedCallTime <= 0L || callTime < lastMarkedCallTime) {
            return false
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
                "Repeat marked call allowed silently: number=$phoneNumber, " +
                    "window=${windowMinutes}min, interval=${callTime - lastMarkedCallTime}ms"
            )
        }
        return isRepeatCall
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.removePrefix("+86")
    }
}
