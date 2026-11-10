package com.example.telegram

import com.example.data.model.SimCardInfo

data class FormattedMessage(
    val text: String,
    val entities: List<TelegramEntity>
)

object TelegramEntityHelper {
    // Exact Custom Emoji IDs specified by VANTA specification
    const val ONLINE_EMOJI_ID = "5470088863789622816"
    const val OFFLINE_EMOJI_ID = "5852812849780362931"
    const val SIM_INDICATOR_EMOJI_ID = "5470088863789622816"
    const val NEW_MESSAGE_EMOJI_ID = "5231379851402827068"
    const val CONTEXT_EMOJI_ID = "5260535596941582167"
    const val GITHUB_EMOJI_ID = "4983325655899833037"

    private const val SEPARATOR = "━━━━━━━━━━━━━━━━━━━━"

    // Placeholder characters for custom emoji attachment (1 or 2 UTF-16 code units)
    private const val EMOJI_PLACEHOLDER = "⚡"

    /**
     * Formats incoming SMS according to the exact VANTA specification:
     * [NEW MESSAGE CUSTOM EMOJI] New Message:
     * By: [SENDER]
     * [CONTEXT CUSTOM EMOJI] context:
     * [SMS CONTENT]
     * [SHORT SEPARATOR]
     */
    fun formatIncomingSms(sender: String, body: String, simLabel: String?): FormattedMessage {
        val entities = mutableListOf<TelegramEntity>()
        val sb = StringBuilder()

        // 1. [NEW MESSAGE CUSTOM EMOJI] New Message:
        val newMsgEmojiOffset = utf16Length(sb.toString())
        sb.append(EMOJI_PLACEHOLDER)
        val newMsgEmojiLen = utf16Length(EMOJI_PLACEHOLDER)
        entities.add(
            TelegramEntity(
                type = "custom_emoji",
                offset = newMsgEmojiOffset,
                length = newMsgEmojiLen,
                customEmojiId = NEW_MESSAGE_EMOJI_ID
            )
        )
        sb.append(" New Message")
        if (!simLabel.isNullOrBlank()) {
            sb.append(" (").append(simLabel).append(")")
        }
        sb.append(":\n")

        // 2. By: [SENDER]
        sb.append("By: ").append(sender).append("\n\n")

        // 3. [CONTEXT CUSTOM EMOJI] context:
        val contextEmojiOffset = utf16Length(sb.toString())
        sb.append(EMOJI_PLACEHOLDER)
        val contextEmojiLen = utf16Length(EMOJI_PLACEHOLDER)
        entities.add(
            TelegramEntity(
                type = "custom_emoji",
                offset = contextEmojiOffset,
                length = contextEmojiLen,
                customEmojiId = CONTEXT_EMOJI_ID
            )
        )
        sb.append(" context:\n")

        // 4. [SMS CONTENT]
        sb.append(body).append("\n\n")

        // 5. Short mobile-friendly separator
        sb.append(SEPARATOR)

        return FormattedMessage(sb.toString(), entities)
    }

    /**
     * Formats /start status message according to exact VANTA specification:
     * Activation: [ONLINE OR OFFLINE]
     * Active Numbers:
     * [SIM1 indicator] SIM1: [NUMBER]
     * [SIM2 indicator] SIM2: [NUMBER]
     * [SEPARATOR]
     */
    fun formatStatusMessage(
        isOnline: Boolean,
        sims: List<SimCardInfo>,
        customNote: String? = null
    ): FormattedMessage {
        val entities = mutableListOf<TelegramEntity>()
        val sb = StringBuilder()

        sb.append("Activation: ")
        val actOffset = utf16Length(sb.toString())
        sb.append(EMOJI_PLACEHOLDER)
        val actLen = utf16Length(EMOJI_PLACEHOLDER)
        entities.add(
            TelegramEntity(
                type = "custom_emoji",
                offset = actOffset,
                length = actLen,
                customEmojiId = if (isOnline) ONLINE_EMOJI_ID else OFFLINE_EMOJI_ID
            )
        )
        sb.append(if (isOnline) " ONLINE" else " OFFLINE").append("\n\n")

        sb.append("Active Numbers:\n")
        if (sims.isEmpty()) {
            sb.append("No active SIM cards detected\n")
        } else {
            for (sim in sims) {
                val simOffset = utf16Length(sb.toString())
                sb.append(EMOJI_PLACEHOLDER)
                val simLen = utf16Length(EMOJI_PLACEHOLDER)
                entities.add(
                    TelegramEntity(
                        type = "custom_emoji",
                        offset = simOffset,
                        length = simLen,
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
            
            VANTA is a secure, open-source Android SMS-to-Telegram bridge created by Sefeshki.
            
            Commands:
            • /start — Verify connection and get fresh device status
            • /status — Check live forwarding & SIM state
            • /help — Show this help message
            
            Security & Pairing:
            Only authorized chat IDs can receive SMS and execute status commands.
            Pair your device from the VANTA app on your Android phone using your personal bot token.
            
            $SEPARATOR
        """.trimIndent()
        return FormattedMessage(text, emptyList())
    }

    /**
     * Calculates UTF-16 code units length of a string, which Telegram API requires for offsets.
     */
    fun utf16Length(s: String): Int {
        var count = 0
        for (i in 0 until s.length) {
            val codePoint = s.codePointAt(i)
            count += Character.charCount(codePoint)
        }
        return s.length
    }
}
