package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.EventLogger
import com.example.core.PreferencesManager
import com.example.core.SecurityManager
import com.example.data.model.LogCategory
import com.example.data.model.SmsMessageItem
import com.example.data.queue.DeliveryQueueManager
import com.example.telegram.InlineButton
import com.example.telegram.TelegramBotApi
import com.example.telegram.TelegramBotManager
import com.example.telegram.TelegramEntity
import com.example.telegram.TelegramEntityHelper
import com.example.telegram.TelegramMessage
import com.example.telegram.TelegramUpdate
import com.example.telegram.TelegramUser
import com.example.ui.screens.SystemHealthScreen
import com.example.ui.theme.VantaTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CleanupVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: VantaApplication
    private lateinit var context: Context

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        context.getSharedPreferences("vanta_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        app.preferencesManager.disconnectTelegram()
        EventLogger.clear()
    }

    @Test
    fun testTelegramPollingRestartsWhenTokenChanges() {
        val prefsMgr = PreferencesManager(context)
        val tokenA = "111111111:AAAbbbCCCdddEEEfffGGGhhhIIIjjj"
        val tokenB = "222222222:KKKlllMMMnnnOOOpppQQQrrrSSSttt"
        prefsMgr.saveBotToken(tokenA, "bot_a", 11L)

        val latchA = CountDownLatch(1)
        val latchB = CountDownLatch(1)
        val tokensPolled = CopyOnWriteArrayList<String>()

        val trackingApi = object : TelegramBotApi {
            override suspend fun getMe(token: String) = Result.success(TelegramUser(1L, true, "Bot", "bot"))
            override suspend fun getCustomEmojiStickers(token: String, customEmojiIds: List<String>) =
                Result.success(emptyMap<String, String>())
            override suspend fun sendMessage(
                token: String,
                chatId: Long,
                text: String,
                entities: List<TelegramEntity>,
                includeGithubButton: Boolean
            ) = Result.success(TelegramMessage(1L, 0L, text))
            override suspend fun sendMessageWithKeyboard(
                token: String,
                chatId: Long,
                text: String,
                entities: List<TelegramEntity>,
                keyboardRows: List<List<InlineButton>>
            ) = Result.success(TelegramMessage(1L, 0L, text))
            override suspend fun editMessageText(
                token: String,
                chatId: Long,
                messageId: Long,
                text: String,
                entities: List<TelegramEntity>,
                keyboardRows: List<List<InlineButton>>
            ) = Result.success(Unit)
            override suspend fun answerCallbackQuery(
                token: String,
                callbackQueryId: String,
                text: String?,
                showAlert: Boolean
            ) = Result.success(Unit)
            override suspend fun getUpdates(token: String, offset: Long?, limit: Int): Result<List<TelegramUpdate>> {
                tokensPolled.add(token)
                if (token == tokenA) latchA.countDown()
                if (token == tokenB) latchB.countDown()
                return Result.success(emptyList())
            }
            override fun sanitizeToken(raw: String) = raw.trim()
        }

        val botManager = TelegramBotManager(prefsMgr, trackingApi, autoStartPolling = true)
        try {
            assertTrue("Polling should start with tokenA", latchA.await(5, TimeUnit.SECONDS))

            // Switch token and trigger startPolling() while polling is already active
            prefsMgr.saveBotToken(tokenB, "bot_b", 22L)
            botManager.startPolling()

            assertTrue("Polling must restart and poll with tokenB", latchB.await(5, TimeUnit.SECONDS))
            assertTrue(tokensPolled.contains(tokenA))
            assertTrue(tokensPolled.contains(tokenB))
        } finally {
            botManager.stopPolling()
        }
    }

    @Test
    fun testDisconnectStopsServiceCallbackAndClearsCredentials() {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.saveBotToken("123456:ABCdefGHIjklMNOpqrs", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(999L)

        val botManager = TelegramBotManager(prefsMgr, FakeTelegramBotApi(), autoStartPolling = false)
        var serviceStopInvoked = false
        botManager.onServiceStopRequested = {
            serviceStopInvoked = true
        }

        botManager.disconnect()

        assertTrue("Disconnect must invoke service stop callback", serviceStopInvoked)
        assertFalse(botManager.isConnected.value)
        assertFalse(botManager.isPollingActive.value)
        assertFalse(prefsMgr.telegramConfig.value.hasToken)
        assertEquals(null, prefsMgr.telegramConfig.value.authorizedChatId)
    }

    @Test
    fun testDeliveryQueueDeduplicatesConcurrentInFlightSms() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.saveBotToken("123456:ABCdefGHIjklMNOpqrs", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)

        val firstSendStarted = CompletableDeferred<Unit>()
        val releaseFirstSend = CompletableDeferred<Unit>()
        val deliveredCounter = AtomicInteger(0)
        val completionLatch = CountDownLatch(1)

        val blockingApi = object : TelegramBotApi {
            override suspend fun getMe(token: String) = Result.success(TelegramUser(1L, true, "Bot", "vanta_bot"))
            override suspend fun getCustomEmojiStickers(token: String, customEmojiIds: List<String>) =
                Result.success(emptyMap<String, String>())
            override suspend fun sendMessage(
                token: String,
                chatId: Long,
                text: String,
                entities: List<TelegramEntity>,
                includeGithubButton: Boolean
            ) = Result.success(TelegramMessage(1L, 0L, text))
            override suspend fun sendMessageWithKeyboard(
                token: String,
                chatId: Long,
                text: String,
                entities: List<TelegramEntity>,
                keyboardRows: List<List<InlineButton>>
            ): Result<TelegramMessage> {
                firstSendStarted.complete(Unit)
                releaseFirstSend.await()
                return Result.success(TelegramMessage(101L, 0L, text))
            }
            override suspend fun editMessageText(
                token: String,
                chatId: Long,
                messageId: Long,
                text: String,
                entities: List<TelegramEntity>,
                keyboardRows: List<List<InlineButton>>
            ) = Result.success(Unit)
            override suspend fun answerCallbackQuery(
                token: String,
                callbackQueryId: String,
                text: String?,
                showAlert: Boolean
            ) = Result.success(Unit)
            override suspend fun getUpdates(token: String, offset: Long?, limit: Int) =
                Result.success(emptyList<TelegramUpdate>())
            override fun sanitizeToken(raw: String) = raw.trim()
        }

        val botManager = TelegramBotManager(prefsMgr, blockingApi, autoStartPolling = false)
        val queueManager = DeliveryQueueManager(
            telegramBotManager = botManager,
            preferencesManager = prefsMgr,
            onDeliverySuccess = {
                deliveredCounter.incrementAndGet()
                completionLatch.countDown()
            }
        )

        val sms = SmsMessageItem(
            id = "sms-1",
            sender = "+15550199",
            body = "Duplicate burst test",
            timestamp = 1715000000000L,
            simSlot = 0,
            subId = 1,
            isOtp = false
        )

        // First enqueue accepted; immediate duplicate before delivery completes must be rejected
        assertTrue(queueManager.enqueue(sms))
        assertFalse("Duplicate SMS during active delivery must be rejected", queueManager.enqueue(sms))

        withTimeout(5000L) {
            firstSendStarted.await()
        }
        // Still rejected while in-flight before completion
        assertFalse(queueManager.enqueue(sms))

        releaseFirstSend.complete(Unit)
        assertTrue("Delivery callback should complete", completionLatch.await(5, TimeUnit.SECONDS))
        assertEquals(1, deliveredCounter.get())
    }

    @Test
    fun testEventLoggerConcurrentWritesAreThreadSafeAndRedacted() {
        EventLogger.clear()
        val threads = (0 until 8).map { tIdx ->
            Thread {
                repeat(25) { i ->
                    EventLogger.log(
                        LogCategory.TELEGRAM,
                        "Thread $tIdx msg $i token 123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Secret"
                    )
                }
            }.apply { start() }
        }
        threads.forEach { it.join(5000L) }

        val entries = EventLogger.logs.value
        assertEquals(150, entries.size)
        assertTrue(entries.all { !it.message.contains("ABCdefGhIJKlmNoPQRstuVWXyz") })
        assertTrue(entries.all { it.message.contains("[REDACTED_BOT_TOKEN]") })
    }

    @Test
    fun testNewMessageSentConfirmationTimestampAndBlankDestinationLabelFallback() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(2026, Calendar.NOVEMBER, 16, 14, 47, 5)
                set(Calendar.MILLISECOND, 0)
            }
            val msg = TelegramEntityHelper.formatNewMessageSentConfirmation(
                recipient = "+14155550100",
                simSlot = 0,
                sentTimestampMillis = cal.timeInMillis,
                messageText = "Hello"
            )
            assertTrue(msg.text.contains("Time: 02:47 PM"))
            assertFalse(msg.text.contains("2026-11-16"))
        } finally {
            TimeZone.setDefault(originalTz)
        }

        val prefsMgr = PreferencesManager(context)
        prefsMgr.addForwardDestination(-100123456L, "   ")
        val saved = prefsMgr.telegramConfig.value.forwardDestinations.first()
        assertEquals("Chat -100123456", saved.label)
    }

    @Test
    fun testSystemHealthDisplaysAccuratePairingAndEncryptionStatus() {
        // Case 1: No bot token configured
        app.preferencesManager.disconnectTelegram()
        composeRule.setContent {
            VantaTheme {
                SystemHealthScreen(onNavigateBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Not Configured").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Authorized & Paired").assertDoesNotExist()
        if (!SecurityManager.isUsingHardwareKeystore()) {
            composeRule.onNodeWithText("AES-256-GCM Software Fallback").performScrollTo().assertIsDisplayed()
        }

        // Case 2: Token saved, pairing pending
        app.preferencesManager.saveBotToken("123456789:ABCdefGhIJKlmNoPQRstuVWXyz", "vanta_test_bot", 1L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pairing Pending (Offline)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Authorized & Paired").assertDoesNotExist()
    }
}
