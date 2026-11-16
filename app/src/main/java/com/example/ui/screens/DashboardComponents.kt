package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.core.UpdateState
import com.example.data.model.AppPreferences
import com.example.data.model.SimCardInfo
import com.example.data.model.SimControlConfig
import com.example.data.model.SimFeatureSettings
import com.example.data.model.TelegramConfig
import com.example.ui.components.GithubStarButton
import com.example.ui.components.GlowCard
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.StatusDot
import com.example.ui.theme.LocalVantaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BoxScope.RightNavigationOverlay(
    isOpen: Boolean,
    topOffsetDp: Dp,
    closeButtonReservedWidth: Dp,
    prefs: AppPreferences,
    telegramConfig: TelegramConfig,
    isServiceRunning: Boolean,
    updateState: UpdateState,
    onDismiss: () -> Unit,
    onOpenUpdateModal: () -> Unit,
    onNavigateToSimControl: () -> Unit,
    onNavigateToTelegram: () -> Unit,
    onNavigateToPrivacyProtection: () -> Unit,
    onNavigateToSystemHealth: () -> Unit,
    onReplayTutorial: () -> Unit,
    onOpenGithub: () -> Unit
) {
    val colors = LocalVantaColors.current

    // Backdrop dimming scrim
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(240)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .testTag("drawer_scrim")
        )
    }

    // Right navigation drawer overlay (~80% width, aligned to menu toggle)
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(240)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(200)),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = topOffsetDp)
    ) {
        val overlayShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
        val borderBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.28f)
            )
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.80f)
                .fillMaxHeight()
                .clip(overlayShape)
                .border(width = 1.dp, brush = borderBrush, shape = overlayShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* consume clicks inside menu */ }
                )
                .testTag("nav_drawer_content"),
            shape = overlayShape,
            color = Color(0xBD030305),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.03f),
                                Color.Black.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.02f)
                            )
                        )
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                    .padding(bottom = 16.dp)
            ) {
                // Header row aligned with top-right close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(start = 20.dp, end = closeButtonReservedWidth),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (updateState is UpdateState.UpdateAvailable) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .clickable(onClick = onOpenUpdateModal)
                                .testTag("drawer_check_updates_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudDownload,
                                    contentDescription = "Update available",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "UPDATE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "NAVIGATION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    DrawerNavItem(
                        icon = Icons.Outlined.SimCard,
                        title = "SIM Control",
                        subtitle = prefs.simControl.summaryLabel() + " • Per-SIM features",
                        onClick = {
                            onDismiss()
                            onNavigateToSimControl()
                        },
                        testTag = "drawer_nav_sim_control"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    DrawerNavItem(
                        icon = Icons.AutoMirrored.Outlined.Send,
                        title = "Telegram Bot",
                        subtitle = if (telegramConfig.hasToken) "@${telegramConfig.botUsername ?: "bot"} • Connected" else "Configure token & chat pairing",
                        onClick = {
                            onDismiss()
                            onNavigateToTelegram()
                        },
                        testTag = "drawer_nav_telegram"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    DrawerNavItem(
                        icon = Icons.Outlined.Shield,
                        title = "Privacy & Security",
                        subtitle = "Keystore integrity & log purge",
                        onClick = {
                            onDismiss()
                            onNavigateToPrivacyProtection()
                        },
                        testTag = "drawer_nav_privacy"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    DrawerNavItem(
                        icon = Icons.Outlined.MonitorHeart,
                        title = "System Health",
                        subtitle = if (isServiceRunning) "Forwarding live • Diagnostics" else "Service idle • Diagnostics",
                        onClick = {
                            onDismiss()
                            onNavigateToSystemHealth()
                        },
                        testTag = "drawer_nav_health"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    DrawerNavItem(
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        title = "Quick Tour",
                        subtitle = "Walk through the feature guide",
                        onClick = {
                            onDismiss()
                            onReplayTutorial()
                        },
                        testTag = "drawer_nav_tutorial"
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Centered drawer branding
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("drawer_branding_section"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.vanta_icon),
                            contentDescription = "VANTA",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(60.dp)
                                .testTag("drawer_branding_logo")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "VANTA",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your phone. Your SIM. Your Telegram.",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.25.sp,
                                lineHeight = 13.sp
                            ),
                            color = colors.textMuted.copy(alpha = 0.78f),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Drawer footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GithubStarButton(
                        onClick = onOpenGithub,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "VANTA · Source Available",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp
                        ),
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val statusText = when (updateState) {
                        is UpdateState.Checking -> "Checking updates..."
                        is UpdateState.UpdateAvailable -> "Update: ${updateState.release.tagName}"
                        is UpdateState.Downloading -> "Downloading update..."
                        is UpdateState.ReadyToInstall -> "Ready to install"
                        is UpdateState.Error -> "Check failed"
                        else -> "Up to date"
                    }

                    Text(
                        text = "v${BuildConfig.VERSION_NAME} · $statusText",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (updateState is UpdateState.UpdateAvailable) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (updateState is UpdateState.UpdateAvailable) Color.White else colors.textMuted,
                        modifier = Modifier.testTag("drawer_version_status")
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = LocalVantaColors.current
    LiquidGlassCard(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.testTag(testTag)
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun StatusPanel(
    isForwardingActive: Boolean,
    isServiceRunning: Boolean,
    isInternetAvailable: Boolean,
    isTelegramConnected: Boolean,
    pendingCount: Int,
    totalDelivered: Int,
    lastDelivery: Long,
    simControl: SimControlConfig,
    sims: List<SimCardInfo> = emptyList(),
    isOtpProtectionEnabled: Boolean = false,
    onToggleSimMaster: (Int, Boolean) -> Unit = { _, _ -> },
    onDisableOtpProtection: () -> Unit = {}
) {
    val colors = LocalVantaColors.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val sim1Info = sims.firstOrNull { it.slotIndex == 0 }
    val sim2Info = sims.firstOrNull { it.slotIndex == 1 }
    val showOtpBlockedWarning = isOtpProtectionEnabled && !simControl.isAnyOtpForwardingEnabled()

    GlowCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE STATUS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontSize = 12.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "ACTIVE: ${simControl.summaryLabel()}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    ),
                    color = colors.textMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryItem(
                    modifier = Modifier.weight(1f),
                    label = "FORWARDING",
                    value = if (isForwardingActive && isServiceRunning) "ACTIVE" else "OFF",
                    isActive = isForwardingActive && isServiceRunning
                )
                TelemetryItem(
                    modifier = Modifier.weight(1f),
                    label = "SERVICE",
                    value = if (isServiceRunning) "ACTIVE" else "OFF",
                    isActive = isServiceRunning
                )
                TelemetryItem(
                    modifier = Modifier.weight(1f),
                    label = "TELEGRAM",
                    value = if (isTelegramConnected) "ONLINE" else "OFFLINE",
                    isActive = isTelegramConnected
                )
                TelemetryItem(
                    modifier = Modifier.weight(1f),
                    label = "INTERNET",
                    value = if (isInternetAvailable) "ONLINE" else "OFFLINE",
                    isActive = isInternetAvailable
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            val anySimHasPermission = sims.any { it.hasPermission }
            SimLiveStatusRow(
                slotIndex = 0,
                simInfo = sim1Info,
                anySimHasPermission = anySimHasPermission,
                settings = simControl.sim1,
                isBridgeForwardingOn = isForwardingActive && isServiceRunning,
                isGlobalOtpProtectionOn = isOtpProtectionEnabled,
                onToggleMaster = { enabled -> onToggleSimMaster(0, enabled) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            SimLiveStatusRow(
                slotIndex = 1,
                simInfo = sim2Info,
                anySimHasPermission = anySimHasPermission,
                settings = simControl.sim2,
                isBridgeForwardingOn = isForwardingActive && isServiceRunning,
                isGlobalOtpProtectionOn = isOtpProtectionEnabled,
                onToggleMaster = { enabled -> onToggleSimMaster(1, enabled) }
            )

            if (showOtpBlockedWarning) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.error.copy(alpha = 0.14f))
                        .border(1.dp, colors.error.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("live_status_otp_warning"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = colors.error,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "OTP forwarding blocked by protection",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colors.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = onDisableOtpProtection,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("live_status_disable_protection_btn")
                    ) {
                        Text(
                            text = "Disable protection",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.10f),
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "LAST TEXT SENT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (lastDelivery > 0) timeFormat.format(Date(lastDelivery)) else "None yet",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "FORWARDED SMS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (pendingCount == 0) "$totalDelivered texts sent" else "$pendingCount waiting • $totalDelivered sent",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = if (pendingCount > 0) colors.warning else colors.success,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SimLiveStatusRow(
    slotIndex: Int,
    simInfo: SimCardInfo?,
    anySimHasPermission: Boolean,
    settings: SimFeatureSettings,
    isBridgeForwardingOn: Boolean,
    isGlobalOtpProtectionOn: Boolean,
    onToggleMaster: (Boolean) -> Unit
) {
    val colors = LocalVantaColors.current
    val isSingleSimHardwareAbsent = simInfo == null && anySimHasPermission
    val hasPerm = (simInfo?.hasPermission ?: false) || anySimHasPermission
    val isAvailable = simInfo?.isAvailable ?: false
    val isAbsent = hasPerm && !isAvailable

    val dotColor = when {
        !hasPerm -> colors.warning
        isAbsent -> colors.textMuted
        !settings.masterEnabled -> colors.textMuted
        isBridgeForwardingOn -> colors.success
        else -> Color.White.copy(alpha = 0.72f)
    }
    val dotActive = hasPerm && isAvailable && settings.masterEnabled

    val titleText = when {
        !hasPerm -> "SIM ${slotIndex + 1} · Permission needed"
        isAbsent -> "SIM ${slotIndex + 1} · Unavailable"
        else -> "SIM ${slotIndex + 1} · ${simInfo?.carrierName ?: "Carrier unavailable"}"
    }

    val featureSummary = buildString {
        if (!settings.smsForwardingEnabled) {
            append("SMS Off")
        } else if (isGlobalOtpProtectionOn || !settings.otpMessagesEnabled) {
            append("SMS Only")
        } else {
            append("SMS + OTP")
        }
        if (settings.incomingCallNotificationsEnabled || settings.callLogForwardingEnabled) {
            append(" · Calls")
        }
        if (settings.smsSendingEnabled) {
            append(" · Reply")
        }
    }

    val statusText = when {
        !hasPerm -> if (settings.masterEnabled) "Master ON · Grant Phone access" else "Disabled"
        isSingleSimHardwareAbsent -> "Single-SIM device"
        isAbsent -> "No SIM inserted"
        !settings.masterEnabled -> "Disabled"
        isBridgeForwardingOn -> "Active · $featureSummary"
        else -> "Enabled · $featureSummary"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_status_sim_$slotIndex"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            StatusDot(
                isActive = dotActive,
                size = 6.dp,
                activeColor = dotColor,
                inactiveColor = dotColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = if (isAbsent || !settings.masterEnabled) colors.textMuted else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp
                    ),
                    color = if (!hasPerm) colors.warning else colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = if (isAbsent) false else settings.masterEnabled,
            enabled = !isAbsent,
            onCheckedChange = onToggleMaster,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.White.copy(alpha = 0.28f),
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceElevated,
                uncheckedBorderColor = Color.White.copy(alpha = 0.16f),
                disabledCheckedThumbColor = colors.textMuted,
                disabledUncheckedThumbColor = colors.textMuted.copy(alpha = 0.55f),
                disabledUncheckedTrackColor = colors.surfaceElevated.copy(alpha = 0.55f)
            ),
            modifier = Modifier
                .scale(0.72f)
                .testTag("live_status_sim_switch_$slotIndex")
        )
    }
}

@Composable
private fun TelemetryItem(
    label: String,
    value: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalVantaColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            StatusDot(isActive = isActive, size = 6.dp)
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp
                ),
                color = if (isActive) colors.success else colors.textMuted,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textMuted,
            maxLines = 1
        )
    }
}
