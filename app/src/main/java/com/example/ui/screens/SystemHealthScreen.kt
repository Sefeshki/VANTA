package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.VantaApplication
import com.example.core.EventLogger
import com.example.ui.components.CyberTerminal
import com.example.ui.components.EventHorizonBackground
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.StatusDot
import com.example.ui.theme.LocalVantaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHealthScreen(
    onNavigateBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onNavigateToPermissions: () -> Unit = {}
) {
    val colors = LocalVantaColors.current
    val context = LocalContext.current
    val app = context.applicationContext as VantaApplication

    val isInternet by app.networkMonitor.isInternetAvailable.collectAsState()
    val isTelegramReachable by app.networkMonitor.isTelegramReachable.collectAsState()
    val isBotConnected by app.telegramBotManager.isConnected.collectAsState()
    val isServiceRunning by app.isServiceRunning.collectAsState()
    val sims by app.simManager.detectedSims.collectAsState()
    val prefs by app.preferencesManager.preferences.collectAsState()
    val logs by EventLogger.logs.collectAsState()

    val hasSimPerm = sims.any { it.hasPermission }
    val activeSimsCount = sims.count { it.isAvailable }
    val simDetail = when {
        !hasSimPerm -> "Permission required"
        activeSimsCount == 0 -> "No active SIM detected"
        else -> "$activeSimsCount/${sims.size} active · ${prefs.simControl.summaryLabel()}"
    }

    EventHorizonBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0.dp),
                    modifier = Modifier.padding(top = 6.dp),
                    title = {
                        Text(
                            text = "SYSTEM HEALTH",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = colors.textPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("health_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                app.networkMonitor.refresh()
                                app.simManager.refreshSims()
                                app.telegramBotManager.checkConnection()
                            },
                            modifier = Modifier.testTag("health_refresh_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "CORE SYSTEMS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = colors.primaryBright,
                    modifier = Modifier.testTag("health_core_systems_header")
                )
                Spacer(modifier = Modifier.height(8.dp))

                LiquidGlassCard(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("health_core_systems_card")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CoreSystemRow(
                            name = "Internet Connection",
                            detail = if (isInternet) "Connected (Active Route)" else "Offline",
                            isHealthy = isInternet
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color.White.copy(alpha = 0.08f))

                        CoreSystemRow(
                            name = "Telegram Gateway",
                            detail = if (isTelegramReachable) "api.telegram.org reachable" else "Unreachable",
                            isHealthy = isTelegramReachable
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color.White.copy(alpha = 0.08f))

                        CoreSystemRow(
                            name = "Bot Session",
                            detail = if (isBotConnected) "Authorized & Paired" else "Disconnected",
                            isHealthy = isBotConnected
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color.White.copy(alpha = 0.08f))

                        CoreSystemRow(
                            name = "Background Forwarder",
                            detail = if (isServiceRunning) "Foreground Service Active" else "Service Stopped",
                            isHealthy = isServiceRunning
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color.White.copy(alpha = 0.08f))

                        CoreSystemRow(
                            name = "Hardware Keystore",
                            detail = "AES-256-GCM Hardware-Backed",
                            isHealthy = true
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color.White.copy(alpha = 0.08f))

                        CoreSystemRow(
                            name = "SIM Hardware",
                            detail = simDetail,
                            isHealthy = hasSimPerm && activeSimsCount > 0
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                CyberTerminal(
                    logs = logs,
                    onClear = { EventLogger.clear() },
                    collapsible = true,
                    initiallyExpanded = false
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CoreSystemRow(
    name: String,
    detail: String,
    isHealthy: Boolean
) {
    val colors = LocalVantaColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        StatusDot(isActive = isHealthy, size = 7.dp)
    }
}
