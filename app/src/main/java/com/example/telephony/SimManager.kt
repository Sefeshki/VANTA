package com.example.telephony

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.core.EventLogger
import com.example.core.PreferencesManager
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SimCardInfo
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
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

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

        val currentSimMode = preferencesManager.preferences.value.simSelectionMode

        if (!hasPhoneStatePermission || subscriptionManager == null) {
            // Cannot query SubscriptionManager without permission
            val simCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephonyManager.activeModemCount
            } else {
                telephonyManager.phoneCount
            }.coerceAtLeast(1)

            val fallbackList = (0 until simCount.coerceAtMost(2)).map { slot ->
                SimCardInfo(
                    slotIndex = slot,
                    subId = slot + 1,
                    carrierName = "SIM ${slot + 1}",
                    phoneNumber = null,
                    isAvailable = true,
                    isSelected = isSimSelectedByMode(slot, currentSimMode)
                )
            }
            _detectedSims.value = fallbackList
            EventLogger.log(
                LogCategory.SIM,
                "Queried hardware modems: ${fallbackList.size} slots detected (phone state permission required for carrier names)",
                LogLevel.INFO
            )
            return
        }

        try {
            val subList: List<SubscriptionInfo>? = subscriptionManager.activeSubscriptionInfoList
            if (subList.isNullOrEmpty()) {
                val fallback = listOf(
                    SimCardInfo(
                        slotIndex = 0,
                        subId = 1,
                        carrierName = "No active SIM card",
                        phoneNumber = null,
                        isAvailable = false,
                        isSelected = false
                    )
                )
                _detectedSims.value = fallback
                EventLogger.log(LogCategory.SIM, "No active SIM subscriptions found on device", LogLevel.WARNING)
                return
            }

            val resultList = subList.map { subInfo ->
                val slotIndex = subInfo.simSlotIndex
                val carrier = subInfo.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    ?: subInfo.displayName?.toString()?.takeIf { it.isNotBlank() }
                    ?: "SIM ${slotIndex + 1}"

                val number = getPhoneNumberSafe(subInfo)

                SimCardInfo(
                    slotIndex = slotIndex,
                    subId = subInfo.subscriptionId,
                    carrierName = carrier,
                    phoneNumber = number,
                    isAvailable = true,
                    isSelected = isSimSelectedByMode(slotIndex, currentSimMode),
                    countryIso = subInfo.countryIso
                )
            }

            _detectedSims.value = resultList
            EventLogger.log(
                LogCategory.SIM,
                "Active SIMs: ${resultList.map { "SIM${it.slotIndex + 1} (${it.carrierName})" }.joinToString(", ")}",
                LogLevel.SUCCESS
            )
        } catch (e: Exception) {
            EventLogger.log(LogCategory.SIM, "Error inspecting SIM subscriptions: ${e.message}", LogLevel.ERROR)
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
        } catch (e: Exception) {
            null
        }
    }

    private fun isSimSelectedByMode(slotIndex: Int, mode: SimSelectionMode): Boolean {
        return when (mode) {
            SimSelectionMode.ALL -> true
            SimSelectionMode.SIM1_ONLY -> slotIndex == 0
            SimSelectionMode.SIM2_ONLY -> slotIndex == 1
            SimSelectionMode.NONE -> false
        }
    }

    fun applySelectionMode(mode: SimSelectionMode) {
        preferencesManager.setSimSelectionMode(mode)
        _detectedSims.value = _detectedSims.value.map { sim ->
            sim.copy(isSelected = isSimSelectedByMode(sim.slotIndex, mode))
        }
        EventLogger.log(
            LogCategory.SIM,
            "Updated SIM filter configuration: ${mode.label}",
            LogLevel.INFO
        )
    }

    fun isSlotEligible(slotIndex: Int): Boolean {
        val mode = preferencesManager.preferences.value.simSelectionMode
        return isSimSelectedByMode(slotIndex, mode)
    }
}
