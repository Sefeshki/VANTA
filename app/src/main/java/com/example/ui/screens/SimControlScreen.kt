package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.VantaApplication
import com.example.core.PermissionHelper
import com.example.core.PermissionRequirement
import com.example.data.model.SimCardInfo
import com.example.data.model.SimFeature
import com.example.data.model.SimFeatureSettings
import com.example.ui.components.EventHorizonBackground
import com.example.ui.components.GlowCard
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.StatusDot
import com.example.ui.components.vantaSwitchColors
import com.example.ui.theme.LocalVantaColors

private data class PendingPermissionAction(
    val slotIndex: Int?,
    val feature: SimFeature?,
    val requirement: PermissionRequirement,
    val isPermanentlyDenied: Boolean,
    val onGrantedCallback: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimControlScreen(
    onNavigateBack: () -> Unit
) {
    val colors = LocalVantaColors.current
    val context = LocalContext.current
    val app = context.applicationContext as VantaApplication

    val sims by app.simManager.detectedSims.collectAsStateWithLifecycle()
    val prefs by app.preferencesManager.preferences.collectAsStateWithLifecycle()

    var selectedSlot by remember { mutableIntStateOf(0) }
    var permissionTick by remember { mutableIntStateOf(0) }
    var pendingPermAction by remember { mutableStateOf<PendingPermissionAction?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                app.simManager.refreshSims()
                app.callMonitorManager.refreshObserverState()
                permissionTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val action = pendingPermAction
        pendingPermAction = null
        app.simManager.refreshSims()
        permissionTick++

        val allGranted = action?.requirement?.permissions?.all { perm ->
            results[perm] == true || PermissionHelper.isGranted(context, perm)
        } ?: false

        if (allGranted && action != null) {
            action.onGrantedCallback()
            app.callMonitorManager.refreshObserverState()
        } else {
            Toast.makeText(
                context,
                "Permission not granted. Feature stays disabled.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun requestPermissionsWithRationale(
        slotIndex: Int?,
        feature: SimFeature?,
        requirement: PermissionRequirement,
        onGranted: () -> Unit
    ) {
        val missing = PermissionHelper.missingPermissions(context, requirement.permissions)
        if (missing.isEmpty()) {
            onGranted()
            return
        }
        val permanentlyDenied = PermissionHelper.isAnyPermanentlyDenied(
            context = context,
            preferencesManager = app.preferencesManager,
            permissions = missing
        )
        pendingPermAction = PendingPermissionAction(
            slotIndex = slotIndex,
            feature = feature,
            requirement = requirement,
            isPermanentlyDenied = permanentlyDenied,
            onGrantedCallback = onGranted
        )
    }

    val hasPhonePerm = remember(permissionTick) {
        PermissionHelper.isGranted(context, Manifest.permission.READ_PHONE_STATE)
    }
    val sim1Info = sims.firstOrNull { it.slotIndex == 0 } ?: SimCardInfo(
        slotIndex = 0,
        subId = -1,
        carrierName = if (hasPhonePerm) "No SIM inserted" else "Permission required",
        phoneNumber = null,
        isAvailable = false,
        isSelected = prefs.simControl.sim1.masterEnabled,
        hasPermission = hasPhonePerm
    )
    val sim2Info = sims.firstOrNull { it.slotIndex == 1 } ?: SimCardInfo(
        slotIndex = 1,
        subId = -1,
        carrierName = if (hasPhonePerm) "Single-SIM / Empty" else "Permission required",
        phoneNumber = null,
        isAvailable = false,
        isSelected = prefs.simControl.sim2.masterEnabled,
        hasPermission = hasPhonePerm
    )
    val hasContactsPerm = remember(permissionTick) {
        PermissionHelper.isGranted(context, Manifest.permission.READ_CONTACTS)
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
                            text = "SIM CONTROL",
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
                            modifier = Modifier.testTag("sim_control_back_btn")
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
                                app.simManager.refreshSims()
                                permissionTick++
                                Toast.makeText(context, "SIM slots refreshed", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("sim_control_refresh_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh SIMs",
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
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                if (!hasPhonePerm) {
                    PhonePermissionBanner(
                        onGrantClick = {
                            requestPermissionsWithRationale(
                                slotIndex = null,
                                feature = null,
                                requirement = PermissionRequirement(
                                    feature = null,
                                    permissions = listOf(
                                        Manifest.permission.READ_PHONE_STATE,
                                        Manifest.permission.READ_PHONE_NUMBERS
                                    ),
                                    title = "SIM Inspection Permission",
                                    rationale = "VANTA needs Phone State permission to read your real carrier names, SIM slots, and Android subscription IDs.",
                                    settingsHint = "Enable Phone permission in Android Settings."
                                ),
                                onGranted = {
                                    app.simManager.refreshSims()
                                    Toast.makeText(context, "SIM info unlocked", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Dual-SIM Selector Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SimSlotTab(
                        sim = sim1Info,
                        settings = prefs.simControl.sim1,
                        isSelectedTab = selectedSlot == 0,
                        onClick = { selectedSlot = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sim_tab_0")
                    )
                    SimSlotTab(
                        sim = sim2Info,
                        settings = prefs.simControl.sim2,
                        isSelectedTab = selectedSlot == 1,
                        onClick = { selectedSlot = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sim_tab_1")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                AnimatedContent(
                    targetState = selectedSlot,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "simDeckTransition"
                ) { slot ->
                    val currentSim = if (slot == 0) sim1Info else sim2Info
                    val currentSettings = prefs.simControl.forSlot(slot)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        SimIdentityAndMasterCard(
                            slotIndex = slot,
                            sim = currentSim,
                            settings = currentSettings,
                            onToggleMaster = { enabled ->
                                app.preferencesManager.setSimMasterEnabled(slot, enabled)
                                app.simManager.syncSelectionStateFromPreferences()
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SIM ${slot + 1} FEATURES",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = colors.primaryBright
                            )
                            Text(
                                text = if (!currentSim.isAvailable && currentSim.hasPermission) {
                                    "SLOT EMPTY"
                                } else if (!currentSettings.masterEnabled) {
                                    "MASTER OFF"
                                } else {
                                    "${currentSettings.activeFeatureCount()} OF 5 ACTIVE"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                ),
                                color = colors.textMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LiquidGlassCard(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val features = SimFeature.entries
                                features.forEachIndexed { index, feature ->
                                    val permMissing = remember(permissionTick, feature, slot) {
                                        PermissionHelper.isFeaturePermissionMissing(context, feature)
                                    }
                                    FeatureControlRow(
                                        slotIndex = slot,
                                        feature = feature,
                                        sim = currentSim,
                                        settings = currentSettings,
                                        permissionMissing = permMissing,
                                        onToggle = { targetEnabled ->
                                            if (!targetEnabled) {
                                                app.preferencesManager.setSimFeatureEnabled(slot, feature, false)
                                            } else {
                                                val req = PermissionHelper.requirementForFeature(feature)
                                                if (PermissionHelper.areAllGranted(context, req.permissions)) {
                                                    app.preferencesManager.setSimFeatureEnabled(slot, feature, true)
                                                    if (feature == SimFeature.CALL_LOG_FORWARDING) {
                                                        app.callMonitorManager.initializeCallLogBaseline()
                                                        app.callMonitorManager.refreshObserverState()
                                                    }
                                                } else {
                                                    requestPermissionsWithRationale(
                                                        slotIndex = slot,
                                                        feature = feature,
                                                        requirement = req,
                                                        onGranted = {
                                                            app.preferencesManager.setSimFeatureEnabled(slot, feature, true)
                                                            if (feature == SimFeature.CALL_LOG_FORWARDING) {
                                                                app.callMonitorManager.initializeCallLogBaseline()
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        },
                                        onFixPermission = {
                                            val req = PermissionHelper.requirementForFeature(feature)
                                            requestPermissionsWithRationale(
                                                slotIndex = slot,
                                                feature = feature,
                                                requirement = req,
                                                onGranted = {
                                                    app.preferencesManager.setSimFeatureEnabled(slot, feature, true)
                                                    if (feature == SimFeature.CALL_LOG_FORWARDING) {
                                                        app.callMonitorManager.initializeCallLogBaseline()
                                                    }
                                                }
                                            )
                                        }
                                    )
                                    if (index < features.lastIndex) {
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Optional Contact Lookup Strip
                LiquidGlassCard(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    onClick = {
                        if (!hasContactsPerm) {
                            requestPermissionsWithRationale(
                                slotIndex = null,
                                feature = null,
                                requirement = PermissionRequirement(
                                    feature = null,
                                    permissions = listOf(Manifest.permission.READ_CONTACTS),
                                    title = "Optional Contact Names",
                                    rationale = "Allow VANTA to display caller and sender contact names instead of raw phone numbers in Telegram notifications.",
                                    settingsHint = "Enable Contacts permission in Android Settings."
                                ),
                                onGranted = {
                                    Toast.makeText(context, "Contact names enabled", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sim_contacts_card")
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
                                imageVector = Icons.Outlined.Contacts,
                                contentDescription = null,
                                tint = if (hasContactsPerm) Color.White else colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Contact Name Resolution",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = if (hasContactsPerm) {
                                        "Showing saved contact names on forwarded SMS & calls"
                                    } else {
                                        "Optional • Tap to show contact names in Telegram"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasContactsPerm) "ON" else "ENABLE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (hasContactsPerm) colors.success else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    pendingPermAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingPermAction = null },
            containerColor = Color(0xFF0B0B0E),
            titleContentColor = Color.White,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = action.requirement.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = action.requirement.rationale,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (action.isPermanentlyDenied) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "This permission was previously denied. ${action.requirement.settingsHint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.warning
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (action.isPermanentlyDenied) {
                            pendingPermAction = null
                            PermissionHelper.openAppSettings(context)
                        } else {
                            app.preferencesManager.markPermissionsRequested(action.requirement.permissions)
                            permissionLauncher.launch(action.requirement.permissions.toTypedArray())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Text(if (action.isPermanentlyDenied) "Open Settings" else "Grant")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingPermAction = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.textMuted)
                ) {
                    Text("Not Now")
                }
            }
        )
    }
}

@Composable
private fun PhonePermissionBanner(onGrantClick: () -> Unit) {
    val colors = LocalVantaColors.current
    GlowCard(
        isHighlighted = true,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PHONE ACCESS NEEDED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.warning
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Grant phone permission to detect carriers and real subscription IDs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedButton(
                onClick = onGrantClick,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Grant", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SimSlotTab(
    sim: SimCardInfo,
    settings: SimFeatureSettings,
    isSelectedTab: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVantaColors.current
    val shape = RoundedCornerShape(16.dp)
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isSelectedTab) 0.65f else 0.16f),
            Color.White.copy(alpha = if (isSelectedTab) 0.22f else 0.06f),
            Color.White.copy(alpha = if (isSelectedTab) 0.45f else 0.12f)
        )
    )

    val dotActive = sim.isAvailable && settings.masterEnabled
    val dotColor = when {
        !sim.hasPermission -> colors.warning
        !sim.isAvailable -> colors.textMuted
        settings.masterEnabled -> colors.success
        else -> colors.textMuted
    }
    val statusBadge = when {
        !sim.hasPermission -> "PERM"
        !sim.isAvailable -> "EMPTY"
        settings.masterEnabled -> "ON"
        else -> "OFF"
    }

    Surface(
        color = if (isSelectedTab) Color(0xCC121216) else Color(0x9908080A),
        shape = shape,
        modifier = modifier
            .clip(shape)
            .border(1.dp, borderBrush, shape)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        isActive = dotActive,
                        size = 6.dp,
                        activeColor = dotColor,
                        inactiveColor = dotColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SIM ${sim.slotIndex + 1}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isSelectedTab) Color.White else colors.textSecondary
                    )
                }
                Text(
                    text = statusBadge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = dotColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sim.carrierName,
                style = MaterialTheme.typography.bodySmall,
                color = if (sim.isAvailable) colors.textSecondary else colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SimIdentityAndMasterCard(
    slotIndex: Int,
    sim: SimCardInfo,
    settings: SimFeatureSettings,
    onToggleMaster: (Boolean) -> Unit
) {
    val colors = LocalVantaColors.current
    val isSlotAvailable = sim.isAvailable

    val stateLabel = when {
        !sim.hasPermission -> "PERMISSION REQUIRED"
        !isSlotAvailable -> "UNAVAILABLE"
        settings.masterEnabled -> "ENABLED"
        else -> "DISABLED"
    }
    val stateColor = when {
        !sim.hasPermission -> colors.warning
        !isSlotAvailable -> colors.textMuted
        settings.masterEnabled -> colors.success
        else -> colors.textMuted
    }

    GlowCard(
        isHighlighted = isSlotAvailable && settings.masterEnabled,
        contentPadding = PaddingValues(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sim_master_card_$slotIndex")
    ) {
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = if (isSlotAvailable) 0.10f else 0.04f))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = if (isSlotAvailable) 0.24f else 0.10f),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SimCard,
                            contentDescription = null,
                            tint = if (isSlotAvailable) Color.White else colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SIM ${slotIndex + 1} · ${sim.carrierName}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        val details = buildList {
                            if (sim.subId >= 0) add("SUB #${sim.subId}")
                            sim.phoneNumber?.takeIf { it.isNotBlank() }?.let { add(it) }
                            sim.countryIso?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                        }.joinToString(" · ")
                        Text(
                            text = if (details.isNotEmpty()) details else stateLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = if (details.isNotEmpty()) colors.textMuted else stateColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = settings.masterEnabled,
                        enabled = isSlotAvailable || !sim.hasPermission,
                        onCheckedChange = onToggleMaster,
                        colors = vantaSwitchColors(),
                        modifier = Modifier
                            .scale(0.86f)
                            .testTag("sim_master_switch_$slotIndex")
                    )
                }
            }

            if (isSlotAvailable && !settings.masterEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Master OFF overrides all SIM ${slotIndex + 1} features while preserving preferences.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            } else if (!isSlotAvailable && sim.hasPermission) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "No active SIM detected in Slot ${slotIndex + 1}. Controls are locked until a SIM is present.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun FeatureControlRow(
    slotIndex: Int,
    feature: SimFeature,
    sim: SimCardInfo,
    settings: SimFeatureSettings,
    permissionMissing: Boolean,
    onToggle: (Boolean) -> Unit,
    onFixPermission: () -> Unit
) {
    val colors = LocalVantaColors.current
    val featureSaved = settings.isFeatureSelected(feature)
    val smsFwdOffForOtp = feature == SimFeature.OTP_MESSAGES && !settings.smsForwardingEnabled
    val slotUnavailable = !sim.isAvailable && sim.hasPermission
    val masterOff = !settings.masterEnabled

    val isEffective = !slotUnavailable && !permissionMissing && settings.isFeatureEffective(feature)

    val statusText = when {
        slotUnavailable -> "UNAVAILABLE"
        permissionMissing -> "PERMISSION NEEDED"
        masterOff -> "OVERRIDDEN"
        smsFwdOffForOtp -> "SMS FWD OFF"
        isEffective -> "ACTIVE"
        else -> "OFF"
    }
    val statusColor = when {
        slotUnavailable -> colors.textMuted
        permissionMissing -> colors.warning
        masterOff || smsFwdOffForOtp -> colors.textMuted
        isEffective -> colors.success
        else -> colors.textMuted
    }

    val canInteract = !slotUnavailable && !masterOff && !smsFwdOffForOtp

    val subtitle = when {
        slotUnavailable -> "Unavailable without SIM in Slot ${slotIndex + 1}"
        permissionMissing -> "Tap to grant required permission"
        masterOff -> "Paused while SIM ${slotIndex + 1} master is OFF"
        smsFwdOffForOtp -> "Requires SMS Forwarding enabled on SIM ${slotIndex + 1}"
        else -> feature.description
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (canInteract || permissionMissing) 1f else 0.48f)
            .clickable(enabled = permissionMissing) { onFixPermission() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = iconForFeature(feature),
                contentDescription = null,
                tint = if (isEffective) Color.White else colors.textMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = feature.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = statusColor,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (slotUnavailable) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Unavailable",
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Switch(
                checked = featureSaved && !permissionMissing,
                enabled = canInteract || permissionMissing,
                onCheckedChange = onToggle,
                colors = vantaSwitchColors(),
                modifier = Modifier
                    .scale(0.80f)
                    .testTag("sim_feature_${slotIndex}_${feature.name}")
            )
        }
    }
}

private fun iconForFeature(feature: SimFeature): ImageVector = when (feature) {
    SimFeature.SMS_FORWARDING -> Icons.Outlined.Sms
    SimFeature.OTP_MESSAGES -> Icons.Outlined.VpnKey
    SimFeature.CALL_LOG_FORWARDING -> Icons.AutoMirrored.Outlined.CallMade
    SimFeature.INCOMING_CALL_NOTIFICATIONS -> Icons.Outlined.PhoneInTalk
    SimFeature.SMS_SENDING -> Icons.AutoMirrored.Outlined.Send
}
