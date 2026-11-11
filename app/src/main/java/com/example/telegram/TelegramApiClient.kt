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
    val text: String?
)

class TelegramApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getMe(token: String): Result<TelegramUser> = withContext(Dispatchers.IO) {
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

    /**
     * Retrieves sticker information for custom emoji IDs using Telegram Bot API getCustomEmojiStickers.
     * Returns a map of custom_emoji_id -> original Unicode emoji string.
     */
    suspend fun getCustomEmojiStickers(
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

    /**
     * Parses the result array from getCustomEmojiStickers into a map of custom_emoji_id to emoji character.
     */
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
        } catch (_: Exception) {}
        return map
    }

    /**
     * Builds the GitHub InlineKeyboardButton JSONObject according to button mode.
     */
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

    suspend fun sendMessage(
        token: String,
        chatId: Long,
        text: String,
        entities: List<TelegramEntity> = emptyList(),
        includeGithubButton: Boolean = true
    ): Result<TelegramMessage> = withContext(Dispatchers.IO) {
        val mode = if (includeGithubButton) GithubButtonMode.ENHANCED else GithubButtonMode.NONE
        sendMessageInternal(token, chatId, text, entities, mode)
    }

    suspend fun sendMessage(
        token: String,
        chatId: Long,
        text: String,
        entities: List<TelegramEntity>,
        buttonMode: GithubButtonMode
    ): Result<TelegramMessage> = withContext(Dispatchers.IO) {
        sendMessageInternal(token, chatId, text, entities, buttonMode)
    }

    private fun sendMessageInternal(
        token: String,
        chatId: Long,
        text: String,
        entities: List<TelegramEntity>,
        buttonMode: GithubButtonMode
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
                    put("entities", entitiesArray)
                }

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

            val body = payload.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    // Graceful fallback for HTTP 400 Bad Request:
                    // If Telegram rejects enhanced button (icon_custom_emoji_id / style), fallback to standard button.
                    // If Telegram rejects standard button, fallback to sending message without button.
                    // Message content and entities are NEVER dropped!
                    if (response.code == 400) {
                        when (buttonMode) {
                            GithubButtonMode.ENHANCED -> {
                                return sendMessageInternal(cleanToken, chatId, text, entities, GithubButtonMode.STANDARD)
                            }
                            GithubButtonMode.STANDARD -> {
                                return sendMessageInternal(cleanToken, chatId, text, entities, GithubButtonMode.NONE)
                            }
                            GithubButtonMode.NONE -> {
                                // Already no button, return error below
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

    suspend fun getUpdates(token: String, offset: Long?, limit: Int = 10): Result<List<TelegramUpdate>> = withContext(Dispatchers.IO) {
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
                val list = mutableListOf<TelegramUpdate>()
                for (i in 0 until updatesArray.length()) {
                    val item = updatesArray.getJSONObject(i)
                    val updateId = item.getLong("update_id")
                    val message = item.optJSONObject("message")
                    val msgId = message?.optLong("message_id")
                    val chat = message?.optJSONObject("chat")
                    val chatId = chat?.optLong("id")
                    val from = message?.optJSONObject("from")
                    val userId = from?.optLong("id")
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
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(formatException(e))
        }
    }

    fun sanitizeToken(raw: String): String {
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
