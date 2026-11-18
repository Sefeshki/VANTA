package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.core.EventLogger
import com.example.core.SecurityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import com.example.ui.components.EventHorizonBackground
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.vantaSwitchColors
import com.example.ui.theme.LocalVantaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyProtectionScreen(
    onNavigateBack: () -> Unit
) {
    val colors = LocalVantaColors.current
    val context = LocalContext.current
    val app = context.applicationContext as VantaApplication

    val prefs by app.preferencesManager.preferences.collectAsState()
    val telegramConfig by app.preferencesManager.telegramConfig.collectAsState()
    val hasHardwareKeystore = remember { SecurityManager.isUsingHardwareKeystore() }
    val anyOtpForwardingEnabled = prefs.simControl.isAnyOtpForwardingEnabled()

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
                            text = "PRIVACY & PROTECTION",
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
                            modifier = Modifier.testTag("privacy_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
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
                // 1. SENSITIVE OTP SHIELD
                Text(
                    text = "OTP & AUTHENTICATION SHIELD",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = colors.primaryBright
                )
                Spacer(modifier = Modifier.height(10.dp))

                OtpProtectionControlCard(
                    isProtectionEnabled = prefs.isOtpProtectionEnabled,
                    isOtpForwardingAvailable = anyOtpForwardingEnabled,
                    onToggle = { enabled ->
                        if (anyOtpForwardingEnabled) {
                            app.preferencesManager.setOtpProtectionEnabled(enabled)
                        }
                    },
                    switchTestTag = "privacy_otp_switch"
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 2. HARDWARE KEYSTORE & ENCRYPTION
                Text(
                    text = "DEVICE KEYSTORE INTEGRITY",
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.VpnKey,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (hasHardwareKeystore) "Hardware-Backed Security" else "Local AES-GCM Encryption",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = if (hasHardwareKeystore) {
                                        "AES-256-GCM authenticated encryption"
                                    } else {
                                        "AES-256-GCM software fallback (Keystore unavailable)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasHardwareKeystore) colors.textMuted else colors.warning
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (hasHardwareKeystore) {
                                    "Your Telegram bot tokens are encrypted with master keys stored in Android Keystore hardware and excluded from cloud backups."
                                } else {
                                    "Android Keystore is unavailable in this environment, so local AES-256-GCM fallback encryption is active and excluded from cloud backups."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. LOG PURGE & CREDENTIAL DESTRUCTION
                Text(
                    text = "DATA SANITIZATION",
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
                            text = "Clear Local Activity Logs",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Wipes all event logs and audit history from device memory immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                EventLogger.clear()
                                Toast.makeText(context, "All logs cleared", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("privacy_clear_logs_btn")
                        ) {
                            Icon(imageVector = Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CLEAR LOGS", fontWeight = FontWeight.SemiBold)
                        }

                        if (telegramConfig.hasToken) {
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Purge Bot Credentials",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Disconnects Telegram, revokes session, and deletes the encrypted bot token from Android Keystore.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    app.telegramBotManager.disconnect()
                                    Toast.makeText(context, "Credentials wiped clean", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                                border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("privacy_purge_credentials_btn")
                            ) {
                                Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("WIPE SAVED CREDENTIALS", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun OtpProtectionControlCard(
    isProtectionEnabled: Boolean,
    isOtpForwardingAvailable: Boolean,
    onToggle: (Boolean) -> Unit,
    switchTestTag: String
) {
    val colors = LocalVantaColors.current
    val isLocked = !isOtpForwardingAvailable

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("otp_protection_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .blur(if (isLocked) 3.dp else 0.dp)
                        .alpha(if (isLocked) 0.36f else 1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = if (isProtectionEnabled && !isLocked) Color.White else colors.textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Shield Login & OTP Codes",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Centralized shield that blocks verification codes, banking OTPs, and 2FA tokens across all SIMs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = isProtectionEnabled,
                        enabled = !isLocked,
                        onCheckedChange = { if (!isLocked) onToggle(it) },
                        colors = vantaSwitchColors(),
                        modifier = Modifier.testTag(switchTestTag)
                    )
                }

                if (isLocked) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent().changes.forEach { it.consume() }
                                    }
                                }
                            }
                            .testTag("otp_protection_locked_overlay")
                    )
                }
            }

            if (isLocked) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Enable OTP forwarding on at least one SIM to use this feature.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("otp_protection_disabled_hint")
                )
            }
        }
    }
}
