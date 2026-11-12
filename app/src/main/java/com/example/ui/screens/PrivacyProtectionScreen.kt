package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.core.EventLogger
import com.example.ui.components.LiquidGlassCard
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

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
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

            LiquidGlassCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (prefs.isOtpProtectionEnabled) colors.primaryBright else colors.textMuted,
                                modifier = Modifier.size(24.dp)
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
                                    text = "Inspects incoming SMS content and suppresses forwarding of verification codes, banking OTPs, and two-factor tokens.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Switch(
                            checked = prefs.isOtpProtectionEnabled,
                            onCheckedChange = { app.preferencesManager.setOtpProtectionEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.primaryBright,
                                checkedTrackColor = colors.primaryDark,
                                uncheckedThumbColor = colors.textMuted,
                                uncheckedTrackColor = colors.surfaceElevated
                            ),
                            modifier = Modifier.testTag("privacy_otp_switch")
                        )
                    }
                }
            }

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
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = colors.primaryBright,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Hardware-Backed Security",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary
                            )
                            Text(
                                text = "AES-256-GCM authenticated encryption",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.primaryDark.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Your Telegram bot tokens and encryption secrets are stored directly in Android Keystore with master keys protected in device hardware. Tokens never touch unencrypted storage.",
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                        border = BorderStroke(1.dp, colors.border),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("privacy_clear_logs_btn")
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CLEAR LOGS")
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
                            modifier = Modifier.fillMaxWidth().testTag("privacy_purge_credentials_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("WIPE SAVED CREDENTIALS")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
