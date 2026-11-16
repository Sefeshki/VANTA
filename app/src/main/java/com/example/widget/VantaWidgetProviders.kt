package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.VantaApplication

enum class VantaWidgetKind {
    COMPACT,
    SIM_STATUS,
    STATUS_PANEL,
    OVERVIEW
}

abstract class BaseVantaWidgetProvider(
    private val widgetKind: VantaWidgetKind
) : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            refreshStateAndUpdate(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        refreshStateAndUpdate(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refreshStateAndUpdate(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        VantaWidgetUpdater.updateSingleWidget(context, widgetKind, appWidgetId, newOptions)
    }

    private fun refreshStateAndUpdate(context: Context) {
        val app = context.applicationContext as? VantaApplication
        app?.simManager?.refreshSims()
        app?.networkMonitor?.refresh()
        if (app != null && app.preferencesManager.telegramConfig.value.hasToken) {
            app.telegramBotManager.checkConnection()
        }
        VantaWidgetUpdater.updateAllWidgets(context, force = true)
    }
}

class VantaCompactWidgetProvider : BaseVantaWidgetProvider(VantaWidgetKind.COMPACT)
class VantaSimStatusWidgetProvider : BaseVantaWidgetProvider(VantaWidgetKind.SIM_STATUS)
class VantaStatusPanelWidgetProvider : BaseVantaWidgetProvider(VantaWidgetKind.STATUS_PANEL)
class VantaOverviewWidgetProvider : BaseVantaWidgetProvider(VantaWidgetKind.OVERVIEW)
