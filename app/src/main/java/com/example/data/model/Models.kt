package com.example.data.model

enum class SimSelectionMode(val label: String) {
    ALL("Both SIMs"),
    SIM1_ONLY("SIM1 Only"),
    SIM2_ONLY("SIM2 Only"),
    NONE("Disabled")
}

data class SimCardInfo(
    val slotIndex: Int,          // 0 for SIM1, 1 for SIM2
    val subId: Int,             // Android Subscription ID
    val carrierName: String,     // e.g. "T-Mobile", "Irancell"
    val phoneNumber: String?,    // null if restricted by OS/carrier
    val isAvailable: Boolean,
    val isSelected: Boolean,
    val countryIso: String? = null
)

enum class LogCategory {
    SYSTEM,
    TELEGRAM,
    NETWORK,
    SIM,
    SERVICE,
    SMS_DELIVERY,
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
    val pairingCodeExpiry: Long = 0L
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
    val isTutorialCompleted: Boolean = false
)
