package com.example

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.core.EventLogger
import com.example.core.NetworkMonitor
import com.example.core.PreferencesManager
import com.example.data.model.BridgeStatus
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.queue.DeliveryQueueManager
import com.example.service.ServiceAlertManager
import com.example.service.VantaForegroundService
import com.example.sms.SmsProcessor
import com.example.telegram.TelegramBotManager
import com.example.telephony.AndroidSmsGateway
import com.example.telephony.CallMonitorManager
import com.example.telephony.ContactResolver
import com.example.telephony.SimManager
import com.example.telephony.SmsGateway
import com.example.widget.VantaWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class VantaApplication : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set
    lateinit var telegramBotManager: TelegramBotManager
        private set
    lateinit var simManager: SimManager
        private set
    lateinit var contactResolver: ContactResolver
        private set
    lateinit var smsGateway: SmsGateway
        private set
    lateinit var serviceAlertManager: ServiceAlertManager
        private set
    lateinit var deliveryQueueManager: DeliveryQueueManager
        private set
    lateinit var smsProcessor: SmsProcessor
        private set
    lateinit var callMonitorManager: CallMonitorManager
        private set

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _serviceStartedElapsedMs = MutableStateFlow<Long?>(null)
    val serviceStartedElapsedMs: StateFlow<Long?> = _serviceStartedElapsedMs.asStateFlow()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        EventLogger.log(LogCategory.SYSTEM, "Starting VANTA...", LogLevel.INFO)

        preferencesManager = PreferencesManager(this)
        networkMonitor = NetworkMonitor(this)
        telegramBotManager = TelegramBotManager(preferencesManager)
        simManager = SimManager(this, preferencesManager)
        contactResolver = ContactResolver(this)
        smsGateway = AndroidSmsGateway(this, simManager, preferencesManager)
        serviceAlertManager = ServiceAlertManager(this, preferencesManager)
        deliveryQueueManager = DeliveryQueueManager(
            telegramBotManager = telegramBotManager,
            preferencesManager = preferencesManager,
            simManager = simManager,
            contactResolver = contactResolver,
            onDeliverySuccess = {
                serviceAlertManager.onForwardingRecovered()
                VantaWidgetUpdater.updateAllWidgets(this)
            },
            onDeliveryDropped = { errorMsg ->
                serviceAlertManager.onForwardingInterrupted(
                    "SMS delivery failed after retries: $errorMsg",
                    MainActivity.DEST_SYSTEM_HEALTH
                )
                VantaWidgetUpdater.updateAllWidgets(this)
            }
        )
        smsProcessor = SmsProcessor(preferencesManager, simManager, deliveryQueueManager)
        callMonitorManager = CallMonitorManager(
            context = this,
            preferencesManager = preferencesManager,
            simManager = simManager,
            contactResolver = contactResolver,
            telegramBotManager = telegramBotManager
        )

        telegramBotManager.simInfoProvider = { simManager.detectedSims.value }
        telegramBotManager.isForwardingActiveProvider = {
            preferencesManager.preferences.value.isForwardingEnabled && _isServiceRunning.value
        }
        telegramBotManager.isServiceRunningProvider = { _isServiceRunning.value }
        telegramBotManager.serviceStartedElapsedProvider = { _serviceStartedElapsedMs.value }
        telegramBotManager.isInternetAvailableProvider = { networkMonitor.checkCurrentInternet() }
        telegramBotManager.pendingQueueCountProvider = { deliveryQueueManager.pendingCount.value }
        telegramBotManager.onSetForwardingEnabled = { enabled ->
            preferencesManager.setForwardingEnabled(enabled)
            if (enabled) {
                VantaForegroundService.start(this)
                EventLogger.log(LogCategory.SYSTEM, "Forwarding turned ON via Telegram", LogLevel.SUCCESS)
            } else {
                VantaForegroundService.stop(this)
                EventLogger.log(LogCategory.SYSTEM, "Forwarding turned OFF via Telegram", LogLevel.INFO)
            }
            VantaWidgetUpdater.updateAllWidgets(this, force = true)
        }
        telegramBotManager.onServiceStopRequested = {
            VantaForegroundService.stop(this)
            VantaWidgetUpdater.updateAllWidgets(this, force = true)
        }
        telegramBotManager.onToggleSimMaster = { slotIndex, enabled ->
            preferencesManager.setSimMasterEnabled(slotIndex, enabled)
            VantaWidgetUpdater.updateAllWidgets(this, force = true)
            EventLogger.log(
                LogCategory.SIM,
                "SIM ${slotIndex + 1} master switch turned ${if (enabled) "ON" else "OFF"} via Telegram",
                LogLevel.INFO
            )
        }
        telegramBotManager.simRefreshProvider = {
            simManager.refreshSims()
            simManager.detectedSims.value
        }
        telegramBotManager.hasSendSmsPermissionProvider = {
            ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        }
        telegramBotManager.smsGateway = smsGateway

        appScope.launch {
            networkMonitor.isInternetAvailable.collect { available ->
                if (available && preferencesManager.telegramConfig.value.hasToken) {
                    telegramBotManager.checkConnection()
                }
            }
        }

        // Evaluate SIM disconnects/restorations and update home screen widgets when SIM state changes
        appScope.launch {
            simManager.detectedSims.collect { sims ->
                serviceAlertManager.evaluateSims(sims)
                if (VantaWidgetUpdater.hasWidgets(this@VantaApplication)) {
                    VantaWidgetUpdater.updateAllWidgets(this@VantaApplication)
                }
            }
        }

        // Reactively evaluate Telegram connectivity alerts and update home screen widgets
        appScope.launch {
            val bridgeStateFlow = combine(
                isServiceRunning,
                preferencesManager.preferences,
                preferencesManager.telegramConfig
            ) { running, prefs, tgConfig ->
                Triple(prefs.isForwardingEnabled && running, prefs, tgConfig)
            }
            val networkStateFlow = combine(
                networkMonitor.isInternetAvailable,
                networkMonitor.isTelegramReachable,
                telegramBotManager.isConnected,
                deliveryQueueManager.pendingCount
            ) { internet, tgReachable, botConnected, pending ->
                listOf(internet, tgReachable, botConnected, pending)
            }
            combine(bridgeStateFlow, networkStateFlow) { bridge, network ->
                bridge to network
            }
                .distinctUntilChanged()
                .collect { (bridge, network) ->
                    val tgConfig = bridge.third
                    val prefs = bridge.second
                    val isInternet = network[0] as Boolean
                    val isTgReachable = network[1] as Boolean
                    val isBotConnected = network[2] as Boolean
                    val isTelegramLive = if (tgConfig.hasToken) {
                        (isBotConnected || isTgReachable) && isInternet
                    } else {
                        isTgReachable && isInternet
                    }

                    serviceAlertManager.evaluateTelegramConnectivity(
                        hasToken = tgConfig.hasToken,
                        authorizedChatId = tgConfig.authorizedChatId,
                        isForwardingEnabled = prefs.isForwardingEnabled,
                        isTelegramLive = isTelegramLive
                    )
                    serviceAlertManager.evaluatePermissions()

                    if (VantaWidgetUpdater.hasWidgets(this@VantaApplication)) {
                        VantaWidgetUpdater.updateAllWidgets(this@VantaApplication)
                    }
                }
        }

        EventLogger.log(LogCategory.SYSTEM, "VANTA is ready", LogLevel.SUCCESS)

        if (preferencesManager.preferences.value.isForwardingEnabled) {
            VantaForegroundService.start(this)
        }
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (running) {
            if (_serviceStartedElapsedMs.value == null) {
                _serviceStartedElapsedMs.value = SystemClock.elapsedRealtime()
            }
        } else {
            _serviceStartedElapsedMs.value = null
        }
        VantaWidgetUpdater.updateAllWidgets(this)
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

    override fun onTerminate() {
        telegramBotManager.stopPolling()
        networkMonitor.stop()
        appScope.cancel()
        super.onTerminate()
    }

    companion object {
        lateinit var instance: VantaApplication
            private set
    }
}

