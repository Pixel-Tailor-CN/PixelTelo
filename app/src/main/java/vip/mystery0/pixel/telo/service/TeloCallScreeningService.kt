package vip.mystery0.pixel.telo.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class TeloCallScreeningService : CallScreeningService() {
    companion object {
        private const val TAG = "TeloCallScreeningServic"
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // 1. 获取来电号码 (注意：Uri 格式通常是 tel:138000...)
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: return

        Log.d(TAG, "Incoming call detected: $phoneNumber")

        val response = CallResponse.Builder()

        // --- 模拟拦截逻辑 ---
        if (phoneNumber == "10086") {
            Log.d(TAG, "Blocking 10086 for test")
            response.setDisallowCall(true) // 拦截
            response.setRejectCall(true)   // 挂断
            response.setSkipCallLog(false) // 是否跳过通话记录(false表示记录)
        } else {
            Log.d(TAG, "Allowing call")
            response.setDisallowCall(false)
            response.setRejectCall(false)
            response.setSkipCallLog(false)
        }

        // 2. 提交响应给系统
        respondToCall(callDetails, response.build())
    }
}