package com.gothwad.launcher.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.gothwad.launcher.Actions
import com.gothwad.launcher.R
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.BluetoothDeviceStatus
import com.gothwad.launcher.data.NetStatus
import com.gothwad.launcher.data.TvControlHelper

@Composable
fun QuickDashboardDialog(
    net: NetStatus,
    bt: BluetoothDeviceStatus,
    apps: List<AppEntry>,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var volPair by remember { mutableStateOf(TvControlHelper.getVolume(context)) }
    var isMuted by remember { mutableStateOf(TvControlHelper.isMuted(context)) }

    fun refreshAudio() {
        volPair = TvControlHelper.getVolume(context)
        isMuted = TvControlHelper.isMuted(context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF14161C))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4C8DF6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.Dashboard, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            stringResource(R.string.control_center),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.25f),
                        ),
                    ) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Volume Bar & Controls
                Text(
                    text = "Audio Control",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF20232B))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Mute Button
                    Surface(
                        onClick = {
                            isMuted = TvControlHelper.toggleMute(context)
                            refreshAudio()
                        },
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isMuted) Color(0xFFE53935) else Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color(0xFF6BA5FF),
                        ),
                    ) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(if (isMuted) AppIcons.Mute else AppIcons.Volume, contentDescription = "Mute", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }

                    // Volume level indicator
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isMuted) "Muted" else "Volume ${volPair.first}/${volPair.second}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            val fraction = if (volPair.second > 0) (volPair.first.toFloat() / volPair.second).coerceIn(0f, 1f) else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(if (isMuted) Color(0xFFE53935) else Color(0xFF4C8DF6))
                            )
                        }
                    }

                    // - and + buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            onClick = {
                                val next = (volPair.first - 1).coerceAtLeast(0)
                                TvControlHelper.setVolume(context, next)
                                refreshAudio()
                            },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f)),
                        ) {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                Text("-", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            }
                        }
                        Surface(
                            onClick = {
                                val next = (volPair.first + 1).coerceAtMost(volPair.second)
                                TvControlHelper.setVolume(context, next)
                                refreshAudio()
                            },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f)),
                        ) {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                Text("+", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Device & System Quick Actions
                Text(
                    text = "System Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Wi-Fi / Network tile
                    Surface(
                        onClick = { Actions.openNetworkSettings(context) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF20232B), focusedContainerColor = Color(0xFF4C8DF6)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = if (net.wifi) AppIcons.Wifi else if (net.ethernet) AppIcons.Ethernet else AppIcons.WifiOff,
                                    contentDescription = null,
                                    tint = if (net.connected) Color(0xFF81C784) else Color(0xFFE57373),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = if (net.wifi) "Wi-Fi" else if (net.ethernet) "Ethernet" else "Offline",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                            if (net.ssid.isNotEmpty()) {
                                Text(
                                    text = net.ssid,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1
                                )
                            }
                            if (net.linkSpeedMbps > 0) {
                                Text(
                                    text = "${net.linkSpeedMbps} Mbps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }

                    // Remote & Bluetooth Tile
                    Surface(
                        onClick = {
                            runCatching {
                                context.startActivity(android.content.Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            }
                        },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF20232B), focusedContainerColor = Color(0xFF4C8DF6)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = AppIcons.Bluetooth,
                                    contentDescription = null,
                                    tint = Color(0xFF64B5F6),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = bt.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = if (bt.batteryLevel >= 0) "Battery: ${bt.batteryLevel}%" else "Connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (bt.batteryLevel in 0..20) Color(0xFFE57373) else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Bottom Quick Action Buttons (Standby, Android Settings, Launcher Settings)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = {
                            onDismiss()
                            onOpenSettings()
                        },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF20232B)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(AppIcons.Gear, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Settings", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }

                    Surface(
                        onClick = {
                            runCatching {
                                context.startActivity(android.content.Intent(Settings.ACTION_SETTINGS).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            }
                        },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF20232B)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(AppIcons.Display, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Android TV", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
