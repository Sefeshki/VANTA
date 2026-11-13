package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.VantaApplication

/**
 * Home screen AppWidgetProvider rendering a minimalist live telemetry status card
 * mirroring the in-app Live Status panel.
 */
class VantaStatusWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            refreshNetworkAndUpdate(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        refreshNetworkAndUpdate(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refreshNetworkAndUpdate(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        VantaWidgetUpdater.updateSingleWidget(context, appWidgetId, newOptions)
    }

    private fun refreshNetworkAndUpdate(context: Context) {
        VantaWidgetUpdater.updateAllWidgets(context, force = true)
        val app = context.applicationContext as? VantaApplication
        app?.networkMonitor?.refresh()
        if (app != null && app.preferencesManager.telegramConfig.value.hasToken) {
            app.telegramBotManager.checkConnection()
        }
    }
}
