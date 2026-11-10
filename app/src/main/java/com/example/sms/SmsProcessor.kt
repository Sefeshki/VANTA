package com.example.sms

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import com.example.core.EventLogger
import com.example.core.PreferencesManager
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SimSelectionMode
import com.example.data.model.SmsMessageItem
import com.example.data.queue.DeliveryQueueManager
import com.example.telephony.SimManager
import java.util.UUID

class SmsProcessor(
    private val preferencesManager: PreferencesManager,
    private val simManager: SimManager,
    private val deliveryQueueManager: DeliveryQueueManager
) {
    // Regex for common OTP and authentication tokens in English, Persian, and Arabic
    private val otpPattern = Regex(
        pattern = """(?i)(\b(code|otp|verification|pin|password|security code|verify)\b|رمز|تایید|کد تایید|کد ورود|رمز یکبار مصرف|كلمة المرور|رمز التحقق)""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun processIncomingSmsIntent(intent: Intent, context: Context) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        // Group PDUs by sender and timestamp to handle multipart messages
        val sender = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: "Unknown"
        val timestamp = messages[0].timestampMillis

        val fullBodyBuilder = StringBuilder()
        for (msg in messages) {
            val part = msg.displayMessageBody ?: msg.messageBody ?: ""
            fullBodyBuilder.append(part)
        }
        val fullBody = fullBodyBuilder.toString()

        val simSlot = extractSimSlot(intent, messages[0])
        val isOtp = isSensitiveMessage(fullBody)

        val smsItem = SmsMessageItem(
            id = UUID.randomUUID().toString(),
            sender = sender,
            body = fullBody,
            timestamp = timestamp,
            simSlot = simSlot,
            isOtp = isOtp
        )

        handleProcessedSms(smsItem)
    }

    fun handleProcessedSms(sms: SmsMessageItem) {
        val prefs = preferencesManager.preferences.value

        // 1. Forwarding switch check
        if (!prefs.isForwardingEnabled) {
            EventLogger.log(
                LogCategory.SMS_DELIVERY,
                "Received text from '${sms.sender}' on SIM${sms.simSlot + 1}, but forwarding is turned off",
                LogLevel.INFO
            )
            return
        }

        // 2. SIM Selection Filter Check
        if (sms.simSlot >= 0 && !simManager.isSlotEligible(sms.simSlot)) {
            val slotName = "SIM${sms.simSlot + 1}"
            EventLogger.log(
                LogCategory.SIM,
                "Text from '${sms.sender}' skipped: $slotName is not selected (${prefs.simSelectionMode.label})",
                LogLevel.WARNING
            )
            return
        }

        // 3. Sensitive / OTP Message Protection Filter
        if (sms.isOtp && prefs.isOtpProtectionEnabled) {
            EventLogger.log(
                LogCategory.SECURITY,
                "Code protection active: skipped forwarding login code from '${sms.sender}'",
                LogLevel.WARNING
            )
            return
        }

        // 4. Enqueue for Telegram delivery
        val enqueued = deliveryQueueManager.enqueue(sms)
        if (enqueued) {
            val slotDisplay = if (sms.simSlot >= 0) "SIM${sms.simSlot + 1}" else "SIM?"
            EventLogger.log(
                LogCategory.SMS_DELIVERY,
                "Text from '${sms.sender}' on $slotDisplay queued for Telegram",
                LogLevel.INFO
            )
        }
    }

    private fun extractSimSlot(intent: Intent, firstMessage: SmsMessage): Int {
        // Try SubscriptionManager index extra
        val subIndex = intent.getIntExtra("subscription", -1)
            .takeIf { it != -1 }
            ?: intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1)
                .takeIf { it != -1 }
            ?: intent.getIntExtra("simId", -1)
                .takeIf { it != -1 }
            ?: intent.getIntExtra("slot", -1)

        if (subIndex in 0..1) {
            return subIndex
        }

        // Match against active detected SIMs if subIndex matches subId
        val detected = simManager.detectedSims.value
        val matchBySubId = detected.firstOrNull { it.subId == subIndex }
        if (matchBySubId != null) {
            return matchBySubId.slotIndex
        }

        // Default to SIM 1 (slot 0) if single SIM or undetected
        return if (detected.isNotEmpty()) detected.first().slotIndex else 0
    }

    fun isSensitiveMessage(text: String): Boolean {
        if (otpPattern.containsMatchIn(text)) return true
        // Check for 4 to 8 digit standalone numbers surrounded by common code terms
        val hasDigits = Regex("""[\d\u0660-\u0669\u06F0-\u06F9]{4,8}""").containsMatchIn(text)
        val hasKeyword = text.contains("code", ignoreCase = true) ||
                text.contains("pin", ignoreCase = true) ||
                text.contains("رمز", ignoreCase = true) ||
                text.contains("تایید", ignoreCase = true)
        return hasDigits && hasKeyword
    }
}
