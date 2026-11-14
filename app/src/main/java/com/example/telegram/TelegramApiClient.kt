package com.example.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

data class TelegramUser(
    val id: Long,
    val isBot: Boolean,
    val firstName: String,
    val username: String?
)

data class TelegramMessage(
    val messageId: Long,
    val date: Long,
    val text: String?
)

data class TelegramEntity(
    val type: String,
    val offset: Int,
    val length: Int,
    val customEmojiId: String? = null,
    val url: String? = null
)

data class InlineButton(
    val text: String,
    val url: String? = null,
    val callbackData: String? = null,
    val customEmojiId: String? = null,
    val style: String? = null
)

enum class GithubButtonMode {
    ENHANCED, // text, url, icon_custom_emoji_id, style="primary"
    STANDARD, // text, url
    NONE      // no inline button
}

data class TelegramUpdate(
    val updateId: Long,
    val messageId: Long?,
    val chatId: Long?,
    val userId: Long?,
    val userName: String?,
    val text: String?,
    val callbackQueryId: String? = null,
    val callbackData: String? = null,
    val callbackMessageId: Long? = null
)

interface TelegramBotApi {
    suspend fun getMe(token: String): Result<TelegramUser>
    suspend fun getCustomEmojiStickers(token: String, customEmojiIds: List<String>): Result<Map<String, String>>
    suspend fun sendMessage(
        token: String,
        chatId: Long,
        text: String,
        entities: List<TelegramEntity> = emptyList(),
        includeGithubButton: Boolean = true
    ): Result<TelegramMessage>

    suspend fun sendMessageWithKeyboard(
        token: String,
        chatId: Long,
        text: String,
        entities: List<TelegramEntity> = emptyList(),
        keyboardRows: List<List<InlineButton>> = emptyList()
    ): Result<TelegramMessage>

    suspend fun editMessageText(
        token: String,
        chatId: Long,
        messageId: Long,
        text: String,
        entities: List<TelegramEntity> = emptyList(),
        keyboardRows: List<List<InlineButton>> = emptyList()
    ): Result<Unit>

    suspend fun answerCallbackQuery(
        token: String,
        callbackQueryId: String,
        text: String? = null,
        showAlert: Boolean = false
    ): Result<Unit>

    suspend fun getUpdates(token: String, offset: Long?, limit: Int = 10): Result<List<TelegramUpdate>>
    fun sanitizeToken(raw: String): String
}

class TelegramApiClient : TelegramBotApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun getMe(token: String): Result<TelegramUser> = withContext(Dispatchers.IO) {
        val cleanToken = sanitizeToken(token)
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token cannot be empty"))
        }
        if (!cleanToken.contains(":") || cleanToken.length < 15) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid token format. Telegram tokens look like 123456789:ABCdef...")
            )
        }

        try {
            val url = "https://api.telegram.org/bot$cleanToken/getMe"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errDesc = parseErrorDescription(bodyString, response.code)
                    return@withContext Result.failure(Exception(errDesc))
                }

                val json = JSONObject(bodyString)
                if (!json.optBoolean("ok", false)) {
                    val desc = json.optString("description", "Unknown Telegram error")
                    return@withContext Result.failure(Exception(desc))
                }

                val result = json.getJSONObject("result")
                val isBot = result.optBoolean("is_bot", true)
                if (!isBot) {
                    return@withContext Result.failure(Exception("Provided token belongs to a user account, not a Telegram bot."))
                }

                val user = TelegramUser(
                    id = result.getLong("id"),
                    isBot = true,
                    firstName = result.optString("first_name", ""),
                    username = if (result.has("username")) result.getString("username") else null
                )
                Result.success(user)
            }
        } catch (e: Exception) {
            Result.failure(formatException(e))
        }
    }

    override suspend fun getCustomEmojiStickers(
        token: String,
        customEmojiIds: List<String>
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        val cleanToken = sanitizeToken(token)
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token cannot be empty"))
        }
        if (customEmojiIds.isEmpty()) {
            return@withContext Result.success(emptyMap())
        }

        try {
            val url = "https://api.telegram.org/bot$cleanToken/getCustomEmojiStickers"
            val payload = JSONObject().apply {
                val arr = JSONArray()
                customEmojiIds.forEach { arr.put(it) }
                put("custom_emoji_ids", arr)
            }
            val body = payload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errDesc = parseErrorDescription(bodyString, response.code)
                    return@withContext Result.failure(Exception(errDesc))
                }

                val json = JSONObject(bodyString)
                if (!json.optBoolean("ok", false)) {
                    val desc = json.optString("description", "Failed to fetch custom emoji stickers")
                    return@withContext Result.failure(Exception(desc))
                }

                val map = parseCustomEmojiStickersResponse(bodyString)
                Result.success(map)
            }
        } catch (e: Exception) {
            Result.failure(formatException(e))
        }
    }

    fun parseCustomEmojiStickersResponse(jsonString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val json = JSONObject(jsonString)
            if (!json.optBoolean("ok", false)) return emptyMap()
            val stickersArray = json.optJSONArray("result") ?: return emptyMap()
            for (i in 0 until stickersArray.length()) {
                val sticker = stickersArray.optJSONObject(i) ?: continue
                val id = sticker.optString("custom_emoji_id", "")
                val emoji = sticker.optString("emoji", "")
                if (id.isNotBlank() && emoji.isNotBlank()) {
                    map[id] = emoji
                }
            }
        } catch (_: Exception) {
        }
        return map
    }

    fun buildGithubButton(mode: GithubButtonMode): JSONObject? {
        if (mode == GithubButtonMode.NONE) return null
        return JSONObject().apply {
            put("text", "GitHub")
            put("url", "https://github.com/Sefeshki/")
            if (mode == GithubButtonMode.ENHANCED) {
                put("icon_custom_emoji_id", TelegramEntityHelper.GITHUB_EMOJI_ID)
                put("style", "primary")
            }
        }
    }

    fun buildInlineKeyboardMarkup(rows: List<List<InlineButton>>): JSONObject? {
        val nonEmptyRows = rows.filter { it.isNotEmpty() }
        if (nonEmptyRows.isEmpty()) return null
        val keyboard = JSONArray()
        for (row in nonEmptyRows) {
            val rowArr = JSONArray()
            for (btn in row) {
                rowArr.put(
                    JSONObject().apply {
                        put("text", btn.text)
                        if (!btn.url.isNullOrBlank()) put("url", btn.url)
                        if (!btn.callbackData.isNullOrBlank()) put("callback_data", btn.callbackData)
                        if (!btn.customEmojiId.isNullOrBlank()) put("icon_custom_emoji_id", btn.customEmojiId)
                        if (!btn.style.isNullOrBlank()) put("style", btn.style)
                    }
                )
            }
            keyboard.put(rowArr)
        }
        return JSONObject().put("inline_keyboard", keyboard)
    }

    override suspend fun sendMessage(
        token: String,
        chatId: Long,
        text: String,
        entities: List<TelegramEntity>,
        includeGithubButton: Boolean
    ): Result<TelegramMessage> = withContext(Dispatchers.IO) {
        val mode = if (includeGithubButton) GithubButtonMode.ENHANCED else GithubButtonMode.NONE
        sendMessageInternal(token, chatId, text, entities, mode, customKeyboard = null)
    }

    suspend fun sendMessage(
        token: String,
        chatId: Long,
        text: String,
        entities: List<TelegramEntity>,
        buttonMode: GithubButtonMode
    ): Result<TelegramMessage> = withContext(Dispatchers.IO) {
        sendMessageInternal(token, chatId, text, entities, buttonMode, customKeyboard = null)
    }

    override suspend fun sendMessageWithKeyboard(
        token: String,
        chatId: Long,
        text: String,
        entities: List<TelegramEntity>,
        keyboardRows: List<List<InlineButton>>
    ): Result<TelegramMessage> = withContext(Dispatchers.IO) {
        sendMessageInternal(
            token = token,
            chatId = chatId,
            text = text,
            entities = entities,
            buttonMode = GithubButtonMode.NONE,
            customKeyboard = buildInlineKeyboardMarkup(keyboardRows)
        )
    }

    override suspend fun editMessageText(
        token: String,
        chatId: Long,
        messageId: Long,
        text: String,
        entities: List<TelegramEntity>,
        keyboardRows: List<List<InlineButton>>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanToken = sanitizeToken(token)
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token not configured"))
        }

        try {
            val url = "https://api.telegram.org/bot$cleanToken/editMessageText"
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("message_id", messageId)
                put("text", text)
                if (entities.isNotEmpty()) {
                    put("entities", buildEntitiesArray(entities))
                }
                val markup = buildInlineKeyboardMarkup(keyboardRows)
                if (markup != null) {
                    put("reply_markup", markup)
                } else {
                    put("reply_markup", JSONObject().put("inline_keyboard", JSONArray()))
                }
            }
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception(parseErrorDescription(bodyString, response.code)))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(formatException(e))
        }
    }

    override suspend fun answerCallbackQuery(
        token: String,
        callbackQueryId: String,
        text: String?,
        showAlert: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanToken = sanitizeToken(token)
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token not configured"))
        }

        try {
            val url = "https://api.telegram.org/bot$cleanToken/answerCallbackQuery"
            val payload = JSONObject().apply {
                put("callback_query_id", callbackQueryId)
                if (!text.isNullOrBlank()) put("text", text)
                if (showAlert) put("show_alert", true)
            }
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception(parseErrorDescription(bodyString, response.code)))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(formatException(e))
        }
    }

    private fun sendMessageInternal(
        token: String,
        chatId: Long,
        text: String,
        entities: List<TelegramEntity>,
        buttonMode: GithubButtonMode,
        customKeyboard: JSONObject?
    ): Result<TelegramMessage> {
        val cleanToken = sanitizeToken(token)
        if (cleanToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Bot token not configured"))
        }

        return try {
            val url = "https://api.telegram.org/bot$cleanToken/sendMessage"
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)

                if (entities.isNotEmpty()) {
                    put("entities", buildEntitiesArray(entities))
                }

                if (customKeyboard != null) {
                    put("reply_markup", customKeyboard)
                } else {
                    val githubBtn = buildGithubButton(buttonMode)
                    if (githubBtn != null) {
                        val replyMarkup = JSONObject()
                        val keyboard = JSONArray()
                        val row = JSONArray()
                        row.put(githubBtn)
                        keyboard.put(row)
                        replyMarkup.put("inline_keyboard", keyboard)
                        put("reply_markup", replyMarkup)
                    }
                }
            }

            val body = payload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    if (response.code == 400 && customKeyboard == null) {
                        when (buttonMode) {
                            GithubButtonMode.ENHANCED -> {
                                return sendMessageInternal(cleanToken, chatId, text, entities, GithubButtonMode.STANDARD, null)
                            }
                            GithubButtonMode.STANDARD -> {
                                return sendMessageInternal(cleanToken, chatId, text, entities, GithubButtonMode.NONE, null)
                            }
                            GithubButtonMode.NONE -> {
                            }
                        }
                    }
                    val errDesc = parseErrorDescription(bodyString, response.code)
                    return Result.failure(Exception(errDesc))
                }

                val json = JSONObject(bodyString)
                if (!json.optBoolean("ok", false)) {
                    val desc = json.optString("description", "Unknown error")
                    return Result.failure(Exception(desc))
                }

                val resObj = json.getJSONObject("result")
                val msg = TelegramMessage(
                    messageId = resObj.getLong("message_id"),
                    date = resObj.optLong("date", System.currentTimeMillis() / 1000),
                    text = resObj.optString("text", text)
                )
                Result.success(msg)
            }
        } catch (e: Exception) {
            Result.failure(formatException(e))
        }
    }

    private fun buildEntitiesArray(entities: List<TelegramEntity>): JSONArray {
        val entitiesArray = JSONArray()
        for (entity in entities) {
            val entityJson = JSONObject().apply {
                put("type", entity.type)
                put("offset", entity.offset)
                put("length", entity.length)
                if (entity.customEmojiId != null) {
                    put("custom_emoji_id", entity.customEmojiId)
                }
                if (entity.url != null) {
                    put("url", entity.url)
                }
            }
            entitiesArray.put(entityJson)
        }
        return entitiesArray
    }

    override suspend fun getUpdates(token: String, offset: Long?, limit: Int): Result<List<TelegramUpdate>> = withContext(Dispatchers.IO) {
        val cleanToken = sanitizeToken(token)
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token not configured"))
        }

        try {
            val urlBuilder = StringBuilder("https://api.telegram.org/bot$cleanToken/getUpdates?timeout=5&limit=$limit")
            if (offset != null) {
                urlBuilder.append("&offset=").append(offset)
            }
            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errDesc = parseErrorDescription(bodyString, response.code)
                    return@withContext Result.failure(Exception(errDesc))
                }

                val json = JSONObject(bodyString)
                if (!json.optBoolean("ok", false)) {
                    return@withContext Result.failure(Exception(json.optString("description", "Failed to fetch updates")))
                }

                val updatesArray = json.getJSONArray("result")
                val list = parseUpdatesArray(updatesArray)
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(formatException(e))
        }
    }

    fun parseUpdatesArray(updatesArray: JSONArray): List<TelegramUpdate> {
        val list = mutableListOf<TelegramUpdate>()
        for (i in 0 until updatesArray.length()) {
            val item = updatesArray.optJSONObject(i) ?: continue
            val updateId = item.getLong("update_id")

            val callbackQuery = item.optJSONObject("callback_query")
            if (callbackQuery != null) {
                val cbId = callbackQuery.optString("id", "").takeIf { it.isNotBlank() }
                val cbData = callbackQuery.optString("data", "").takeIf { it.isNotBlank() }
                val cbFrom = callbackQuery.optJSONObject("from")
                val cbUserId = if (cbFrom?.has("id") == true) cbFrom.getLong("id") else null
                val cbUserName = if (cbFrom?.has("username") == true) cbFrom.getString("username") else null
                val cbMsg = callbackQuery.optJSONObject("message")
                val cbMsgId = if (cbMsg?.has("message_id") == true) cbMsg.getLong("message_id") else null
                val cbChatId = cbMsg?.optJSONObject("chat")?.let {
                    if (it.has("id")) it.getLong("id") else null
                }

                list.add(
                    TelegramUpdate(
                        updateId = updateId,
                        messageId = cbMsgId,
                        chatId = cbChatId,
                        userId = cbUserId,
                        userName = cbUserName,
                        text = null,
                        callbackQueryId = cbId,
                        callbackData = cbData,
                        callbackMessageId = cbMsgId
                    )
                )
                continue
            }

            val message = item.optJSONObject("message")
            val msgId = if (message?.has("message_id") == true) message.getLong("message_id") else null
            val chat = message?.optJSONObject("chat")
            val chatId = if (chat?.has("id") == true) chat.getLong("id") else null
            val from = message?.optJSONObject("from")
            val userId = if (from?.has("id") == true) from.getLong("id") else null
            val userName = if (from?.has("username") == true) from.getString("username") else null
            val text = if (message?.has("text") == true) message.getString("text") else null

            list.add(
                TelegramUpdate(
                    updateId = updateId,
                    messageId = msgId,
                    chatId = chatId,
                    userId = userId,
                    userName = userName,
                    text = text
                )
            )
        }
        return list
    }

    override fun sanitizeToken(raw: String): String {
        var t = raw.trim()
        if (t.startsWith("https://api.telegram.org/bot", ignoreCase = true)) {
            t = t.substring(28).substringBefore("/").substringBefore("?")
        } else if (t.startsWith("http://api.telegram.org/bot", ignoreCase = true)) {
            t = t.substring(27).substringBefore("/").substringBefore("?")
        } else if (t.startsWith("bot", ignoreCase = true) && t.length > 3 && t[3].isDigit()) {
            t = t.substring(3)
        }
        return t.trim()
    }

    private fun parseErrorDescription(body: String, code: Int): String {
        val apiDescription = try {
            val json = JSONObject(body)
            json.optString("description", "")
        } catch (e: Exception) {
            ""
        }
        return when {
            code == 401 -> "Invalid bot token (Unauthorized). Please verify with @BotFather."
            code == 404 -> "Bot not found (404). Check the token format."
            code == 400 && apiDescription.isNotBlank() -> "Telegram request error: $apiDescription"
            code == 429 -> "Telegram rate limit reached (429). Please wait a moment."
            apiDescription.isNotBlank() -> apiDescription
            else -> "Telegram error (HTTP $code)"
        }
    }

    private fun formatException(e: Exception): Exception {
        return when (e) {
            is UnknownHostException -> Exception("Cannot connect to Telegram servers. Please check your internet connection.", e)
            is SocketTimeoutException -> Exception("Connection to Telegram timed out. Please try again.", e)
            is SSLException -> Exception("Secure TLS connection to Telegram failed. Check network or date/time settings.", e)
            else -> Exception(e.message ?: "Failed to connect to Telegram", e)
        }
    }
}
