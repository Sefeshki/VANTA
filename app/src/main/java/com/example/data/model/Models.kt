package com.example.data.model

enum class SimSelectionMode(val label: String) {
    ALL("Both SIMs"),
    SIM1_ONLY("SIM1 Only"),
    SIM2_ONLY("SIM2 Only"),
    NONE("Disabled")
}

enum class SimFeature(
    val title: String,
    val description: String
) {
    SMS_FORWARDING(
        title = "SMS Forwarding",
        description = "Forward incoming SMS to Telegram"
    ),
    OTP_MESSAGES(
        title = "OTP Forwarding",
        description = "Allow forwarding verification & login codes"
    ),
    CALL_LOG_FORWARDING(
        title = "Call Log Forwarding",
        description = "Forward completed incoming, outgoing & missed calls"
    ),
    INCOMING_CALL_NOTIFICATIONS(
        title = "Incoming Call Notifications",
        description = "Alert immediately when a call is ringing"
    ),
    SMS_SENDING(
        title = "SMS Sending / Replies",
        description = "Send SMS replies from Telegram"
    )
}

data class SimFeatureSettings(
    val masterEnabled: Boolean = true,
    val smsForwardingEnabled: Boolean = true,
    val otpMessagesEnabled: Boolean = true,
    val callLogForwardingEnabled: Boolean = false,
    val incomingCallNotificationsEnabled: Boolean = false,
    val smsSendingEnabled: Boolean = true
) {
    fun isFeatureSelected(feature: SimFeature): Boolean = when (feature) {
        SimFeature.SMS_FORWARDING -> smsForwardingEnabled
        SimFeature.OTP_MESSAGES -> otpMessagesEnabled
        SimFeature.CALL_LOG_FORWARDING -> callLogForwardingEnabled
        SimFeature.INCOMING_CALL_NOTIFICATIONS -> incomingCallNotificationsEnabled
        SimFeature.SMS_SENDING -> smsSendingEnabled
    }

    fun isFeatureEffective(feature: SimFeature): Boolean {
        if (!masterEnabled) return false
        return when (feature) {
            SimFeature.SMS_FORWARDING -> smsForwardingEnabled
            SimFeature.OTP_MESSAGES -> smsForwardingEnabled && otpMessagesEnabled
            SimFeature.CALL_LOG_FORWARDING -> callLogForwardingEnabled
            SimFeature.INCOMING_CALL_NOTIFICATIONS -> incomingCallNotificationsEnabled
            SimFeature.SMS_SENDING -> smsSendingEnabled
        }
    }

    fun withFeature(feature: SimFeature, enabled: Boolean): SimFeatureSettings = when (feature) {
        SimFeature.SMS_FORWARDING -> copy(smsForwardingEnabled = enabled)
        SimFeature.OTP_MESSAGES -> copy(otpMessagesEnabled = enabled)
        SimFeature.CALL_LOG_FORWARDING -> copy(callLogForwardingEnabled = enabled)
        SimFeature.INCOMING_CALL_NOTIFICATIONS -> copy(incomingCallNotificationsEnabled = enabled)
        SimFeature.SMS_SENDING -> copy(smsSendingEnabled = enabled)
    }

    fun activeFeatureCount(): Int {
        if (!masterEnabled) return 0
        var count = 0
        if (smsForwardingEnabled) count++
        if (smsForwardingEnabled && otpMessagesEnabled) count++
        if (callLogForwardingEnabled) count++
        if (incomingCallNotificationsEnabled) count++
        if (smsSendingEnabled) count++
        return count
    }

    fun compactSummary(): String {
        if (!masterEnabled) return "Disabled"
        val tags = mutableListOf<String>()
        if (smsForwardingEnabled) {
            tags.add(if (otpMessagesEnabled) "SMS+OTP" else "SMS")
        }
        if (incomingCallNotificationsEnabled || callLogForwardingEnabled) {
            tags.add("Calls")
        }
        if (smsSendingEnabled) {
            tags.add("Reply")
        }
        return if (tags.isEmpty()) "No active features" else tags.joinToString(" · ")
    }
}

data class SimControlConfig(
    val sim1: SimFeatureSettings = SimFeatureSettings(),
    val sim2: SimFeatureSettings = SimFeatureSettings()
) {
    fun forSlot(slotIndex: Int): SimFeatureSettings = when (slotIndex) {
        0 -> sim1
        1 -> sim2
        else -> sim1
    }

    fun toLegacySelectionMode(): SimSelectionMode {
        val s1On = sim1.masterEnabled && sim1.smsForwardingEnabled
        val s2On = sim2.masterEnabled && sim2.smsForwardingEnabled
        return when {
            s1On && s2On -> SimSelectionMode.ALL
            s1On -> SimSelectionMode.SIM1_ONLY
            s2On -> SimSelectionMode.SIM2_ONLY
            else -> SimSelectionMode.NONE
        }
    }

    fun isAnyOtpForwardingEnabled(): Boolean {
        return sim1.isFeatureEffective(SimFeature.OTP_MESSAGES) ||
            sim2.isFeatureEffective(SimFeature.OTP_MESSAGES)
    }

    fun summaryLabel(): String {
        val s1 = sim1.masterEnabled
        val s2 = sim2.masterEnabled
        return when {
            s1 && s2 -> "SIM 1 + SIM 2"
            s1 -> "SIM 1 ONLY"
            s2 -> "SIM 2 ONLY"
            else -> "SIMS OFF"
        }
    }
}

data class SimCardInfo(
    val slotIndex: Int,          // 0 for SIM1, 1 for SIM2
    val subId: Int,              // Real Android Subscription ID, or -1 if unavailable/unknown
    val carrierName: String,
    val phoneNumber: String?,    // null if restricted by OS/carrier
    val isAvailable: Boolean,
    val isSelected: Boolean,
    val countryIso: String? = null,
    val iccId: String? = null,
    val hasPermission: Boolean = true,
    val isDefaultSms: Boolean = false
)

enum class LogCategory {
    SYSTEM,
    TELEGRAM,
    NETWORK,
    SIM,
    SERVICE,
    SMS_DELIVERY,
    CALLS,
    SECURITY,
    ERROR
}

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class LogEntry(
    val id: String,
    val timestamp: Long,
    val category: LogCategory,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

data class SmsMessageItem(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val simSlot: Int,           // 0 for SIM1, 1 for SIM2, -1 if unknown
    val subId: Int = -1,
    val isOtp: Boolean = false
)

data class SmsReplyContext(
    val contextId: String,
    val senderAddress: String,
    val senderName: String?,
    val simSlot: Int,
    val subId: Int,
    val receivedTimestamp: Long,
    val messageBody: String = "",
    val isOtp: Boolean = false,
    val forwardedChatIds: List<Long> = emptyList(),
    val simLabel: String? = null,
    val notificationMessageId: Long? = null,
    val sentReplies: List<String> = emptyList()
)

data class ForwardDestination(
    val chatId: Long,
    val label: String,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun displayName(): String {
        val clean = label.trim()
        return if (clean.isNotBlank()) "$clean ($chatId)" else chatId.toString()
    }
}

enum class CallType(val label: String) {
    INCOMING("Incoming Call"),
    OUTGOING("Outgoing Call"),
    MISSED("Missed Call"),
    REJECTED("Rejected Call")
}

data class CallLogEntryItem(
    val callLogId: Long,
    val number: String,
    val contactName: String?,
    val callType: CallType,
    val timestamp: Long,
    val durationSeconds: Long,
    val simSlot: Int,
    val subId: Int
)

enum class DeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED,
    FILTERED
}

data class DeliveryQueueItem(
    val id: String,
    val sms: SmsMessageItem,
    val timestamp: Long,
    var attemptCount: Int = 0,
    var lastAttemptTime: Long = 0L,
    var status: DeliveryStatus = DeliveryStatus.PENDING,
    var lastError: String? = null
)

data class TelegramConfig(
    val hasToken: Boolean = false,
    val tokenMasked: String = "",
    val botUsername: String? = null,
    val botId: Long? = null,
    val authorizedChatId: Long? = null,
    val activePairingCode: String? = null,
    val pairingCodeExpiry: Long = 0L,
    val forwardDestinations: List<ForwardDestination> = emptyList()
)

data class BridgeStatus(
    val isForwardingActive: Boolean = false,
    val isServiceRunning: Boolean = false,
    val isTelegramConnected: Boolean = false,
    val isInternetConnected: Boolean = false,
    val lastSuccessfulDelivery: Long? = null,
    val lastConnectionAttempt: Long? = null,
    val pendingCount: Int = 0,
    val totalDeliveredCount: Int = 0
)

data class AppPreferences(
    val isForwardingEnabled: Boolean = false,
    val isStayAliveEnabled: Boolean = true,
    val isAutoStartEnabled: Boolean = false,
    val isOtpProtectionEnabled: Boolean = false,
    val hasSeenOtpSuggestion: Boolean = false,
    val simSelectionMode: SimSelectionMode = SimSelectionMode.ALL,
    val simControl: SimControlConfig = SimControlConfig(),
    val isTutorialCompleted: Boolean = false
)
