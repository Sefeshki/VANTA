package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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

    private val _detectedSims = MutableStateFlow<List<SimCardInfo>>(emptyList())
    val detectedSims: StateFlow<List<SimCardInfo>> = _detectedSims.asStateFlow()

    init {
        refreshSims()
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
            _detectedSims.value = noPermList
            EventLogger.log(
                LogCategory.SIM,
                "SIM state limited: Phone State permission required to inspect active subscriptions",
                LogLevel.INFO
            )
            return
        }

        try {
            val subList: List<SubscriptionInfo> = subscriptionManager.activeSubscriptionInfoList.orEmpty()
            val bySlot = subList.associateBy { it.simSlotIndex }
            val slotCount = maxOf(modemCount, (bySlot.keys.maxOrNull() ?: 0) + 1).coerceIn(1, 2)

            val resultList = (0 until slotCount).map { slot ->
                val subInfo = bySlot[slot]
                if (subInfo != null) {
                    val carrier = subInfo.carrierName?.toString()?.takeIf { it.isNotBlank() }
                        ?: subInfo.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: "SIM ${slot + 1}"

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
                        countryIso = subInfo.countryIso,
                        iccId = icc,
                        hasPermission = true
                    )
                } else {
                    SimCardInfo(
                        slotIndex = slot,
                        subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                        carrierName = "No SIM inserted",
                        phoneNumber = null,
                        isAvailable = false,
                        isSelected = simControl.forSlot(slot).masterEnabled,
                        hasPermission = true
                    )
                }
            }

            _detectedSims.value = resultList
            val activeSummary = resultList.filter { it.isAvailable }
                .joinToString(", ") { "SIM ${it.slotIndex + 1} (${it.carrierName}, subId=${it.subId})" }
            EventLogger.log(
                LogCategory.SIM,
                if (activeSummary.isNotEmpty()) "Active SIMs: $activeSummary" else "No active SIM subscriptions detected",
                if (activeSummary.isNotEmpty()) LogLevel.SUCCESS else LogLevel.WARNING
            )
        } catch (e: Exception) {
            EventLogger.log(LogCategory.SIM, "Error inspecting SIM subscriptions: ${e.message}", LogLevel.ERROR)
        }
    }

    private fun getHardwareSlotCount(): Int {
        return try {
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephonyManager?.activeModemCount ?: 2
            } else {
                @Suppress("DEPRECATION")
                telephonyManager?.phoneCount ?: 2
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

    fun resolveSlotFromSubId(subId: Int): Int {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID || subId < 0) return -1
        getSimForSubId(subId)?.let { return it.slotIndex }
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
        // 1. Direct numeric subId match (common on AOSP Android 10+)
        phoneAccountId.toIntOrNull()?.let { numericId ->
            val match = sims.firstOrNull { it.isAvailable && it.subId == numericId }
            if (match != null) return match.slotIndex to match.subId
            val slot = resolveSlotFromSubId(numericId)
            if (slot in 0..1) return slot to numericId
        }

        // 2. Match against ICCID prefix/full string (common on Samsung/Qualcomm/MTK)
        for (sim in sims) {
            val icc = sim.iccId
            if (!icc.isNullOrBlank() && phoneAccountId.contains(icc, ignoreCase = true)) {
                return sim.slotIndex to sim.subId
            }
        }

        // 3. Query TelecomManager PhoneAccountHandle -> TelephonyManager.getSubscriptionId (API 30+)
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

        // 4. Single active SIM fallback when account ID is opaque
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
        return when {
            slotNum != null && sim != null && sim.isAvailable && sim.carrierName.isNotBlank() ->
                "SIM $slotNum · ${sim.carrierName}"
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
}
