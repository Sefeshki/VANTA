package com.example.telegram

enum class NewMessageStep {
    AWAITING_RECIPIENT,
    CONFIRMING_RECIPIENT,
    AWAITING_BODY,
    SELECTING_SIM,
    SENDING
}

data class NewMessageSession(
    val sessionId: String,
    val chatId: Long,
    val userId: Long,
    val step: NewMessageStep,
    val recipientDisplay: String? = null,
    val recipientDialable: String? = null,
    val messageBody: String? = null,
    val promptMessageId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ValidatedPhoneNumber(
    val displayNumber: String,
    val dialableNumber: String
)

object PhoneNumberValidator {

    fun validateInternationalNumber(rawInput: String?): ValidatedPhoneNumber? {
        if (rawInput == null) return null
        val trimmed = rawInput.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isBlank() || trimmed.startsWith("/")) return null
        if (trimmed.any { it.isLetter() }) return null

        val hasPlusPrefix = trimmed.startsWith("+")
        val hasIddPrefix = !hasPlusPrefix && trimmed.startsWith("00")
        if (!hasPlusPrefix && !hasIddPrefix) return null

        var parenBalance = 0
        var lastParenContentDigits = 0
        for ((index, ch) in trimmed.withIndex()) {
            when {
                ch == '+' -> if (index != 0) return null
                ch.isDigit() -> if (parenBalance > 0) lastParenContentDigits++
                ch == ' ' || ch == '-' || ch == '.' -> Unit
                ch == '(' -> {
                    parenBalance++
                    if (parenBalance > 1) return null
                    lastParenContentDigits = 0
                }
                ch == ')' -> {
                    parenBalance--
                    if (parenBalance < 0 || lastParenContentDigits == 0) return null
                }
                else -> return null
            }
        }
        if (parenBalance != 0) return null

        val lastChar = trimmed.last()
        if (!lastChar.isDigit() && lastChar != ')') return null

        val rawDigits = trimmed.filter { it.isDigit() }
        val intlDigits = if (hasIddPrefix) rawDigits.drop(2) else rawDigits

        if (intlDigits.length !in 7..15) return null
        if (intlDigits.first() !in '1'..'9') return null

        val normalizedDisplay = if (hasIddPrefix) {
            val remainder = trimmed.removePrefix("00").trimStart(' ', '-', '.')
            "+$remainder"
        } else {
            val afterPlus = trimmed.removePrefix("+").trimStart()
            if (afterPlus.isEmpty() || (!afterPlus.first().isDigit() && afterPlus.first() != '(')) {
                return null
            }
            "+$afterPlus"
        }

        return ValidatedPhoneNumber(
            displayNumber = normalizedDisplay,
            dialableNumber = "+$intlDigits"
        )
    }
}
