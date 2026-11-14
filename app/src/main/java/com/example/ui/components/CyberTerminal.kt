package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.ui.graphics.Brush
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
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
    initiallyExpanded: Boolean = true
) {
    val colors = LocalVantaColors.current
    var selectedFilter by remember { mutableStateOf<LogCategory?>(null) }
    var isExpanded by remember { mutableStateOf(if (collapsible) initiallyExpanded else true) }
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, selectedFilter) {
        if (selectedFilter == null) logs else logs.filter { it.category == selectedFilter }
    }

    val terminalShape = RoundedCornerShape(16.dp)
    val terminalBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.28f),
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.18f)
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(terminalShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.05f),
                        Color(0xCC060608)
                    )
                )
            )
            .border(1.dp, terminalBorderBrush, terminalShape)
            .padding(14.dp)
            .testTag("cyber_terminal")
    ) {
        // Header with minimalist vector LOG emblem & optional collapse toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (collapsible) {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 2.dp)
                            .testTag("log_collapse_toggle")
                    } else {
                        Modifier
                    }
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WoodLogIcon(tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LOG",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusDot(isActive = true, size = 6.dp)
                if (collapsible) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${logs.size} EVENTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp
                        ),
                        color = colors.textMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!collapsible || isExpanded) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(32.dp).testTag("clear_terminal_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = "Clear Log",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (collapsible) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse Log" else "Expand Log",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        LogCategory.CALLS,
                        LogCategory.SECURITY
                    ).forEach { cat ->
                        val displayLabel = when (cat) {
                            LogCategory.SMS_DELIVERY -> "DELIVERY"
                            LogCategory.TELEGRAM -> "TELEGRAM"
                            LogCategory.SERVICE -> "SERVICE"
                            LogCategory.SIM -> "SIMS"
                            LogCategory.CALLS -> "CALLS"
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
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xDD000000))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
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
            selectedContainerColor = Color.White.copy(alpha = 0.14f),
            selectedLabelColor = Color.White,
            containerColor = Color(0xFF0A0A0D),
            labelColor = colors.textMuted
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.White.copy(alpha = 0.14f),
            selectedBorderColor = Color.White.copy(alpha = 0.65f)
        ),
        modifier = Modifier.height(26.dp)
    )
}

@Composable
private fun TerminalLine(log: LogEntry) {
    val colors = LocalVantaColors.current

    val tagColor = when (log.category) {
        LogCategory.SYSTEM -> Color(0xFFE4E4E7)
        LogCategory.TELEGRAM -> Color.White
        LogCategory.NETWORK -> Color(0xFFD4D4D8)
        LogCategory.SIM -> Color(0xFFA1A1AA)
        LogCategory.SERVICE -> Color.White
        LogCategory.SMS_DELIVERY -> colors.success
        LogCategory.CALLS -> Color(0xFFD4D4D8)
        LogCategory.SECURITY -> Color(0xFFE4E4E7)
        LogCategory.ERROR -> colors.error
    }

    val textColor = when (log.level) {
        LogLevel.ERROR -> colors.error
        LogLevel.WARNING -> colors.warning
        LogLevel.SUCCESS -> Color(0xFFFFFFFF)
        LogLevel.INFO -> Color(0xFFB4B4BE)
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
