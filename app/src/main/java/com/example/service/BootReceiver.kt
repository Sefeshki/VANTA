package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.VantaApplication
import com.example.core.EventLogger
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        EventLogger.log(LogCategory.SYSTEM, "Device reboot completed signal received", LogLevel.INFO)

        val app = context.applicationContext as? VantaApplication ?: return
        val prefs = app.preferencesManager.preferences.value
        val telegramConfig = app.preferencesManager.telegramConfig.value

        // Only recover if user had explicitly enabled auto-start AND forwarding was previously active
        if (prefs.isAutoStartEnabled && prefs.isForwardingEnabled && telegramConfig.hasToken) {
            EventLogger.log(
                LogCategory.SERVICE,
                "Auto-Start criteria met: restoring VANTA Foreground Service after boot",
                LogLevel.SUCCESS
            )
            VantaForegroundService.start(context)
        } else {
            EventLogger.log(
                LogCategory.SERVICE,
                "Auto-Start bypassed: (AutoStart=${prefs.isAutoStartEnabled}, Forwarding=${prefs.isForwardingEnabled}, BotConfigured=${telegramConfig.hasToken})",
                LogLevel.INFO
            )
        }
    }
}
