package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SimCardAlert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.VantaApplication
import com.example.data.model.SimCardInfo
import com.example.data.model.SimSelectionMode
import com.example.ui.components.GlowCard
import com.example.ui.components.StatusDot
import com.example.ui.theme.LocalVantaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimManagementScreen(
    onNavigateBack: () -> Unit
) {
    val colors = LocalVantaColors.current
    val context = LocalContext.current
    val app = context.applicationContext as VantaApplication

    val sims by app.simManager.detectedSims.collectAsState()
    val prefs by app.preferencesManager.preferences.collectAsState()

    var selectedMode by remember(prefs.simSelectionMode) {
        mutableStateOf(prefs.simSelectionMode)
    }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SIM CARDS",
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
                        modifier = Modifier.testTag("sim_back_btn")
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
                        onClick = {
                            app.simManager.refreshSims()
                            Toast.makeText(context, "Checked SIM slots", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("sim_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh SIMs",
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
                text = "DETECTED SIM CARDS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = colors.primaryBright
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Display detected SIMs
            if (sims.isEmpty()) {
                GlowCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SimCardAlert,
                            contentDescription = null,
                            tint = colors.warning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "No SIM cards found. Check if your SIM is inserted properly and phone permissions are granted.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
                sims.forEach { sim ->
                    SimSlotCard(
                        sim = sim,
                        isSelectedInCurrentMode = when (selectedMode) {
                            SimSelectionMode.ALL -> true
                            SimSelectionMode.SIM1_ONLY -> sim.slotIndex == 0
                            SimSelectionMode.SIM2_ONLY -> sim.slotIndex == 1
                            SimSelectionMode.NONE -> false
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "WHICH SIMS SHOULD WE FORWARD?",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = colors.primaryBright
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Selection Mode Cards
            SelectionModeCard(
                mode = SimSelectionMode.ALL,
                title = "Both SIM Cards",
                subtitle = "Forward messages arriving on either SIM 1 or SIM 2",
                isSelected = selectedMode == SimSelectionMode.ALL,
                onClick = { selectedMode = SimSelectionMode.ALL }
            )
            Spacer(modifier = Modifier.height(10.dp))

            SelectionModeCard(
                mode = SimSelectionMode.SIM1_ONLY,
                title = "SIM 1 Only",
                subtitle = "Only forward messages received on your primary SIM",
                isSelected = selectedMode == SimSelectionMode.SIM1_ONLY,
                onClick = { selectedMode = SimSelectionMode.SIM1_ONLY }
            )
            Spacer(modifier = Modifier.height(10.dp))

            SelectionModeCard(
                mode = SimSelectionMode.SIM2_ONLY,
                title = "SIM 2 Only",
                subtitle = "Only forward messages received on your secondary SIM",
                isSelected = selectedMode == SimSelectionMode.SIM2_ONLY,
                onClick = { selectedMode = SimSelectionMode.SIM2_ONLY }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Informational Notice on Phone Number Availability
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = colors.info,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Your carrier controls whether the phone number shows up here. If it says 'NUMBER UNAVAILABLE', don't worry—VANTA still forwards your texts without a hitch using the hardware slot.",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = colors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    app.simManager.applySelectionMode(selectedMode)
                    Toast.makeText(context, "Saved: ${selectedMode.label}", Toast.LENGTH_SHORT).show()
                    onNavigateBack()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_sim_selection_btn")
            ) {
                Text(
                    text = "SAVE SELECTION",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SimSlotCard(
    sim: SimCardInfo,
    isSelectedInCurrentMode: Boolean
) {
    val colors = LocalVantaColors.current
    val slotLabel = "SIM ${sim.slotIndex + 1}"

    GlowCard(
        isHighlighted = isSelectedInCurrentMode,
        modifier = Modifier.fillMaxWidth().testTag("sim_slot_card_${sim.slotIndex}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (sim.isAvailable) colors.primaryDark else colors.surfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SimCard,
                            contentDescription = null,
                            tint = if (sim.isAvailable) colors.primaryBright else colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = slotLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                        Text(
                            text = sim.carrierName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primaryBright
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelectedInCurrentMode) colors.primaryDark else colors.surfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isSelectedInCurrentMode) "FORWARDING" else "IGNORED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isSelectedInCurrentMode) colors.primaryBright else colors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Phone Number:",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
                Text(
                    text = sim.phoneNumber?.takeIf { it.isNotBlank() } ?: "NUMBER UNAVAILABLE",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (sim.phoneNumber != null) colors.textPrimary else colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun SelectionModeCard(
    mode: SimSelectionMode,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalVantaColors.current

    Surface(
        color = if (isSelected) colors.surfaceElevated else colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected) colors.primaryBright else colors.border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("sim_mode_${mode.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) colors.textPrimary else colors.textSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }

            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) colors.primaryBright else colors.textMuted,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
