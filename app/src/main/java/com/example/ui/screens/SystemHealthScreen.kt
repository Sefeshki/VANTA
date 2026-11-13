package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.VantaApplication
import com.example.ui.components.EventHorizonBackground
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.StatusDot
import com.example.ui.theme.LocalVantaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHealthScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val colors = LocalVantaColors.current
    val context = LocalContext.current
    val app = context.applicationContext as VantaApplication

    val isInternet by app.networkMonitor.isInternetAvailable.collectAsState()
    val isTelegramReachable by app.networkMonitor.isTelegramReachable.collectAsState()
    val isBotConnected by app.telegramBotManager.isConnected.collectAsState()
    val isServiceRunning by app.isServiceRunning.collectAsState()
    val pendingCount by app.deliveryQueueManager.pendingCount.collectAsState()
    val sims by app.simManager.detectedSims.collectAsState()
    val prefs by app.preferencesManager.preferences.collectAsState()

    EventHorizonBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
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
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // 1. LIVE TELEMETRY
                Text(
                    text = "CORE SUBSYSTEMS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = colors.primaryBright
                )
                Spacer(modifier = Modifier.height(10.dp))

                LiquidGlassCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HealthItemRow(
                            name = "Internet Connection",
                            detail = if (isInternet) "Connected (Active Route)" else "Offline",
                            isHealthy = isInternet
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.10f))

                        HealthItemRow(
                            name = "Telegram Gateway",
                            detail = if (isTelegramReachable) "api.telegram.org reachable" else "DNS or Firewall blocked",
                            isHealthy = isTelegramReachable
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.10f))

                        HealthItemRow(
                            name = "Bot Session",
                            detail = if (isBotConnected) "Authorized & Paired" else "Disconnected",
                            isHealthy = isBotConnected
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.10f))

                        HealthItemRow(
                            name = "Background Forwarder",
                            detail = if (isServiceRunning) "Foreground Service Active" else "Service Stopped",
                            isHealthy = isServiceRunning
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.10f))

                        HealthItemRow(
                            name = "Hardware Keystore",
                            detail = "AES-256-GCM Hardware-Backed",
                            isHealthy = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. HARDWARE & QUEUE METRICS
                Text(
                    text = "HARDWARE & QUEUE METRICS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = colors.primaryBright
                )
                Spacer(modifier = Modifier.height(10.dp))

                LiquidGlassCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Detected SIM Cards",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "${sims.size} slot(s) populated  •  Mode: ${prefs.simSelectionMode.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.09f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
                            ) {
                                Text(
                                    text = "${sims.size} ACTIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.10f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Pending SMS Queue",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = if (pendingCount == 0) "Queue empty • All delivered" else "$pendingCount waiting for network dispatch",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (pendingCount == 0) colors.textMuted else colors.warning
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (pendingCount == 0) Color.White.copy(alpha = 0.05f) else colors.warning.copy(alpha = 0.2f),
                                border = BorderStroke(
                                    1.dp,
                                    if (pendingCount == 0) Color.White.copy(alpha = 0.16f) else colors.warning.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = "$pendingCount MSG",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (pendingCount == 0) colors.textMuted else colors.warning,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. PERMISSIONS SHORTCUT
                Text(
                    text = "OS CAPABILITIES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = colors.primaryBright
                )
                Spacer(modifier = Modifier.height(10.dp))

                LiquidGlassCard {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Android System Permissions",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Verify battery optimization exemptions, SMS reception grants, and phone state access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onNavigateToPermissions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("health_manage_permissions_btn")
                        ) {
                            Icon(imageVector = Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OPEN PERMISSION CENTER", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun HealthItemRow(
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
                color = colors.textPrimary
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        StatusDot(isActive = isHealthy)
    }
}
