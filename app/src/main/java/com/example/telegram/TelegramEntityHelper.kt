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

    val ALL_CUSTOM_EMOJI_IDS = listOf(
        ONLINE_EMOJI_ID,
        OFFLINE_EMOJI_ID,
        NEW_MESSAGE_EMOJI_ID,
        CONTEXT_EMOJI_ID,
        GITHUB_EMOJI_ID
    )

    // Fallbacks if not yet resolved via getCustomEmojiStickers
    private val DEFAULT_EMOJI_FALLBACKS = mapOf(
        ONLINE_EMOJI_ID to "🟢",
        OFFLINE_EMOJI_ID to "🔴",
        NEW_MESSAGE_EMOJI_ID to "📩",
        CONTEXT_EMOJI_ID to "📝",
        GITHUB_EMOJI_ID to "🐙"
    )

    // Thread-safe cache holding resolved Unicode emoji characters for each custom emoji ID
    private val emojiCache = java.util.concurrent.ConcurrentHashMap<String, String>(DEFAULT_EMOJI_FALLBACKS)

    /**
     * Updates the custom emoji cache with sticker mappings returned by Telegram Bot API getCustomEmojiStickers.
     */
    fun updateEmojiCache(map: Map<String, String>) {
        for ((id, emoji) in map) {
            if (emoji.isNotBlank()) {
                emojiCache[id] = emoji
            }
        }
    }

    /**
     * Resets the cache back to default fallbacks (useful for tests).
     */
    fun resetEmojiCacheToDefaults() {
        emojiCache.clear()
        emojiCache.putAll(DEFAULT_EMOJI_FALLBACKS)
    }

    /**
     * Returns the exact Unicode emoji character to be covered by the custom_emoji entity.
     * In Telegram Bot API, custom_emoji entities MUST wrap the exact Unicode emoji character
     * corresponding to the sticker's emoji field, with exact UTF-16 code unit offset and length.
     */
    fun getPlaceholder(customEmojiId: String): String {
        return emojiCache[customEmojiId] ?: DEFAULT_EMOJI_FALLBACKS[customEmojiId] ?: "⚡"
    }

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
        val newMsgPlaceholder = getPlaceholder(NEW_MESSAGE_EMOJI_ID)
        val newMsgEmojiOffset = sb.length
        sb.append(newMsgPlaceholder)
        val newMsgEmojiLen = newMsgPlaceholder.length
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
        val contextPlaceholder = getPlaceholder(CONTEXT_EMOJI_ID)
        val contextEmojiOffset = sb.length
        sb.append(contextPlaceholder)
        val contextEmojiLen = contextPlaceholder.length
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
        val actId = if (isOnline) ONLINE_EMOJI_ID else OFFLINE_EMOJI_ID
        val actPlaceholder = getPlaceholder(actId)
        val actOffset = sb.length
        sb.append(actPlaceholder)
        val actLen = actPlaceholder.length
        entities.add(
            TelegramEntity(
                type = "custom_emoji",
                offset = actOffset,
                length = actLen,
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
                val simLen = simPlaceholder.length
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
            
            VANTA is a secure, source-available Android SMS-to-Telegram bridge created by Sefeshki.
            
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
    fun utf16Length(s: String): Int = s.length
}
