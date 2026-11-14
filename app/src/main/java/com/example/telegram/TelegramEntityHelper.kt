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
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
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
        isOtp: Boolean = false
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

    fun formatReplyPrompt(
        recipientAddress: String,
        recipientName: String?,
        selectedSimLabel: String
    ): FormattedMessage {
        val target = formatSenderDisplay(recipientAddress, recipientName)
        val text = """
            ↩️ SMS Reply Mode
            To: $target
            Via: $selectedSimLabel

            Send your reply message now.
            Tap a SIM button to switch SIMs, or send /cancel to abort.

            $SEPARATOR
        """.trimIndent()
        return FormattedMessage(text, emptyList())
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

    fun formatHelpMessage(): FormattedMessage {
        val text = """
            VANTA SMS Bridge — Help

            VANTA is a source-available Android communication bridge created by Sefeshki.

            Commands:
            • /start — Verify connection and get live device status
            • /status — Check forwarding & SIM state
            • /cancel — Cancel an active SMS reply session
            • /help — Show this help message

            Replying to SMS:
            Tap Reply beneath any forwarded SMS, optionally pick SIM 1 or SIM 2, then send your message text.

            $SEPARATOR
        """.trimIndent()
        return FormattedMessage(text, emptyList())
    }

    fun utf16Length(s: String): Int = s.length
}
