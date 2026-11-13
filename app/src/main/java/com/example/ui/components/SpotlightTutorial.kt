package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalVantaColors

data class TutorialStep(
    val title: String,
    val subtitle: String,
    val description: String
)

val VANTA_TUTORIAL_STEPS = listOf(
    TutorialStep(
        title = "Welcome to VANTA!",
        subtitle = "Built by Sefeshki",
        description = "With VANTA, you can send your SMS messages to your own personal Telegram bot and access them whenever you want!"
    ),
    TutorialStep(
        title = "Your SMS. Your Telegram.",
        subtitle = "The big power button",
        description = "Turn this on and VANTA will forward new messages from your selected SIMs to Telegram. Turn it off whenever you want to stop forwarding."
    ),
    TutorialStep(
        title = "The Vitals",
        subtitle = "Is everything humming along?",
        description = "Check here to see if your phone has internet, whether Telegram is happy, which SIMs are active, and if any messages are waiting in line."
    ),
    TutorialStep(
        title = "Pick Your SIMs",
        subtitle = "One, the other, or both",
        description = "Got two SIMs? Pick whichever one receives the texts you care about. If you want everything, just leave both turned on."
    ),
    TutorialStep(
        title = "Hook Up Telegram",
        subtitle = "A quick chat with @BotFather",
        description = "Grab a free bot token from @BotFather, drop it in, and send the 6-digit code to your bot. That pairs your account so only you get your messages."
    ),
    TutorialStep(
        title = "The LOG",
        subtitle = "Real-time event telemetry",
        description = "A clean real-time record of what VANTA is doing behind the scenes. We never log your actual message text, codes, or secrets."
    ),
    TutorialStep(
        title = "Stay Alive",
        subtitle = "Don't let Android sleep on the job",
        description = "Keeps VANTA awake with a quiet background notification so Android doesn't randomly kill it when you lock the phone."
    ),
    TutorialStep(
        title = "Auto Start",
        subtitle = "For when your phone reboots overnight",
        description = "If your secondary phone restarts, this picks right back up where you left off without needing you to touch the phone."
    ),
    TutorialStep(
        title = "Privacy & Control",
        subtitle = "Security on your terms",
        description = "Manage OTP privacy, check system health diagnostics, or wipe your credentials clean whenever you want."
    ),
    TutorialStep(
        title = "You're All Set!",
        subtitle = "Ready for launch",
        description = "Pair your bot, grant the SMS permission, ignite the energy core, and you're good to go."
    )
)

@Composable
fun SpotlightTutorialOverlay(
    isVisible: Boolean,
    onFinish: () -> Unit
) {
    if (!isVisible) return

    val colors = LocalVantaColors.current
    var currentStepIndex by remember { mutableIntStateOf(0) }
    val step = VANTA_TUTORIAL_STEPS[currentStepIndex]
    val isLast = currentStepIndex == VANTA_TUTORIAL_STEPS.lastIndex

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent click-through */ }
                )
                .padding(24.dp)
                .testTag("spotlight_tutorial_overlay"),
            contentAlignment = Alignment.Center
        ) {
            val shape = RoundedCornerShape(24.dp)
            val strokeBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.50f),
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.26f)
                )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xEB070709))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = strokeBrush,
                        shape = shape
                    )
                    .padding(24.dp)
            ) {
                // Header with step badge and close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "STEP ${currentStepIndex + 1} OF ${VANTA_TUTORIAL_STEPS.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = onFinish,
                        modifier = Modifier.size(36.dp).testTag("tutorial_skip_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Skip Tutorial",
                            tint = colors.textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                AnimatedContent(
                    targetState = currentStepIndex,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                    },
                    label = "spotlightStepAnim"
                ) { idx ->
                    val animatedStep = VANTA_TUTORIAL_STEPS[idx]
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Title and Icon
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = animatedStep.title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = animatedStep.subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primaryBright
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = animatedStep.description,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Step Indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    VANTA_TUTORIAL_STEPS.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (index == currentStepIndex) 16.dp else 6.dp, 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (index == currentStepIndex) colors.primaryBright
                                    else colors.border
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = { currentStepIndex-- },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier.testTag("tutorial_prev_btn")
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                    } else {
                        TextButton(
                            onClick = onFinish,
                            modifier = Modifier.testTag("tutorial_skip_text_btn")
                        ) {
                            Text("Skip Tour", color = colors.textMuted)
                        }
                    }

                    Button(
                        onClick = {
                            if (isLast) onFinish() else currentStepIndex++
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("tutorial_next_btn")
                    ) {
                        Text(
                            text = if (isLast) "Got It!" else "Next",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}
