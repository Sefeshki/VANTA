package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.EventLogger
import com.example.data.model.LogCategory
import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import com.example.ui.theme.LocalVantaColors

@Composable
fun WoodLogIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val strokeWidth = 1.6f
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Outer tree bark ring
        drawCircle(
            color = tint,
            radius = cx - 1f,
            style = Stroke(width = strokeWidth)
        )
        // Mid annual growth ring
        drawCircle(
            color = tint.copy(alpha = 0.65f),
            radius = cx * 0.6f,
            style = Stroke(width = strokeWidth * 0.9f)
        )
        // Center core
        drawCircle(
            color = tint,
            radius = cx * 0.22f
        )
    }
}

@Composable
fun CyberTerminal(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVantaColors.current
    var selectedFilter by remember { mutableStateOf<LogCategory?>(null) }
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, selectedFilter) {
        if (selectedFilter == null) logs else logs.filter { it.category == selectedFilter }
    }

    val terminalShape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(terminalShape)
            .background(Color(0xFF07070D))
            .border(1.dp, colors.borderGlow.copy(alpha = 0.7f), terminalShape)
            .padding(14.dp)
            .testTag("cyber_terminal")
    ) {
        // Header with tree-style LOG icon treatment
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WoodLogIcon(tint = colors.primaryBright)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LOG",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "🪵",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusDot(isActive = true, size = 6.dp)
            }

            IconButton(
                onClick = onClear,
                modifier = Modifier.size(28.dp).testTag("clear_terminal_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Log",
                    tint = colors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TerminalFilterChip(
                label = "ALL",
                selected = selectedFilter == null,
                onClick = { selectedFilter = null }
            )
            listOf(
                LogCategory.SMS_DELIVERY,
                LogCategory.TELEGRAM,
                LogCategory.SERVICE,
                LogCategory.SIM,
                LogCategory.SECURITY
            ).forEach { cat ->
                val displayLabel = when (cat) {
                    LogCategory.SMS_DELIVERY -> "DELIVERY"
                    LogCategory.TELEGRAM -> "TELEGRAM"
                    LogCategory.SERVICE -> "SERVICE"
                    LogCategory.SIM -> "SIMS"
                    LogCategory.SECURITY -> "SECURITY"
                    else -> cat.name
                }
                TerminalFilterChip(
                    label = displayLabel,
                    selected = selectedFilter == cat,
                    onClick = { selectedFilter = if (selectedFilter == cat) null else cat }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Log display window
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF030305))
                .padding(8.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[ ALL QUIET • WAITING FOR ACTIVITY ]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colors.textMuted
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        TerminalLine(log = log)
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalVantaColors.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.primaryDark,
            selectedLabelColor = colors.primaryBright,
            containerColor = Color(0xFF111119),
            labelColor = colors.textMuted
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colors.border,
            selectedBorderColor = colors.primary
        ),
        modifier = Modifier.height(26.dp)
    )
}

@Composable
private fun TerminalLine(log: LogEntry) {
    val colors = LocalVantaColors.current

    val tagColor = when (log.category) {
        LogCategory.SYSTEM -> Color(0xFF38BDF8)
        LogCategory.TELEGRAM -> colors.primaryBright
        LogCategory.NETWORK -> Color(0xFF60A5FA)
        LogCategory.SIM -> Color(0xFFFBBF24)
        LogCategory.SERVICE -> Color(0xFF34D399)
        LogCategory.SMS_DELIVERY -> Color(0xFF4ADE80)
        LogCategory.SECURITY -> Color(0xFFF97316)
        LogCategory.ERROR -> colors.error
    }

    val textColor = when (log.level) {
        LogLevel.ERROR -> colors.error
        LogLevel.WARNING -> colors.warning
        LogLevel.SUCCESS -> Color(0xFFE2E8F0)
        LogLevel.INFO -> Color(0xFFCBD5E1)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "[${EventLogger.formatTime(log.timestamp)}]",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "[${log.category.name}]",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            ),
            color = tagColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = log.message,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = textColor,
            modifier = Modifier.weight(1f)
        )
    }
}
