package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.VantaApplication
import com.example.core.EventLogger
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SimSelectionMode
import com.example.service.VantaForegroundService
import com.example.ui.components.CyberTerminal
import com.example.ui.components.GlowCard
import com.example.ui.components.OtpSuggestionDialog
import com.example.ui.components.SpotlightTutorialOverlay
import com.example.ui.components.StatusDot
import com.example.ui.components.VpnToggleSwitch
import com.example.ui.theme.LocalVantaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSims: () -> Unit,
    onNavigateToTelegram: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val colors = LocalVantaColors.current
    val context = LocalContext.current
    val app = context.applicationContext as VantaApplication

    val prefs by app.preferencesManager.preferences.collectAsState()
    val telegramConfig by app.preferencesManager.telegramConfig.collectAsState()
    val isInternet by app.networkMonitor.isInternetAvailable.collectAsState()
    val isTelegramReachable by app.networkMonitor.isTelegramReachable.collectAsState()
    val isServiceRunning by app.isServiceRunning.collectAsState()
    val pendingCount by app.deliveryQueueManager.pendingCount.collectAsState()
    val sims by app.simManager.detectedSims.collectAsState()
    val logs by EventLogger.logs.collectAsState()

    val isBotConnected by app.telegramBotManager.isConnected.collectAsState()

    var showTutorial by remember { mutableStateOf(!prefs.isTutorialCompleted) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                app.networkMonitor.refresh()
                app.simManager.refreshSims()
                if (telegramConfig.hasToken) {
                    app.telegramBotManager.checkConnection()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val smsGranted = results[Manifest.permission.RECEIVE_SMS] == true
        if (smsGranted) {
            EventLogger.log(LogCategory.SECURITY, "SMS permissions granted by user", LogLevel.SUCCESS)
            app.simManager.refreshSims()
        } else {
            EventLogger.log(LogCategory.SECURITY, "SMS permission denied", LogLevel.WARNING)
        }
    }

    LaunchedEffect(Unit) {
        app.networkMonitor.refresh()
        app.simManager.refreshSims()
        if (telegramConfig.hasToken) {
            app.telegramBotManager.checkConnection()
        }
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.vanta_icon),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "VANTA",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusDot(
                            isActive = prefs.isForwardingEnabled && isServiceRunning,
                            size = 8.dp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToPermissions,
                        modifier = Modifier.testTag("dashboard_nav_permissions")
                    ) {
                        val hasSms = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECEIVE_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Permissions",
                            tint = if (hasSms) colors.success else colors.warning
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("dashboard_nav_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                // 1. MAIN FORWARDING CONTROL (Large VPN-style switch)
                VpnToggleSwitch(
                    isActive = prefs.isForwardingEnabled && isServiceRunning,
                    onToggle = {
                        val hasSms = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECEIVE_SMS
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasSms) {
                            Toast.makeText(context, "We need SMS permission first so we can catch incoming texts.", Toast.LENGTH_SHORT).show()
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.READ_PHONE_STATE
                                )
                            )
                            return@VpnToggleSwitch
                        }

                        if (!telegramConfig.hasToken || telegramConfig.authorizedChatId == null) {
                            Toast.makeText(
                                context,
                                "Connect and pair your Telegram bot first so we know where to send texts!",
                                Toast.LENGTH_SHORT
                            ).show()
                            onNavigateToTelegram()
                            return@VpnToggleSwitch
                        }

                        val targetState = !prefs.isForwardingEnabled
                        app.preferencesManager.setForwardingEnabled(targetState)
                        if (targetState) {
                            VantaForegroundService.start(context)
                            EventLogger.log(
                                LogCategory.SYSTEM,
                                "Forwarding turned ON",
                                LogLevel.SUCCESS
                            )
                        } else {
                            VantaForegroundService.stop(context)
                            EventLogger.log(
                                LogCategory.SYSTEM,
                                "Forwarding turned OFF",
                                LogLevel.INFO
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                val isTelegramLive = if (telegramConfig.hasToken) {
                    (isBotConnected || isTelegramReachable) && isInternet
                } else {
                    isTelegramReachable && isInternet
                }

                // 2. STATUS PANEL (Real-time technical metrics)
                StatusPanel(
                    isForwardingActive = prefs.isForwardingEnabled,
                    isServiceRunning = isServiceRunning,
                    isInternetAvailable = isInternet,
                    isTelegramConnected = isTelegramLive,
                    pendingCount = pendingCount,
                    totalDelivered = app.preferencesManager.getTotalDeliveredCount(),
                    lastDelivery = app.preferencesManager.getLastDeliveryTime(),
                    simSelectionMode = prefs.simSelectionMode
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 3. TELEGRAM CONFIGURATION BUTTON
                GlowCard(
                    onClick = onNavigateToTelegram,
                    isHighlighted = !telegramConfig.hasToken,
                    modifier = Modifier.testTag("dashboard_telegram_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(colors.primaryDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = colors.primaryBright,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "TELEGRAM BOT",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = when {
                                        !telegramConfig.hasToken -> "Tap to connect your bot"
                                        telegramConfig.authorizedChatId == null -> "Waiting for pairing code"
                                        else -> "@${telegramConfig.botUsername ?: "bot"} • Ready & paired"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (telegramConfig.authorizedChatId != null) colors.success else colors.warning
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Configure Telegram",
                            tint = colors.primaryBright
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. SIM CONFIGURATION CARD
                GlowCard(
                    onClick = onNavigateToSims,
                    modifier = Modifier.testTag("dashboard_sim_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = colors.primaryBright,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SIM CARDS",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.textPrimary
                                )
                            }
                            Text(
                                text = "CHANGE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = colors.primaryBright
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val sim1Active = prefs.simSelectionMode == SimSelectionMode.ALL || prefs.simSelectionMode == SimSelectionMode.SIM1_ONLY
                        val sim2Active = prefs.simSelectionMode == SimSelectionMode.ALL || prefs.simSelectionMode == SimSelectionMode.SIM2_ONLY

                        val sim1Info = sims.firstOrNull { it.slotIndex == 0 }
                        val sim2Info = sims.firstOrNull { it.slotIndex == 1 }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SIM 1: ${sim1Info?.carrierName ?: "Primary"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                            Text(
                                text = if (sim1Active) "ACTIVE" else "OFF",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (sim1Active) colors.success else colors.textMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SIM 2: ${sim2Info?.carrierName ?: "Secondary"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                            Text(
                                text = if (sim2Active) "ACTIVE" else "OFF",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (sim2Active) colors.success else colors.textMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. STAY ALIVE & AUTO START CONTROLS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Stay Alive Card
                    GlowCard(modifier = Modifier.weight(1f)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "STAY ALIVE",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.textPrimary
                                )
                                Switch(
                                    checked = prefs.isStayAliveEnabled,
                                    onCheckedChange = { app.preferencesManager.setStayAliveEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.primaryBright,
                                        checkedTrackColor = colors.primaryDark,
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceElevated
                                    ),
                                    modifier = Modifier.testTag("stay_alive_switch")
                                )
                            }
                            Text(
                                text = "Keeps VANTA awake in the background so Android doesn't put it to sleep when you lock the phone.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = colors.textMuted
                            )
                        }
                    }

                    // Auto Start Card
                    GlowCard(modifier = Modifier.weight(1f)) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AUTO START",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.textPrimary
                                )
                                Switch(
                                    checked = prefs.isAutoStartEnabled,
                                    onCheckedChange = { app.preferencesManager.setAutoStartEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.primaryBright,
                                        checkedTrackColor = colors.primaryDark,
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceElevated
                                    ),
                                    modifier = Modifier.testTag("auto_start_switch")
                                )
                            }
                            Text(
                                text = "Automatically restarts forwarding if your phone reboots while you're away.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = colors.textMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 6. CYBER TERMINAL
                CyberTerminal(
                    logs = logs,
                    onClear = { EventLogger.clear() }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Spotlight Tutorial
            SpotlightTutorialOverlay(
                isVisible = showTutorial,
                onFinish = {
                    showTutorial = false
                    app.preferencesManager.setTutorialCompleted(true)
                }
            )

            // First-Launch OTP Suggestion Popup
            OtpSuggestionDialog(
                isVisible = !showTutorial && !prefs.hasSeenOtpSuggestion,
                onEnable = {
                    app.preferencesManager.setOtpProtectionEnabled(true)
                    app.preferencesManager.setSeenOtpSuggestion(true)
                    Toast.makeText(context, "Code protection enabled", Toast.LENGTH_SHORT).show()
                },
                onDismiss = {
                    app.preferencesManager.setSeenOtpSuggestion(true)
                }
            )
        }
    }
}

@Composable
private fun StatusPanel(
    isForwardingActive: Boolean,
    isServiceRunning: Boolean,
    isInternetAvailable: Boolean,
    isTelegramConnected: Boolean,
    pendingCount: Int,
    totalDelivered: Int,
    lastDelivery: Long,
    simSelectionMode: SimSelectionMode
) {
    val colors = LocalVantaColors.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    GlowCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE STATUS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = colors.primaryBright
                )
                Text(
                    text = "LISTENING: ${simSelectionMode.label.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.textMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Grid of telemetry indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryItem(
                    label = "FORWARDING",
                    value = if (isForwardingActive && isServiceRunning) "ACTIVE" else "OFF",
                    isActive = isForwardingActive && isServiceRunning
                )
                TelemetryItem(
                    label = "SERVICE",
                    value = if (isServiceRunning) "ACTIVE" else "OFF",
                    isActive = isServiceRunning
                )
                TelemetryItem(
                    label = "TELEGRAM",
                    value = if (isTelegramConnected) "ONLINE" else "OFFLINE",
                    isActive = isTelegramConnected
                )
                TelemetryItem(
                    label = "INTERNET",
                    value = if (isInternetAvailable) "ONLINE" else "OFFLINE",
                    isActive = isInternetAvailable
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "LAST TEXT SENT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = colors.textMuted
                    )
                    Text(
                        text = if (lastDelivery > 0) timeFormat.format(Date(lastDelivery)) else "None yet",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.textPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "IN LINE / SENT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        ),
                        color = colors.textMuted
                    )
                    Text(
                        text = if (pendingCount == 0) "$totalDelivered texts sent" else "$pendingCount waiting • $totalDelivered sent",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (pendingCount > 0) colors.warning else colors.success
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryItem(
    label: String,
    value: String,
    isActive: Boolean
) {
    val colors = LocalVantaColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(isActive = isActive, size = 6.dp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = if (isActive) colors.success else colors.textMuted
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            ),
            color = colors.textMuted
        )
    }
}
