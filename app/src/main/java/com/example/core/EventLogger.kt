package com.example.core

import com.example.data.model.LogCategory
import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object EventLogger {
    private const val MAX_LOGS = 150
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val timeFormatLocal = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    @Synchronized
    fun log(category: LogCategory, message: String, level: LogLevel = LogLevel.INFO) {
        // Redact any accidental tokens or sensitive sequences
        val safeMessage = sanitize(message)
        val entry = LogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            category = category,
            message = safeMessage,
            level = level
        )

        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry) // Newest first
        if (currentList.size > MAX_LOGS) {
            currentList.removeAt(currentList.lastIndex)
        }
        _logs.value = currentList
    }

    @Synchronized
    fun clear() {
        _logs.value = emptyList()
        log(LogCategory.SYSTEM, "Terminal events cleared by user", LogLevel.INFO)
    }

    fun formatTime(timestamp: Long): String {
        val formatter = timeFormatLocal.get() ?: SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    private fun sanitize(input: String): String {
        var result = input
        // Redact bot token pattern (digits:letters/digits)
        result = result.replace(Regex("""\b\d{8,12}:[A-Za-z0-9_-]{30,40}\b"""), "[REDACTED_BOT_TOKEN]")
        // Redact 4-8 digit potential OTPs
        result = result.replace(Regex("""(?i)(otp|code|pin|رمز|تایید)[:\s]+(\d{4,8})"""), "$1: [PROTECTED]")
        return result
    }
}
