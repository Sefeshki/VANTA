package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.VantaApplication
import com.example.core.EventLogger
import com.example.core.UpdateManager
import com.example.core.UpdateState
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import com.example.data.model.SimSelectionMode
import com.example.service.VantaForegroundService
import com.example.ui.components.AnimatedHamburgerArrow
import com.example.ui.components.CyberTerminal
import com.example.ui.components.EventHorizonBackground
import com.example.ui.components.GlowCard
import com.example.ui.components.OtpSuggestionDialog
import com.example.ui.components.SpotlightTutorialOverlay
import com.example.ui.components.StatusDot
import com.example.ui.components.UpdateDialog
import com.example.ui.components.VpnToggleSwitch
import com.example.ui.components.blurBitmapInPlace
import com.example.ui.components.createBlurredBackdrop
import com.example.ui.theme.LocalVantaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun DashboardScreen(
    onNavigateToSims: () -> Unit,
    onNavigateToTelegram: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrivacyProtection: () -> Unit = {},
    onNavigateToSystemHealth: () -> Unit = {}
) {
    val colors = LocalVantaColors.current
    val context = LocalContext.current
    val app = context.applicationContext as VantaApplication

    var isDrawerOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = isDrawerOpen) {
        isDrawerOpen = false
    }

    val updateState by UpdateManager.state.collectAsState()

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
    var showUpdateModal by remember { mutableStateOf(false) }

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
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    LaunchedEffect(updateState) {
        if (updateState is UpdateState.Downloading || updateState is UpdateState.ReadyToInstall) {
            showUpdateModal = true
        }
    }

    LaunchedEffect(Unit) {
        app.networkMonitor.refresh()
        app.simManager.refreshSims()
        if (telegramConfig.hasToken) {
            app.telegramBotManager.checkConnection()
        }
        UpdateManager.checkForUpdates()
    }

    val isBridgeRunning = prefs.isForwardingEnabled && isServiceRunning

    val useNativeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val usePixelCopyBlur = !useNativeBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    val coroutineScope = rememberCoroutineScope()

    // ~80% genuine GPU RenderEffect blur on Android 12+ when drawer is open
    val backdropBlur by animateDpAsState(
        targetValue = if (useNativeBlur && isDrawerOpen) 28.dp else 0.dp,
        animationSpec = tween(durationMillis = 260),
        label = "drawerBackdropBlur"
    )

    // Genuine downscaled multi-pass blur fallback for Android 7–11 (API < 31)
    val dashboardGraphicsLayer = rememberGraphicsLayer()
    var fallbackBlurredBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isCapturingBackdrop by remember { mutableStateOf(false) }

    fun openDrawerWithBackdropBlur() {
        if (useNativeBlur) {
            isDrawerOpen = true
            return
        }
        if (isCapturingBackdrop) return

        if (usePixelCopyBlur) {
            val window = context.findActivity()?.window
            val decorView = window?.decorView
            if (window == null || decorView == null || decorView.width <= 0 || decorView.height <= 0) {
                isDrawerOpen = true
                return
            }

            isCapturingBackdrop = true
            val scaledW = (decorView.width / 4).coerceAtLeast(32)
            val scaledH = (decorView.height / 4).coerceAtLeast(32)
            val destBitmap = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
            val mainHandler = Handler(Looper.getMainLooper())
            var finished = false

            val timeoutRunnable = Runnable {
                if (!finished) {
                    finished = true
                    isCapturingBackdrop = false
                    isDrawerOpen = true
                }
            }
            mainHandler.postDelayed(timeoutRunnable, 80L)

            try {
                PixelCopy.request(
                    window,
                    null,
                    destBitmap,
                    PixelCopy.OnPixelCopyFinishedListener { copyResult ->
                        if (finished) return@OnPixelCopyFinishedListener
                        finished = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        if (copyResult == PixelCopy.SUCCESS) {
                            coroutineScope.launch {
                                try {
                                    val blurred = withContext(Dispatchers.Default) {
                                        blurBitmapInPlace(destBitmap, radius = 12)
                                    }
                                    fallbackBlurredBitmap = blurred
                                } catch (_: Throwable) {
                                    fallbackBlurredBitmap = null
                                } finally {
                                    isCapturingBackdrop = false
                                    isDrawerOpen = true
                                }
                            }
                        } else {
                            isCapturingBackdrop = false
                            isDrawerOpen = true
                        }
                    },
                    mainHandler
                )
            } catch (_: Throwable) {
                if (!finished) {
                    finished = true
                    mainHandler.removeCallbacks(timeoutRunnable)
                    isCapturingBackdrop = false
                    isDrawerOpen = true
                }
            }
        } else {
            // API 24-25 fallback via GraphicsLayer snapshot
            isCapturingBackdrop = true
            coroutineScope.launch {
                try {
                    val captured = dashboardGraphicsLayer.toImageBitmap()
                    val blurred = withContext(Dispatchers.Default) {
                        createBlurredBackdrop(captured, downscaleFactor = 4, radius = 12)
                    }
                    fallbackBlurredBitmap = blurred
                } catch (_: Throwable) {
                    fallbackBlurredBitmap = null
                } finally {
                    isCapturingBackdrop = false
                    isDrawerOpen = true
                }
            }
        }
    }

    LaunchedEffect(isDrawerOpen, useNativeBlur) {
        if (!useNativeBlur && !isDrawerOpen) {
            delay(280)
            if (!isDrawerOpen) {
                fallbackBlurredBitmap = null
            }
        }
    }

    val density = LocalDensity.current
    var rootWindowOffset by remember { mutableStateOf(Offset.Zero) }
    var rootWidthPx by remember { mutableIntStateOf(0) }
    var hamburgerWindowOffset by remember { mutableStateOf(Offset.Zero) }
    var hasMeasuredHamburger by remember { mutableStateOf(false) }

    val hamburgerLocalX = if (hasMeasuredHamburger) {
        (hamburgerWindowOffset.x - rootWindowOffset.x).roundToInt()
    } else 0
    val hamburgerLocalY = if (hasMeasuredHamburger) {
        (hamburgerWindowOffset.y - rootWindowOffset.y).roundToInt().coerceAtLeast(0)
    } else 0

    val drawerTopOffsetDp = with(density) { hamburgerLocalY.toDp() }
    val drawerCloseReservedWidth = if (hasMeasuredHamburger && rootWidthPx > hamburgerLocalX) {
        with(density) { (rootWidthPx - hamburgerLocalX).toDp() } + 4.dp
    } else 56.dp

    EventHorizonBackground(ambientIllumination = if (isBridgeRunning) 1f else 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    rootWindowOffset = coords.positionInWindow()
                    rootWidthPx = coords.size.width
                }
        ) {
            // Underlying Fullscreen Dashboard Content (blurred when menu overlay is open)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        when {
                            useNativeBlur && backdropBlur > 0.dp -> {
                                Modifier.blur(backdropBlur, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                            }
                            !useNativeBlur && !usePixelCopyBlur -> {
                                Modifier.drawWithContent {
                                    dashboardGraphicsLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawLayer(dashboardGraphicsLayer)
                                }
                            }
                            else -> Modifier
                        }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp)
                ) {
                    Spacer(modifier = Modifier.height(44.dp))

                    // 1. WHITE ENERGY CORE POWER SWITCH (Centered in upper space above StatusPanel)
                    VpnToggleSwitch(
                        isActive = isBridgeRunning,
                        onToggle = {
                            val hasSms = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECEIVE_SMS
                            ) == PackageManager.PERMISSION_GRANTED

                            if (!hasSms) {
                                Toast.makeText(
                                    context,
                                    "We need SMS permission first so we can catch incoming texts.",
                                    Toast.LENGTH_SHORT
                                ).show()
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

                    Spacer(modifier = Modifier.height(16.dp))

                    val isTelegramLive = if (telegramConfig.hasToken) {
                        (isBotConnected || isTelegramReachable) && isInternet
                    } else {
                        isTelegramReachable && isInternet
                    }

                    // 2. STATUS PANEL
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. TELEGRAM BOT SECTION (Setup card until paired; minimal single line once paired)
                    val isTelegramFullyPaired = telegramConfig.hasToken && telegramConfig.authorizedChatId != null
                    if (!isTelegramFullyPaired) {
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Send,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = "TELEGRAM BOT",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (!telegramConfig.hasToken) {
                                                "Tap to connect your bot"
                                            } else {
                                                "Waiting for pairing code"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.warning,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Configure Telegram",
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("dashboard_telegram_paired_line"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusDot(isActive = isTelegramLive, size = 7.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "@${telegramConfig.botUsername ?: "bot"} · Ready & Paired",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.SimCard,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SIM CARDS",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Manage SIM Cards",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val sim1Active = prefs.simSelectionMode == SimSelectionMode.ALL || prefs.simSelectionMode == SimSelectionMode.SIM1_ONLY
                            val sim2Active = prefs.simSelectionMode == SimSelectionMode.ALL || prefs.simSelectionMode == SimSelectionMode.SIM2_ONLY

                            val sim1Info = sims.firstOrNull { it.slotIndex == 0 }
                            val sim2Info = sims.firstOrNull { it.slotIndex == 1 }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SIM 1: ${sim1Info?.carrierName ?: "Primary"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (sim1Active) "ACTIVE" else "OFF",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (sim1Active) colors.success else colors.textMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SIM 2: ${sim2Info?.carrierName ?: "Secondary"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (sim2Active) "ACTIVE" else "OFF",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (sim2Active) colors.success else colors.textMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. STAY ALIVE & AUTO START CONTROLS (Compact Cards)
                    val switchColors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.White.copy(alpha = 0.28f),
                        uncheckedThumbColor = colors.textMuted,
                        uncheckedTrackColor = colors.surfaceElevated,
                        uncheckedBorderColor = Color.White.copy(alpha = 0.16f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlowCard(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "STAY ALIVE",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        ),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Switch(
                                        checked = prefs.isStayAliveEnabled,
                                        onCheckedChange = { app.preferencesManager.setStayAliveEnabled(it) },
                                        colors = switchColors,
                                        modifier = Modifier
                                            .scale(0.82f)
                                            .testTag("stay_alive_switch")
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Background protection",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        GlowCard(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "AUTO START",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        ),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Switch(
                                        checked = prefs.isAutoStartEnabled,
                                        onCheckedChange = { app.preferencesManager.setAutoStartEnabled(it) },
                                        colors = switchColors,
                                        modifier = Modifier
                                            .scale(0.82f)
                                            .testTag("auto_start_switch")
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Start on boot",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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

                    Spacer(modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }

                // Floating top-right controls without a bulky top bar or container
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateToPermissions,
                        modifier = Modifier.testTag("dashboard_nav_permissions")
                    ) {
                        val hasSms = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECEIVE_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = "Permissions",
                            tint = if (hasSms) colors.success else colors.warning
                        )
                    }
                    // Exact 48.dp anchor slot for the animated hamburger / drawer close icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .onGloballyPositioned { coords ->
                                hamburgerWindowOffset = coords.positionInWindow()
                                hasMeasuredHamburger = true
                            }
                    )
                }
            }

            // Genuinely blurred opaque backdrop layer for Android < 12 (API < 31)
            if (!useNativeBlur) {
                AnimatedVisibility(
                    visible = isDrawerOpen && fallbackBlurredBitmap != null,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(200))
                ) {
                    fallbackBlurredBitmap?.let { blurredBmp ->
                        Image(
                            bitmap = blurredBmp,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("fallback_blurred_backdrop")
                        )
                    }
                }
            }

            // Right-Side Floating Glass Overlay (~80% width, aligned with hamburger top edge)
            RightNavigationOverlay(
                isOpen = isDrawerOpen,
                topOffsetDp = drawerTopOffsetDp,
                closeButtonReservedWidth = drawerCloseReservedWidth,
                prefs = prefs,
                telegramConfig = telegramConfig,
                isServiceRunning = isServiceRunning,
                updateState = updateState,
                onDismiss = { isDrawerOpen = false },
                onOpenUpdateModal = { showUpdateModal = true },
                onNavigateToTelegram = onNavigateToTelegram,
                onNavigateToPrivacyProtection = onNavigateToPrivacyProtection,
                onNavigateToSystemHealth = onNavigateToSystemHealth,
                onReplayTutorial = {
                    showTutorial = true
                    app.preferencesManager.setTutorialCompleted(false)
                },
                onOpenGithub = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sefeshki/VANTA"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // Single animated hamburger-to-arrow icon anchored in front of drawer's top-right corner
            if (hasMeasuredHamburger) {
                AnimatedHamburgerArrow(
                    isOpen = isDrawerOpen,
                    onClick = {
                        if (isDrawerOpen) {
                            isDrawerOpen = false
                        } else {
                            openDrawerWithBackdropBlur()
                        }
                    },
                    modifier = Modifier
                        .offset { IntOffset(hamburgerLocalX, hamburgerLocalY) }
                        .testTag("dashboard_nav_settings")
                )
            }

            // Modals & Spotlight Tutorial above drawer and icon
            SpotlightTutorialOverlay(
                isVisible = showTutorial,
                onFinish = {
                    showTutorial = false
                    app.preferencesManager.setTutorialCompleted(true)
                }
            )

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

            if (showUpdateModal && updateState !is UpdateState.Idle) {
                UpdateDialog(
                    updateState = updateState,
                    onDismiss = {
                        showUpdateModal = false
                        if (updateState is UpdateState.UpToDate || updateState is UpdateState.Error) {
                            UpdateManager.resetState()
                        }
                    },
                    onStartDownload = { release -> UpdateManager.startDownload(context, release) },
                    onCancelDownload = {
                        UpdateManager.cancelDownload()
                        showUpdateModal = false
                    },
                    onInstall = { apkFile -> UpdateManager.promptInstall(context, apkFile) },
                    onCheckAgain = { UpdateManager.checkForUpdates() }
                )
            }
        }
    }
}
