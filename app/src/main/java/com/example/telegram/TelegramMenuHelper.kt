package com.example.telegram

import com.example.core.PreferencesManager
import com.example.data.model.SimCardInfo

enum class BotMenuPanel(val id: String) {
    OVERVIEW("overview"),
    SIMS("sims"),
    SERVICE("service"),
    FORWARDING("forwarding"),
    CONTROLS("controls"),
    HELP("help");

    companion object {
        fun fromId(raw: String?): BotMenuPanel =
            entries.firstOrNull { it.id == raw } ?: OVERVIEW
    }
}

object TelegramMenuHelper {

    fun buildPanelContent(
        panel: BotMenuPanel,
        preferencesManager: PreferencesManager,
        sims: List<SimCardInfo>,
        isServiceRunning: Boolean,
        serviceStartedElapsedMs: Long?,
        isInternetOnline: Boolean,
        isBotConnected: Boolean,
        isPollingActive: Boolean,
        pendingQueueCount: Int,
        customNote: String? = null
    ): Pair<FormattedMessage, List<List<InlineButton>>> {
        val prefs = preferencesManager.preferences.value
        val totalDelivered = preferencesManager.getTotalDeliveredCount()

        val message = when (panel) {
            BotMenuPanel.OVERVIEW -> TelegramEntityHelper.formatOverviewPanel(
                isForwardingEnabled = prefs.isForwardingEnabled,
                isServiceRunning = isServiceRunning,
                isInternetOnline = isInternetOnline,
                isBotPollingActive = isPollingActive,
                sims = sims,
                simControl = prefs.simControl,
                isOtpProtectionEnabled = prefs.isOtpProtectionEnabled,
                totalDelivered = totalDelivered,
                pendingQueueCount = pendingQueueCount,
                customNote = customNote
            )
            BotMenuPanel.SIMS -> TelegramEntityHelper.formatSimInfoPanel(
                sims = sims,
                simControl = prefs.simControl
            )
            BotMenuPanel.SERVICE -> TelegramEntityHelper.formatServiceStatusPanel(
                isServiceRunning = isServiceRunning,
                serviceUptimeText = formatUptime(isServiceRunning, serviceStartedElapsedMs),
                isInternetOnline = isInternetOnline,
                isBotConnected = isBotConnected,
                isPollingActive = isPollingActive
            )
            BotMenuPanel.FORWARDING -> TelegramEntityHelper.formatForwardingStatusPanel(
                isForwardingEnabled = prefs.isForwardingEnabled,
                isServiceRunning = isServiceRunning,
                isOtpProtectionEnabled = prefs.isOtpProtectionEnabled,
                simControl = prefs.simControl,
                totalDelivered = totalDelivered,
                pendingQueueCount = pendingQueueCount
            )
            BotMenuPanel.CONTROLS -> TelegramEntityHelper.formatControlsPanel(
                isForwardingEnabled = prefs.isForwardingEnabled,
                isServiceRunning = isServiceRunning,
                isOtpProtectionEnabled = prefs.isOtpProtectionEnabled,
                sims = sims,
                simControl = prefs.simControl
            )
            BotMenuPanel.HELP -> TelegramEntityHelper.formatHelpMessage()
        }

        val keyboard = buildPanelKeyboard(
            panel = panel,
            isForwardingEnabled = prefs.isForwardingEnabled,
            isOtpProtectionEnabled = prefs.isOtpProtectionEnabled,
            sims = sims,
            simControl = prefs.simControl
        )
        return message to keyboard
    }

    fun buildPanelKeyboard(
        panel: BotMenuPanel,
        isForwardingEnabled: Boolean,
        isOtpProtectionEnabled: Boolean,
        sims: List<SimCardInfo>,
        simControl: com.example.data.model.SimControlConfig
    ): List<List<InlineButton>> {
        return when (panel) {
            BotMenuPanel.OVERVIEW -> listOf(
                listOf(
                    InlineButton(text = "📊 Overview", callbackData = "menu:overview"),
                    InlineButton(text = "📶 SIM Info", callbackData = "menu:sims")
                ),
                listOf(
                    InlineButton(text = "⚙️ Service", callbackData = "menu:service"),
                    InlineButton(text = "📡 Forwarding", callbackData = "menu:forwarding")
                ),
                listOf(
                    InlineButton(text = "🎛️ Controls", callbackData = "menu:controls"),
                    InlineButton(text = "ℹ️ About", callbackData = "menu:help")
                ),
                listOf(
                    InlineButton(text = "🔄 Refresh", callbackData = "menu:refresh:overview")
                )
            )

            BotMenuPanel.SIMS -> buildList {
                val simButtons = buildAvailableSimToggleButtons(sims, simControl, origin = panel.id)
                if (simButtons.isNotEmpty()) {
                    add(simButtons)
                }
                add(
                    listOf(
                        InlineButton(text = "🔄 Refresh", callbackData = "menu:refresh:sims"),
                        InlineButton(text = "⬅️ Overview", callbackData = "menu:overview")
                    )
                )
            }

            BotMenuPanel.SERVICE -> listOf(
                listOf(
                    InlineButton(
                        text = if (isForwardingEnabled) "⏹ Stop Service" else "▶️ Start Service",
                        callbackData = "act:svc_toggle:service"
                    ),
                    InlineButton(text = "🔄 Refresh", callbackData = "menu:refresh:service")
                ),
                listOf(
                    InlineButton(text = "📡 Forwarding", callbackData = "menu:forwarding"),
                    InlineButton(text = "⬅️ Overview", callbackData = "menu:overview")
                )
            )

            BotMenuPanel.FORWARDING -> listOf(
                listOf(
                    InlineButton(
                        text = if (isForwardingEnabled) "⏸ Pause Bridge" else "▶️ Enable Bridge",
                        callbackData = "act:fwd_toggle:forwarding"
                    ),
                    InlineButton(
                        text = if (isOtpProtectionEnabled) "🛡️ OTP Shield: ON" else "🛡️ OTP Shield: OFF",
                        callbackData = "act:otp_toggle:forwarding"
                    )
                ),
                listOf(
                    InlineButton(text = "🔄 Refresh", callbackData = "menu:refresh:forwarding"),
                    InlineButton(text = "⬅️ Overview", callbackData = "menu:overview")
                )
            )

            BotMenuPanel.CONTROLS -> buildList {
                add(
                    listOf(
                        InlineButton(
                            text = if (isForwardingEnabled) "⏹ Disable Bridge" else "▶️ Enable Bridge",
                            callbackData = "act:fwd_toggle:controls"
                        )
                    )
                )
                val simButtons = buildAvailableSimToggleButtons(sims, simControl, origin = panel.id)
                if (simButtons.isNotEmpty()) {
                    add(simButtons)
                }
                add(
                    listOf(
                        InlineButton(
                            text = if (isOtpProtectionEnabled) "🛡️ OTP Protection: ON" else "🛡️ OTP Protection: OFF",
                            callbackData = "act:otp_toggle:controls"
                        )
                    )
                )
                add(
                    listOf(
                        InlineButton(text = "📶 SIM Info", callbackData = "menu:sims"),
                        InlineButton(text = "⬅️ Overview", callbackData = "menu:overview")
                    )
                )
            }

            BotMenuPanel.HELP -> listOf(
                listOf(
                    InlineButton(text = "📶 SIM Info", callbackData = "menu:sims"),
                    InlineButton(text = "🎛️ Controls", callbackData = "menu:controls")
                ),
                listOf(
                    InlineButton(text = "⬅️ Overview", callbackData = "menu:overview")
                )
            )
        }
    }

    private fun buildAvailableSimToggleButtons(
        sims: List<SimCardInfo>,
        simControl: com.example.data.model.SimControlConfig,
        origin: String
    ): List<InlineButton> {
        val buttons = mutableListOf<InlineButton>()
        for (slot in 0..1) {
            val sim = sims.firstOrNull { it.slotIndex == slot }
            if (sim != null && sim.isAvailable && sim.hasPermission) {
                val enabled = simControl.forSlot(slot).masterEnabled
                val label = "SIM ${slot + 1}: ${if (enabled) "ON" else "OFF"}"
                buttons.add(InlineButton(text = label, callbackData = "act:sim_toggle:$slot:$origin"))
            }
        }
        return buttons
    }

    private fun formatUptime(isServiceRunning: Boolean, startedElapsedMs: Long?): String {
        if (!isServiceRunning) return "IDLE"
        if (startedElapsedMs == null) return "ACTIVE"
        val elapsedMs = (android.os.SystemClock.elapsedRealtime() - startedElapsedMs).coerceAtLeast(0L)
        val totalMinutes = (elapsedMs / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m"
            else -> "<1m"
        }
    }
}
