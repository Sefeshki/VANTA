package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.VantaApplication
import com.example.core.EventLogger
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.widget.VantaWidgetUpdater

class VantaForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isPaused = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        EventLogger.log(LogCategory.SERVICE, "VANTA Foreground Service created", LogLevel.INFO)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when (action) {
            ACTION_STOP -> {
                EventLogger.log(LogCategory.SERVICE, "Stopping VANTA Foreground Service by user action", LogLevel.INFO)
                val app = applicationContext as? VantaApplication
                app?.preferencesManager?.setForwardingEnabled(false)
                app?.callMonitorManager?.stopMonitoring()
                app?.setServiceRunning(false)
                VantaWidgetUpdater.updateAllWidgets(this, force = true)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                isPaused = true
                EventLogger.log(LogCategory.SERVICE, "SMS Forwarding paused", LogLevel.WARNING)
                val app = applicationContext as? VantaApplication
                app?.preferencesManager?.setForwardingEnabled(false)
                app?.callMonitorManager?.stopMonitoring()
                updateNotification()
                VantaWidgetUpdater.updateAllWidgets(this)
                return START_STICKY
            }
            ACTION_RESUME -> {
                isPaused = false
                EventLogger.log(LogCategory.SERVICE, "SMS Forwarding resumed", LogLevel.SUCCESS)
                val app = applicationContext as? VantaApplication
                app?.preferencesManager?.setForwardingEnabled(true)
                app?.callMonitorManager?.startMonitoring()
                updateNotification()
                VantaWidgetUpdater.updateAllWidgets(this)
                return START_STICKY
            }
            else -> {
                isPaused = false
                startInForeground()
            }
        }

        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        val app = applicationContext as? VantaApplication
        app?.setServiceRunning(true)
        app?.callMonitorManager?.startMonitoring()
        VantaWidgetUpdater.updateAllWidgets(this)
        EventLogger.log(LogCategory.SERVICE, "VANTA Foreground Service ACTIVE and running", LogLevel.SUCCESS)
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val app = applicationContext as? VantaApplication
        val simSummary = app?.preferencesManager?.preferences?.value?.simControl?.summaryLabel() ?: "SIM 1 + SIM 2"
        val telegramConfig = app?.preferencesManager?.telegramConfig?.value

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (isPaused) {
            val resumeIntent = Intent(this, VantaForegroundService::class.java).apply { action = ACTION_RESUME }
            val resumePendingIntent = PendingIntent.getService(
                this,
                1,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Resume",
                resumePendingIntent
            ).build()
        } else {
            val pauseIntent = Intent(this, VantaForegroundService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(
                this,
                2,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                pausePendingIntent
            ).build()
        }

        val stopIntent = Intent(this, VantaForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            3,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop",
            stopPendingIntent
        ).build()

        val title = if (isPaused) "VANTA (Paused)" else "VANTA"
        val content = if (isPaused) {
            "Forwarding is paused. Tap resume whenever you're ready."
        } else {
            val botStatus = if (telegramConfig?.authorizedChatId != null) "Bot connected" else "Needs pairing"
            "Bridge active • $simSummary • $botStatus"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(!isPaused)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VANTA Bridge Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps VANTA running in the background so you never miss an SMS or call"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VANTA:ForegroundServiceWakeLock"
            ).apply {
                acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val app = applicationContext as? VantaApplication
        app?.callMonitorManager?.stopMonitoring()
        app?.setServiceRunning(false)
        EventLogger.log(LogCategory.SERVICE, "VANTA Foreground Service terminated", LogLevel.WARNING)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "vanta_bridge_channel"

        const val ACTION_START = "com.example.vanta.action.START"
        const val ACTION_STOP = "com.example.vanta.action.STOP"
        const val ACTION_PAUSE = "com.example.vanta.action.PAUSE"
        const val ACTION_RESUME = "com.example.vanta.action.RESUME"

        fun start(context: Context) {
            val intent = Intent(context, VantaForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VantaForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
