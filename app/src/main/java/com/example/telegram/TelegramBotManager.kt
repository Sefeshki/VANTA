package com.example.telegram

import com.example.core.EventLogger
import com.example.core.PreferencesManager
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SimCardInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Locale

class TelegramBotManager(
    private val preferencesManager: PreferencesManager,
    private val apiClient: TelegramApiClient = TelegramApiClient()
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var pollingJob: Job? = null
    private var lastUpdateId: Long? = null

    private val _isPollingActive = MutableStateFlow(false)
    val isPollingActive: StateFlow<Boolean> = _isPollingActive.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val secureRandom = SecureRandom()

    // Provider for currently active SIM cards
    var simInfoProvider: (() -> List<SimCardInfo>)? = null
    var isForwardingActiveProvider: (() -> Boolean)? = null

    init {
        // Automatically start polling if token and chat ID are configured
        if (preferencesManager.telegramConfig.value.hasToken) {
            startPolling()
        }
    }

    suspend fun verifyAndSaveToken(token: String): Result<TelegramUser> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val cleanToken = apiClient.sanitizeToken(token)
        val result = apiClient.getMe(cleanToken)
        result.onSuccess { user ->
            preferencesManager.saveBotToken(cleanToken, user.username, user.id)
            _isConnected.value = true
            EventLogger.log(
                LogCategory.TELEGRAM,
                "Bot verified: @${user.username ?: user.firstName} (ID: ${user.id})",
                LogLevel.SUCCESS
            )
            startPolling()
        }.onFailure { err ->
            _isConnected.value = false
            EventLogger.log(
                LogCategory.SECURITY,
                "Bot token verification failed: ${err.message}",
                LogLevel.ERROR
            )
        }
        result
    }

    fun checkConnection() {
        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) {
            _isConnected.value = false
            return
        }
        scope.launch {
            val res = apiClient.getMe(token)
            _isConnected.value = res.isSuccess
            if (res.isSuccess && !_isPollingActive.value) {
                startPolling()
            }
        }
    }

    fun generatePairingCode(): String {
        val code = String.format(Locale.US, "%06d", secureRandom.nextInt(1_000_000))
        val expiry = System.currentTimeMillis() + (10 * 60 * 1000) // 10 minutes
        preferencesManager.setPairingCode(code, expiry)
        EventLogger.log(
            LogCategory.SECURITY,
            "Generated new 6-digit Telegram pairing code (valid for 10 min)",
            LogLevel.INFO
        )
        startPolling()
        return code
    }

    fun pairManually(chatId: Long) {
        preferencesManager.setAuthorizedChatId(chatId)
        preferencesManager.setPairingCode(null, 0L)
        EventLogger.log(
            LogCategory.SECURITY,
            "Manually paired authorized Telegram Chat ID: $chatId",
            LogLevel.SUCCESS
        )
        // Send initial confirmation status
        scope.launch {
            sendStartStatus(chatId)
        }
    }

    fun disconnect() {
        stopPolling()
        preferencesManager.disconnectTelegram()
        _isConnected.value = false
        EventLogger.log(LogCategory.SECURITY, "Telegram bot disconnected and credentials purged", LogLevel.WARNING)
    }

    fun startPolling() {
        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) return

        if (pollingJob?.isActive == true) return

        _isPollingActive.value = true
        pollingJob = scope.launch {
            EventLogger.log(LogCategory.TELEGRAM, "Started Telegram command listener", LogLevel.INFO)
            while (isActive) {
                try {
                    val result = apiClient.getUpdates(token, lastUpdateId?.plus(1), limit = 5)
                    result.onSuccess { updates ->
                        _isConnected.value = true
                        for (update in updates) {
                            lastUpdateId = update.updateId
                            handleUpdate(update)
                        }
                    }.onFailure {
                        _isConnected.value = false
                    }
                } catch (e: Exception) {
                    _isConnected.value = false
                }
                delay(3000)
            }
            _isPollingActive.value = false
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _isPollingActive.value = false
    }

    private suspend fun handleUpdate(update: TelegramUpdate) {
        val chatId = update.chatId ?: return
        val rawText = update.text?.trim() ?: return
        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) return

        val config = preferencesManager.telegramConfig.value
        val authorizedChatId = config.authorizedChatId

        // Check if pairing command (/start <code> or /pair <code>)
        if (rawText.startsWith("/start ") || rawText.startsWith("/pair ")) {
            val codeArg = rawText.substringAfter(" ").trim()
            val activeCode = config.activePairingCode
            val expiry = config.pairingCodeExpiry

            if (activeCode != null && codeArg == activeCode && System.currentTimeMillis() <= expiry) {
                // Successful pairing!
                preferencesManager.setAuthorizedChatId(chatId)
                preferencesManager.setPairingCode(null, 0L)
                EventLogger.log(
                    LogCategory.SECURITY,
                    "Successfully paired Telegram chat: $chatId (@${update.userName ?: "user"})",
                    LogLevel.SUCCESS
                )

                // Send confirmation
                val confirmation = "Pairing successful! This chat is now authorized to receive SMS from VANTA.\n\n"
                val sims = simInfoProvider?.invoke() ?: emptyList()
                val isOnline = isForwardingActiveProvider?.invoke() ?: false
                val statusMsg = TelegramEntityHelper.formatStatusMessage(isOnline, sims, confirmation)
                apiClient.sendMessage(token, chatId, statusMsg.text, statusMsg.entities)
                return
            } else {
                apiClient.sendMessage(
                    token,
                    chatId,
                    "Invalid or expired pairing code. Please generate a new code in the VANTA Android app."
                )
                return
            }
        }

        // Check authorization for all other commands
        if (authorizedChatId == null || authorizedChatId != chatId) {
            EventLogger.log(
                LogCategory.SECURITY,
                "Rejected command from unauthorized chat ID: $chatId",
                LogLevel.WARNING
            )
            apiClient.sendMessage(
                token,
                chatId,
                "Unauthorized. Pair this Telegram chat with the VANTA app on your Android device to gain access."
            )
            return
        }

        // Process authorized commands
        when {
            rawText == "/start" -> {
                sendStartStatus(chatId)
            }
            rawText == "/status" -> {
                val isOnline = isForwardingActiveProvider?.invoke() ?: false
                val sims = simInfoProvider?.invoke() ?: emptyList()
                val statusMsg = TelegramEntityHelper.formatStatusMessage(isOnline, sims)
                apiClient.sendMessage(token, chatId, statusMsg.text, statusMsg.entities)
                EventLogger.log(LogCategory.TELEGRAM, "Responded to /status command", LogLevel.INFO)
            }
            rawText == "/help" -> {
                val helpMsg = TelegramEntityHelper.formatHelpMessage()
                apiClient.sendMessage(token, chatId, helpMsg.text, helpMsg.entities)
                EventLogger.log(LogCategory.TELEGRAM, "Responded to /help command", LogLevel.INFO)
            }
            else -> {
                apiClient.sendMessage(
                    token,
                    chatId,
                    "Unknown command. Send /status for live device status or /help for assistance."
                )
            }
        }
    }

    suspend fun sendStartStatus(chatId: Long): Result<TelegramMessage> {
        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) return Result.failure(Exception("No token"))

        val isOnline = isForwardingActiveProvider?.invoke() ?: false
        val sims = simInfoProvider?.invoke() ?: emptyList()
        val formatted = TelegramEntityHelper.formatStatusMessage(isOnline, sims)

        EventLogger.log(LogCategory.TELEGRAM, "Sending /start status message to chat $chatId", LogLevel.INFO)
        return apiClient.sendMessage(token, chatId, formatted.text, formatted.entities)
    }

    suspend fun forwardSms(sender: String, body: String, simLabel: String?): Result<TelegramMessage> {
        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) return Result.failure(Exception("Telegram bot token not configured"))

        val chatId = preferencesManager.telegramConfig.value.authorizedChatId
            ?: return Result.failure(Exception("No authorized Telegram chat paired"))

        val formatted = TelegramEntityHelper.formatIncomingSms(sender, body, simLabel)
        val result = apiClient.sendMessage(token, chatId, formatted.text, formatted.entities)

        result.onSuccess {
            _isConnected.value = true
            preferencesManager.recordDeliverySuccess()
            EventLogger.log(
                LogCategory.SMS_DELIVERY,
                "SMS from '$sender' successfully forwarded to Telegram",
                LogLevel.SUCCESS
            )
        }.onFailure { err ->
            _isConnected.value = false
            EventLogger.log(
                LogCategory.ERROR,
                "Failed to forward SMS to Telegram: ${err.message}",
                LogLevel.ERROR
            )
        }

        return result
    }
}
