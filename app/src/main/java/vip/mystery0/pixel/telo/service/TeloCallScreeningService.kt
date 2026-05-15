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
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository
import vip.mystery0.pixel.telo.viewmodel.SettingViewModel

class TeloCallScreeningService : CallScreeningService(), KoinComponent {
    companion object {
        private const val TAG = "TeloCallScreeningService"
    }

    private val blockedCallRepository: BlockedCallRepository by inject()
    private val spamNumberRepository: SpamNumberRepository by inject()
    private val prefs: SharedPreferences by inject()

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: return

        Log.d(TAG, "Incoming call detected: $phoneNumber")

        val response = CallResponse.Builder()
        val notifyOnly = prefs.getBoolean(SettingViewModel.KEY_NOTIFY_ONLY, true)

        runBlocking(Dispatchers.IO) {
            try {
                val result = spamNumberRepository.checkSpam(phoneNumber)
                Log.i(
                    TAG,
                    "Screen result: number=$phoneNumber, shouldBlock=${result.shouldBlock}, " +
                        "notifyOnly=$notifyOnly, resultType=${result.resultType}, " +
                        "label=${result.label}, localCost=${result.localCost}ms, " +
                        "networkCost=${result.networkCost}ms"
                )

                // Decide response based on result type
                if (result.shouldBlock) {
                    if (notifyOnly) {
                        // Pass but record as PASS_BUT_NOTIFY
                        response.setDisallowCall(false)
                        response.setRejectCall(false)
                        response.setSkipCallLog(false)

                        blockedCallRepository.insert(
                            phoneNumber,
                            remark = result.label + " (仅提示)",
                            ResultType.PASS_BUT_NOTIFY,
                            result.localCost,
                            result.networkCost,
                            label = result.label.takeIf { it.isNotBlank() }
                        )
                    } else {
                        // Real Block
                        response.setDisallowCall(true)
                        response.setRejectCall(true)
                        response.setSkipCallLog(false)

                        blockedCallRepository.insert(
                            phoneNumber,
                            remark = result.label,
                            ResultType.INTERCEPT,
                            result.localCost,
                            result.networkCost,
                            label = result.label.takeIf { it.isNotBlank() }
                        )
                    }
                } else {
                    // Allowed
                    response.setDisallowCall(false)
                    response.setRejectCall(false)
                    response.setSkipCallLog(false)

                    // If TIMEOUT, we still record it for diagnostics as requested
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
                            label = result.label.takeIf { it.isNotBlank() }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking spam, allowing call", e)
                // Fallback
                response.setDisallowCall(false)
                response.setRejectCall(false)
                response.setSkipCallLog(false)
            } finally {
                respondToCall(callDetails, response.build())
            }
        }
    }
}
