package com.example.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.GitHubRelease
import com.example.core.UpdateState
import com.example.ui.theme.LocalVantaColors
import java.io.File
import java.util.Locale

@Composable
fun UpdateDialog(
    updateState: UpdateState,
    onDismiss: () -> Unit,
    onStartDownload: (GitHubRelease) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onCheckAgain: () -> Unit
) {
    val colors = LocalVantaColors.current

    Dialog(
        onDismissRequest = {
            if (updateState !is UpdateState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = updateState !is UpdateState.Downloading,
            dismissOnClickOutside = updateState !is UpdateState.Downloading
        )
    ) {
        val shape = RoundedCornerShape(24.dp)
        val strokeBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.55f),
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.30f)
            )
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.dp, strokeBrush, shape),
            shape = shape,
            color = Color(0xEB070709)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
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
                            color = Color.White
                        )

                        if (updateState is UpdateState.UpdateAvailable) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
                            ) {
                                Text(
                                    text = updateState.release.tagName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when (updateState) {
                        is UpdateState.UpdateAvailable -> {
                            val release = updateState.release
                            val sizeMb = if (release.apkSizeBytes > 0) {
                                String.format(Locale.US, "%.1f MB", release.apkSizeBytes / (1024f * 1024f))
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
                                text = "Release Notes",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xAA000000))
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
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
                                TextButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.testTag("update_later_btn")
                                ) {
                                    Text("LATER", color = colors.textMuted)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onStartDownload(release) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("update_download_btn")
                                ) {
                                    Text("DOWNLOAD & INSTALL", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        is UpdateState.Downloading -> {
                            Text(
                                text = "Downloading update from GitHub...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            if (updateState.totalBytes > 0) {
                                LinearProgressIndicator(
                                    progress = { updateState.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color.White,
                                    trackColor = colors.surfaceElevated
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val dlMb = updateState.downloadedBytes / (1024f * 1024f)
                                val totMb = updateState.totalBytes / (1024f * 1024f)
                                val pct = (updateState.progress * 100).toInt()
                                Text(
                                    text = String.format(Locale.US, "%.1f MB / %.1f MB (%d%%)", dlMb, totMb, pct),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = colors.textMuted
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color.White,
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

                        is UpdateState.ReadyToInstall -> {
                            Text(
                                text = "Update downloaded successfully",
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
                                TextButton(onClick = onDismiss) {
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

                        is UpdateState.UpToDate -> {
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
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("OK", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        is UpdateState.Error -> {
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
                                TextButton(onClick = onDismiss) {
                                    Text("DISMISS", color = colors.textMuted)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onCheckAgain,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("RETRY", fontWeight = FontWeight.Bold)
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
