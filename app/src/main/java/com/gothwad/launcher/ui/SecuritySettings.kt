package com.gothwad.launcher.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.gothwad.launcher.R
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import kotlinx.coroutines.launch

@Composable
private fun initialFocus(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(30) {
            if (runCatching { fr.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos {}
        }
    }
    return fr
}

@Composable
private fun SecuritySectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, start = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SecurityCheckMark(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(
                if (checked) Color(0xFF4C8DFF) else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .border(
                width = 2.dp,
                color = if (checked) Color(0xFF4C8DFF) else Color.White.copy(alpha = 0.4f),
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                AppIcons.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SecuritySettingsItem(
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    ListItem(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .padding(horizontal = 8.dp)
            .background(
                if (focused) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            ),
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = androidx.tv.material3.ListItemDefaults.colors(
            focusedContainerColor = Color.Transparent,
            focusedContentColor = Color.White,
        ),
    )
}

/**
 * Main Password & Security Screen matching native Android Settings look & feel.
 */
@Composable
fun SecurityScreen(
    config: LauncherConfig,
    apps: List<AppEntry>,
    store: ConfigStore,
    onBack: () -> Unit,
    onOpenLockedApps: () -> Unit,
    onOpenHiddenApps: () -> Unit,
    onSetupPin: (target: SecurityTarget, initialLength: Int) -> Unit,
    onVerifyPin: (target: SecurityTarget, pin: String, length: Int, onSuccess: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showSecretCodeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = Color.White.copy(alpha = 0.25f),
                ),
            ) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Back, contentDescription = "Back", modifier = Modifier.size(20.dp))
                }
            }
            Text(
                text = stringResource(R.string.security_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        val f = initialFocus()

        // ================= DEVICE LOCK =================
        SecuritySectionLabel(stringResource(R.string.device_lock_title))
        SecuritySettingsItem(
            selected = false,
            onClick = {
                if (config.deviceLockEnabled) {
                    // Disable device lock: verify PIN first
                    onVerifyPin(
                        SecurityTarget.DEVICE_LOCK,
                        config.deviceLockPin,
                        config.deviceLockPinLength,
                    ) {
                        scope.launch { store.update { it.copy(deviceLockEnabled = false) } }
                    }
                } else {
                    // Enable device lock: if no PIN set, prompt to set PIN
                    if (config.deviceLockPin.isEmpty()) {
                        onSetupPin(SecurityTarget.DEVICE_LOCK, config.deviceLockPinLength)
                    } else {
                        scope.launch { store.update { it.copy(deviceLockEnabled = true) } }
                    }
                }
            },
            modifier = Modifier.focusRequester(f),
            headlineContent = { Text(stringResource(R.string.device_lock_title)) },
            supportingContent = { Text(stringResource(R.string.device_lock_sub)) },
            leadingContent = { Icon(AppIcons.Lock, contentDescription = null) },
            trailingContent = { SecurityCheckMark(checked = config.deviceLockEnabled) },
        )

        if (config.deviceLockEnabled || config.deviceLockPin.isNotEmpty()) {
            SecuritySettingsItem(
                selected = false,
                onClick = {
                    onSetupPin(SecurityTarget.DEVICE_LOCK, config.deviceLockPinLength)
                },
                headlineContent = { Text(if (config.deviceLockPin.isEmpty()) stringResource(R.string.set_pin) else stringResource(R.string.change_pin)) },
                supportingContent = { Text("${config.deviceLockPinLength} Digits PIN") },
                leadingContent = { Icon(AppIcons.Key, contentDescription = null) },
            )
        }

        // ================= APP LOCK =================
        SecuritySectionLabel(stringResource(R.string.app_lock_title))
        SecuritySettingsItem(
            selected = false,
            onClick = {
                if (config.appLockEnabled) {
                    onVerifyPin(
                        SecurityTarget.APP_LOCK,
                        config.appLockPin,
                        config.appLockPinLength,
                    ) {
                        scope.launch { store.update { it.copy(appLockEnabled = false) } }
                    }
                } else {
                    if (config.appLockPin.isEmpty()) {
                        onSetupPin(SecurityTarget.APP_LOCK, config.appLockPinLength)
                    } else {
                        scope.launch { store.update { it.copy(appLockEnabled = true) } }
                    }
                }
            },
            headlineContent = { Text(stringResource(R.string.app_lock_title)) },
            supportingContent = { Text(stringResource(R.string.app_lock_sub)) },
            leadingContent = { Icon(AppIcons.Lock, contentDescription = null) },
            trailingContent = { SecurityCheckMark(checked = config.appLockEnabled) },
        )

        if (config.appLockEnabled || config.appLockPin.isNotEmpty()) {
            SecuritySettingsItem(
                selected = false,
                onClick = {
                    onSetupPin(SecurityTarget.APP_LOCK, config.appLockPinLength)
                },
                headlineContent = { Text(if (config.appLockPin.isEmpty()) stringResource(R.string.set_pin) else stringResource(R.string.change_pin)) },
                supportingContent = { Text("${config.appLockPinLength} Digits PIN") },
                leadingContent = { Icon(AppIcons.Key, contentDescription = null) },
            )

            SecuritySettingsItem(
                selected = false,
                onClick = onOpenLockedApps,
                headlineContent = { Text(stringResource(R.string.locked_apps_title)) },
                supportingContent = { Text(stringResource(R.string.locked_apps_count, config.lockedApps.size)) },
                leadingContent = { Icon(AppIcons.Apps, contentDescription = null) },
            )
        }

        // ================= HIDE APPS =================
        SecuritySectionLabel(stringResource(R.string.hide_apps_title))
        SecuritySettingsItem(
            selected = false,
            onClick = {
                if (config.hideAppsEnabled) {
                    onVerifyPin(
                        SecurityTarget.HIDE_APPS,
                        config.hideAppsPin,
                        config.hideAppsPinLength,
                    ) {
                        scope.launch { store.update { it.copy(hideAppsEnabled = false) } }
                    }
                } else {
                    if (config.hideAppsPin.isEmpty()) {
                        onSetupPin(SecurityTarget.HIDE_APPS, config.hideAppsPinLength)
                    } else {
                        scope.launch { store.update { it.copy(hideAppsEnabled = true) } }
                    }
                }
            },
            headlineContent = { Text(stringResource(R.string.hide_apps_title)) },
            supportingContent = { Text(stringResource(R.string.hide_apps_sub)) },
            leadingContent = { Icon(AppIcons.Hide, contentDescription = null) },
            trailingContent = { SecurityCheckMark(checked = config.hideAppsEnabled) },
        )

        if (config.hideAppsEnabled || config.hideAppsPin.isNotEmpty()) {
            SecuritySettingsItem(
                selected = false,
                onClick = {
                    onSetupPin(SecurityTarget.HIDE_APPS, config.hideAppsPinLength)
                },
                headlineContent = { Text(if (config.hideAppsPin.isEmpty()) stringResource(R.string.set_pin) else stringResource(R.string.change_pin)) },
                supportingContent = { Text("${config.hideAppsPinLength} Digits PIN") },
                leadingContent = { Icon(AppIcons.Key, contentDescription = null) },
            )

            SecuritySettingsItem(
                selected = false,
                onClick = { showSecretCodeDialog = true },
                headlineContent = { Text(stringResource(R.string.secret_code_title)) },
                supportingContent = {
                    val codeDisplay = config.hideAppsCode.ifEmpty { config.hideAppsPin.ifEmpty { "Not set" } }
                    Text("Code: $codeDisplay (Search bar trigger)")
                },
                leadingContent = { Icon(AppIcons.Search, contentDescription = null) },
            )

            SecuritySettingsItem(
                selected = false,
                onClick = {
                    if (config.hideAppsPin.isNotEmpty()) {
                        onVerifyPin(
                            SecurityTarget.HIDE_APPS,
                            config.hideAppsPin,
                            config.hideAppsPinLength,
                        ) {
                            onOpenHiddenApps()
                        }
                    } else {
                        onOpenHiddenApps()
                    }
                },
                headlineContent = { Text(stringResource(R.string.hidden_apps_title)) },
                supportingContent = { Text(stringResource(R.string.hidden_apps_count, config.hidden.size)) },
                leadingContent = { Icon(AppIcons.Hide, contentDescription = null) },
            )
        }
    }

    if (showSecretCodeDialog) {
        SecretCodeEditDialog(
            currentCode = config.hideAppsCode.ifEmpty { config.hideAppsPin },
            onSave = { newCode ->
                scope.launch { store.update { it.copy(hideAppsCode = newCode) } }
                showSecretCodeDialog = false
            },
            onDismiss = { showSecretCodeDialog = false },
        )
    }
}

/**
 * Screen to choose which apps to lock with App Lock PIN.
 */
@Composable
fun LockedAppsScreen(
    apps: List<AppEntry>,
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sortedApps = remember(apps) { apps.sortedBy { it.label.lowercase() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = Color.White.copy(alpha = 0.25f),
                ),
            ) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Back, contentDescription = "Back", modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text(
                    text = stringResource(R.string.locked_apps_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${config.lockedApps.size} apps selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val f = initialFocus()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(sortedApps, key = { it.pkg }) { app ->
                val isLocked = app.pkg in config.lockedApps
                SecuritySettingsItem(
                    selected = false,
                    onClick = {
                        val next = if (isLocked) config.lockedApps - app.pkg else config.lockedApps + app.pkg
                        scope.launch { store.update { it.copy(lockedApps = next) } }
                    },
                    modifier = if (sortedApps.indexOf(app) == 0) Modifier.focusRequester(f) else Modifier,
                    headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(app.pkg, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        if (app.icon != null) {
                            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                    },
                    trailingContent = { SecurityCheckMark(checked = isLocked) },
                )
            }
        }
    }
}

/**
 * Screen to choose which apps to completely hide from the launcher.
 */
@Composable
fun HiddenAppsScreen(
    apps: List<AppEntry>,
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sortedApps = remember(apps) { apps.sortedBy { it.label.lowercase() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Surface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = Color.White.copy(alpha = 0.25f),
                ),
            ) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Back, contentDescription = "Back", modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text(
                    text = stringResource(R.string.hidden_apps_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${config.hidden.size} apps hidden from launcher",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val f = initialFocus()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(sortedApps, key = { it.pkg }) { app ->
                val isHidden = app.pkg in config.hidden
                SecuritySettingsItem(
                    selected = false,
                    onClick = {
                        val next = if (isHidden) config.hidden - app.pkg else config.hidden + app.pkg
                        scope.launch { store.update { it.copy(hidden = next) } }
                    },
                    modifier = if (sortedApps.indexOf(app) == 0) Modifier.focusRequester(f) else Modifier,
                    headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(app.pkg, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        if (app.icon != null) {
                            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                    },
                    trailingContent = { SecurityCheckMark(checked = isHidden) },
                )
            }
        }
    }
}

/**
 * Dialog to set or edit the custom Secret Code to reveal Hidden Apps in Search bar.
 */
@Composable
fun SecretCodeEditDialog(
    currentCode: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var codeText by remember { mutableStateOf(currentCode) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        runCatching { focusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF161A23))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.secret_code_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )

                Text(
                    text = stringResource(R.string.secret_code_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = codeText,
                        onValueChange = { codeText = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        cursorBrush = SolidColor(Color(0xFF4C8DFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.weight(1f).height(42.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.cancel))
                        }
                    }

                    Surface(
                        onClick = { onSave(codeText.trim()) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFF4C8DFF),
                            focusedContainerColor = Color(0xFF3373E6),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.weight(1f).height(42.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

enum class SecurityTarget {
    DEVICE_LOCK,
    APP_LOCK,
    HIDE_APPS,
}
