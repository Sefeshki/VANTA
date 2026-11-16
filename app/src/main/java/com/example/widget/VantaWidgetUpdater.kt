package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.VantaApplication
import com.example.data.model.SimCardInfo
import com.example.data.model.SimControlConfig
import com.example.telephony.SimManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WidgetSimSummary(
    val slotIndex: Int,
    val title: String,
    val shortStatus: String,
    val detailStatus: String,
    val isActive: Boolean,
    val isUnavailable: Boolean
)

data class WidgetStatusSnapshot(
    val isForwardingActive: Boolean,
    val isServiceRunning: Boolean,
    val isTelegramOnline: Boolean,
    val isTelegramPaired: Boolean,
    val isInternetOnline: Boolean,
    val isOtpProtectionEnabled: Boolean,
    val sims: List<SimCardInfo>,
    val simControl: SimControlConfig,
    val serviceUptimeText: String,
    val totalDelivered: Int,
    val pendingCount: Int,
    val lastDeliveryTime: Long
)

object VantaWidgetUpdater {

    private val ACTIVE_GREEN = Color.parseColor("#22C55E")
    private val ERROR_RED = Color.parseColor("#EF4444")
    private val MUTED_GRAY = Color.parseColor("#71717A")
    private val PRIMARY_WHITE = Color.parseColor("#FFFFFFFF")
    private val SECONDARY_TEXT = Color.parseColor("#A1A1AA")

    @Volatile
    private var lastPushedSnapshot: WidgetStatusSnapshot? = null

    private val providerMapping: List<Pair<VantaWidgetKind, Class<*>>> = listOf(
        VantaWidgetKind.COMPACT to VantaCompactWidgetProvider::class.java,
        VantaWidgetKind.SIM_STATUS to VantaSimStatusWidgetProvider::class.java,
        VantaWidgetKind.STATUS_PANEL to VantaStatusPanelWidgetProvider::class.java,
        VantaWidgetKind.OVERVIEW to VantaOverviewWidgetProvider::class.java
    )

    fun hasWidgets(context: Context): Boolean {
        return providerMapping.any { (_, cls) -> getWidgetIds(context, cls).isNotEmpty() }
    }

    fun getWidgetIds(context: Context, providerClass: Class<*>): IntArray {
        return try {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, providerClass))
        } catch (_: Exception) {
            IntArray(0)
        }
    }

    fun captureCurrentSnapshot(context: Context): WidgetStatusSnapshot {
        val app = context.applicationContext as? VantaApplication
        if (app == null) {
            return WidgetStatusSnapshot(
                isForwardingActive = false,
                isServiceRunning = false,
                isTelegramOnline = false,
                isTelegramPaired = false,
                isInternetOnline = false,
                isOtpProtectionEnabled = false,
                sims = emptyList(),
                simControl = SimControlConfig(),
                serviceUptimeText = "IDLE",
                totalDelivered = 0,
                pendingCount = 0,
                lastDeliveryTime = 0L
            )
        }

        val prefs = app.preferencesManager.preferences.value
        val telegramConfig = app.preferencesManager.telegramConfig.value
        val isServiceRunning = app.isServiceRunning.value
        val isInternet = app.networkMonitor.checkCurrentInternet()
        val isTelegramReachable = app.networkMonitor.isTelegramReachable.value
        val isBotConnected = app.telegramBotManager.isConnected.value

        val isForwardingActive = prefs.isForwardingEnabled && isServiceRunning
        val isTelegramLive = if (telegramConfig.hasToken) {
            (isBotConnected || isTelegramReachable) && isInternet
        } else {
            isTelegramReachable && isInternet
        }

        val uptimeText = if (isServiceRunning) {
            val startedElapsed = app.serviceStartedElapsedMs.value
            if (startedElapsed != null) {
                formatUptime((SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(0L))
            } else {
                "ACTIVE"
            }
        } else {
            "IDLE"
        }

        return WidgetStatusSnapshot(
            isForwardingActive = isForwardingActive,
            isServiceRunning = isServiceRunning,
            isTelegramOnline = isTelegramLive,
            isTelegramPaired = telegramConfig.hasToken && telegramConfig.authorizedChatId != null,
            isInternetOnline = isInternet,
            isOtpProtectionEnabled = prefs.isOtpProtectionEnabled,
            sims = app.simManager.detectedSims.value,
            simControl = prefs.simControl,
            serviceUptimeText = uptimeText,
            totalDelivered = app.preferencesManager.getTotalDeliveredCount(),
            pendingCount = app.deliveryQueueManager.pendingCount.value,
            lastDeliveryTime = app.preferencesManager.getLastDeliveryTime()
        )
    }

    fun updateAllWidgets(context: Context, force: Boolean = false) {
        val manager = try {
            AppWidgetManager.getInstance(context)
        } catch (_: Exception) {
            return
        }

        val activeByKind = providerMapping.mapNotNull { (kind, cls) ->
            val ids = getWidgetIds(context, cls)
            if (ids.isNotEmpty()) kind to ids else null
        }

        if (activeByKind.isEmpty()) {
            lastPushedSnapshot = null
            return
        }

        val snapshot = captureCurrentSnapshot(context)
        if (!force && snapshot == lastPushedSnapshot) {
            return
        }

        for ((kind, ids) in activeByKind) {
            for (appWidgetId in ids) {
                val options = try {
                    manager.getAppWidgetOptions(appWidgetId)
                } catch (_: Exception) {
                    Bundle.EMPTY
                }
                val views = buildRemoteViewsForKind(context, kind, snapshot, options)
                manager.updateAppWidget(appWidgetId, views)
            }
        }
        lastPushedSnapshot = snapshot
    }

    fun updateSingleWidget(
        context: Context,
        kind: VantaWidgetKind,
        appWidgetId: Int,
        options: Bundle
    ) {
        val snapshot = captureCurrentSnapshot(context)
        val manager = try {
            AppWidgetManager.getInstance(context)
        } catch (_: Exception) {
            return
        }
        val views = buildRemoteViewsForKind(context, kind, snapshot, options)
        manager.updateAppWidget(appWidgetId, views)
        lastPushedSnapshot = snapshot
    }

    fun buildRemoteViewsForKind(
        context: Context,
        kind: VantaWidgetKind,
        snapshot: WidgetStatusSnapshot,
        options: Bundle = Bundle.EMPTY
    ): RemoteViews = when (kind) {
        VantaWidgetKind.COMPACT -> buildCompactRemoteViews(context, snapshot, options)
        VantaWidgetKind.SIM_STATUS -> buildSimStatusRemoteViews(context, snapshot, options)
        VantaWidgetKind.STATUS_PANEL -> buildStatusPanelRemoteViews(context, snapshot, options)
        VantaWidgetKind.OVERVIEW -> buildOverviewRemoteViews(context, snapshot, options)
    }

    fun buildCompactRemoteViews(
        context: Context,
        snapshot: WidgetStatusSnapshot,
        options: Bundle = Bundle.EMPTY
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vanta_compact)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160)
        val isVeryNarrow = minWidthDp in 1 until 140

        views.setViewVisibility(R.id.widget_compact_sublabel, if (isVeryNarrow) View.GONE else View.VISIBLE)
        views.setTextViewText(
            R.id.widget_compact_sublabel,
            if (snapshot.isForwardingActive) "ACTIVE" else "IDLE"
        )
        views.setTextColor(
            R.id.widget_compact_sublabel,
            if (snapshot.isForwardingActive) ACTIVE_GREEN else MUTED_GRAY
        )

        views.setImageViewResource(
            R.id.widget_compact_internet_dot,
            if (snapshot.isInternetOnline) R.drawable.widget_dot_active else R.drawable.widget_dot_error
        )
        views.setTextViewText(
            R.id.widget_compact_internet_value,
            if (snapshot.isInternetOnline) "ON" else "OFF"
        )
        views.setTextColor(
            R.id.widget_compact_internet_value,
            if (snapshot.isInternetOnline) ACTIVE_GREEN else ERROR_RED
        )

        views.setImageViewResource(
            R.id.widget_compact_telegram_dot,
            if (snapshot.isTelegramOnline) R.drawable.widget_dot_active else R.drawable.widget_dot_error
        )
        views.setTextViewText(
            R.id.widget_compact_telegram_value,
            if (snapshot.isTelegramOnline) "ON" else "OFF"
        )
        views.setTextColor(
            R.id.widget_compact_telegram_value,
            if (snapshot.isTelegramOnline) ACTIVE_GREEN else ERROR_RED
        )

        attachLaunchIntent(context, views, R.id.widget_compact_root, 1001)
        return views
    }

    fun buildSimStatusRemoteViews(
        context: Context,
        snapshot: WidgetStatusSnapshot,
        options: Bundle = Bundle.EMPTY
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vanta_sim_status)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        val hideHeader = minHeightDp in 1 until 92
        views.setViewVisibility(R.id.widget_sim_header, if (hideHeader) View.GONE else View.VISIBLE)

        val sim1 = summarizeSimSlot(0, snapshot)
        val sim2 = summarizeSimSlot(1, snapshot)

        views.setImageViewResource(
            R.id.widget_sim1_dot,
            when {
                sim1.isActive -> R.drawable.widget_dot_active
                sim1.isUnavailable -> R.drawable.widget_dot_error
                else -> R.drawable.widget_dot_inactive
            }
        )
        views.setTextViewText(R.id.widget_sim1_title, sim1.title)
        views.setTextColor(R.id.widget_sim1_title, if (sim1.isActive) PRIMARY_WHITE else MUTED_GRAY)
        views.setTextViewText(R.id.widget_sim1_status, sim1.shortStatus)
        views.setTextColor(
            R.id.widget_sim1_status,
            when {
                sim1.isActive -> ACTIVE_GREEN
                sim1.isUnavailable -> ERROR_RED
                else -> MUTED_GRAY
            }
        )

        views.setImageViewResource(
            R.id.widget_sim2_dot,
            when {
                sim2.isActive -> R.drawable.widget_dot_active
                sim2.isUnavailable -> R.drawable.widget_dot_error
                else -> R.drawable.widget_dot_inactive
            }
        )
        views.setTextViewText(R.id.widget_sim2_title, sim2.title)
        views.setTextColor(R.id.widget_sim2_title, if (sim2.isActive) PRIMARY_WHITE else MUTED_GRAY)
        views.setTextViewText(R.id.widget_sim2_status, sim2.shortStatus)
        views.setTextColor(
            R.id.widget_sim2_status,
            when {
                sim2.isActive -> ACTIVE_GREEN
                sim2.isUnavailable -> ERROR_RED
                else -> MUTED_GRAY
            }
        )

        attachLaunchIntent(context, views, R.id.widget_sim_root, 1002)
        return views
    }

    fun buildStatusPanelRemoteViews(
        context: Context,
        snapshot: WidgetStatusSnapshot,
        options: Bundle = Bundle.EMPTY
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vanta_status_panel)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

        val isNarrow = minWidthDp in 1 until 215
        val isShort = minHeightDp in 1 until 88

        views.setViewVisibility(R.id.widget_panel_header, if (isShort) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_panel_divider_top, if (isShort) View.GONE else View.VISIBLE)
        views.setTextViewText(
            R.id.widget_panel_summary,
            if (snapshot.isForwardingActive) "BRIDGE ACTIVE" else "BRIDGE OFF"
        )
        views.setTextColor(
            R.id.widget_panel_summary,
            if (snapshot.isForwardingActive) ACTIVE_GREEN else MUTED_GRAY
        )

        views.setTextViewText(R.id.widget_panel_internet_label, if (isNarrow) "NET" else "INTERNET")
        views.setTextViewText(R.id.widget_panel_telegram_label, if (isNarrow) "TG" else "TELEGRAM")
        views.setTextViewText(R.id.widget_panel_service_label, if (isNarrow) "SVC" else "SERVICE")

        // Internet
        views.setImageViewResource(
            R.id.widget_panel_internet_dot,
            if (snapshot.isInternetOnline) R.drawable.widget_dot_active else R.drawable.widget_dot_error
        )
        views.setTextViewText(
            R.id.widget_panel_internet_value,
            if (snapshot.isInternetOnline) (if (isNarrow) "ON" else "ONLINE") else (if (isNarrow) "OFF" else "OFFLINE")
        )
        views.setTextColor(
            R.id.widget_panel_internet_value,
            if (snapshot.isInternetOnline) ACTIVE_GREEN else ERROR_RED
        )

        // Telegram
        views.setImageViewResource(
            R.id.widget_panel_telegram_dot,
            if (snapshot.isTelegramOnline) R.drawable.widget_dot_active else R.drawable.widget_dot_error
        )
        views.setTextViewText(
            R.id.widget_panel_telegram_value,
            if (snapshot.isTelegramOnline) (if (isNarrow) "ON" else "ONLINE") else (if (isNarrow) "OFF" else "OFFLINE")
        )
        views.setTextColor(
            R.id.widget_panel_telegram_value,
            if (snapshot.isTelegramOnline) ACTIVE_GREEN else ERROR_RED
        )

        // Service
        views.setImageViewResource(
            R.id.widget_panel_service_dot,
            if (snapshot.isServiceRunning) R.drawable.widget_dot_active else R.drawable.widget_dot_inactive
        )
        views.setTextViewText(
            R.id.widget_panel_service_value,
            if (snapshot.isServiceRunning) "ACTIVE" else "OFF"
        )
        views.setTextColor(
            R.id.widget_panel_service_value,
            if (snapshot.isServiceRunning) ACTIVE_GREEN else MUTED_GRAY
        )

        // SIM 1 & SIM 2
        val sim1 = summarizeSimSlot(0, snapshot)
        val sim2 = summarizeSimSlot(1, snapshot)

        views.setImageViewResource(
            R.id.widget_panel_sim1_dot,
            when {
                sim1.isActive -> R.drawable.widget_dot_active
                sim1.isUnavailable -> R.drawable.widget_dot_error
                else -> R.drawable.widget_dot_inactive
            }
        )
        views.setTextViewText(R.id.widget_panel_sim1_title, sim1.title)
        views.setTextColor(R.id.widget_panel_sim1_title, if (sim1.isActive) PRIMARY_WHITE else MUTED_GRAY)
        views.setTextViewText(R.id.widget_panel_sim1_status, sim1.shortStatus)
        views.setTextColor(R.id.widget_panel_sim1_status, if (sim1.isActive) SECONDARY_TEXT else MUTED_GRAY)

        views.setImageViewResource(
            R.id.widget_panel_sim2_dot,
            when {
                sim2.isActive -> R.drawable.widget_dot_active
                sim2.isUnavailable -> R.drawable.widget_dot_error
                else -> R.drawable.widget_dot_inactive
            }
        )
        views.setTextViewText(R.id.widget_panel_sim2_title, sim2.title)
        views.setTextColor(R.id.widget_panel_sim2_title, if (sim2.isActive) PRIMARY_WHITE else MUTED_GRAY)
        views.setTextViewText(R.id.widget_panel_sim2_status, sim2.shortStatus)
        views.setTextColor(R.id.widget_panel_sim2_status, if (sim2.isActive) SECONDARY_TEXT else MUTED_GRAY)

        attachLaunchIntent(context, views, R.id.widget_panel_root, 1003)
        return views
    }

    fun buildOverviewRemoteViews(
        context: Context,
        snapshot: WidgetStatusSnapshot,
        options: Bundle = Bundle.EMPTY
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vanta_overview)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250)
        val isCompactHeight = minHeightDp in 1 until 215
        val isVeryShortHeight = minHeightDp in 1 until 180
        val isNarrowWidth = minWidthDp in 1 until 125

        views.setTextViewText(
            R.id.widget_overview_uptime,
            if (snapshot.isServiceRunning) "UPTIME ${snapshot.serviceUptimeText}" else "SERVICE IDLE"
        )
        views.setTextColor(
            R.id.widget_overview_uptime,
            if (snapshot.isServiceRunning) ACTIVE_GREEN else MUTED_GRAY
        )

        views.setTextViewText(R.id.widget_overview_internet_label, if (isNarrowWidth) "NET" else "INTERNET")
        views.setTextViewText(R.id.widget_overview_telegram_label, if (isNarrowWidth) "TG" else "TELEGRAM")
        views.setTextViewText(R.id.widget_overview_service_label, if (isNarrowWidth) "SVC" else "SERVICE")

        // Health Rows
        views.setImageViewResource(
            R.id.widget_overview_internet_dot,
            if (snapshot.isInternetOnline) R.drawable.widget_dot_active else R.drawable.widget_dot_error
        )
        views.setTextViewText(
            R.id.widget_overview_internet_value,
            if (snapshot.isInternetOnline) (if (isNarrowWidth) "ON" else "ONLINE") else (if (isNarrowWidth) "OFF" else "OFFLINE")
        )
        views.setTextColor(R.id.widget_overview_internet_value, if (snapshot.isInternetOnline) ACTIVE_GREEN else ERROR_RED)

        views.setImageViewResource(
            R.id.widget_overview_telegram_dot,
            if (snapshot.isTelegramOnline) R.drawable.widget_dot_active else R.drawable.widget_dot_error
        )
        views.setTextViewText(
            R.id.widget_overview_telegram_value,
            when {
                !snapshot.isTelegramPaired -> if (isNarrowWidth) "UNPAIR" else "UNPAIRED"
                snapshot.isTelegramOnline -> if (isNarrowWidth) "ON" else "ONLINE"
                else -> if (isNarrowWidth) "OFF" else "OFFLINE"
            }
        )
        views.setTextColor(
            R.id.widget_overview_telegram_value,
            if (snapshot.isTelegramOnline && snapshot.isTelegramPaired) ACTIVE_GREEN else if (!snapshot.isTelegramPaired) MUTED_GRAY else ERROR_RED
        )

        views.setImageViewResource(
            R.id.widget_overview_service_dot,
            if (snapshot.isServiceRunning) R.drawable.widget_dot_active else R.drawable.widget_dot_inactive
        )
        views.setTextViewText(R.id.widget_overview_service_value, if (snapshot.isServiceRunning) "ACTIVE" else "OFF")
        views.setTextColor(R.id.widget_overview_service_value, if (snapshot.isServiceRunning) ACTIVE_GREEN else MUTED_GRAY)

        // SIM 1 & SIM 2 Detailed Cards
        val sim1 = summarizeSimSlot(0, snapshot)
        val sim2 = summarizeSimSlot(1, snapshot)

        views.setImageViewResource(
            R.id.widget_overview_sim1_dot,
            when {
                sim1.isActive -> R.drawable.widget_dot_active
                sim1.isUnavailable -> R.drawable.widget_dot_error
                else -> R.drawable.widget_dot_inactive
            }
        )
        views.setTextViewText(R.id.widget_overview_sim1_title, sim1.title)
        views.setTextColor(R.id.widget_overview_sim1_title, if (sim1.isActive) PRIMARY_WHITE else MUTED_GRAY)
        views.setTextViewText(R.id.widget_overview_sim1_badge, if (sim1.isActive) "ACTIVE" else if (sim1.isUnavailable) "UNAVAIL" else "OFF")
        views.setTextColor(
            R.id.widget_overview_sim1_badge,
            if (sim1.isActive) ACTIVE_GREEN else if (sim1.isUnavailable) ERROR_RED else MUTED_GRAY
        )
        views.setViewVisibility(R.id.widget_overview_sim1_detail, if (isVeryShortHeight) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_overview_sim1_detail, sim1.detailStatus)

        views.setImageViewResource(
            R.id.widget_overview_sim2_dot,
            when {
                sim2.isActive -> R.drawable.widget_dot_active
                sim2.isUnavailable -> R.drawable.widget_dot_error
                else -> R.drawable.widget_dot_inactive
            }
        )
        views.setTextViewText(R.id.widget_overview_sim2_title, sim2.title)
        views.setTextColor(R.id.widget_overview_sim2_title, if (sim2.isActive) PRIMARY_WHITE else MUTED_GRAY)
        views.setTextViewText(R.id.widget_overview_sim2_badge, if (sim2.isActive) "ACTIVE" else if (sim2.isUnavailable) "UNAVAIL" else "OFF")
        views.setTextColor(
            R.id.widget_overview_sim2_badge,
            if (sim2.isActive) ACTIVE_GREEN else if (sim2.isUnavailable) ERROR_RED else MUTED_GRAY
        )
        views.setViewVisibility(R.id.widget_overview_sim2_detail, if (isVeryShortHeight) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_overview_sim2_detail, sim2.detailStatus)

        // Footer
        views.setViewVisibility(R.id.widget_overview_footer_divider, if (isCompactHeight) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_overview_footer, if (isCompactHeight) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_overview_delivered, "SENT: ${snapshot.totalDelivered}")
        views.setTextViewText(R.id.widget_overview_pending, "QUEUE: ${snapshot.pendingCount}")
        val lastText = if (snapshot.lastDeliveryTime > 0L) {
            SimpleDateFormat("hh:mm a", Locale.US).format(Date(snapshot.lastDeliveryTime))
        } else {
            "--"
        }
        views.setTextViewText(R.id.widget_overview_last_sync, "LAST: $lastText")

        attachLaunchIntent(context, views, R.id.widget_overview_root, 1004)
        return views
    }

    fun summarizeSimSlot(slotIndex: Int, snapshot: WidgetStatusSnapshot): WidgetSimSummary {
        val simInfo = snapshot.sims.firstOrNull { it.slotIndex == slotIndex }
        val anyPerm = snapshot.sims.any { it.hasPermission }
        val settings = snapshot.simControl.forSlot(slotIndex)
        val hasPerm = (simInfo?.hasPermission ?: false) || anyPerm
        val isSingleSimAbsent = simInfo == null && anyPerm
        val isAvailable = simInfo?.isAvailable ?: false

        val slotTitle = when {
            !hasPerm -> "SIM ${slotIndex + 1} · Perm needed"
            isSingleSimAbsent -> "SIM ${slotIndex + 1} · Single-SIM"
            !isAvailable -> "SIM ${slotIndex + 1} · No SIM"
            simInfo?.carrierName == SimManager.CARRIER_UNAVAILABLE -> "SIM ${slotIndex + 1} · Carrier N/A"
            else -> "SIM ${slotIndex + 1} · ${simInfo?.carrierName ?: SimManager.CARRIER_UNAVAILABLE}"
        }

        val forwardingPart = when {
            !hasPerm -> "Grant Phone access"
            isSingleSimAbsent -> "No second slot"
            !isAvailable -> "Unavailable"
            !settings.masterEnabled -> "Disabled"
            !settings.smsForwardingEnabled -> "SMS Off"
            snapshot.isOtpProtectionEnabled || !settings.otpMessagesEnabled -> "SMS Only"
            else -> "SMS + OTP"
        }

        val detailStatus = when {
            !hasPerm -> "Grant READ_PHONE_STATE in Permissions"
            isSingleSimAbsent -> "Single-SIM hardware"
            !isAvailable -> "No active SIM inserted in slot ${slotIndex + 1}"
            !settings.masterEnabled -> "Master switch OFF in SIM Control"
            else -> buildList {
                if (simInfo != null && simInfo.subId >= 0) add("Sub #${simInfo.subId}")
                add(forwardingPart)
                if (settings.callLogForwardingEnabled || settings.incomingCallNotificationsEnabled) add("Calls")
                if (settings.smsSendingEnabled) add("Reply")
            }.joinToString(" · ")
        }

        val isActive = hasPerm && isAvailable && settings.masterEnabled
        val isUnavailable = hasPerm && !isAvailable
        return WidgetSimSummary(
            slotIndex = slotIndex,
            title = slotTitle,
            shortStatus = forwardingPart,
            detailStatus = detailStatus,
            isActive = isActive,
            isUnavailable = isUnavailable
        )
    }

    private fun formatUptime(elapsedMs: Long): String {
        val totalMinutes = (elapsedMs / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m"
            else -> "<1m"
        }
    }

    private fun attachLaunchIntent(context: Context, views: RemoteViews, rootId: Int, requestCode: Int) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(rootId, pendingIntent)
    }
}
