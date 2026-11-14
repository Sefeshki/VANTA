package com.example

import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SubscriptionInfo
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.core.PreferencesManager
import com.example.data.model.CallLogEntryItem
import com.example.data.model.CallType
import com.example.data.model.SimCardInfo
import com.example.data.model.SimControlConfig
import com.example.data.model.SimFeature
import com.example.data.model.SimFeatureSettings
import com.example.data.model.SimSelectionMode
import com.example.data.model.SmsMessageItem
import com.example.data.queue.DeliveryQueueManager
import com.example.sms.SmsProcessor
import com.example.telegram.InlineButton
import com.example.telegram.TelegramBotApi
import com.example.telegram.TelegramBotManager
import com.example.telegram.TelegramEntity
import com.example.telegram.TelegramEntityHelper
import com.example.telegram.TelegramMessage
import com.example.telegram.TelegramUpdate
import com.example.telegram.TelegramUser
import com.example.telephony.AndroidSmsGateway
import com.example.telephony.SimManager
import com.example.telephony.SmsGateway
import com.example.telephony.SmsSendResult
import com.example.ui.screens.OtpProtectionControlCard
import com.example.ui.screens.StatusPanel
import com.example.ui.screens.SystemHealthScreen
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

data class RecordedSend(
    val chatId: Long,
    val text: String,
    val entities: List<TelegramEntity>,
    val keyboardRows: List<List<InlineButton>>
)

data class RecordedEdit(
    val chatId: Long,
    val messageId: Long,
    val text: String,
    val keyboardRows: List<List<InlineButton>>
)

data class RecordedSmsDispatch(
    val destination: String,
    val messageText: String,
    val slotIndex: Int
)

class FakeTelegramBotApi : TelegramBotApi {
    var nextMsgId = 100L
    val sentMessages = mutableListOf<RecordedSend>()
    val editedMessages = mutableListOf<RecordedEdit>()
    val answeredCallbacks = mutableListOf<Pair<String, String?>>()

    override suspend fun getMe(token: String): Result<TelegramUser> =
        Result.success(TelegramUser(1L, true, "VantaTestBot", "vanta_test_bot"))

    override suspend fun getCustomEmojiStickers(token: String, customEmojiIds: List<String>): Result<Map<String, String>> =
        Result.success(emptyMap())

    override suspend fun sendMessage(
        token: String,
        chatId: Long,
        text: String,
        entities: List<TelegramEntity>,
        includeGithubButton: Boolean
    ): Result<TelegramMessage> {
        val id = nextMsgId++
        sentMessages.add(RecordedSend(chatId, text, entities, emptyList()))
        return Result.success(TelegramMessage(id, System.currentTimeMillis() / 1000, text))
    }

    override suspend fun sendMessageWithKeyboard(
        token: String,
        chatId: Long,
        text: String,
        entities: List<TelegramEntity>,
        keyboardRows: List<List<InlineButton>>
    ): Result<TelegramMessage> {
        val id = nextMsgId++
        sentMessages.add(RecordedSend(chatId, text, entities, keyboardRows))
        return Result.success(TelegramMessage(id, System.currentTimeMillis() / 1000, text))
    }

    override suspend fun editMessageText(
        token: String,
        chatId: Long,
        messageId: Long,
        text: String,
        entities: List<TelegramEntity>,
        keyboardRows: List<List<InlineButton>>
    ): Result<Unit> {
        editedMessages.add(RecordedEdit(chatId, messageId, text, keyboardRows))
        return Result.success(Unit)
    }

    override suspend fun answerCallbackQuery(
        token: String,
        callbackQueryId: String,
        text: String?,
        showAlert: Boolean
    ): Result<Unit> {
        answeredCallbacks.add(callbackQueryId to text)
        return Result.success(Unit)
    }

    override suspend fun getUpdates(token: String, offset: Long?, limit: Int): Result<List<TelegramUpdate>> =
        Result.success(emptyList())

    override fun sanitizeToken(raw: String): String = raw.trim()
}

class FakeSmsGateway(
    private val nextResult: (String, String, Int) -> Result<SmsSendResult> = { dest, _, slot ->
        Result.success(SmsSendResult(dest, slot, subId = 10 + slot, carrierName = "Carrier${slot + 1}", partCount = 1))
    }
) : SmsGateway {
    val dispatched = mutableListOf<RecordedSmsDispatch>()

    override suspend fun sendSms(destination: String, messageText: String, slotIndex: Int): Result<SmsSendResult> {
        dispatched.add(RecordedSmsDispatch(destination, messageText, slotIndex))
        return nextResult(destination, messageText, slotIndex)
    }

    override fun canReplyToAddress(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val trimmed = address.trim()
        return !trimmed.any { it.isLetter() } && trimmed.count { it.isDigit() } >= 3
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimControlAndReplyTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vanta_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testLegacyPreferencesMigrationAndPersistence() {
        val rawPrefs = context.getSharedPreferences("vanta_secure_prefs", Context.MODE_PRIVATE)
        rawPrefs.edit()
            .putInt("sim_selection", SimSelectionMode.SIM1_ONLY.ordinal)
            .putBoolean("otp_protection", true)
            .commit()

        val prefsMgr = PreferencesManager(context)
        val appPrefs = prefsMgr.preferences.value
        val simControl = appPrefs.simControl

        assertTrue("SIM 1 master should be ON from SIM1_ONLY", simControl.sim1.masterEnabled)
        assertFalse("SIM 2 master should be OFF from SIM1_ONLY", simControl.sim2.masterEnabled)
        assertTrue("Centralized OTP protection must preserve legacy otp_protection=true", appPrefs.isOtpProtectionEnabled)
        assertTrue("Per-SIM OTP forwarding remains enabled independently by default", simControl.sim1.otpMessagesEnabled)
        assertTrue("Per-SIM OTP forwarding remains enabled independently by default", simControl.sim2.otpMessagesEnabled)
        assertTrue(simControl.sim1.smsForwardingEnabled)
        assertTrue(simControl.sim1.smsSendingEnabled)
        assertFalse(simControl.sim1.callLogForwardingEnabled)
        assertFalse(simControl.sim1.incomingCallNotificationsEnabled)

        // Toggle SIM 2 features independently and verify persistence across restart
        prefsMgr.setSimMasterEnabled(1, true)
        prefsMgr.setSimFeatureEnabled(1, SimFeature.CALL_LOG_FORWARDING, true)
        prefsMgr.setSimFeatureEnabled(0, SimFeature.INCOMING_CALL_NOTIFICATIONS, true)

        val reloaded = PreferencesManager(context).preferences.value.simControl
        assertTrue(reloaded.sim2.masterEnabled)
        assertTrue(reloaded.sim2.callLogForwardingEnabled)
        assertFalse(reloaded.sim1.callLogForwardingEnabled)
        assertTrue(reloaded.sim1.incomingCallNotificationsEnabled)
        assertFalse(reloaded.sim2.incomingCallNotificationsEnabled)
    }

    @Test
    fun testMasterSwitchOverridesWithoutDeletingSavedFeaturesAndIsIsolatedPerSim() {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.setSimMasterEnabled(0, true)
        prefsMgr.setSimMasterEnabled(1, true)
        prefsMgr.setSimFeatureEnabled(0, SimFeature.SMS_FORWARDING, true)
        prefsMgr.setSimFeatureEnabled(0, SimFeature.CALL_LOG_FORWARDING, true)
        prefsMgr.setSimFeatureEnabled(1, SimFeature.SMS_FORWARDING, true)

        // Turn SIM 1 master OFF
        prefsMgr.setSimMasterEnabled(0, false)

        val afterOff = prefsMgr.preferences.value.simControl
        assertFalse(afterOff.sim1.masterEnabled)
        // Stored per-SIM feature values must be preserved
        assertTrue(afterOff.sim1.smsForwardingEnabled)
        assertTrue(afterOff.sim1.callLogForwardingEnabled)
        // Effective checks must return false when master is OFF
        assertFalse(afterOff.sim1.isFeatureEffective(SimFeature.SMS_FORWARDING))
        assertFalse(afterOff.sim1.isFeatureEffective(SimFeature.CALL_LOG_FORWARDING))
        assertFalse(afterOff.sim1.isFeatureEffective(SimFeature.SMS_SENDING))
        // SIM 2 must be completely unaffected
        assertTrue(afterOff.sim2.masterEnabled)
        assertTrue(afterOff.sim2.isFeatureEffective(SimFeature.SMS_FORWARDING))

        // Turn SIM 1 master back ON: saved preferences become effective again
        prefsMgr.setSimMasterEnabled(0, true)
        val afterOn = prefsMgr.preferences.value.simControl
        assertTrue(afterOn.sim1.isFeatureEffective(SimFeature.SMS_FORWARDING))
        assertTrue(afterOn.sim1.isFeatureEffective(SimFeature.CALL_LOG_FORWARDING))
    }

    @Test
    fun testSmsProcessorPerSimFilteringOtpAndRealSubIdRouting() {
        val prefsMgr = PreferencesManager(context)
        val app = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        app.grantPermissions(android.Manifest.permission.READ_PHONE_STATE)

        // Configure subscription 7 in slot 0 and subscription 2 in slot 1 (non-1-based subIds!)
        val subMgr = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
        val sub0 = ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder()
            .setId(7)
            .setSimSlotIndex(0)
            .setCarrierName("AlphaTel")
            .buildSubscriptionInfo()
        val sub1 = ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder()
            .setId(2)
            .setSimSlotIndex(1)
            .setCarrierName("BetaNet")
            .buildSubscriptionInfo()
        shadowOf(subMgr).setActiveSubscriptionInfos(sub0, sub1)

        val simManager = SimManager(context, prefsMgr)
        simManager.refreshSims()

        val fakeApi = FakeTelegramBotApi()
        val botManager = TelegramBotManager(prefsMgr, fakeApi, autoStartPolling = false)
        val queueManager = DeliveryQueueManager(botManager, prefsMgr, simManager)
        val processor = SmsProcessor(prefsMgr, simManager, queueManager)

        // Verify subId 7 -> slot 0, subId 2 -> slot 1 (no assumption that subId == slot + 1)
        val intentForSim2 = Intent().apply { putExtra("subscription", 2) }
        val (resolvedSlot2, resolvedSub2) = processor.extractSimRouting(intentForSim2)
        assertEquals(1, resolvedSlot2)
        assertEquals(2, resolvedSub2)

        val intentForSim1 = Intent().apply { putExtra("subscription", 7) }
        val (resolvedSlot1, resolvedSub1) = processor.extractSimRouting(intentForSim1)
        assertEquals(0, resolvedSlot1)
        assertEquals(7, resolvedSub1)

        // Enable bridge: SIM 1 has SMS + OTP ON; SIM 2 has SMS ON but OTP OFF
        prefsMgr.setForwardingEnabled(true)
        prefsMgr.setSimMasterEnabled(0, true)
        prefsMgr.setSimMasterEnabled(1, true)
        prefsMgr.setSimFeatureEnabled(0, SimFeature.SMS_FORWARDING, true)
        prefsMgr.setSimFeatureEnabled(0, SimFeature.OTP_MESSAGES, true)
        prefsMgr.setSimFeatureEnabled(1, SimFeature.SMS_FORWARDING, true)
        prefsMgr.setSimFeatureEnabled(1, SimFeature.OTP_MESSAGES, false)

        val normalSim2 = SmsMessageItem(
            id = "m1",
            sender = "+15550002222",
            body = "Dinner at 7?",
            timestamp = 1715000100000L,
            simSlot = -1,
            subId = 2,
            isOtp = false
        )
        assertTrue("Non-OTP on SIM 2 should forward", processor.handleProcessedSms(normalSim2))

        val otpSim2 = SmsMessageItem(
            id = "m2",
            sender = "+15550002223",
            body = "Your verification code is 991122",
            timestamp = 1715000200000L,
            simSlot = 1,
            subId = 2,
            isOtp = true
        )
        assertFalse("OTP on SIM 2 must be filtered when OTP_MESSAGES=false", processor.handleProcessedSms(otpSim2))

        val otpSim1 = SmsMessageItem(
            id = "m3",
            sender = "+15550001111",
            body = "Your verification code is 123456",
            timestamp = 1715000300000L,
            simSlot = 0,
            subId = 7,
            isOtp = true
        )
        assertTrue("OTP on SIM 1 must forward when SIM 1 OTP_MESSAGES=true", processor.handleProcessedSms(otpSim1))

        // Master OFF on SIM 1 blocks SIM 1 without blocking SIM 2
        prefsMgr.setSimMasterEnabled(0, false)
        val blockedSim1 = SmsMessageItem(
            id = "m4",
            sender = "+15550001119",
            body = "Another text",
            timestamp = 1715000400000L,
            simSlot = 0,
            subId = 7,
            isOtp = false
        )
        assertFalse("SIM 1 master OFF must block SIM 1", processor.handleProcessedSms(blockedSim1))
    }

    @Test
    fun testCallLogFormattingCoversAllTypesAndDuration() {
        val incoming = CallLogEntryItem(
            callLogId = 1L,
            number = "+12025550101",
            contactName = "Mom",
            callType = CallType.INCOMING,
            timestamp = 1715000500000L,
            durationSeconds = 125L,
            simSlot = 0,
            subId = 1
        )
        val formattedIncoming = TelegramEntityHelper.formatCallLogMessage(incoming, "SIM 1 · CarrierA")
        assertTrue(formattedIncoming.text.contains("Incoming Call"))
        assertTrue(formattedIncoming.text.contains("Caller: Mom (+12025550101)"))
        assertTrue(formattedIncoming.text.contains("SIM: SIM 1 · CarrierA"))
        assertTrue(formattedIncoming.text.contains("Duration: 2m 5s"))
        assertTrue(formattedIncoming.text.contains(TelegramEntityHelper.formatTimestamp(1715000500000L)))

        val missed = incoming.copy(callLogId = 2L, callType = CallType.MISSED, durationSeconds = 0L, contactName = null)
        val formattedMissed = TelegramEntityHelper.formatCallLogMessage(missed, "SIM 2 · CarrierB")
        assertTrue(formattedMissed.text.contains("Missed Call"))
        assertTrue(formattedMissed.text.contains("Caller: +12025550101"))
        assertFalse("Missed calls should not include duration line", formattedMissed.text.contains("Duration:"))
    }

    @Test
    fun testTelegramReplyFlowSimSwitchCancelAuthMultiUserAndDeduplication() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.saveBotToken("123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Test", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)
        prefsMgr.setSimMasterEnabled(0, true)
        prefsMgr.setSimMasterEnabled(1, true)
        prefsMgr.setSimFeatureEnabled(0, SimFeature.SMS_SENDING, true)
        prefsMgr.setSimFeatureEnabled(1, SimFeature.SMS_SENDING, true)

        val fakeApi = FakeTelegramBotApi()
        val fakeGateway = FakeSmsGateway()
        val botManager = TelegramBotManager(prefsMgr, fakeApi, autoStartPolling = false).apply {
            smsGateway = fakeGateway
            simInfoProvider = {
                listOf(
                    SimCardInfo(0, 10, "Alpha", "+10001", true, true, hasPermission = true),
                    SimCardInfo(1, 20, "Beta", "+10002", true, true, hasPermission = true)
                )
            }
        }

        // 1. Forward SMS from SIM 1 -> captures reply:<contextId> button
        val fwdRes = botManager.forwardSms(
            sender = "+14155550199",
            body = "Meet at 5?",
            simLabel = "SIM 1 · Alpha",
            contactName = "Sarah",
            receivedTimestamp = 1715000000000L,
            isOtp = false,
            simSlot = 0,
            subId = 10
        )
        assertTrue(fwdRes.isSuccess)
        val replyCallback = fakeApi.sentMessages.last().keyboardRows.flatten().first().callbackData!!
        assertTrue(replyCallback.startsWith("reply:"))

        // 2. Unauthorized chat tapping reply callback must be rejected
        botManager.handleUpdate(
            TelegramUpdate(
                updateId = 1L,
                messageId = null,
                chatId = 9999L, // unauthorized!
                userId = 88L,
                userName = "intruder",
                text = null,
                callbackQueryId = "cb_unauth",
                callbackData = replyCallback,
                callbackMessageId = 100L
            )
        )
        assertNull(botManager.getActiveSession(9999L, 88L))
        assertEquals("Unauthorized", fakeApi.answeredCallbacks.last().second)

        // 3. Authorized user taps Reply -> starts session defaulting to SIM 0
        botManager.handleUpdate(
            TelegramUpdate(
                updateId = 2L,
                messageId = null,
                chatId = 9001L,
                userId = 101L,
                userName = "alice",
                text = null,
                callbackQueryId = "cb_1",
                callbackData = replyCallback,
                callbackMessageId = 100L
            )
        )
        val promptSend = fakeApi.sentMessages.last()
        assertTrue(promptSend.text.contains("Sarah (+14155550199)"))
        assertTrue(promptSend.text.contains("SIM 1 · Alpha"))
        val sim2SwitchData = promptSend.keyboardRows.flatten().first { it.callbackData?.endsWith(":1") == true }.callbackData!!

        // Concurrent second user opens separate session cleanly without disturbing user 101
        botManager.handleUpdate(
            TelegramUpdate(
                updateId = 3L,
                messageId = null,
                chatId = 9001L,
                userId = 202L,
                userName = "bob",
                text = null,
                callbackQueryId = "cb_2",
                callbackData = replyCallback,
                callbackMessageId = 100L
            )
        )
        assertNotNull(botManager.getActiveSession(9001L, 101L))
        assertNotNull(botManager.getActiveSession(9001L, 202L))

        // User 202 cancels via /cancel -> does not affect user 101's session
        botManager.handleUpdate(
            TelegramUpdate(
                updateId = 4L,
                messageId = 500L,
                chatId = 9001L,
                userId = 202L,
                userName = "bob",
                text = "/cancel"
            )
        )
        assertNull(botManager.getActiveSession(9001L, 202L))
        assertNotNull(botManager.getActiveSession(9001L, 101L))

        // 4. User 101 switches SIM to SIM 2
        botManager.handleUpdate(
            TelegramUpdate(
                updateId = 5L,
                messageId = null,
                chatId = 9001L,
                userId = 101L,
                userName = "alice",
                text = null,
                callbackQueryId = "cb_sim2",
                callbackData = sim2SwitchData,
                callbackMessageId = 101L
            )
        )
        assertEquals(1, botManager.getActiveSession(9001L, 101L)?.selectedSlotIndex)
        assertTrue(fakeApi.editedMessages.last().text.contains("SIM 2 · Beta"))

        // 5. Sending text dispatches SMS on SIM 2 and consumes session
        botManager.handleUpdate(
            TelegramUpdate(
                updateId = 6L,
                messageId = 501L,
                chatId = 9001L,
                userId = 101L,
                userName = "alice",
                text = "On my way!"
            )
        )
        assertEquals(1, fakeGateway.dispatched.size)
        assertEquals("+14155550199", fakeGateway.dispatched.first().destination)
        assertEquals("On my way!", fakeGateway.dispatched.first().messageText)
        assertEquals(1, fakeGateway.dispatched.first().slotIndex)
        assertNull("Session should clear after sending", botManager.getActiveSession(9001L, 101L))
        assertTrue(fakeApi.editedMessages.last().text.contains("✓ SMS delivered to carrier"))

        // 6. Duplicate update/messageId must NOT dispatch SMS again
        botManager.handleUpdate(
            TelegramUpdate(
                updateId = 6L,
                messageId = 501L,
                chatId = 9001L,
                userId = 101L,
                userName = "alice",
                text = "On my way!"
            )
        )
        assertEquals(1, fakeGateway.dispatched.size)
    }

    @Test
    fun testReplyCancelButtonAndGatewayFailureAndAlphanumericRejection() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        prefsMgr.saveBotToken("123456789:ABCdefGhIJKlmNoPQRstuVWXyz_Test", "vanta_bot", 1L)
        prefsMgr.setAuthorizedChatId(9001L)

        val fakeApi = FakeTelegramBotApi()
        val failingGateway = FakeSmsGateway { _, _, _ ->
            Result.failure(IllegalStateException("Carrier radio off"))
        }
        val botManager = TelegramBotManager(prefsMgr, fakeApi, autoStartPolling = false).apply {
            smsGateway = failingGateway
            simInfoProvider = { listOf(SimCardInfo(0, 10, "Alpha", null, true, true, hasPermission = true)) }
        }

        // Alphanumeric sender rejects reply callback cleanly
        botManager.forwardSms("Google", "Your code is 1234", "SIM 1 · Alpha", simSlot = 0, subId = 10)
        val alphaCb = fakeApi.sentMessages.last().keyboardRows.flatten().first().callbackData!!
        botManager.handleUpdate(
            TelegramUpdate(10L, null, 9001L, 101L, "alice", null, "cb_alpha", alphaCb, 100L)
        )
        assertNull(botManager.getActiveSession(9001L, 101L))
        assertTrue(fakeApi.answeredCallbacks.last().second?.contains("alphanumeric") == true)

        // Dialable sender -> test Cancel inline button
        botManager.forwardSms("+15551234567", "Hey", "SIM 1 · Alpha", simSlot = 0, subId = 10)
        val validCb = fakeApi.sentMessages.last().keyboardRows.flatten().first().callbackData!!
        botManager.handleUpdate(
            TelegramUpdate(11L, null, 9001L, 101L, "alice", null, "cb_start1", validCb, 101L)
        )
        val cancelCb = fakeApi.sentMessages.last().keyboardRows.flatten().first { it.text == "Cancel" }.callbackData!!
        botManager.handleUpdate(
            TelegramUpdate(12L, null, 9001L, 101L, "alice", null, "cb_cancel", cancelCb, 102L)
        )
        assertNull(botManager.getActiveSession(9001L, 101L))

        // Start another session and test SmsGateway failure reports accurately (never claims success)
        botManager.handleUpdate(
            TelegramUpdate(13L, null, 9001L, 101L, "alice", null, "cb_start2", validCb, 101L)
        )
        botManager.handleUpdate(
            TelegramUpdate(14L, 600L, 9001L, 101L, "alice", "Reply attempt")
        )
        val lastEdit = fakeApi.editedMessages.last().text
        assertTrue("Failure must report error", lastEdit.contains("✕ SMS failed to send"))
        assertTrue("Failure must include exact reason", lastEdit.contains("Carrier radio off"))
    }

    @Test
    fun testAndroidSmsGatewayValidationChecksPermissionAndPerSimSwitch() = runBlocking {
        val prefsMgr = PreferencesManager(context)
        val simManager = SimManager(context, prefsMgr)
        val gateway = AndroidSmsGateway(context, simManager, prefsMgr)

        assertFalse(gateway.canReplyToAddress("Google"))
        assertFalse(gateway.canReplyToAddress("  "))
        assertTrue(gateway.canReplyToAddress("+14155550100"))
        assertTrue(gateway.canReplyToAddress("10086"))

        // Without SEND_SMS permission, sendSms must fail cleanly
        val noPermRes = gateway.sendSms("+14155550100", "Hello", 0)
        assertTrue(noPermRes.isFailure)
        assertTrue(noPermRes.exceptionOrNull()?.message?.contains("SEND_SMS") == true)

        // Grant SEND_SMS, disable SIM 1 SMS_SENDING -> fail cleanly
        val app = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        app.grantPermissions(android.Manifest.permission.SEND_SMS)
        prefsMgr.setSimFeatureEnabled(0, SimFeature.SMS_SENDING, false)

        val disabledRes = gateway.sendSms("+14155550100", "Hello", 0)
        assertTrue(disabledRes.isFailure)
        assertTrue(disabledRes.exceptionOrNull()?.message?.contains("disabled") == true)
    }

    @Test
    fun testSubIdIsInvalidWhenPermissionMissing() {
        val prefsMgr = PreferencesManager(context)
        val simManager = SimManager(context, prefsMgr)
        val sims = simManager.detectedSims.value
        assertTrue(sims.isNotEmpty())
        assertTrue("Without READ_PHONE_STATE, subId must be INVALID (-1)", sims.all { it.subId == -1 && !it.hasPermission && !it.isAvailable })
    }

    @Test
    fun testCentralizedOtpProtectionBlocksGloballyWhileDecoupledFromPerSimSettings() {
        val prefsMgr = PreferencesManager(context)
        val app = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        app.grantPermissions(android.Manifest.permission.READ_PHONE_STATE)

        val subMgr = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
        val sub0 = ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder()
            .setId(5)
            .setSimSlotIndex(0)
            .setCarrierName("AlphaTel")
            .buildSubscriptionInfo()
        shadowOf(subMgr).setActiveSubscriptionInfos(sub0)

        val simManager = SimManager(context, prefsMgr)
        simManager.refreshSims()

        val fakeApi = FakeTelegramBotApi()
        val botManager = TelegramBotManager(prefsMgr, fakeApi, autoStartPolling = false)
        val queueManager = DeliveryQueueManager(botManager, prefsMgr, simManager)
        val processor = SmsProcessor(prefsMgr, simManager, queueManager)

        prefsMgr.setForwardingEnabled(true)
        prefsMgr.setSimMasterEnabled(0, true)
        prefsMgr.setSimFeatureEnabled(0, SimFeature.SMS_FORWARDING, true)
        prefsMgr.setSimFeatureEnabled(0, SimFeature.OTP_MESSAGES, true)
        prefsMgr.setOtpProtectionEnabled(true)

        // Enabling OTP protection does NOT mutate per-SIM otpMessagesEnabled
        assertTrue(prefsMgr.preferences.value.simControl.isAnyOtpForwardingEnabled())
        assertTrue(prefsMgr.preferences.value.isOtpProtectionEnabled)

        val otpSms = SmsMessageItem(
            id = "otp-shield-1",
            sender = "+15550009999",
            body = "Your verification code is 445566",
            timestamp = 1715000600000L,
            simSlot = 0,
            subId = 5,
            isOtp = true
        )
        assertFalse("Global OTP protection must block OTP even when SIM 1 OTP forwarding is ON", processor.handleProcessedSms(otpSms))

        prefsMgr.setOtpProtectionEnabled(false)
        assertTrue("Disabling global OTP protection must allow SIM 1 OTP to forward", processor.handleProcessedSms(otpSms))
    }

    @Test
    fun testDashboardLiveStatusSimRowsAndOtpWarningBehavior() {
        var simControlState by mutableStateOf(
            SimControlConfig(
                sim1 = SimFeatureSettings(masterEnabled = true, smsForwardingEnabled = true, otpMessagesEnabled = false),
                sim2 = SimFeatureSettings(masterEnabled = true, smsForwardingEnabled = true, otpMessagesEnabled = false)
            )
        )
        var otpProtectionState by mutableStateOf(true)
        val sims = listOf(
            SimCardInfo(slotIndex = 0, subId = 1, carrierName = "Vodafone", phoneNumber = null, isAvailable = true, isSelected = true, hasPermission = true),
            SimCardInfo(slotIndex = 1, subId = -1, carrierName = "No SIM", phoneNumber = null, isAvailable = false, isSelected = false, hasPermission = true)
        )

        composeRule.setContent {
            VantaTheme {
                StatusPanel(
                    isForwardingActive = true,
                    isServiceRunning = true,
                    isInternetAvailable = true,
                    isTelegramConnected = true,
                    pendingCount = 0,
                    totalDelivered = 12,
                    lastDelivery = 0L,
                    simControl = simControlState,
                    sims = sims,
                    isOtpProtectionEnabled = otpProtectionState,
                    onToggleSimMaster = { slot, enabled ->
                        simControlState = if (slot == 0) {
                            simControlState.copy(sim1 = simControlState.sim1.copy(masterEnabled = enabled))
                        } else {
                            simControlState.copy(sim2 = simControlState.sim2.copy(masterEnabled = enabled))
                        }
                    },
                    onDisableOtpProtection = {
                        otpProtectionState = false
                    }
                )
            }
        }
        composeRule.waitForIdle()

        // SIM 1 is available and ON; SIM 2 is absent and switch must be disabled & OFF
        composeRule.onNodeWithTag("live_status_sim_0").assertIsDisplayed()
        composeRule.onNodeWithTag("live_status_sim_1").assertIsDisplayed()
        composeRule.onNodeWithText("No SIM inserted").assertIsDisplayed()
        composeRule.onNodeWithTag("live_status_sim_switch_0").assertIsEnabled().assertIsOn()
        composeRule.onNodeWithTag("live_status_sim_switch_1").assertIsNotEnabled().assertIsOff()

        // OTP warning appears because protection=true and both SIMs have OTP forwarding disabled
        composeRule.onNodeWithTag("live_status_otp_warning").assertIsDisplayed()
        composeRule.onNodeWithText("OTP forwarding blocked by protection").assertIsDisplayed()

        // Clicking "Disable protection" turns off protection and removes warning immediately
        composeRule.onNodeWithTag("live_status_disable_protection_btn").performClick()
        composeRule.waitForIdle()
        assertFalse(otpProtectionState)
        composeRule.onNodeWithTag("live_status_otp_warning").assertDoesNotExist()

        // Re-enable protection & turn SIM 1 OTP forwarding ON -> warning stays hidden
        otpProtectionState = true
        simControlState = simControlState.copy(sim1 = simControlState.sim1.copy(otpMessagesEnabled = true))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_status_otp_warning").assertDoesNotExist()

        // Toggle SIM 1 master OFF while protection=true -> OTP forwarding becomes effectively OFF on both SIMs -> warning reappears
        composeRule.onNodeWithTag("live_status_sim_switch_0").performClick()
        composeRule.waitForIdle()
        assertFalse(simControlState.sim1.masterEnabled)
        composeRule.onNodeWithTag("live_status_otp_warning").assertIsDisplayed()
    }

    @Test
    fun testOtpProtectionControlLockedStateAndUnlockBehavior() {
        var otpForwardingAvailable by mutableStateOf(false)
        var protectionEnabled by mutableStateOf(false)
        var toggleCount = 0

        composeRule.setContent {
            VantaTheme {
                OtpProtectionControlCard(
                    isProtectionEnabled = protectionEnabled,
                    isOtpForwardingAvailable = otpForwardingAvailable,
                    onToggle = {
                        toggleCount++
                        protectionEnabled = it
                    },
                    switchTestTag = "test_otp_switch"
                )
            }
        }
        composeRule.waitForIdle()

        // Locked state: explanation shown, locked overlay shown, switch disabled
        composeRule.onNodeWithTag("otp_protection_locked_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("otp_protection_disabled_hint").assertIsDisplayed()
        composeRule.onNodeWithText("Enable OTP forwarding on at least one SIM to use this feature.").assertIsDisplayed()
        composeRule.onNodeWithTag("test_otp_switch").assertIsNotEnabled()
        assertEquals(0, toggleCount)

        // Unlock when OTP forwarding becomes available on at least one SIM
        otpForwardingAvailable = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("otp_protection_locked_overlay").assertDoesNotExist()
        composeRule.onNodeWithTag("otp_protection_disabled_hint").assertDoesNotExist()
        composeRule.onNodeWithTag("test_otp_switch").assertIsEnabled().performClick()
        composeRule.waitForIdle()
        assertEquals(1, toggleCount)
        assertTrue(protectionEnabled)
    }

    @Test
    fun testSystemHealthRebuiltLayoutAndCollapsibleLog() {
        composeRule.setContent {
            VantaTheme {
                SystemHealthScreen(onNavigateBack = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("health_core_systems_header").assertIsDisplayed()
        composeRule.onNodeWithTag("health_core_systems_card").assertIsDisplayed()
        composeRule.onNodeWithText("OS CAPABILITIES").assertDoesNotExist()
        composeRule.onNodeWithText("Pending SMS Queue").assertDoesNotExist()

        // LOG section is present directly beneath CORE SYSTEMS and starts collapsed
        composeRule.onNodeWithTag("log_collapse_toggle").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("clear_terminal_btn").assertDoesNotExist()

        // Clicking LOG header expands the terminal
        composeRule.onNodeWithTag("log_collapse_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("clear_terminal_btn").performScrollTo().assertIsDisplayed()
    }
}
