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
    val contextId: String,
    val chatId: Long,
    val userId: Long,
    val recipientAddress: String,
    val recipientName: String?,
    val selectedSlotIndex: Int,
    val notificationMessageId: Long? = null,
    val promptMessageId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ForwardSession(
    val sessionId: String,
    val contextId: String,
    val chatId: Long,
    val userId: Long,
    val awaitingManualChatId: Boolean = false,
    val selectedDestinationChatId: Long? = null,
    val selectedDestinationLabel: String? = null,
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
    var isServiceRunningProvider: (() -> Boolean)? = null
    var serviceStartedElapsedProvider: (() -> Long?)? = null
    var isInternetAvailableProvider: (() -> Boolean)? = null
    var pendingQueueCountProvider: (() -> Int)? = null
    var onSetForwardingEnabled: ((Boolean) -> Unit)? = null
    var onToggleSimMaster: ((Int, Boolean) -> Unit)? = null
    var onToggleOtpProtection: ((Boolean) -> Unit)? = null
    var smsGateway: SmsGateway? = null

    private val sessionMutex = Mutex()
    private val activeReplySessions = ConcurrentHashMap<String, ReplySession>()
    private val activeForwardSessions = ConcurrentHashMap<String, ForwardSession>()
    private val forwardInFlightKeys = ConcurrentHashMap.newKeySet<String>()
    private val replyInFlightKeys = ConcurrentHashMap.newKeySet<String>()

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
        activeForwardSessions.clear()
        forwardInFlightKeys.clear()
        replyInFlightKeys.clear()
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

    fun getActiveForwardSession(chatId: Long, userId: Long): ForwardSession? {
        return activeForwardSessions[sessionKey(chatId, userId)]
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

                val confirmation = "Pairing successful! This chat is now authorized with VANTA."
                sendMenuPanel(token, chatId, BotMenuPanel.OVERVIEW, customNote = confirmation)
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

        // 3. Support /cancel fallback if triggered during an active session
        if (rawText == "/cancel" || rawText.startsWith("/cancel@")) {
            val (removedReply, removedForward) = sessionMutex.withLock {
                val key = sessionKey(chatId, userId)
                activeReplySessions.remove(key) to activeForwardSessions.remove(key)
            }
            if (removedReply != null) {
                if (removedReply.promptMessageId != null) {
                    apiClient.editMessageText(
                        token = token,
                        chatId = chatId,
                        messageId = removedReply.promptMessageId,
                        text = "Canceled.",
                        keyboardRows = emptyList()
                    )
                } else {
                    apiClient.sendMessage(token, chatId, "Canceled.", includeGithubButton = false)
                }
                EventLogger.log(LogCategory.TELEGRAM, "SMS reply session cancelled", LogLevel.INFO)
            } else if (removedForward != null) {
                if (removedForward.promptMessageId != null) {
                    apiClient.editMessageText(
                        token = token,
                        chatId = chatId,
                        messageId = removedForward.promptMessageId,
                        text = "Canceled.",
                        keyboardRows = emptyList()
                    )
                } else {
                    apiClient.sendMessage(token, chatId, "Canceled.", includeGithubButton = false)
                }
                EventLogger.log(LogCategory.TELEGRAM, "SMS forward session cancelled", LogLevel.INFO)
            } else {
                sendMenuPanel(token, chatId, BotMenuPanel.OVERVIEW)
            }
            return
        }

        // 4. Entry commands open interactive inline-keyboard menu (no slash commands required for normal operation)
        when {
            rawText == "/start" || rawText.startsWith("/start@") ||
                rawText == "/menu" || rawText.startsWith("/menu@") ||
                rawText == "/status" || rawText.startsWith("/status@") -> {
                sendMenuPanel(token, chatId, BotMenuPanel.OVERVIEW)
                EventLogger.log(LogCategory.TELEGRAM, "Sent interactive VANTA Overview panel", LogLevel.INFO)
                return
            }
            rawText == "/help" || rawText.startsWith("/help@") -> {
                sendMenuPanel(token, chatId, BotMenuPanel.HELP)
                EventLogger.log(LogCategory.TELEGRAM, "Sent interactive VANTA Help panel", LogLevel.INFO)
                return
            }
        }

        // 5. Active Forward Session awaiting numeric destination Chat ID
        val activeForward = sessionMutex.withLock {
            val key = sessionKey(chatId, userId)
            val existing = activeForwardSessions[key]
            if (existing != null && existing.awaitingManualChatId) existing else null
        }
        if (activeForward != null) {
            if (rawText.startsWith("/")) {
                apiClient.sendMessage(
                    token,
                    chatId,
                    "Waiting for a numeric destination Chat ID (or tap Cancel below).",
                    includeGithubButton = false
                )
                return
            }

            val parsedDestId = rawText.trim().toLongOrNull()
            if (parsedDestId == null || parsedDestId == 0L) {
                apiClient.sendMessage(
                    token,
                    chatId,
                    "Invalid Chat ID '$rawText'. Enter a valid numeric Chat ID (e.g., 123456789 or -1001234567890) or tap Cancel.",
                    includeGithubButton = false
                )
                return
            }

            val ctx = preferencesManager.getReplyContext(activeForward.contextId)
            if (ctx == null) {
                sessionMutex.withLock { activeForwardSessions.remove(sessionKey(chatId, userId)) }
                apiClient.sendMessage(
                    token,
                    chatId,
                    "Original SMS context expired.",
                    includeGithubButton = false
                )
                return
            }

            val destLabel = resolveDestinationLabel(parsedDestId)
            val updated = activeForward.copy(
                awaitingManualChatId = false,
                selectedDestinationChatId = parsedDestId,
                selectedDestinationLabel = destLabel
            )
            sessionMutex.withLock {
                activeForwardSessions[sessionKey(chatId, userId)] = updated
            }

            val simLabel = ctx.simLabel ?: formatContextSimLabel(ctx)
            val confirmPrompt = TelegramEntityHelper.formatForwardConfirmPrompt(
                sender = ctx.senderAddress,
                contactName = ctx.senderName,
                simLabel = simLabel,
                destinationLabel = destLabel,
                destinationChatId = parsedDestId,
                alreadyForwarded = ctx.forwardedChatIds.contains(parsedDestId)
            )
            val keyboard = buildForwardConfirmKeyboard(updated.sessionId, parsedDestId)
            if (updated.promptMessageId != null) {
                apiClient.editMessageText(
                    token = token,
                    chatId = chatId,
                    messageId = updated.promptMessageId,
                    text = confirmPrompt.text,
                    entities = confirmPrompt.entities,
                    keyboardRows = keyboard
                )
            } else {
                apiClient.sendMessageWithKeyboard(
                    token = token,
                    chatId = chatId,
                    text = confirmPrompt.text,
                    entities = confirmPrompt.entities,
                    keyboardRows = keyboard
                )
            }
            return
        }

        // 6. Active SMS Reply Session for (chatId, userId)
        val activeSession = sessionMutex.withLock {
            activeReplySessions.remove(sessionKey(chatId, userId))
        }
        if (activeSession != null) {
            val inFlightKey = "${chatId}:${userId}:${activeSession.sessionId}"
            if (!replyInFlightKeys.add(inFlightKey)) {
                return
            }
            try {
                dispatchSmsReply(token, activeSession, rawText)
            } finally {
                replyInFlightKeys.remove(inFlightKey)
            }
            return
        }

        // 7. Default idle text opens the button-driven Overview panel (replaces slash commands)
        sendMenuPanel(token, chatId, BotMenuPanel.OVERVIEW)
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
            data.startsWith("menu:") -> {
                handleMenuCallback(token, chatId, update.callbackMessageId, cbId, data)
            }

            data.startsWith("act:") -> {
                handleControlActionCallback(token, chatId, update.callbackMessageId, cbId, data)
            }

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

                val key = sessionKey(chatId, userId)
                var previousPromptToClean: Long? = null
                val alreadyActive = sessionMutex.withLock {
                    val existing = activeReplySessions[key]
                    if (existing != null && existing.contextId == contextId) {
                        true
                    } else {
                        previousPromptToClean = existing?.promptMessageId
                        false
                    }
                }
                if (alreadyActive) {
                    apiClient.answerCallbackQuery(token, cbId, "Reply prompt is already active.")
                    return
                }
                if (previousPromptToClean != null) {
                    apiClient.deleteMessage(token, chatId, previousPromptToClean!!)
                }

                val sims = simInfoProvider?.invoke().orEmpty()
                val slotBySub = if (ctx.subId >= 0) {
                    sims.firstOrNull { it.subId == ctx.subId }?.slotIndex
                } else {
                    null
                }
                val originalSlot = slotBySub ?: ctx.simSlot
                val defaultSlot = resolveDefaultReplySlot(originalSlot)
                val sessionId = UUID.randomUUID().toString().take(8)

                val prompt = TelegramEntityHelper.formatReplyPrompt()
                val keyboard = buildReplyPromptKeyboard(sessionId)

                apiClient.answerCallbackQuery(token, cbId, "Send your reply")
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
                    contextId = contextId,
                    chatId = chatId,
                    userId = userId,
                    recipientAddress = ctx.senderAddress,
                    recipientName = ctx.senderName,
                    selectedSlotIndex = defaultSlot,
                    notificationMessageId = update.callbackMessageId ?: ctx.notificationMessageId,
                    promptMessageId = promptMsgId
                )
                sessionMutex.withLock {
                    activeForwardSessions.remove(key)
                    activeReplySessions[key] = session
                }
                EventLogger.log(
                    LogCategory.TELEGRAM,
                    "Entered SMS reply session for SIM ${defaultSlot + 1} (subId=${ctx.subId})",
                    LogLevel.INFO
                )
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

                if (removed == null) {
                    apiClient.answerCallbackQuery(token, cbId, "Reply session expired.")
                    val staleMsgId = update.callbackMessageId
                    if (staleMsgId != null) {
                        apiClient.editMessageText(
                            token = token,
                            chatId = chatId,
                            messageId = staleMsgId,
                            text = "Canceled.",
                            keyboardRows = emptyList()
                        )
                    }
                    return
                }

                apiClient.answerCallbackQuery(token, cbId, "Canceled.")
                val msgIdToEdit = update.callbackMessageId ?: removed.promptMessageId
                if (msgIdToEdit != null) {
                    apiClient.editMessageText(
                        token = token,
                        chatId = chatId,
                        messageId = msgIdToEdit,
                        text = "Canceled.",
                        keyboardRows = emptyList()
                    )
                } else {
                    apiClient.sendMessage(token, chatId, "Canceled.", includeGithubButton = false)
                }
                EventLogger.log(LogCategory.TELEGRAM, "SMS reply session cancelled via button", LogLevel.INFO)
            }

            data.startsWith("fwd:") -> {
                val contextId = data.removePrefix("fwd:")
                val ctx = preferencesManager.getReplyContext(contextId)
                if (ctx == null || ctx.messageBody.isBlank()) {
                    apiClient.answerCallbackQuery(token, cbId, "Original SMS content expired or unavailable.", showAlert = true)
                    return
                }

                val sessionId = UUID.randomUUID().toString().take(8)
                val destinations = preferencesManager.telegramConfig.value.forwardDestinations
                val prompt = TelegramEntityHelper.formatForwardPrompt(
                    sender = ctx.senderAddress,
                    contactName = ctx.senderName,
                    simLabel = formatContextSimLabel(ctx),
                    hasSavedDestinations = destinations.isNotEmpty()
                )
                val keyboard = buildForwardDestinationsKeyboard(sessionId, destinations)

                apiClient.answerCallbackQuery(token, cbId, "Select destination")
                val sent = apiClient.sendMessageWithKeyboard(
                    token = token,
                    chatId = chatId,
                    text = prompt.text,
                    entities = prompt.entities,
                    keyboardRows = keyboard
                )
                val promptMsgId = sent.getOrNull()?.messageId
                val session = ForwardSession(
                    sessionId = sessionId,
                    contextId = contextId,
                    chatId = chatId,
                    userId = userId,
                    promptMessageId = promptMsgId
                )
                sessionMutex.withLock {
                    val key = sessionKey(chatId, userId)
                    activeReplySessions.remove(key)
                    activeForwardSessions[key] = session
                }
                EventLogger.log(LogCategory.TELEGRAM, "Started SMS forward selection flow", LogLevel.INFO)
            }

            data.startsWith("fwdmanual:") -> {
                val sessionId = data.removePrefix("fwdmanual:")
                val key = sessionKey(chatId, userId)
                val updated = sessionMutex.withLock {
                    val existing = activeForwardSessions[key]
                    if (existing != null && existing.sessionId == sessionId) {
                        val next = existing.copy(awaitingManualChatId = true, selectedDestinationChatId = null)
                        activeForwardSessions[key] = next
                        next
                    } else null
                }
                if (updated == null) {
                    apiClient.answerCallbackQuery(token, cbId, "Forward session expired.", showAlert = true)
                    return
                }
                val ctx = preferencesManager.getReplyContext(updated.contextId)
                if (ctx == null) {
                    apiClient.answerCallbackQuery(token, cbId, "Original SMS context expired.", showAlert = true)
                    return
                }
                apiClient.answerCallbackQuery(token, cbId, "Send destination Chat ID")
                val prompt = TelegramEntityHelper.formatForwardManualChatIdPrompt(
                    sender = ctx.senderAddress,
                    contactName = ctx.senderName,
                    simLabel = formatContextSimLabel(ctx)
                )
                val msgIdToEdit = update.callbackMessageId ?: updated.promptMessageId
                if (msgIdToEdit != null) {
                    apiClient.editMessageText(
                        token = token,
                        chatId = chatId,
                        messageId = msgIdToEdit,
                        text = prompt.text,
                        entities = prompt.entities,
                        keyboardRows = listOf(
                            listOf(
                                InlineButton(text = "Back", callbackData = "fwdback:$sessionId"),
                                InlineButton(text = "Cancel", callbackData = "fwdcancel:$sessionId")
                            )
                        )
                    )
                }
            }

            data.startsWith("fwdsel:") -> {
                val parts = data.removePrefix("fwdsel:").split(":")
                if (parts.size != 2) {
                    apiClient.answerCallbackQuery(token, cbId, "Invalid destination selection")
                    return
                }
                val sessionId = parts[0]
                val destChatId = parts[1].toLongOrNull()
                if (destChatId == null || destChatId == 0L) {
                    apiClient.answerCallbackQuery(token, cbId, "Invalid destination Chat ID")
                    return
                }

                val key = sessionKey(chatId, userId)
                val updated = sessionMutex.withLock {
                    val existing = activeForwardSessions[key]
                    if (existing != null && existing.sessionId == sessionId) {
                        val label = resolveDestinationLabel(destChatId)
                        val next = existing.copy(
                            awaitingManualChatId = false,
                            selectedDestinationChatId = destChatId,
                            selectedDestinationLabel = label
                        )
                        activeForwardSessions[key] = next
                        next
                    } else null
                }
                if (updated == null) {
                    apiClient.answerCallbackQuery(token, cbId, "Forward session expired.", showAlert = true)
                    return
                }

                val ctx = preferencesManager.getReplyContext(updated.contextId)
                if (ctx == null) {
                    apiClient.answerCallbackQuery(token, cbId, "Original SMS context expired.", showAlert = true)
                    return
                }

                apiClient.answerCallbackQuery(token, cbId, "Confirm forward")
                val prompt = TelegramEntityHelper.formatForwardConfirmPrompt(
                    sender = ctx.senderAddress,
                    contactName = ctx.senderName,
                    simLabel = formatContextSimLabel(ctx),
                    destinationLabel = updated.selectedDestinationLabel ?: destChatId.toString(),
                    destinationChatId = destChatId,
                    alreadyForwarded = ctx.forwardedChatIds.contains(destChatId)
                )
                val msgIdToEdit = update.callbackMessageId ?: updated.promptMessageId
                if (msgIdToEdit != null) {
                    apiClient.editMessageText(
                        token = token,
                        chatId = chatId,
                        messageId = msgIdToEdit,
                        text = prompt.text,
                        entities = prompt.entities,
                        keyboardRows = buildForwardConfirmKeyboard(sessionId, destChatId)
                    )
                }
            }

            data.startsWith("fwdback:") -> {
                val sessionId = data.removePrefix("fwdback:")
                val key = sessionKey(chatId, userId)
                val updated = sessionMutex.withLock {
                    val existing = activeForwardSessions[key]
                    if (existing != null && existing.sessionId == sessionId) {
                        val next = existing.copy(
                            awaitingManualChatId = false,
                            selectedDestinationChatId = null,
                            selectedDestinationLabel = null
                        )
                        activeForwardSessions[key] = next
                        next
                    } else null
                }
                if (updated == null) {
                    apiClient.answerCallbackQuery(token, cbId, "Forward session expired.", showAlert = true)
                    return
                }
                val ctx = preferencesManager.getReplyContext(updated.contextId)
                if (ctx == null) {
                    apiClient.answerCallbackQuery(token, cbId, "Original SMS context expired.", showAlert = true)
                    return
                }
                val destinations = preferencesManager.telegramConfig.value.forwardDestinations
                val prompt = TelegramEntityHelper.formatForwardPrompt(
                    sender = ctx.senderAddress,
                    contactName = ctx.senderName,
                    simLabel = formatContextSimLabel(ctx),
                    hasSavedDestinations = destinations.isNotEmpty()
                )
                apiClient.answerCallbackQuery(token, cbId)
                val msgIdToEdit = update.callbackMessageId ?: updated.promptMessageId
                if (msgIdToEdit != null) {
                    apiClient.editMessageText(
                        token = token,
                        chatId = chatId,
                        messageId = msgIdToEdit,
                        text = prompt.text,
                        entities = prompt.entities,
                        keyboardRows = buildForwardDestinationsKeyboard(sessionId, destinations)
                    )
                }
            }

            data.startsWith("fwdgo:") -> {
                val parts = data.removePrefix("fwdgo:").split(":")
                if (parts.size != 2) {
                    apiClient.answerCallbackQuery(token, cbId, "Invalid forward request")
                    return
                }
                val sessionId = parts[0]
                val destChatId = parts[1].toLongOrNull()
                if (destChatId == null || destChatId == 0L) {
                    apiClient.answerCallbackQuery(token, cbId, "Invalid destination Chat ID")
                    return
                }

                val key = sessionKey(chatId, userId)
                val session = sessionMutex.withLock {
                    val existing = activeForwardSessions[key]
                    if (existing != null && existing.sessionId == sessionId) existing else null
                }
                if (session == null) {
                    apiClient.answerCallbackQuery(token, cbId, "Forward session expired.", showAlert = true)
                    return
                }

                val ctx = preferencesManager.getReplyContext(session.contextId)
                if (ctx == null || ctx.messageBody.isBlank()) {
                    sessionMutex.withLock { activeForwardSessions.remove(key) }
                    apiClient.answerCallbackQuery(token, cbId, "Original SMS context expired.", showAlert = true)
                    return
                }

                val inFlightKey = "${session.contextId}:$destChatId"
                if (ctx.forwardedChatIds.contains(destChatId) || !forwardInFlightKeys.add(inFlightKey)) {
                    sessionMutex.withLock { activeForwardSessions.remove(key) }
                    apiClient.answerCallbackQuery(token, cbId, "Already forwarded to this chat.", showAlert = true)
                    val msgIdToEdit = update.callbackMessageId ?: session.promptMessageId
                    if (msgIdToEdit != null) {
                        apiClient.editMessageText(
                            token = token,
                            chatId = chatId,
                            messageId = msgIdToEdit,
                            text = "Already forwarded to destination ($destChatId). Duplicate send prevented.",
                            keyboardRows = emptyList()
                        )
                    }
                    return
                }

                val destLabel = session.selectedDestinationLabel ?: resolveDestinationLabel(destChatId)
                val destDisplay = if (destLabel != destChatId.toString()) "$destLabel ($destChatId)" else destChatId.toString()

                try {
                    apiClient.answerCallbackQuery(token, cbId, "Forwarding SMS...")
                    val forwardedMsg = TelegramEntityHelper.formatForwardedSmsToChat(
                        sender = ctx.senderAddress,
                        contactName = ctx.senderName,
                        body = ctx.messageBody,
                        simLabel = formatContextSimLabel(ctx),
                        receivedTimestamp = ctx.receivedTimestamp,
                        isOtp = ctx.isOtp
                    )

                    val sendResult = apiClient.sendMessage(
                        token = token,
                        chatId = destChatId,
                        text = forwardedMsg.text,
                        entities = forwardedMsg.entities,
                        includeGithubButton = false
                    )

                    val msgIdToEdit = update.callbackMessageId ?: session.promptMessageId
                    sendResult.onSuccess {
                        preferencesManager.markReplyContextForwarded(session.contextId, destChatId)
                        sessionMutex.withLock { activeForwardSessions.remove(key) }
                        if (msgIdToEdit != null) {
                            apiClient.editMessageText(
                                token = token,
                                chatId = chatId,
                                messageId = msgIdToEdit,
                                text = "✓ Forwarded SMS via VANTA to $destDisplay",
                                keyboardRows = emptyList()
                            )
                        }
                        EventLogger.log(
                            LogCategory.TELEGRAM,
                            "Forwarded SMS from '${ctx.senderAddress}' to Telegram destination $destChatId",
                            LogLevel.SUCCESS
                        )
                    }.onFailure { err ->
                        val reason = err.message ?: "Telegram API rejected destination"
                        if (msgIdToEdit != null) {
                            apiClient.editMessageText(
                                token = token,
                                chatId = chatId,
                                messageId = msgIdToEdit,
                                text = "✕ Failed to forward SMS\nDestination: $destDisplay\nReason: $reason",
                                keyboardRows = listOf(
                                    listOf(
                                        InlineButton(text = "Back", callbackData = "fwdback:$sessionId"),
                                        InlineButton(text = "Cancel", callbackData = "fwdcancel:$sessionId")
                                    )
                                )
                            )
                        }
                        EventLogger.log(
                            LogCategory.ERROR,
                            "SMS forward to destination $destChatId failed: $reason",
                            LogLevel.ERROR
                        )
                    }
                } finally {
                    forwardInFlightKeys.remove(inFlightKey)
                }
            }

            data.startsWith("fwdcancel:") -> {
                val sessionId = data.removePrefix("fwdcancel:")
                val key = sessionKey(chatId, userId)
                val removed = sessionMutex.withLock {
                    val existing = activeForwardSessions[key]
                    if (existing != null && existing.sessionId == sessionId) {
                        activeForwardSessions.remove(key)
                    } else null
                }

                apiClient.answerCallbackQuery(token, cbId, "Forward cancelled")
                val msgIdToEdit = update.callbackMessageId ?: removed?.promptMessageId
                if (msgIdToEdit != null) {
                    apiClient.editMessageText(
                        token = token,
                        chatId = chatId,
                        messageId = msgIdToEdit,
                        text = "SMS forward cancelled.",
                        keyboardRows = emptyList()
                    )
                }
                EventLogger.log(LogCategory.TELEGRAM, "SMS forward session cancelled via button", LogLevel.INFO)
            }

            else -> {
                apiClient.answerCallbackQuery(token, cbId, "Action expired or unavailable.")
            }
        }
    }

    private suspend fun handleMenuCallback(
        token: String,
        chatId: Long,
        messageId: Long?,
        cbId: String,
        data: String
    ) {
        val rawTarget = data.removePrefix("menu:")
        val isRefresh = rawTarget.startsWith("refresh:")
        val panel = BotMenuPanel.fromId(rawTarget.removePrefix("refresh:"))

        val feedback = if (isRefresh) "Updated" else null
        apiClient.answerCallbackQuery(token, cbId, feedback)

        if (messageId != null) {
            editMenuPanel(token, chatId, messageId, panel)
        } else {
            sendMenuPanel(token, chatId, panel)
        }
    }

    private suspend fun handleControlActionCallback(
        token: String,
        chatId: Long,
        messageId: Long?,
        cbId: String,
        data: String
    ) {
        val parts = data.removePrefix("act:").split(":")
        val action = parts.getOrNull(0) ?: ""

        when (action) {
            "fwd_toggle", "svc_toggle" -> {
                val origin = BotMenuPanel.fromId(parts.getOrNull(1))
                val target = !preferencesManager.preferences.value.isForwardingEnabled
                onSetForwardingEnabled?.invoke(target) ?: preferencesManager.setForwardingEnabled(target)
                val toast = if (target) "Forwarding enabled" else "Forwarding stopped"
                apiClient.answerCallbackQuery(token, cbId, toast)
                if (messageId != null) {
                    editMenuPanel(token, chatId, messageId, origin)
                }
            }

            "sim_toggle" -> {
                val slot = parts.getOrNull(1)?.toIntOrNull()
                val origin = BotMenuPanel.fromId(parts.getOrNull(2))
                if (slot == null || slot !in 0..1) {
                    apiClient.answerCallbackQuery(token, cbId, "Invalid SIM slot")
                    return
                }
                val sim = simInfoProvider?.invoke()?.firstOrNull { it.slotIndex == slot }
                if (sim == null || !sim.isAvailable || !sim.hasPermission) {
                    apiClient.answerCallbackQuery(token, cbId, "SIM ${slot + 1} is unavailable.", showAlert = true)
                    if (messageId != null) editMenuPanel(token, chatId, messageId, origin)
                    return
                }
                val currentMaster = preferencesManager.preferences.value.simControl.forSlot(slot).masterEnabled
                val target = !currentMaster
                onToggleSimMaster?.invoke(slot, target) ?: preferencesManager.setSimMasterEnabled(slot, target)
                apiClient.answerCallbackQuery(
                    token,
                    cbId,
                    "SIM ${slot + 1} ${if (target) "enabled" else "disabled"}"
                )
                if (messageId != null) {
                    editMenuPanel(token, chatId, messageId, origin)
                }
            }

            "otp_toggle" -> {
                val origin = BotMenuPanel.fromId(parts.getOrNull(1))
                val prefs = preferencesManager.preferences.value
                val target = !prefs.isOtpProtectionEnabled
                if (target && !prefs.simControl.isAnyOtpForwardingEnabled()) {
                    apiClient.answerCallbackQuery(
                        token,
                        cbId,
                        "Enable OTP forwarding on at least one SIM first.",
                        showAlert = true
                    )
                    return
                }
                onToggleOtpProtection?.invoke(target) ?: preferencesManager.setOtpProtectionEnabled(target)
                apiClient.answerCallbackQuery(
                    token,
                    cbId,
                    "OTP Protection ${if (target) "enabled" else "disabled"}"
                )
                if (messageId != null) {
                    editMenuPanel(token, chatId, messageId, origin)
                }
            }

            else -> {
                apiClient.answerCallbackQuery(token, cbId, "Action expired or unavailable.")
            }
        }
    }

    private suspend fun sendMenuPanel(
        token: String,
        chatId: Long,
        panel: BotMenuPanel,
        customNote: String? = null
    ): Result<TelegramMessage> {
        val (formatted, keyboard) = buildMenuPayload(panel, customNote)
        return apiClient.sendMessageWithKeyboard(
            token = token,
            chatId = chatId,
            text = formatted.text,
            entities = formatted.entities,
            keyboardRows = keyboard
        )
    }

    private suspend fun editMenuPanel(
        token: String,
        chatId: Long,
        messageId: Long,
        panel: BotMenuPanel
    ): Result<Unit> {
        val (formatted, keyboard) = buildMenuPayload(panel, customNote = null)
        return apiClient.editMessageText(
            token = token,
            chatId = chatId,
            messageId = messageId,
            text = formatted.text,
            entities = formatted.entities,
            keyboardRows = keyboard
        )
    }

    private fun buildMenuPayload(
        panel: BotMenuPanel,
        customNote: String?
    ): Pair<FormattedMessage, List<List<InlineButton>>> {
        val sims = simInfoProvider?.invoke() ?: emptyList()
        val isServiceRunning = isServiceRunningProvider?.invoke()
            ?: (isForwardingActiveProvider?.invoke() == true)
        val isInternet = isInternetAvailableProvider?.invoke() ?: _isConnected.value
        val pendingCount = pendingQueueCountProvider?.invoke() ?: 0
        return TelegramMenuHelper.buildPanelContent(
            panel = panel,
            preferencesManager = preferencesManager,
            sims = sims,
            isServiceRunning = isServiceRunning,
            serviceStartedElapsedMs = serviceStartedElapsedProvider?.invoke(),
            isInternetOnline = isInternet,
            isBotConnected = _isConnected.value,
            isPollingActive = _isPollingActive.value,
            pendingQueueCount = pendingCount,
            customNote = customNote
        )
    }

    private suspend fun dispatchSmsReply(
        token: String,
        session: ReplySession,
        replyText: String
    ) {
        val gateway = smsGateway
        if (gateway == null) {
            if (session.promptMessageId != null) {
                apiClient.editMessageText(
                    token = token,
                    chatId = session.chatId,
                    messageId = session.promptMessageId,
                    text = "SMS sending service is unavailable right now.",
                    keyboardRows = emptyList()
                )
            } else {
                apiClient.sendMessage(
                    token,
                    session.chatId,
                    "SMS sending service is unavailable right now.",
                    includeGithubButton = false
                )
            }
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
        sendResult.onSuccess {
            val updatedCtx = preferencesManager.appendReplyToContext(session.contextId, replyText)
            val notifMsgId = session.notificationMessageId ?: updatedCtx?.notificationMessageId
            if (notifMsgId != null && updatedCtx != null) {
                val simLabel = updatedCtx.simLabel ?: formatContextSimLabel(updatedCtx)
                val updatedNotification = TelegramEntityHelper.formatIncomingSms(
                    sender = updatedCtx.senderAddress,
                    body = updatedCtx.messageBody,
                    simLabel = simLabel,
                    contactName = updatedCtx.senderName,
                    receivedTimestamp = updatedCtx.receivedTimestamp,
                    isOtp = updatedCtx.isOtp,
                    sentReplies = updatedCtx.sentReplies
                )
                apiClient.editMessageText(
                    token = token,
                    chatId = session.chatId,
                    messageId = notifMsgId,
                    text = updatedNotification.text,
                    entities = updatedNotification.entities,
                    keyboardRows = buildSmsNotificationKeyboard(updatedCtx.contextId)
                )
                if (session.promptMessageId != null) {
                    apiClient.deleteMessage(token, session.chatId, session.promptMessageId)
                }
            } else {
                val fallbackText = "You replied:\n$replyText"
                if (session.promptMessageId != null) {
                    apiClient.editMessageText(
                        token = token,
                        chatId = session.chatId,
                        messageId = session.promptMessageId,
                        text = fallbackText,
                        keyboardRows = emptyList()
                    )
                } else {
                    apiClient.sendMessage(token, session.chatId, fallbackText, includeGithubButton = false)
                }
            }
        }.onFailure { err ->
            val reason = err.message ?: "Unknown sending failure"
            val report = "✕ Failed to send SMS reply\nTo: $target\nVia: SIM ${session.selectedSlotIndex + 1}\nReason: $reason"
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

    private fun buildReplyPromptKeyboard(sessionId: String): List<List<InlineButton>> {
        return listOf(listOf(InlineButton(text = "Cancel", callbackData = "rcancel:$sessionId")))
    }

    private fun buildSmsNotificationKeyboard(contextId: String): List<List<InlineButton>> {
        return listOf(
            listOf(
                InlineButton(text = "Reply", callbackData = "reply:$contextId"),
                InlineButton(text = "Forward", callbackData = "fwd:$contextId")
            )
        )
    }

    private fun buildForwardDestinationsKeyboard(
        sessionId: String,
        destinations: List<com.example.data.model.ForwardDestination>
    ): List<List<InlineButton>> {
        val rows = mutableListOf<List<InlineButton>>()
        destinations.chunked(2).forEach { pair ->
            rows.add(
                pair.map { dest ->
                    InlineButton(
                        text = dest.label,
                        callbackData = "fwdsel:$sessionId:${dest.chatId}"
                    )
                }
            )
        }
        rows.add(
            listOf(
                InlineButton(text = "Enter Chat ID", callbackData = "fwdmanual:$sessionId"),
                InlineButton(text = "Cancel", callbackData = "fwdcancel:$sessionId")
            )
        )
        return rows
    }

    private fun buildForwardConfirmKeyboard(sessionId: String, destChatId: Long): List<List<InlineButton>> {
        return listOf(
            listOf(InlineButton(text = "✓ Confirm Forward", callbackData = "fwdgo:$sessionId:$destChatId")),
            listOf(
                InlineButton(text = "Back", callbackData = "fwdback:$sessionId"),
                InlineButton(text = "Cancel", callbackData = "fwdcancel:$sessionId")
            )
        )
    }

    private fun resolveDestinationLabel(destChatId: Long): String {
        val saved = preferencesManager.telegramConfig.value.forwardDestinations
            .firstOrNull { it.chatId == destChatId }
        return saved?.label ?: destChatId.toString()
    }

    private fun formatContextSimLabel(ctx: SmsReplyContext): String? {
        if (ctx.simSlot !in 0..1 && ctx.subId < 0) return null
        val sims = simInfoProvider?.invoke().orEmpty()
        val sim = sims.firstOrNull { it.subId >= 0 && it.subId == ctx.subId }
            ?: sims.firstOrNull { it.slotIndex == ctx.simSlot }
        val slotNum = when {
            sim != null -> sim.slotIndex + 1
            ctx.simSlot in 0..1 -> ctx.simSlot + 1
            else -> return null
        }
        val validCarrier = sim?.carrierName?.takeIf {
            sim.isAvailable &&
                it.isNotBlank() &&
                it != "Carrier unavailable" &&
                it != "Permission required" &&
                it != "No SIM inserted"
        }
        return if (validCarrier != null) "SIM $slotNum · $validCarrier" else "SIM $slotNum"
    }

    suspend fun sendStartStatus(chatId: Long): Result<TelegramMessage> {
        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) return Result.failure(Exception("No token"))

        EventLogger.log(LogCategory.TELEGRAM, "Sending interactive VANTA Overview panel to chat $chatId", LogLevel.INFO)
        return sendMenuPanel(token, chatId, BotMenuPanel.OVERVIEW)
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
                receivedTimestamp = receivedTimestamp,
                messageBody = body,
                isOtp = isOtp,
                simLabel = simLabel
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
        val actionKeyboard = buildSmsNotificationKeyboard(contextId)

        val result = apiClient.sendMessageWithKeyboard(
            token = token,
            chatId = chatId,
            text = formatted.text,
            entities = formatted.entities,
            keyboardRows = actionKeyboard
        )

        result.onSuccess { sentMsg ->
            _isConnected.value = true
            preferencesManager.updateReplyContextNotificationId(contextId, sentMsg.messageId)
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
