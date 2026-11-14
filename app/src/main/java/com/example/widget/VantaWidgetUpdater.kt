package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.VantaApplication

/**
 * Snapshot of live bridge metrics displayed on the home screen widget.
 */
data class WidgetStatusSnapshot(
    val isForwardingActive: Boolean,
    val isTelegramOnline: Boolean,
    val isInternetOnline: Boolean,
    val simLabel: String
)

/**
 * Efficient, deduplicated helper for updating all pinned VANTA Live Status widgets.
 * Performs no work when no widget instances are installed on the launcher.
 */
object VantaWidgetUpdater {

    private val ACTIVE_GREEN = Color.parseColor("#22C55E")
    private val MUTED_GRAY = Color.parseColor("#71717A")

    @Volatile
    private var lastPushedSnapshot: WidgetStatusSnapshot? = null

    fun hasWidgets(context: Context): Boolean {
        return getWidgetIds(context).isNotEmpty()
    }

    fun getWidgetIds(context: Context): IntArray {
        return try {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, VantaStatusWidgetProvider::class.java))
        } catch (_: Exception) {
            IntArray(0)
        }
    }

    fun captureCurrentSnapshot(context: Context): WidgetStatusSnapshot {
        val app = context.applicationContext as? VantaApplication
        if (app == null) {
            return WidgetStatusSnapshot(
                isForwardingActive = false,
                isTelegramOnline = false,
                isInternetOnline = false,
                simLabel = "VANTA"
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

        return WidgetStatusSnapshot(
            isForwardingActive = isForwardingActive,
            isTelegramOnline = isTelegramLive,
            isInternetOnline = isInternet,
            simLabel = prefs.simControl.summaryLabel()
        )
    }

    fun updateAllWidgets(context: Context, force: Boolean = false) {
        val ids = getWidgetIds(context)
        if (ids.isEmpty()) {
            lastPushedSnapshot = null
            return
        }

        val snapshot = captureCurrentSnapshot(context)
        if (!force && snapshot == lastPushedSnapshot) {
            return
        }

        val manager = AppWidgetManager.getInstance(context)
        for (appWidgetId in ids) {
            val options = try {
                manager.getAppWidgetOptions(appWidgetId)
            } catch (_: Exception) {
                Bundle.EMPTY
            }
            val views = buildRemoteViews(context, snapshot, options)
            manager.updateAppWidget(appWidgetId, views)
        }
        lastPushedSnapshot = snapshot
    }

    fun updateSingleWidget(context: Context, appWidgetId: Int, options: Bundle) {
        val snapshot = captureCurrentSnapshot(context)
        val manager = AppWidgetManager.getInstance(context)
        val views = buildRemoteViews(context, snapshot, options)
        manager.updateAppWidget(appWidgetId, views)
        lastPushedSnapshot = snapshot
    }

    fun buildRemoteViews(
        context: Context,
        snapshot: WidgetStatusSnapshot,
        options: Bundle = Bundle.EMPTY
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_vanta_status)

        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80)

        val isNarrow = minWidthDp in 1 until 200
        val isUltraShort = minHeightDp in 1 until 62

        // Adapt header & labels smoothly across resized widget dimensions
        if (isUltraShort) {
            views.setViewVisibility(R.id.widget_header_row, View.GONE)
            views.setViewVisibility(R.id.widget_divider, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_header_row, View.VISIBLE)
            views.setViewVisibility(R.id.widget_divider, View.VISIBLE)
            views.setViewVisibility(R.id.widget_subtitle, if (isNarrow) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.widget_subtitle, snapshot.simLabel)
        }

        views.setTextViewText(R.id.widget_forwarding_label, if (isNarrow) "FWD" else "FORWARDING")
        views.setTextViewText(R.id.widget_telegram_label, if (isNarrow) "TG" else "TELEGRAM")
        views.setTextViewText(R.id.widget_internet_label, if (isNarrow) "NET" else "INTERNET")

        // 1. Forwarding Indicator
        views.setImageViewResource(
            R.id.widget_forwarding_dot,
            if (snapshot.isForwardingActive) R.drawable.widget_dot_active else R.drawable.widget_dot_inactive
        )
        views.setTextViewText(
            R.id.widget_forwarding_value,
            if (snapshot.isForwardingActive) "ACTIVE" else "OFF"
        )
        views.setTextColor(
            R.id.widget_forwarding_value,
            if (snapshot.isForwardingActive) ACTIVE_GREEN else MUTED_GRAY
        )

        // 2. Telegram Indicator
        views.setImageViewResource(
            R.id.widget_telegram_dot,
            if (snapshot.isTelegramOnline) R.drawable.widget_dot_active else R.drawable.widget_dot_inactive
        )
        views.setTextViewText(
            R.id.widget_telegram_value,
            if (snapshot.isTelegramOnline) "ONLINE" else "OFFLINE"
        )
        views.setTextColor(
            R.id.widget_telegram_value,
            if (snapshot.isTelegramOnline) ACTIVE_GREEN else MUTED_GRAY
        )

        // 3. Internet Indicator
        views.setImageViewResource(
            R.id.widget_internet_dot,
            if (snapshot.isInternetOnline) R.drawable.widget_dot_active else R.drawable.widget_dot_inactive
        )
        views.setTextViewText(
            R.id.widget_internet_value,
            if (snapshot.isInternetOnline) "ONLINE" else "OFFLINE"
        )
        views.setTextColor(
            R.id.widget_internet_value,
            if (snapshot.isInternetOnline) ACTIVE_GREEN else MUTED_GRAY
        )

        // Tap anywhere on widget to launch VANTA
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        return views
    }
}
