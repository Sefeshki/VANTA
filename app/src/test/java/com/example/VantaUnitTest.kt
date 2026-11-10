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

        val onlineFormatted = TelegramEntityHelper.formatStatusMessage(true, sims)
        assertTrue(onlineFormatted.text.contains("Activation: ⚡ ONLINE"))
        assertTrue(onlineFormatted.text.contains("SIM1 (T-Mobile): +12025550101"))
        assertTrue(onlineFormatted.text.contains("SIM2 (Irancell): NUMBER UNAVAILABLE"))

        // Check online custom emoji
        val actEntity = onlineFormatted.entities.first()
        assertEquals(TelegramEntityHelper.ONLINE_EMOJI_ID, actEntity.customEmojiId)

        val offlineFormatted = TelegramEntityHelper.formatStatusMessage(false, sims)
        assertTrue(offlineFormatted.text.contains("Activation: ⚡ OFFLINE"))
        assertEquals(TelegramEntityHelper.OFFLINE_EMOJI_ID, offlineFormatted.entities.first().customEmojiId)
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
}
