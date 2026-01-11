package vip.mystery0.pixel.telo.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository

class TeloCallScreeningService : CallScreeningService(), KoinComponent {
    companion object {
        private const val TAG = "TeloCallScreeningServic"
    }

    private val repository: BlockedCallRepository by inject()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        // 1. 获取来电号码 (注意：Uri 格式通常是 tel:138000...)
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: return

        Log.d(TAG, "Incoming call detected: $phoneNumber")

        val response = CallResponse.Builder()

        response.setDisallowCall(false)
        response.setRejectCall(false)
        response.setSkipCallLog(false)

        respondToCall(callDetails, response.build())

//        if (phoneNumber == "10086") {
//            Log.d(TAG, "Blocking 10086 for test")
//            response.setDisallowCall(true) // 拦截
//            response.setRejectCall(true)   // 挂断
//            response.setSkipCallLog(false) // 是否跳过通话记录(false表示记录)
//
//            // 记录拦截信息
//            scope.launch {
//                repository.insert(phoneNumber, "测试拦截 10086")
//            }
//        } else {
//            Log.d(TAG, "Allowing call")
//            response.setDisallowCall(false)
//            response.setRejectCall(false)
//            response.setSkipCallLog(false)
//        }
//
//        respondToCall(callDetails, response.build())
    }
}