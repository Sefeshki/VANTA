package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.VantaApplication
import com.example.core.PermissionHelper
import com.example.ui.components.EventHorizonBackground
import com.example.ui.components.GlowCard
import com.example.ui.theme.LocalVantaColors

enum class PermState {
    GRANTED,
    MISSING,
    ATTENTION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCenterScreen(
    onNavigateBack: () -> Unit
) {
    val colors = LocalVantaColors.current
    val context = LocalContext.current
    val app = context.applicationContext as VantaApplication

    var refreshTrigger by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
                app.simManager.refreshSims()
                app.callMonitorManager.refreshObserverState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    val smsGranted = remember(refreshTrigger) { PermissionHelper.isGranted(context, Manifest.permission.RECEIVE_SMS) }
    val sendSmsGranted = remember(refreshTrigger) { PermissionHelper.isGranted(context, Manifest.permission.SEND_SMS) }
    val callLogGranted = remember(refreshTrigger) { PermissionHelper.isGranted(context, Manifest.permission.READ_CALL_LOG) }
    val contactsGranted = remember(refreshTrigger) { PermissionHelper.isGranted(context, Manifest.permission.READ_CONTACTS) }
    val notifGranted = remember(refreshTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionHelper.isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else true
    }
    val phoneStateGranted = remember(refreshTrigger) { PermissionHelper.isGranted(context, Manifest.permission.READ_PHONE_STATE) }
    val batteryIgnored = remember(refreshTrigger) { isBatteryOptimizationIgnored() }

    val singlePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshTrigger++
        app.simManager.refreshSims()
        app.callMonitorManager.refreshObserverState()
    }

    val multiplePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTrigger++
        app.simManager.refreshSims()
        app.callMonitorManager.refreshObserverState()
    }

    fun launchOrOpenSettings(permissions: List<String>) {
        if (PermissionHelper.isAnyPermanentlyDenied(context, app.preferencesManager, permissions)) {
            PermissionHelper.openAppSettings(context)
        } else {
            app.preferencesManager.markPermissionsRequested(permissions)
            if (permissions.size == 1) {
                singlePermLauncher.launch(permissions.first())
            } else {
                multiplePermLauncher.launch(permissions.toTypedArray())
            }
        }
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
                            text = "PERMISSIONS",
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
                            modifier = Modifier.testTag("perm_back_btn")
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
                            onClick = { refreshTrigger++ },
                            modifier = Modifier.testTag("perm_refresh_btn")
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
                Text(
                    text = "CORE BRIDGE PERMISSIONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = colors.primaryBright
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "VANTA only requests permissions needed for features you choose to enable. Optional permissions stay off until granted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 1. RECEIVE_SMS
                PermissionCard(
                    title = "SMS Reception",
                    permissionName = "android.permission.RECEIVE_SMS",
                    icon = Icons.Outlined.Sms,
                    state = if (smsGranted) PermState.GRANTED else PermState.MISSING,
                    whatItDoes = "Catches new text messages as they arrive on this phone.",
                    whyRequired = "Without this, VANTA can't see your incoming texts to forward them.",
                    whatHappensWithout = "SMS forwarding stays inactive.",
                    isPermanentlyDenied = PermissionHelper.isAnyPermanentlyDenied(
                        context, app.preferencesManager, listOf(Manifest.permission.RECEIVE_SMS)
                    ),
                    onRequest = {
                        launchOrOpenSettings(listOf(Manifest.permission.RECEIVE_SMS))
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. POST_NOTIFICATIONS
                PermissionCard(
                    title = "Foreground Notification",
                    permissionName = "android.permission.POST_NOTIFICATIONS",
                    icon = Icons.Outlined.Notifications,
                    state = if (notifGranted) PermState.GRANTED else PermState.MISSING,
                    whatItDoes = "Shows a quiet notification so Android knows VANTA is actively working.",
                    whyRequired = "Modern Android shuts down apps that try to run silently in the background.",
                    whatHappensWithout = "The bridge stops working a few minutes after you lock your phone.",
                    isPermanentlyDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        PermissionHelper.isAnyPermanentlyDenied(
                            context, app.preferencesManager, listOf(Manifest.permission.POST_NOTIFICATIONS)
                        ),
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            launchOrOpenSettings(listOf(Manifest.permission.POST_NOTIFICATIONS))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. READ_PHONE_STATE & READ_PHONE_NUMBERS
                PermissionCard(
                    title = "SIM Card Info",
                    permissionName = "android.permission.READ_PHONE_STATE",
                    icon = Icons.Outlined.PhoneAndroid,
                    state = if (phoneStateGranted) PermState.GRANTED else PermState.ATTENTION,
                    whatItDoes = "Identifies SIM 1 vs SIM 2, carrier names, subscription IDs, and live ringing state.",
                    whyRequired = "Enables accurate dual-SIM routing and incoming call alerts.",
                    whatHappensWithout = "VANTA cannot identify carrier slots or detect ringing calls.",
                    isPermanentlyDenied = PermissionHelper.isAnyPermanentlyDenied(
                        context,
                        app.preferencesManager,
                        listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)
                    ),
                    onRequest = {
                        launchOrOpenSettings(
                            listOf(
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.READ_PHONE_NUMBERS
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "OPTIONAL COMMUNICATION FEATURES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = colors.primaryBright
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 4. SEND_SMS
                PermissionCard(
                    title = "Telegram SMS Replies",
                    permissionName = "android.permission.SEND_SMS",
                    icon = Icons.AutoMirrored.Outlined.Send,
                    state = if (sendSmsGranted) PermState.GRANTED else PermState.ATTENTION,
                    whatItDoes = "Allows VANTA to dispatch SMS replies sent from your Telegram bot on SIM 1 or SIM 2.",
                    whyRequired = "Required by Android before sending SMS messages through your carrier.",
                    whatHappensWithout = "Tapping Reply in Telegram will report that sending permission is disabled.",
                    isPermanentlyDenied = PermissionHelper.isAnyPermanentlyDenied(
                        context, app.preferencesManager, listOf(Manifest.permission.SEND_SMS)
                    ),
                    onRequest = {
                        launchOrOpenSettings(listOf(Manifest.permission.SEND_SMS))
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 5. READ_CALL_LOG
                PermissionCard(
                    title = "Call Log & Caller ID",
                    permissionName = "android.permission.READ_CALL_LOG",
                    icon = Icons.AutoMirrored.Outlined.CallMade,
                    state = if (callLogGranted) PermState.GRANTED else PermState.ATTENTION,
                    whatItDoes = "Reads completed calls (incoming, outgoing, missed, rejected) and caller numbers during ringing.",
                    whyRequired = "Needed to forward call history and caller numbers to Telegram.",
                    whatHappensWithout = "Call Log Forwarding stays unavailable.",
                    isPermanentlyDenied = PermissionHelper.isAnyPermanentlyDenied(
                        context, app.preferencesManager, listOf(Manifest.permission.READ_CALL_LOG)
                    ),
                    onRequest = {
                        launchOrOpenSettings(listOf(Manifest.permission.READ_CALL_LOG))
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 6. READ_CONTACTS
                PermissionCard(
                    title = "Contact Name Lookup",
                    permissionName = "android.permission.READ_CONTACTS",
                    icon = Icons.Outlined.Contacts,
                    state = if (contactsGranted) PermState.GRANTED else PermState.ATTENTION,
                    whatItDoes = "Resolves sender and caller phone numbers to saved contact names in Telegram messages.",
                    whyRequired = "Optional privacy-friendly enrichment so Telegram shows who texted or called.",
                    whatHappensWithout = "Telegram shows raw phone numbers instead of contact names.",
                    isPermanentlyDenied = PermissionHelper.isAnyPermanentlyDenied(
                        context, app.preferencesManager, listOf(Manifest.permission.READ_CONTACTS)
                    ),
                    onRequest = {
                        launchOrOpenSettings(listOf(Manifest.permission.READ_CONTACTS))
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 7. BATTERY OPTIMIZATION
                PermissionCard(
                    title = "Battery Saver Exemption",
                    permissionName = "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                    icon = Icons.Outlined.BatteryAlert,
                    state = if (batteryIgnored) PermState.GRANTED else PermState.ATTENTION,
                    whatItDoes = "Tells Android's aggressive battery manager not to kill VANTA.",
                    whyRequired = "Ensures messages forward right away even after your phone sits untouched for hours.",
                    whatHappensWithout = "Messages might get delayed until you turn the screen on.",
                    isPermanentlyDenied = false,
                    onRequest = {
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            PermissionHelper.openAppSettings(context)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_system_settings_btn")
                ) {
                    Text("OPEN SYSTEM SETTINGS", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    permissionName: String,
    icon: ImageVector,
    state: PermState,
    whatItDoes: String,
    whyRequired: String,
    whatHappensWithout: String,
    isPermanentlyDenied: Boolean = false,
    onRequest: () -> Unit
) {
    val colors = LocalVantaColors.current

    val stateColor = when (state) {
        PermState.GRANTED -> colors.success
        PermState.MISSING -> colors.error
        PermState.ATTENTION -> colors.warning
    }

    val stateText = when (state) {
        PermState.GRANTED -> "GRANTED"
        PermState.MISSING -> "MISSING"
        PermState.ATTENTION -> "ACTION NEEDED"
    }

    GlowCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = permissionName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            ),
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(stateColor.copy(alpha = 0.18f))
                        .border(1.dp, stateColor.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stateText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = stateColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "• Purpose: $whatItDoes",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "• Why required: $whyRequired",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "• Without it: $whatHappensWithout",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )

            if (state != PermState.GRANTED) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = if (isPermanentlyDenied) "OPEN SETTINGS" else "GRANT PERMISSION",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
