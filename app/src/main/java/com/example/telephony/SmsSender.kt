package com.example.telephony

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.core.EventLogger
import com.example.core.PreferencesManager
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SimFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

data class SmsSendResult(
    val recipient: String,
    val slotIndex: Int,
    val subId: Int,
    val carrierName: String,
    val partCount: Int
)

interface SmsGateway {
    suspend fun sendSms(
        destination: String,
        messageText: String,
        slotIndex: Int
    ): Result<SmsSendResult>

    fun canReplyToAddress(address: String?): Boolean
}

class AndroidSmsGateway(
    private val context: Context,
    private val simManager: SimManager,
    private val preferencesManager: PreferencesManager
) : SmsGateway {

    override fun canReplyToAddress(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val trimmed = address.trim()
        // Require numeric dialable digits and reject alphanumeric sender IDs like "Google"
        val hasLetters = trimmed.any { it.isLetter() }
        val digitCount = trimmed.count { it.isDigit() }
        return !hasLetters && digitCount >= 3
    }

    override suspend fun sendSms(
        destination: String,
        messageText: String,
        slotIndex: Int
    ): Result<SmsSendResult> {
        if (!canReplyToAddress(destination)) {
            return Result.failure(
                IllegalArgumentException("Invalid SMS destination '$destination'. Alphanumeric sender IDs cannot receive replies.")
            )
        }
        if (messageText.isBlank()) {
            return Result.failure(IllegalArgumentException("Reply message cannot be empty."))
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.failure(SecurityException("SEND_SMS permission is not granted on this device."))
        }

        val simSettings = preferencesManager.preferences.value.simControl.forSlot(slotIndex)
        if (!simSettings.masterEnabled) {
            return Result.failure(IllegalStateException("SIM ${slotIndex + 1} is disabled in SIM Control."))
        }
        if (!simSettings.isFeatureEffective(SimFeature.SMS_SENDING)) {
            return Result.failure(IllegalStateException("SMS Sending / Replies is disabled for SIM ${slotIndex + 1}."))
        }

        simManager.refreshSims()
        val simInfo = simManager.getSimForSlot(slotIndex)
        if (simInfo == null || !simInfo.isAvailable || simInfo.subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID || simInfo.subId < 0) {
            return Result.failure(IllegalStateException("SIM ${slotIndex + 1} is unavailable or not ready."))
        }

        val smsManager = resolveSmsManager(simInfo.subId)
            ?: return Result.failure(IllegalStateException("Unable to acquire SMS manager for SIM ${slotIndex + 1}."))

        val parts = smsManager.divideMessage(messageText)
        if (parts.isNullOrEmpty()) {
            return Result.failure(IllegalArgumentException("Failed to encode SMS payload."))
        }

        val sessionAction = "${ACTION_SMS_SENT_BASE}.${UUID.randomUUID()}"
        val remainingParts = AtomicInteger(parts.size)
        val completion = CompletableDeferred<Result<Unit>>()
        var failureReason: String? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val code = resultCode
                if (code != Activity.RESULT_OK && failureReason == null) {
                    failureReason = describeSmsErrorCode(code)
                }
                if (remainingParts.decrementAndGet() <= 0 && !completion.isCompleted) {
                    val err = failureReason
                    if (err == null) {
                        completion.complete(Result.success(Unit))
                    } else {
                        completion.complete(Result.failure(Exception(err)))
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(sessionAction),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        return try {
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            for (index in parts.indices) {
                val intent = Intent(sessionAction).apply {
                    `package` = context.packageName
                    putExtra("part_index", index)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                sentIntents.add(
                    PendingIntent.getBroadcast(
                        context,
                        (sessionAction.hashCode() + index) and 0x7FFFFFFF,
                        intent,
                        flags
                    )
                )
            }

            if (parts.size == 1) {
                smsManager.sendTextMessage(destination, null, parts[0], sentIntents[0], null)
            } else {
                smsManager.sendMultipartTextMessage(destination, null, parts, sentIntents, null)
            }

            val sendOutcome = withTimeoutOrNull(SMS_SEND_TIMEOUT_MS) {
                completion.await()
            } ?: Result.failure(Exception("Timed out waiting for Android carrier send confirmation."))

            sendOutcome.fold(
                onSuccess = {
                    EventLogger.log(
                        LogCategory.SMS_DELIVERY,
                        "Sent SMS reply to '$destination' via SIM ${slotIndex + 1} (${simInfo.carrierName})",
                        LogLevel.SUCCESS
                    )
                    Result.success(
                        SmsSendResult(
                            recipient = destination,
                            slotIndex = slotIndex,
                            subId = simInfo.subId,
                            carrierName = simInfo.carrierName,
                            partCount = parts.size
                        )
                    )
                },
                onFailure = { error ->
                    EventLogger.log(
                        LogCategory.ERROR,
                        "SMS send failed on SIM ${slotIndex + 1}: ${error.message}",
                        LogLevel.ERROR
                    )
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            EventLogger.log(
                LogCategory.ERROR,
                "SMS send exception on SIM ${slotIndex + 1}: ${e.message}",
                LogLevel.ERROR
            )
            Result.failure(e)
        } finally {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveSmsManager(subId: Int): SmsManager? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)?.createForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subId)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun describeSmsErrorCode(resultCode: Int): String = when (resultCode) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Carrier reported generic SMS failure."
        SmsManager.RESULT_ERROR_RADIO_OFF -> "Cellular radio is off (Airplane mode or disabled SIM)."
        SmsManager.RESULT_ERROR_NULL_PDU -> "Failed to construct SMS PDU."
        SmsManager.RESULT_ERROR_NO_SERVICE -> "No cellular service on selected SIM."
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "Android SMS rate limit exceeded."
        SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> "Blocked by Fixed Dialing Number (FDN) list."
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "Short code sending is restricted."
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "Short code sending is permanently blocked."
        else -> "Carrier rejected SMS (code $resultCode)."
    }

    companion object {
        private const val ACTION_SMS_SENT_BASE = "com.example.vanta.action.SMS_SENT"
        private const val SMS_SEND_TIMEOUT_MS = 30_000L
    }
}
