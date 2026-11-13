package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import com.example.VantaApplication
import com.example.ui.components.EventHorizonBackground
import com.example.ui.components.GlowCard
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.StatusDot
import com.example.ui.components.TelegramBotTutorialDialog
import com.example.ui.theme.LocalVantaColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramConfigScreen(
    onNavigateBack: () -> Unit
) {
    val colors = LocalVantaColors.current
    val context = LocalContext.current
    val app = context.applicationContext as VantaApplication
    val scope = rememberCoroutineScope()

    val config by app.preferencesManager.telegramConfig.collectAsState()
    val isConnected by app.telegramBotManager.isConnected.collectAsState()

    var showBotTutorial by remember { mutableStateOf(false) }

    var tokenInput by remember { mutableStateOf("") }
    var isTokenVisible by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    var manualChatIdInput by remember { mutableStateOf("") }
    var showManualPairing by remember { mutableStateOf(false) }

    EventHorizonBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "CONNECT TELEGRAM",
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
                            modifier = Modifier.testTag("telegram_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            // Header / Status Card
            GlowCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.primaryDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = colors.primaryBright,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (config.hasToken) "@${config.botUsername ?: "bot"}" else "NO BOT CONNECTED YET",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = colors.textPrimary
                            )
                            Text(
                                text = when {
                                    !config.hasToken -> "Paste your token below to link your bot"
                                    config.authorizedChatId == null -> "Almost ready • Send the pairing code"
                                    else -> "Paired to your account (Chat ID: ${config.authorizedChatId})"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (config.authorizedChatId != null) colors.success else colors.warning
                            )
                        }
                    }

                    StatusDot(
                        isActive = config.authorizedChatId != null && isConnected,
                        size = 10.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tutorial trigger: "Don't have a bot yet? Learn how to create one."
            LiquidGlassCard(
                onClick = { showBotTutorial = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_bot_tutorial_btn")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.primaryDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = colors.primaryBright,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Don't have a bot yet?",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Learn how to create one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.primaryBright
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Step 1: Bot Token Entry
            Text(
                text = "1. PASTE YOUR BOT TOKEN",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = colors.primaryBright
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = if (config.hasToken && tokenInput.isEmpty()) config.tokenMasked else tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text("Bot Token from @BotFather") },
                placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRstuVWXyz") },
                visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                        Icon(
                            imageVector = if (isTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle token visibility",
                            tint = colors.textMuted
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primaryBright,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bot_token_input")
            )

            if (validationError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val tokenToVerify = if (tokenInput.isNotBlank()) {
                            tokenInput.trim()
                        } else if (config.hasToken) {
                            app.preferencesManager.getDecryptedBotToken()
                        } else {
                            ""
                        }
                        if (tokenToVerify.isEmpty()) {
                            Toast.makeText(context, "Please paste your bot token", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isValidating = true
                        validationError = null
                        scope.launch {
                            val res = app.telegramBotManager.verifyAndSaveToken(tokenToVerify)
                            isValidating = false
                            if (res.isSuccess) {
                                tokenInput = ""
                                Toast.makeText(context, "Bot verified successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                validationError = res.exceptionOrNull()?.message ?: "Verification failed"
                            }
                        }
                    },
                    enabled = !isValidating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("verify_token_btn")
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (config.hasToken) "UPDATE TOKEN" else "VERIFY TOKEN",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val botFatherIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BotFather"))
                            context.startActivity(botFatherIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Telegram app or browser not found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.primaryBright.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primaryBright),
                    modifier = Modifier.height(46.dp)
                ) {
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("@BotFather", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step 2: Secure Pairing
            Text(
                text = "2. PAIR YOUR ACCOUNT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = colors.primaryBright
            )
            Spacer(modifier = Modifier.height(10.dp))

            GlowCard(isHighlighted = config.authorizedChatId == null && config.hasToken) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (config.authorizedChatId != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = colors.success,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "YOU'RE PAIRED UP!",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = colors.success
                                )
                                Text(
                                    text = "Paired Chat ID: ${config.authorizedChatId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Only your Telegram account will receive forwarded texts and status updates. Any random messages from strangers are politely ignored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    } else if (!config.hasToken) {
                        Text(
                            text = "First verify your bot token above, then we'll generate your pairing code.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted
                        )
                    } else {
                        Text(
                            text = "Now pair your Telegram account so VANTA sends your texts directly to you and nobody else.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (config.activePairingCode != null && System.currentTimeMillis() <= config.pairingCodeExpiry) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xB307070F))
                                    .border(
                                        1.dp,
                                        colors.primaryBright.copy(alpha = 0.35f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "PAIRING CODE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.primaryBright
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = config.activePairingCode ?: "",
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 6.sp
                                    ),
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Open @${config.botUsername ?: "your bot"} in Telegram and send:\n/start ${config.activePairingCode}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 18.sp
                                    ),
                                    color = colors.primaryBright
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    app.telegramBotManager.generatePairingCode()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryBright, contentColor = Color.Black),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("generate_pair_code_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("GET A CODE")
                            }

                            if (config.botUsername != null) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val uri = Uri.parse("https://t.me/${config.botUsername}")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open Telegram", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("OPEN IN TELEGRAM")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manual Chat ID Pairing Toggle (Fallback)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showManualPairing = !showManualPairing },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Advanced: Enter Chat ID manually",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
                Text(
                    text = if (showManualPairing) "Hide" else "Show",
                    style = MaterialTheme.typography.labelSmall.copy(color = colors.primaryBright)
                )
            }

            AnimatedVisibility(visible = showManualPairing) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = manualChatIdInput,
                        onValueChange = { manualChatIdInput = it.filter { ch -> ch.isDigit() || ch == '-' } },
                        label = { Text("Telegram Chat ID") },
                        placeholder = { Text("e.g. 123456789") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primaryBright,
                            unfocusedBorderColor = colors.border,
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("manual_chat_id_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val id = manualChatIdInput.toLongOrNull()
                            if (id != null) {
                                app.telegramBotManager.pairManually(id)
                                manualChatIdInput = ""
                                Toast.makeText(context, "Paired Chat ID: $id", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Invalid Chat ID", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("save_manual_chat_id_btn")
                    ) {
                        Text("SAVE CHAT ID")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Disconnect & Wipe Credentials Button
            if (config.hasToken) {
                OutlinedButton(
                    onClick = {
                        app.telegramBotManager.disconnect()
                        tokenInput = ""
                        Toast.makeText(context, "Disconnected and credentials wiped", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("disconnect_telegram_btn")
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DISCONNECT BOT & ERASE CREDENTIALS")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Security Explanation Card
            LiquidGlassCard(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = colors.primaryBright,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "How we keep it secure: Your bot token is encrypted inside Android's hardware Keystore. VANTA talks directly to Telegram over secure HTTPS with zero middleman servers.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    }

    TelegramBotTutorialDialog(
        isVisible = showBotTutorial,
        onDismiss = { showBotTutorial = false }
    )
}
