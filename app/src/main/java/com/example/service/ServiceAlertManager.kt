package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.core.EventLogger
import com.example.core.PermissionHelper
import com.example.core.PreferencesManager
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SimCardInfo
import com.example.data.model.SimFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Event-driven notification manager for VANTA operational health, connectivity,
 * SIM status, permission changes, and service recovery.
 *
 * Designed to avoid spam: tracks active alert states and only emits recovery notices
 * after a corresponding problem was actually reported.
 */
class ServiceAlertManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile
    private var hasObservedLiveTelegram = false

    @Volatile
    private var telegramUnreachableJob: Job? = null

    private var knownAvailableSlots: Set<Int>? = null

    init {
        ensureNotificationChannel()
    }

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val channel = NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "VANTA Service Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts for Telegram connectivity, SIM changes, permission status, and service recovery"
                }
                nm.createNotificationChannel(channel)
            } catch (_: Exception) {
            }
        }
    }

    fun evaluateTelegramConnectivity(
        hasToken: Boolean,
        authorizedChatId: Long?,
        isForwardingEnabled: Boolean,
        isTelegramLive: Boolean
    ) {
        if (!hasToken || authorizedChatId == null || !isForwardingEnabled) {
            telegramUnreachableJob?.cancel()
            telegramUnreachableJob = null
            if (!hasToken || authorizedChatId == null) {
                hasObservedLiveTelegram = false
                preferencesManager.setTelegramAlertActive(false)
            }
            return
        }

        if (isTelegramLive) {
            telegramUnreachableJob?.cancel()
            telegramUnreachableJob = null
            hasObservedLiveTelegram = true
            if (preferencesManager.isTelegramAlertActive()) {
                preferencesManager.setTelegramAlertActive(false)
                postAlert(
                    notificationId = NOTIF_TELEGRAM_ID,
                    title = "Telegram Connectivity Restored",
                    body = "VANTA reconnected to the Telegram Bot API and is ready to forward.",
                    destination = MainActivity.DEST_TELEGRAM_CONFIG
                )
                EventLogger.log(LogCategory.TELEGRAM, "Telegram connectivity restored", LogLevel.SUCCESS)
            }
        } else if (hasObservedLiveTelegram && !preferencesManager.isTelegramAlertActive()) {
            if (telegramUnreachableJob?.isActive == true) return
            telegramUnreachableJob = scope.launch {
                delay(TELEGRAM_OUTAGE_DEBOUNCE_MS)
                if (!preferencesManager.isTelegramAlertActive()) {
                    preferencesManager.setTelegramAlertActive(true)
                    postAlert(
                        notificationId = NOTIF_TELEGRAM_ID,
                        title = "Telegram Unreachable",
                        body = "Unable to reach Telegram Bot API. Queued SMS will retry automatically.",
                        destination = MainActivity.DEST_TELEGRAM_CONFIG
                    )
                    EventLogger.log(LogCategory.TELEGRAM, "Telegram unreachable alert posted", LogLevel.WARNING)
                }
            }
        }
    }

    fun evaluateSims(sims: List<SimCardInfo>) {
        if (sims.isEmpty() || sims.none { it.hasPermission }) return

        val currentAvailable = sims.filter { it.isAvailable }.map { it.slotIndex }.toSet()
        val previous = knownAvailableSlots
        if (previous == null) {
            knownAvailableSlots = currentAvailable
            for (slot in currentAvailable) {
                if (preferencesManager.isSimAlertActive(slot)) {
                    preferencesManager.setSimAlertActive(slot, false)
                    val sim = sims.firstOrNull { it.slotIndex == slot }
                    val carrierNote = sim?.carrierName?.let { " ($it)" } ?: ""
                    postAlert(
                        notificationId = NOTIF_SIM_BASE_ID + slot,
                        title = "SIM Connection Restored",
                        body = "SIM ${slot + 1}$carrierNote is active again.",
                        destination = MainActivity.DEST_SIM_CONTROL
                    )
                }
            }
            return
        }

        val disconnectedSlots = previous - currentAvailable
        val restoredSlots = currentAvailable - previous
        knownAvailableSlots = currentAvailable

        for (slot in disconnectedSlots) {
            if (!preferencesManager.isSimAlertActive(slot)) {
                preferencesManager.setSimAlertActive(slot, true)
                postAlert(
                    notificationId = NOTIF_SIM_BASE_ID + slot,
                    title = "SIM Disconnected",
                    body = "SIM ${slot + 1} became unavailable or was removed. Check SIM Control.",
                    destination = MainActivity.DEST_SIM_CONTROL
                )
                EventLogger.log(LogCategory.SIM, "Alerted SIM disconnect: SIM ${slot + 1}", LogLevel.WARNING)
            }
        }

        for (slot in restoredSlots) {
            if (preferencesManager.isSimAlertActive(slot)) {
                preferencesManager.setSimAlertActive(slot, false)
                val sim = sims.firstOrNull { it.slotIndex == slot }
                val carrierNote = sim?.carrierName?.let { " ($it)" } ?: ""
                postAlert(
                    notificationId = NOTIF_SIM_BASE_ID + slot,
                    title = "SIM Connection Restored",
                    body = "SIM ${slot + 1}$carrierNote is active again.",
                    destination = MainActivity.DEST_SIM_CONTROL
                )
                EventLogger.log(LogCategory.SIM, "Alerted SIM restoration: SIM ${slot + 1}", LogLevel.SUCCESS)
            }
        }
    }

    fun evaluatePermissions() {
        val prefs = preferencesManager.preferences.value
        val required = linkedSetOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (prefs.simControl.sim1.isFeatureEffective(SimFeature.SMS_SENDING) ||
            prefs.simControl.sim2.isFeatureEffective(SimFeature.SMS_SENDING)
        ) {
            required.add(Manifest.permission.SEND_SMS)
        }
        if (prefs.simControl.sim1.isFeatureEffective(SimFeature.CALL_LOG_FORWARDING) ||
            prefs.simControl.sim2.isFeatureEffective(SimFeature.CALL_LOG_FORWARDING)
        ) {
            required.add(Manifest.permission.READ_CALL_LOG)
        }

        val currentlyGranted = required.filter { PermissionHelper.isGranted(context, it) }.toSet()
        val knownGranted = preferencesManager.getKnownGrantedPermissions()

        // Record newly granted permissions
        val updatedKnown = knownGranted + currentlyGranted
        if (updatedKnown != knownGranted) {
            preferencesManager.setKnownGrantedPermissions(updatedKnown)
        }

        val revoked = (updatedKnown intersect required) - currentlyGranted
        if (revoked.isNotEmpty()) {
            if (!preferencesManager.isPermissionAlertActive()) {
                val revokedLabels = revoked.joinToString(", ") { friendlyPermissionName(it) }
                preferencesManager.setPermissionAlertActive(true)
                postAlert(
                    notificationId = NOTIF_PERMISSION_ID,
                    title = "Required Permission Revoked",
                    body = "Access missing: $revokedLabels. Tap to restore in Permissions.",
                    destination = MainActivity.DEST_PERMISSION_CENTER
                )
                EventLogger.log(
                    LogCategory.SECURITY,
                    "Permission revoked alert triggered: $revokedLabels",
                    LogLevel.WARNING
                )
            }
        } else if (preferencesManager.isPermissionAlertActive() && required.intersect(updatedKnown).all { it in currentlyGranted }) {
            preferencesManager.setPermissionAlertActive(false)
            postAlert(
                notificationId = NOTIF_PERMISSION_ID,
                title = "Permissions Restored",
                body = "Required VANTA permissions are granted again.",
                destination = MainActivity.DEST_PERMISSION_CENTER
            )
            EventLogger.log(LogCategory.SECURITY, "Required permissions restored", LogLevel.SUCCESS)
        }
    }

    fun onServiceStarted() {
        if (preferencesManager.wasUnexpectedServiceStopAlerted()) {
            preferencesManager.setUnexpectedServiceStopAlerted(false)
            postAlert(
                notificationId = NOTIF_SERVICE_ID,
                title = "VANTA Service Recovered",
                body = "Background forwarding service is active again.",
                destination = MainActivity.DEST_DASHBOARD
            )
            EventLogger.log(LogCategory.SERVICE, "Service recovery notification posted", LogLevel.SUCCESS)
        }
    }

    fun onServiceUnexpectedlyStopped() {
        val prefs = preferencesManager.preferences.value
        if (!prefs.isForwardingEnabled) return
        if (!preferencesManager.wasUnexpectedServiceStopAlerted()) {
            preferencesManager.setUnexpectedServiceStopAlerted(true)
            postAlert(
                notificationId = NOTIF_SERVICE_ID,
                title = "VANTA Service Stopped",
                body = "Forwarding service stopped unexpectedly while enabled. Tap to inspect.",
                destination = MainActivity.DEST_DASHBOARD
            )
            EventLogger.log(LogCategory.SERVICE, "Unexpected service stop notification posted", LogLevel.WARNING)
        }
    }

    fun onForwardingInterrupted(reason: String, destination: String = MainActivity.DEST_SYSTEM_HEALTH) {
        if (!preferencesManager.isForwardingAlertActive()) {
            preferencesManager.setForwardingAlertActive(true)
            postAlert(
                notificationId = NOTIF_FORWARDING_ID,
                title = "SMS Forwarding Stopped",
                body = reason,
                destination = destination
            )
            EventLogger.log(LogCategory.SMS_DELIVERY, "Forwarding interruption alert: $reason", LogLevel.WARNING)
        }
    }

    fun onForwardingRecovered() {
        if (preferencesManager.isForwardingAlertActive()) {
            preferencesManager.setForwardingAlertActive(false)
            postAlert(
                notificationId = NOTIF_FORWARDING_ID,
                title = "SMS Forwarding Resumed",
                body = "Message forwarding pipeline is operating normally again.",
                destination = MainActivity.DEST_DASHBOARD
            )
            EventLogger.log(LogCategory.SMS_DELIVERY, "Forwarding recovery notification posted", LogLevel.SUCCESS)
        }
    }

    private fun postAlert(
        notificationId: Int,
        title: String,
        body: String,
        destination: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return
        }

        try {
            ensureNotificationChannel()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_NAV_DESTINATION, destination)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(notificationId, notification)
        } catch (_: Exception) {
        }
    }

    private fun formatSimAvailabilitySummary(sims: List<SimCardInfo>): String {
        val active = sims.filter { it.isAvailable }
        return if (active.isEmpty()) {
            "No active SIM cards detected."
        } else {
            active.joinToString(" • ") { "SIM ${it.slotIndex + 1}: ${it.carrierName}" }
        }
    }

    private fun friendlyPermissionName(permission: String): String = when (permission) {
        Manifest.permission.RECEIVE_SMS -> "SMS Reception"
        Manifest.permission.READ_PHONE_STATE -> "Phone / SIM State"
        Manifest.permission.SEND_SMS -> "SMS Sending"
        Manifest.permission.READ_CALL_LOG -> "Call Logs"
        else -> permission.substringAfterLast('.')
    }

    companion object {
        const val ALERT_CHANNEL_ID = "vanta_service_alerts_channel"
        const val NOTIF_TELEGRAM_ID = 2101
        const val NOTIF_SIM_BASE_ID = 2102
        const val NOTIF_PERMISSION_ID = 2104
        const val NOTIF_SERVICE_ID = 2105
        const val NOTIF_FORWARDING_ID = 2106
        private const val TELEGRAM_OUTAGE_DEBOUNCE_MS = 5_000L
    }
}
