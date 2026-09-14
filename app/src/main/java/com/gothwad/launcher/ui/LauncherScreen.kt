package com.gothwad.launcher.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.gothwad.launcher.Actions
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.AppRepository
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.CORNER_RADII
import com.gothwad.launcher.data.GAP_SIZES
import com.gothwad.launcher.data.ICON_SIZES
import com.gothwad.launcher.data.LAYOUT_DOCK
import com.gothwad.launcher.data.LAYOUT_GRID
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.MODE_PC
import com.gothwad.launcher.data.MODE_TV
import com.gothwad.launcher.data.UI_SCALES
import com.gothwad.launcher.ui.pc.PcLauncherScreen
import com.gothwad.launcher.ui.tv.TvLauncherScreen
import com.gothwad.launcher.data.NetStatus
import com.gothwad.launcher.data.networkStatusFlow
import com.gothwad.launcher.data.BluetoothDeviceStatus
import com.gothwad.launcher.data.WeatherData
import com.gothwad.launcher.data.WeatherRepository
import com.gothwad.launcher.data.UsageTracker
import com.gothwad.launcher.data.bluetoothStatusFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Allocated ONCE — recomposition never rebuilds the scrim brushes. */
// Full: darkens the whole wallpaper evenly.
private val ScrimFull = Brush.verticalGradient(
    listOf(
        Color.Black.copy(alpha = 0.55f),
        Color.Black.copy(alpha = 0.25f),
        Color.Black.copy(alpha = 0.45f),
    )
)
// Top & bottom: dark bands only where the status bar and dock/labels sit; the
// middle of the wallpaper stays fully clear (video/aerials keep their punch).
private val ScrimTopBottom = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.6f),
    0.16f to Color.Black.copy(alpha = 0f),
    0.82f to Color.Black.copy(alpha = 0f),
    1.0f to Color.Black.copy(alpha = 0.5f),
)
// Top only — dark band under the status bar, rest clear.
private val ScrimTop = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.6f),
    0.20f to Color.Black.copy(alpha = 0f),
    1.0f to Color.Black.copy(alpha = 0f),
)
// Bottom only — dark band under the dock/labels, rest clear.
private val ScrimBottom = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0f),
    0.80f to Color.Black.copy(alpha = 0f),
    1.0f to Color.Black.copy(alpha = 0.5f),
)

/** The effective UI scale: 0 = Auto (compact high-DPI TVs down to ~1200dp of
 *  width so every device reads the same, never enlarging roomy ones); 1..5 pick
 *  a fixed value from [UI_SCALES]. */
fun uiScaleFactor(index: Int, screenWidthDp: Int): Float =
    if (index <= 0) (screenWidthDp / 1200f).coerceIn(0.6f, 1.0f)
    else UI_SCALES[(index - 1).coerceIn(0, UI_SCALES.size - 1)]

/** Global text boost (sp only, not dp): 1 = off. Raise to enlarge every label
 *  without touching icons or layout. */
const val FONT_BOOST = 1f

/** Renders [content] at [scale]× the device density — one knob shrinks/grows
 *  every dp and sp uniformly (icons, text, settings, wizard). Also rescales the
 *  Configuration so width-derived layout (dock/grid columns) stays correct. */
@Composable
fun ScaledUi(scale: Float, content: @Composable () -> Unit) {
    val base = androidx.compose.ui.platform.LocalDensity.current
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val scaledCfg = remember(cfg, scale) {
        android.content.res.Configuration(cfg).apply {
            screenWidthDp = (cfg.screenWidthDp / scale).toInt()
            screenHeightDp = (cfg.screenHeightDp / scale).toInt()
            smallestScreenWidthDp = (cfg.smallestScreenWidthDp / scale).toInt()
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides
            androidx.compose.ui.unit.Density(base.density * scale, base.fontScale * FONT_BOOST),
        androidx.compose.ui.platform.LocalConfiguration provides scaledCfg,
    ) { content() }
}
// compositionLocalOf (not static): roundness changes at runtime and must
// recompose every reader, including those inside subcompositions like the
// dock's BoxWithConstraints (its 2nd-row peek) — static wouldn't reach them.
val LocalCornerRadius = androidx.compose.runtime.compositionLocalOf { 10.dp }

/**
 * How many full-size cards fit in [available] width, and the spacing to use.
 * Icons NEVER shrink: if the requested gap doesn't fit, the gap is squeezed
 * instead so every icon keeps its exact size.
 */
fun fitRow(available: Dp, cardWidth: Dp, gap: Dp): Pair<Int, Dp> {
    val cols = (((available + gap) / (cardWidth + gap)).toInt()).coerceAtLeast(1)
    val leftover = available - cardWidth * cols
    val used = if (cols > 1 && leftover > 0.dp) {
        val spread = leftover / (cols - 1)
        if (spread < gap) spread else gap
    } else 0.dp
    return cols to used
}

/** Unwrap a (possibly wrapped) Context to its host Activity, or null. */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
fun LauncherApp(rescanTick: Int) {
    val context = LocalContext.current
    val store = remember { ConfigStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    // Instant startup: default config immediately on frame 0, live update from DataStore flow
    val config by produceState(initialValue = LauncherConfig()) {
        store.flow.collect { value = it }
    }
    // Instant startup: start with in-memory cache immediately if available, then update with scan
    val apps by produceState<List<AppEntry>>(initialValue = AppRepository.memoryCache ?: emptyList(), rescanTick) {
        value = withContext(Dispatchers.Default) { AppRepository.scan(context.applicationContext) }
    }

    val uiScale = uiScaleFactor(
        config.uiScale,
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp,
    )

    // Mirror the chosen language to SharedPreferences (which attachBaseContext
    // reads synchronously) and recreate the activity when it changes, so the new
    // locale takes effect without a restart.
    val activity = remember(context) { context.findActivity() }
    LaunchedEffect(config.language) {
        if (com.gothwad.launcher.MainActivity.currentLocalePref(context) != config.language) {
            com.gothwad.launcher.MainActivity.persistLocale(context, config.language)
            activity?.recreate()
        }
    }
    LaunchedEffect(activity) {
        activity?.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK)
        )
    }

    if (config.launcherMode.isEmpty()) {
        ScaledUi(uiScale) {
            LiteTvTheme(accent = ACCENTS[0]) {
                ModeSelectionDialog(
                    currentMode = config.launcherMode,
                    onSelectMode = { mode ->
                        scope.launch {
                            store.update { it.copy(launcherMode = mode) }
                        }
                    },
                    onDismiss = null,
                )
            }
        }
        return
    }

    if (!config.setupDone) {
        ScaledUi(uiScale) {
            LiteTvTheme(accent = ACCENTS[0]) {
                SetupWizard(
                    onDone = {
                        scope.launch { store.update { it.copy(setupDone = true) } }
                    },
                    onVpnChosen = { pkg ->
                        scope.launch {
                            store.update {
                                // Picked an app → VPN button opens it; skipped → hide it.
                                if (pkg == null) it.copy(showVpnButton = false, vpnApp = "")
                                else it.copy(showVpnButton = true, vpnApp = pkg)
                            }
                        }
                    },
                )
            }
        }
        return
    }
    // flowOn(IO): the callbackFlow's initial compute() does ConnectivityManager
    // binder calls — keep them off the main thread on the first real frame.
    val net by remember { networkStatusFlow(context).flowOn(Dispatchers.IO) }
        .collectAsStateWithLifecycle(initialValue = NetStatus())

    val bt by remember { bluetoothStatusFlow(context).flowOn(Dispatchers.IO) }
        .collectAsStateWithLifecycle(initialValue = BluetoothDeviceStatus())

    var weather by remember { mutableStateOf(WeatherData()) }
    var weatherRefreshTick by remember { mutableIntStateOf(0) }

    var showVoiceSearch by remember { mutableStateOf(false) }
    var showDashboard by remember { mutableStateOf(false) }
    var showWeatherDetails by remember { mutableStateOf(false) }
    var showBackgroundMediaDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var isDeviceUnlocked by remember { mutableStateOf(false) }
    var appToUnlock by remember { mutableStateOf<AppEntry?>(null) }

    val backgroundMedia by remember(context) {
        com.gothwad.launcher.data.BackgroundMediaTracker.backgroundMediaFlow(context)
    }.collectAsStateWithLifecycle(initialValue = com.gothwad.launcher.data.BackgroundMediaState())

    // Bump on every ON_RESUME so the clock/date refresh immediately after
    // sleep — they normally only tick on minute boundaries via produceState.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Format with the app's current locale (reflects the language override, and
    // recomposes on change) — not Locale.getDefault(), which the framework can
    // reset, leaving the date stuck in the previous language.
    val locale = androidx.core.os.ConfigurationCompat
        .getLocales(androidx.compose.ui.platform.LocalConfiguration.current)
        .get(0) ?: Locale.getDefault()
    val time by produceState(initialValue = "", config.h24, locale, resumeTick) {
        val fmt = SimpleDateFormat(if (config.h24) "HH:mm" else "h:mm a", locale)
        while (true) {
            value = fmt.format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    val date by produceState(initialValue = "", config.dateFormat, locale, resumeTick) {
        val pattern = com.gothwad.launcher.data.DATE_FORMATS
            .getOrElse(config.dateFormat) { "" }
        if (pattern.isEmpty()) { value = ""; return@produceState }
        val fmt = SimpleDateFormat(pattern, locale)
        while (true) {
            value = fmt.format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }

    // New installs are auto-added to the FIRST section.
    LaunchedEffect(apps) {
        if (apps.isEmpty()) return@LaunchedEffect
        val current = apps.map { it.pkg }.toSet()
        store.update { cfg ->
            if (cfg.knownApps.isEmpty()) cfg.copy(knownApps = current)
            else {
                val added = current - cfg.knownApps
                if (added.isEmpty() && current == cfg.knownApps) cfg
                else {
                    val first = cfg.categories.first().id
                    cfg.copy(
                        knownApps = current,
                        sections = cfg.sections + added.associateWith { setOf(first) },
                    )
                }
            }
        }
    }

    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var movePkg by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var wallpaperVersion by remember { mutableIntStateOf(0) }

    val launchWithLockCheck: (AppEntry) -> Unit = { app ->
        if (movePkg == null) {
            if (config.appLockEnabled && config.appLockPin.isNotEmpty() && app.pkg in config.lockedApps) {
                appToUnlock = app
            } else {
                Actions.launchApp(context, app.pkg)
            }
        }
    }

    val notifications by com.gothwad.launcher.service.NotificationManagerBridge.notifications.collectAsStateWithLifecycle()
    var hasNotificationPermission by remember {
        mutableStateOf(com.gothwad.launcher.service.NotificationManagerBridge.isNotificationAccessGranted(context))
    }

    LaunchedEffect(resumeTick, weatherRefreshTick) {
        hasNotificationPermission = com.gothwad.launcher.service.NotificationManagerBridge.isNotificationAccessGranted(context)
        weather = WeatherRepository.getWeather(context)
    }

    // Drop empty sections: an empty row is dead space AND a focus trap that
    // stops D-pad navigation from reaching the section below it.
    val categorized = remember(apps, config, resumeTick) {
        val base = AppRepository.categorize(apps, config).filter { it.second.isNotEmpty() }
        if (UsageTracker.hasPermission(context)) {
            val mostUsedPkgs = UsageTracker.getMostUsedPackageNames(context, limit = 8)
                .filter { it !in config.hidden }
            val appsByPkg = apps.associateBy { it.pkg }
            val mostUsedList = mostUsedPkgs.mapNotNull { appsByPkg[it] }
            if (mostUsedList.isNotEmpty()) {
                val freqCat = com.gothwad.launcher.data.CategoryCfg("__frequent__", "Frequently Used")
                listOf(freqCat to mostUsedList) + base
            } else base
        } else base
    }
    // The dock's flat app list (all sections concatenated, de-duped). Hoisted &
    // remembered so its identity stays stable across recompositions — otherwise a
    // fresh list on every clock tick blocks DockArea from strong-skipping.
    val dockApps = remember(categorized) {
        categorized.flatMap { it.second }.distinctBy { it.pkg }
    }
    val accent = ACCENTS[config.accent.coerceIn(0, ACCENTS.size - 1)]

    // ----- display dimensions: size + spacing drive everything, columns
    // are always derived from available width (never a fixed count) -----
    val cardWidth: Dp = ICON_SIZES[config.iconScale.coerceIn(0, ICON_SIZES.size - 1)]
    val gap: Dp = GAP_SIZES[config.spacing.coerceIn(0, GAP_SIZES.size - 1)]

    // Columns as laid out on screen (mirrors the padding used by GridSection /
    // DockArea) — move mode needs them to shift an icon up/down by a full row.
    val screenW: Dp = LocalConfiguration.current.screenWidthDp.dp
    val gridCols = fitRow(screenW - 88.dp, cardWidth, gap).first
    val dockCols = fitRow(screenW - 116.dp, cardWidth, gap).first

    var dockExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(config.layout) { if (config.layout != LAYOUT_DOCK) dockExpanded = false }

    // ----- move-mode helpers -----
    fun findMoving(): Triple<Int, List<AppEntry>, Int>? {
        val pkg = movePkg ?: return null
        categorized.forEachIndexed { catIndex, (_, list) ->
            val i = list.indexOfFirst { it.pkg == pkg }
            if (i >= 0) return Triple(catIndex, list, i)
        }
        return null
    }

    fun moveWithinRow(delta: Int) {
        val (catIndex, list, i) = findMoving() ?: return
        val j = i + delta
        if (j < 0 || j >= list.size) return
        val catId = categorized[catIndex].first.id
        val newOrder = list.map { it.pkg }.toMutableList().also { l ->
            val tmp = l[i]; l[i] = l[j]; l[j] = tmp
        }
        scope.launch { store.update { it.copy(order = it.order + (catId to newOrder)) } }
    }

    fun moveAcrossRows(delta: Int) {
        val (catIndex, list, i) = findMoving() ?: return
        val target = catIndex + delta
        if (target < 0 || target >= categorized.size) return
        val pkg = movePkg ?: return
        val app = apps.firstOrNull { it.pkg == pkg } ?: return
        val sourceId = categorized[catIndex].first.id
        val targetId = categorized[target].first.id
        val targetList = categorized[target].second
        if (targetList.any { it.pkg == pkg }) return // already in target section
        val insertAt = i.coerceAtMost(targetList.size)
        val newTargetOrder = targetList.map { it.pkg }.toMutableList().also { it.add(insertAt, pkg) }
        val newSourceOrder = list.map { it.pkg }.filter { it != pkg }
        scope.launch {
            store.update { cfg ->
                val effective = AppRepository.sectionsOf(app, cfg)
                cfg.copy(
                    sections = cfg.sections + (pkg to (effective - sourceId + targetId)),
                    order = cfg.order + (sourceId to newSourceOrder) + (targetId to newTargetOrder),
                )
            }
        }
    }

    // Reorder the moving app within its own section to a new index.
    fun reorderWithin(catIndex: Int, list: List<AppEntry>, from: Int, to: Int) {
        if (from == to) return
        val catId = categorized[catIndex].first.id
        val seq = list.map { it.pkg }.toMutableList()
        val p = seq.removeAt(from)
        seq.add(to.coerceIn(0, seq.size), p)
        scope.launch { store.update { it.copy(order = it.order + (catId to seq)) } }
    }

    // Grid: up/down move by a full row (±cols) inside the section; past the
    // top/bottom edge it hops to the neighbouring section.
    fun moveGridVertical(delta: Int, cols: Int) {
        val (catIndex, list, i) = findMoving() ?: return
        val size = list.size
        if (delta < 0) {
            if (i < cols) moveAcrossRows(-1) else reorderWithin(catIndex, list, i, i - cols)
        } else {
            val lastRowStart = ((size - 1) / cols) * cols
            if (i >= lastRowStart) moveAcrossRows(1)
            else reorderWithin(catIndex, list, i, minOf(i + cols, size - 1))
        }
    }

    // Dock: one flat sequence (all sections concatenated). Moving by [delta]
    // positions may cross a section block, so the moved app is re-homed into
    // its new neighbour's section and every affected order list is rebuilt from
    // the resulting sequence.
    // ponytail: a multi-section app (shown once in the dock) is re-homed only
    // for its displayed section; its other memberships are left as-is.
    fun moveDock(delta: Int) {
        val pkg = movePkg ?: return
        val fi = dockApps.indexOfFirst { it.pkg == pkg }
        if (fi < 0) return
        val target = fi + delta
        if (target < 0 || target >= dockApps.size) return
        val flat = dockApps.map { it.pkg }.toMutableList()
        flat.removeAt(fi)
        flat.add(target, pkg)

        val orderCats = config.categories.map { it.id }
        fun dockCatOf(p: String): String {
            val app = apps.firstOrNull { it.pkg == p } ?: return orderCats.last()
            val secs = AppRepository.sectionsOf(app, config)
            return orderCats.firstOrNull { it in secs } ?: orderCats.last()
        }
        val sourceCat = dockCatOf(pkg)
        val targetCat = flat.getOrNull(target - 1)?.let { dockCatOf(it) }
            ?: flat.getOrNull(target + 1)?.let { dockCatOf(it) }
            ?: sourceCat
        val movedApp = apps.firstOrNull { it.pkg == pkg }

        scope.launch {
            store.update { cfg ->
                val newSections = if (movedApp != null && sourceCat != targetCat) {
                    val eff = AppRepository.sectionsOf(movedApp, cfg)
                    cfg.sections + (pkg to (eff - sourceCat + targetCat))
                } else cfg.sections
                val catOf = { p: String -> if (p == pkg) targetCat else dockCatOf(p) }
                val newOrder = cfg.order.toMutableMap()
                for (cid in orderCats) {
                    val seq = flat.filter { catOf(it) == cid }
                    if (seq.isEmpty()) continue
                    // keep any stored (e.g. hidden) packages not on screen
                    val extras = cfg.order[cid].orEmpty().filter { it !in seq }
                    newOrder[cid] = seq + extras
                }
                cfg.copy(sections = newSections, order = newOrder)
            }
        }
    }

    fun moveHorizontal(delta: Int) =
        if (config.layout == LAYOUT_DOCK) moveDock(delta) else moveWithinRow(delta)

    fun moveVertical(delta: Int) = when (config.layout) {
        LAYOUT_GRID -> moveGridVertical(delta, gridCols)
        LAYOUT_DOCK -> moveDock(if (delta < 0) -dockCols else dockCols)
        else -> moveAcrossRows(delta)
    }

    val moveFocus = remember { FocusRequester() }
    LaunchedEffect(categorized, movePkg) {
        if (movePkg != null) runCatching { moveFocus.requestFocus() }
    }

    // ----- wallpaper: decoded off the main thread, downsampled, cached.
    // A ~100px copy is decoded alongside: upscaled by the GPU it looks
    // blurred, and crossfading it in costs one texture blend per frame
    // instead of a full-screen RenderEffect blur (heavy on TV GPUs). -----
    val wallpaperPair by produceState<Pair<ImageBitmap?, ImageBitmap?>>(
        initialValue = null to null, config.useCustomWallpaper, wallpaperVersion
    ) {
        value = if (!config.useCustomWallpaper) null to null
        else withContext(Dispatchers.IO) {
            val f = File(context.filesDir, "wallpaper.jpg")
            if (!f.exists()) null to null
            else runCatching {
                // 1280px is indistinguishable as a scrimmed background but the
                // texture is ~2.3× smaller than 1920 — TV GPUs choke on the
                // first upload of full-res textures. prepareToDraw() moves the
                // upload off the first visible frame.
                val sharp = decodeDownsampled(f, maxWidth = 1280)
                    ?.also { it.prepareToDraw() }?.asImageBitmap()
                val blurred = decodeDownsampled(f, maxWidth = 96)
                    ?.also { it.prepareToDraw() }?.asImageBitmap()
                sharp to blurred
            }.getOrDefault(null to null)
        }
    }
    val (wallpaperSharp, wallpaperBlurred) = wallpaperPair
    val presetBrush = remember(config.wallpaper) {
        WALLPAPERS[config.wallpaper.coerceIn(0, WALLPAPERS.size - 1)].brush()
    }

    val corner: Dp = CORNER_RADII[config.cornerRadius.coerceIn(0, CORNER_RADII.size - 1)]
    Box(Modifier.fillMaxSize()) {
    ScaledUi(uiScale) {
    androidx.compose.runtime.CompositionLocalProvider(LocalCornerRadius provides corner) {
    LiteTvTheme(accent = accent) {

        Box(Modifier.fillMaxSize()) {
            if (config.launcherMode == MODE_PC) {
                PcLauncherScreen(
                    apps = apps,
                    config = config,
                    store = store,
                    accent = accent,
                    net = net,
                    bt = bt,
                    weather = weather,
                    time = time,
                    date = date,
                    notifications = notifications,
                    wallpaperSharp = wallpaperSharp,
                    presetBrush = presetBrush,
                    onOpenSettings = { showSettings = true },
                    onOpenSearch = { showSearchDialog = true },
                    onOpenDashboard = { showDashboard = true },
                    onOpenNotifications = { showNotifications = true },
                    onLaunchApp = launchWithLockCheck,
                    onAppMenu = { app -> menuFor = app },
                )
            } else {
                TvLauncherScreen(
                    apps = apps,
                    config = config,
                    store = store,
                    accent = accent,
                    net = net,
                    bt = bt,
                    weather = weather,
                    backgroundMedia = backgroundMedia,
                    time = time,
                    date = date,
                    notifications = notifications,
                    hasNotificationPermission = hasNotificationPermission,
                    wallpaperSharp = wallpaperSharp,
                    wallpaperBlurred = wallpaperBlurred,
                    presetBrush = presetBrush,
                    onOpenSettings = { showSettings = true },
                    onOpenSearch = { showSearchDialog = true },
                    onOpenVoiceSearch = { showVoiceSearch = true },
                    onOpenDashboard = { showDashboard = true },
                    onOpenNotifications = { showNotifications = true },
                    onOpenWeatherDetails = { showWeatherDetails = true },
                    onOpenBackgroundMedia = { showBackgroundMediaDialog = true },
                    onLaunchApp = launchWithLockCheck,
                    onAppMenu = { app -> menuFor = app },
                )
            }

            // ----- dialogs -----
            menuFor?.let { app ->
                AppContextMenu(
                    app = app,
                    isHidden = app.pkg in config.hidden,
                    onDismiss = { menuFor = null },
                    onOpen = {
                        menuFor = null
                        launchWithLockCheck(app)
                    },
                    onMove = { menuFor = null },
                    onToggleHide = {
                        menuFor = null
                        scope.launch {
                            store.update {
                                it.copy(
                                    hidden = if (app.pkg in it.hidden) it.hidden - app.pkg
                                    else it.hidden + app.pkg
                                )
                            }
                        }
                    },
                    onAppInfo = { menuFor = null; Actions.openAppInfo(context, app.pkg) },
                    onClose = { menuFor = null; Actions.close(context, app.pkg) },
                    onUninstall = { menuFor = null; Actions.uninstall(context, app.pkg) },
                )
            }

            if (showNotifications) {
                NotificationSheet(
                    config = config,
                    onDismiss = {
                        showNotifications = false
                        hasNotificationPermission = com.gothwad.launcher.service.NotificationManagerBridge.isNotificationAccessGranted(context)
                    },
                )
            }

            if (showSettings) {
                SettingsSheet(
                    config = config,
                    apps = apps,
                    store = store,
                    onDismiss = { showSettings = false },
                    onWallpaperChanged = { wallpaperVersion++ },
                    onRerunWizard = {
                        showSettings = false
                        scope.launch { store.update { it.copy(setupDone = false) } }
                    },
                )
            }

            if (showVoiceSearch) {
                VoiceSearchDialog(
                    apps = apps,
                    onDismiss = { showVoiceSearch = false },
                )
            }

            if (showDashboard) {
                QuickDashboardDialog(
                    net = net,
                    bt = bt,
                    apps = apps,
                    onDismiss = { showDashboard = false },
                    onOpenSettings = {
                        showDashboard = false
                        showSettings = true
                    },
                )
            }

            if (showWeatherDetails) {
                WeatherDetailsDialog(
                    weather = weather,
                    onRefresh = { weatherRefreshTick++ },
                    onDismiss = { showWeatherDetails = false },
                )
            }

            if (showBackgroundMediaDialog) {
                BackgroundMediaDialog(
                    mediaState = backgroundMedia,
                    onDismiss = { showBackgroundMediaDialog = false },
                )
            }

            if (showSearchDialog) {
                SearchDialog(
                    apps = apps,
                    config = config,
                    accent = accent,
                    onLaunchApp = { app ->
                        showSearchDialog = false
                        launchWithLockCheck(app)
                    },
                    onDismiss = { showSearchDialog = false },
                )
            }

            if (appToUnlock != null) {
                AppLockDialog(
                    app = appToUnlock!!,
                    correctPin = config.appLockPin,
                    pinLength = config.appLockPinLength,
                    onUnlocked = {
                        val pkg = appToUnlock!!.pkg
                        appToUnlock = null
                        Actions.launchApp(context, pkg)
                    },
                    onDismiss = { appToUnlock = null },
                )
            }

            if (config.deviceLockEnabled && config.deviceLockPin.isNotEmpty() && !isDeviceUnlocked) {
                DeviceLockDialog(
                    correctPin = config.deviceLockPin,
                    pinLength = config.deviceLockPinLength,
                    onUnlocked = { isDeviceUnlocked = true },
                )
            }
        }
    }
    }
    }
    }
}

private fun decodeDownsampled(file: File, maxWidth: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val width = bounds.outWidth
    if (width <= 0) return null
    var sample = 1
    while (width / (sample * 2) >= maxWidth) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, opts)
}

