package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.PreferencesManager
import com.example.data.model.SimCardInfo
import com.example.telegram.BotMenuPanel
import com.example.telegram.InlineButton
import com.example.telegram.TelegramBotApi
import com.example.telegram.TelegramBotManager
import com.example.telegram.TelegramEntity
import com.example.telegram.TelegramEntityHelper
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
class GithubStarSupportTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vanta_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testGithubStarButtonInMenusIsSubtleUrlAndStaysAvailableWhenRemindersDisabled() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.saveBotToken("123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Test", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)
        prefsMgr.setGithubReminderDisabled(userId = 50L, disabled = true, nowMillis = 1_000_000L)

        val sims = listOf(
            SimCardInfo(0, 10, "Alpha", "+10001", true, true, hasPermission = true),
            SimCardInfo(1, 20, "Beta", "+10002", true, true, hasPermission = true)
        )

        val overviewKb = TelegramMenuHelper.buildPanelKeyboard(
            panel = BotMenuPanel.OVERVIEW,
            isForwardingEnabled = true,
            sims = sims,
            simControl = prefsMgr.preferences.value.simControl
        )
        val helpKb = TelegramMenuHelper.buildPanelKeyboard(
            panel = BotMenuPanel.HELP,
            isForwardingEnabled = true,
            sims = sims,
            simControl = prefsMgr.preferences.value.simControl
        )

        for (kb in listOf(overviewKb, helpKb)) {
            val flat = kb.flatten()
            val githubButtons = flat.filter { it.url == "https://github.com/Sefeshki/VANTA" }
            assertEquals(1, githubButtons.size)
            val btn = githubButtons.first()
            assertEquals("★ Give VANTA a Star", btn.text)
            assertNull(btn.callbackData)
            assertNull(btn.style)
            assertNull(btn.customEmojiId)
            // Secondary placement at bottom row without disturbing primary buttons
            assertEquals(listOf(btn), kb.last())
        }

        // Secondary panels don't overcrowd with redundant GitHub rows
        val controlsKb = TelegramMenuHelper.buildPanelKeyboard(
            panel = BotMenuPanel.CONTROLS,
            isForwardingEnabled = true,
            sims = sims,
            simControl = prefsMgr.preferences.value.simControl
        ).flatten()
        assertFalse(controlsKb.any { it.url == TelegramEntityHelper.GITHUB_REPO_URL })

        // Verify live /menu and /help still deliver the GitHub star button even with reminders disabled
        val fakeApi = RecordingTelegramApi()
        val bot = TelegramBotManager(prefsMgr, fakeApi, autoStartPolling = false).apply {
            simInfoProvider = { sims }
            clockProvider = { 10_000_000_000L }
        }
        bot.handleUpdate(TelegramUpdate(1L, 100L, 9001L, 50L, "owner", "/menu"))
        assertEquals(1, fakeApi.sent.size)
        assertTrue(fakeApi.sent.last().keyboard.flatten().any {
            it.text == "★ Give VANTA a Star" && it.url == "https://github.com/Sefeshki/VANTA"
        })

        bot.handleUpdate(TelegramUpdate(2L, 101L, 9001L, 50L, "owner", "/help"))
        assertEquals(2, fakeApi.sent.size)
        assertTrue(fakeApi.sent.last().keyboard.flatten().any {
            it.text == "★ Give VANTA a Star" && it.url == "https://github.com/Sefeshki/VANTA"
        })
    }

    @Test
    fun testWeeklyReminderCadenceNonSpamDismissDisableAndPersistence() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.saveBotToken("123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Test", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)

        var now = 1_700_000_000_000L
        val weekMs = PreferencesManager.WEEKLY_GITHUB_REMINDER_INTERVAL_MS
        val fakeApi = RecordingTelegramApi()
        val bot = TelegramBotManager(prefsMgr, fakeApi, autoStartPolling = false).apply {
            clockProvider = { now }
        }

        // 1. First interaction: NEVER sends reminder immediately
        bot.handleUpdate(TelegramUpdate(1L, 200L, 9001L, 50L, "owner", "/menu"))
        assertEquals(1, fakeApi.sent.size)
        assertEquals(0, fakeApi.reminderMessages().size)

        // 2. Rapid follow-up interactions within 7 days -> NEVER sends reminder
        now += 60_000L
        bot.handleUpdate(TelegramUpdate(2L, 201L, 9001L, 50L, "owner", "/help"))
        now += weekMs - 120_000L
        bot.handleUpdate(TelegramUpdate(3L, null, 9001L, 50L, "owner", null, "cb_menu", "menu:overview", 100L))
        assertEquals(0, fakeApi.reminderMessages().size)

        // 3. After >= 7 days -> sends ONE gentle reminder with required text + buttons
        now += 120_000L
        bot.handleUpdate(TelegramUpdate(4L, 202L, 9001L, 50L, "owner", "/menu"))
        val reminders1 = fakeApi.reminderMessages()
        assertEquals(1, reminders1.size)
        val rem1 = reminders1.first()
        assertEquals(
            "Enjoying VANTA? If you find the project useful, consider giving it a star on GitHub. Every star means a lot and helps the project grow. Thank you for supporting my work.",
            rem1.text
        )
        val buttons = rem1.keyboard.flatten()
        assertEquals(3, buttons.size)
        assertEquals("★ Star VANTA on GitHub", buttons[0].text)
        assertEquals("https://github.com/Sefeshki/VANTA", buttons[0].url)
        assertEquals("Dismiss", buttons[1].text)
        assertEquals("gh_rem:dismiss", buttons[1].callbackData)
        assertEquals("Don't remind me again", buttons[2].text)
        assertEquals("gh_rem:disable", buttons[2].callbackData)

        // 4. Subsequent interaction same day -> NO duplicate reminder
        now += 10_000L
        bot.handleUpdate(TelegramUpdate(5L, 203L, 9001L, 50L, "owner", "/help"))
        assertEquals(1, fakeApi.reminderMessages().size)

        // 5. User clicks Dismiss -> cleans up message, keeps next 7-day cycle enabled
        bot.handleUpdate(TelegramUpdate(6L, null, 9001L, 50L, "owner", null, "cb_dismiss", "gh_rem:dismiss", rem1.id))
        assertTrue(fakeApi.deleted.contains(9001L to rem1.id))

        // Day 6 after dismiss -> still quiet
        now += weekMs - 50_000L
        bot.handleUpdate(TelegramUpdate(7L, 204L, 9001L, 50L, "owner", "/menu"))
        assertEquals(1, fakeApi.reminderMessages().size)

        // Day 7+ after reminder -> eligible again and sends second weekly reminder
        now += 60_000L
        bot.handleUpdate(TelegramUpdate(8L, 205L, 9001L, 50L, "owner", "/menu"))
        val reminders2 = fakeApi.reminderMessages()
        assertEquals(2, reminders2.size)
        val rem2 = reminders2.last()

        // 6. User clicks Don't remind me again (and test delete failure graceful edit fallback)
        fakeApi.failDelete = true
        bot.handleUpdate(TelegramUpdate(9L, null, 9001L, 50L, "owner", null, "cb_disable", "gh_rem:disable", rem2.id))
        assertEquals(rem2.id, fakeApi.edited.last().first)
        assertTrue(fakeApi.edited.last().second.contains("disabled", ignoreCase = true))

        // Reload PreferencesManager + BotManager to verify SharedPreferences persistence
        val reloadedPrefs = PreferencesManager(context)
        assertTrue(reloadedPrefs.getGithubReminderState(50L)?.disabled == true)
        val reloadedBot = TelegramBotManager(reloadedPrefs, fakeApi, autoStartPolling = false).apply {
            clockProvider = { now }
        }
        now += weekMs * 5
        reloadedBot.handleUpdate(TelegramUpdate(10L, 206L, 9001L, 50L, "owner", "/menu"))
        assertEquals(2, fakeApi.reminderMessages().size)
        // But the Overview menu itself still retains the subtle ★ Give VANTA a Star button
        assertTrue(fakeApi.sent.last().keyboard.flatten().any {
            it.text == "★ Give VANTA a Star" && it.url == "https://github.com/Sefeshki/VANTA"
        })
    }

    @Test
    fun testPerUserIsolationInGroupOrMultiUserChat() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.saveBotToken("123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Test", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)

        var now = 1_700_000_000_000L
        val weekMs = PreferencesManager.WEEKLY_GITHUB_REMINDER_INTERVAL_MS
        val fakeApi = RecordingTelegramApi()
        val bot = TelegramBotManager(prefsMgr, fakeApi, autoStartPolling = false).apply {
            clockProvider = { now }
        }

        // User 100 and User 200 both interact at t=0
        bot.handleUpdate(TelegramUpdate(1L, 300L, 9001L, 100L, "userA", "/menu"))
        bot.handleUpdate(TelegramUpdate(2L, 301L, 9001L, 200L, "userB", "/menu"))
        assertEquals(0, fakeApi.reminderMessages().size)

        // User 100 disables reminders; User 200 should still get their own after 7 days
        bot.handleUpdate(TelegramUpdate(3L, null, 9001L, 100L, "userA", null, "cb_dis_a", "gh_rem:disable", 99L))
        now += weekMs + 1000L

        bot.handleUpdate(TelegramUpdate(4L, 302L, 9001L, 100L, "userA", "/menu"))
        assertEquals(0, fakeApi.reminderMessages().size)

        bot.handleUpdate(TelegramUpdate(5L, 303L, 9001L, 200L, "userB", "/menu"))
        assertEquals(1, fakeApi.reminderMessages().size)
    }

    @Test
    fun testNeverInterruptsActiveSmsWorkflowsAndHandlesTelegramApiErrorsGracefully() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.saveBotToken("123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Test", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)

        var now = 1_700_000_000_000L
        val weekMs = PreferencesManager.WEEKLY_GITHUB_REMINDER_INTERVAL_MS
        val sims = listOf(
            SimCardInfo(0, 10, "Alpha", "+10001", true, true, hasPermission = true)
        )
        val fakeApi = RecordingTelegramApi()
        val fakeGateway = object : SmsGateway {
            val sentSms = mutableListOf<String>()
            override suspend fun sendSms(destination: String, messageText: String, slotIndex: Int): Result<SmsSendResult> {
                sentSms.add("$destination:$messageText")
                return Result.success(SmsSendResult(destination, slotIndex, 10, "Alpha", 1))
            }
            override fun canReplyToAddress(address: String?) = true
        }
        val bot = TelegramBotManager(prefsMgr, fakeApi, autoStartPolling = false).apply {
            clockProvider = { now }
            simInfoProvider = { sims }
            smsGateway = fakeGateway
            hasSendSmsPermissionProvider = { true }
        }

        // First seen at t=0
        bot.handleUpdate(TelegramUpdate(1L, 400L, 9001L, 50L, "owner", "/menu"))
        // Advance past 7 days so user 50 IS due for a reminder
        now += weekMs + 10_000L

        // A. Incoming SMS forwarding must NEVER emit a reminder
        val smsMsg = bot.forwardSms("+14155550100", "Hello incoming", "SIM 1 · Alpha", simSlot = 0, subId = 10).getOrThrow()
        assertEquals(0, fakeApi.reminderMessages().size)

        // B. Active Reply flow must NEVER emit a reminder
        val replyCb = fakeApi.sent.last().keyboard.flatten().first { it.text == "Reply" }.callbackData!!
        bot.handleUpdate(TelegramUpdate(2L, null, 9001L, 50L, "owner", null, "cb_rep", replyCb, smsMsg.messageId))
        bot.handleUpdate(TelegramUpdate(3L, 401L, 9001L, 50L, "owner", "Quick reply"))
        assertEquals(0, fakeApi.reminderMessages().size)

        // C. Active New Message flow must NEVER emit a reminder at any step
        bot.handleUpdate(TelegramUpdate(4L, null, 9001L, 50L, "owner", null, "cb_new", "newmsg:start", 10L))
        bot.handleUpdate(TelegramUpdate(5L, 402L, 9001L, 50L, "owner", "+14155550199"))
        val yesCb = fakeApi.edited.last().third.flatten().first { it.text == "Yes" }.callbackData!!
        bot.handleUpdate(TelegramUpdate(6L, null, 9001L, 50L, "owner", null, "cb_yes", yesCb, fakeApi.lastEditedId()))
        bot.handleUpdate(TelegramUpdate(7L, 403L, 9001L, 50L, "owner", "New outgoing text"))
        assertEquals(2, fakeGateway.sentSms.size)
        assertEquals(0, fakeApi.reminderMessages().size)

        // D. Telegram API failure when sending reminder must NOT throw or break bot operation
        fakeApi.failNextReminderSend = true
        bot.handleUpdate(TelegramUpdate(8L, 404L, 9001L, 50L, "owner", "/menu"))
        // Menu itself succeeded
        assertTrue(fakeApi.sent.last().text.contains("VANTA"))
        // Bot remains completely responsive afterwards
        bot.handleUpdate(TelegramUpdate(9L, 405L, 9001L, 50L, "owner", "/help"))
        assertTrue(fakeApi.sent.last().text.contains("VANTA SMS Bridge"))
    }

    private data class RecordedMsg(
        val id: Long,
        val chatId: Long,
        val text: String,
        val keyboard: List<List<InlineButton>>
    )

    private class RecordingTelegramApi : TelegramBotApi {
        var nextId = 100L
        var failDelete = false
        var failNextReminderSend = false
        val sent = mutableListOf<RecordedMsg>()
        val edited = mutableListOf<Triple<Long, String, List<List<InlineButton>>>>()
        val deleted = mutableListOf<Pair<Long, Long>>()

        fun reminderMessages(): List<RecordedMsg> =
            sent.filter { it.text.startsWith("Enjoying VANTA?") }

        fun lastEditedId(): Long = edited.last().first

        override suspend fun getMe(token: String) =
            Result.success(TelegramUser(1L, true, "VANTA", "vanta_bot"))

        override suspend fun getCustomEmojiStickers(token: String, customEmojiIds: List<String>) =
            Result.success(emptyMap<String, String>())

        override suspend fun sendMessage(
            token: String,
            chatId: Long,
            text: String,
            entities: List<TelegramEntity>,
            includeGithubButton: Boolean
        ): Result<TelegramMessage> {
            val id = nextId++
            sent.add(RecordedMsg(id, chatId, text, emptyList()))
            return Result.success(TelegramMessage(id, 1000L, text))
        }

        override suspend fun sendMessageWithKeyboard(
            token: String,
            chatId: Long,
            text: String,
            entities: List<TelegramEntity>,
            keyboardRows: List<List<InlineButton>>
        ): Result<TelegramMessage> {
            if (failNextReminderSend && text.startsWith("Enjoying VANTA?")) {
                failNextReminderSend = false
                return Result.failure(RuntimeException("Simulated Telegram send failure"))
            }
            val id = nextId++
            sent.add(RecordedMsg(id, chatId, text, keyboardRows))
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
            edited.add(Triple(messageId, text, keyboardRows))
            return Result.success(Unit)
        }

        override suspend fun deleteMessage(token: String, chatId: Long, messageId: Long): Result<Unit> {
            if (failDelete) return Result.failure(Exception("Cannot delete"))
            deleted.add(chatId to messageId)
            return Result.success(Unit)
        }

        override suspend fun answerCallbackQuery(
            token: String,
            callbackQueryId: String,
            text: String?,
            showAlert: Boolean
        ): Result<Unit> = Result.success(Unit)

        override suspend fun getUpdates(token: String, offset: Long?, limit: Int) =
            Result.success(emptyList<TelegramUpdate>())

        override fun sanitizeToken(raw: String): String = raw.trim()
    }
}
