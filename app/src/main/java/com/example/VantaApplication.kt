package com.example

import android.app.Application
import com.example.core.EventLogger
import com.example.core.NetworkMonitor
import com.example.core.PreferencesManager
import com.example.data.model.BridgeStatus
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.queue.DeliveryQueueManager
import com.example.service.VantaForegroundService
import com.example.sms.SmsProcessor
import com.example.telephony.SimManager
import com.example.telegram.TelegramBotManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VantaApplication : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set
    lateinit var telegramBotManager: TelegramBotManager
        private set
    lateinit var simManager: SimManager
        private set
    lateinit var deliveryQueueManager: DeliveryQueueManager
        private set
    lateinit var smsProcessor: SmsProcessor
        private set

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        instance = this

        EventLogger.log(LogCategory.SYSTEM, "Starting VANTA...", LogLevel.INFO)

        preferencesManager = PreferencesManager(this)
        networkMonitor = NetworkMonitor(this)
        telegramBotManager = TelegramBotManager(preferencesManager)
        simManager = SimManager(this, preferencesManager)
        deliveryQueueManager = DeliveryQueueManager(telegramBotManager, preferencesManager)
        smsProcessor = SmsProcessor(preferencesManager, simManager, deliveryQueueManager)

        // Wire providers to TelegramBotManager
        telegramBotManager.simInfoProvider = { simManager.detectedSims.value }
        telegramBotManager.isForwardingActiveProvider = {
            preferencesManager.preferences.value.isForwardingEnabled && _isServiceRunning.value
        }

        EventLogger.log(LogCategory.SYSTEM, "VANTA is ready", LogLevel.SUCCESS)

        // If forwarding was active, resume foreground service
        if (preferencesManager.preferences.value.isForwardingEnabled) {
            VantaForegroundService.start(this)
        }
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun getBridgeStatus(): BridgeStatus {
        val prefs = preferencesManager.preferences.value
        val isService = _isServiceRunning.value
        val isTelegram = telegramBotManager.isConnected.value
        val isInternet = networkMonitor.isInternetAvailable.value
        val lastDelivery = preferencesManager.getLastDeliveryTime()
        val totalDelivered = preferencesManager.getTotalDeliveredCount()
        val pending = deliveryQueueManager.pendingCount.value

        return BridgeStatus(
            isForwardingActive = prefs.isForwardingEnabled && isService,
            isServiceRunning = isService,
            isTelegramConnected = isTelegram,
            isInternetConnected = isInternet,
            lastSuccessfulDelivery = if (lastDelivery > 0) lastDelivery else null,
            lastConnectionAttempt = System.currentTimeMillis(),
            pendingCount = pending,
            totalDeliveredCount = totalDelivered
        )
    }

    companion object {
        lateinit var instance: VantaApplication
            private set
    }
}
