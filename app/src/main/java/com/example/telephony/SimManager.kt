package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.core.EventLogger
import com.example.core.PreferencesManager
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SimCardInfo
import com.example.data.model.SimFeature
import com.example.data.model.SimSelectionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SimManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val subscriptionManager =
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val telecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingRefreshRunnable = Runnable { refreshSims() }

    private val _detectedSims = MutableStateFlow<List<SimCardInfo>>(emptyList())
    val detectedSims: StateFlow<List<SimCardInfo>> = _detectedSims.asStateFlow()

    private var subscriptionsListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    private var simBroadcastReceiver: BroadcastReceiver? = null

    init {
        refreshSims()
        registerSystemObservers()
    }

    private fun scheduleDebouncedRefresh() {
        mainHandler.removeCallbacks(pendingRefreshRunnable)
        mainHandler.postDelayed(pendingRefreshRunnable, REFRESH_DEBOUNCE_MS)
    }

    private fun registerSystemObservers() {
        try {
            val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                override fun onSubscriptionsChanged() {
                    scheduleDebouncedRefresh()
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                subscriptionManager?.addOnSubscriptionsChangedListener(
                    ContextCompat.getMainExecutor(context),
                    listener
                )
            } else {
                @Suppress("DEPRECATION")
                subscriptionManager?.addOnSubscriptionsChangedListener(listener)
            }
            subscriptionsListener = listener
        } catch (_: Exception) {
        }

        try {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val action = intent?.action ?: return
                    if (action == ACTION_SIM_STATE_CHANGED ||
                        action == ACTION_DEFAULT_SMS_SUB_CHANGED
                    ) {
                        scheduleDebouncedRefresh()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(ACTION_SIM_STATE_CHANGED)
                addAction(ACTION_DEFAULT_SMS_SUB_CHANGED)
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            simBroadcastReceiver = receiver
        } catch (_: Exception) {
        }
    }

    fun release() {
        mainHandler.removeCallbacks(pendingRefreshRunnable)
        subscriptionsListener?.let { listener ->
            try {
                subscriptionManager?.removeOnSubscriptionsChangedListener(listener)
            } catch (_: Exception) {
            }
        }
        subscriptionsListener = null
        simBroadcastReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
        simBroadcastReceiver = null
    }

    @SuppressLint("MissingPermission")
    fun refreshSims() {
        val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val simControl = preferencesManager.preferences.value.simControl
        val modemCount = getHardwareSlotCount()

        if (!hasPhoneStatePermission || subscriptionManager == null) {
            val noPermList = (0 until modemCount).map { slot ->
                SimCardInfo(
                    slotIndex = slot,
                    subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                    carrierName = "Permission required",
                    phoneNumber = null,
                    isAvailable = false,
                    isSelected = simControl.forSlot(slot).masterEnabled,
                    hasPermission = false
                )
            }
            val changed = _detectedSims.value != noPermList
            _detectedSims.value = noPermList
            if (changed) {
                EventLogger.log(
                    LogCategory.SIM,
                    "SIM state limited: Phone State permission required to inspect active subscriptions",
                    LogLevel.INFO
                )
            }
            return
        }

        try {
            val subList: List<SubscriptionInfo> = subscriptionManager.activeSubscriptionInfoList.orEmpty()
            val bySlot = subList.associateBy { it.simSlotIndex }
            val slotCount = maxOf(modemCount, (bySlot.keys.maxOrNull() ?: 0) + 1).coerceIn(1, 2)
            val defaultSmsSubId = getDefaultSmsSubIdSafe()

            val resultList = (0 until slotCount).map { slot ->
                val subInfo = bySlot[slot]
                if (subInfo != null) {
                    val carrier = resolveRealCarrierName(subInfo)
                    val icc = try {
                        @Suppress("DEPRECATION")
                        subInfo.iccId?.takeIf { it.isNotBlank() }
                    } catch (_: Exception) {
                        null
                    }

                    SimCardInfo(
                        slotIndex = slot,
                        subId = subInfo.subscriptionId,
                        carrierName = carrier,
                        phoneNumber = getPhoneNumberSafe(subInfo),
                        isAvailable = true,
                        isSelected = simControl.forSlot(slot).masterEnabled,
                        countryIso = subInfo.countryIso?.takeIf { it.isNotBlank() },
                        iccId = icc,
                        hasPermission = true,
                        isDefaultSms = defaultSmsSubId >= 0 && subInfo.subscriptionId == defaultSmsSubId
                    )
                } else {
                    SimCardInfo(
                        slotIndex = slot,
                        subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                        carrierName = "No SIM inserted",
                        phoneNumber = null,
                        isAvailable = false,
                        isSelected = simControl.forSlot(slot).masterEnabled,
                        hasPermission = true,
                        isDefaultSms = false
                    )
                }
            }

            val previous = _detectedSims.value
            _detectedSims.value = resultList
            if (previous != resultList) {
                val activeSummary = resultList.filter { it.isAvailable }
                    .joinToString(", ") {
                        val defaultTag = if (it.isDefaultSms) ", default SMS" else ""
                        "SIM ${it.slotIndex + 1} (${it.carrierName}, subId=${it.subId}$defaultTag)"
                    }
                EventLogger.log(
                    LogCategory.SIM,
                    if (activeSummary.isNotEmpty()) "Active SIMs: $activeSummary" else "No active SIM subscriptions detected",
                    if (activeSummary.isNotEmpty()) LogLevel.SUCCESS else LogLevel.WARNING
                )
            }
        } catch (e: Exception) {
            EventLogger.log(LogCategory.SIM, "Error inspecting SIM subscriptions: ${e.message}", LogLevel.ERROR)
        }
    }

    private fun resolveRealCarrierName(subInfo: SubscriptionInfo): String {
        val rawCarrier = subInfo.carrierName?.toString()?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        if (rawCarrier != null) return rawCarrier

        val rawDisplay = subInfo.displayName?.toString()?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        if (rawDisplay != null) return rawDisplay

        val operatorName = try {
            telephonyManager?.createForSubscriptionId(subInfo.subscriptionId)
                ?.simOperatorName
                ?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        } catch (_: Exception) {
            null
        }
        return operatorName ?: CARRIER_UNAVAILABLE
    }

    private fun getDefaultSmsSubIdSafe(): Int {
        return try {
            val id = SubscriptionManager.getDefaultSmsSubscriptionId()
            if (id != SubscriptionManager.INVALID_SUBSCRIPTION_ID && id >= 0) id else -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun getHardwareSlotCount(): Int {
        return try {
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephonyManager?.activeModemCount ?: 1
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.phoneCount ?: 1
            }
            raw.coerceIn(1, 2)
        } catch (_: Exception) {
            2
        }
    }

    @SuppressLint("MissingPermission", "HardwareIds")
    private fun getPhoneNumberSafe(subInfo: SubscriptionInfo): String? {
        val hasNumberPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_NUMBERS
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasNumberPermission) return null

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && subscriptionManager != null) {
                val num = subscriptionManager.getPhoneNumber(subInfo.subscriptionId)
                if (!num.isNullOrBlank()) num else null
            } else {
                @Suppress("DEPRECATION")
                val num = subInfo.number
                if (!num.isNullOrBlank()) num else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getSimForSlot(slotIndex: Int): SimCardInfo? {
        return _detectedSims.value.firstOrNull { it.slotIndex == slotIndex }
    }

    fun getSimForSubId(subId: Int): SimCardInfo? {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID || subId < 0) return null
        return _detectedSims.value.firstOrNull { it.subId == subId }
    }

    @SuppressLint("MissingPermission")
    fun resolveSlotFromSubId(subId: Int): Int {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID || subId < 0) return -1
        getSimForSubId(subId)?.let { return it.slotIndex }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val activeInfo = subscriptionManager?.getActiveSubscriptionInfo(subId)
                if (activeInfo != null && activeInfo.simSlotIndex in 0..1) {
                    return activeInfo.simSlotIndex
                }
            } catch (_: Exception) {
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val slot = try {
                SubscriptionManager.getSlotIndex(subId)
            } catch (_: Exception) {
                SubscriptionManager.INVALID_SIM_SLOT_INDEX
            }
            if (slot in 0..1) return slot
        }
        return -1
    }

    /**
     * Resolves a CallLog.Calls.PHONE_ACCOUNT_ID string to (slotIndex, subId).
     * Returns (-1, -1) if the device or OS does not expose mapping.
     */
    @SuppressLint("MissingPermission")
    fun resolveSlotFromPhoneAccountId(phoneAccountId: String?): Pair<Int, Int> {
        if (phoneAccountId.isNullOrBlank()) {
            val activeSims = _detectedSims.value.filter { it.isAvailable }
            return if (activeSims.size == 1) {
                activeSims.first().slotIndex to activeSims.first().subId
            } else {
                -1 to SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }
        }

        val sims = _detectedSims.value
        phoneAccountId.toIntOrNull()?.let { numericId ->
            val match = sims.firstOrNull { it.isAvailable && it.subId == numericId }
            if (match != null) return match.slotIndex to match.subId
            val slot = resolveSlotFromSubId(numericId)
            if (slot in 0..1) return slot to numericId
        }

        for (sim in sims) {
            val icc = sim.iccId
            if (!icc.isNullOrBlank() && phoneAccountId.contains(icc, ignoreCase = true)) {
                return sim.slotIndex to sim.subId
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            telecomManager != null &&
            telephonyManager != null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                for (handle in telecomManager.callCapablePhoneAccounts) {
                    if (handle.id == phoneAccountId) {
                        val subId = telephonyManager.getSubscriptionId(handle)
                        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                            val slot = resolveSlotFromSubId(subId)
                            if (slot in 0..1) return slot to subId
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        val activeSims = sims.filter { it.isAvailable }
        return if (activeSims.size == 1) {
            activeSims.first().slotIndex to activeSims.first().subId
        } else {
            -1 to SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }

    fun formatSimDisplayLabel(slotIndex: Int, subId: Int = -1): String {
        val sim = getSimForSubId(subId) ?: getSimForSlot(slotIndex)
        val slotNum = when {
            sim != null -> sim.slotIndex + 1
            slotIndex >= 0 -> slotIndex + 1
            else -> null
        }
        val validCarrier = sim?.carrierName?.takeIf {
            sim.isAvailable &&
                it.isNotBlank() &&
                it != CARRIER_UNAVAILABLE &&
                it != "Permission required" &&
                it != "No SIM inserted"
        }
        return when {
            slotNum != null && validCarrier != null -> "SIM $slotNum · $validCarrier"
            slotNum != null -> "SIM $slotNum"
            else -> "Unknown SIM"
        }
    }

    fun syncSelectionStateFromPreferences() {
        val simControl = preferencesManager.preferences.value.simControl
        _detectedSims.value = _detectedSims.value.map { sim ->
            sim.copy(isSelected = simControl.forSlot(sim.slotIndex).masterEnabled)
        }
    }

    fun applySelectionMode(mode: SimSelectionMode) {
        preferencesManager.setSimSelectionMode(mode)
        syncSelectionStateFromPreferences()
        EventLogger.log(
            LogCategory.SIM,
            "Updated SIM configuration: ${mode.label}",
            LogLevel.INFO
        )
    }

    fun isSlotEligible(slotIndex: Int): Boolean {
        if (slotIndex !in 0..1) return true
        return preferencesManager.preferences.value.simControl.forSlot(slotIndex).masterEnabled
    }

    fun isFeatureEnabled(slotIndex: Int, feature: SimFeature): Boolean {
        val simControl = preferencesManager.preferences.value.simControl
        if (slotIndex !in 0..1) {
            return simControl.sim1.isFeatureEffective(feature) || simControl.sim2.isFeatureEffective(feature)
        }
        return simControl.forSlot(slotIndex).isFeatureEffective(feature)
    }

    companion object {
        const val CARRIER_UNAVAILABLE = "Carrier unavailable"
        private const val REFRESH_DEBOUNCE_MS = 250L
        private const val ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED"
        private const val ACTION_DEFAULT_SMS_SUB_CHANGED = "android.telephony.action.DEFAULT_SMS_SUBSCRIPTION_CHANGED"
    }
}
