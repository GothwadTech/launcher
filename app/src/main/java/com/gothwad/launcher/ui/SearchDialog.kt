package com.gothwad.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.foundation.BorderStroke
import com.gothwad.launcher.R
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.LauncherConfig
import kotlinx.coroutines.delay

/**
 * TV Search Dialog supporting live app filtering, and secret code detection to reveal Hidden Apps.
 */
@Composable
fun SearchDialog(
    apps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    onLaunchApp: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { searchFocusRequester.requestFocus() }
    }

    // Check if entered query triggers the Hidden Apps Secret Vault
    val trimmedQuery = query.trim()
    val isSecretCodeMatch = remember(trimmedQuery, config.hideAppsCode, config.hideAppsPin, config.hidden) {
        if (trimmedQuery.isEmpty()) false
        else {
            val codeMatches = config.hideAppsCode.isNotEmpty() && trimmedQuery.equals(config.hideAppsCode, ignoreCase = true)
            val pinMatches = config.hideAppsPin.isNotEmpty() && trimmedQuery == config.hideAppsPin
            codeMatches || pinMatches
        }
    }

    // If secret code is matched: show ONLY hidden apps.
    // Otherwise: filter visible (non-hidden) apps by query.
    val displayedApps = remember(query, isSecretCodeMatch, apps, config.hidden) {
        if (isSecretCodeMatch) {
            apps.filter { it.pkg in config.hidden }
        } else if (trimmedQuery.isEmpty()) {
            apps.filter { it.pkg !in config.hidden }
        } else {
            apps.filter { it.pkg !in config.hidden && (it.label.contains(trimmedQuery, ignoreCase = true) || it.pkg.contains(trimmedQuery, ignoreCase = true)) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(horizontal = 48.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(760.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF14171F))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Top Search Bar Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(
                                width = if (isSecretCodeMatch) 2.dp else 1.dp,
                                color = if (isSecretCodeMatch) Color(0xFFFFB300) else Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = if (isSecretCodeMatch) AppIcons.LockOpen else AppIcons.Search,
                                contentDescription = null,
                                tint = if (isSecretCodeMatch) Color(0xFFFFB300) else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp),
                            )

                            Box(modifier = Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_apps_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.4f),
                                    )
                                }
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                    cursorBrush = SolidColor(if (isSecretCodeMatch) Color(0xFFFFB300) else accent),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { /* Handled live */ }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocusRequester),
                                )
                            }

                            if (query.isNotEmpty()) {
                                Surface(
                                    onClick = { query = "" },
                                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.12f),
                                        contentColor = Color.White,
                                    ),
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = AppIcons.Close,
                                            contentDescription = "Clear",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Close Button
                    Surface(
                        onClick = onDismiss,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White,
                            focusedContentColor = Color.White,
                        ),
                        modifier = Modifier.height(52.dp).padding(horizontal = 4.dp),
                    ) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.cancel),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                // Secret Vault Indicator Banner (if unlocked)
                if (isSecretCodeMatch) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFB300).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.LockOpen,
                            contentDescription = null,
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.secret_vault_unlocked, displayedApps.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB300),
                        )
                    }
                }

                // Results Grid
                if (displayedApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isSecretCodeMatch) "No hidden apps configured yet" else stringResource(R.string.search_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                    ) {
                        items(displayedApps, key = { it.pkg }) { app ->
                            val isLocked = config.appLockEnabled && app.pkg in config.lockedApps
                            SearchAppCard(
                                app = app,
                                isLocked = isLocked,
                                accent = if (isSecretCodeMatch) Color(0xFFFFB300) else accent,
                                onClick = {
                                    onLaunchApp(app)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchAppCard(
    app: AppEntry,
    isLocked: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(cardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = app.tile,
            focusedContainerColor = app.tile,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                BorderStroke(2.dp, accent),
                shape = cardShape,
            )
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            if (app.banner != null) {
                Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (app.icon != null) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Lock,
                        contentDescription = "Locked",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
    }
}
