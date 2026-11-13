package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.EventHorizonBackground
import com.example.ui.theme.LocalVantaColors
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val colors = LocalVantaColors.current
    val context = LocalContext.current

    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(700, easing = FastOutSlowInEasing)
        )
        delay(1200)
        onSplashFinished()
    }

    EventHorizonBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onSplashFinished() }
                .testTag("splash_screen"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .scale(scale.value)
                    .alpha(alpha.value)
            ) {
                // Glowing Official Icon with subtle radial theme halo
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(188.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(188.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        colors.primaryGlow.copy(alpha = 0.30f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Image(
                        painter = painterResource(id = R.drawable.vanta_icon),
                        contentDescription = "VANTA Icon",
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                    )
                }

            Spacer(modifier = Modifier.height(24.dp))

            // Cinematic VANTA Wordmark
            Text(
                text = "VANTA",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 8.sp
                ),
                color = colors.textPrimary
            )

            Text(
                text = "SMS BRIDGE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp
                ),
                color = colors.primaryBright
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Creator Attribution: "Made by Sefeshki" (with "Sefeshki" in theme accent)
            val attributionString = buildAnnotatedString {
                append("Made by ")
                withStyle(
                    style = SpanStyle(
                        color = colors.primaryBright,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append("Sefeshki")
                }
            }

            Text(
                text = attributionString,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Clickable GitHub Link
            Text(
                text = "https://github.com/Sefeshki/",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDecoration = TextDecoration.Underline
                ),
                color = colors.textMuted,
                modifier = Modifier
                    .clickable {
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sefeshki/"))
                            context.startActivity(browserIntent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    .testTag("splash_github_link")
            )
        }
    }
    }
}
