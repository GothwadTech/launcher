package com.gothwad.launcher.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.gothwad.launcher.data.BackgroundMediaState
import com.gothwad.launcher.data.BackgroundMediaTracker

/**
 * Dialog shown when clicking the Background Media / Ad indicator icon in the header.
 * Allows user to silence running background ads, stop media, or kill background processes.
 */
@Composable
fun BackgroundMediaDialog(
    mediaState: BackgroundMediaState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF14171F).copy(alpha = 0.95f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (mediaState.isStockAdCandidate) Color(0xFFE53935).copy(alpha = 0.2f)
                                else Color(0xFF4C8DF6).copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (mediaState.isStockAdCandidate) AppIcons.Shield else AppIcons.Equalizer,
                            contentDescription = null,
                            tint = if (mediaState.isStockAdCandidate) Color(0xFFFF5252) else Color(0xFF8AB4F8),
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (mediaState.isStockAdCandidate) "Background Ad Shield" else "Background Audio Active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = if (mediaState.isStockAdCandidate) "Detected potential OEM / Operator background ad"
                            else "An app is currently playing audio in background",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                }

                // Info Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Source: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                            Text(
                                text = mediaState.appName ?: "Unknown Audio Player",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }

                        if (!mediaState.title.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Title: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                                Text(
                                    text = mediaState.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }

                        if (!mediaState.packageName.isNullOrBlank()) {
                            Text(
                                text = mediaState.packageName,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = Color.White.copy(alpha = 0.4f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Actions
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1. Silence Audio (Mute Ad)
                    Surface(
                        onClick = {
                            BackgroundMediaTracker.silenceAudio(context)
                            Toast.makeText(context, "Audio silenced", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFFE53935).copy(alpha = 0.25f),
                            focusedContainerColor = Color(0xFFE53935),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(AppIcons.Mute, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("Silence Audio (Mute Ad)", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // 2. Stop Player
                    Surface(
                        onClick = {
                            BackgroundMediaTracker.stopActiveMedia(context)
                            Toast.makeText(context, "Media playback stopped", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.22f),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(AppIcons.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text("Stop Media Playback", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                    }

                    // 3. Kill Background Process (if package known)
                    if (!mediaState.packageName.isNullOrBlank()) {
                        Surface(
                            onClick = {
                                BackgroundMediaTracker.killPackage(context, mediaState.packageName)
                                Toast.makeText(context, "Background process killed", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color.White.copy(alpha = 0.22f),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(AppIcons.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Text("Kill Background Process", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                        }

                        // 4. Open App Info
                        Surface(
                            onClick = {
                                BackgroundMediaTracker.openAppInfo(context, mediaState.packageName)
                                onDismiss()
                            },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                focusedContainerColor = Color.White.copy(alpha = 0.22f),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(AppIcons.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Text("Open App Settings / Force Stop", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            }
                        }
                    }

                    // 5. Dismiss
                    Surface(
                        onClick = onDismiss,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.White.copy(alpha = 0.15f),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Close", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}
