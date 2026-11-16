package com.example.sms

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.example.core.EventLogger
import com.example.core.PreferencesManager
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SimFeature
import com.example.data.model.SmsMessageItem
import com.example.data.queue.DeliveryQueueManager
import com.example.telephony.SimManager
import java.util.UUID

class SmsProcessor(
    private val preferencesManager: PreferencesManager,
    private val simManager: SimManager,
    private val deliveryQueueManager: DeliveryQueueManager
) {
    private val otpPattern = Regex(
        pattern = """(?i)(\b(code|otp|verification|pin|password|security code|verify)\b|رمز|تایید|کد تایید|کد ورود|رمز یکبار مصرف|كلمة المرور|رمز التحقق)""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun processIncomingSmsIntent(intent: Intent, context: Context) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        val sender = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: "Unknown"
        val timestamp = messages[0].timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()

        val fullBodyBuilder = StringBuilder()
        for (msg in messages) {
            val part = msg.displayMessageBody ?: msg.messageBody ?: ""
            fullBodyBuilder.append(part)
        }
        val fullBody = fullBodyBuilder.toString()

        val (simSlot, subId) = extractSimRouting(intent)
        val isOtp = isSensitiveMessage(fullBody)

        val smsItem = SmsMessageItem(
            id = UUID.randomUUID().toString(),
            sender = sender,
            body = fullBody,
            timestamp = timestamp,
            simSlot = simSlot,
            subId = subId,
            isOtp = isOtp
        )

        handleProcessedSms(smsItem)
    }

    fun handleProcessedSms(sms: SmsMessageItem): Boolean {
        val prefs = preferencesManager.preferences.value

        // 1. Main bridge power check
        if (!prefs.isForwardingEnabled) {
            EventLogger.log(
                LogCategory.SMS_DELIVERY,
                "Received SMS from '${sms.sender}', but forwarding is turned off",
                LogLevel.INFO
            )
            return false
        }

        // 2. Global OTP Protection shield
        if (sms.isOtp && prefs.isOtpProtectionEnabled) {
            EventLogger.log(
                LogCategory.SECURITY,
                "OTP Protection active: blocked verification message from '${sms.sender}'",
                LogLevel.WARNING
            )
            return false
        }

        // Resolve slot index if only subId was provided
        val effectiveSlot = when {
            sms.simSlot in 0..1 -> sms.simSlot
            sms.subId >= 0 -> simManager.resolveSlotFromSubId(sms.subId)
            else -> -1
        }
        val slotName = if (effectiveSlot in 0..1) "SIM ${effectiveSlot + 1}" else "Unknown SIM"

        // 2. Per-SIM master switch & SMS forwarding check
        if (effectiveSlot in 0..1) {
            val simSettings = prefs.simControl.forSlot(effectiveSlot)
            if (!simSettings.masterEnabled) {
                EventLogger.log(
                    LogCategory.SIM,
                    "SMS from '${sms.sender}' skipped: $slotName master switch is OFF",
                    LogLevel.WARNING
                )
                return false
            }
            if (!simSettings.smsForwardingEnabled) {
                EventLogger.log(
                    LogCategory.SIM,
                    "SMS from '${sms.sender}' skipped: SMS Forwarding disabled on $slotName",
                    LogLevel.WARNING
                )
                return false
            }
            // 3. Per-SIM OTP / Verification Messages filter
            if (sms.isOtp && !simSettings.otpMessagesEnabled) {
                EventLogger.log(
                    LogCategory.SECURITY,
                    "OTP filter active on $slotName: skipped forwarding verification message from '${sms.sender}'",
                    LogLevel.WARNING
                )
                return false
            }
        } else {
            // Unknown slot fallback: check if any SIM allows SMS (and OTP if OTP)
            val sim1Allow = prefs.simControl.sim1.isFeatureEffective(SimFeature.SMS_FORWARDING) &&
                (!sms.isOtp || prefs.simControl.sim1.isFeatureEffective(SimFeature.OTP_MESSAGES))
            val sim2Allow = prefs.simControl.sim2.isFeatureEffective(SimFeature.SMS_FORWARDING) &&
                (!sms.isOtp || prefs.simControl.sim2.isFeatureEffective(SimFeature.OTP_MESSAGES))
            if (!sim1Allow && !sim2Allow) {
                EventLogger.log(
                    LogCategory.SIM,
                    "SMS from '${sms.sender}' skipped by SIM filtering rules",
                    LogLevel.WARNING
                )
                return false
            }
        }

        // 4. Enqueue via single DeliveryQueueManager pipeline (preserving duplicate protection)
        val normalizedSms = if (sms.simSlot != effectiveSlot) sms.copy(simSlot = effectiveSlot) else sms
        val enqueued = deliveryQueueManager.enqueue(normalizedSms)
        if (enqueued) {
            EventLogger.log(
                LogCategory.SMS_DELIVERY,
                "SMS from '${sms.sender}' on $slotName queued for Telegram",
                LogLevel.INFO
            )
        }
        return enqueued
    }

    /**
     * Resolves (slotIndex, subId) from SMS_RECEIVED intent extras without assuming subId == slot + 1.
     */
    fun extractSimRouting(intent: Intent): Pair<Int, Int> {
        val rawSubId = intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            .takeIf { it >= 0 }
            ?: intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                .takeIf { it >= 0 }
            ?: intent.getIntExtra("sub_id", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                .takeIf { it >= 0 }
            ?: intent.getIntExtra("subscription_id", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                .takeIf { it >= 0 }
            ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID

        if (rawSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            var resolvedSlot = simManager.resolveSlotFromSubId(rawSubId)
            if (resolvedSlot !in 0..1) {
                simManager.refreshSims()
                resolvedSlot = simManager.resolveSlotFromSubId(rawSubId)
            }
            if (resolvedSlot in 0..1) {
                return resolvedSlot to rawSubId
            }
        }

        val explicitSlot = intent.getIntExtra("android.telephony.extra.SLOT_INDEX", -1)
            .takeIf { it in 0..1 }
            ?: intent.getIntExtra("slot", -1).takeIf { it in 0..1 }
            ?: intent.getIntExtra("simId", -1).takeIf { it in 0..1 }
            ?: intent.getIntExtra("phone", -1).takeIf { it in 0..1 }

        if (explicitSlot != null) {
            val simForSlot = simManager.getSimForSlot(explicitSlot)
            val resolvedSubId = if (rawSubId >= 0) rawSubId else (simForSlot?.subId ?: -1)
            return explicitSlot to resolvedSubId
        }

        // Fallback if only one active SIM is available on device
        val availableSims = simManager.detectedSims.value.filter { it.isAvailable }
        if (availableSims.size == 1) {
            val onlySim = availableSims.first()
            return onlySim.slotIndex to (if (rawSubId >= 0) rawSubId else onlySim.subId)
        }

        return -1 to rawSubId
    }

    fun isSensitiveMessage(text: String): Boolean {
        if (otpPattern.containsMatchIn(text)) return true
        val hasDigits = Regex("""[\d\u0660-\u0669\u06F0-\u06F9]{4,8}""").containsMatchIn(text)
        val hasKeyword = text.contains("code", ignoreCase = true) ||
            text.contains("pin", ignoreCase = true) ||
            text.contains("رمز", ignoreCase = true) ||
            text.contains("تایید", ignoreCase = true)
        return hasDigits && hasKeyword
    }
}
