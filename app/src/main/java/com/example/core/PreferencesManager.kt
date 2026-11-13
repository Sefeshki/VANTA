package com.example.core

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppPreferences
import com.example.data.model.SimSelectionMode
import com.example.data.model.TelegramConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vanta_secure_prefs", Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    private val _telegramConfig = MutableStateFlow(loadTelegramConfig())
    val telegramConfig: StateFlow<TelegramConfig> = _telegramConfig.asStateFlow()

    private fun loadPreferences(): AppPreferences {
        val simOrdinal = prefs.getInt(KEY_SIM_SELECTION, SimSelectionMode.ALL.ordinal)
        val simMode = SimSelectionMode.entries.getOrElse(simOrdinal) { SimSelectionMode.ALL }

        return AppPreferences(
            isForwardingEnabled = prefs.getBoolean(KEY_FORWARDING_ENABLED, false),
            isStayAliveEnabled = prefs.getBoolean(KEY_STAY_ALIVE_ENABLED, true),
            isAutoStartEnabled = prefs.getBoolean(KEY_AUTO_START_ENABLED, false),
            isOtpProtectionEnabled = prefs.getBoolean(KEY_OTP_PROTECTION, false),
            hasSeenOtpSuggestion = prefs.getBoolean(KEY_SEEN_OTP_SUGGESTION, false),
            simSelectionMode = simMode,
            isTutorialCompleted = prefs.getBoolean(KEY_TUTORIAL_COMPLETED, false)
        )
    }

    private fun loadTelegramConfig(): TelegramConfig {
        val encryptedToken = prefs.getString(KEY_ENCRYPTED_TOKEN, "") ?: ""
        val token = if (encryptedToken.isNotEmpty()) SecurityManager.decrypt(encryptedToken) else ""
        val authorizedChatId = if (prefs.contains(KEY_AUTH_CHAT_ID)) prefs.getLong(KEY_AUTH_CHAT_ID, 0L) else null
        val botUsername = prefs.getString(KEY_BOT_USERNAME, null)
        val botId = if (prefs.contains(KEY_BOT_ID)) prefs.getLong(KEY_BOT_ID, 0L) else null
        val pairingCode = prefs.getString(KEY_PAIRING_CODE, null)
        val pairingExpiry = prefs.getLong(KEY_PAIRING_EXPIRY, 0L)

        return TelegramConfig(
            hasToken = token.isNotEmpty(),
            tokenMasked = if (token.isNotEmpty()) SecurityManager.maskToken(token) else "",
            botUsername = botUsername,
            botId = botId,
            authorizedChatId = authorizedChatId,
            activePairingCode = pairingCode,
            pairingCodeExpiry = pairingExpiry
        )
    }

    fun getDecryptedBotToken(): String {
        val encrypted = prefs.getString(KEY_ENCRYPTED_TOKEN, "") ?: ""
        return if (encrypted.isNotEmpty()) SecurityManager.decrypt(encrypted) else ""
    }

    fun saveBotToken(token: String, username: String?, botId: Long?) {
        val encrypted = SecurityManager.encrypt(token)
        prefs.edit().apply {
            putString(KEY_ENCRYPTED_TOKEN, encrypted)
            putString(KEY_BOT_USERNAME, username)
            if (botId != null) putLong(KEY_BOT_ID, botId) else remove(KEY_BOT_ID)
            apply()
        }
        _telegramConfig.value = loadTelegramConfig()
    }

    fun setAuthorizedChatId(chatId: Long?) {
        prefs.edit().apply {
            if (chatId != null) putLong(KEY_AUTH_CHAT_ID, chatId) else remove(KEY_AUTH_CHAT_ID)
            apply()
        }
        _telegramConfig.value = loadTelegramConfig()
    }

    fun setPairingCode(code: String?, expiryMillis: Long = 0L) {
        prefs.edit().apply {
            if (code != null) {
                putString(KEY_PAIRING_CODE, code)
                putLong(KEY_PAIRING_EXPIRY, expiryMillis)
            } else {
                remove(KEY_PAIRING_CODE)
                remove(KEY_PAIRING_EXPIRY)
            }
            apply()
        }
        _telegramConfig.value = loadTelegramConfig()
    }

    fun disconnectTelegram() {
        prefs.edit().apply {
            remove(KEY_ENCRYPTED_TOKEN)
            remove(KEY_BOT_USERNAME)
            remove(KEY_BOT_ID)
            remove(KEY_AUTH_CHAT_ID)
            remove(KEY_PAIRING_CODE)
            remove(KEY_PAIRING_EXPIRY)
            putBoolean(KEY_FORWARDING_ENABLED, false)
            apply()
        }
        _telegramConfig.value = loadTelegramConfig()
        _preferences.value = loadPreferences()
    }

    fun setForwardingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FORWARDING_ENABLED, enabled).apply()
        _preferences.value = _preferences.value.copy(isForwardingEnabled = enabled)
    }

    fun setStayAliveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STAY_ALIVE_ENABLED, enabled).apply()
        _preferences.value = _preferences.value.copy(isStayAliveEnabled = enabled)
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_ENABLED, enabled).apply()
        _preferences.value = _preferences.value.copy(isAutoStartEnabled = enabled)
    }

    fun setOtpProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OTP_PROTECTION, enabled).apply()
        _preferences.value = _preferences.value.copy(isOtpProtectionEnabled = enabled)
    }

    fun setSimSelectionMode(mode: SimSelectionMode) {
        prefs.edit().putInt(KEY_SIM_SELECTION, mode.ordinal).apply()
        _preferences.value = _preferences.value.copy(simSelectionMode = mode)
    }

    fun setTutorialCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_TUTORIAL_COMPLETED, completed).apply()
        _preferences.value = _preferences.value.copy(isTutorialCompleted = completed)
    }

    fun setSeenOtpSuggestion(seen: Boolean) {
        prefs.edit().putBoolean(KEY_SEEN_OTP_SUGGESTION, seen).apply()
        _preferences.value = _preferences.value.copy(hasSeenOtpSuggestion = seen)
    }

    fun getLastDeliveryTime(): Long = prefs.getLong(KEY_LAST_DELIVERY, 0L)

    fun recordDeliverySuccess() {
        val now = System.currentTimeMillis()
        val count = prefs.getInt(KEY_DELIVERY_COUNT, 0) + 1
        prefs.edit()
            .putLong(KEY_LAST_DELIVERY, now)
            .putInt(KEY_DELIVERY_COUNT, count)
            .apply()
    }

    fun getTotalDeliveredCount(): Int = prefs.getInt(KEY_DELIVERY_COUNT, 0)

    companion object {
        private const val KEY_ENCRYPTED_TOKEN = "enc_bot_token"
        private const val KEY_BOT_USERNAME = "bot_username"
        private const val KEY_BOT_ID = "bot_id"
        private const val KEY_AUTH_CHAT_ID = "auth_chat_id"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_PAIRING_EXPIRY = "pairing_expiry"
        private const val KEY_FORWARDING_ENABLED = "fwd_enabled"
        private const val KEY_STAY_ALIVE_ENABLED = "stay_alive_enabled"
        private const val KEY_AUTO_START_ENABLED = "auto_start_enabled"
        private const val KEY_OTP_PROTECTION = "otp_protection"
        private const val KEY_SEEN_OTP_SUGGESTION = "seen_otp_suggestion"
        private const val KEY_SIM_SELECTION = "sim_selection"
        private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"
        private const val KEY_LAST_DELIVERY = "last_delivery_timestamp"
        private const val KEY_DELIVERY_COUNT = "delivery_count"
    }
}
