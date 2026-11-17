package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.PreferencesManager
import com.example.data.model.SimCardInfo
import com.example.data.model.SimFeature
import com.example.telegram.BotMenuPanel
import com.example.telegram.InlineButton
import com.example.telegram.NewMessageStep
import com.example.telegram.PhoneNumberValidator
import com.example.telegram.TelegramBotApi
import com.example.telegram.TelegramBotManager
import com.example.telegram.TelegramEntity
import com.example.telegram.TelegramMenuHelper
import com.example.telegram.TelegramMessage
import com.example.telegram.TelegramUpdate
import com.example.telegram.TelegramUser
import com.example.telephony.SmsGateway
import com.example.telephony.SmsSendResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewMessageAndOtpRestrictionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vanta_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testInternationalPhoneNumberValidationAndFormatting() {
        val v1 = PhoneNumberValidator.validateInternationalNumber("+98 9211234567")
        assertNotNull(v1)
        assertEquals("+98 9211234567", v1!!.displayNumber)
        assertEquals("+989211234567", v1.dialableNumber)

        val v2 = PhoneNumberValidator.validateInternationalNumber("  +1 (415) 555-0199 ")
        assertNotNull(v2)
        assertEquals("+1 (415) 555-0199", v2!!.displayNumber)
        assertEquals("+14155550199", v2.dialableNumber)

        val v3 = PhoneNumberValidator.validateInternationalNumber("0044 7911 123456")
        assertNotNull(v3)
        assertEquals("+44 7911 123456", v3!!.displayNumber)
        assertEquals("+447911123456", v3.dialableNumber)

        // Clearly invalid inputs must be rejected
        assertNull(PhoneNumberValidator.validateInternationalNumber(""))
        assertNull(PhoneNumberValidator.validateInternationalNumber("hello"))
        assertNull(PhoneNumberValidator.validateInternationalNumber("+98abc12345"))
        assertNull(PhoneNumberValidator.validateInternationalNumber("09211234567")) // missing country code
        assertNull(PhoneNumberValidator.validateInternationalNumber("9211234567")) // missing +/00
        assertNull(PhoneNumberValidator.validateInternationalNumber("+09211234567")) // country code cannot start with 0
        assertNull(PhoneNumberValidator.validateInternationalNumber("+98 123")) // too short
        assertNull(PhoneNumberValidator.validateInternationalNumber("+1234567890123456")) // > 15 digits
        assertNull(PhoneNumberValidator.validateInternationalNumber("+98+9211234567")) // '+' in middle
        assertNull(PhoneNumberValidator.validateInternationalNumber("*123#"))
        assertNull(PhoneNumberValidator.validateInternationalNumber("+98 () 9211234567"))
    }

    @Test
    fun testOtpProtectionIsStrictlyAppOnlyAcrossAllTelegramPanelsAndCallbacks() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.saveBotToken("123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Test", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)
        prefsMgr.setOtpProtectionEnabled(true)

        val sims = listOf(
            SimCardInfo(0, 10, "Alpha", "+10001", true, true, hasPermission = true),
            SimCardInfo(1, 20, "Beta", "+10002", true, true, hasPermission = true)
        )

        // Verify no panel exposes any OTP button or callback
        for (panel in BotMenuPanel.entries) {
            val keyboard = TelegramMenuHelper.buildPanelKeyboard(
                panel = panel,
                isForwardingEnabled = true,
                sims = sims,
                simControl = prefsMgr.preferences.value.simControl
            ).flatten()
            assertFalse(
                "Panel ${panel.id} must not expose any OTP callback",
                keyboard.any { it.callbackData?.contains("otp", ignoreCase = true) == true }
            )
            assertFalse(
                "Panel ${panel.id} must not expose any OTP toggle label",
                keyboard.any { it.text.contains("OTP", ignoreCase = true) }
            )
        }

        // Verify forged OTP callbacks never mutate OTP preference
        val fakeApi = FakeTelegramBotApi()
        val botManager = TelegramBotManager(prefsMgr, fakeApi, autoStartPolling = false).apply {
            simInfoProvider = { sims }
        }
        botManager.handleUpdate(TelegramUpdate(1L, null, 9001L, 50L, "owner", null, "cb_otp1", "act:otp_toggle:controls", 10L))
        assertTrue("OTP must remain unchanged (true)", prefsMgr.preferences.value.isOtpProtectionEnabled)

        prefsMgr.setOtpProtectionEnabled(false)
        botManager.handleUpdate(TelegramUpdate(2L, null, 9001L, 50L, "owner", null, "cb_otp2", "act:otp_toggle:forwarding", 10L))
        assertFalse("OTP must remain unchanged (false)", prefsMgr.preferences.value.isOtpProtectionEnabled)
    }

    @Test
    fun testNewMessageDualSimFullWorkflowCleanupAndSuccessFormat() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.saveBotToken("123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Test", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)

        val sims = listOf(
            SimCardInfo(0, 11, "Irancell", "+989350001", true, true, hasPermission = true),
            SimCardInfo(1, 22, "MCI", "+989120002", true, true, hasPermission = true)
        )
        val fakeApi = FakeTelegramBotApi()
        val fakeGateway = FakeSmsGateway()
        val botManager = TelegramBotManager(prefsMgr, fakeApi, autoStartPolling = false).apply {
            simInfoProvider = { sims }
            smsGateway = fakeGateway
            hasSendSmsPermissionProvider = { true }
        }

        // Unrelated incoming SMS notification to verify cleanup does NOT delete it
        val incomingNotif = botManager.forwardSms(
            sender = "+14155550100",
            body = "Existing incoming message",
            simLabel = "SIM 1 · Irancell",
            simSlot = 0,
            subId = 11
        ).getOrThrow()

        // 1. Open bot menu & click New Message
        botManager.handleUpdate(TelegramUpdate(1L, 200L, 9001L, 50L, "owner", "/menu"))
        val menuSend = fakeApi.sentMessages.last()
        val menuMsgId = fakeApi.nextMsgId - 1
        val newMsgBtn = menuSend.keyboardRows.flatten().first { it.text == "New Message" }

        botManager.handleUpdate(
            TelegramUpdate(2L, null, 9001L, 50L, "owner", null, "cb_new", newMsgBtn.callbackData, menuMsgId)
        )
        val recipientPromptSend = fakeApi.sentMessages.last()
        val promptMsgId = fakeApi.nextMsgId - 1
        assertTrue(recipientPromptSend.text.contains("Enter the recipient's phone number:"))
        assertTrue(recipientPromptSend.text.contains("+98 921xxxxxxx"))
        assertEquals(NewMessageStep.AWAITING_RECIPIENT, botManager.getActiveNewMessageSession(9001L, 50L)?.step)

        // 2. Enter clearly invalid number -> rejected, user msg cleaned, asked again
        botManager.handleUpdate(TelegramUpdate(3L, 501L, 9001L, 50L, "owner", "09123456789"))
        assertTrue(fakeApi.deletedMessages.contains(9001L to 501L))
        assertTrue(fakeApi.editedMessages.last().text.contains("Invalid phone number"))
        assertTrue(fakeApi.editedMessages.last().text.contains("Enter the recipient's phone number:"))
        assertEquals(NewMessageStep.AWAITING_RECIPIENT, botManager.getActiveNewMessageSession(9001L, 50L)?.step)

        // 3. Enter valid number -> shows exact confirmation with exactly No and Yes buttons
        botManager.handleUpdate(TelegramUpdate(4L, 502L, 9001L, 50L, "owner", "+98 9211234567"))
        assertTrue(fakeApi.deletedMessages.contains(9001L to 502L))
        val confirmEdit = fakeApi.editedMessages.last()
        assertEquals("Recipient:\n+98 9211234567\n\nIs this number correct?", confirmEdit.text)
        val confirmButtons = confirmEdit.keyboardRows.flatten()
        assertEquals(2, confirmButtons.size)
        assertEquals("No", confirmButtons[0].text)
        assertEquals("Yes", confirmButtons[1].text)
        assertEquals(0, fakeGateway.dispatched.size)

        // 4. Tap No -> returns to recipient number input step
        botManager.handleUpdate(
            TelegramUpdate(5L, null, 9001L, 50L, "owner", null, "cb_no", confirmButtons[0].callbackData, promptMsgId)
        )
        assertTrue(fakeApi.editedMessages.last().text.contains("Enter the recipient's phone number:"))
        assertEquals(NewMessageStep.AWAITING_RECIPIENT, botManager.getActiveNewMessageSession(9001L, 50L)?.step)

        // 5. Enter corrected number and confirm Yes -> asks "What would you like to send?"
        botManager.handleUpdate(TelegramUpdate(6L, 503L, 9001L, 50L, "owner", "+98 9217654321"))
        val yesBtn = fakeApi.editedMessages.last().keyboardRows.flatten().first { it.text == "Yes" }
        botManager.handleUpdate(
            TelegramUpdate(7L, null, 9001L, 50L, "owner", null, "cb_yes", yesBtn.callbackData, promptMsgId)
        )
        assertEquals("What would you like to send?", fakeApi.editedMessages.last().text)
        assertEquals(NewMessageStep.AWAITING_BODY, botManager.getActiveNewMessageSession(9001L, 50L)?.step)
        assertEquals(0, fakeGateway.dispatched.size)

        // 6. Compose message preserving punctuation, spacing, newlines -> shows 2 SIM choices
        val originalBody = "  Hello! First line.\nSecond line — 100% intact?  "
        botManager.handleUpdate(TelegramUpdate(8L, 504L, 9001L, 50L, "owner", originalBody))
        assertTrue(fakeApi.deletedMessages.contains(9001L to 504L))
        assertEquals(0, fakeGateway.dispatched.size)

        val simPrompt = fakeApi.editedMessages.last()
        assertTrue(simPrompt.text.contains("To: +98 9217654321"))
        val simButtons = simPrompt.keyboardRows.flatten()
        assertTrue(simButtons.any { it.text == "SIM 1 — Irancell" })
        assertTrue(simButtons.any { it.text == "SIM 2 — MCI" })
        val sim2Cb = simButtons.first { it.text == "SIM 2 — MCI" }.callbackData!!

        // 7. Select SIM 2 -> sends SMS once, cleans temporary prompt, posts ONLY final confirmation
        botManager.handleUpdate(
            TelegramUpdate(9L, null, 9001L, 50L, "owner", null, "cb_sim2", sim2Cb, promptMsgId)
        )
        // Duplicate tap on SIM 2 callback must be ignored (no duplicate send)
        botManager.handleUpdate(
            TelegramUpdate(10L, null, 9001L, 50L, "owner", null, "cb_sim2_dup", sim2Cb, promptMsgId)
        )

        assertEquals(1, fakeGateway.dispatched.size)
        assertEquals("+989217654321", fakeGateway.dispatched.first().destination)
        assertEquals(originalBody, fakeGateway.dispatched.first().messageText)
        assertEquals(1, fakeGateway.dispatched.first().slotIndex)
        assertNull(botManager.getActiveNewMessageSession(9001L, 50L))

        // Temporary prompt deleted
        assertTrue(fakeApi.deletedMessages.contains(9001L to promptMsgId))
        // Existing incoming SMS and overview menu must NOT be deleted
        assertFalse(fakeApi.deletedMessages.any { it.second == incomingNotif.messageId })
        assertFalse(fakeApi.deletedMessages.any { it.second == menuMsgId })

        val finalMsg = fakeApi.sentMessages.last().text
        assertTrue(finalMsg.startsWith("Message sent!\n\nTo: +98 9217654321 (SIM 2)\n\nTime: "))
        assertTrue(finalMsg.contains("\n\nText:\n$originalBody\n\n---------------------"))
        assertTrue(finalMsg.endsWith("---------------------"))
        assertTrue(fakeApi.sentMessages.last().keyboardRows.isEmpty())

        // 8. Verify existing incoming SMS Reply still works cleanly afterwards
        val replyCb = fakeApi.sentMessages.first { it.text.contains("Existing incoming message") }
            .keyboardRows.flatten().first { it.text == "Reply" }.callbackData!!
        botManager.handleUpdate(
            TelegramUpdate(11L, null, 9001L, 50L, "owner", null, "cb_rep", replyCb, incomingNotif.messageId)
        )
        assertEquals("Send the message you want to reply:", fakeApi.sentMessages.last().text)
        botManager.handleUpdate(TelegramUpdate(12L, 505L, 9001L, 50L, "owner", "Got it!"))
        assertEquals(2, fakeGateway.dispatched.size)
        assertEquals("+14155550100", fakeGateway.dispatched.last().destination)
        assertEquals(0, fakeGateway.dispatched.last().slotIndex)
    }

    @Test
    fun testSingleAvailableSimAutoSelectsWithoutSimPrompt() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.saveBotToken("123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Test", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)
        // SIM 1 disabled in SIM Control, SIM 2 enabled & active -> only 1 SIM available for outgoing SMS!
        prefsMgr.setSimMasterEnabled(0, false)
        prefsMgr.setSimMasterEnabled(1, true)

        val sims = listOf(
            SimCardInfo(0, 10, "Alpha", null, true, false, hasPermission = true),
            SimCardInfo(1, 25, "Beta", null, true, true, hasPermission = true)
        )
        val fakeApi = FakeTelegramBotApi()
        val fakeGateway = FakeSmsGateway()
        val botManager = TelegramBotManager(prefsMgr, fakeApi, autoStartPolling = false).apply {
            simInfoProvider = { sims }
            smsGateway = fakeGateway
            hasSendSmsPermissionProvider = { true }
        }

        botManager.handleUpdate(TelegramUpdate(1L, null, 9001L, 50L, "owner", null, "cb_start", "newmsg:start", 10L))
        val promptId = fakeApi.nextMsgId - 1
        botManager.handleUpdate(TelegramUpdate(2L, 300L, 9001L, 50L, "owner", "+14155550123"))
        val yesCb = fakeApi.editedMessages.last().keyboardRows.flatten().first { it.text == "Yes" }.callbackData!!
        botManager.handleUpdate(TelegramUpdate(3L, null, 9001L, 50L, "owner", null, "cb_yes", yesCb, promptId))
        botManager.handleUpdate(TelegramUpdate(4L, 301L, 9001L, 50L, "owner", "Auto SIM test"))

        // No SIM picker prompt was shown; automatically used SIM 2
        assertFalse(fakeApi.editedMessages.any { it.text.contains("Which SIM should send") })
        assertEquals(1, fakeGateway.dispatched.size)
        assertEquals(1, fakeGateway.dispatched.first().slotIndex)
        assertTrue(fakeApi.sentMessages.last().text.contains("To: +14155550123 (SIM 2)"))
    }

    @Test
    fun testNoSimAvailablePermissionDeniedMidFlowDisconnectSubIdChangeAndDeleteFailure() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.saveBotToken("123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Test", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)

        var currentSims = listOf(
            SimCardInfo(0, 10, "Carrier unavailable", null, true, true, hasPermission = true),
            SimCardInfo(1, 20, "Beta", null, true, true, hasPermission = true)
        )
        var hasSendPerm = true
        var failDeletes = false
        var failGatewaySend = false

        val resilientApi = object : TelegramBotApi {
            var nextId = 100L
            val sent = mutableListOf<Pair<String, List<List<InlineButton>>>>()
            val edited = mutableListOf<Pair<Long, String>>()
            override suspend fun getMe(token: String) = Result.success(TelegramUser(1L, true, "Bot", "vanta_bot"))
            override suspend fun getCustomEmojiStickers(token: String, customEmojiIds: List<String>) = Result.success(emptyMap<String, String>())
            override suspend fun sendMessage(
                token: String,
                chatId: Long,
                text: String,
                entities: List<TelegramEntity>,
                includeGithubButton: Boolean
            ): Result<TelegramMessage> {
                val id = nextId++
                sent.add(text to emptyList())
                return Result.success(TelegramMessage(id, 1000L, text))
            }
            override suspend fun sendMessageWithKeyboard(
                token: String,
                chatId: Long,
                text: String,
                entities: List<TelegramEntity>,
                keyboardRows: List<List<InlineButton>>
            ): Result<TelegramMessage> {
                val id = nextId++
                sent.add(text to keyboardRows)
                return Result.success(TelegramMessage(id, 1000L, text))
            }
            override suspend fun editMessageText(
                token: String,
                chatId: Long,
                messageId: Long,
                text: String,
                entities: List<TelegramEntity>,
                keyboardRows: List<List<InlineButton>>
            ): Result<Unit> {
                edited.add(messageId to text)
                return Result.success(Unit)
            }
            override suspend fun deleteMessage(token: String, chatId: Long, messageId: Long): Result<Unit> {
                return if (failDeletes) Result.failure(Exception("Bad Request: message can't be deleted")) else Result.success(Unit)
            }
            override suspend fun answerCallbackQuery(token: String, callbackQueryId: String, text: String?, showAlert: Boolean) = Result.success(Unit)
            override suspend fun getUpdates(token: String, offset: Long?, limit: Int) = Result.success(emptyList<TelegramUpdate>())
            override fun sanitizeToken(raw: String) = raw.trim()
        }

        val gateway = object : SmsGateway {
            val sends = mutableListOf<Pair<String, Int>>()
            override suspend fun sendSms(destination: String, messageText: String, slotIndex: Int): Result<SmsSendResult> {
                if (failGatewaySend) return Result.failure(IllegalStateException("Carrier radio off"))
                sends.add(destination to slotIndex)
                return Result.success(SmsSendResult(destination, slotIndex, 10 + slotIndex, "Carrier", 1))
            }
            override fun canReplyToAddress(address: String?) = true
        }

        val botManager = TelegramBotManager(prefsMgr, resilientApi, autoStartPolling = false).apply {
            simRefreshProvider = { currentSims }
            hasSendSmsPermissionProvider = { hasSendPerm }
            smsGateway = gateway
        }

        // A. Unavailable carrier label never fabricates carrier, shows "SIM 1" cleanly; subId change blocks silent send
        botManager.handleUpdate(TelegramUpdate(1L, null, 9001L, 50L, "owner", null, "c1", "newmsg:start", 10L))
        val p1 = resilientApi.nextId - 1
        botManager.handleUpdate(TelegramUpdate(2L, 400L, 9001L, 50L, "owner", "+98 9210001111"))
        val yes1 = resilientApi.edited.last().second
        assertTrue(yes1.contains("+98 9210001111"))
        // Extract Yes callback by inspecting Confirm step
        botManager.handleUpdate(TelegramUpdate(3L, null, 9001L, 50L, "owner", null, "c2",
            "newmsg_confirm:${botManager.getActiveNewMessageSession(9001L, 50L)!!.sessionId}:yes", p1))
        botManager.handleUpdate(TelegramUpdate(4L, 401L, 9001L, 50L, "owner", "Test subId change"))
        val simPromptWithUnavailableCarrier = resilientApi.sent.lastOrNull()?.second
        // Note: deletes worked, so SIM prompt was edited in place; inspect edited + check session
        val sess1 = botManager.getActiveNewMessageSession(9001L, 50L)!!
        assertEquals(NewMessageStep.SELECTING_SIM, sess1.step)

        // Simulate Android changing SIM 1 subscription ID from 10 -> 99 mid-process
        currentSims = listOf(
            SimCardInfo(0, 99, "Carrier unavailable", null, true, true, hasPermission = true),
            SimCardInfo(1, 20, "Beta", null, true, true, hasPermission = true)
        )
        botManager.handleUpdate(
            TelegramUpdate(5L, null, 9001L, 50L, "owner", null, "c3", "newmsg_sim:${sess1.sessionId}:0:10", p1)
        )
        assertEquals(0, gateway.sends.size)
        assertTrue(resilientApi.edited.last().second.contains("subscription changed"))

        // Simulate SIM 1 becoming unavailable mid-process -> updates choices instead of sending via absent SIM
        currentSims = listOf(
            SimCardInfo(0, -1, "No SIM inserted", null, false, false, hasPermission = true),
            SimCardInfo(1, 20, "Beta", null, true, true, hasPermission = true)
        )
        botManager.handleUpdate(
            TelegramUpdate(6L, null, 9001L, 50L, "owner", null, "c4", "newmsg_sim:${sess1.sessionId}:0:99", p1)
        )
        assertEquals(0, gateway.sends.size)
        assertTrue(resilientApi.edited.last().second.contains("SIM 1 became unavailable"))

        // Cancel cleanly
        botManager.handleUpdate(
            TelegramUpdate(7L, null, 9001L, 50L, "owner", null, "c5", "newmsg_cancel:${sess1.sessionId}", p1)
        )
        assertNull(botManager.getActiveNewMessageSession(9001L, 50L))

        // B. 0 SIMs available -> fails clearly without sending or false success
        currentSims = emptyList()
        botManager.handleUpdate(TelegramUpdate(8L, null, 9001L, 50L, "owner", null, "c6", "newmsg:start", 10L))
        val sNoSim = botManager.getActiveNewMessageSession(9001L, 50L)!!.sessionId
        botManager.handleUpdate(TelegramUpdate(9L, 402L, 9001L, 50L, "owner", "+98 9210002222"))
        botManager.handleUpdate(TelegramUpdate(10L, null, 9001L, 50L, "owner", null, "c7", "newmsg_confirm:$sNoSim:yes", 10L))
        botManager.handleUpdate(TelegramUpdate(11L, 403L, 9001L, 50L, "owner", "Should fail"))
        assertEquals(0, gateway.sends.size)
        assertFalse(resilientApi.edited.last().second.contains("Message sent!"))
        assertTrue(resilientApi.edited.last().second.contains("No available SIM"))

        // C. SEND_SMS permission denied -> fails clearly without false success
        currentSims = listOf(SimCardInfo(0, 10, "Alpha", null, true, true, hasPermission = true))
        hasSendPerm = false
        botManager.handleUpdate(TelegramUpdate(12L, null, 9001L, 50L, "owner", null, "c8", "newmsg:start", 10L))
        val sPerm = botManager.getActiveNewMessageSession(9001L, 50L)!!.sessionId
        botManager.handleUpdate(TelegramUpdate(13L, 404L, 9001L, 50L, "owner", "+98 9210003333"))
        botManager.handleUpdate(TelegramUpdate(14L, null, 9001L, 50L, "owner", null, "c9", "newmsg_confirm:$sPerm:yes", 10L))
        botManager.handleUpdate(TelegramUpdate(15L, 405L, 9001L, 50L, "owner", "Perm check"))
        assertEquals(0, gateway.sends.size)
        assertTrue(resilientApi.edited.last().second.contains("SEND_SMS"))
        assertFalse(resilientApi.edited.last().second.contains("Message sent!"))

        // D. Gateway failure never shows false success
        hasSendPerm = true
        failGatewaySend = true
        botManager.handleUpdate(TelegramUpdate(16L, null, 9001L, 50L, "owner", null, "c10", "newmsg:start", 10L))
        val sFail = botManager.getActiveNewMessageSession(9001L, 50L)!!.sessionId
        botManager.handleUpdate(TelegramUpdate(17L, 406L, 9001L, 50L, "owner", "+98 9210004444"))
        botManager.handleUpdate(TelegramUpdate(18L, null, 9001L, 50L, "owner", null, "c11", "newmsg_confirm:$sFail:yes", 10L))
        botManager.handleUpdate(TelegramUpdate(19L, 407L, 9001L, 50L, "owner", "Radio off test"))
        assertFalse(resilientApi.sent.last().first.contains("Message sent!"))
        assertTrue(resilientApi.sent.last().first.contains("Carrier radio off"))

        // E. Telegram deleteMessage API failure never blocks SMS sending and still delivers final confirmation cleanly
        failGatewaySend = false
        failDeletes = true
        botManager.handleUpdate(TelegramUpdate(20L, null, 9001L, 50L, "owner", null, "c12", "newmsg:start", 10L))
        val pRes = resilientApi.nextId - 1
        val sDelFail = botManager.getActiveNewMessageSession(9001L, 50L)!!.sessionId
        botManager.handleUpdate(TelegramUpdate(21L, 408L, 9001L, 50L, "owner", "+98 9210005555"))
        val pAfterNum = resilientApi.nextId - 1
        botManager.handleUpdate(TelegramUpdate(22L, null, 9001L, 50L, "owner", null, "c13", "newmsg_confirm:$sDelFail:yes", pAfterNum))
        botManager.handleUpdate(TelegramUpdate(23L, 409L, 9001L, 50L, "owner", "Works despite delete failure"))

        assertEquals(1, gateway.sends.size)
        assertEquals("+989210005555", gateway.sends.first().first)
        // Because deleteMessage failed on the sending prompt, finalizeManagedMessage edited that prompt into final confirmation
        assertTrue(resilientApi.edited.last().second.contains("Message sent!"))
        assertTrue(resilientApi.edited.last().second.contains("To: +98 9210005555 (SIM 1)"))
        assertTrue(resilientApi.edited.last().second.contains("Works despite delete failure"))
        assertTrue(pRes > 0)
    }
}
