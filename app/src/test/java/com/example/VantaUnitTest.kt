package com.example

import com.example.core.EventLogger
import com.example.data.model.LogCategory
import com.example.data.model.SimCardInfo
import com.example.sms.SmsProcessor
import com.example.telegram.TelegramEntityHelper
import com.example.ui.theme.VantaThemeId
import com.example.ui.theme.getVantaColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VantaUnitTest {

    @Test
    fun testTelegramCustomEmojiFormatting() {
        val sender = "+12025550198"
        val body = "Hello from VANTA test"
        val formatted = TelegramEntityHelper.formatIncomingSms(sender, body, "SIM1")

        // Must contain sender, body, and separator
        assertTrue(formatted.text.contains("By: $sender"))
        assertTrue(formatted.text.contains(body))
        assertTrue(formatted.text.contains("━━━━━━━━━━━━━━━━━━━━"))

        // Check custom emoji entities
        assertEquals(2, formatted.entities.size)
        val newMsgEntity = formatted.entities.first { it.customEmojiId == TelegramEntityHelper.NEW_MESSAGE_EMOJI_ID }
        val contextEntity = formatted.entities.first { it.customEmojiId == TelegramEntityHelper.CONTEXT_EMOJI_ID }

        assertEquals("5231379851402827068", newMsgEntity.customEmojiId)
        assertEquals("5260535596941582167", contextEntity.customEmojiId)
        assertEquals("custom_emoji", newMsgEntity.type)
        assertEquals("custom_emoji", contextEntity.type)
    }

    @Test
    fun testStatusMessageFormattingOnlineAndOffline() {
        val sims = listOf(
            SimCardInfo(
                slotIndex = 0,
                subId = 1,
                carrierName = "T-Mobile",
                phoneNumber = "+12025550101",
                isAvailable = true,
                isSelected = true
            ),
            SimCardInfo(
                slotIndex = 1,
                subId = 2,
                carrierName = "Irancell",
                phoneNumber = null,
                isAvailable = true,
                isSelected = false
            )
        )

        val onlinePlaceholder = TelegramEntityHelper.getPlaceholder(TelegramEntityHelper.ONLINE_EMOJI_ID)
        val onlineFormatted = TelegramEntityHelper.formatStatusMessage(true, sims)
        assertTrue(onlineFormatted.text.contains("Activation: $onlinePlaceholder ONLINE"))
        assertTrue(onlineFormatted.text.contains("SIM1 (T-Mobile): +12025550101"))
        assertTrue(onlineFormatted.text.contains("SIM2 (Irancell): NUMBER UNAVAILABLE"))

        // Check online custom emoji
        val actEntity = onlineFormatted.entities.first()
        assertEquals(TelegramEntityHelper.ONLINE_EMOJI_ID, actEntity.customEmojiId)
        assertEquals("Activation: ".length, actEntity.offset)
        assertEquals(onlinePlaceholder.length, actEntity.length)

        val offlinePlaceholder = TelegramEntityHelper.getPlaceholder(TelegramEntityHelper.OFFLINE_EMOJI_ID)
        val offlineFormatted = TelegramEntityHelper.formatStatusMessage(false, sims)
        assertTrue(offlineFormatted.text.contains("Activation: $offlinePlaceholder OFFLINE"))
        assertEquals(TelegramEntityHelper.OFFLINE_EMOJI_ID, offlineFormatted.entities.first().customEmojiId)
        assertEquals(offlinePlaceholder.length, offlineFormatted.entities.first().length)
    }

    @Test
    fun testUnicodePersianAndArabicSmsPreservation() {
        val persianSms = "سلام، این پیام برای تست زبان فارسی در سامانه وانتا می‌باشد."
        val formatted = TelegramEntityHelper.formatIncomingSms("IranTelecom", persianSms, "SIM1")

        assertTrue(formatted.text.contains(persianSms))
        val arabicSms = "مرحبا، هذا اختبار للغة العربية في فـانـتـا"
        val formattedArabic = TelegramEntityHelper.formatIncomingSms("Operator", arabicSms, "SIM2")
        assertTrue(formattedArabic.text.contains(arabicSms))
    }

    @Test
    fun testSensitiveOtpMessageDetection() {
        val otpPattern = Regex(
            pattern = """(?i)(\b(code|otp|verification|pin|password|security code|verify)\b|رمز|تایید|کد تایید|کد ورود|رمز یکبار مصرف|كلمة المرور|رمز التحقق)""",
            options = setOf(RegexOption.IGNORE_CASE)
        )

        fun isOtp(text: String): Boolean {
            if (otpPattern.containsMatchIn(text)) return true
            val hasDigits = Regex("""[\d\u0660-\u0669\u06F0-\u06F9]{4,8}""").containsMatchIn(text)
            val hasKeyword = text.contains("code", ignoreCase = true) ||
                    text.contains("pin", ignoreCase = true) ||
                    text.contains("رمز", ignoreCase = true) ||
                    text.contains("تایید", ignoreCase = true)
            return hasDigits && hasKeyword
        }

        assertTrue(isOtp("Your Google verification code is 849201. Do not share it."))
        assertTrue(isOtp("کد تایید ورود شما به سیستم: ۵۸۲۹۳۰"))
        assertTrue(isOtp("رمز یکبار مصرف شما: 123456"))
        assertTrue(isOtp("رمز التحقق الخاص بك هو 778899"))

        // Non-OTP standard messages should not trigger
        assertFalse(isOtp("Hey, let's meet at the coffee shop at 5pm!"))
        assertFalse(isOtp("Don't forget to buy milk and bread on your way home."))
    }

    @Test
    fun testEventLoggerRedactsTokens() {
        EventLogger.clear()
        val mockToken = "123456789:ABCdefGhIJKlmNoPQRstuVWXyz_12345"
        EventLogger.log(LogCategory.TELEGRAM, "Connecting with token: $mockToken")

        val latestLog = EventLogger.logs.value.first()
        assertFalse(latestLog.message.contains(mockToken))
        assertTrue(latestLog.message.contains("[REDACTED_BOT_TOKEN]"))
    }

    @Test
    fun testAllFourThemesAreConfigured() {
        for (theme in VantaThemeId.entries) {
            val colors = getVantaColors(theme)
            assertNotNull(colors.primary)
            assertNotNull(colors.primaryBright)
            assertNotNull(colors.primaryGlow)
            assertNotNull(colors.background)
        }
        assertEquals("Purple", VantaThemeId.PURPLE.displayName)
        assertEquals("Red", VantaThemeId.RED.displayName)
        assertEquals("Blue", VantaThemeId.BLUE.displayName)
        assertEquals("Cyan", VantaThemeId.CYAN.displayName)
    }

    @Test
    fun testOtpProtectionDefaultIsOff() {
        val defaultPrefs = com.example.data.model.AppPreferences()
        assertFalse("OTP protection must be OFF by default", defaultPrefs.isOtpProtectionEnabled)
        assertFalse("Seen OTP suggestion must be false by default", defaultPrefs.hasSeenOtpSuggestion)
    }

    @Test
    fun testTelegramTokenSanitization() {
        val client = com.example.telegram.TelegramApiClient()
        val rawToken = "123456789:ABCdefGhIJKlmNoPQRstuVWXyz"

        // Plain token with surrounding whitespace
        assertEquals(rawToken, client.sanitizeToken("  $rawToken  \n"))

        // Token prefixed with "bot"
        assertEquals(rawToken, client.sanitizeToken("bot$rawToken"))
        assertEquals(rawToken, client.sanitizeToken("BOT$rawToken"))

        // Full Telegram Bot API URL
        assertEquals(rawToken, client.sanitizeToken("https://api.telegram.org/bot$rawToken/getMe"))
        assertEquals(rawToken, client.sanitizeToken("https://api.telegram.org/bot$rawToken"))
    }

    @Test
    fun testTelegramApiClientTokenValidation() = kotlinx.coroutines.runBlocking {
        val client = com.example.telegram.TelegramApiClient()

        // Empty token
        val emptyResult = client.getMe("")
        assertTrue(emptyResult.isFailure)
        assertTrue(emptyResult.exceptionOrNull()?.message?.contains("cannot be empty") == true)

        // Invalid format without colon
        val invalidResult = client.getMe("abcdef123456789")
        assertTrue(invalidResult.isFailure)
        assertTrue(invalidResult.exceptionOrNull()?.message?.contains("Invalid token format") == true)
    }

    @Test
    fun testSecurityManagerEncryptionAndDecryption() {
        val sampleToken = "123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Test123"
        val encrypted = com.example.core.SecurityManager.encrypt(sampleToken)
        assertNotNull(encrypted)
        assertTrue(encrypted.isNotBlank())
        assertFalse(encrypted.contains(sampleToken))

        val decrypted = com.example.core.SecurityManager.decrypt(encrypted)
        assertEquals(sampleToken, decrypted)
    }

    @Test
    fun testCustomEmojiDynamicUtf16OffsetsAndLengths() {
        TelegramEntityHelper.resetEmojiCacheToDefaults()

        // Test with surrogate pairs (length 2)
        val formattedDefault = TelegramEntityHelper.formatIncomingSms("+123456789", "Test body", "SIM1")
        val newMsgEntity = formattedDefault.entities.first { it.customEmojiId == TelegramEntityHelper.NEW_MESSAGE_EMOJI_ID }
        val newMsgPlaceholder = TelegramEntityHelper.getPlaceholder(TelegramEntityHelper.NEW_MESSAGE_EMOJI_ID)
        assertEquals(0, newMsgEntity.offset)
        assertEquals(newMsgPlaceholder.length, newMsgEntity.length)

        val contextEntity = formattedDefault.entities.first { it.customEmojiId == TelegramEntityHelper.CONTEXT_EMOJI_ID }
        val contextPlaceholder = TelegramEntityHelper.getPlaceholder(TelegramEntityHelper.CONTEXT_EMOJI_ID)
        assertEquals(contextPlaceholder.length, contextEntity.length)

        // Verify entity accurately wraps the placeholder in the formatted text
        assertEquals(
            newMsgPlaceholder,
            formattedDefault.text.substring(newMsgEntity.offset, newMsgEntity.offset + newMsgEntity.length)
        )
        assertEquals(
            contextPlaceholder,
            formattedDefault.text.substring(contextEntity.offset, contextEntity.offset + contextEntity.length)
        )

        // Now test updating cache with 1-char BMP emoji (e.g., ⚡ U+26A1 length 1)
        TelegramEntityHelper.updateEmojiCache(
            mapOf(
                TelegramEntityHelper.NEW_MESSAGE_EMOJI_ID to "⚡",
                TelegramEntityHelper.CONTEXT_EMOJI_ID to "⚡"
            )
        )

        val formattedBmp = TelegramEntityHelper.formatIncomingSms("+123456789", "Test body", "SIM1")
        val bmpNewMsgEntity = formattedBmp.entities.first { it.customEmojiId == TelegramEntityHelper.NEW_MESSAGE_EMOJI_ID }
        assertEquals(1, bmpNewMsgEntity.length)
        assertEquals("⚡", formattedBmp.text.substring(bmpNewMsgEntity.offset, bmpNewMsgEntity.offset + bmpNewMsgEntity.length))

        // Reset back to defaults
        TelegramEntityHelper.resetEmojiCacheToDefaults()
    }

    @Test
    fun testParseCustomEmojiStickersResponse() {
        val client = com.example.telegram.TelegramApiClient()
        val mockJson = """
            {
                "ok": true,
                "result": [
                    {
                        "custom_emoji_id": "5470088863789622816",
                        "file_id": "CAACAgIAAxkBA...",
                        "file_unique_id": "AQAD...",
                        "width": 100,
                        "height": 100,
                        "is_animated": false,
                        "is_video": false,
                        "type": "custom_emoji",
                        "emoji": "🟢"
                    },
                    {
                        "custom_emoji_id": "4983325655899833037",
                        "file_id": "CAACAgIAAxkBA...",
                        "file_unique_id": "AQAD...",
                        "width": 100,
                        "height": 100,
                        "is_animated": false,
                        "is_video": false,
                        "type": "custom_emoji",
                        "emoji": "🐙"
                    }
                ]
            }
        """.trimIndent()

        val parsed = client.parseCustomEmojiStickersResponse(mockJson)
        assertEquals("🟢", parsed["5470088863789622816"])
        assertEquals("🐙", parsed["4983325655899833037"])
        assertEquals(2, parsed.size)
    }

    @Test
    fun testGithubButtonFormattingModes() {
        val client = com.example.telegram.TelegramApiClient()

        // Enhanced mode: must include icon_custom_emoji_id and style="primary"
        val enhancedBtn = client.buildGithubButton(com.example.telegram.GithubButtonMode.ENHANCED)
        assertNotNull(enhancedBtn)
        assertEquals("GitHub", enhancedBtn?.getString("text"))
        assertEquals("https://github.com/Sefeshki/", enhancedBtn?.getString("url"))
        assertEquals("4983325655899833037", enhancedBtn?.getString("icon_custom_emoji_id"))
        assertEquals("primary", enhancedBtn?.getString("style"))

        // Standard mode: must only include text and url, without icon_custom_emoji_id or style
        val standardBtn = client.buildGithubButton(com.example.telegram.GithubButtonMode.STANDARD)
        assertNotNull(standardBtn)
        assertEquals("GitHub", standardBtn?.getString("text"))
        assertEquals("https://github.com/Sefeshki/", standardBtn?.getString("url"))
        assertFalse(standardBtn?.has("icon_custom_emoji_id") == true)
        assertFalse(standardBtn?.has("style") == true)

        // None mode: must return null
        val noneBtn = client.buildGithubButton(com.example.telegram.GithubButtonMode.NONE)
        org.junit.Assert.assertNull(noneBtn)
    }
}
