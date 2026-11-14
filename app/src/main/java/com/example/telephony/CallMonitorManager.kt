package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.VantaApplication
import com.example.core.EventLogger
import com.example.core.PreferencesManager
import com.example.data.model.CallLogEntryItem
import com.example.data.model.CallType
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SimFeature
import com.example.telegram.TelegramBotManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val app = context.applicationContext as? VantaApplication ?: return
        app.callMonitorManager.handlePhoneStateIntent(intent)
    }
}

class CallMonitorManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val simManager: SimManager,
    private val contactResolver: ContactResolver,
    private val telegramBotManager: TelegramBotManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val callLogMutex = Mutex()
    private var observer: ContentObserver? = null

    private var pendingRingingJob: Job? = null
    private var lastRingingTimestamp: Long = 0L
    private var lastCallState: String = TelephonyManager.EXTRA_STATE_IDLE

    fun startMonitoring() {
        val mainHandler = Handler(Looper.getMainLooper())
        if (observer == null && hasReadCallLogPermission()) {
            initializeCallLogBaseline()
            val newObserver = object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    scope.launch {
                        delay(400)
                        checkNewCallLogs()
                    }
                }
            }
            try {
                context.contentResolver.registerContentObserver(
                    CallLog.Calls.CONTENT_URI,
                    true,
                    newObserver
                )
                observer = newObserver
                EventLogger.log(LogCategory.CALLS, "Call log monitor active", LogLevel.INFO)
            } catch (e: Exception) {
                EventLogger.log(LogCategory.ERROR, "Failed to register call log observer: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun stopMonitoring() {
        observer?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {
            }
        }
        observer = null
        pendingRingingJob?.cancel()
        pendingRingingJob = null
    }

    /**
     * Ensures call monitoring starts dynamically if permissions or call toggles were turned on
     * while the foreground service is already running.
     */
    fun refreshObserverState() {
        if (observer == null &&
            preferencesManager.preferences.value.isForwardingEnabled &&
            hasReadCallLogPermission()
        ) {
            startMonitoring()
        }
    }

    @SuppressLint("MissingPermission")
    fun initializeCallLogBaseline() {
        if (!hasReadCallLogPermission() || preferencesManager.hasInitializedCallLogBaseline()) {
            return
        }
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                null,
                null,
                "${CallLog.Calls._ID} DESC LIMIT 1"
            )?.use { cursor ->
                val latestId = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                preferencesManager.setLastForwardedCallLogId(latestId)
                preferencesManager.setCallLogBaselineInitialized(true)
            }
        } catch (_: Exception) {
        }
    }

    fun handlePhoneStateIntent(intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val prefs = preferencesManager.preferences.value
        if (!prefs.isForwardingEnabled) {
            lastCallState = state
            return
        }

        val (slotIndex, subId) = extractCallSimRouting(intent)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                @Suppress("DEPRECATION")
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                val now = System.currentTimeMillis()

                // Android fires two RINGING broadcasts when READ_CALL_LOG is granted: first without number,
                // second with incoming number. Prefer the numbered one; wait 350ms before fallback alert.
                if (!incomingNumber.isNullOrBlank()) {
                    pendingRingingJob?.cancel()
                    if (now - lastRingingTimestamp > RING_DEDUP_WINDOW_MS) {
                        lastRingingTimestamp = now
                        dispatchIncomingCallNotification(incomingNumber, slotIndex, subId, now)
                    }
                } else if (lastCallState != TelephonyManager.EXTRA_STATE_RINGING &&
                    now - lastRingingTimestamp > RING_DEDUP_WINDOW_MS
                ) {
                    pendingRingingJob?.cancel()
                    pendingRingingJob = scope.launch {
                        delay(350)
                        val triggerTime = System.currentTimeMillis()
                        if (triggerTime - lastRingingTimestamp > RING_DEDUP_WINDOW_MS) {
                            lastRingingTimestamp = triggerTime
                            dispatchIncomingCallNotification(null, slotIndex, subId, triggerTime)
                        }
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                pendingRingingJob?.cancel()
                if (lastCallState != TelephonyManager.EXTRA_STATE_IDLE) {
                    scope.launch {
                        delay(1500)
                        checkNewCallLogs()
                    }
                }
            }
        }

        lastCallState = state
    }

    private fun dispatchIncomingCallNotification(
        callerNumber: String?,
        slotIndex: Int,
        subId: Int,
        timestamp: Long
    ) {
        if (!hasPhoneStatePermission()) return

        val prefs = preferencesManager.preferences.value
        if (!prefs.isForwardingEnabled) return

        val isEnabledForCall = if (slotIndex in 0..1) {
            prefs.simControl.forSlot(slotIndex).isFeatureEffective(SimFeature.INCOMING_CALL_NOTIFICATIONS)
        } else {
            prefs.simControl.sim1.isFeatureEffective(SimFeature.INCOMING_CALL_NOTIFICATIONS) ||
                prefs.simControl.sim2.isFeatureEffective(SimFeature.INCOMING_CALL_NOTIFICATIONS)
        }

        if (!isEnabledForCall) {
            EventLogger.log(
                LogCategory.CALLS,
                "Incoming call alert skipped: feature disabled for ${if (slotIndex in 0..1) "SIM ${slotIndex + 1}" else "active SIMs"}",
                LogLevel.INFO
            )
            return
        }

        scope.launch {
            val contactName = contactResolver.resolveContactName(callerNumber)
            val simLabel = simManager.formatSimDisplayLabel(slotIndex, subId)
            telegramBotManager.forwardIncomingCallNotification(
                callerNumber = callerNumber,
                contactName = contactName,
                simLabel = simLabel,
                timestamp = timestamp
            )
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun checkNewCallLogs() {
        if (!preferencesManager.preferences.value.isForwardingEnabled) return
        if (!hasReadCallLogPermission()) return

        if (!preferencesManager.hasInitializedCallLogBaseline()) {
            initializeCallLogBaseline()
            return
        }

        callLogMutex.withLock {
            val lastId = preferencesManager.getLastForwardedCallLogId()
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.PHONE_ACCOUNT_ID
            )
            val selection = "${CallLog.Calls._ID} > ?"
            val selectionArgs = arrayOf(lastId.toString())
            val sortOrder = "${CallLog.Calls._ID} ASC"

            try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    var highestSeenId = lastId
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        if (id > highestSeenId) highestSeenId = id

                        val number = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "Private Number"
                        val cachedName = cursor.getString(2)?.takeIf { it.isNotBlank() }
                        val rawType = cursor.getInt(3)
                        val callDate = cursor.getLong(4)
                        val duration = cursor.getLong(5)
                        val phoneAccountId = cursor.getString(6)

                        val callType = mapCallType(rawType) ?: continue
                        val (slotIndex, subId) = simManager.resolveSlotFromPhoneAccountId(phoneAccountId)

                        val entry = CallLogEntryItem(
                            callLogId = id,
                            number = number,
                            contactName = cachedName ?: contactResolver.resolveContactName(number),
                            callType = callType,
                            timestamp = callDate,
                            durationSeconds = duration,
                            simSlot = slotIndex,
                            subId = subId
                        )

                        processCallLogEntry(entry)
                    }
                    if (highestSeenId > lastId) {
                        preferencesManager.setLastForwardedCallLogId(highestSeenId)
                    }
                }
            } catch (e: Exception) {
                EventLogger.log(
                    LogCategory.ERROR,
                    "Error reading call history: ${e.message}",
                    LogLevel.ERROR
                )
            }
        }
    }

    suspend fun processCallLogEntry(entry: CallLogEntryItem): Boolean {
        val prefs = preferencesManager.preferences.value
        if (!prefs.isForwardingEnabled) return false

        val isEnabled = if (entry.simSlot in 0..1) {
            prefs.simControl.forSlot(entry.simSlot).isFeatureEffective(SimFeature.CALL_LOG_FORWARDING)
        } else {
            prefs.simControl.sim1.isFeatureEffective(SimFeature.CALL_LOG_FORWARDING) ||
                prefs.simControl.sim2.isFeatureEffective(SimFeature.CALL_LOG_FORWARDING)
        }

        if (!isEnabled) {
            val simTag = if (entry.simSlot in 0..1) "SIM ${entry.simSlot + 1}" else "active SIMs"
            EventLogger.log(
                LogCategory.CALLS,
                "${entry.callType.label} skipped: Call Log Forwarding disabled on $simTag",
                LogLevel.INFO
            )
            return false
        }

        val simLabel = simManager.formatSimDisplayLabel(entry.simSlot, entry.subId)
        return telegramBotManager.forwardCallLog(entry, simLabel).isSuccess
    }

    private fun extractCallSimRouting(intent: Intent): Pair<Int, Int> {
        val rawSubId = intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            .takeIf { it >= 0 }
            ?: intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                .takeIf { it >= 0 }
            ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID

        if (rawSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            val slot = simManager.resolveSlotFromSubId(rawSubId)
            if (slot in 0..1) return slot to rawSubId
        }

        val explicitSlot = intent.getIntExtra("slot", -1).takeIf { it in 0..1 }
            ?: intent.getIntExtra("simId", -1).takeIf { it in 0..1 }
        if (explicitSlot != null) {
            val sim = simManager.getSimForSlot(explicitSlot)
            return explicitSlot to (if (rawSubId >= 0) rawSubId else (sim?.subId ?: -1))
        }

        val activeSims = simManager.detectedSims.value.filter { it.isAvailable }
        if (activeSims.size == 1) {
            val only = activeSims.first()
            return only.slotIndex to only.subId
        }
        return -1 to rawSubId
    }

    private fun mapCallType(rawType: Int): CallType? = when (rawType) {
        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
        else -> null
    }

    private fun hasReadCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val RING_DEDUP_WINDOW_MS = 4_000L
    }
}
