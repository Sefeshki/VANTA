package com.example.telegram

import com.example.core.EventLogger
import com.example.core.PreferencesManager
import com.example.data.model.CallLogEntryItem
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SimCardInfo
import com.example.data.model.SimFeature
import com.example.data.model.SmsReplyContext
import com.example.telephony.SmsGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ReplySession(
    val sessionId: String,
    val chatId: Long,
    val userId: Long,
    val recipientAddress: String,
    val recipientName: String?,
    val selectedSlotIndex: Int,
    val promptMessageId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class TelegramBotManager(
    private val preferencesManager: PreferencesManager,
    private val apiClient: TelegramBotApi = TelegramApiClient(),
    autoStartPolling: Boolean = true
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var pollingJob: Job? = null
    private var lastUpdateId: Long? = null

    private val _isPollingActive = MutableStateFlow(false)
    val isPollingActive: StateFlow<Boolean> = _isPollingActive.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val secureRandom = SecureRandom()

    var simInfoProvider: (() -> List<SimCardInfo>)? = null
    var isForwardingActiveProvider: (() -> Boolean)? = null
    var smsGateway: SmsGateway? = null

    private val sessionMutex = Mutex()
    private val activeReplySessions = ConcurrentHashMap<String, ReplySession>()

    // Deduplication guards to avoid duplicate sends from repeated Telegram updates
    private val processedUpdateIds = LinkedHashSet<Long>()
    private val processedMessageKeys = LinkedHashSet<String>()
    private val processedCallbackIds = LinkedHashSet<String>()

    init {
        if (autoStartPolling && preferencesManager.telegramConfig.value.hasToken) {
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
            refreshCustomEmojis(cleanToken)
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

    private fun refreshCustomEmojis(token: String) {
        scope.launch {
            try {
                val res = apiClient.getCustomEmojiStickers(token, TelegramEntityHelper.ALL_CUSTOM_EMOJI_IDS)
                res.onSuccess { emojiMap ->
                    if (emojiMap.isNotEmpty()) {
                        TelegramEntityHelper.updateEmojiCache(emojiMap)
                        EventLogger.log(
                            LogCategory.TELEGRAM,
                            "Custom emoji stickers updated (${emojiMap.size} resolved)",
                            LogLevel.INFO
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }
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
            if (res.isSuccess) {
                refreshCustomEmojis(token)
                if (!_isPollingActive.value) {
                    startPolling()
                }
            }
        }
    }

    fun generatePairingCode(): String {
        val code = String.format(Locale.US, "%06d", secureRandom.nextInt(1_000_000))
        val expiry = System.currentTimeMillis() + (10 * 60 * 1000)
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
        scope.launch {
            sendStartStatus(chatId)
        }
    }

    fun disconnect() {
        stopPolling()
        activeReplySessions.clear()
        preferencesManager.disconnectTelegram()
        _isConnected.value = false
        EventLogger.log(LogCategory.SECURITY, "Telegram bot disconnected and credentials purged", LogLevel.WARNING)
    }

    fun startPolling() {
        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) return

        refreshCustomEmojis(token)

        if (pollingJob?.isActive == true) return

        _isPollingActive.value = true
        pollingJob = scope.launch {
            EventLogger.log(LogCategory.TELEGRAM, "Started Telegram command listener", LogLevel.INFO)
            while (isActive) {
                try {
                    val result = apiClient.getUpdates(token, lastUpdateId?.plus(1), limit = 10)
                    result.onSuccess { updates ->
                        _isConnected.value = true
                        for (update in updates) {
                            lastUpdateId = maxOf(lastUpdateId ?: update.updateId, update.updateId)
                            handleUpdate(update)
                        }
                    }.onFailure {
                        _isConnected.value = false
                    }
                } catch (_: Exception) {
                    _isConnected.value = false
                }
                delay(2500)
            }
            _isPollingActive.value = false
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _isPollingActive.value = false
    }

    fun getActiveSession(chatId: Long, userId: Long): ReplySession? {
        return activeReplySessions[sessionKey(chatId, userId)]
    }

    suspend fun handleUpdate(update: TelegramUpdate) {
        // Deduplicate by updateId
        synchronized(processedUpdateIds) {
            if (!processedUpdateIds.add(update.updateId)) return
            if (processedUpdateIds.size > MAX_DEDUP_CACHE) {
                processedUpdateIds.iterator().let { it.next(); it.remove() }
            }
        }

        if (update.callbackQueryId != null && update.callbackData != null) {
            handleCallbackQuery(update)
            return
        }

        val chatId = update.chatId ?: return
        val userId = update.userId ?: chatId
        val msgId = update.messageId
        if (msgId != null) {
            val dedupMsgKey = "$chatId:$msgId"
            synchronized(processedMessageKeys) {
                if (!processedMessageKeys.add(dedupMsgKey)) return
                if (processedMessageKeys.size > MAX_DEDUP_CACHE) {
                    processedMessageKeys.iterator().let { it.next(); it.remove() }
                }
            }
        }

        val rawText = update.text?.trim() ?: return
        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) return

        val config = preferencesManager.telegramConfig.value
        val authorizedChatId = config.authorizedChatId

        // 1. Handle pairing command (/start <code> or /pair <code>)
        if (rawText.startsWith("/start ") || rawText.startsWith("/pair ")) {
            val codeArg = rawText.substringAfter(" ").trim()
            val activeCode = config.activePairingCode
            val expiry = config.pairingCodeExpiry

            if (activeCode != null && codeArg == activeCode && System.currentTimeMillis() <= expiry) {
                preferencesManager.setAuthorizedChatId(chatId)
                preferencesManager.setPairingCode(null, 0L)
                EventLogger.log(
                    LogCategory.SECURITY,
                    "Successfully paired Telegram chat: $chatId (@${update.userName ?: "user"})",
                    LogLevel.SUCCESS
                )

                val confirmation = "Pairing successful! This chat is now authorized with VANTA.\n\n"
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

        // 2. Authorization gate
        if (authorizedChatId == null || authorizedChatId != chatId) {
            EventLogger.log(
                LogCategory.SECURITY,
                "Rejected message from unauthorized chat ID: $chatId",
                LogLevel.WARNING
            )
            apiClient.sendMessage(
                token,
                chatId,
                "Unauthorized. Pair this Telegram chat with VANTA on your Android device first."
            )
            return
        }

        // 3. Support /cancel at any point
        if (rawText == "/cancel" || rawText.startsWith("/cancel@")) {
            val removed = sessionMutex.withLock {
                activeReplySessions.remove(sessionKey(chatId, userId))
            }
            if (removed != null) {
                if (removed.promptMessageId != null) {
                    apiClient.editMessageText(
                        token = token,
                        chatId = chatId,
                        messageId = removed.promptMessageId,
                        text = "Reply cancelled.",
                        keyboardRows = emptyList()
                    )
                }
                apiClient.sendMessage(
                    token,
                    chatId,
                    "SMS reply session cancelled.",
                    includeGithubButton = false
                )
                EventLogger.log(LogCategory.TELEGRAM, "SMS reply session cancelled via /cancel", LogLevel.INFO)
            } else {
                apiClient.sendMessage(
                    token,
                    chatId,
                    "No active SMS reply session to cancel.",
                    includeGithubButton = false
                )
            }
            return
        }

        // 4. Standard commands (never interpreted as SMS reply text)
        when {
            rawText == "/start" || rawText.startsWith("/start@") -> {
                sendStartStatus(chatId)
                return
            }
            rawText == "/status" || rawText.startsWith("/status@") -> {
                val isOnline = isForwardingActiveProvider?.invoke() ?: false
                val sims = simInfoProvider?.invoke() ?: emptyList()
                val statusMsg = TelegramEntityHelper.formatStatusMessage(isOnline, sims)
                apiClient.sendMessage(token, chatId, statusMsg.text, statusMsg.entities)
                EventLogger.log(LogCategory.TELEGRAM, "Responded to /status command", LogLevel.INFO)
                return
            }
            rawText == "/help" || rawText.startsWith("/help@") -> {
                val helpMsg = TelegramEntityHelper.formatHelpMessage()
                apiClient.sendMessage(token, chatId, helpMsg.text, helpMsg.entities)
                EventLogger.log(LogCategory.TELEGRAM, "Responded to /help command", LogLevel.INFO)
                return
            }
        }

        // 5. Active SMS Reply Session for (chatId, userId)
        val activeSession = sessionMutex.withLock {
            activeReplySessions.remove(sessionKey(chatId, userId))
        }
        if (activeSession != null) {
            if (rawText.startsWith("/")) {
                // Unknown slash command during reply mode: restore session & prompt
                sessionMutex.withLock {
                    activeReplySessions[sessionKey(chatId, userId)] = activeSession
                }
                apiClient.sendMessage(
                    token,
                    chatId,
                    "Still in SMS reply mode. Send your text message to reply, or send /cancel to abort.",
                    includeGithubButton = false
                )
                return
            }

            dispatchSmsReply(token, activeSession, rawText)
            return
        }

        apiClient.sendMessage(
            token,
            chatId,
            "Tap Reply under a forwarded SMS to send a reply, or use /status, /cancel, /help.",
            includeGithubButton = false
        )
    }

    private suspend fun handleCallbackQuery(update: TelegramUpdate) {
        val cbId = update.callbackQueryId ?: return
        val data = update.callbackData ?: return
        val chatId = update.chatId ?: return
        val userId = update.userId ?: chatId

        synchronized(processedCallbackIds) {
            if (!processedCallbackIds.add(cbId)) return
            if (processedCallbackIds.size > MAX_DEDUP_CACHE) {
                processedCallbackIds.iterator().let { it.next(); it.remove() }
            }
        }

        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) return

        val authorizedChatId = preferencesManager.telegramConfig.value.authorizedChatId
        if (authorizedChatId == null || authorizedChatId != chatId) {
            EventLogger.log(
                LogCategory.SECURITY,
                "Rejected callback interaction from unauthorized chat ID: $chatId",
                LogLevel.WARNING
            )
            apiClient.answerCallbackQuery(token, cbId, "Unauthorized", showAlert = true)
            return
        }

        when {
            data.startsWith("reply:") -> {
                val contextId = data.removePrefix("reply:")
                val ctx = preferencesManager.getReplyContext(contextId)
                if (ctx == null) {
                    apiClient.answerCallbackQuery(token, cbId, "Original SMS context expired.", showAlert = true)
                    return
                }

                val gateway = smsGateway
                if (gateway != null && !gateway.canReplyToAddress(ctx.senderAddress)) {
                    apiClient.answerCallbackQuery(
                        token,
                        cbId,
                        "Cannot reply to '${ctx.senderAddress}' (alphanumeric sender).",
                        showAlert = true
                    )
                    return
                }

                val defaultSlot = resolveDefaultReplySlot(ctx.simSlot)
                val sessionId = UUID.randomUUID().toString().take(8)
                val simLabel = formatSimLabelForReply(defaultSlot)

                val prompt = TelegramEntityHelper.formatReplyPrompt(
                    recipientAddress = ctx.senderAddress,
                    recipientName = ctx.senderName,
                    selectedSimLabel = simLabel
                )
                val keyboard = buildReplyPromptKeyboard(sessionId, defaultSlot)

                apiClient.answerCallbackQuery(token, cbId, "Reply mode ready")
                val sent = apiClient.sendMessageWithKeyboard(
                    token = token,
                    chatId = chatId,
                    text = prompt.text,
                    entities = prompt.entities,
                    keyboardRows = keyboard
                )
                val promptMsgId = sent.getOrNull()?.messageId
                val session = ReplySession(
                    sessionId = sessionId,
                    chatId = chatId,
                    userId = userId,
                    recipientAddress = ctx.senderAddress,
                    recipientName = ctx.senderName,
                    selectedSlotIndex = defaultSlot,
                    promptMessageId = promptMsgId
                )
                sessionMutex.withLock {
                    activeReplySessions[sessionKey(chatId, userId)] = session
                }
                EventLogger.log(
                    LogCategory.TELEGRAM,
                    "Entered SMS reply session for SIM ${defaultSlot + 1}",
                    LogLevel.INFO
                )
            }

            data.startsWith("rsim:") -> {
                val parts = data.removePrefix("rsim:").split(":")
                if (parts.size != 2) {
                    apiClient.answerCallbackQuery(token, cbId, "Invalid SIM selection")
                    return
                }
                val sessionId = parts[0]
                val targetSlot = parts[1].toIntOrNull() ?: 0
                if (targetSlot !in 0..1) {
                    apiClient.answerCallbackQuery(token, cbId, "Invalid SIM slot")
                    return
                }

                val key = sessionKey(chatId, userId)
                val updated = sessionMutex.withLock {
                    val existing = activeReplySessions[key]
                    if (existing != null && existing.sessionId == sessionId) {
                        val next = existing.copy(selectedSlotIndex = targetSlot)
                        activeReplySessions[key] = next
                        next
                    } else {
                        null
                    }
                }

                if (updated == null) {
                    apiClient.answerCallbackQuery(token, cbId, "Reply session expired.", showAlert = true)
                    return
                }

                val simLabel = formatSimLabelForReply(targetSlot)
                apiClient.answerCallbackQuery(token, cbId, "Selected SIM ${targetSlot + 1}")
                val prompt = TelegramEntityHelper.formatReplyPrompt(
                    recipientAddress = updated.recipientAddress,
                    recipientName = updated.recipientName,
                    selectedSimLabel = simLabel
                )
                val msgIdToEdit = update.callbackMessageId ?: updated.promptMessageId
                if (msgIdToEdit != null) {
                    apiClient.editMessageText(
                        token = token,
                        chatId = chatId,
                        messageId = msgIdToEdit,
                        text = prompt.text,
                        entities = prompt.entities,
                        keyboardRows = buildReplyPromptKeyboard(sessionId, targetSlot)
                    )
                }
            }

            data.startsWith("rcancel:") -> {
                val sessionId = data.removePrefix("rcancel:")
                val key = sessionKey(chatId, userId)
                val removed = sessionMutex.withLock {
                    val existing = activeReplySessions[key]
                    if (existing != null && existing.sessionId == sessionId) {
                        activeReplySessions.remove(key)
                    } else {
                        null
                    }
                }

                apiClient.answerCallbackQuery(token, cbId, "Reply cancelled")
                val msgIdToEdit = update.callbackMessageId ?: removed?.promptMessageId
                if (msgIdToEdit != null) {
                    apiClient.editMessageText(
                        token = token,
                        chatId = chatId,
                        messageId = msgIdToEdit,
                        text = "SMS reply cancelled.",
                        keyboardRows = emptyList()
                    )
                }
                EventLogger.log(LogCategory.TELEGRAM, "SMS reply session cancelled via button", LogLevel.INFO)
            }

            else -> {
                apiClient.answerCallbackQuery(token, cbId)
            }
        }
    }

    private suspend fun dispatchSmsReply(
        token: String,
        session: ReplySession,
        replyText: String
    ) {
        val gateway = smsGateway
        if (gateway == null) {
            apiClient.sendMessage(
                token,
                session.chatId,
                "SMS sending service is unavailable right now.",
                includeGithubButton = false
            )
            return
        }

        if (session.promptMessageId != null) {
            apiClient.editMessageText(
                token = token,
                chatId = session.chatId,
                messageId = session.promptMessageId,
                text = "Sending SMS via SIM ${session.selectedSlotIndex + 1}...",
                keyboardRows = emptyList()
            )
        }

        val sendResult = gateway.sendSms(
            destination = session.recipientAddress,
            messageText = replyText,
            slotIndex = session.selectedSlotIndex
        )

        val target = TelegramEntityHelper.formatSenderDisplay(session.recipientAddress, session.recipientName)
        sendResult.onSuccess { info ->
            val partNote = if (info.partCount > 1) " (${info.partCount} parts)" else ""
            val report = "✓ SMS delivered to carrier\nTo: $target\nVia: SIM ${info.slotIndex + 1} (${info.carrierName})$partNote"
            if (session.promptMessageId != null) {
                apiClient.editMessageText(
                    token = token,
                    chatId = session.chatId,
                    messageId = session.promptMessageId,
                    text = report,
                    keyboardRows = emptyList()
                )
            } else {
                apiClient.sendMessage(token, session.chatId, report, includeGithubButton = false)
            }
        }.onFailure { err ->
            val reason = err.message ?: "Unknown sending failure"
            val report = "✕ SMS failed to send\nTo: $target\nVia: SIM ${session.selectedSlotIndex + 1}\nReason: $reason"
            if (session.promptMessageId != null) {
                apiClient.editMessageText(
                    token = token,
                    chatId = session.chatId,
                    messageId = session.promptMessageId,
                    text = report,
                    keyboardRows = emptyList()
                )
            } else {
                apiClient.sendMessage(token, session.chatId, report, includeGithubButton = false)
            }
        }
    }

    private fun resolveDefaultReplySlot(originalSlot: Int): Int {
        val simControl = preferencesManager.preferences.value.simControl
        if (originalSlot in 0..1) {
            val origSettings = simControl.forSlot(originalSlot)
            if (origSettings.isFeatureEffective(SimFeature.SMS_SENDING)) {
                return originalSlot
            }
        }
        if (simControl.sim1.isFeatureEffective(SimFeature.SMS_SENDING)) return 0
        if (simControl.sim2.isFeatureEffective(SimFeature.SMS_SENDING)) return 1
        return if (originalSlot in 0..1) originalSlot else 0
    }

    private fun formatSimLabelForReply(slotIndex: Int): String {
        val sims = simInfoProvider?.invoke().orEmpty()
        val sim = sims.firstOrNull { it.slotIndex == slotIndex }
        val carrier = sim?.carrierName?.takeIf { it.isNotBlank() }
        val base = if (carrier != null) "SIM ${slotIndex + 1} · $carrier" else "SIM ${slotIndex + 1}"
        val simSettings = preferencesManager.preferences.value.simControl.forSlot(slotIndex)
        return when {
            sim != null && !sim.hasPermission -> "$base (Permission needed)"
            sim != null && !sim.isAvailable -> "$base (Unavailable)"
            !simSettings.masterEnabled -> "$base (SIM Disabled)"
            !simSettings.smsSendingEnabled -> "$base (Sending Disabled)"
            else -> base
        }
    }

    private fun buildReplyPromptKeyboard(sessionId: String, selectedSlot: Int): List<List<InlineButton>> {
        val sims = simInfoProvider?.invoke().orEmpty()
        val hasDualSlots = sims.size >= 2 || sims.any { it.slotIndex == 1 }
        val rows = mutableListOf<List<InlineButton>>()

        if (hasDualSlots) {
            val s1 = sims.firstOrNull { it.slotIndex == 0 }
            val s2 = sims.firstOrNull { it.slotIndex == 1 }
            val s1Label = buildString {
                if (selectedSlot == 0) append("✓ ")
                append("SIM 1")
                if (s1?.isAvailable == true && s1.carrierName.isNotBlank()) append(" (${s1.carrierName})")
            }
            val s2Label = buildString {
                if (selectedSlot == 1) append("✓ ")
                append("SIM 2")
                if (s2?.isAvailable == true && s2.carrierName.isNotBlank()) append(" (${s2.carrierName})")
            }
            rows.add(
                listOf(
                    InlineButton(text = s1Label, callbackData = "rsim:$sessionId:0"),
                    InlineButton(text = s2Label, callbackData = "rsim:$sessionId:1")
                )
            )
        }

        rows.add(listOf(InlineButton(text = "Cancel", callbackData = "rcancel:$sessionId")))
        return rows
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

    suspend fun forwardSms(
        sender: String,
        body: String,
        simLabel: String?,
        contactName: String? = null,
        receivedTimestamp: Long = System.currentTimeMillis(),
        isOtp: Boolean = false,
        simSlot: Int = -1,
        subId: Int = -1
    ): Result<TelegramMessage> {
        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) return Result.failure(Exception("Telegram bot token not configured"))

        val chatId = preferencesManager.telegramConfig.value.authorizedChatId
            ?: return Result.failure(Exception("No authorized Telegram chat paired"))

        val contextId = UUID.randomUUID().toString().replace("-", "").take(10)
        preferencesManager.saveReplyContext(
            SmsReplyContext(
                contextId = contextId,
                senderAddress = sender,
                senderName = contactName,
                simSlot = simSlot,
                subId = subId,
                receivedTimestamp = receivedTimestamp
            )
        )

        val formatted = TelegramEntityHelper.formatIncomingSms(
            sender = sender,
            body = body,
            simLabel = simLabel,
            contactName = contactName,
            receivedTimestamp = receivedTimestamp,
            isOtp = isOtp
        )
        val replyKeyboard = listOf(
            listOf(InlineButton(text = "Reply", callbackData = "reply:$contextId"))
        )

        val result = apiClient.sendMessageWithKeyboard(
            token = token,
            chatId = chatId,
            text = formatted.text,
            entities = formatted.entities,
            keyboardRows = replyKeyboard
        )

        result.onSuccess {
            _isConnected.value = true
            preferencesManager.recordDeliverySuccess()
            EventLogger.log(
                LogCategory.SMS_DELIVERY,
                "SMS from '$sender' forwarded to Telegram",
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

    suspend fun forwardCallLog(entry: CallLogEntryItem, simLabel: String): Result<TelegramMessage> {
        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) return Result.failure(Exception("Telegram bot token not configured"))

        val chatId = preferencesManager.telegramConfig.value.authorizedChatId
            ?: return Result.failure(Exception("No authorized Telegram chat paired"))

        val formatted = TelegramEntityHelper.formatCallLogMessage(entry, simLabel)
        val result = apiClient.sendMessage(
            token = token,
            chatId = chatId,
            text = formatted.text,
            entities = formatted.entities,
            includeGithubButton = false
        )

        result.onSuccess {
            _isConnected.value = true
            preferencesManager.recordDeliverySuccess()
            EventLogger.log(
                LogCategory.CALLS,
                "${entry.callType.label} from '${entry.number}' forwarded to Telegram",
                LogLevel.SUCCESS
            )
        }.onFailure { err ->
            _isConnected.value = false
            EventLogger.log(
                LogCategory.ERROR,
                "Failed to forward call log to Telegram: ${err.message}",
                LogLevel.ERROR
            )
        }
        return result
    }

    suspend fun forwardIncomingCallNotification(
        callerNumber: String?,
        contactName: String?,
        simLabel: String,
        timestamp: Long
    ): Result<TelegramMessage> {
        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) return Result.failure(Exception("Telegram bot token not configured"))

        val chatId = preferencesManager.telegramConfig.value.authorizedChatId
            ?: return Result.failure(Exception("No authorized Telegram chat paired"))

        val formatted = TelegramEntityHelper.formatIncomingCallNotification(
            callerNumber = callerNumber,
            contactName = contactName,
            simLabel = simLabel,
            timestamp = timestamp
        )
        val result = apiClient.sendMessage(
            token = token,
            chatId = chatId,
            text = formatted.text,
            entities = formatted.entities,
            includeGithubButton = false
        )

        result.onSuccess {
            _isConnected.value = true
            EventLogger.log(
                LogCategory.CALLS,
                "Incoming call alert forwarded to Telegram",
                LogLevel.SUCCESS
            )
        }.onFailure { err ->
            _isConnected.value = false
            EventLogger.log(
                LogCategory.ERROR,
                "Failed to send incoming call alert: ${err.message}",
                LogLevel.ERROR
            )
        }
        return result
    }

    private fun sessionKey(chatId: Long, userId: Long): String = "$chatId:$userId"

    companion object {
        private const val MAX_DEDUP_CACHE = 200
    }
}
