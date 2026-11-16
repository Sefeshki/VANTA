package com.example.telegram

import com.example.data.model.CallLogEntryItem
import com.example.data.model.CallType
import com.example.data.model.SimCardInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FormattedMessage(
    val text: String,
    val entities: List<TelegramEntity>
)

object TelegramEntityHelper {
    // Telegram custom emoji IDs
    const val ONLINE_EMOJI_ID = "5470088863789622816"
    const val OFFLINE_EMOJI_ID = "5852812849780362931"
    const val SIM_INDICATOR_EMOJI_ID = "5470088863789622816"
    const val NEW_MESSAGE_EMOJI_ID = "5231379851402827068"
    const val CONTEXT_EMOJI_ID = "5260535596941582167"
    const val GITHUB_EMOJI_ID = "4983325655899833037"

    private const val SEPARATOR = "━━━━━━━━━━━━━━━━━━━━"

    val ALL_CUSTOM_EMOJI_IDS = listOf(
        ONLINE_EMOJI_ID,
        OFFLINE_EMOJI_ID,
        NEW_MESSAGE_EMOJI_ID,
        CONTEXT_EMOJI_ID,
        GITHUB_EMOJI_ID
    )

    private val DEFAULT_EMOJI_FALLBACKS = mapOf(
        ONLINE_EMOJI_ID to "🟢",
        OFFLINE_EMOJI_ID to "🔴",
        NEW_MESSAGE_EMOJI_ID to "📩",
        CONTEXT_EMOJI_ID to "📝",
        GITHUB_EMOJI_ID to "🐙"
    )

    private val emojiCache = java.util.concurrent.ConcurrentHashMap<String, String>(DEFAULT_EMOJI_FALLBACKS)

    fun updateEmojiCache(map: Map<String, String>) {
        for ((id, emoji) in map) {
            if (emoji.isNotBlank()) {
                emojiCache[id] = emoji
            }
        }
    }

    fun resetEmojiCacheToDefaults() {
        emojiCache.clear()
        emojiCache.putAll(DEFAULT_EMOJI_FALLBACKS)
    }

    fun getPlaceholder(customEmojiId: String): String {
        return emojiCache[customEmojiId] ?: DEFAULT_EMOJI_FALLBACKS[customEmojiId] ?: "⚡"
    }

    fun formatTimestamp(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("hh:mm a", Locale.US)
        return formatter.format(Date(timestampMillis))
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0L) return "0s"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hrs > 0 -> "${hrs}h ${mins}m ${secs}s"
            mins > 0 -> "${mins}m ${secs}s"
            else -> "${secs}s"
        }
    }

    fun formatSenderDisplay(senderNumber: String, contactName: String?): String {
        val cleanName = contactName?.trim()?.takeIf { it.isNotBlank() }
        val cleanNum = senderNumber.trim().ifBlank { "Unknown" }
        return if (cleanName != null && !cleanName.equals(cleanNum, ignoreCase = true)) {
            "$cleanName ($cleanNum)"
        } else {
            cleanNum
        }
    }

    /**
     * Formats an incoming SMS with contact/sender display, receiving SIM label,
     * Android SMS timestamp, OTP indicator, and message content.
     */
    fun formatIncomingSms(
        sender: String,
        body: String,
        simLabel: String?,
        contactName: String? = null,
        receivedTimestamp: Long = System.currentTimeMillis(),
        isOtp: Boolean = false,
        sentReplies: List<String> = emptyList()
    ): FormattedMessage {
        val entities = mutableListOf<TelegramEntity>()
        val sb = StringBuilder()

        val newMsgPlaceholder = getPlaceholder(NEW_MESSAGE_EMOJI_ID)
        val newMsgEmojiOffset = sb.length
        sb.append(newMsgPlaceholder)
        entities.add(
            TelegramEntity(
                type = "custom_emoji",
                offset = newMsgEmojiOffset,
                length = newMsgPlaceholder.length,
                customEmojiId = NEW_MESSAGE_EMOJI_ID
            )
        )
        sb.append(" New SMS")
        if (isOtp) {
            sb.append(" · OTP / Verification")
        }
        sb.append("\n")

        sb.append("From: ").append(formatSenderDisplay(sender, contactName)).append("\n")
        if (!simLabel.isNullOrBlank()) {
            sb.append("SIM: ").append(simLabel).append("\n")
        }
        sb.append("Time: ").append(formatTimestamp(receivedTimestamp)).append("\n\n")

        val contextPlaceholder = getPlaceholder(CONTEXT_EMOJI_ID)
        val contextEmojiOffset = sb.length
        sb.append(contextPlaceholder)
        entities.add(
            TelegramEntity(
                type = "custom_emoji",
                offset = contextEmojiOffset,
                length = contextPlaceholder.length,
                customEmojiId = CONTEXT_EMOJI_ID
            )
        )
        sb.append(" Message:\n")
        sb.append(body).append("\n\n")
        sb.append(SEPARATOR)

        for (reply in sentReplies) {
            sb.append("\n\nYou replied:\n").append(reply)
        }

        return FormattedMessage(sb.toString(), entities)
    }

    fun formatCallLogMessage(
        entry: CallLogEntryItem,
        simLabel: String
    ): FormattedMessage {
        val sb = StringBuilder()
        val headerTag = when (entry.callType) {
            CallType.INCOMING -> "📞 Incoming Call"
            CallType.OUTGOING -> "📤 Outgoing Call"
            CallType.MISSED -> "📵 Missed Call"
            CallType.REJECTED -> "🚫 Rejected Call"
        }
        sb.append(headerTag).append("\n")
        sb.append("Caller: ").append(formatSenderDisplay(entry.number, entry.contactName)).append("\n")
        sb.append("SIM: ").append(simLabel).append("\n")
        sb.append("Time: ").append(formatTimestamp(entry.timestamp)).append("\n")
        if (entry.callType == CallType.INCOMING || entry.callType == CallType.OUTGOING) {
            sb.append("Duration: ").append(formatDuration(entry.durationSeconds)).append("\n")
        }
        sb.append("\n").append(SEPARATOR)
        return FormattedMessage(sb.toString(), emptyList())
    }

    fun formatIncomingCallNotification(
        callerNumber: String?,
        contactName: String?,
        simLabel: String,
        timestamp: Long = System.currentTimeMillis()
    ): FormattedMessage {
        val sb = StringBuilder()
        val callerDisplay = if (callerNumber.isNullOrBlank()) {
            "Number unavailable"
        } else {
            formatSenderDisplay(callerNumber, contactName)
        }
        sb.append("📲 Incoming Call (Ringing)\n")
        sb.append("Caller: ").append(callerDisplay).append("\n")
        sb.append("SIM: ").append(simLabel).append("\n")
        sb.append("Time: ").append(formatTimestamp(timestamp)).append("\n\n")
        sb.append(SEPARATOR)
        return FormattedMessage(sb.toString(), emptyList())
    }

    fun formatReplyPrompt(): FormattedMessage {
        return FormattedMessage("Send the message you want to reply:", emptyList())
    }

    fun formatForwardPrompt(
        sender: String,
        contactName: String?,
        simLabel: String?,
        hasSavedDestinations: Boolean
    ): FormattedMessage {
        val fromDisplay = formatSenderDisplay(sender, contactName)
        val simLine = if (!simLabel.isNullOrBlank()) "\nSIM: $simLabel" else ""
        val instruction = if (hasSavedDestinations) {
            "Select a destination below, or tap Enter Chat ID."
        } else {
            "No saved destinations yet. Tap Enter Chat ID below, or add recipients inside VANTA → Telegram Bot."
        }
        val text = """
            ↪️ Forward SMS
            Original From: $fromDisplay$simLine

            $instruction

            $SEPARATOR
        """.trimIndent()
        return FormattedMessage(text, emptyList())
    }

    fun formatForwardManualChatIdPrompt(
        sender: String,
        contactName: String?,
        simLabel: String?
    ): FormattedMessage {
        val fromDisplay = formatSenderDisplay(sender, contactName)
        val simLine = if (!simLabel.isNullOrBlank()) "\nSIM: $simLabel" else ""
        val text = """
            ↪️ Forward SMS — Destination Chat ID
            Original From: $fromDisplay$simLine

            Send the numeric Telegram Chat ID (for example: 123456789 or -1001234567890), or tap Cancel below.

            $SEPARATOR
        """.trimIndent()
        return FormattedMessage(text, emptyList())
    }

    fun formatForwardConfirmPrompt(
        sender: String,
        contactName: String?,
        simLabel: String?,
        destinationLabel: String,
        destinationChatId: Long,
        alreadyForwarded: Boolean
    ): FormattedMessage {
        val fromDisplay = formatSenderDisplay(sender, contactName)
        val simLine = if (!simLabel.isNullOrBlank()) "\nSIM: $simLabel" else ""
        val destDisplay = if (destinationLabel.isNotBlank() && destinationLabel != destinationChatId.toString()) {
            "$destinationLabel ($destinationChatId)"
        } else {
            destinationChatId.toString()
        }
        val dupNote = if (alreadyForwarded) {
            "\n\n⚠️ Already forwarded to this destination."
        } else {
            "\n\nTap Confirm Forward to deliver this SMS."
        }
        val text = """
            ↪️ Confirm Forward SMS
            Original From: $fromDisplay$simLine
            Destination: $destDisplay$dupNote

            $SEPARATOR
        """.trimIndent()
        return FormattedMessage(text, emptyList())
    }

    fun formatForwardedSmsToChat(
        sender: String,
        contactName: String?,
        body: String,
        simLabel: String?,
        receivedTimestamp: Long,
        isOtp: Boolean = false
    ): FormattedMessage {
        val sb = StringBuilder()
        sb.append("↪️ Forwarded SMS via VANTA")
        if (isOtp) {
            sb.append(" · OTP / Verification")
        }
        sb.append("\n")
        sb.append("From: ").append(formatSenderDisplay(sender, contactName)).append("\n")
        if (!simLabel.isNullOrBlank()) {
            sb.append("SIM: ").append(simLabel).append("\n")
        }
        sb.append("Time: ").append(formatTimestamp(receivedTimestamp)).append("\n\n")
        sb.append("Message:\n").append(body).append("\n\n")
        sb.append(SEPARATOR)
        return FormattedMessage(sb.toString(), emptyList())
    }

    fun formatStatusMessage(
        isOnline: Boolean,
        sims: List<SimCardInfo>,
        customNote: String? = null
    ): FormattedMessage {
        val entities = mutableListOf<TelegramEntity>()
        val sb = StringBuilder()

        sb.append("Activation: ")
        val actId = if (isOnline) ONLINE_EMOJI_ID else OFFLINE_EMOJI_ID
        val actPlaceholder = getPlaceholder(actId)
        val actOffset = sb.length
        sb.append(actPlaceholder)
        entities.add(
            TelegramEntity(
                type = "custom_emoji",
                offset = actOffset,
                length = actPlaceholder.length,
                customEmojiId = actId
            )
        )
        sb.append(if (isOnline) " ONLINE" else " OFFLINE").append("\n\n")

        sb.append("Active Numbers:\n")
        if (sims.isEmpty()) {
            sb.append("No active SIM cards detected\n")
        } else {
            for (sim in sims) {
                val simPlaceholder = getPlaceholder(SIM_INDICATOR_EMOJI_ID)
                val simOffset = sb.length
                sb.append(simPlaceholder)
                entities.add(
                    TelegramEntity(
                        type = "custom_emoji",
                        offset = simOffset,
                        length = simPlaceholder.length,
                        customEmojiId = SIM_INDICATOR_EMOJI_ID
                    )
                )
                val numberDisplay = sim.phoneNumber?.takeIf { it.isNotBlank() } ?: "NUMBER UNAVAILABLE"
                val slotName = "SIM${sim.slotIndex + 1}"
                val carrierInfo = if (sim.carrierName.isNotBlank()) " (${sim.carrierName})" else ""
                sb.append(" $slotName$carrierInfo: $numberDisplay\n")
            }
        }

        if (customNote != null) {
            sb.append("\n").append(customNote).append("\n")
        }

        sb.append("\n").append(SEPARATOR)

        return FormattedMessage(sb.toString(), entities)
    }

    fun formatOverviewPanel(
        isForwardingEnabled: Boolean,
        isServiceRunning: Boolean,
        isInternetOnline: Boolean,
        isBotPollingActive: Boolean,
        sims: List<SimCardInfo>,
        simControl: com.example.data.model.SimControlConfig,
        isOtpProtectionEnabled: Boolean,
        totalDelivered: Int,
        pendingQueueCount: Int,
        customNote: String? = null
    ): FormattedMessage {
        val entities = mutableListOf<TelegramEntity>()
        val sb = StringBuilder()

        if (!customNote.isNullOrBlank()) {
            sb.append(customNote).append("\n\n")
        }

        sb.append("⚡ VANTA Overview\n\n")
        val bridgeLive = isForwardingEnabled && isServiceRunning
        sb.append("Bridge: ")
        val actId = if (bridgeLive) ONLINE_EMOJI_ID else OFFLINE_EMOJI_ID
        val actPlaceholder = getPlaceholder(actId)
        val actOffset = sb.length
        sb.append(actPlaceholder)
        entities.add(
            TelegramEntity(
                type = "custom_emoji",
                offset = actOffset,
                length = actPlaceholder.length,
                customEmojiId = actId
            )
        )
        sb.append(if (bridgeLive) " ACTIVE" else if (isForwardingEnabled) " STARTING" else " OFF").append("\n")
        sb.append("Service: ").append(if (isServiceRunning) "Running" else "Stopped").append("\n")
        sb.append("Internet: ").append(if (isInternetOnline) "Online" else "Offline")
            .append(" · Bot: ").append(if (isBotPollingActive) "Listening" else "Idle").append("\n")
        sb.append("OTP Protection: ").append(if (isOtpProtectionEnabled) "ON" else "OFF").append("\n")
        sb.append("Delivered: ").append(totalDelivered).append(" · Queue: ").append(pendingQueueCount).append("\n\n")

        sb.append("SIM Summary:\n")
        val anyPerm = sims.any { it.hasPermission }
        for (slot in 0..1) {
            val sim = sims.firstOrNull { it.slotIndex == slot }
            val cfg = simControl.forSlot(slot)
            val statusLabel = when {
                sim != null && !sim.hasPermission -> "Permission needed"
                sim == null && anyPerm -> "Single-SIM device"
                sim == null || !sim.isAvailable -> "No SIM inserted"
                !cfg.masterEnabled -> "${sim.carrierName} (OFF)"
                else -> "${sim.carrierName} · ${cfg.compactSummary()}"
            }
            sb.append("• SIM ").append(slot + 1).append(": ").append(statusLabel).append("\n")
        }

        sb.append("\n").append(SEPARATOR)
        return FormattedMessage(sb.toString(), entities)
    }

    fun formatSimInfoPanel(
        sims: List<SimCardInfo>,
        simControl: com.example.data.model.SimControlConfig
    ): FormattedMessage {
        val sb = StringBuilder()
        sb.append("📶 SIM Information\n\n")

        val anyPerm = sims.any { it.hasPermission }
        for (slot in 0..1) {
            val sim = sims.firstOrNull { it.slotIndex == slot }
            val cfg = simControl.forSlot(slot)
            sb.append("SIM ").append(slot + 1)
            if (sim?.isDefaultSms == true) sb.append(" [Default SMS]")
            sb.append("\n")

            when {
                sim != null && !sim.hasPermission -> {
                    sb.append("• State: Permission required (READ_PHONE_STATE)\n")
                }
                sim == null && anyPerm -> {
                    sb.append("• State: Not present (Single-SIM hardware)\n")
                }
                sim == null || !sim.isAvailable -> {
                    sb.append("• State: No SIM inserted\n")
                }
                else -> {
                    val subText = if (sim.subId >= 0) "Sub #${sim.subId}" else "Sub N/A"
                    val numText = sim.phoneNumber?.takeIf { it.isNotBlank() } ?: "NUMBER UNAVAILABLE"
                    sb.append("• Carrier: ").append(sim.carrierName).append(" (").append(subText).append(")\n")
                    sb.append("• Number: ").append(numText).append("\n")
                    sb.append("• Master: ").append(if (cfg.masterEnabled) "ON" else "OFF")
                    if (cfg.masterEnabled) {
                        sb.append(" · ").append(cfg.compactSummary())
                    }
                    sb.append("\n")
                }
            }
            if (slot == 0) sb.append("\n")
        }

        sb.append("\n").append(SEPARATOR)
        return FormattedMessage(sb.toString(), emptyList())
    }

    fun formatServiceStatusPanel(
        isServiceRunning: Boolean,
        serviceUptimeText: String,
        isInternetOnline: Boolean,
        isBotConnected: Boolean,
        isPollingActive: Boolean
    ): FormattedMessage {
        val sb = StringBuilder()
        sb.append("⚙️ Service Status\n\n")
        sb.append("• Foreground Service: ").append(if (isServiceRunning) "ACTIVE" else "STOPPED").append("\n")
        sb.append("• Uptime: ").append(if (isServiceRunning) serviceUptimeText else "IDLE").append("\n")
        sb.append("• Internet: ").append(if (isInternetOnline) "ONLINE" else "OFFLINE").append("\n")
        sb.append("• Telegram API: ").append(if (isBotConnected) "CONNECTED" else "DISCONNECTED").append("\n")
        sb.append("• Update Listener: ").append(if (isPollingActive) "ACTIVE" else "PAUSED").append("\n\n")
        sb.append(SEPARATOR)
        return FormattedMessage(sb.toString(), emptyList())
    }

    fun formatForwardingStatusPanel(
        isForwardingEnabled: Boolean,
        isServiceRunning: Boolean,
        isOtpProtectionEnabled: Boolean,
        simControl: com.example.data.model.SimControlConfig,
        totalDelivered: Int,
        pendingQueueCount: Int
    ): FormattedMessage {
        val sb = StringBuilder()
        sb.append("📡 Forwarding Status\n\n")
        sb.append("• Bridge Power: ").append(if (isForwardingEnabled) "ENABLED" else "DISABLED").append("\n")
        sb.append("• Pipeline State: ")
            .append(if (isForwardingEnabled && isServiceRunning) "Forwarding live" else "Paused / Idle")
            .append("\n")
        val anyOtpSim = simControl.isAnyOtpForwardingEnabled()
        val otpLabel = when {
            isOtpProtectionEnabled && !anyOtpSim -> "ON (Both SIMs OTP already OFF)"
            isOtpProtectionEnabled -> "ON (Blocking verification codes)"
            !anyOtpSim -> "OFF (Locked — enable OTP on a SIM first)"
            else -> "OFF"
        }
        sb.append("• OTP Protection: ").append(otpLabel).append("\n")
        sb.append("• SIM 1 Policy: ").append(simControl.sim1.compactSummary()).append("\n")
        sb.append("• SIM 2 Policy: ").append(simControl.sim2.compactSummary()).append("\n")
        sb.append("• Delivered: ").append(totalDelivered).append(" · Pending: ").append(pendingQueueCount).append("\n\n")
        sb.append(SEPARATOR)
        return FormattedMessage(sb.toString(), emptyList())
    }

    fun formatControlsPanel(
        isForwardingEnabled: Boolean,
        isServiceRunning: Boolean,
        isOtpProtectionEnabled: Boolean,
        sims: List<SimCardInfo>,
        simControl: com.example.data.model.SimControlConfig
    ): FormattedMessage {
        val sb = StringBuilder()
        sb.append("🎛️ VANTA Controls\n\n")
        sb.append("• Forwarding Bridge: ")
            .append(if (isForwardingEnabled && isServiceRunning) "ACTIVE" else if (isForwardingEnabled) "ENABLED" else "OFF")
            .append("\n")
        for (slot in 0..1) {
            val sim = sims.firstOrNull { it.slotIndex == slot }
            val master = simControl.forSlot(slot).masterEnabled
            val state = when {
                sim == null || !sim.isAvailable -> "Unavailable"
                master -> "ON (${sim.carrierName})"
                else -> "OFF (${sim.carrierName})"
            }
            sb.append("• SIM ").append(slot + 1).append(": ").append(state).append("\n")
        }
        sb.append("• OTP Protection: ").append(if (isOtpProtectionEnabled) "ON" else "OFF").append("\n\n")
        sb.append("Tap any control button below to update live settings.\n\n")
        sb.append(SEPARATOR)
        return FormattedMessage(sb.toString(), emptyList())
    }

    fun formatHelpMessage(): FormattedMessage {
        val text = """
            ℹ️ VANTA SMS Bridge

            VANTA forwards incoming SMS, call logs, and call alerts to this paired Telegram chat with SIM-aware routing.

            Interactive Controls:
            • Tap Reply under any forwarded SMS to respond via the original receiving SIM.
            • Tap Forward under any forwarded SMS to relay it to a saved chat or Chat ID.
            • Use the inline buttons below any status message to inspect SIMs, monitor the service, or toggle forwarding controls.

            $SEPARATOR
        """.trimIndent()
        return FormattedMessage(text, emptyList())
    }

    fun utf16Length(s: String): Int = s.length
}
