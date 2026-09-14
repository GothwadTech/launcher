package com.gothwad.launcher.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import com.gothwad.launcher.Actions
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.BackgroundMediaState
import com.gothwad.launcher.data.BluetoothDeviceStatus
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.GAP_SIZES
import com.gothwad.launcher.data.ICON_SIZES
import com.gothwad.launcher.data.LAYOUT_DOCK
import com.gothwad.launcher.data.LAYOUT_GRID
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.NetStatus
import com.gothwad.launcher.data.WeatherData
import com.gothwad.launcher.service.TvNotificationItem
import com.gothwad.launcher.ui.AppCard
import com.gothwad.launcher.ui.AppIcons
import com.gothwad.launcher.ui.LocalCornerRadius
import com.gothwad.launcher.ui.SmoothCornerShape
import com.gothwad.launcher.ui.StatusBar
import com.gothwad.launcher.ui.view.TvNativeAppGrid
import kotlinx.coroutines.launch

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
@Composable
fun TvLauncherScreen(
    apps: List<AppEntry>,
    config: LauncherConfig,
    store: ConfigStore,
    accent: Color,
    net: NetStatus,
    bt: BluetoothDeviceStatus,
    weather: WeatherData = WeatherData(),
    backgroundMedia: BackgroundMediaState = BackgroundMediaState(),
    time: String,
    date: String,
    notifications: List<TvNotificationItem>,
    hasNotificationPermission: Boolean,
    wallpaperSharp: ImageBitmap?,
    wallpaperBlurred: ImageBitmap?,
    presetBrush: Brush,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenVoiceSearch: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenWeatherDetails: () -> Unit,
    onOpenBackgroundMedia: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var movePkg by remember { mutableStateOf<String?>(null) }
    val moveFocus = remember { FocusRequester() }

    val cardWidth: Dp = ICON_SIZES[config.iconScale.coerceIn(0, ICON_SIZES.size - 1)]
    val gap: Dp = GAP_SIZES[config.spacing.coerceIn(0, GAP_SIZES.size - 1)]

    var introShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { introShown = true }

    val categorized = remember(apps, config.categories, config.sections, config.order, config.hidden) {
        val nonHidden = apps.filter { it.pkg !in config.hidden }
        config.categories.map { cat ->
            val inCat = nonHidden.filter { app ->
                val assigned = config.sections[app.pkg] ?: setOf(config.categories.firstOrNull()?.id ?: "apps")
                cat.id in assigned
            }
            val explicit = config.order[cat.id]
            val ordered = if (explicit == null) inCat else {
                val byPkg = inCat.associateBy { it.pkg }
                val fromOrder = explicit.mapNotNull { byPkg[it] }
                val remaining = inCat.filter { it.pkg !in explicit.toSet() }
                fromOrder + remaining
            }
            cat to ordered
        }.filter { it.second.isNotEmpty() }
    }

    val dockApps = remember(categorized, apps, config.hidden) {
        val all = categorized.flatMap { it.second }.distinctBy { it.pkg }
        if (all.isNotEmpty()) all else apps.filter { it.pkg !in config.hidden }
    }

    var dockExpanded by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                    onOpenSettings()
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        // Wallpaper layer
        if (wallpaperSharp != null) {
            Image(
                bitmap = wallpaperSharp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(presetBrush))
        }

        // Dock-expanded blur overlay
        val overlayAlpha by animateFloatAsState(
            targetValue = if (dockExpanded) 1f else 0f,
            animationSpec = tween(600),
            label = "dockOverlay",
        )
        if (overlayAlpha > 0.01f) {
            if (wallpaperBlurred != null) {
                Image(
                    bitmap = wallpaperBlurred,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = overlayAlpha,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * overlayAlpha))
            )
        }

        // Top & bottom readability scrim
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.25f to Color.Transparent,
                        0.75f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.65f),
                    )
                )
        )

        // Main Layout
        Column(Modifier.fillMaxSize()) {
            if (config.showStatusBar) {
                StatusBar(
                    net = net,
                    bt = bt,
                    weather = weather,
                    backgroundMedia = backgroundMedia,
                    time = time,
                    date = date,
                    showVpn = config.showVpnButton,
                    glass = config.statusBarGlass,
                    notificationCount = notifications.size,
                    hasNotificationPermission = hasNotificationPermission,
                    onSearchClick = onOpenSearch,
                    onDashboardClick = onOpenDashboard,
                    onVoiceSearchClick = onOpenVoiceSearch,
                    onBluetoothClick = onOpenDashboard,
                    onWeatherClick = onOpenWeatherDetails,
                    onBackgroundMediaClick = onOpenBackgroundMedia,
                    onNotificationsClick = onOpenNotifications,
                    onVpnClick = {
                        if (config.vpnApp.isNotEmpty()) Actions.launchApp(context, config.vpnApp)
                        else Actions.openVpnSettings(context)
                    },
                    onNetworkClick = { Actions.openNetworkSettings(context) },
                    onSettingsClick = onOpenSettings,
                )
            }

            when (config.layout) {
                LAYOUT_DOCK -> DockArea(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    apps = dockApps,
                    config = config,
                    accent = accent,
                    movePkg = movePkg,
                    moveFocus = moveFocus,
                    cardWidth = cardWidth,
                    gap = gap,
                    intro = introShown,
                    expanded = dockExpanded,
                    onExpandChange = { dockExpanded = it },
                    onLaunch = onLaunchApp,
                    onMenu = { app -> if (movePkg == null) onAppMenu(app) },
                )
                LAYOUT_GRID -> {
                    val density = LocalDensity.current
                    val cardHeightPx = with(density) { (cardWidth * 9f / 16f).toPx() }
                    val peekPx = cardHeightPx * 0.15f
                    val labelPx = with(density) { if (config.showCategoryNames) 34.dp.toPx() else 0f }
                    val pivotPx = peekPx + labelPx
                    val vPivot = remember(pivotPx) {
                        object : androidx.compose.foundation.gestures.BringIntoViewSpec {
                            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                                offset - pivotPx
                        }
                    }

                    CompositionLocalProvider(LocalBringIntoViewSpec provides vPivot) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRestorer(),
                            contentPadding = PaddingValues(
                                top = with(density) { pivotPx.toDp() },
                                bottom = with(density) { peekPx.toDp() } + 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(categorized.size, key = { categorized[it].first.id }) { rowIndex ->
                                val (cat, catApps) = categorized[rowIndex]
                                Column(Modifier.fillMaxWidth()) {
                                    if (config.showCategoryNames) {
                                        Text(
                                            text = cat.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
                                        )
                                    }
                                    GridSection(
                                        catApps = catApps,
                                        config = config,
                                        accent = accent,
                                        movePkg = movePkg,
                                        moveFocus = moveFocus,
                                        cardWidth = cardWidth,
                                        gap = gap,
                                        onLaunch = onLaunchApp,
                                        onMenu = onAppMenu,
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    // High-performance Android View RecyclerView with shared ViewPool & DiffUtil
                    TvNativeAppGrid(
                        modifier = Modifier.fillMaxSize(),
                        categorized = categorized,
                        config = config,
                        accent = accent,
                        cardWidth = cardWidth,
                        gap = gap,
                        movePkg = movePkg,
                        onLaunchApp = onLaunchApp,
                        onAppMenu = onAppMenu,
                    )
                }
            }
        }
    }
}

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
@Composable
private fun CarouselSection(
    catApps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    movePkg: String?,
    moveFocus: FocusRequester,
    cardWidth: Dp,
    gap: Dp,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pivotPx = with(density) { 48.dp.toPx() }
    val hPivot = remember(pivotPx) {
        object : androidx.compose.foundation.gestures.BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                offset - pivotPx
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides hPivot) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val delta = event.changes.firstOrNull()?.scrollDelta
                                if (delta != null) {
                                    val scrollAmount = if (delta.x != 0f) delta.x else delta.y
                                    listState.dispatchRawDelta(scrollAmount * 75f)
                                }
                            }
                        }
                    }
                },
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(catApps.size, key = { catApps[it].pkg }) { idx ->
                val app = catApps[idx]
                val moving = app.pkg == movePkg
                AppCard(
                    app = app,
                    isMoving = moving,
                    isHidden = app.pkg in config.hidden,
                    accent = accent,
                    cardWidth = cardWidth,
                    showLabel = config.showAppLabels,
                    modifier = if (moving) Modifier.focusRequester(moveFocus) else Modifier,
                    onLaunch = { onLaunch(app) },
                    onLongPress = { onMenu(app) },
                )
            }
        }
    }
}

@Composable
private fun GridSection(
    catApps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    movePkg: String?,
    moveFocus: FocusRequester,
    cardWidth: Dp,
    gap: Dp,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 48.dp)) {
        val (cols, gapUsed) = fitRow(maxWidth, cardWidth, gap)
        val rows = catApps.chunked(cols)
        Column(verticalArrangement = Arrangement.spacedBy(gapUsed)) {
            rows.forEach { rowApps ->
                Row(horizontalArrangement = Arrangement.spacedBy(gapUsed)) {
                    rowApps.forEach { app ->
                        val moving = app.pkg == movePkg
                        AppCard(
                            app = app,
                            isMoving = moving,
                            isHidden = app.pkg in config.hidden,
                            accent = accent,
                            cardWidth = cardWidth,
                            showLabel = config.showAppLabels,
                            modifier = if (moving) Modifier.focusRequester(moveFocus) else Modifier,
                            onLaunch = { onLaunch(app) },
                            onLongPress = { onMenu(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockArea(
    modifier: Modifier = Modifier,
    apps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    movePkg: String?,
    moveFocus: FocusRequester,
    cardWidth: Dp,
    gap: Dp,
    intro: Boolean,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    var expandFromCol by remember { mutableIntStateOf(0) }
    val dockFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (expanded) {
            runCatching { gridFocus.requestFocus() }
        } else {
            runCatching { dockFocus.requestFocus() }
        }
    }

    BoxWithConstraints(modifier) {
        val panelInnerPadding = if (maxWidth < 600.dp) 8.dp else 18.dp
        val available = maxWidth - (if (maxWidth < 600.dp) 16.dp else 80.dp) - panelInnerPadding * 2
        val (cols, gapUsed) = fitRow(available, cardWidth, gap)
        val cardHeight = cardWidth * 9f / 16f
        val rows = apps.chunked(cols)
        val targetRow = 0
        val targetCol = expandFromCol.coerceIn(0, (rows.getOrNull(targetRow)?.size ?: 1) - 1)
        val blockWidth = cardWidth * cols + gapUsed * (cols - 1)
        val peekH = cardHeight * 0.22f
        val dockRisePx = with(LocalDensity.current) {
            (maxHeight - peekH - cardHeight - 52.dp).toPx()
        }.toInt().coerceAtLeast(0)

        val rowGap by animateDpAsState(
            targetValue = if (expanded) gap else gap + cardHeight * 0.35f,
            animationSpec = tween(340, easing = FastOutSlowInEasing),
            label = "rowGap",
        )

        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                DockGlassPanel(
                    visible = intro && !expanded,
                    rowApps = rows.firstOrNull().orEmpty(),
                    config = config,
                    accent = accent,
                    movePkg = movePkg,
                    moveFocus = moveFocus,
                    dockFocus = dockFocus,
                    cardWidth = cardWidth,
                    gapUsed = gapUsed,
                    innerPadding = panelInnerPadding,
                    onExpandFrom = { i -> expandFromCol = i; onExpandChange(true) },
                    onLaunch = onLaunch,
                    onMenu = onMenu,
                )
            }

            AnimatedVisibility(
                visible = intro && !expanded && rows.size > 1,
                enter = fadeIn(tween(200, delayMillis = 220)),
                exit = fadeOut(tween(200)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onExpandChange(true) },
                    contentAlignment = Alignment.TopCenter
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gapUsed)) {
                        rows.getOrNull(1).orEmpty().forEach { app ->
                            PeekStrip(app, cardWidth, cardHeight, peekH)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(150)) + slideInVertically(tween(340, easing = FastOutSlowInEasing)) { dockRisePx },
            exit = fadeOut(tween(120, delayMillis = 240)) + slideOutVertically(tween(340, easing = FastOutSlowInEasing)) { dockRisePx },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && (e.key == Key.Escape || e.key == Key.Back)) {
                            onExpandChange(false)
                            true
                        } else false
                    },
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                item(key = "dock_collapse_bar") {
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            onClick = { onExpandChange(false) },
                            shape = ClickableSurfaceDefaults.shape(CircleShape),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = accent,
                                contentColor = Color.White,
                                focusedContentColor = Color.White,
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
                            modifier = Modifier
                                .size(width = 110.dp, height = 32.dp)
                                .pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = AppIcons.Down,
                                    contentDescription = "Collapse to Dock",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Dock",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
                items(rows.size, key = { rows.getOrNull(it)?.firstOrNull()?.pkg ?: "row$it" }) { r ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.width(blockWidth)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gapUsed)) {
                                rows[r].forEachIndexed { c, app ->
                                    val moving = app.pkg == movePkg
                                    AppCard(
                                        app = app,
                                        isMoving = moving,
                                        isHidden = app.pkg in config.hidden,
                                        accent = accent,
                                        cardWidth = cardWidth,
                                        showLabel = config.showAppLabels,
                                        modifier = when {
                                            moving -> Modifier.focusRequester(moveFocus)
                                            r == targetRow && c == targetCol -> Modifier.focusRequester(gridFocus)
                                            else -> Modifier
                                        },
                                        onLaunch = { onLaunch(app) },
                                        onLongPress = { onMenu(app) },
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

@Composable
private fun DockGlassPanel(
    visible: Boolean,
    rowApps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    movePkg: String?,
    moveFocus: FocusRequester,
    dockFocus: FocusRequester,
    cardWidth: Dp,
    gapUsed: Dp,
    innerPadding: Dp,
    onExpandFrom: (Int) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll) {
                        val delta = event.changes.firstOrNull()?.scrollDelta
                        if (delta != null && delta.y > 0f) {
                            onExpandFrom(0)
                        }
                    }
                }
            }
        }
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + slideInVertically(tween(340, easing = FastOutSlowInEasing)) { it },
            exit = fadeOut(tween(300)) + slideOutVertically(tween(340, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(SmoothCornerShape(LocalCornerRadius.current + 14.dp))
                    .background(Color(0xB3121418))
                    .background(Color.White.copy(alpha = 0.06f))
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200, delayMillis = 220)),
            exit = fadeOut(tween(90)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(innerPadding),
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .size(width = 44.dp, height = 16.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onExpandFrom(0) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Up,
                        contentDescription = "Expand Dock",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gapUsed),
                ) {
                    rowApps.forEachIndexed { i, app ->
                        val moving = app.pkg == movePkg
                        AppCard(
                            app = app,
                            isMoving = moving,
                            isHidden = app.pkg in config.hidden,
                            accent = accent,
                            cardWidth = cardWidth,
                            showLabel = config.showAppLabels,
                            modifier = (when {
                                moving -> Modifier.focusRequester(moveFocus)
                                i == 0 -> Modifier.focusRequester(dockFocus)
                                else -> Modifier
                            }).onPreviewKeyEvent { e ->
                                if (movePkg == null && e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                                    onExpandFrom(i); true
                                } else false
                            },
                            onLaunch = { onLaunch(app) },
                            onLongPress = { onMenu(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeekStrip(app: AppEntry, cardWidth: Dp, cardHeight: Dp, peekHeight: Dp) {
    Box(
        Modifier
            .width(cardWidth)
            .height(peekHeight)
            .clip(RoundedCornerShape(topStart = LocalCornerRadius.current, topEnd = LocalCornerRadius.current))
            .background(Color.White.copy(alpha = 0.04f))
    )
}

fun fitRow(availableWidth: Dp, cardWidth: Dp, minGap: Dp): Pair<Int, Dp> {
    if (availableWidth <= 0.dp || cardWidth <= 0.dp) return 1 to minGap
    val count = ((availableWidth + minGap) / (cardWidth + minGap)).toInt().coerceAtLeast(1)
    val totalGaps = count - 1
    val gap = if (totalGaps <= 0) minGap else ((availableWidth - cardWidth * count) / totalGaps).coerceAtLeast(minGap)
    return count to gap
}
