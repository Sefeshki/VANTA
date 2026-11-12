package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalVantaColors

@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalVantaColors.current
    val shape = RoundedCornerShape(20.dp)

    val strokeBrush = Brush.linearGradient(
        colors = listOf(
            if (isHighlighted) colors.primaryBright.copy(alpha = 0.75f) else colors.primaryBright.copy(alpha = 0.40f),
            colors.primaryGlow.copy(alpha = 0.20f),
            colors.border.copy(alpha = 0.35f)
        )
    )

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = colors.primary),
            onClick = onClick
        )
    } else Modifier

    Surface(
        modifier = modifier
            .clip(shape)
            .border(1.dp, strokeBrush, shape)
            .then(clickableModifier),
        shape = shape,
        color = Color(0xCC0D0B18),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.primaryDark.copy(alpha = if (isHighlighted) 0.35f else 0.18f),
                            Color(0x0A0D0B18)
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
    val targetColor = if (isActive) (activeColor ?: colors.success) else (inactiveColor ?: colors.textMuted)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.35f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

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
                    .background(targetColor.copy(alpha = 0.35f))
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

@Composable
fun VpnToggleSwitch(
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVantaColors.current

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isActive) 0.85f else 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val activeColor by animateColorAsState(
        targetValue = if (isActive) colors.primary else colors.surfaceElevated,
        animationSpec = tween(400),
        label = "activeColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isActive) colors.primaryBright else colors.border,
        animationSpec = tween(400),
        label = "borderColor"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(190.dp)
                .testTag("main_vpn_toggle")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 95.dp, color = colors.primary),
                    onClick = onToggle
                )
        ) {
            // Outer Glowing Ring
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    colors.primary.copy(alpha = glowAlpha * 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Main Border Ring
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isActive) 3.dp else 2.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                borderColor,
                                borderColor.copy(alpha = 0.4f),
                                borderColor
                            )
                        ),
                        shape = CircleShape
                    )
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (isActive) colors.primaryDark else colors.surfaceElevated,
                                colors.background
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Forwarding Switch",
                        tint = if (isActive) colors.primaryBright else colors.textMuted,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isActive) "ACTIVE" else "OFF",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = if (isActive) colors.primaryBright else colors.textMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isActive) "Bridge is live. Texts are forwarding to Telegram." else "Tap to start forwarding your texts.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) colors.textPrimary else colors.textMuted
        )
    }
}

@Composable
fun CircularGithubButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalVantaColors.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = colors.primaryBright),
                    onClick = onClick
                )
                .testTag("github_button"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.github),
                contentDescription = "GitHub - Sefeshki",
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sefeshki",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.primaryBright
        )
    }
}

@Composable
fun AnimatedHamburgerArrow(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val colors = LocalVantaColors.current
    val strokeColor = tint ?: colors.primaryBright

    val progress by animateFloatAsState(
        targetValue = if (isOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "hamburgerProgress"
    )
    val rotation by animateFloatAsState(
        targetValue = if (isOpen) 180f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "hamburgerRotation"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .testTag("animated_hamburger_arrow")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp, color = colors.primary),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(24.dp)
        ) {
            val w = size.width
            val h = size.height
            val strokeWidth = 2.4.dp.toPx()

            fun lerpVal(start: Float, end: Float, fraction: Float): Float =
                start + (end - start) * fraction

            rotate(degrees = rotation) {
                // Top line / upper arrow wing
                val topStartX = lerpVal(w * 0.18f, w * 0.52f, progress)
                val topStartY = lerpVal(h * 0.28f, h * 0.22f, progress)
                val topEndX = lerpVal(w * 0.82f, w * 0.82f, progress)
                val topEndY = lerpVal(h * 0.28f, h * 0.50f, progress)
                drawLine(
                    color = strokeColor,
                    start = androidx.compose.ui.geometry.Offset(topStartX, topStartY),
                    end = androidx.compose.ui.geometry.Offset(topEndX, topEndY),
                    strokeWidth = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Middle line / arrow shaft
                val midStartX = w * 0.18f
                val midStartY = h * 0.50f
                val midEndX = w * 0.82f
                val midEndY = h * 0.50f
                drawLine(
                    color = strokeColor,
                    start = androidx.compose.ui.geometry.Offset(midStartX, midStartY),
                    end = androidx.compose.ui.geometry.Offset(midEndX, midEndY),
                    strokeWidth = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                // Bottom line / lower arrow wing
                val btmStartX = lerpVal(w * 0.18f, w * 0.52f, progress)
                val btmStartY = lerpVal(h * 0.72f, h * 0.78f, progress)
                val btmEndX = lerpVal(w * 0.82f, w * 0.82f, progress)
                val btmEndY = lerpVal(h * 0.72f, h * 0.50f, progress)
                drawLine(
                    color = strokeColor,
                    start = androidx.compose.ui.geometry.Offset(btmStartX, btmStartY),
                    end = androidx.compose.ui.geometry.Offset(btmEndX, btmEndY),
                    strokeWidth = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalVantaColors.current
    val strokeBrush = Brush.linearGradient(
        colors = listOf(
            (borderColor ?: colors.primaryBright).copy(alpha = 0.50f),
            colors.primaryGlow.copy(alpha = 0.25f),
            colors.border.copy(alpha = 0.30f)
        )
    )
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = colors.primary),
            onClick = onClick
        )
    } else Modifier

    Surface(
        modifier = modifier
            .clip(shape)
            .border(borderWidth, strokeBrush, shape)
            .then(clickableModifier),
        shape = shape,
        color = Color(0xCC0D0B18),
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.primaryDark.copy(alpha = 0.25f),
                            Color(0x0A0D0B18)
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
fun UpdateDialog(
    updateState: com.example.core.UpdateState,
    onDismiss: () -> Unit,
    onStartDownload: (com.example.core.GitHubRelease) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit,
    onCheckAgain: () -> Unit
) {
    val colors = LocalVantaColors.current

    androidx.compose.ui.window.Dialog(
        onDismissRequest = {
            if (updateState !is com.example.core.UpdateState.Downloading) {
                onDismiss()
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = updateState !is com.example.core.UpdateState.Downloading,
            dismissOnClickOutside = updateState !is com.example.core.UpdateState.Downloading
        )
    ) {
        val shape = RoundedCornerShape(22.dp)
        val strokeBrush = Brush.linearGradient(
            colors = listOf(
                colors.primaryBright.copy(alpha = 0.65f),
                colors.primaryGlow.copy(alpha = 0.35f),
                colors.border.copy(alpha = 0.3f)
            )
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.2.dp, strokeBrush, shape),
            shape = shape,
            color = Color(0xF00D0B18)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.primaryDark.copy(alpha = 0.35f),
                                Color(0x0F0D0B18)
                            )
                        )
                    )
                    .padding(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VANTA UPDATE",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            ),
                            color = colors.primaryBright
                        )

                        when (updateState) {
                            is com.example.core.UpdateState.UpdateAvailable -> {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.primary.copy(alpha = 0.25f),
                                    border = BorderStroke(1.dp, colors.primaryBright.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = updateState.release.tagName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = colors.primaryBright,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            else -> {}
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when (updateState) {
                        is com.example.core.UpdateState.UpdateAvailable -> {
                            val release = updateState.release
                            val sizeMb = if (release.apkSizeBytes > 0) {
                                String.format(java.util.Locale.US, "%.1f MB", release.apkSizeBytes / (1024f * 1024f))
                            } else "Direct APK"

                            Text(
                                text = "A new version of VANTA is available on GitHub.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Current: v${updateState.currentVersion}  •  Latest: ${release.tagName}  •  $sizeMb",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = colors.textMuted
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Release Notes:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.primaryBright
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x33000000))
                                    .border(1.dp, colors.border.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                ) {
                                    Text(
                                        text = release.releaseNotes.ifBlank { "Performance improvements and bug fixes." },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                androidx.compose.material3.TextButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.testTag("update_later_btn")
                                ) {
                                    Text("LATER", color = colors.textMuted)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onStartDownload(release) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.primary,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("update_download_btn")
                                ) {
                                    Text("DOWNLOAD & INSTALL")
                                }
                            }
                        }

                        is com.example.core.UpdateState.Downloading -> {
                            Text(
                                text = "Downloading update from GitHub...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            if (updateState.totalBytes > 0) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { updateState.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = colors.primaryBright,
                                    trackColor = colors.surfaceElevated
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val dlMb = updateState.downloadedBytes / (1024f * 1024f)
                                val totMb = updateState.totalBytes / (1024f * 1024f)
                                val pct = (updateState.progress * 100).toInt()
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f MB / %.1f MB (%d%%)", dlMb, totMb, pct),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.textMuted
                                )
                            } else {
                                androidx.compose.material3.LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = colors.primaryBright,
                                    trackColor = colors.surfaceElevated
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = onCancelDownload,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("update_cancel_btn")
                                ) {
                                    Text("CANCEL")
                                }
                            }
                        }

                        is com.example.core.UpdateState.ReadyToInstall -> {
                            Text(
                                text = "Update downloaded successfully!",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.success
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap install to launch the Android package installer for ${updateState.release.tagName}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                androidx.compose.material3.TextButton(
                                    onClick = onDismiss
                                ) {
                                    Text("DISMISS", color = colors.textMuted)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onInstall(updateState.apkFile) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.success,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("update_install_now_btn")
                                ) {
                                    Text("INSTALL NOW", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        is com.example.core.UpdateState.UpToDate -> {
                            Text(
                                text = "You're running the latest version of VANTA (v${updateState.currentVersion}).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("OK")
                                }
                            }
                        }

                        is com.example.core.UpdateState.Error -> {
                            Text(
                                text = "Update Check Failed",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.error
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = updateState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                androidx.compose.material3.TextButton(
                                    onClick = onDismiss
                                ) {
                                    Text("DISMISS", color = colors.textMuted)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onCheckAgain,
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("RETRY")
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

