package com.example.core

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AppPreferences
import com.example.data.model.SimControlConfig
import com.example.data.model.SimFeature
import com.example.data.model.SimFeatureSettings
import com.example.data.model.SimSelectionMode
import com.example.data.model.SmsReplyContext
import com.example.data.model.TelegramConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vanta_secure_prefs", Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    private val _telegramConfig = MutableStateFlow(loadTelegramConfig())
    val telegramConfig: StateFlow<TelegramConfig> = _telegramConfig.asStateFlow()

    private fun loadPreferences(): AppPreferences {
        val legacySimOrdinal = prefs.getInt(KEY_SIM_SELECTION, SimSelectionMode.ALL.ordinal)
        val legacySimMode = SimSelectionMode.entries.getOrElse(legacySimOrdinal) { SimSelectionMode.ALL }

        if (!prefs.getBoolean(KEY_SIM_CONTROL_MIGRATED, false)) {
            val s1Master = legacySimMode == SimSelectionMode.ALL || legacySimMode == SimSelectionMode.SIM1_ONLY
            val s2Master = legacySimMode == SimSelectionMode.ALL || legacySimMode == SimSelectionMode.SIM2_ONLY

            prefs.edit().apply {
                putBoolean(simKey(0, SUFFIX_MASTER), s1Master)
                putBoolean(simKey(0, SUFFIX_SMS_FWD), true)
                putBoolean(simKey(0, SUFFIX_OTP), true)
                putBoolean(simKey(0, SUFFIX_CALL_LOG), false)
                putBoolean(simKey(0, SUFFIX_INCOMING_CALL), false)
                putBoolean(simKey(0, SUFFIX_SMS_SEND), true)

                putBoolean(simKey(1, SUFFIX_MASTER), s2Master)
                putBoolean(simKey(1, SUFFIX_SMS_FWD), true)
                putBoolean(simKey(1, SUFFIX_OTP), true)
                putBoolean(simKey(1, SUFFIX_CALL_LOG), false)
                putBoolean(simKey(1, SUFFIX_INCOMING_CALL), false)
                putBoolean(simKey(1, SUFFIX_SMS_SEND), true)

                putBoolean(KEY_SIM_CONTROL_MIGRATED, true)
                apply()
            }
        }

        val simControl = SimControlConfig(
            sim1 = loadSimSettings(0),
            sim2 = loadSimSettings(1)
        )
        val derivedSimMode = simControl.toLegacySelectionMode()
        val globalOtpProtection = prefs.getBoolean(KEY_OTP_PROTECTION, false)

        return AppPreferences(
            isForwardingEnabled = prefs.getBoolean(KEY_FORWARDING_ENABLED, false),
            isStayAliveEnabled = prefs.getBoolean(KEY_STAY_ALIVE_ENABLED, true),
            isAutoStartEnabled = prefs.getBoolean(KEY_AUTO_START_ENABLED, false),
            isOtpProtectionEnabled = globalOtpProtection,
            hasSeenOtpSuggestion = prefs.getBoolean(KEY_SEEN_OTP_SUGGESTION, false),
            simSelectionMode = derivedSimMode,
            simControl = simControl,
            isTutorialCompleted = prefs.getBoolean(KEY_TUTORIAL_COMPLETED, false)
        )
    }

    private fun loadSimSettings(slotIndex: Int): SimFeatureSettings {
        return SimFeatureSettings(
            masterEnabled = prefs.getBoolean(simKey(slotIndex, SUFFIX_MASTER), true),
            smsForwardingEnabled = prefs.getBoolean(simKey(slotIndex, SUFFIX_SMS_FWD), true),
            otpMessagesEnabled = prefs.getBoolean(simKey(slotIndex, SUFFIX_OTP), true),
            callLogForwardingEnabled = prefs.getBoolean(simKey(slotIndex, SUFFIX_CALL_LOG), false),
            incomingCallNotificationsEnabled = prefs.getBoolean(simKey(slotIndex, SUFFIX_INCOMING_CALL), false),
            smsSendingEnabled = prefs.getBoolean(simKey(slotIndex, SUFFIX_SMS_SEND), true)
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
            remove(KEY_REPLY_CONTEXTS)
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
        val s1Master = mode == SimSelectionMode.ALL || mode == SimSelectionMode.SIM1_ONLY
        val s2Master = mode == SimSelectionMode.ALL || mode == SimSelectionMode.SIM2_ONLY
        prefs.edit().apply {
            putInt(KEY_SIM_SELECTION, mode.ordinal)
            putBoolean(simKey(0, SUFFIX_MASTER), s1Master)
            putBoolean(simKey(1, SUFFIX_MASTER), s2Master)
            apply()
        }
        _preferences.value = loadPreferences()
    }

    fun setSimMasterEnabled(slotIndex: Int, enabled: Boolean) {
        if (slotIndex !in 0..1) return
        prefs.edit().putBoolean(simKey(slotIndex, SUFFIX_MASTER), enabled).apply()
        val updated = loadPreferences()
        prefs.edit().putInt(KEY_SIM_SELECTION, updated.simControl.toLegacySelectionMode().ordinal).apply()
        _preferences.value = updated
    }

    fun setSimFeatureEnabled(slotIndex: Int, feature: SimFeature, enabled: Boolean) {
        if (slotIndex !in 0..1) return
        val suffix = when (feature) {
            SimFeature.SMS_FORWARDING -> SUFFIX_SMS_FWD
            SimFeature.OTP_MESSAGES -> SUFFIX_OTP
            SimFeature.CALL_LOG_FORWARDING -> SUFFIX_CALL_LOG
            SimFeature.INCOMING_CALL_NOTIFICATIONS -> SUFFIX_INCOMING_CALL
            SimFeature.SMS_SENDING -> SUFFIX_SMS_SEND
        }
        prefs.edit().apply {
            putBoolean(simKey(slotIndex, suffix), enabled)
            apply()
        }
        val updated = loadPreferences()
        prefs.edit().putInt(KEY_SIM_SELECTION, updated.simControl.toLegacySelectionMode().ordinal).apply()
        _preferences.value = updated
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

    // --- SMS Reply Context Persistence (survives service/app restarts) ---

    @Synchronized
    fun saveReplyContext(context: SmsReplyContext) {
        val current = loadReplyContexts().toMutableList()
        current.removeAll { it.contextId == context.contextId }
        current.add(0, context)
        if (current.size > MAX_REPLY_CONTEXTS) {
            current.subList(MAX_REPLY_CONTEXTS, current.size).clear()
        }
        val array = JSONArray()
        for (item in current) {
            array.put(
                JSONObject().apply {
                    put("id", item.contextId)
                    put("addr", item.senderAddress)
                    if (item.senderName != null) put("name", item.senderName)
                    put("slot", item.simSlot)
                    put("sub", item.subId)
                    put("ts", item.receivedTimestamp)
                }
            )
        }
        prefs.edit().putString(KEY_REPLY_CONTEXTS, array.toString()).apply()
    }

    @Synchronized
    fun getReplyContext(contextId: String): SmsReplyContext? {
        return loadReplyContexts().firstOrNull { it.contextId == contextId }
    }

    private fun loadReplyContexts(): List<SmsReplyContext> {
        val raw = prefs.getString(KEY_REPLY_CONTEXTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = ArrayList<SmsReplyContext>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(
                    SmsReplyContext(
                        contextId = obj.getString("id"),
                        senderAddress = obj.getString("addr"),
                        senderName = if (obj.has("name")) obj.getString("name") else null,
                        simSlot = obj.optInt("slot", -1),
                        subId = obj.optInt("sub", -1),
                        receivedTimestamp = obj.optLong("ts", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    // --- Call Log Baseline Persistence ---

    fun getLastForwardedCallLogId(): Long = prefs.getLong(KEY_LAST_CALL_LOG_ID, -1L)

    fun setLastForwardedCallLogId(id: Long) {
        prefs.edit().putLong(KEY_LAST_CALL_LOG_ID, id).apply()
    }

    fun hasInitializedCallLogBaseline(): Boolean = prefs.getBoolean(KEY_CALL_LOG_INITIALIZED, false)

    fun setCallLogBaselineInitialized(initialized: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_LOG_INITIALIZED, initialized).apply()
    }

    // --- Permission Requested Tracking ---

    fun markPermissionsRequested(permissions: List<String>) {
        prefs.edit().apply {
            permissions.forEach { putBoolean(KEY_PERM_ASKED_PREFIX + it, true) }
            apply()
        }
    }

    fun wasPermissionRequested(permission: String): Boolean {
        return prefs.getBoolean(KEY_PERM_ASKED_PREFIX + permission, false)
    }

    private fun simKey(slotIndex: Int, suffix: String): String = "sim${slotIndex}_$suffix"

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
        private const val KEY_SIM_CONTROL_MIGRATED = "sim_control_migrated_v2"
        private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"
        private const val KEY_LAST_DELIVERY = "last_delivery_timestamp"
        private const val KEY_DELIVERY_COUNT = "delivery_count"
        private const val KEY_REPLY_CONTEXTS = "sms_reply_contexts"
        private const val KEY_LAST_CALL_LOG_ID = "last_call_log_id"
        private const val KEY_CALL_LOG_INITIALIZED = "call_log_initialized"
        private const val KEY_PERM_ASKED_PREFIX = "perm_asked_"
        private const val MAX_REPLY_CONTEXTS = 60

        private const val SUFFIX_MASTER = "master"
        private const val SUFFIX_SMS_FWD = "sms_fwd"
        private const val SUFFIX_OTP = "otp_msgs"
        private const val SUFFIX_CALL_LOG = "call_log"
        private const val SUFFIX_INCOMING_CALL = "incoming_call"
        private const val SUFFIX_SMS_SEND = "sms_send"
    }
}
