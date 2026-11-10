package com.example.telegram

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun getMe(token: String): Result<TelegramUser> {
        return try {
            val url = "https://api.telegram.org/bot$token/getMe"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errDesc = parseErrorDescription(bodyString, response.code)
                    return Result.failure(Exception(errDesc))
                }

                val json = JSONObject(bodyString)
                if (!json.optBoolean("ok", false)) {
                    val desc = json.optString("description", "Unknown error")
                    return Result.failure(Exception(desc))
                }

                val result = json.getJSONObject("result")
                val user = TelegramUser(
                    id = result.getLong("id"),
                    isBot = result.optBoolean("is_bot", true),
                    firstName = result.optString("first_name", ""),
                    username = if (result.has("username")) result.getString("username") else null
                )
                Result.success(user)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendMessage(
        token: String,
        chatId: Long,
        text: String,
        entities: List<TelegramEntity> = emptyList(),
        includeGithubButton: Boolean = true
    ): Result<TelegramMessage> {
        return try {
            val url = "https://api.telegram.org/bot$token/sendMessage"
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

                if (includeGithubButton) {
                    val replyMarkup = JSONObject()
                    val keyboard = JSONArray()
                    val row = JSONArray()

                    val githubBtn = JSONObject().apply {
                        put("text", "GitHub")
                        put("url", "https://github.com/Sefeshki/")
                        // icon_custom_emoji_id is Telegram's field for button custom emoji
                        put("icon_custom_emoji_id", TelegramEntityHelper.GITHUB_EMOJI_ID)
                    }
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
            Result.failure(e)
        }
    }

    fun getUpdates(token: String, offset: Long?, limit: Int = 10): Result<List<TelegramUpdate>> {
        return try {
            val urlBuilder = StringBuilder("https://api.telegram.org/bot$token/getUpdates?timeout=5&limit=$limit")
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
                    return Result.failure(Exception("HTTP ${response.code}: $bodyString"))
                }

                val json = JSONObject(bodyString)
                if (!json.optBoolean("ok", false)) {
                    return Result.failure(Exception(json.optString("description", "Failed to fetch updates")))
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
            Result.failure(e)
        }
    }

    private fun parseErrorDescription(body: String, code: Int): String {
        return try {
            val json = JSONObject(body)
            json.optString("description", "HTTP $code")
        } catch (e: Exception) {
            "HTTP $code: $body"
        }
    }
}
