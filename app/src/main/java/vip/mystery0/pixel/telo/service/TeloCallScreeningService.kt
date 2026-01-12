package vip.mystery0.pixel.telo.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.SpamNumberRepository

class TeloCallScreeningService : CallScreeningService(), KoinComponent {
    companion object {
        private const val TAG = "TeloCallScreeningServic"
    }

    private val blockedCallRepository: BlockedCallRepository by inject()
    private val spamNumberRepository: SpamNumberRepository by inject()

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: return

        Log.d(TAG, "Incoming call detected: $phoneNumber")

        val response = CallResponse.Builder()

        runBlocking(Dispatchers.IO) {
            val (shouldFilter, spamLabel) = spamNumberRepository.checkSpam(phoneNumber)
            if (shouldFilter) {
                response.setDisallowCall(true) // 拦截
                response.setRejectCall(true)   // 挂断
                response.setSkipCallLog(false) // 是否跳过通话记录(false表示记录)

                // 记录拦截信息
                blockedCallRepository.insert(phoneNumber, spamLabel)
            } else {
                response.setDisallowCall(false)
                response.setRejectCall(false)
                response.setSkipCallLog(false)
            }
            respondToCall(callDetails, response.build())
        }
    }
}