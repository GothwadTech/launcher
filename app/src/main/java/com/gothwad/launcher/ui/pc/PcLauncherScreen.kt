package com.gothwad.launcher.ui.pc

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gothwad.launcher.Actions
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.BluetoothDeviceStatus
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.MODE_TV
import com.gothwad.launcher.data.NetStatus
import com.gothwad.launcher.data.VIDEO_SPEEDS
import com.gothwad.launcher.data.WeatherData
import com.gothwad.launcher.service.TvNotificationItem
import com.gothwad.launcher.ui.AppIcons
import com.gothwad.launcher.ui.VideoWallpaper
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PcLauncherScreen(
    apps: List<AppEntry>,
    config: LauncherConfig,
    store: ConfigStore,
    accent: Color,
    net: NetStatus,
    bt: BluetoothDeviceStatus,
    weather: WeatherData?,
    time: String,
    date: String,
    notifications: List<TvNotificationItem>,
    wallpaperSharp: ImageBitmap?,
    presetBrush: Brush,
    aerialWallpaper: String?,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenNotifications: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var startMenuOpen by remember { mutableStateOf(false) }
    var desktopMenuExpanded by remember { mutableStateOf(false) }
    var desktopMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

    // Desktop icons: filter out hidden apps
    val visibleApps = remember(apps, config.hidden) {
        apps.filter { it.pkg !in config.hidden }
    }

    // Taskbar pinned packages: default or configured
    val pinnedPkgs = remember(config.pcTaskbarPinned, apps) {
        if (config.pcTaskbarPinned.isNotEmpty()) {
            config.pcTaskbarPinned
        } else {
            // Default pinned apps (first 5 common apps)
            visibleApps.take(5).map { it.pkg }
        }
    }

    val pinnedApps = remember(pinnedPkgs, visibleApps) {
        val byPkg = visibleApps.associateBy { it.pkg }
        pinnedPkgs.mapNotNull { byPkg[it] }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        // Windows / Super key opens start menu
                        Key.MetaLeft, Key.MetaRight -> {
                            startMenuOpen = !startMenuOpen
                            true
                        }
                        Key.Escape -> {
                            if (startMenuOpen) {
                                startMenuOpen = false
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // 1. Wallpaper
        val videoSpeed = VIDEO_SPEEDS[config.videoSpeed.coerceIn(0, VIDEO_SPEEDS.size - 1)]
        if (aerialWallpaper != null && net.connected) {
            VideoWallpaper(
                uri = aerialWallpaper,
                speed = videoSpeed,
                loop = false,
                coverBrush = presetBrush,
            )
        } else if (config.useVideoWallpaper && config.videoUri.isNotEmpty()) {
            VideoWallpaper(uri = config.videoUri, speed = videoSpeed, loop = true, coverBrush = presetBrush)
        } else if (wallpaperSharp != null) {
            Image(
                bitmap = wallpaperSharp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(presetBrush))
        }

        // 2. Desktop Area (Clicking background closes Start Menu, right-click opens Desktop Context Menu)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 50.dp) // Leave room for bottom taskbar
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (startMenuOpen) startMenuOpen = false
                            desktopMenuExpanded = false
                        },
                        onLongPress = { offset: Offset ->
                            desktopMenuOffset = DpOffset((offset.x / density.density).dp, (offset.y / density.density).dp)
                            desktopMenuExpanded = true
                        }
                    )
                }
        ) {
            // Desktop App Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 92.dp),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visibleApps, key = { it.pkg }) { app ->
                    DesktopIconItem(
                        app = app,
                        accent = accent,
                        onLaunch = {
                            startMenuOpen = false
                            onLaunchApp(app)
                        },
                        onRightClick = { onAppMenu(app) },
                    )
                }
            }

            // Desktop Context Menu (Windows style)
            DropdownMenu(
                expanded = desktopMenuExpanded,
                onDismissRequest = { desktopMenuExpanded = false },
                offset = desktopMenuOffset,
                modifier = Modifier
                    .background(Color(0xFF1E2129))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            ) {
                DropdownMenuItem(
                    text = { Text("Refresh Apps", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = { Icon(AppIcons.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        desktopMenuExpanded = false
                        // Trigger rescan
                    },
                )
                DropdownMenuItem(
                    text = { Text("Switch to TV Mode", color = Color(0xFF60A5FA), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(AppIcons.Tv, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(16.dp)) },
                    onClick = {
                        desktopMenuExpanded = false
                        scope.launch { store.update { it.copy(launcherMode = MODE_TV) } }
                    },
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text("Display & Wallpaper", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = { Icon(AppIcons.Display, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        desktopMenuExpanded = false
                        onOpenSettings()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Launcher Settings", color = Color.White, fontSize = 13.sp) },
                    leadingIcon = { Icon(AppIcons.Gear, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        desktopMenuExpanded = false
                        onOpenSettings()
                    },
                )
            }
        }

        // 3. Windows 11 / Linux Floating Start Menu
        AnimatedVisibility(
            visible = startMenuOpen,
            enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.94f) + slideInVertically(tween(220)) { it / 6 },
            exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.96f) + slideOutVertically(tween(160)) { it / 8 },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 58.dp),
        ) {
            StartMenuPopup(
                apps = visibleApps,
                accent = accent,
                onLaunchApp = {
                    startMenuOpen = false
                    onLaunchApp(it)
                },
                onOpenSearch = {
                    startMenuOpen = false
                    onOpenSearch()
                },
                onOpenSettings = {
                    startMenuOpen = false
                    onOpenSettings()
                },
                onSwitchToTv = {
                    startMenuOpen = false
                    scope.launch { store.update { it.copy(launcherMode = MODE_TV) } }
                },
                onAppMenu = {
                    startMenuOpen = false
                    onAppMenu(it)
                },
            )
        }

        // 4. Windows / Linux Bottom Taskbar
        Taskbar(
            startMenuOpen = startMenuOpen,
            pinnedApps = pinnedApps,
            accent = accent,
            time = time,
            date = date,
            net = net,
            notificationCount = notifications.size,
            onToggleStart = { startMenuOpen = !startMenuOpen },
            onOpenSearch = {
                startMenuOpen = false
                onOpenSearch()
            },
            onLaunchApp = {
                startMenuOpen = false
                onLaunchApp(it)
            },
            onSwitchToTv = {
                scope.launch { store.update { it.copy(launcherMode = MODE_TV) } }
            },
            onOpenNotifications = onOpenNotifications,
            onOpenSettings = onOpenSettings,
            onShowDesktop = { startMenuOpen = false },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Desktop Icon (Grid Item with hover highlight, icon, and label).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopIconItem(
    app: AppEntry,
    accent: Color,
    onLaunch: () -> Unit,
    onRightClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isHighlighted = isFocused || isHovered

    Column(
        modifier = Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> accent.copy(alpha = 0.28f)
                    isHovered -> Color.White.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }
            )
            .border(
                1.dp,
                when {
                    isFocused -> accent
                    isHovered -> Color.White.copy(alpha = 0.25f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(8.dp)
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onLaunch,
                onLongClick = onRightClick,
            )
            .focusable(interactionSource = interactionSource)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .scale(if (isHighlighted) 1.06f else 1f)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(accent.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = app.label.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                lineHeight = 14.sp,
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Windows / Linux Taskbar at the bottom.
 */
@Composable
private fun Taskbar(
    startMenuOpen: Boolean,
    pinnedApps: List<AppEntry>,
    accent: Color,
    time: String,
    date: String,
    net: NetStatus,
    notificationCount: Int,
    onToggleStart: () -> Unit,
    onOpenSearch: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onSwitchToTv: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowDesktop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        color = Color(0xF212141A),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Start Menu Button (Windows 4-tiles icon style)
            val startInteraction = remember { MutableInteractionSource() }
            val isStartHovered by startInteraction.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            startMenuOpen -> accent.copy(alpha = 0.35f)
                            isStartHovered -> Color.White.copy(alpha = 0.15f)
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        1.dp,
                        if (startMenuOpen) accent else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(interactionSource = startInteraction, indication = null, onClick = onToggleStart)
                    .pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Windows,
                    contentDescription = "Start",
                    tint = if (startMenuOpen) accent else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(6.dp))

            // Search Bar pill (Windows Search style)
            Surface(
                modifier = Modifier
                    .height(34.dp)
                    .width(180.dp)
                    .clip(CircleShape)
                    .clickable { onOpenSearch() }
                    .pointerHoverIcon(PointerIcon.Hand),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = "Search",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Type to search...",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = Color.White.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Taskbar Pinned Apps
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pinnedApps.forEach { app ->
                    TaskbarAppIcon(
                        app = app,
                        accent = accent,
                        onClick = { onLaunchApp(app) },
                    )
                }
            }

            // System Tray (Right Side)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // TV Mode Quick Switcher Icon
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF3B82F6).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .clickable { onSwitchToTv() }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AppIcons.Tv,
                            contentDescription = "Switch to TV",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "TV",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = Color(0xFF60A5FA),
                        )
                    }
                }

                // Wi-Fi / Ethernet Icon
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable { onOpenSettings() }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (net.ethernet) AppIcons.Ethernet else if (net.connected) AppIcons.Wifi else AppIcons.WifiOff,
                        contentDescription = "Network",
                        tint = if (net.connected) Color.White else Color(0xFFFF6B6B),
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Notifications Bell
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable { onOpenNotifications() }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (notificationCount > 0) AppIcons.BellActive else AppIcons.Bell,
                        contentDescription = "Notifications",
                        tint = if (notificationCount > 0) accent else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Date & Time (Stacked Windows style)
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenSettings() }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        ),
                        color = Color.White,
                    )
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.65f),
                        ),
                    )
                }

                // Show Desktop strip (Very right edge)
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onShowDesktop() }
                        .pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}

/**
 * Pinned Taskbar App Icon with hover state and active indicator dot.
 */
@Composable
private fun TaskbarAppIcon(
    app: AppEntry,
    accent: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .size(width = 44.dp, height = 46.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isFocused -> accent.copy(alpha = 0.25f)
                    isHovered -> Color.White.copy(alpha = 0.14f)
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = app.label.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // Running indicator pill
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(if (isHovered || isFocused) accent else Color.White.copy(alpha = 0.35f))
        )
    }
}

/**
 * Windows 11 / KDE style Floating Start Menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StartMenuPopup(
    apps: List<AppEntry>,
    accent: Color,
    onLaunchApp: (AppEntry) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchToTv: () -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(520.dp)
            .height(540.dp)
            .shadow(24.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xF7181A22),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            // Search Input Header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenSearch() }
                    .pointerHoverIcon(PointerIcon.Hand),
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = "Search",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Search for apps, settings, and files...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // "Pinned Apps" Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pinned",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    text = "${apps.size} Apps Installed",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.height(12.dp))

            // Apps Grid inside Start Menu
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(apps, key = { it.pkg }) { app ->
                    val interaction = remember { MutableInteractionSource() }
                    val isHovered by interaction.collectIsHoveredAsState()
                    val isFocused by interaction.collectIsFocusedAsState()

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isFocused -> accent.copy(alpha = 0.25f)
                                    isHovered -> Color.White.copy(alpha = 0.12f)
                                    else -> Color.Transparent
                                }
                            )
                            .pointerHoverIcon(PointerIcon.Hand)
                            .combinedClickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = { onLaunchApp(app) },
                                onLongClick = { onAppMenu(app) },
                            )
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = app.label,
                                modifier = Modifier.size(38.dp),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = app.label.take(1),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(12.dp))

            // Bottom Profile and Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // User / Device badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.25f))
                            .border(1.dp, accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = AppIcons.Desktop,
                            contentDescription = "PC User",
                            tint = accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PC User",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                        Text(
                            text = "Desktop Mode",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Switch to TV Mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f))
                            .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable { onSwitchToTv() }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Tv, contentDescription = "TV Mode", tint = Color(0xFF60A5FA), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Switch to TV", color = Color(0xFF60A5FA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Settings Button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { onOpenSettings() }
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = AppIcons.Gear,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
