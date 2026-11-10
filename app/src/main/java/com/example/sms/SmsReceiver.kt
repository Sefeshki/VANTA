package com.example.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.VantaApplication
import com.example.core.EventLogger
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val app = context.applicationContext as? VantaApplication
            if (app != null) {
                app.smsProcessor.processIncomingSmsIntent(intent, context)
            } else {
                EventLogger.log(
                    LogCategory.ERROR,
                    "SmsReceiver: VantaApplication context unavailable",
                    LogLevel.ERROR
                )
            }
        } catch (e: Exception) {
            EventLogger.log(
                LogCategory.ERROR,
                "SmsReceiver processing error: ${e.message}",
                LogLevel.ERROR
            )
        }
    }
}
