package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.CheckCircle
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

    var forwardDestLabelInput by remember { mutableStateOf("") }
    var forwardDestChatIdInput by remember { mutableStateOf("") }

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
                // Header / Status Card
                GlowCard(modifier = Modifier.fillMaxWidth()) {
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
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
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

                        Spacer(modifier = Modifier.width(8.dp))

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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
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
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
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
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = if (config.hasToken && tokenInput.isEmpty()) config.tokenMasked else tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Bot Token from @BotFather") },
                    placeholder = { Text("123456789:ABCdefGhIJKlmNoPQRstuVWXyz") },
                    visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                            Icon(
                                imageVector = if (isTokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Toggle token visibility",
                                tint = colors.textMuted
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                        focusedContainerColor = Color(0xCC060608),
                        unfocusedContainerColor = Color(0xCC060608),
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = colors.textMuted,
                        cursorColor = Color.White
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
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("verify_token_btn")
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
                            } catch (_: Exception) {
                                Toast.makeText(context, "Telegram app or browser not found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("@BotFather", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
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
                                    imageVector = Icons.Rounded.CheckCircle,
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
                                        .background(Color.Black)
                                        .border(
                                            1.dp,
                                            Color.White.copy(alpha = 0.28f),
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "PAIRING CODE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = config.activePairingCode ?: "",
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 6.sp
                                        ),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Open @${config.botUsername ?: "your bot"} in Telegram and send:\n/start ${config.activePairingCode}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 18.sp
                                        ),
                                        color = colors.textSecondary
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
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("generate_pair_code_btn")
                                ) {
                                    Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("GET A CODE", fontWeight = FontWeight.Bold)
                                }

                                if (config.botUsername != null) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val uri = Uri.parse("https://t.me/${config.botUsername}")
                                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Could not open Telegram", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("OPEN IN TELEGRAM", fontWeight = FontWeight.SemiBold)
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
                        .clickable { showManualPairing = !showManualPairing }
                        .padding(vertical = 4.dp),
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
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                AnimatedVisibility(visible = showManualPairing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualChatIdInput,
                            onValueChange = { manualChatIdInput = it.filter { ch -> ch.isDigit() || ch == '-' } },
                            label = { Text("Telegram Chat ID") },
                            placeholder = { Text("e.g. 123456789") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                focusedContainerColor = Color(0xCC060608),
                                unfocusedContainerColor = Color(0xCC060608),
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = colors.textMuted,
                                cursorColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_chat_id_input")
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_manual_chat_id_btn")
                        ) {
                            Text("SAVE CHAT ID", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Forwarding Destinations (for Telegram "Forward" inline button)
                Text(
                    text = "3. FORWARDING DESTINATIONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = colors.primaryBright
                )
                Spacer(modifier = Modifier.height(8.dp))

                GlowCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Save Telegram chats, groups, or channels to appear as quick destination buttons when you tap Forward under an SMS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = forwardDestLabelInput,
                                onValueChange = { forwardDestLabelInput = it.take(28) },
                                label = { Text("Label") },
                                placeholder = { Text("Work Group") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                    focusedContainerColor = Color(0xCC060608),
                                    unfocusedContainerColor = Color(0xCC060608),
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                    focusedLabelColor = Color.White,
                                    unfocusedLabelColor = colors.textMuted,
                                    cursorColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("forward_dest_label_input")
                            )

                            OutlinedTextField(
                                value = forwardDestChatIdInput,
                                onValueChange = { forwardDestChatIdInput = it.filter { ch -> ch.isDigit() || ch == '-' } },
                                label = { Text("Chat ID") },
                                placeholder = { Text("-100...") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
                                    focusedContainerColor = Color(0xCC060608),
                                    unfocusedContainerColor = Color(0xCC060608),
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                    focusedLabelColor = Color.White,
                                    unfocusedLabelColor = colors.textMuted,
                                    cursorColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("forward_dest_chat_id_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val parsedId = forwardDestChatIdInput.toLongOrNull()
                                if (parsedId == null || parsedId == 0L) {
                                    Toast.makeText(context, "Enter a valid numeric Chat ID", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                app.preferencesManager.addForwardDestination(parsedId, forwardDestLabelInput)
                                forwardDestLabelInput = ""
                                forwardDestChatIdInput = ""
                                Toast.makeText(context, "Destination saved", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("add_forward_dest_btn")
                        ) {
                            Text("ADD DESTINATION", fontWeight = FontWeight.Bold)
                        }

                        if (config.forwardDestinations.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            config.forwardDestinations.forEach { dest ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("forward_dest_item_${dest.chatId}"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dest.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "Chat ID: ${dest.chatId}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                            color = colors.textMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = { app.preferencesManager.removeForwardDestination(dest.chatId) },
                                        modifier = Modifier.testTag("remove_forward_dest_btn_${dest.chatId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Remove destination",
                                            tint = colors.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("disconnect_telegram_btn")
                    ) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DISCONNECT BOT & ERASE CREDENTIALS", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Security Explanation Card
                LiquidGlassCard(
                    contentPadding = PaddingValues(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
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
