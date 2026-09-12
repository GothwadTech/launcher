package com.gothwad.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.gothwad.launcher.R
import com.gothwad.launcher.data.BackgroundMediaState
import com.gothwad.launcher.data.BluetoothDeviceStatus
import com.gothwad.launcher.data.NetStatus
import com.gothwad.launcher.data.WeatherData

@Composable
fun StatusBar(
    net: NetStatus,
    bt: BluetoothDeviceStatus,
    weather: WeatherData,
    backgroundMedia: BackgroundMediaState = BackgroundMediaState(),
    time: String,
    date: String,
    showVpn: Boolean,
    glass: Boolean,
    notificationCount: Int,
    hasNotificationPermission: Boolean,
    onDashboardClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onVoiceSearchClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onWeatherClick: () -> Unit,
    onBackgroundMediaClick: () -> Unit = {},
    onNotificationsClick: () -> Unit,
    onVpnClick: () -> Unit,
    onNetworkClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val isNarrow = screenWidth < 650
    val padH = if (isNarrow) 10.dp else 40.dp
    val padV = if (isNarrow) 8.dp else 18.dp

    // Symmetrical glass panels for both Left and Right clusters — matching dock look & roundness
    val cluster = if (glass) {
        Modifier
            .clip(SmoothCornerShape(LocalCornerRadius.current))
            .background(Color(0xB3121418))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = if (isNarrow) 6.dp else 10.dp, vertical = 4.dp)
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = padH, vertical = padV),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // TOP-LEFT CARD: Control Center, Voice Search, Remote Battery & Live Weather
        Row(
            modifier = cluster,
            horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 2.dp else 4.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusIcon(
                icon = AppIcons.Dashboard,
                active = true,
                contentDescription = stringResource(R.string.cd_dashboard),
                onClick = onDashboardClick,
            )
            StatusIcon(
                icon = AppIcons.Search,
                active = true,
                contentDescription = stringResource(R.string.cd_search),
                onClick = onSearchClick,
            )
            StatusIcon(
                icon = AppIcons.Mic,
                active = true,
                contentDescription = stringResource(R.string.cd_voice_search),
                onClick = onVoiceSearchClick,
            )
            if (!isNarrow || screenWidth >= 400) {
                BluetoothStatusPill(
                    bt = bt,
                    onClick = onBluetoothClick,
                )
            }
            WeatherStatusPill(
                weather = weather,
                onClick = onWeatherClick,
            )
        }

        // TOP-RIGHT CARD: Background Media, VPN, Network (Wi-Fi/Ethernet), Notifications, Settings, Date & Clock
        Row(
            modifier = cluster,
            horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 3.dp else 6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (backgroundMedia.isPlaying) {
                BackgroundMediaStatusIcon(
                    media = backgroundMedia,
                    onClick = onBackgroundMediaClick,
                )
            }
            if (showVpn) {
                StatusIcon(
                    icon = AppIcons.Vpn,
                    active = net.vpn,
                    contentDescription = stringResource(R.string.cd_vpn),
                    onClick = onVpnClick,
                )
            }
            StatusIcon(
                icon = when {
                    net.ethernet -> AppIcons.Ethernet
                    net.wifi -> AppIcons.Wifi
                    else -> AppIcons.WifiOff
                },
                active = net.connected,
                contentDescription = stringResource(R.string.cd_network),
                onClick = onNetworkClick,
            )
            NotificationStatusIcon(
                count = notificationCount,
                hasPermission = hasNotificationPermission,
                onClick = onNotificationsClick,
            )
            StatusIcon(
                icon = AppIcons.Gear,
                active = true,
                contentDescription = stringResource(R.string.cd_settings),
                onClick = onSettingsClick,
            )
            if (date.isNotEmpty() && !isNarrow) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = time,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = Color.White,
                modifier = Modifier.padding(start = if (isNarrow) 4.dp else 8.dp),
            )
        }
    }
}

@Composable
private fun BackgroundMediaStatusIcon(
    media: BackgroundMediaState,
    onClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = (if (media.isStockAdCandidate) Color(0xFFE53935) else Color(0xFF1E88E5)).copy(alpha = 0.22f),
            focusedContainerColor = Color.White.copy(alpha = 0.25f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer { alpha = alphaAnim },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (media.isStockAdCandidate) AppIcons.Shield else AppIcons.Equalizer,
                contentDescription = if (media.isStockAdCandidate) "Background Ad Detected" else "Background Audio Playing",
                tint = if (media.isStockAdCandidate) Color(0xFFFF7043) else Color(0xFF81D4FA),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun BluetoothStatusPill(
    bt: BluetoothDeviceStatus,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = AppIcons.Bluetooth,
                contentDescription = stringResource(R.string.cd_bluetooth),
                tint = Color(0xFF64B5F6).copy(alpha = if (bt.connected) 0.95f else 0.45f),
                modifier = Modifier.size(18.dp),
            )
            if (bt.batteryLevel >= 0) {
                Text(
                    text = "${bt.batteryLevel}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = if (bt.batteryLevel in 0..20) Color(0xFFE57373) else Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun WeatherStatusPill(
    weather: WeatherData,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val icon = when (weather.condition.lowercase()) {
                "rain", "showers", "thunderstorm" -> AppIcons.Rain
                "partly cloudy", "foggy" -> AppIcons.Cloud
                else -> AppIcons.Sun
            }
            val tint = if (weather.condition.lowercase() == "clear") Color(0xFFFFD54F) else Color(0xFF90CAF9)
            Icon(
                imageVector = icon,
                contentDescription = stringResource(R.string.cd_weather),
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = weather.temp,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = Color.White.copy(alpha = 0.95f),
            )
        }
    }
}

@Composable
private fun NotificationStatusIcon(
    count: Int,
    hasPermission: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.18f),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (count > 0) AppIcons.BellActive else AppIcons.Bell,
                contentDescription = stringResource(R.string.cd_notifications),
                tint = Color.White.copy(alpha = if (count > 0 || !hasPermission) 0.95f else 0.45f),
                modifier = Modifier.size(22.dp),
            )
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .size(if (count > 9) 16.dp else 14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (count > 9) "9+" else count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        ),
                        color = Color.White,
                    )
                }
            } else if (!hasPermission) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB300)),
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(
    icon: ImageVector,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.18f),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (active) 0.95f else 0.35f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
