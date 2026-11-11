package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.core.content.ContextCompat
import com.example.VantaApplication
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

    var refreshTrigger by remember { mutableStateOf(0) }

    fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    val smsGranted = remember(refreshTrigger) { isGranted(Manifest.permission.RECEIVE_SMS) }
    val notifGranted = remember(refreshTrigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(Manifest.permission.POST_NOTIFICATIONS)
        } else true
    }
    val phoneStateGranted = remember(refreshTrigger) { isGranted(Manifest.permission.READ_PHONE_STATE) }
    val batteryIgnored = remember(refreshTrigger) { isBatteryOptimizationIgnored() }

    val singlePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshTrigger++
        app.simManager.refreshSims()
    }

    val multiplePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTrigger++
        app.simManager.refreshSims()
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = colors.primaryBright
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
            Text(
                text = "WHY WE ASK FOR THESE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = colors.primaryBright
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No creepy stuff here. VANTA only asks for what it actually needs to catch incoming SMS and deliver them to your Telegram bot.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. RECEIVE_SMS
            PermissionCard(
                title = "SMS Reception",
                permissionName = "android.permission.RECEIVE_SMS",
                icon = Icons.Default.Sms,
                state = if (smsGranted) PermState.GRANTED else PermState.MISSING,
                whatItDoes = "Catches new text messages as they arrive on this phone.",
                whyRequired = "Without this, VANTA can't see your incoming texts to forward them.",
                whatHappensWithout = "Nothing gets forwarded.",
                onRequest = {
                    singlePermLauncher.launch(Manifest.permission.RECEIVE_SMS)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. POST_NOTIFICATIONS
            PermissionCard(
                title = "Foreground Notification",
                permissionName = "android.permission.POST_NOTIFICATIONS",
                icon = Icons.Default.Notifications,
                state = if (notifGranted) PermState.GRANTED else PermState.MISSING,
                whatItDoes = "Shows a quiet notification so Android knows VANTA is actively working.",
                whyRequired = "Modern Android shuts down apps that try to run silently in the background.",
                whatHappensWithout = "The bridge stops working a few minutes after you lock your phone.",
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        singlePermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3. READ_PHONE_STATE & READ_PHONE_NUMBERS
            PermissionCard(
                title = "SIM Card Info",
                permissionName = "android.permission.READ_PHONE_STATE",
                icon = Icons.Default.PhoneAndroid,
                state = if (phoneStateGranted) PermState.GRANTED else PermState.ATTENTION,
                whatItDoes = "Identifies whether an incoming text arrived on SIM 1 or SIM 2.",
                whyRequired = "Lets you choose which SIM cards to forward.",
                whatHappensWithout = "VANTA won't be able to tell your SIM slots apart.",
                onRequest = {
                    multiplePermLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_PHONE_NUMBERS
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 4. BATTERY OPTIMIZATION
            PermissionCard(
                title = "Battery Saver Exemption",
                permissionName = "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                icon = Icons.Default.BatteryAlert,
                state = if (batteryIgnored) PermState.GRANTED else PermState.ATTENTION,
                whatItDoes = "Tells Android's aggressive battery manager not to kill VANTA.",
                whyRequired = "Ensures messages forward right away even after your phone sits untouched for hours.",
                whatHappensWithout = "Messages might get delayed until you turn the screen on.",
                onRequest = {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val appIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(appIntent)
                    }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // System App Info Button
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_system_settings_btn")
            ) {
                Text("OPEN SYSTEM SETTINGS")
            }

            Spacer(modifier = Modifier.height(24.dp))
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
                            .background(colors.primaryDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colors.primaryBright,
                            modifier = Modifier.size(20.dp)
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
                        .background(stateColor.copy(alpha = 0.2f))
                        .border(1.dp, stateColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
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
                        containerColor = colors.primary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text(
                        text = "GRANT PERMISSION",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
