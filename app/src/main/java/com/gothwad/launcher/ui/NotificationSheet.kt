package com.gothwad.launcher.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.gothwad.launcher.Actions
import com.gothwad.launcher.R
import com.gothwad.launcher.data.BackgroundMediaState
import com.gothwad.launcher.data.BackgroundMediaTracker
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.service.NotificationManagerBridge
import com.gothwad.launcher.service.TvNotificationItem

@Composable
fun NotificationSheet(
    config: LauncherConfig,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val notifications by NotificationManagerBridge.notifications.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(NotificationManagerBridge.isNotificationAccessGranted(context)) }

    val bgMedia by remember(context) {
        BackgroundMediaTracker.backgroundMediaFlow(context)
    }.collectAsStateWithLifecycle(initialValue = BackgroundMediaState())

    // Re-check permission if user returned from system settings
    LaunchedEffect(Unit) {
        hasPermission = NotificationManagerBridge.isNotificationAccessGranted(context)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            ((dialogView.parent as? DialogWindowProvider)?.window)?.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
            )
        }

        ScaledUi(uiScaleFactor(config.uiScale, LocalConfiguration.current.screenWidthDp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .width(480.dp)
                        .fillMaxHeight(),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Header row
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = AppIcons.BellActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp),
                                )
                                Text(
                                    stringResource(R.string.notifications_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                )
                                if (notifications.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            notifications.size.toString(),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                            ),
                                            color = Color.Black,
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (hasPermission && notifications.any { it.isClearable }) {
                                    NotificationSmallButton(
                                        icon = AppIcons.ClearAll,
                                        label = stringResource(R.string.notifications_clear_all),
                                        onClick = { NotificationManagerBridge.clearAll() },
                                    )
                                }
                                NotificationSmallButton(
                                    icon = AppIcons.Close,
                                    label = stringResource(R.string.cancel),
                                    onClick = onDismiss,
                                )
                            }
                        }

                        // Content
                        if (!hasPermission) {
                            PermissionRequiredView(
                                onGrant = {
                                    Actions.openNotificationAccessSettings(context)
                                }
                            )
                        } else {
                            val firstFocus = remember { FocusRequester() }
                            LaunchedEffect(notifications.size, bgMedia.isPlaying) {
                                runCatching { firstFocus.requestFocus() }
                            }

                            LazyColumn(
                                Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (bgMedia.isPlaying) {
                                    item(key = "__bg_media__") {
                                        BackgroundMediaNotificationCard(
                                            media = bgMedia,
                                            onSilence = { BackgroundMediaTracker.silenceAudio(context) },
                                            onStop = { BackgroundMediaTracker.stopActiveMedia(context) },
                                        )
                                    }
                                }

                                if (notifications.isEmpty() && !bgMedia.isPlaying) {
                                    item(key = "__empty__") {
                                        EmptyNotificationsView(
                                            onSettings = { Actions.openNotificationAccessSettings(context) }
                                        )
                                    }
                                } else {
                                    items(notifications, key = { it.key }) { item ->
                                        NotificationCard(
                                            item = item,
                                            modifier = if (item.key == notifications.firstOrNull()?.key && !bgMedia.isPlaying) {
                                                Modifier.focusRequester(firstFocus)
                                            } else Modifier,
                                            onClick = {
                                                onDismiss()
                                                NotificationManagerBridge.launchNotification(context, item)
                                            },
                                            onDismiss = {
                                                NotificationManagerBridge.dismissNotification(item.key)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundMediaNotificationCard(
    media: BackgroundMediaState,
    onSilence: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        onClick = onSilence,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (media.isStockAdCandidate) Color(0xFFE53935).copy(alpha = 0.22f) else Color(0xFF1E88E5).copy(alpha = 0.2f),
            focusedContainerColor = if (media.isStockAdCandidate) Color(0xFFE53935).copy(alpha = 0.4f) else Color(0xFF1E88E5).copy(alpha = 0.35f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (media.isStockAdCandidate) AppIcons.Shield else AppIcons.Equalizer,
                        contentDescription = null,
                        tint = if (media.isStockAdCandidate) Color(0xFFFF7043) else Color(0xFF81D4FA),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (media.isStockAdCandidate) "Background OEM Ad Detected" else "Background Audio Active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = media.appName ?: "Unknown Audio Player",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSilence) {
                    Text("Silence Audio", fontSize = 12.sp)
                }
                Button(onClick = onStop) {
                    Text("Stop Media", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: TvNotificationItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.07f),
            focusedContainerColor = Color.White.copy(alpha = 0.20f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .then(
                if (isFocused) {
                    Modifier.border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        RoundedCornerShape(12.dp)
                    )
                } else Modifier
            ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // App info & time header
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (item.appIcon != null) {
                        Image(
                            bitmap = item.appIcon,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    } else {
                        Icon(
                            imageVector = AppIcons.Apps,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        item.appName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                    Text(
                        formatTimeAgo(item.postTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }

                if (item.isClearable) {
                    NotificationSmallButton(
                        icon = AppIcons.Close,
                        label = stringResource(R.string.notifications_dismiss),
                        onClick = onDismiss,
                        size = 28,
                        iconSize = 14,
                    )
                }
            }

            // Notification Title
            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Notification Content Body
            if (item.text.isNotBlank()) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!item.subText.isNullOrBlank() && item.subText != item.title) {
                Text(
                    text = item.subText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyNotificationsView(
    onSettings: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = AppIcons.Bell,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(56.dp),
            )
            Text(
                stringResource(R.string.notifications_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                stringResource(R.string.notifications_empty_sub),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.size(8.dp))
            Button(onClick = onSettings) {
                Text(stringResource(R.string.item_android_settings))
            }
        }
    }
}

@Composable
private fun PermissionRequiredView(
    onGrant: () -> Unit,
) {
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { f.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.BellActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                stringResource(R.string.notifications_permission_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Text(
                stringResource(R.string.notifications_permission_sub),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.size(6.dp))
            Button(
                onClick = onGrant,
                modifier = Modifier.focusRequester(f),
            ) {
                Text(stringResource(R.string.notifications_enable_btn))
            }
        }
    }
}

@Composable
private fun NotificationSmallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Int = 34,
    iconSize: Int = 18,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
    ) {
        Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize.dp))
        }
    }
}

private fun formatTimeAgo(timeMs: Long): String {
    val diff = System.currentTimeMillis() - timeMs
    if (diff < 60_000L) return "Just now"
    val mins = diff / 60_000L
    if (mins < 60) return "${mins}m ago"
    val hours = mins / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    return "${days}d ago"
}
