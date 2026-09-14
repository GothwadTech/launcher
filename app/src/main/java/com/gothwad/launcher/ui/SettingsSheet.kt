package com.gothwad.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.gothwad.launcher.Actions
import com.gothwad.launcher.R
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.AppRepository
import com.gothwad.launcher.data.CategoryCfg
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.DATE_FORMATS
import com.gothwad.launcher.data.LAYOUT_DOCK
import com.gothwad.launcher.data.LAYOUT_GRID
import com.gothwad.launcher.data.LauncherConfig
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.serialization.json.Json

import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.core.content.ContextCompat
import com.gothwad.launcher.data.UsageTracker

private val format = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

private enum class SettingsScreen {
    Main, Wallpaper, Display, StatusBar, Apps, Categories, Launcher, Permissions, Security, LockedApps, HiddenApps, About
}

/**
 * Settings panel built entirely from focusable rows and buttons — every
 * element is reachable with the D-pad alone. Text entry only ever happens
 * inside a dedicated dialog (opened with the pencil button).
 */
@Composable
fun SettingsSheet(
    config: LauncherConfig,
    apps: List<AppEntry>,
    store: ConfigStore,
    onDismiss: () -> Unit,
    onWallpaperChanged: () -> Unit,
    onRerunWizard: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(SettingsScreen.Main) }

    var pinSetupTarget by remember { mutableStateOf<SecurityTarget?>(null) }
    var pinSetupInitialLength by remember { mutableIntStateOf(4) }

    var pinVerifyTarget by remember { mutableStateOf<SecurityTarget?>(null) }
    var pinVerifyCorrectPin by remember { mutableStateOf("") }
    var pinVerifyLength by remember { mutableIntStateOf(4) }
    var pinVerifySuccessAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showModeDialog by remember { mutableStateOf(false) }

    // Same document-picker flow as videos: opens the system file manager,
    // works with USB drives and network storage providers.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        File(context.filesDir, "wallpaper.jpg").outputStream().use { out ->
                            input.copyTo(out)
                        }
                    }
                }
                store.update {
                    it.copy(
                        useCustomWallpaper = true,
                    )
                }
                onWallpaperChanged()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (screen != SettingsScreen.Main) screen = SettingsScreen.Main else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Force the dialog window to fill the screen — Compose dialogs otherwise
        // keep a small platform margin, so the panel wouldn't sit flush against
        // the right edge.
        val dialogView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            ((dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window)
                ?.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                )
        }
        // A Dialog spawns its own window that re-provides the platform density,
        // so the launcher's ScaledUi doesn't reach here — re-apply it. The
        // dialog's LocalConfiguration is the real (unscaled) device width.
        ScaledUi(uiScaleFactor(config.uiScale, androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                colors = SurfaceDefaults.colors(containerColor = Color(0xFF141720)),
                modifier = Modifier
                    .width(460.dp)
                    .fillMaxHeight()
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    ),
            ) {
                // Slide left when entering a sub-screen, right when going back
                // to Main (the root); cross-fade so it never looks abrupt.
                androidx.compose.animation.AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val forward = targetState != SettingsScreen.Main
                        val dir = if (forward) 1 else -1
                        (androidx.compose.animation.slideInHorizontally(tween(260)) { w -> dir * w } +
                            androidx.compose.animation.fadeIn(tween(260))) togetherWith
                            (androidx.compose.animation.slideOutHorizontally(tween(260)) { w -> -dir * w } +
                                androidx.compose.animation.fadeOut(tween(260)))
                    },
                    label = "settingsScreen",
                ) { target ->
                when (target) {
                    SettingsScreen.Main -> MainScreen(
                        config = config,
                        onModeSelection = { showModeDialog = true },
                        onWallpaper = { screen = SettingsScreen.Wallpaper },
                        onDisplay = { screen = SettingsScreen.Display },
                        onStatusBar = { screen = SettingsScreen.StatusBar },
                        onApps = { screen = SettingsScreen.Apps },
                        onCategories = { screen = SettingsScreen.Categories },
                        onLauncher = { screen = SettingsScreen.Launcher },
                        onPermissions = { screen = SettingsScreen.Permissions },
                        onSecurity = { screen = SettingsScreen.Security },
                        onAndroidSettings = { Actions.openSystemSettings(context) },
                        onAbout = { screen = SettingsScreen.About },
                    )
                    SettingsScreen.Wallpaper -> WallpaperScreen(
                        config = config,
                        onSetScrim = { v ->
                            scope.launch { store.update { it.copy(scrimMode = v) } }
                        },
                        onPreset = { i ->
                            scope.launch {
                                store.update {
                                    it.copy(
                                        wallpaper = i,
                                        useCustomWallpaper = false,
                                    )
                                }
                            }
                        },
                        onPickPhoto = {
                            runCatching { photoPicker.launch(arrayOf("image/*")) }
                                .onFailure { Actions.toast(context, context.getString(R.string.toast_no_picker)) }
                        },
                    )
                    SettingsScreen.Display -> DisplayScreen(
                        config = config,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.StatusBar -> StatusBarScreen(
                        config = config,
                        apps = apps,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.Apps -> AppsScreen(
                        apps = apps,
                        config = config,
                        onBack = { screen = SettingsScreen.Main },
                        onLaunch = { pkg -> Actions.launchApp(context, pkg) },
                        onAppInfo = { pkg -> Actions.openAppInfo(context, pkg) },
                        onUninstall = { pkg -> Actions.uninstall(context, pkg) },
                        onToggleHide = { pkg ->
                            scope.launch {
                                store.update { c ->
                                    c.copy(hidden = if (pkg in c.hidden) c.hidden - pkg else c.hidden + pkg)
                                }
                            }
                        },
                    )
                    SettingsScreen.Categories -> CategoriesScreen(
                        config = config,
                        apps = apps,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.About -> AboutScreen(
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.Permissions -> PermissionsScreen(
                        onBack = { screen = SettingsScreen.Main },
                    )
                    SettingsScreen.Launcher -> LauncherSettingsSubscreen(
                        config = config,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                        onRerunWizard = onRerunWizard,
                    )
                    SettingsScreen.Security -> SecurityScreen(
                        config = config,
                        apps = apps,
                        store = store,
                        onBack = { screen = SettingsScreen.Main },
                        onOpenLockedApps = { screen = SettingsScreen.LockedApps },
                        onOpenHiddenApps = { screen = SettingsScreen.HiddenApps },
                        onSetupPin = { targetType, initialLen ->
                            pinSetupTarget = targetType
                            pinSetupInitialLength = initialLen
                        },
                        onVerifyPin = { targetType, pin, len, onSuccess ->
                            pinVerifyTarget = targetType
                            pinVerifyCorrectPin = pin
                            pinVerifyLength = len
                            pinVerifySuccessAction = onSuccess
                        },
                    )
                    SettingsScreen.LockedApps -> LockedAppsScreen(
                        apps = apps,
                        config = config,
                        store = store,
                        onBack = { screen = SettingsScreen.Security },
                    )
                    SettingsScreen.HiddenApps -> HiddenAppsScreen(
                        apps = apps,
                        config = config,
                        store = store,
                        onBack = { screen = SettingsScreen.Security },
                    )
                }
                }
            }
        }

        if (pinSetupTarget != null) {
            val target = pinSetupTarget!!
            val title = when (target) {
                SecurityTarget.DEVICE_LOCK -> stringResource(R.string.device_lock_title)
                SecurityTarget.APP_LOCK -> stringResource(R.string.app_lock_title)
                SecurityTarget.HIDE_APPS -> stringResource(R.string.hide_apps_title)
            }
            PinSetupDialog(
                title = title,
                initialLength = pinSetupInitialLength,
                onPinSaved = { pin, length ->
                    scope.launch {
                        store.update { cfg ->
                            when (target) {
                                SecurityTarget.DEVICE_LOCK -> cfg.copy(
                                    deviceLockPin = pin,
                                    deviceLockPinLength = length,
                                    deviceLockEnabled = true
                                )
                                SecurityTarget.APP_LOCK -> cfg.copy(
                                    appLockPin = pin,
                                    appLockPinLength = length,
                                    appLockEnabled = true
                                )
                                SecurityTarget.HIDE_APPS -> cfg.copy(
                                    hideAppsPin = pin,
                                    hideAppsPinLength = length,
                                    hideAppsEnabled = true
                                )
                            }
                        }
                    }
                    pinSetupTarget = null
                },
                onDismiss = { pinSetupTarget = null },
            )
        }

        if (pinVerifyTarget != null) {
            val target = pinVerifyTarget!!
            val title = when (target) {
                SecurityTarget.DEVICE_LOCK -> stringResource(R.string.device_lock_title)
                SecurityTarget.APP_LOCK -> stringResource(R.string.app_lock_title)
                SecurityTarget.HIDE_APPS -> stringResource(R.string.hide_apps_title)
            }
            VerifyPinDialog(
                title = title,
                subtitle = stringResource(R.string.unlock_to_continue),
                correctPin = pinVerifyCorrectPin,
                pinLength = pinVerifyLength,
                onSuccess = {
                    val action = pinVerifySuccessAction
                    pinVerifyTarget = null
                    pinVerifySuccessAction = null
                    action?.invoke()
                },
                onDismiss = {
                    pinVerifyTarget = null
                    pinVerifySuccessAction = null
                },
            )
        }
        if (showModeDialog) {
            ModeSelectionDialog(
                currentMode = config.launcherMode,
                onSelectMode = { newMode ->
                    scope.launch {
                        store.update { it.copy(launcherMode = newMode) }
                    }
                    showModeDialog = false
                },
                onDismiss = { showModeDialog = false },
            )
        }
        }
    }
}

/* ------------------------------ main menu ------------------------------ */

@Composable
private fun MainScreen(
    config: LauncherConfig,
    onModeSelection: () -> Unit,
    onWallpaper: () -> Unit,
    onDisplay: () -> Unit,
    onStatusBar: () -> Unit,
    onApps: () -> Unit,
    onCategories: () -> Unit,
    onLauncher: () -> Unit,
    onPermissions: () -> Unit,
    onSecurity: () -> Unit,
    onAndroidSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        val f = initialFocus()

        // ---- Experience Mode ----
        SectionLabel("Experience Mode")
        SettingsItem(
            selected = false,
            onClick = onModeSelection,
            headlineContent = {
                Text(
                    if (config.launcherMode == com.gothwad.launcher.data.MODE_PC) "Desktop PC Mode" else "Android TV Mode"
                )
            },
            supportingContent = {
                Text("Switch between Android TV and Desktop PC interface")
            },
            leadingContent = {
                Icon(
                    if (config.launcherMode == com.gothwad.launcher.data.MODE_PC) AppIcons.Desktop else AppIcons.Tv,
                    contentDescription = null,
                )
            },
            modifier = Modifier.focusRequester(f),
        )

        // ---- Content: what shows on the home screen ----
        SectionLabel(stringResource(R.string.group_content))
        SettingsItem(
            selected = false, onClick = onApps,
            headlineContent = { Text(stringResource(R.string.item_app)) },
            supportingContent = { Text(stringResource(R.string.item_app_sub)) },
            leadingContent = { Icon(AppIcons.Apps, contentDescription = null) },
        )
        SettingsItem(
            selected = false, onClick = onCategories,
            headlineContent = { Text(stringResource(R.string.item_sections)) },
            supportingContent = { Text(stringResource(R.string.item_sections_sub)) },
            leadingContent = { Icon(AppIcons.Folder, contentDescription = null) },
        )

        // ---- Appearance: how it looks ----
        SectionLabel(stringResource(R.string.group_appearance))
        SettingsItem(
            selected = false, onClick = onDisplay,
            headlineContent = { Text(stringResource(R.string.item_display)) },
            supportingContent = { Text(stringResource(R.string.item_display_sub)) },
            leadingContent = { Icon(AppIcons.Display, contentDescription = null) },
        )
        SettingsItem(
            selected = false, onClick = onWallpaper,
            headlineContent = { Text(stringResource(R.string.item_wallpaper)) },
            supportingContent = { Text(stringResource(R.string.item_wallpaper_sub)) },
            leadingContent = { Icon(AppIcons.Image, contentDescription = null) },
        )
        SettingsItem(
            selected = false, onClick = onStatusBar,
            headlineContent = { Text(stringResource(R.string.item_statusbar)) },
            supportingContent = { Text(stringResource(R.string.item_statusbar_sub)) },
            leadingContent = { Icon(AppIcons.Wifi, contentDescription = null) },
        )

        // ---- Security & Privacy ----
        SectionLabel(stringResource(R.string.security_title))
        SettingsItem(
            selected = false, onClick = onSecurity,
            headlineContent = { Text(stringResource(R.string.item_security)) },
            supportingContent = { Text(stringResource(R.string.item_security_sub)) },
            leadingContent = { Icon(AppIcons.Lock, contentDescription = null) },
        )

        // ---- System ----
        SectionLabel(stringResource(R.string.group_system))
        SettingsItem(
            selected = false, onClick = onPermissions,
            headlineContent = { Text(stringResource(R.string.item_permissions)) },
            supportingContent = { Text(stringResource(R.string.item_permissions_sub)) },
            leadingContent = { Icon(AppIcons.Dashboard, contentDescription = null) },
        )
        SettingsItem(
            selected = false, onClick = onLauncher,
            headlineContent = { Text(stringResource(R.string.item_launcher_settings)) },
            supportingContent = { Text(stringResource(R.string.item_launcher_settings_sub)) },
            leadingContent = { Icon(painter = androidx.compose.ui.res.painterResource(R.drawable.app_icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
        )
        SettingsItem(
            selected = false, onClick = onAndroidSettings,
            headlineContent = { Text(stringResource(R.string.item_android_settings)) },
            supportingContent = { Text(stringResource(R.string.item_android_settings_sub)) },
            leadingContent = { Icon(AppIcons.Gear, contentDescription = null) },
        )
        SettingsItem(
            selected = false, onClick = onAbout,
            headlineContent = { Text(stringResource(R.string.item_about)) },
            leadingContent = { Icon(AppIcons.Info, contentDescription = null) },
        )
    }
}

/* ------------------------------ about ------------------------------ */

@Composable
private fun AboutScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.item_about),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Column {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "v" + (runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrDefault("?")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(R.string.about_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp),
        )

        SectionLabel(stringResource(R.string.about_foss))
        Text(
            stringResource(R.string.about_license),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
        // Source code
        Text(
            stringResource(R.string.about_source) + "  ·  " + stringResource(R.string.github_url),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        )

        Text(
            stringResource(R.string.about_thirdparty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 8.dp),
        )
    }
}

/* ------------------------------ display ------------------------------ */

@Composable
private fun DisplayScreen(
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun update(transform: (LauncherConfig) -> LauncherConfig) {
        scope.launch { store.update(transform) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.item_display),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        SectionLabel(stringResource(R.string.section_layout))
        val f = initialFocus()
        SelectorRow(
            modifier = Modifier.focusRequester(f),
            label = stringResource(R.string.view_mode),
            value = listOf(stringResource(R.string.mode_carousel), stringResource(R.string.mode_grid), stringResource(R.string.mode_dock))[config.layout.coerceIn(0, 2)],
            description = when (config.layout) {
                LAYOUT_GRID -> stringResource(R.string.mode_grid_desc)
                LAYOUT_DOCK -> stringResource(R.string.mode_dock_desc)
                else -> stringResource(R.string.mode_carousel_desc)
            },
            steps = 3,
            current = config.layout.coerceIn(0, 2),
            onSelect = { v -> update { it.copy(layout = v) } },
        )
        // Section names are only drawn in carousel/grid — hide the toggle in dock.
        if (config.layout != LAYOUT_DOCK) {
            SectionLabel(stringResource(R.string.section_elements))
            SettingsItem(
                selected = false,
                onClick = { update { it.copy(showCategoryNames = !it.showCategoryNames) } },
                headlineContent = { Text(stringResource(R.string.section_names_label)) },
                trailingContent = { CheckMark(checked = config.showCategoryNames) },
            )
        }
        SectionLabel(stringResource(R.string.section_size))
        run {
            val labels = listOf(stringResource(R.string.scale_auto), "75%", "90%", "100%", "115%", "130%")
            val idx = config.uiScale.coerceIn(0, labels.size - 1)
            SelectorRow(
                label = stringResource(R.string.ui_scale),
                value = labels[idx],
                description = if (idx == 0) stringResource(R.string.ui_scale_auto_sub) else null,
                steps = labels.size,
                current = idx,
                onSelect = { v -> update { it.copy(uiScale = v) } },
            )
        }
        SelectorRow(
            label = stringResource(R.string.roundness),
            value = listOf(stringResource(R.string.size_xs), stringResource(R.string.size_s), stringResource(R.string.size_n), stringResource(R.string.size_l), stringResource(R.string.size_xl))[config.cornerRadius.coerceIn(0, 4)],
            steps = 5,
            current = config.cornerRadius.coerceIn(0, 4),
            onSelect = { v -> update { it.copy(cornerRadius = v) } },
        )
        SelectorRow(
            label = stringResource(R.string.icon_size),
            value = listOf(stringResource(R.string.size_xs), stringResource(R.string.size_s), stringResource(R.string.size_n), stringResource(R.string.size_l), stringResource(R.string.size_xl))[config.iconScale.coerceIn(0, 4)],
            steps = 5,
            current = config.iconScale.coerceIn(0, 4),
            onSelect = { v -> update { it.copy(iconScale = v) } },
        )
        SelectorRow(
            label = stringResource(R.string.spacing),
            value = listOf(stringResource(R.string.size_xs), stringResource(R.string.size_s), stringResource(R.string.size_n), stringResource(R.string.size_l), stringResource(R.string.size_xl))[config.spacing.coerceIn(0, 4)],
            steps = 5,
            current = config.spacing.coerceIn(0, 4),
            onSelect = { v -> update { it.copy(spacing = v) } },
        )
    }
}

/**
 * D-pad selector row: Left/Right changes the value directly, OK cycles.
 * The dots show the position within the available steps.
 */
@Composable
private fun SelectorRow(
    label: String,
    value: String,
    steps: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    SettingsItem(
        selected = false,
        onClick = { onSelect((current + 1) % steps) },
        headlineContent = { Text(label) },
        supportingContent = { if (description != null) Text(description) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("◄", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value)
                Text("►", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft -> { onSelect((current - 1 + steps) % steps); true }
                Key.DirectionRight -> { onSelect((current + 1) % steps); true }
                else -> false
            }
        },
    )
}

/* --------------- app list: icon + name launches, ⓘ = app info --------------- */

@Composable
private fun AppsScreen(
    apps: List<AppEntry>,
    config: LauncherConfig,
    onBack: () -> Unit,
    onLaunch: (String) -> Unit,
    onAppInfo: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onToggleHide: (String) -> Unit,
) {
    val catNames = remember(config.categories) { config.categories.associate { it.id to it.name } }
    val visibleApps = remember(apps, config.hidden) {
        apps.filter { it.pkg !in config.hidden }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            stringResource(R.string.item_app),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp, start = 8.dp),
        )
        Text(
            stringResource(R.string.app_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        val f = initialFocus()
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(visibleApps.size, key = { visibleApps[it].pkg }) { i ->
                val app = visibleApps[i]
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        SettingsItem(
                            selected = false,
                            onClick = { onLaunch(app.pkg) },
                            modifier = if (i == 0) Modifier.focusRequester(f) else Modifier,
                            headlineContent = { Text(app.label) },
                            supportingContent = {
                                val names = AppRepository.sectionsOf(app, config)
                                    .mapNotNull { catNames[it] }
                                Text(names.joinToString(" · ").ifEmpty { stringResource(R.string.no_section) })
                            },
                            leadingContent = { AppThumb(app) },
                        )
                    }
                    SmallIconButton(AppIcons.Info, stringResource(R.string.menu_app_info)) { onAppInfo(app.pkg) }
                    SmallIconButton(AppIcons.Delete, stringResource(R.string.menu_uninstall)) { onUninstall(app.pkg) }
                }
            }
        }
    }
}

/** Small leading artwork for list rows: banner if the app ships one, else icon. */
@Composable
private fun AppThumb(app: AppEntry) {
    when {
        app.banner != null -> Image(
            bitmap = app.banner,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 52.dp, height = 30.dp)
                .clip(RoundedCornerShape(5.dp)),
        )
        app.icon != null -> Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
        )
        else -> Box(
            Modifier
                .size(width = 52.dp, height = 30.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(5.dp))
        )
    }
}

/* ---------- section apps picker: vertical grid, checked = included ---------- */

@Composable
private fun SectionAppsDialog(
    cat: CategoryCfg,
    apps: List<AppEntry>,
    config: LauncherConfig,
    onToggle: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.width(720.dp).height(520.dp),
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text(cat.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.section_apps_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                val f = initialFocus()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(apps.size, key = { apps[it].pkg }) { i ->
                        val app = apps[i]
                        val inSection = cat.id in AppRepository.sectionsOf(app, config)
                        Surface(
                            onClick = { onToggle(app) },
                            modifier = if (i == 0) Modifier.focusRequester(f) else Modifier,
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.06f),
                                focusedContainerColor = Color.White.copy(alpha = 0.18f),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                        ) {
                            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (app.banner != null) {
                                        Image(
                                            bitmap = app.banner,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(6.dp)),
                                        )
                                    } else {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (app.icon != null) {
                                                Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                                            }
                                        }
                                    }
                                    Text(
                                        app.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                                    CheckMark(checked = inSection)
                                }
                            }
                        }
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}


/* ------------------------------ launcher settings ------------------------------ */

@Composable
private fun LauncherSettingsSubscreen(
    config: LauncherConfig,
    store: ConfigStore,
    onBack: () -> Unit,
    onRerunWizard: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun saveConfig() {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    dir.mkdirs()
                    File(dir, "CouchyBackup.json").writeText(format.encodeToString(LauncherConfig.serializer(), config))
                }
            }
            Actions.toast(context, "Saved to Downloads/CouchyBackup.json")
        }
    }

    val loadPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            format.decodeFromString(LauncherConfig.serializer(), String(input.readBytes()))
                        }
                    }.getOrNull()
                }
                if (loaded != null) {
                    store.update { loaded.copy(knownApps = config.knownApps, setupDone = true) }
                    Actions.toast(context, context.getString(R.string.toast_config_loaded))
                } else {
                    Actions.toast(context, context.getString(R.string.toast_config_bad))
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.item_launcher_settings),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        val isA11yEnabled = com.gothwad.launcher.service.LauncherAccessibilityService.isEnabled(context)
        val isNotifEnabled = com.gothwad.launcher.service.NotificationManagerBridge.isNotificationAccessGranted(context)

        SettingsItem(
            selected = false,
            onClick = { Actions.openAccessibilitySettings(context) },
            headlineContent = { Text(stringResource(R.string.accessibility_home_title)) },
            supportingContent = {
                Text(
                    if (isA11yEnabled) stringResource(R.string.accessibility_status_enabled)
                    else stringResource(R.string.accessibility_status_disabled)
                )
            },
            leadingContent = { Icon(AppIcons.Accessibility, contentDescription = null) },
            trailingContent = { CheckMark(checked = isA11yEnabled) },
        )
        SettingsItem(
            selected = false,
            onClick = { Actions.openNotificationAccessSettings(context) },
            headlineContent = { Text(stringResource(R.string.item_notifications)) },
            supportingContent = {
                Text(
                    if (isNotifEnabled) "✓ Active: TV status bar alert listener enabled"
                    else "Inactive: Click to grant notification access"
                )
            },
            leadingContent = { Icon(AppIcons.Bell, contentDescription = null) },
            trailingContent = { CheckMark(checked = isNotifEnabled) },
        )
        SettingsItem(
            selected = false,
            onClick = { saveConfig() },
            headlineContent = { Text(stringResource(R.string.item_save_config)) },
            supportingContent = { Text(stringResource(R.string.item_save_config_sub)) },
            leadingContent = { Icon(AppIcons.Save, contentDescription = null) },
        )
        SettingsItem(
            selected = false,
            onClick = { runCatching { loadPicker.launch(arrayOf("application/json", "*/*")) }.onFailure { Actions.toast(context, context.getString(R.string.toast_no_picker)) } },
            headlineContent = { Text(stringResource(R.string.item_load_config)) },
            supportingContent = { Text(stringResource(R.string.item_load_config_sub)) },
            leadingContent = { Icon(AppIcons.Folder, contentDescription = null) },
        )
        SettingsItem(
            selected = false,
            onClick = onRerunWizard,
            headlineContent = { Text(stringResource(R.string.rerun_wizard)) },
            supportingContent = { Text(stringResource(R.string.rerun_wizard_sub)) },
            leadingContent = { Icon(AppIcons.Play, contentDescription = null) },
        )
    }
}

/* ------------------------------ status bar ------------------------------ */

@Composable
private fun StatusBarScreen(
    config: LauncherConfig,
    apps: List<AppEntry>,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pickVpn by remember { mutableStateOf(false) }
    val vpnLabel = apps.firstOrNull { it.pkg == config.vpnApp }?.label ?: stringResource(R.string.vpn_system)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.item_statusbar),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        val f = initialFocus()
        SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(showStatusBar = !it.showStatusBar) } } },
            modifier = Modifier.focusRequester(f),
            headlineContent = { Text(stringResource(R.string.show_statusbar)) },
            supportingContent = { Text(stringResource(R.string.show_statusbar_sub)) },
            trailingContent = { CheckMark(checked = config.showStatusBar) },
        )
        SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(statusBarGlass = !it.statusBarGlass) } } },
            headlineContent = { Text(stringResource(R.string.statusbar_glass)) },
            supportingContent = { Text(stringResource(R.string.statusbar_glass_sub)) },
            trailingContent = { CheckMark(checked = config.statusBarGlass) },
        )
        SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(h24 = !it.h24) } } },
            headlineContent = { Text(stringResource(R.string.clock_24h)) },
            trailingContent = { CheckMark(checked = config.h24) },
        )
        run {
            val idx = config.dateFormat.coerceIn(0, DATE_FORMATS.size - 1)
            SelectorRow(
                label = stringResource(R.string.date_label),
                // Live preview of today's date in the selected format
                value = if (idx == 0) stringResource(R.string.off)
                else java.text.SimpleDateFormat(DATE_FORMATS[idx], java.util.Locale.getDefault())
                    .format(java.util.Date()),
                steps = DATE_FORMATS.size,
                current = idx,
                onSelect = { v -> scope.launch { store.update { it.copy(dateFormat = v) } } },
            )
        }
        SettingsItem(
            selected = false,
            onClick = { scope.launch { store.update { it.copy(showVpnButton = !it.showVpnButton) } } },
            headlineContent = { Text(stringResource(R.string.show_vpn_button)) },
            leadingContent = { Icon(AppIcons.Vpn, contentDescription = null) },
            trailingContent = { CheckMark(checked = config.showVpnButton) },
        )
        // Grayed out (and not focusable) while the VPN button is hidden
        SettingsItem(
            selected = false,
            enabled = config.showVpnButton,
            onClick = { pickVpn = true },
            headlineContent = { Text(stringResource(R.string.vpn_opens)) },
            supportingContent = { Text(vpnLabel) },
            leadingContent = { Icon(AppIcons.Vpn, contentDescription = null) },
        )
    }

    if (pickVpn) {
        Dialog(onDismissRequest = { pickVpn = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.width(380.dp).height(480.dp),
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        stringResource(R.string.vpn_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
                    )
                    val fv = initialFocus()
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item {
                            SettingsItem(
                                selected = false,
                                onClick = {
                                    scope.launch { store.update { it.copy(vpnApp = "") } }
                                    pickVpn = false
                                },
                                headlineContent = { Text(stringResource(R.string.vpn_system)) },
                                trailingContent = { CheckMark(checked = config.vpnApp.isEmpty()) },
                                modifier = Modifier.focusRequester(fv),
                            )
                        }
                        items(apps.size, key = { apps[it].pkg }) { i ->
                            val app = apps[i]
                            SettingsItem(
                                selected = false,
                                onClick = {
                                    scope.launch { store.update { it.copy(vpnApp = app.pkg) } }
                                    pickVpn = false
                                },
                                headlineContent = { Text(app.label) },
                                leadingContent = { AppThumb(app) },
                                trailingContent = { CheckMark(checked = config.vpnApp == app.pkg) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------ sections ------------------------------ */

@Composable
private fun CategoriesScreen(
    config: LauncherConfig,
    apps: List<AppEntry>,
    store: ConfigStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var renameFor by remember { mutableStateOf<CategoryCfg?>(null) }
    var appsFor by remember { mutableStateOf<CategoryCfg?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.item_sections),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        val f = initialFocus()
        // Auto-filled "All apps" section: a single toggle, no per-app assignment.
        run {
            val hasAll = config.categories.any { it.id == AppRepository.ALL_APPS_ID }
            val allName = stringResource(R.string.cat_all_apps)
            SettingsItem(
                selected = false,
                onClick = {
                    scope.launch {
                        store.update { cfg ->
                            val rest = cfg.categories.filter { it.id != AppRepository.ALL_APPS_ID }
                            cfg.copy(
                                categories = if (hasAll) rest
                                else rest + CategoryCfg(AppRepository.ALL_APPS_ID, allName),
                            )
                        }
                    }
                },
                modifier = Modifier.focusRequester(f),
                headlineContent = { Text(allName) },
                supportingContent = { Text(stringResource(R.string.all_apps_sub)) },
                leadingContent = { Icon(AppIcons.Apps, contentDescription = null) },
                trailingContent = { CheckMark(checked = hasAll) },
            )
        }
        config.categories.forEachIndexed { i, cat ->
            if (cat.id == AppRepository.ALL_APPS_ID) return@forEachIndexed
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    SettingsItem(
                        selected = false,
                        onClick = { appsFor = cat },
                        headlineContent = { Text(cat.name) },
                        supportingContent = { Text(stringResource(R.string.section_row_sub)) },
                        leadingContent = { Icon(AppIcons.Folder, contentDescription = null) },
                    )
                }
                SmallIconButton(AppIcons.Pencil, stringResource(R.string.rename_section)) { renameFor = cat }
                SmallIconButton(AppIcons.Up, stringResource(R.string.cd_move_up)) {
                    if (i > 0) scope.launch {
                        store.update { cfg ->
                            val l = cfg.categories.toMutableList()
                            val tmp = l[i - 1]; l[i - 1] = l[i]; l[i] = tmp
                            cfg.copy(categories = l)
                        }
                    }
                }
                SmallIconButton(AppIcons.Down, stringResource(R.string.cd_move_down)) {
                    if (i < config.categories.size - 1) scope.launch {
                        store.update { cfg ->
                            val l = cfg.categories.toMutableList()
                            val tmp = l[i + 1]; l[i + 1] = l[i]; l[i] = tmp
                            cfg.copy(categories = l)
                        }
                    }
                }
                if (config.categories.size > 1) {
                    SmallIconButton(AppIcons.Delete, stringResource(R.string.cd_delete_section)) {
                        scope.launch {
                            store.update { cfg ->
                                cfg.copy(categories = cfg.categories.filter { it.id != cat.id })
                            }
                        }
                    }
                }
            }
        }
        val newSectionName = stringResource(R.string.new_section)
        SettingsItem(
            selected = false,
            onClick = {
                scope.launch {
                    store.update {
                        it.copy(
                            categories = it.categories +
                                CategoryCfg("c${System.currentTimeMillis()}", newSectionName)
                        )
                    }
                }
            },
            headlineContent = { Text(stringResource(R.string.add_section)) },
            leadingContent = { Icon(AppIcons.Add, contentDescription = null) },
        )
    }

    appsFor?.let { cat ->
        // Keep the dialog's category object in sync with the live config
        val liveCat = config.categories.firstOrNull { it.id == cat.id } ?: cat
        SectionAppsDialog(
            cat = liveCat,
            apps = apps,
            config = config,
            onToggle = { app ->
                scope.launch {
                    store.update { cfg ->
                        val current = AppRepository.sectionsOf(app, cfg)
                        val next = if (liveCat.id in current) current - liveCat.id
                        else current + liveCat.id
                        cfg.copy(sections = cfg.sections + (app.pkg to next))
                    }
                }
            },
            onDismiss = { appsFor = null },
        )
    }

    renameFor?.let { cat ->
        RenameDialog(
            initial = cat.name,
            onSave = { newName ->
                scope.launch {
                    store.update { cfg ->
                        cfg.copy(categories = cfg.categories.map {
                            if (it.id == cat.id) it.copy(name = newName) else it
                        })
                    }
                }
                renameFor = null
            },
            onDismiss = { renameFor = null },
        )
    }
}

/** Pencil dialog: the ONLY place with a text field, so D-pad browsing never lands in an editor. */
@Composable
private fun RenameDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.width(340.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.rename_section), style = MaterialTheme.typography.titleMedium)
                val f = initialFocus()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { if (name.isNotBlank()) onSave(name.trim()) }
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text(stringResource(R.string.save)) }
                    Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

/* ------------------------------ wallpaper ------------------------------ */

/**
 * ONE row per background mode, checkmark = active. Selecting any mode turns
 * the others off. Options: Static (color gradient, photo).
 */
@Composable
private fun WallpaperScreen(
    config: LauncherConfig,
    onSetScrim: (Int) -> Unit,
    onPreset: (Int) -> Unit,
    onPickPhoto: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.item_wallpaper),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp, start = 8.dp),
        )
        val f = initialFocus()
        val staticActive = !config.useCustomWallpaper

        // Dimming — how much the wallpaper is darkened for icon readability.
        // Applies to any background; "Top & bottom" keeps the centre clear.
        run {
            val names = listOf(
                stringResource(R.string.dim_top_bottom),
                stringResource(R.string.dim_top),
                stringResource(R.string.dim_bottom),
                stringResource(R.string.dim_full),
                stringResource(R.string.dim_off),
            )
            val idx = config.scrimMode.coerceIn(0, names.size - 1)
            SelectorRow(
                modifier = Modifier.focusRequester(f),
                label = stringResource(R.string.dim_label),
                value = names[idx],
                steps = names.size,
                current = idx,
                onSelect = { v -> onSetScrim(v) },
            )
        }

        /* ---------------- STATIC: color gradient + your own photo ---------------- */
        SectionLabel(stringResource(R.string.wp_section_static))

        // Color gradient mode — checkmark like every other mode; the picker
        // below appears once it's active (mirrors the aerial collection row).
        val presetIdx = config.wallpaper.coerceIn(0, WALLPAPERS.size - 1)
        SettingsItem(
            selected = false, onClick = { onPreset(presetIdx) },
            headlineContent = { Text(stringResource(R.string.color_gradient)) },
            leadingContent = { Icon(AppIcons.Palette, contentDescription = null) },
            trailingContent = { CheckMark(checked = staticActive) },
        )
        if (staticActive) {
            val presetNames = listOf(
                stringResource(R.string.wp_midnight),
                stringResource(R.string.wp_aurora),
                stringResource(R.string.wp_sunset),
                stringResource(R.string.wp_deep),
                stringResource(R.string.wp_charcoal),
            )
            SelectorRow(
                label = stringResource(R.string.preset_label),
                value = presetNames[presetIdx],
                steps = WALLPAPERS.size,
                current = presetIdx,
                onSelect = { v -> onPreset(v) },
            )
        }

        // A photo of your own
        SettingsItem(
            selected = false, onClick = onPickPhoto,
            headlineContent = { Text(stringResource(R.string.pick_photo)) },
            supportingContent = { Text(stringResource(R.string.pick_photo_sub)) },
            leadingContent = { Icon(AppIcons.Image, contentDescription = null) },
            trailingContent = { CheckMark(checked = config.useCustomWallpaper) },
        )
    }
}

/* ------------------------------ widgets ------------------------------ */

/**
 * Every menu focuses its first item on entry (Back on the remote steps out,
 * so there are no on-screen Back buttons).
 */
@Composable
private fun initialFocus(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Claim focus the moment the first item's node is attached (retry per
        // frame, stop on the first success) instead of after a fixed 300ms delay.
        // A late request would yank focus back to item 1 if the user had already
        // navigated down — the "quick navigation resets to the top" bug.
        repeat(30) {
            if (runCatching { fr.requestFocus() }.isSuccess) return@LaunchedEffect
            withFrameNanos {}
        }
    }
    return fr
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, start = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun CheckMark(checked: Boolean) {
    Box(
        Modifier
            .size(22.dp)
            .background(
                if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(5.dp),
            )
            .border(
                width = 2.dp,
                color = if (checked) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.4f),
                shape = RoundedCornerShape(5.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                AppIcons.Check,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * A settings row with NO focus scale. tv-material's default focusedScale
 * scales the whole row — including its already-rasterized text — which looks
 * blurry on TV; the focused container colour alone signals selection.
 */
@Composable
private fun SettingsItem(
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
                if (focused) Color(0xFFF1F2F4) else Color.Transparent,
                RoundedCornerShape(12.dp)
            ),
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = androidx.tv.material3.ListItemDefaults.colors(
            focusedContainerColor = Color.Transparent,
            focusedContentColor = Color(0xFF14151A),
        ),
    )
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.25f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            // label is the accessibility name announced by TalkBack.
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        }
    }
}

/* ------------------------------ permissions screen ------------------------------ */

@Composable
private fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }

    val hasUsage = remember(refreshKey) { UsageTracker.hasPermission(context) }
    val hasLocation = remember(refreshKey) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val hasMic = remember(refreshKey) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val hasOverlay = remember(refreshKey) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey = refreshKey + 1 }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey = refreshKey + 1 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
            SmallIconButton(icon = AppIcons.Close, label = "Back", onClick = onBack)
        }

        Text(
            text = "Grant permissions to unlock all smart TV features. You can toggle them anytime.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        // 1. Most Used Apps (Usage Access)
        PermissionCard(
            title = stringResource(R.string.permission_usage_title),
            description = stringResource(R.string.permission_usage_desc),
            icon = AppIcons.Star,
            granted = hasUsage,
            buttonText = if (hasUsage) "Granted" else "Grant in Settings",
            onClick = {
                runCatching {
                    context.startActivity(android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
        )

        // 2. Weather & Day/Night (Location)
        PermissionCard(
            title = stringResource(R.string.permission_location_title),
            description = stringResource(R.string.permission_location_desc),
            icon = AppIcons.Sun,
            granted = hasLocation,
            buttonText = if (hasLocation) "Granted" else "Enable Location",
            onClick = {
                locationLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        )

        // 3. Voice Search (Microphone)
        PermissionCard(
            title = stringResource(R.string.permission_mic_title),
            description = stringResource(R.string.permission_mic_desc),
            icon = AppIcons.Mic,
            granted = hasMic,
            buttonText = if (hasMic) "Granted" else "Allow Microphone",
            onClick = {
                micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        )

        // 4. Quick TV Dashboard / Side Panel (Draw Over Other Apps)
        PermissionCard(
            title = stringResource(R.string.permission_overlay_title),
            description = stringResource(R.string.permission_overlay_desc),
            icon = AppIcons.Dashboard,
            granted = hasOverlay,
            buttonText = if (hasOverlay) "Granted" else "Enable Overlay",
            onClick = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                        )
                    }
                }
            }
        )

        // 5. Bluetooth Remote Battery Status
        PermissionCard(
            title = stringResource(R.string.permission_bt_title),
            description = stringResource(R.string.permission_bt_desc),
            icon = AppIcons.Bluetooth,
            granted = true,
            buttonText = "Bluetooth Settings",
            onClick = {
                runCatching {
                    context.startActivity(android.content.Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
        )

        // 6. Remote Button Remapping (Accessibility Service)
        PermissionCard(
            title = stringResource(R.string.permission_accessibility_title),
            description = stringResource(R.string.permission_accessibility_desc),
            icon = AppIcons.Gear,
            granted = true,
            buttonText = "Accessibility Settings",
            onClick = {
                runCatching {
                    context.startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    granted: Boolean,
    buttonText: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1E2129),
            focusedContainerColor = Color(0xFF2C3240),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (granted) Color(0xFF2E7D32).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) Color(0xFF81C784) else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (granted) Color(0xFF2E7D32) else Color(0xFFE65100))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (granted) "Active" else "Action needed",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (granted) Color.White.copy(alpha = 0.1f) else Color(0xFF4C8DF6))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}
