package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.LocalVantaColors

/**
 * Observes lifecycle state so decorative infinite animations pause automatically
 * when the app is backgrounded or inactive.
 */
@Composable
fun rememberIsAppActive(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return isResumed
}

@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalVantaColors.current
    val isAppActive = rememberIsAppActive()
    val shape = RoundedCornerShape(20.dp)

    val shimmerPhase = if (isHighlighted && isAppActive) {
        rememberInfiniteTransition(label = "highlightShimmer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "phase"
        ).value
    } else 0.5f

    val strokeBrush = if (isHighlighted) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.35f + 0.25f * shimmerPhase),
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.38f - 0.12f * shimmerPhase)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.24f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.16f)
            )
        )
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = Color.White),
            onClick = onClick
        )
    } else Modifier

    Surface(
        modifier = modifier
            .clip(shape)
            .border(1.dp, strokeBrush, shape)
            .then(clickableModifier),
        shape = shape,
        color = Color(0xB808080A),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isHighlighted) 0.09f else 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(contentPadding)
        ) {
            content()
        }
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val tint = borderColor ?: Color.White
    val strokeBrush = Brush.linearGradient(
        colors = listOf(
            tint.copy(alpha = 0.34f),
            tint.copy(alpha = 0.10f),
            tint.copy(alpha = 0.20f)
        )
    )
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = Color.White),
            onClick = onClick
        )
    } else Modifier

    Surface(
        modifier = modifier
            .clip(shape)
            .border(borderWidth, strokeBrush, shape)
            .then(clickableModifier),
        shape = shape,
        color = Color(0xB808080A),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
                .padding(contentPadding)
        ) {
            content()
        }
    }
}

@Composable
fun StatusDot(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    activeColor: Color? = null,
    inactiveColor: Color? = null
) {
    val colors = LocalVantaColors.current
    val isAppActive = rememberIsAppActive()
    val targetColor = if (isActive) (activeColor ?: colors.success) else (inactiveColor ?: colors.textMuted)

    val pulseScale = if (isActive && isAppActive) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(1300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        ).value
    } else 1f

    Box(
        modifier = modifier.size(size * 1.6f),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(targetColor.copy(alpha = 0.30f))
            )
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(targetColor)
        )
    }
}

/**
 * Shared switch color tokens across VANTA:
 * - ON: bright phosphor green (#39FF14) track with pure white thumb
 * - OFF: pure black AMOLED track with subtle crisp border and muted silver thumb
 */
@Composable
fun vantaSwitchColors(): SwitchColors {
    val colors = LocalVantaColors.current
    return SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = colors.success,
        checkedBorderColor = colors.success,
        uncheckedThumbColor = colors.textMuted,
        uncheckedTrackColor = colors.background,
        uncheckedBorderColor = Color.White.copy(alpha = 0.24f),
        disabledCheckedThumbColor = colors.textMuted,
        disabledCheckedTrackColor = colors.background,
        disabledCheckedBorderColor = Color.White.copy(alpha = 0.14f),
        disabledUncheckedThumbColor = colors.textMuted.copy(alpha = 0.55f),
        disabledUncheckedTrackColor = colors.background,
        disabledUncheckedBorderColor = Color.White.copy(alpha = 0.14f)
    )
}

/**
 * Monochrome hamburger button at top-right that smoothly morphs into a
 * right-pointing close arrow when the right-side overlay menu opens.
 */
@Composable
fun AnimatedHamburgerArrow(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val strokeColor = tint ?: Color.White

    val progress by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "hamburgerProgress"
    )
    val rotation by animateFloatAsState(
        targetValue = if (isOpen) 180f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "hamburgerRotation"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = if (isOpen) "Close menu" else "Open menu" }
            .testTag("animated_hamburger_arrow")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp, color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val strokeWidth = 2.dp.toPx()

            fun lerpVal(start: Float, end: Float, fraction: Float): Float =
                start + (end - start) * fraction

            rotate(degrees = rotation) {
                // Morph to left-pointing inside rotation(180) => visual right-pointing arrow (->)
                val topStartX = lerpVal(w * 0.16f, w * 0.48f, progress)
                val topStartY = lerpVal(h * 0.27f, h * 0.22f, progress)
                val topEndX = lerpVal(w * 0.84f, w * 0.16f, progress)
                val topEndY = lerpVal(h * 0.27f, h * 0.50f, progress)
                drawLine(
                    color = strokeColor,
                    start = Offset(topStartX, topStartY),
                    end = Offset(topEndX, topEndY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = strokeColor,
                    start = Offset(w * 0.16f, h * 0.50f),
                    end = Offset(w * 0.84f, h * 0.50f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                val btmStartX = lerpVal(w * 0.16f, w * 0.48f, progress)
                val btmStartY = lerpVal(h * 0.73f, h * 0.78f, progress)
                val btmEndX = lerpVal(w * 0.84f, w * 0.16f, progress)
                val btmEndY = lerpVal(h * 0.73f, h * 0.50f, progress)
                drawLine(
                    color = strokeColor,
                    start = Offset(btmStartX, btmStartY),
                    end = Offset(btmEndX, btmEndY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Sleek monochrome GitHub Star action pill with subtle white specular border,
 * free of author branding for use inside the navigation menu.
 */
@Composable
fun GithubStarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = modifier
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick
            )
            .testTag("github_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.StarOutline,
                contentDescription = "Star VANTA on GitHub",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "STAR ON GITHUB",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun EventHorizonBackground(
    modifier: Modifier = Modifier,
    ambientIllumination: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    val animatedGlow by animateFloatAsState(
        targetValue = ambientIllumination.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "ambientIllumination"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.03f + 0.06f * animatedGlow),
                        Color.White.copy(alpha = 0.015f * animatedGlow),
                        Color.Black
                    )
                )
            )
    ) {
        content()
    }
}
