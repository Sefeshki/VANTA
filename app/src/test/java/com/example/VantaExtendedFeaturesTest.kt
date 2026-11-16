package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.core.PreferencesManager
import com.example.data.model.SimCardInfo
import com.example.data.model.SimControlConfig
import com.example.data.model.SimFeatureSettings
import com.example.service.ServiceAlertManager
import com.example.telegram.TelegramBotApi
import com.example.telegram.TelegramBotManager
import com.example.telegram.TelegramEntity
import com.example.telegram.TelegramEntityHelper
import com.example.telegram.TelegramMessage
import com.example.telegram.TelegramUpdate
import com.example.telegram.TelegramUser
import com.example.telephony.SimManager
import com.example.ui.screens.StatusPanel
import com.example.ui.theme.VantaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSubscriptionManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VantaExtendedFeaturesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vanta_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testSimIntelligenceCarrierUnavailableAndSingleSimMapping() {
        val app = shadowOf(ApplicationProvider.getApplicationContext<Application>())
        app.grantPermissions(Manifest.permission.READ_PHONE_STATE)

        val subMgr = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
        // Subscription with blank carrier/display name must fall back cleanly to "Carrier unavailable"
        val subBlank = ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder()
            .setId(42)
            .setSimSlotIndex(0)
            .setCarrierName("")
            .setDisplayName("")
            .buildSubscriptionInfo()
        shadowOf(subMgr).setActiveSubscriptionInfos(subBlank)

        val prefsMgr = PreferencesManager(context)
        val simManager = SimManager(context, prefsMgr)
        simManager.refreshSims()

        val sims = simManager.detectedSims.value
        val sim1 = sims.firstOrNull { it.slotIndex == 0 }
        assertNotNull(sim1)
        assertEquals(42, sim1?.subId)
        assertEquals(SimManager.CARRIER_UNAVAILABLE, sim1?.carrierName)
        assertEquals("SIM 1", simManager.formatSimDisplayLabel(0, 42))
        assertEquals(0, simManager.resolveSlotFromSubId(42))
    }

    @Test
    fun testDashboardSingleSimDeviceShowsAbsentNotPermissionNeeded() {
        val singleSimList = listOf(
            SimCardInfo(
                slotIndex = 0,
                subId = 9,
                carrierName = "Vodafone",
                phoneNumber = null,
                isAvailable = true,
                isSelected = true,
                hasPermission = true
            )
        )

        composeRule.setContent {
            VantaTheme {
                StatusPanel(
                    isForwardingActive = true,
                    isServiceRunning = true,
                    isInternetAvailable = true,
                    isTelegramConnected = true,
                    pendingCount = 0,
                    totalDelivered = 3,
                    lastDelivery = 0L,
                    simControl = SimControlConfig(
                        sim1 = SimFeatureSettings(masterEnabled = true),
                        sim2 = SimFeatureSettings(masterEnabled = true)
                    ),
                    sims = singleSimList
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("SIM 2 · Unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Single-SIM device").assertIsDisplayed()
        composeRule.onNodeWithTag("live_status_sim_switch_1").assertIsNotEnabled().assertIsOff()
    }

    @Test
    fun testTelegramForwardSmsInlineButtonSavedDestinationsManualChatIdAndDedup() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        val secretToken = "123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Secret"
        prefsMgr.saveBotToken(secretToken, "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)
        prefsMgr.addForwardDestination(-100777888999L, "Team Ops")

        val fakeApi = FakeTelegramBotApi()
        val botManager = TelegramBotManager(prefsMgr, fakeApi, autoStartPolling = false).apply {
            simInfoProvider = {
                listOf(SimCardInfo(0, 14, "T-Mobile", "+10001", true, true, hasPermission = true))
            }
        }

        val timestamp = 1715000000000L
        val fwdRes = botManager.forwardSms(
            sender = "+14155550123",
            body = "Server alert: CPU nominal",
            simLabel = "SIM 1 · T-Mobile",
            contactName = "Alice",
            receivedTimestamp = timestamp,
            isOtp = false,
            simSlot = 0,
            subId = 14
        )
        assertTrue(fwdRes.isSuccess)

        // Verify both Reply and Forward inline buttons exist side-by-side
        val buttons = fakeApi.sentMessages.last().keyboardRows.flatten()
        assertEquals(2, buttons.size)
        assertEquals("Reply", buttons[0].text)
        assertEquals("Forward", buttons[1].text)
        val forwardCallback = buttons[1].callbackData!!
        assertTrue(forwardCallback.startsWith("fwd:"))

        // 1. Tap Forward -> prompts with saved destination "Team Ops" + Enter Chat ID + Cancel
        botManager.handleUpdate(TelegramUpdate(1L, null, 9001L, 50L, "owner", null, "cb_f1", forwardCallback, 100L))
        val session = botManager.getActiveForwardSession(9001L, 50L)
        assertNotNull(session)
        val pickerButtons = fakeApi.sentMessages.last().keyboardRows.flatten()
        val teamOpsBtn = pickerButtons.first { it.text == "Team Ops" }

        // 2. Select Team Ops -> confirmation screen
        botManager.handleUpdate(TelegramUpdate(2L, null, 9001L, 50L, "owner", null, "cb_sel1", teamOpsBtn.callbackData, 101L))
        val confirmBtn = fakeApi.editedMessages.last().keyboardRows.flatten().first { it.text.contains("Confirm Forward") }

        // 3. Confirm Forward -> sends "Forwarded SMS via VANTA" to -100777888999L
        botManager.handleUpdate(TelegramUpdate(3L, null, 9001L, 50L, "owner", null, "cb_go1", confirmBtn.callbackData, 101L))
        val deliveredMsg = fakeApi.sentMessages.last()
        assertEquals(-100777888999L, deliveredMsg.chatId)
        assertTrue(deliveredMsg.text.contains("Forwarded SMS via VANTA"))
        assertTrue(deliveredMsg.text.contains("From: Alice (+14155550123)"))
        assertTrue(deliveredMsg.text.contains("SIM: SIM 1 · T-Mobile"))
        assertTrue(deliveredMsg.text.contains("Time: ${TelegramEntityHelper.formatTimestamp(timestamp)}"))
        assertTrue(deliveredMsg.text.contains("Server alert: CPU nominal"))
        assertNull("Session clears after successful forward", botManager.getActiveForwardSession(9001L, 50L))

        // 4. Test custom Chat ID input + invalid rejection + cancel
        botManager.handleUpdate(TelegramUpdate(4L, null, 9001L, 50L, "owner", null, "cb_f2", forwardCallback, 100L))
        val manualBtn = fakeApi.sentMessages.last().keyboardRows.flatten().first { it.text == "Enter Chat ID" }
        botManager.handleUpdate(TelegramUpdate(5L, null, 9001L, 50L, "owner", null, "cb_man", manualBtn.callbackData, 103L))

        // Invalid text should warn instead of dispatching
        val countBeforeInvalid = fakeApi.sentMessages.size
        botManager.handleUpdate(TelegramUpdate(6L, 200L, 9001L, 50L, "owner", "not-a-chat-id"))
        assertEquals(countBeforeInvalid + 1, fakeApi.sentMessages.size)
        assertTrue(fakeApi.sentMessages.last().text.contains("Invalid Chat ID"))

        // Valid numeric chat ID transitions to confirmation
        botManager.handleUpdate(TelegramUpdate(7L, 201L, 9001L, 50L, "owner", "555666777"))
        val confirmManualBtn = fakeApi.editedMessages.last().keyboardRows.flatten().first { it.text.contains("Confirm Forward") }
        botManager.handleUpdate(TelegramUpdate(8L, null, 9001L, 50L, "owner", null, "cb_go2", confirmManualBtn.callbackData, 103L))
        assertEquals(555666777L, fakeApi.sentMessages.last().chatId)

        // 5. Re-forwarding to Team Ops must prevent duplicate send once already forwarded
        botManager.handleUpdate(TelegramUpdate(9L, null, 9001L, 50L, "owner", null, "cb_f3", forwardCallback, 100L))
        val teamOpsBtn2 = fakeApi.sentMessages.last().keyboardRows.flatten().first { it.text == "Team Ops" }
        botManager.handleUpdate(TelegramUpdate(10L, null, 9001L, 50L, "owner", null, "cb_sel2", teamOpsBtn2.callbackData, 106L))
        assertTrue(fakeApi.editedMessages.last().text.contains("Already forwarded to this destination"))
        val confirmDupBtn = fakeApi.editedMessages.last().keyboardRows.flatten().first { it.text.contains("Confirm Forward") }
        val sentBeforeDupAttempt = fakeApi.sentMessages.size
        botManager.handleUpdate(TelegramUpdate(11L, null, 9001L, 50L, "owner", null, "cb_go_dup", confirmDupBtn.callbackData, 106L))
        assertEquals("Duplicate forward to same destination must be blocked", sentBeforeDupAttempt, fakeApi.sentMessages.size)
    }

    @Test
    fun testTelegramForwardApiFailureReportsErrorWithoutTokenLeak() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        val secretToken = "999888777:ABCdefGhIJKlmNoPQRstuVWXyz_Token"
        prefsMgr.saveBotToken(secretToken, "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)

        val failingForwardApi = object : TelegramBotApi {
            var nextId = 10L
            val sends = mutableListOf<RecordedSend>()
            val edits = mutableListOf<RecordedEdit>()
            override suspend fun getMe(token: String) = Result.success(TelegramUser(1L, true, "Bot", "vanta_bot"))
            override suspend fun getCustomEmojiStickers(token: String, customEmojiIds: List<String>) = Result.success(emptyMap<String, String>())
            override suspend fun sendMessage(
                token: String,
                chatId: Long,
                text: String,
                entities: List<TelegramEntity>,
                includeGithubButton: Boolean
            ): Result<TelegramMessage> {
                if (chatId == 777777L) {
                    return Result.failure(Exception("Forbidden: bot was blocked by the user"))
                }
                val id = nextId++
                sends.add(RecordedSend(chatId, text, entities, emptyList()))
                return Result.success(TelegramMessage(id, 1000L, text))
            }
            override suspend fun sendMessageWithKeyboard(
                token: String,
                chatId: Long,
                text: String,
                entities: List<TelegramEntity>,
                keyboardRows: List<List<com.example.telegram.InlineButton>>
            ): Result<TelegramMessage> {
                val id = nextId++
                sends.add(RecordedSend(chatId, text, entities, keyboardRows))
                return Result.success(TelegramMessage(id, 1000L, text))
            }
            override suspend fun editMessageText(
                token: String,
                chatId: Long,
                messageId: Long,
                text: String,
                entities: List<TelegramEntity>,
                keyboardRows: List<List<com.example.telegram.InlineButton>>
            ): Result<Unit> {
                edits.add(RecordedEdit(chatId, messageId, text, entities, keyboardRows))
                return Result.success(Unit)
            }
            override suspend fun answerCallbackQuery(token: String, callbackQueryId: String, text: String?, showAlert: Boolean) = Result.success(Unit)
            override suspend fun getUpdates(token: String, offset: Long?, limit: Int) = Result.success(emptyList<TelegramUpdate>())
            override fun sanitizeToken(raw: String) = raw.trim()
        }

        val botManager = TelegramBotManager(prefsMgr, failingForwardApi, autoStartPolling = false)
        botManager.forwardSms("+15551112222", "Hello", "SIM 1", simSlot = 0, subId = 1)
        val fwdCb = failingForwardApi.sends.last().keyboardRows.flatten().first { it.text == "Forward" }.callbackData!!

        botManager.handleUpdate(TelegramUpdate(1L, null, 9001L, 10L, "u", null, "c1", fwdCb, 10L))
        val manualBtn = failingForwardApi.sends.last().keyboardRows.flatten().first { it.text == "Enter Chat ID" }
        botManager.handleUpdate(TelegramUpdate(2L, null, 9001L, 10L, "u", null, "c2", manualBtn.callbackData, 11L))
        botManager.handleUpdate(TelegramUpdate(3L, 50L, 9001L, 10L, "u", "777777"))
        val goBtn = failingForwardApi.edits.last().keyboardRows.flatten().first { it.text.contains("Confirm Forward") }
        botManager.handleUpdate(TelegramUpdate(4L, null, 9001L, 10L, "u", null, "c3", goBtn.callbackData, 11L))

        val errorReport = failingForwardApi.edits.last().text
        assertTrue(errorReport.contains("✕ Failed to forward SMS"))
        assertTrue(errorReport.contains("bot was blocked by the user"))
        assertFalse("Error message must never leak bot token", errorReport.contains(secretToken))
    }

    @Test
    fun testServiceAlertManagerDeduplicationRecoveryAndPermissions() {
        val app = shadowOf(ApplicationProvider.getApplicationContext<Application>())
        app.grantPermissions(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        val prefsMgr = PreferencesManager(context)
        prefsMgr.setForwardingEnabled(true)
        prefsMgr.saveBotToken("123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Token", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)

        val alertManager = ServiceAlertManager(context, prefsMgr)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(nm)

        // 1. Service unexpected stop posts once, repeated call is deduplicated, recovery clears it
        alertManager.onServiceUnexpectedlyStopped()
        assertEquals(1, shadowNm.allNotifications.size)
        val stopTitle = shadowNm.allNotifications.first().extras.getString("android.title")
        assertEquals("VANTA Service Stopped", stopTitle)
        assertFalse(
            "Notification must not leak bot token",
            shadowNm.allNotifications.first().extras.getString("android.text")?.contains("ABCdef") == true
        )

        // Repeated call should NOT create a second duplicate alert
        alertManager.onServiceUnexpectedlyStopped()
        assertEquals(1, shadowNm.allNotifications.size)

        // Recovery updates to recovered notification, then a second onServiceStarted does NOT spam
        alertManager.onServiceStarted()
        val recoveredTitle = shadowNm.getNotification(ServiceAlertManager.NOTIF_SERVICE_ID)?.extras?.getString("android.title")
        assertEquals("VANTA Service Recovered", recoveredTitle)

        // 2. SIM disconnect -> alert, then restore -> recovery alert
        val initialSims = listOf(
            SimCardInfo(0, 10, "Alpha", null, true, true, hasPermission = true),
            SimCardInfo(1, 20, "Beta", null, true, true, hasPermission = true)
        )
        alertManager.evaluateSims(initialSims)
        assertNull(shadowNm.getNotification(ServiceAlertManager.NOTIF_SIM_BASE_ID + 1))

        val sim2Removed = listOf(
            SimCardInfo(0, 10, "Alpha", null, true, true, hasPermission = true),
            SimCardInfo(1, -1, "No SIM inserted", null, false, false, hasPermission = true)
        )
        alertManager.evaluateSims(sim2Removed)
        assertEquals(
            "SIM Disconnected",
            shadowNm.getNotification(ServiceAlertManager.NOTIF_SIM_BASE_ID + 1)?.extras?.getString("android.title")
        )

        alertManager.evaluateSims(initialSims)
        assertEquals(
            "SIM Connection Restored",
            shadowNm.getNotification(ServiceAlertManager.NOTIF_SIM_BASE_ID + 1)?.extras?.getString("android.title")
        )

        // 3. Permission revocation -> alert, re-grant -> recovery alert
        alertManager.evaluatePermissions()
        assertNull(shadowNm.getNotification(ServiceAlertManager.NOTIF_PERMISSION_ID))

        app.denyPermissions(Manifest.permission.RECEIVE_SMS)
        alertManager.evaluatePermissions()
        assertEquals(
            "Required Permission Revoked",
            shadowNm.getNotification(ServiceAlertManager.NOTIF_PERMISSION_ID)?.extras?.getString("android.title")
        )

        app.grantPermissions(Manifest.permission.RECEIVE_SMS)
        alertManager.evaluatePermissions()
        assertEquals(
            "Permissions Restored",
            shadowNm.getNotification(ServiceAlertManager.NOTIF_PERMISSION_ID)?.extras?.getString("android.title")
        )
    }
}
