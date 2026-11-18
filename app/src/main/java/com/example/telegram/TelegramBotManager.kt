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
    private var activePollingToken: String? = null
    private var lastUpdateId: Long? = null

    private val _isPollingActive = MutableStateFlow(false)
    val isPollingActive: StateFlow<Boolean> = _isPollingActive.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val secureRandom = SecureRandom()

    var simInfoProvider: (() -> List<SimCardInfo>)? = null
    var simRefreshProvider: (() -> List<SimCardInfo>)? = null
    var hasSendSmsPermissionProvider: (() -> Boolean)? = null
    var isForwardingActiveProvider: (() -> Boolean)? = null
    var isServiceRunningProvider: (() -> Boolean)? = null
    var serviceStartedElapsedProvider: (() -> Long?)? = null
    var isInternetAvailableProvider: (() -> Boolean)? = null
    var pendingQueueCountProvider: (() -> Int)? = null
    var onSetForwardingEnabled: ((Boolean) -> Unit)? = null
    var onServiceStopRequested: (() -> Unit)? = null
    var onToggleSimMaster: ((Int, Boolean) -> Unit)? = null
    var smsGateway: SmsGateway? = null
    var clockProvider: () -> Long = { System.currentTimeMillis() }

    private val sessionMutex = Mutex()
    private val activeReplySessions = ConcurrentHashMap<String, ReplySession>()
    private val activeForwardSessions = ConcurrentHashMap<String, ForwardSession>()
    private val activeNewMessageSessions = ConcurrentHashMap<String, NewMessageSession>()
    private val forwardInFlightKeys = ConcurrentHashMap.newKeySet<String>()
    private val replyInFlightKeys = ConcurrentHashMap.newKeySet<String>()
    private val newMessageInFlightKeys = ConcurrentHashMap.newKeySet<String>()

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
                if (!_isPollingActive.value || activePollingToken != token) {
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
        activeNewMessageSessions.clear()
        forwardInFlightKeys.clear()
        replyInFlightKeys.clear()
        newMessageInFlightKeys.clear()
        preferencesManager.disconnectTelegram()
        _isConnected.value = false
        onServiceStopRequested?.invoke()
        EventLogger.log(LogCategory.SECURITY, "Telegram bot disconnected and credentials purged", LogLevel.WARNING)
    }

    fun startPolling() {
        val token = preferencesManager.getDecryptedBotToken()
        if (token.isEmpty()) return

        refreshCustomEmojis(token)

        if (pollingJob?.isActive == true && activePollingToken == token) return
        if (pollingJob?.isActive == true) {
            pollingJob?.cancel()
            pollingJob = null
        }

        activePollingToken = token
        _isPollingActive.value = true
        pollingJob = scope.launch {
            EventLogger.log(LogCategory.TELEGRAM, "Started Telegram command listener", LogLevel.INFO)
            try {
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
            } finally {
                _isPollingActive.value = false
                if (activePollingToken == token) {
                    activePollingToken = null
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        activePollingToken = null
        _isPollingActive.value = false
    }

    fun getActiveSession(chatId: Long, userId: Long): ReplySession? {
        return activeReplySessions[sessionKey(chatId, userId)]
    }

    fun getActiveForwardSession(chatId: Long, userId: Long): ForwardSession? {
        return activeForwardSessions[sessionKey(chatId, userId)]
    }

    fun getActiveNewMessageSession(chatId: Long, userId: Long): NewMessageSession? {
        return activeNewMessageSessions[sessionKey(chatId, userId)]
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

        val exactText = update.text ?: return
        val rawText = exactText.trim()
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
                preferencesManager.recordBotInteractionIfFirstSeen(userId, clockProvider())
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
            val key = sessionKey(chatId, userId)
            val (removedReply, removedForward, removedNewMsg) = sessionMutex.withLock {
                Triple(
                    activeReplySessions.remove(key),
                    activeForwardSessions.remove(key),
                    activeNewMessageSessions.remove(key)
                )
            }
            if (removedNewMsg != null) {
                deleteMessageSafely(token, chatId, msgId)
                val deletedPrompt = deleteMessageSafely(token, chatId, removedNewMsg.promptMessageId)
                if (!deletedPrompt && removedNewMsg.promptMessageId != null) {
                    apiClient.editMessageText(
                        token = token,
                        chatId = chatId,
                        messageId = removedNewMsg.promptMessageId,
                        text = "Canceled.",
                        keyboardRows = emptyList()
                    )
                }
                EventLogger.log(LogCategory.TELEGRAM, "New SMS workflow cancelled", LogLevel.INFO)
            } else if (removedReply != null) {
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

        // 4. Entry commands open interactive inline-keyboard menu (clears any abandoned New Message flow cleanly)
        when {
            rawText == "/start" || rawText.startsWith("/start@") ||
                rawText == "/menu" || rawText.startsWith("/menu@") ||
                rawText == "/status" || rawText.startsWith("/status@") -> {
                cleanupAbandonedNewMessageSession(token, chatId, userId)
                sendMenuPanel(token, chatId, BotMenuPanel.OVERVIEW)
                EventLogger.log(LogCategory.TELEGRAM, "Sent interactive VANTA Overview panel", LogLevel.INFO)
                maybeSendWeeklyGithubReminder(token, chatId, userId)
                return
            }
            rawText == "/help" || rawText.startsWith("/help@") -> {
                cleanupAbandonedNewMessageSession(token, chatId, userId)
                sendMenuPanel(token, chatId, BotMenuPanel.HELP)
                EventLogger.log(LogCategory.TELEGRAM, "Sent interactive VANTA Help panel", LogLevel.INFO)
                maybeSendWeeklyGithubReminder(token, chatId, userId)
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

        // 5b. Active New Outgoing SMS workflow for (chatId, userId)
        val activeNewMsg = sessionMutex.withLock {
            activeNewMessageSessions[sessionKey(chatId, userId)]
        }
        if (activeNewMsg != null) {
            handleNewMessageTextInput(token, chatId, userId, msgId, exactText, activeNewMsg)
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
        maybeSendWeeklyGithubReminder(token, chatId, userId)
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
                maybeSendWeeklyGithubReminder(token, chatId, userId)
            }

            data.startsWith("act:") -> {
                handleControlActionCallback(token, chatId, update.callbackMessageId, cbId, data)
                maybeSendWeeklyGithubReminder(token, chatId, userId)
            }

            data == "gh_rem:dismiss" -> {
                handleGithubReminderDismiss(token, chatId, update.callbackMessageId, cbId)
            }

            data == "gh_rem:disable" -> {
                handleGithubReminderDisable(token, chatId, userId, update.callbackMessageId, cbId)
            }

            data == "newmsg:start" -> {
                handleNewMessageStartCallback(token, chatId, userId, cbId)
            }

            data.startsWith("newmsg_confirm:") -> {
                handleNewMessageConfirmCallback(token, chatId, userId, update.callbackMessageId, cbId, data)
            }

            data.startsWith("newmsg_sim:") -> {
                handleNewMessageSimCallback(token, chatId, userId, update.callbackMessageId, cbId, data)
            }

            data.startsWith("newmsg_cancel:") -> {
                handleNewMessageCancelCallback(token, chatId, userId, update.callbackMessageId, cbId, data)
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
                var newMsgPromptToClean: Long? = null
                val alreadyActive = sessionMutex.withLock {
                    val existing = activeReplySessions[key]
                    if (existing != null && existing.contextId == contextId) {
                        true
                    } else {
                        previousPromptToClean = existing?.promptMessageId
                        newMsgPromptToClean = activeNewMessageSessions.remove(key)?.promptMessageId
                        false
                    }
                }
                if (alreadyActive) {
                    apiClient.answerCallbackQuery(token, cbId, "Reply prompt is already active.")
                    return
                }
                deleteMessageSafely(token, chatId, previousPromptToClean)
                deleteMessageSafely(token, chatId, newMsgPromptToClean)

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
                val key = sessionKey(chatId, userId)
                val newMsgPromptToClean = sessionMutex.withLock {
                    activeReplySessions.remove(key)
                    activeForwardSessions[key] = session
                    activeNewMessageSessions.remove(key)?.promptMessageId
                }
                deleteMessageSafely(token, chatId, newMsgPromptToClean)
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
                EventLogger.log(
                    LogCategory.SECURITY,
                    "Blocked attempt to toggle OTP Protection via Telegram callback",
                    LogLevel.WARNING
                )
                apiClient.answerCallbackQuery(
                    token,
                    cbId,
                    "OTP Protection can only be configured inside the VANTA app.",
                    showAlert = true
                )
                if (messageId != null) {
                    editMenuPanel(token, chatId, messageId, BotMenuPanel.fromId(parts.getOrNull(1)))
                }
            }

            else -> {
                apiClient.answerCallbackQuery(token, cbId, "Action expired or unavailable.")
            }
        }
    }

    // --- New Outgoing SMS Workflow ---

    private suspend fun cleanupAbandonedNewMessageSession(token: String, chatId: Long, userId: Long) {
        val removed = sessionMutex.withLock {
            activeNewMessageSessions.remove(sessionKey(chatId, userId))
        }
        if (removed != null) {
            deleteMessageSafely(token, chatId, removed.promptMessageId)
        }
    }

    private suspend fun handleNewMessageStartCallback(
        token: String,
        chatId: Long,
        userId: Long,
        cbId: String
    ) {
        val key = sessionKey(chatId, userId)
        var oldNewMsgPromptId: Long? = null
        var oldReplyPromptId: Long? = null
        var oldFwdPromptId: Long? = null

        sessionMutex.withLock {
            val existing = activeNewMessageSessions[key]
            if (existing != null && existing.step == NewMessageStep.SENDING) {
                apiClient.answerCallbackQuery(token, cbId, "Sending message in progress...")
                return
            }
            oldNewMsgPromptId = activeNewMessageSessions.remove(key)?.promptMessageId
            oldReplyPromptId = activeReplySessions.remove(key)?.promptMessageId
            oldFwdPromptId = activeForwardSessions.remove(key)?.promptMessageId
        }

        deleteMessageSafely(token, chatId, oldNewMsgPromptId)
        deleteMessageSafely(token, chatId, oldReplyPromptId)
        deleteMessageSafely(token, chatId, oldFwdPromptId)

        val sessionId = UUID.randomUUID().toString().take(8)
        val prompt = TelegramEntityHelper.formatNewMessageRecipientPrompt()
        val keyboard = buildNewMessageCancelKeyboard(sessionId)

        apiClient.answerCallbackQuery(token, cbId)
        val sent = try {
            apiClient.sendMessageWithKeyboard(
                token = token,
                chatId = chatId,
                text = prompt.text,
                entities = prompt.entities,
                keyboardRows = keyboard
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
        val promptMsgId = sent.getOrNull()?.messageId
        val session = NewMessageSession(
            sessionId = sessionId,
            chatId = chatId,
            userId = userId,
            step = NewMessageStep.AWAITING_RECIPIENT,
            promptMessageId = promptMsgId
        )
        sessionMutex.withLock {
            activeNewMessageSessions[key] = session
        }
        EventLogger.log(LogCategory.TELEGRAM, "Started New Message SMS workflow", LogLevel.INFO)
    }

    private suspend fun handleNewMessageTextInput(
        token: String,
        chatId: Long,
        userId: Long,
        userMsgId: Long?,
        exactText: String,
        session: NewMessageSession
    ) {
        val key = sessionKey(chatId, userId)

        when (session.step) {
            NewMessageStep.AWAITING_RECIPIENT -> {
                val validated = PhoneNumberValidator.validateInternationalNumber(exactText)
                if (validated == null) {
                    val prompt = TelegramEntityHelper.formatNewMessageRecipientPrompt(
                        errorNote = "Invalid phone number. Please enter an international phone number with country code."
                    )
                    val nextPromptId = presentManagedPrompt(
                        token = token,
                        chatId = chatId,
                        existingPromptId = session.promptMessageId,
                        userMessageIdToClean = userMsgId,
                        formatted = prompt,
                        keyboardRows = buildNewMessageCancelKeyboard(session.sessionId)
                    )
                    sessionMutex.withLock {
                        val current = activeNewMessageSessions[key]
                        if (current != null && current.sessionId == session.sessionId) {
                            activeNewMessageSessions[key] = current.copy(promptMessageId = nextPromptId)
                        }
                    }
                    return
                }

                val prompt = TelegramEntityHelper.formatNewMessageConfirmRecipient(validated.displayNumber)
                val confirmKeyboard = buildNewMessageConfirmKeyboard(session.sessionId)
                val nextPromptId = presentManagedPrompt(
                    token = token,
                    chatId = chatId,
                    existingPromptId = session.promptMessageId,
                    userMessageIdToClean = userMsgId,
                    formatted = prompt,
                    keyboardRows = confirmKeyboard
                )
                sessionMutex.withLock {
                    val current = activeNewMessageSessions[key]
                    if (current != null && current.sessionId == session.sessionId) {
                        activeNewMessageSessions[key] = current.copy(
                            step = NewMessageStep.CONFIRMING_RECIPIENT,
                            recipientDisplay = validated.displayNumber,
                            recipientDialable = validated.dialableNumber,
                            promptMessageId = nextPromptId
                        )
                    }
                }
            }

            NewMessageStep.CONFIRMING_RECIPIENT -> {
                val recipient = session.recipientDisplay ?: return
                val prompt = TelegramEntityHelper.formatNewMessageConfirmRecipient(recipient)
                val nextPromptId = presentManagedPrompt(
                    token = token,
                    chatId = chatId,
                    existingPromptId = session.promptMessageId,
                    userMessageIdToClean = userMsgId,
                    formatted = prompt,
                    keyboardRows = buildNewMessageConfirmKeyboard(session.sessionId)
                )
                sessionMutex.withLock {
                    val current = activeNewMessageSessions[key]
                    if (current != null && current.sessionId == session.sessionId) {
                        activeNewMessageSessions[key] = current.copy(promptMessageId = nextPromptId)
                    }
                }
            }

            NewMessageStep.AWAITING_BODY -> {
                if (exactText.isBlank()) {
                    val prompt = TelegramEntityHelper.formatNewMessageBodyPrompt(
                        errorNote = "Message text cannot be empty."
                    )
                    val nextPromptId = presentManagedPrompt(
                        token = token,
                        chatId = chatId,
                        existingPromptId = session.promptMessageId,
                        userMessageIdToClean = userMsgId,
                        formatted = prompt,
                        keyboardRows = buildNewMessageCancelKeyboard(session.sessionId)
                    )
                    sessionMutex.withLock {
                        val current = activeNewMessageSessions[key]
                        if (current != null && current.sessionId == session.sessionId) {
                            activeNewMessageSessions[key] = current.copy(promptMessageId = nextPromptId)
                        }
                    }
                    return
                }

                // Clean up user's composed message if permitted
                val userDeleted = deleteMessageSafely(token, chatId, userMsgId)

                // Check SEND_SMS permission before SIM selection/sending
                if (hasSendSmsPermissionProvider?.invoke() == false) {
                    sessionMutex.withLock { activeNewMessageSessions.remove(key) }
                    val errorMsg = FormattedMessage(
                        "✕ Cannot send SMS\nSMS permission (SEND_SMS) is denied on this device. Grant SMS permission in the VANTA app and try again.",
                        emptyList()
                    )
                    presentTerminalCleanup(token, chatId, session.promptMessageId, userDeleted, errorMsg)
                    return
                }

                val allSims = getFreshSimInfo()
                val availableSims = filterAvailableOutgoingSims(allSims)

                when (availableSims.size) {
                    0 -> {
                        sessionMutex.withLock { activeNewMessageSessions.remove(key) }
                        val reason = buildNoAvailableSimMessage(allSims)
                        presentTerminalCleanup(
                            token = token,
                            chatId = chatId,
                            existingPromptId = session.promptMessageId,
                            userDeleted = userDeleted,
                            formatted = FormattedMessage("✕ Cannot send SMS\n$reason", emptyList())
                        )
                    }

                    1 -> {
                        val onlySim = availableSims.first()
                        val claimed = sessionMutex.withLock {
                            val current = activeNewMessageSessions[key]
                            if (current != null &&
                                current.sessionId == session.sessionId &&
                                current.step == NewMessageStep.AWAITING_BODY &&
                                newMessageInFlightKeys.add(session.sessionId)
                            ) {
                                val next = current.copy(step = NewMessageStep.SENDING, messageBody = exactText)
                                activeNewMessageSessions[key] = next
                                next
                            } else null
                        } ?: return

                        try {
                            val sendingPromptId = presentManagedPrompt(
                                token = token,
                                chatId = chatId,
                                existingPromptId = claimed.promptMessageId,
                                userMessageIdToClean = null,
                                forceRepost = !userDeleted,
                                formatted = FormattedMessage("Sending SMS via SIM ${onlySim.slotIndex + 1}...", emptyList()),
                                keyboardRows = emptyList()
                            )
                            val updatedClaimed = claimed.copy(promptMessageId = sendingPromptId)
                            dispatchNewMessageSms(token, updatedClaimed, onlySim)
                        } finally {
                            sessionMutex.withLock { activeNewMessageSessions.remove(key) }
                            newMessageInFlightKeys.remove(session.sessionId)
                        }
                    }

                    else -> {
                        val recipientDisp = session.recipientDisplay ?: session.recipientDialable ?: ""
                        val prompt = TelegramEntityHelper.formatNewMessageSimPrompt(recipientDisp)
                        val simKeyboard = buildNewMessageSimKeyboard(session.sessionId, availableSims)
                        val nextPromptId = presentManagedPrompt(
                            token = token,
                            chatId = chatId,
                            existingPromptId = session.promptMessageId,
                            userMessageIdToClean = null,
                            forceRepost = !userDeleted,
                            formatted = prompt,
                            keyboardRows = simKeyboard
                        )
                        sessionMutex.withLock {
                            val current = activeNewMessageSessions[key]
                            if (current != null && current.sessionId == session.sessionId) {
                                activeNewMessageSessions[key] = current.copy(
                                    step = NewMessageStep.SELECTING_SIM,
                                    messageBody = exactText,
                                    promptMessageId = nextPromptId
                                )
                            }
                        }
                    }
                }
            }

            NewMessageStep.SELECTING_SIM -> {
                val userDeleted = deleteMessageSafely(token, chatId, userMsgId)
                val allSims = getFreshSimInfo()
                val availableSims = filterAvailableOutgoingSims(allSims)
                if (availableSims.isEmpty()) {
                    sessionMutex.withLock { activeNewMessageSessions.remove(key) }
                    val reason = buildNoAvailableSimMessage(allSims)
                    presentTerminalCleanup(
                        token = token,
                        chatId = chatId,
                        existingPromptId = session.promptMessageId,
                        userDeleted = userDeleted,
                        formatted = FormattedMessage("✕ Cannot send SMS\n$reason", emptyList())
                    )
                    return
                }
                val recipientDisp = session.recipientDisplay ?: session.recipientDialable ?: ""
                val prompt = TelegramEntityHelper.formatNewMessageSimPrompt(
                    recipient = recipientDisp,
                    note = "Please choose a SIM button below or tap Cancel."
                )
                val nextPromptId = presentManagedPrompt(
                    token = token,
                    chatId = chatId,
                    existingPromptId = session.promptMessageId,
                    userMessageIdToClean = null,
                    forceRepost = !userDeleted,
                    formatted = prompt,
                    keyboardRows = buildNewMessageSimKeyboard(session.sessionId, availableSims)
                )
                sessionMutex.withLock {
                    val current = activeNewMessageSessions[key]
                    if (current != null && current.sessionId == session.sessionId) {
                        activeNewMessageSessions[key] = current.copy(promptMessageId = nextPromptId)
                    }
                }
            }

            NewMessageStep.SENDING -> {
                deleteMessageSafely(token, chatId, userMsgId)
            }
        }
    }

    private suspend fun handleNewMessageConfirmCallback(
        token: String,
        chatId: Long,
        userId: Long,
        callbackMsgId: Long?,
        cbId: String,
        data: String
    ) {
        val parts = data.removePrefix("newmsg_confirm:").split(":")
        if (parts.size != 2) {
            apiClient.answerCallbackQuery(token, cbId, "Action expired or unavailable.")
            return
        }
        val sessionId = parts[0]
        val choice = parts[1]
        val key = sessionKey(chatId, userId)

        val nextStepSession = sessionMutex.withLock {
            val existing = activeNewMessageSessions[key]
            if (existing == null || existing.sessionId != sessionId || existing.step != NewMessageStep.CONFIRMING_RECIPIENT) {
                null
            } else if (choice == "no") {
                val updated = existing.copy(
                    step = NewMessageStep.AWAITING_RECIPIENT,
                    recipientDisplay = null,
                    recipientDialable = null,
                    promptMessageId = callbackMsgId ?: existing.promptMessageId
                )
                activeNewMessageSessions[key] = updated
                updated
            } else if (choice == "yes") {
                val updated = existing.copy(
                    step = NewMessageStep.AWAITING_BODY,
                    promptMessageId = callbackMsgId ?: existing.promptMessageId
                )
                activeNewMessageSessions[key] = updated
                updated
            } else {
                null
            }
        }

        if (nextStepSession == null) {
            apiClient.answerCallbackQuery(token, cbId, "Session expired. Tap New Message to start again.")
            return
        }

        apiClient.answerCallbackQuery(token, cbId)
        val prompt: FormattedMessage
        val keyboard: List<List<InlineButton>>
        if (nextStepSession.step == NewMessageStep.AWAITING_RECIPIENT) {
            prompt = TelegramEntityHelper.formatNewMessageRecipientPrompt()
            keyboard = buildNewMessageCancelKeyboard(sessionId)
        } else {
            prompt = TelegramEntityHelper.formatNewMessageBodyPrompt()
            keyboard = buildNewMessageCancelKeyboard(sessionId)
        }

        val nextPromptId = presentManagedPrompt(
            token = token,
            chatId = chatId,
            existingPromptId = nextStepSession.promptMessageId,
            userMessageIdToClean = null,
            formatted = prompt,
            keyboardRows = keyboard
        )
        if (nextPromptId != nextStepSession.promptMessageId) {
            sessionMutex.withLock {
                val current = activeNewMessageSessions[key]
                if (current != null && current.sessionId == sessionId) {
                    activeNewMessageSessions[key] = current.copy(promptMessageId = nextPromptId)
                }
            }
        }
    }

    private suspend fun handleNewMessageSimCallback(
        token: String,
        chatId: Long,
        userId: Long,
        callbackMsgId: Long?,
        cbId: String,
        data: String
    ) {
        val parts = data.removePrefix("newmsg_sim:").split(":")
        if (parts.size < 2) {
            apiClient.answerCallbackQuery(token, cbId, "Invalid SIM selection.")
            return
        }
        val sessionId = parts[0]
        val slotIndex = parts[1].toIntOrNull()
        val expectedSubId = parts.getOrNull(2)?.toIntOrNull()
        if (slotIndex == null || slotIndex !in 0..1) {
            apiClient.answerCallbackQuery(token, cbId, "Invalid SIM slot.")
            return
        }

        val key = sessionKey(chatId, userId)
        val currentSession = sessionMutex.withLock {
            val existing = activeNewMessageSessions[key]
            when {
                existing == null || existing.sessionId != sessionId -> null to "expired"
                existing.step == NewMessageStep.SENDING -> existing to "sending"
                existing.step != NewMessageStep.SELECTING_SIM -> existing to "expired"
                else -> existing to "ok"
            }
        }
        val session = currentSession.first
        when (currentSession.second) {
            "sending" -> {
                apiClient.answerCallbackQuery(token, cbId, "Sending in progress...")
                return
            }
            "expired" -> {
                apiClient.answerCallbackQuery(token, cbId, "Session expired. Tap New Message to start again.")
                return
            }
        }
        if (session == null) return

        val promptId = callbackMsgId ?: session.promptMessageId

        if (hasSendSmsPermissionProvider?.invoke() == false) {
            sessionMutex.withLock { activeNewMessageSessions.remove(key) }
            apiClient.answerCallbackQuery(token, cbId, "SMS permission denied.", showAlert = true)
            finalizeManagedMessage(
                token = token,
                chatId = chatId,
                promptMessageId = promptId,
                formatted = FormattedMessage(
                    "✕ Cannot send SMS\nSMS permission (SEND_SMS) is denied on this device. Grant SMS permission in the VANTA app and try again.",
                    emptyList()
                )
            )
            return
        }

        val allSims = getFreshSimInfo()
        val availableSims = filterAvailableOutgoingSims(allSims)
        val selectedSim = availableSims.firstOrNull { it.slotIndex == slotIndex }

        if (selectedSim == null) {
            if (availableSims.isEmpty()) {
                sessionMutex.withLock { activeNewMessageSessions.remove(key) }
                apiClient.answerCallbackQuery(token, cbId, "No SIM available.", showAlert = true)
                val reason = buildNoAvailableSimMessage(allSims)
                finalizeManagedMessage(
                    token = token,
                    chatId = chatId,
                    promptMessageId = promptId,
                    formatted = FormattedMessage(
                        "✕ Cannot send SMS\nSIM ${slotIndex + 1} became unavailable.\n$reason",
                        emptyList()
                    )
                )
            } else {
                apiClient.answerCallbackQuery(token, cbId, "SIM ${slotIndex + 1} is no longer available.", showAlert = true)
                val recipientDisp = session.recipientDisplay ?: session.recipientDialable ?: ""
                val refreshedPrompt = TelegramEntityHelper.formatNewMessageSimPrompt(
                    recipient = recipientDisp,
                    note = "SIM ${slotIndex + 1} became unavailable. Please select an available SIM:"
                )
                presentManagedPrompt(
                    token = token,
                    chatId = chatId,
                    existingPromptId = promptId,
                    userMessageIdToClean = null,
                    formatted = refreshedPrompt,
                    keyboardRows = buildNewMessageSimKeyboard(sessionId, availableSims)
                )
            }
            return
        }

        if (expectedSubId != null && expectedSubId >= 0 && selectedSim.subId != expectedSubId) {
            apiClient.answerCallbackQuery(token, cbId, "SIM subscription changed. Please re-select.", showAlert = true)
            val recipientDisp = session.recipientDisplay ?: session.recipientDialable ?: ""
            val refreshedPrompt = TelegramEntityHelper.formatNewMessageSimPrompt(
                recipient = recipientDisp,
                note = "SIM ${slotIndex + 1} subscription changed on device. Confirm which SIM should send:"
            )
            presentManagedPrompt(
                token = token,
                chatId = chatId,
                existingPromptId = promptId,
                userMessageIdToClean = null,
                formatted = refreshedPrompt,
                keyboardRows = buildNewMessageSimKeyboard(sessionId, availableSims)
            )
            return
        }

        val claimed = sessionMutex.withLock {
            val latest = activeNewMessageSessions[key]
            if (latest != null &&
                latest.sessionId == sessionId &&
                latest.step == NewMessageStep.SELECTING_SIM &&
                newMessageInFlightKeys.add(sessionId)
            ) {
                val updated = latest.copy(step = NewMessageStep.SENDING, promptMessageId = promptId)
                activeNewMessageSessions[key] = updated
                updated
            } else null
        }
        if (claimed == null) {
            apiClient.answerCallbackQuery(token, cbId, "Sending in progress...")
            return
        }

        apiClient.answerCallbackQuery(token, cbId, "Sending via SIM ${selectedSim.slotIndex + 1}...")
        try {
            val sendingPromptId = presentManagedPrompt(
                token = token,
                chatId = chatId,
                existingPromptId = promptId,
                userMessageIdToClean = null,
                formatted = FormattedMessage("Sending SMS via SIM ${selectedSim.slotIndex + 1}...", emptyList()),
                keyboardRows = emptyList()
            )
            dispatchNewMessageSms(token, claimed.copy(promptMessageId = sendingPromptId), selectedSim)
        } finally {
            sessionMutex.withLock { activeNewMessageSessions.remove(key) }
            newMessageInFlightKeys.remove(sessionId)
        }
    }

    private suspend fun handleNewMessageCancelCallback(
        token: String,
        chatId: Long,
        userId: Long,
        callbackMsgId: Long?,
        cbId: String,
        data: String
    ) {
        val sessionId = data.removePrefix("newmsg_cancel:")
        val key = sessionKey(chatId, userId)
        val removalResult = sessionMutex.withLock {
            val existing = activeNewMessageSessions[key]
            if (existing != null && existing.sessionId == sessionId && existing.step == NewMessageStep.SENDING) {
                "sending" to existing
            } else if (existing != null && existing.sessionId == sessionId) {
                "removed" to activeNewMessageSessions.remove(key)
            } else {
                "expired" to null
            }
        }
        if (removalResult.first == "sending") {
            apiClient.answerCallbackQuery(token, cbId, "SMS is already being sent.")
            return
        }
        val removed = removalResult.second
        apiClient.answerCallbackQuery(token, cbId, if (removed != null) "Canceled." else "Session expired.")

        val msgToClean = callbackMsgId ?: removed?.promptMessageId
        if (msgToClean != null) {
            val deleted = deleteMessageSafely(token, chatId, msgToClean)
            if (!deleted) {
                apiClient.editMessageText(
                    token = token,
                    chatId = chatId,
                    messageId = msgToClean,
                    text = "Canceled.",
                    keyboardRows = emptyList()
                )
            }
        }
        EventLogger.log(LogCategory.TELEGRAM, "New SMS workflow cancelled via button", LogLevel.INFO)
    }

    private suspend fun dispatchNewMessageSms(
        token: String,
        session: NewMessageSession,
        selectedSim: SimCardInfo
    ) {
        val recipientDisplay = session.recipientDisplay ?: session.recipientDialable ?: return
        val recipientDialable = session.recipientDialable ?: recipientDisplay
        val messageBody = session.messageBody ?: return
        val gateway = smsGateway

        if (gateway == null) {
            finalizeManagedMessage(
                token = token,
                chatId = session.chatId,
                promptMessageId = session.promptMessageId,
                formatted = FormattedMessage(
                    "✕ Failed to send SMS\nTo: $recipientDisplay (SIM ${selectedSim.slotIndex + 1})\nReason: SMS sending service is unavailable right now.",
                    emptyList()
                )
            )
            return
        }

        val sendResult = gateway.sendSmsWithSubIdCheck(
            destination = recipientDialable,
            messageText = messageBody,
            slotIndex = selectedSim.slotIndex,
            expectedSubId = selectedSim.subId
        )

        sendResult.onSuccess { sent ->
            val sentTimestamp = System.currentTimeMillis()
            val confirmation = TelegramEntityHelper.formatNewMessageSentConfirmation(
                recipient = recipientDisplay,
                simSlot = sent.slotIndex,
                sentTimestampMillis = sentTimestamp,
                messageText = messageBody
            )
            finalizeManagedMessage(
                token = token,
                chatId = session.chatId,
                promptMessageId = session.promptMessageId,
                formatted = confirmation
            )
            EventLogger.log(
                LogCategory.SMS_DELIVERY,
                "Sent new SMS via Telegram to '$recipientDisplay' on SIM ${sent.slotIndex + 1} (subId=${sent.subId})",
                LogLevel.SUCCESS
            )
        }.onFailure { err ->
            val reason = err.message ?: "Unknown sending failure"
            val failureFormatted = FormattedMessage(
                "✕ Failed to send SMS\nTo: $recipientDisplay (SIM ${selectedSim.slotIndex + 1})\nReason: $reason",
                emptyList()
            )
            finalizeManagedMessage(
                token = token,
                chatId = session.chatId,
                promptMessageId = session.promptMessageId,
                formatted = failureFormatted
            )
            EventLogger.log(
                LogCategory.ERROR,
                "New SMS via Telegram failed to '$recipientDisplay' on SIM ${selectedSim.slotIndex + 1}: $reason",
                LogLevel.ERROR
            )
        }
    }

    private fun getFreshSimInfo(): List<SimCardInfo> {
        return (simRefreshProvider ?: simInfoProvider)?.invoke().orEmpty()
    }

    private fun filterAvailableOutgoingSims(allSims: List<SimCardInfo>): List<SimCardInfo> {
        val simControl = preferencesManager.preferences.value.simControl
        return allSims
            .filter { sim ->
                sim.slotIndex in 0..1 &&
                    sim.hasPermission &&
                    sim.isAvailable &&
                    sim.subId >= 0 &&
                    simControl.forSlot(sim.slotIndex).isFeatureEffective(SimFeature.SMS_SENDING)
            }
            .sortedBy { it.slotIndex }
    }

    private fun buildNoAvailableSimMessage(allSims: List<SimCardInfo>): String {
        val missingPhonePerm = allSims.isNotEmpty() && allSims.none { it.hasPermission }
        if (missingPhonePerm) {
            return "Phone State permission is required in the VANTA app to inspect SIM cards for sending SMS."
        }
        val anyPhysicalSim = allSims.any { it.isAvailable && it.hasPermission }
        return if (anyPhysicalSim) {
            "No SIM card is enabled for SMS Sending. Enable at least one active SIM and its SMS Sending toggle in VANTA SIM Control."
        } else {
            "No available SIM card can send SMS right now. Insert or enable an active SIM and try again."
        }
    }

    private fun formatSimChoiceButtonLabel(sim: SimCardInfo): String {
        val validCarrier = sim.carrierName.takeIf {
            it.isNotBlank() &&
                it != "Carrier unavailable" &&
                it != "Permission required" &&
                it != "No SIM inserted"
        }
        return if (validCarrier != null) {
            "SIM ${sim.slotIndex + 1} — $validCarrier"
        } else {
            "SIM ${sim.slotIndex + 1}"
        }
    }

    private fun buildNewMessageCancelKeyboard(sessionId: String): List<List<InlineButton>> {
        return listOf(
            listOf(InlineButton(text = "Cancel", callbackData = "newmsg_cancel:$sessionId"))
        )
    }

    private fun buildNewMessageConfirmKeyboard(sessionId: String): List<List<InlineButton>> {
        return listOf(
            listOf(
                InlineButton(text = "No", callbackData = "newmsg_confirm:$sessionId:no"),
                InlineButton(text = "Yes", callbackData = "newmsg_confirm:$sessionId:yes")
            )
        )
    }

    private fun buildNewMessageSimKeyboard(
        sessionId: String,
        availableSims: List<SimCardInfo>
    ): List<List<InlineButton>> {
        val rows = availableSims.map { sim ->
            listOf(
                InlineButton(
                    text = formatSimChoiceButtonLabel(sim),
                    callbackData = "newmsg_sim:$sessionId:${sim.slotIndex}:${sim.subId}"
                )
            )
        }.toMutableList()
        rows.add(listOf(InlineButton(text = "Cancel", callbackData = "newmsg_cancel:$sessionId")))
        return rows
    }

    private suspend fun deleteMessageSafely(token: String, chatId: Long, messageId: Long?): Boolean {
        if (messageId == null) return false
        return try {
            apiClient.deleteMessage(token, chatId, messageId).isSuccess
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun presentManagedPrompt(
        token: String,
        chatId: Long,
        existingPromptId: Long?,
        userMessageIdToClean: Long?,
        formatted: FormattedMessage,
        keyboardRows: List<List<InlineButton>>,
        forceRepost: Boolean = false
    ): Long? {
        val userDeleted = if (userMessageIdToClean != null) {
            deleteMessageSafely(token, chatId, userMessageIdToClean)
        } else {
            true
        }
        if (!forceRepost && existingPromptId != null && userDeleted) {
            val edited = try {
                apiClient.editMessageText(
                    token = token,
                    chatId = chatId,
                    messageId = existingPromptId,
                    text = formatted.text,
                    entities = formatted.entities,
                    keyboardRows = keyboardRows
                ).isSuccess
            } catch (_: Exception) {
                false
            }
            if (edited) return existingPromptId
        }
        deleteMessageSafely(token, chatId, existingPromptId)
        val sent = try {
            apiClient.sendMessageWithKeyboard(
                token = token,
                chatId = chatId,
                text = formatted.text,
                entities = formatted.entities,
                keyboardRows = keyboardRows
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
        return sent.getOrNull()?.messageId
    }

    private suspend fun presentTerminalCleanup(
        token: String,
        chatId: Long,
        existingPromptId: Long?,
        userDeleted: Boolean,
        formatted: FormattedMessage
    ) {
        if (existingPromptId != null && userDeleted) {
            val edited = try {
                apiClient.editMessageText(
                    token = token,
                    chatId = chatId,
                    messageId = existingPromptId,
                    text = formatted.text,
                    entities = formatted.entities,
                    keyboardRows = emptyList()
                ).isSuccess
            } catch (_: Exception) {
                false
            }
            if (edited) return
        }
        deleteMessageSafely(token, chatId, existingPromptId)
        try {
            apiClient.sendMessage(
                token = token,
                chatId = chatId,
                text = formatted.text,
                entities = formatted.entities,
                includeGithubButton = false
            )
        } catch (_: Exception) {
        }
    }

    private suspend fun finalizeManagedMessage(
        token: String,
        chatId: Long,
        promptMessageId: Long?,
        formatted: FormattedMessage
    ) {
        if (promptMessageId != null) {
            val deleted = deleteMessageSafely(token, chatId, promptMessageId)
            if (!deleted) {
                val edited = try {
                    apiClient.editMessageText(
                        token = token,
                        chatId = chatId,
                        messageId = promptMessageId,
                        text = formatted.text,
                        entities = formatted.entities,
                        keyboardRows = emptyList()
                    ).isSuccess
                } catch (_: Exception) {
                    false
                }
                if (edited) return
            }
        }
        try {
            apiClient.sendMessage(
                token = token,
                chatId = chatId,
                text = formatted.text,
                entities = formatted.entities,
                includeGithubButton = false
            )
        } catch (_: Exception) {
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

    private suspend fun maybeSendWeeklyGithubReminder(
        token: String,
        chatId: Long,
        userId: Long
    ) {
        if (userId == 0L) return
        val authorizedChatId = preferencesManager.telegramConfig.value.authorizedChatId
        if (authorizedChatId == null || authorizedChatId != chatId) return

        val key = sessionKey(chatId, userId)
        val hasActiveSmsWorkflow = sessionMutex.withLock {
            activeReplySessions.containsKey(key) ||
                activeForwardSessions.containsKey(key) ||
                activeNewMessageSessions.containsKey(key)
        }
        if (hasActiveSmsWorkflow) return

        val eligible = preferencesManager.claimWeeklyGithubReminderIfEligible(
            userId = userId,
            nowMillis = clockProvider()
        )
        if (!eligible) return

        val formatted = TelegramEntityHelper.formatWeeklyGithubReminder()
        val keyboard = buildWeeklyGithubReminderKeyboard()
        try {
            apiClient.sendMessageWithKeyboard(
                token = token,
                chatId = chatId,
                text = formatted.text,
                entities = formatted.entities,
                keyboardRows = keyboard
            ).onSuccess {
                EventLogger.log(
                    LogCategory.TELEGRAM,
                    "Sent weekly GitHub support reminder to user $userId",
                    LogLevel.INFO
                )
            }
        } catch (_: Exception) {
            // Non-blocking: never disrupt bot functionality if reminder delivery fails
        }
    }

    private suspend fun handleGithubReminderDismiss(
        token: String,
        chatId: Long,
        messageId: Long?,
        cbId: String
    ) {
        try {
            apiClient.answerCallbackQuery(token, cbId, "Dismissed")
        } catch (_: Exception) {
        }
        val deleted = deleteMessageSafely(token, chatId, messageId)
        if (!deleted && messageId != null) {
            try {
                apiClient.editMessageText(
                    token = token,
                    chatId = chatId,
                    messageId = messageId,
                    text = "Reminder dismissed.",
                    keyboardRows = emptyList()
                )
            } catch (_: Exception) {
            }
        }
        EventLogger.log(LogCategory.TELEGRAM, "GitHub reminder dismissed", LogLevel.INFO)
    }

    private suspend fun handleGithubReminderDisable(
        token: String,
        chatId: Long,
        userId: Long,
        messageId: Long?,
        cbId: String
    ) {
        preferencesManager.setGithubReminderDisabled(
            userId = userId,
            disabled = true,
            nowMillis = clockProvider()
        )
        try {
            apiClient.answerCallbackQuery(token, cbId, "GitHub reminders disabled.")
        } catch (_: Exception) {
        }
        val deleted = deleteMessageSafely(token, chatId, messageId)
        if (!deleted && messageId != null) {
            try {
                apiClient.editMessageText(
                    token = token,
                    chatId = chatId,
                    messageId = messageId,
                    text = "GitHub reminders disabled.",
                    keyboardRows = emptyList()
                )
            } catch (_: Exception) {
            }
        }
        EventLogger.log(LogCategory.TELEGRAM, "GitHub reminders disabled for user $userId", LogLevel.INFO)
    }

    private fun buildWeeklyGithubReminderKeyboard(): List<List<InlineButton>> {
        return listOf(
            listOf(
                InlineButton(
                    text = TelegramEntityHelper.GITHUB_REMINDER_BUTTON_LABEL,
                    url = TelegramEntityHelper.GITHUB_REPO_URL
                )
            ),
            listOf(
                InlineButton(text = "Dismiss", callbackData = "gh_rem:dismiss"),
                InlineButton(text = "Don't remind me again", callbackData = "gh_rem:disable")
            )
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
                        text = dest.label.trim().ifBlank { "Chat ${dest.chatId}" },
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
