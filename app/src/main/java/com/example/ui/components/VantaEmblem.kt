package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Animated VANTA logo emblem:
 * Displays the actual VANTA logo image at the center, surrounded by monochrome
 * circular orbital motion, rotating accretion rings, and a subtle pulsing white halo.
 */
@Composable
fun VantaBlackHoleEmblem(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    logoFraction: Float = 0.72f
) {
    val isAppActive = rememberIsAppActive()
    val shouldAnimate = animate && isAppActive

    val primaryRotation: Float
    val secondaryRotation: Float
    val pulseAlpha: Float

    if (shouldAnimate) {
        val infiniteTransition = rememberInfiniteTransition(label = "blackHoleEmblem")
        primaryRotation = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbit1"
        ).value
        secondaryRotation = infiniteTransition.animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbit2"
        ).value
        pulseAlpha = infiniteTransition.animateFloat(
            initialValue = 0.22f,
            targetValue = 0.42f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "horizonGlow"
        ).value
    } else {
        primaryRotation = -26f
        secondaryRotation = 38f
        pulseAlpha = 0.28f
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokePrimary = (minDim * 0.014f).coerceAtLeast(1.0.dp.toPx())
            val strokeSecondary = (minDim * 0.009f).coerceAtLeast(0.8.dp.toPx())

            // 1. Soft white orbital halo around the logo centerpiece while preserving dark core contrast
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = pulseAlpha * 0.14f),
                    0.42f to Color.White.copy(alpha = pulseAlpha * 0.50f),
                    0.72f to Color.White.copy(alpha = pulseAlpha * 0.18f),
                    1.0f to Color.Transparent,
                    center = center,
                    radius = minDim * 0.50f
                ),
                radius = minDim * 0.50f,
                center = center
            )

            // 2. Fine outer counter-rotating polar orbital ring
            val outerW = minDim * 0.92f
            val outerH = minDim * 0.40f
            rotate(degrees = 28f + secondaryRotation * 0.15f, pivot = center) {
                drawOval(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.White.copy(alpha = 0.42f),
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.52f),
                            Color.White.copy(alpha = 0.06f)
                        ),
                        center = center
                    ),
                    topLeft = Offset(center.x - outerW / 2f, center.y - outerH / 2f),
                    size = Size(outerW, outerH),
                    style = Stroke(width = strokeSecondary, cap = StrokeCap.Round)
                )
            }

            // 3. Primary circular orbital glow ring surrounding the logo
            val circularRadius = minDim * 0.43f
            rotate(degrees = primaryRotation, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.62f),
                            Color.White.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.48f),
                            Color.White.copy(alpha = 0.08f)
                        ),
                        center = center
                    ),
                    radius = circularRadius,
                    center = center,
                    style = Stroke(width = strokeSecondary * 1.15f)
                )
            }

            // 4. Primary tilted accretion orbit with soft luminous trail
            val innerW = minDim * 0.94f
            val innerH = minDim * 0.34f
            rotate(degrees = -26f, pivot = center) {
                drawOval(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(center.x - innerW / 2f, center.y - innerH / 2f),
                    size = Size(innerW, innerH),
                    style = Stroke(width = strokePrimary * 2.2f)
                )

                rotate(degrees = primaryRotation, pivot = center) {
                    drawOval(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.White.copy(alpha = 0.90f),
                                Color.White.copy(alpha = 0.32f),
                                Color.White.copy(alpha = 0.80f),
                                Color.White.copy(alpha = 0.18f)
                            ),
                            center = center
                        ),
                        topLeft = Offset(center.x - innerW / 2f, center.y - innerH / 2f),
                        size = Size(innerW, innerH),
                        style = Stroke(width = strokePrimary, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // 5. User's actual VANTA logo image as the crisp centerpiece
        Image(
            painter = painterResource(id = R.drawable.vanta_icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(logoFraction)
        )
    }
}
