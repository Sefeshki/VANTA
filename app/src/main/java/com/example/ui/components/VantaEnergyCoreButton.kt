package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalVantaColors

/**
 * Monochrome White Energy Core power switch.
 * - Dormant: Pure black interior with fine silver perimeter.
 * - Active: Animated white orbital energy field inside the core, gentle white halo,
 *   and an expanding activation wave when starting.
 */
@Composable
fun VpnToggleSwitch(
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVantaColors.current
    val isAppActive = rememberIsAppActive()

    // Smooth activation / dissipation energy multiplier
    val energyLevel by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isActive) 550 else 380,
            easing = FastOutSlowInEasing
        ),
        label = "energyLevel"
    )

    // One-shot expanding activation wave when VANTA transitions OFF -> ON
    var prevActive by remember { mutableStateOf(isActive) }
    val waveProgress = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive && !prevActive) {
            waveProgress.snapTo(0f)
            waveProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing)
            )
            waveProgress.snapTo(0f)
        }
        prevActive = isActive
    }

    // Internal flowing energy field (runs only when active and app is foregrounded)
    val flowAngle1: Float
    val flowAngle2: Float
    val haloPulse: Float

    if (energyLevel > 0.01f && isAppActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "energyCore")
        flowAngle1 = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "flow1"
        ).value
        flowAngle2 = infiniteTransition.animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "flow2"
        ).value
        haloPulse = infiniteTransition.animateFloat(
            initialValue = 0.78f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "haloPulse"
        ).value
    } else {
        flowAngle1 = 0f
        flowAngle2 = 0f
        haloPulse = 0.85f
    }

    val iconTint by animateColorAsState(
        targetValue = if (isActive) Color.White else Color(0xFF71717A),
        animationSpec = tween(350),
        label = "powerIconTint"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(210.dp)
                .testTag("main_vpn_toggle")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 92.dp, color = Color.White),
                    onClick = onToggle
                )
        ) {
            Canvas(modifier = Modifier.size(210.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val coreRadius = 74.dp.toPx()
                val haloRadius = 96.dp.toPx()

                // Expanding startup wave ring
                if (waveProgress.value > 0f && waveProgress.value < 1f) {
                    val p = waveProgress.value
                    val waveRadius = coreRadius + (haloRadius * 0.45f) * p
                    val waveAlpha = (1f - p) * 0.55f
                    drawCircle(
                        color = Color.White.copy(alpha = waveAlpha),
                        radius = waveRadius,
                        center = center,
                        style = Stroke(width = (2.5f * (1f - p) + 0.6f).dp.toPx())
                    )
                }

                // Subtle surrounding white corona when active
                if (energyLevel > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.24f * energyLevel * haloPulse),
                                Color.White.copy(alpha = 0.07f * energyLevel * haloPulse),
                                Color.Transparent
                            ),
                            center = center,
                            radius = haloRadius
                        ),
                        radius = haloRadius,
                        center = center
                    )
                }

                // Pure black central interior
                drawCircle(
                    color = Color.Black,
                    radius = coreRadius,
                    center = center
                )

                // Animated white energy field INSIDE the black button when running
                if (energyLevel > 0.01f) {
                    // Inner plasma mist inside the black core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.16f * energyLevel * haloPulse),
                                Color.White.copy(alpha = 0.06f * energyLevel),
                                Color.Transparent
                            ),
                            center = center,
                            radius = coreRadius * 0.94f
                        ),
                        radius = coreRadius * 0.94f,
                        center = center
                    )

                    // Primary flowing white orbital stream inside core
                    val r1 = coreRadius * 0.84f
                    rotate(degrees = flowAngle1, pivot = center) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.72f * energyLevel),
                                    Color.White.copy(alpha = 0.15f * energyLevel),
                                    Color.White.copy(alpha = 0.55f * energyLevel),
                                    Color.Transparent
                                ),
                                center = center
                            ),
                            startAngle = 0f,
                            sweepAngle = 290f,
                            useCenter = false,
                            topLeft = Offset(center.x - r1, center.y - r1),
                            size = Size(r1 * 2f, r1 * 2f),
                            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Counter-rotating inner elliptical energy filament
                    val rx2 = coreRadius * 0.76f
                    val ry2 = coreRadius * 0.44f
                    rotate(degrees = flowAngle2, pivot = center) {
                        drawOval(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.40f * energyLevel),
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.30f * energyLevel),
                                    Color.Transparent
                                ),
                                center = center
                            ),
                            topLeft = Offset(center.x - rx2, center.y - ry2),
                            size = Size(rx2 * 2f, ry2 * 2f),
                            style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Crisp outer white neon / dormant silver rim
                val rimBrush = if (energyLevel > 0.05f) {
                    Brush.sweepGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.90f * energyLevel + 0.22f * (1f - energyLevel)),
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.78f * energyLevel + 0.20f * (1f - energyLevel)),
                            Color.White.copy(alpha = 0.90f * energyLevel + 0.22f * (1f - energyLevel))
                        ),
                        center = center
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.26f),
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.22f)
                        )
                    )
                }

                drawCircle(
                    brush = rimBrush,
                    radius = coreRadius,
                    center = center,
                    style = Stroke(width = (1.3f + 1.1f * energyLevel).dp.toPx())
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = "Forwarding Switch",
                    tint = iconTint,
                    modifier = Modifier.size(46.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isActive) "ACTIVE" else "OFF",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.5.sp
                    ),
                    color = if (isActive) Color.White else colors.textMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isActive) "Bridge active • Forwarding incoming SMS" else "Tap core to start forwarding",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) colors.textPrimary else colors.textMuted
        )
    }
}
