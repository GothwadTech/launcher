package com.gothwad.launcher.ui.tv

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.Actions
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.AppRepository
import com.gothwad.launcher.data.CORNER_RADII
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.GAP_SIZES
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.FragmentTvLauncherBinding
import com.gothwad.launcher.ui.ACCENTS
import com.gothwad.launcher.ui.AppLockGate
import com.gothwad.launcher.ui.DEFAULT_WALLPAPER_INDEX
import com.gothwad.launcher.ui.DensityAdapter
import com.gothwad.launcher.ui.WALLPAPERS
import com.gothwad.launcher.ui.dialogs.SetupWizardDialogFragment
import com.gothwad.launcher.ui.view.AppCardAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * TV Launcher home screen: flat 6-column grid of all installed apps.
 * Category system has been removed for a unified, streamlined TV experience.
 */
class TvLauncherFragment : Fragment() {

    private var _binding: FragmentTvLauncherBinding? = null
    val binding get() = _binding!!

    private var appCardAdapter: AppCardAdapter? = null
    private var quickRowAdapter: AppCardAdapter? = null

    /** Accent sampled from the current wallpaper when `followWallpaperPalette` is on. */
    private var wallpaperPaletteColor: Int? = null

    private var currentConfig: LauncherConfig = LauncherConfig()
    private var allApps: List<AppEntry> = emptyList()

    /** Guards against re-showing the first-run wizard on every config emission. */
    private var wizardShownThisView: Boolean = false

    /** False until the first package scan completed, so early config emissions cannot blank the grid. */
    private var hasLoadedApps: Boolean = false

    /**
     * Identifies the wallpaper currently drawn ("preset:<index>" or
     * "custom:<size>:<modified>"). The config flow re-emits on every settings change; without
     * this guard each emission re-decoded the custom wallpaper and left the previous bitmap to
     * the GC - an easy OOM on a 2 GB TV box.
     */
    private var appliedWallpaperKey: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTvLauncherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeData()
    }

    private fun calculateCardDimensions(
        gapPx: Int,
        density: Float,
        columns: Int = 6,
    ): Pair<Int, Int> {
        val screenWidthPx = resources.displayMetrics.widthPixels
        val horizontalPaddingPx = (48 * 2 * density).toInt() // 48dp on each side
        val availableWidthPx = screenWidthPx - horizontalPaddingPx - ((columns - 1) * gapPx)
        val cardWidth = (availableWidthPx / columns).coerceAtLeast((60 * density).toInt())
        val mode = currentConfig.layoutMode.coerceIn(0, 3)
        val aspect = when (mode) {
            1 -> 3f / 4f   // compact: slightly taller than wide
            3 -> 1f        // square icon grid
            else -> 9f / 16f
        }
        // Card width in grid mode is MATCH_PARENT (columns define it); height carries the
        // layout mode's shape and the user's icon-size multiplier.
        val sizePct = currentConfig.iconSizePct.coerceIn(60, 160) / 100f
        val baseHeight = when (mode) {
            2 -> (cardWidth * 9f / 16f * 1.25f) // large tiles
            1 -> (cardWidth * 4f / 3f * 0.62f)  // compact keeps tiles small
            3 -> cardWidth                      // square
            else -> (cardWidth * 9f / 16f)
        }
        val cardHeight = (baseHeight * sizePct).toInt().coerceAtLeast((48 * density).toInt())
        return Pair(cardWidth, cardHeight)
    }

    /** Columns for the current layout mode (grid modes honour `config.gridColumns`). */
    private fun gridSpanCount(): Int {
        val base = currentConfig.gridColumns.coerceIn(3, 8)
        return when (currentConfig.layoutMode.coerceIn(0, 3)) {
            1 -> base + 2
            2 -> minOf(base, 4)
            3 -> base + 1
            else -> base
        }
    }

    /** Focus/hover animation duration from the anim-speed + reduced-motion settings. */
    private fun animDurationMs(): Int = when {
        currentConfig.reduceMotion || currentConfig.perfProfile == 0 -> 0
        currentConfig.animSpeed == 0 -> 240
        currentConfig.animSpeed == 2 -> 90
        else -> 150
    }

    private fun setupRecyclerView() {
        val density = resources.displayMetrics.density
        val gapPx = (GAP_SIZES.getOrElse(currentConfig.spacing.coerceIn(0, GAP_SIZES.size - 1)) { GAP_SIZES[2] } * density).toInt()
        val spanCount = gridSpanCount()
        val (defaultWidthPx, defaultHeightPx) = calculateCardDimensions(gapPx, density, spanCount)
        val defaultRadiusPx = (CORNER_RADII.getOrElse(currentConfig.cornerRadius.coerceIn(0, CORNER_RADII.size - 1)) { CORNER_RADII[2] } * density)
        val accentColor = accentColor()
        val theme = theme()

        appCardAdapter = AppCardAdapter(
            cardWidthPx = defaultWidthPx,
            cardHeightPx = defaultHeightPx,
            cornerRadiusPx = defaultRadiusPx,
            accentColor = accentColor,
            showLabels = currentConfig.showTileLabels,
            isGridMode = true,
            lockedPackages = currentConfig.lockedApps,
            movingPackage = null,
            themeSurface = theme.surface,
            focusScalePct = currentConfig.focusScalePct.coerceIn(100, 140),
            focusEffect = currentConfig.focusEffect.coerceIn(0, 3),
            animDurationMs = animDurationMs(),
            labelSizeSp = currentConfig.labelSizeSp.coerceIn(10, 16),
            onLaunchApp = { app ->
                handleAppLaunch(app)
            },
            onAppMenu = { app ->
                openAppMenu(app)
            }
        )

        val gridLayoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.recyclerCategories.apply {
            layoutManager = gridLayoutManager
            adapter = appCardAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(12)
            while (itemDecorationCount > 0) {
                removeItemDecorationAt(0)
            }
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    val position = parent.getChildAdapterPosition(view)
                    if (position == RecyclerView.NO_POSITION) return
                    val column = position % spanCount
                    outRect.left = column * gapPx / spanCount
                    outRect.right = gapPx - (column + 1) * gapPx / spanCount
                    outRect.bottom = gapPx
                }
            })
        }

        setupQuickRow()
    }

    /**
     * The favourites + recents strip above the grid. One horizontal list, same card visuals
     * as the grid (fixed card size), hidden entirely when both toggles are off.
     */
    private fun setupQuickRow() {
        quickRowAdapter = AppCardAdapter(
            cardWidthPx = (150 * resources.displayMetrics.density).toInt(),
            cardHeightPx = (84 * resources.displayMetrics.density).toInt(),
            cornerRadiusPx = (CORNER_RADII.getOrElse(currentConfig.cornerRadius.coerceIn(0, CORNER_RADII.size - 1)) { CORNER_RADII[2] } * resources.displayMetrics.density),
            accentColor = accentColor(),
            showLabels = true,
            isGridMode = false,
            lockedPackages = currentConfig.lockedApps,
            movingPackage = null,
            themeSurface = theme().surface,
            focusScalePct = currentConfig.focusScalePct.coerceIn(100, 140),
            focusEffect = currentConfig.focusEffect.coerceIn(0, 3),
            animDurationMs = animDurationMs(),
            labelSizeSp = currentConfig.labelSizeSp.coerceIn(10, 16),
            onLaunchApp = { app -> handleAppLaunch(app) },
            onAppMenu = { app -> openAppMenu(app) },
        )
        binding.recyclerQuickRow.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = quickRowAdapter
            itemAnimator = null
        }
    }

    private fun theme(): com.gothwad.launcher.ui.LauncherTheme =
        com.gothwad.launcher.ui.THEMES.getOrElse(currentConfig.themeIndex.coerceIn(0, com.gothwad.launcher.ui.THEMES.size - 1)) {
            com.gothwad.launcher.ui.THEMES[0]
        }

    /** Accent colour: theme accent, or the user's pick, or a wallpaper-derived palette colour. */
    private fun accentColor(): Int {
        if (currentConfig.followWallpaperPalette) {
            wallpaperPaletteColor?.let { return it }
        }
        return ACCENTS.getOrElse(currentConfig.accent.coerceIn(0, ACCENTS.size - 1)) { ACCENTS[0] }
    }

    private fun handleAppLaunch(app: AppEntry) {
        // App lock & hidden vault gate
        val context = requireContext()
        AppLockGate.evaluate(
            context = context,
            fragmentManager = parentFragmentManager,
            app = app,
            config = currentConfig,
        ) {
            Actions.launchApp(context, app.pkg)
        }
    }

    private fun openAppDetails(pkg: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    /** Long-press / menu-key quick actions for an app tile. */
    private fun openAppMenu(app: AppEntry) {
        if (!isAdded) return
        // The dialog writes ConfigStore directly; the config flow re-renders the grid.
        AppMenuDialogFragment.newInstance(app = app, config = currentConfig)
            .show(parentFragmentManager, AppMenuDialogFragment.TAG)
    }

    private fun observeData() {
        val configStore = ConfigStore(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    configStore.flow.collectLatest { config ->
                        currentConfig = config
                        updateDimensionsAndStyling()
                        applyWallpaper()
                        rebuildAppList()

                        if (!config.setupDone && !wizardShownThisView && isResumed) {
                            wizardShownThisView = true
                            showSetupWizard()
                        }
                    }
                }

                launch {
                    loadApps()
                }
            }
        }
    }

    private fun showSetupWizard() {
        SetupWizardDialogFragment.newInstance {
            viewLifecycleOwner.lifecycleScope.launch {
                ConfigStore(requireContext()).update { it.copy(setupDone = true) }
            }
        }.show(parentFragmentManager, SetupWizardDialogFragment.TAG)
    }

    /**
     * Applies the configured wallpaper and scrim.
     *
     * Cheap and idempotent: the bitmap is only re-decoded when the source actually changed
     * (see [appliedWallpaperKey]), and it is decoded down-sampled to the physical screen size
     * instead of at the camera's full resolution.
     */
    fun applyWallpaper() {
        val viewBinding = _binding ?: return
        if (!isAdded) return
        val appContext = requireContext().applicationContext
        val config = currentConfig

        viewLifecycleOwner.lifecycleScope.launch {
            val customFile = File(appContext.filesDir, WALLPAPER_FILE)
            val wantsCustom = config.useCustomWallpaper && customFile.exists()
            val key = if (wantsCustom) {
                "custom:${customFile.length()}:${customFile.lastModified()}"
            } else {
                "preset:${config.wallpaper}"
            }

            if (key != appliedWallpaperKey) {
                if (wantsCustom) {
                    val (screenW, screenH) = DensityAdapter.getRealScreenSize(appContext)
                    val bmp = withContext(Dispatchers.IO) {
                        decodeSampledWallpaper(customFile, screenW, screenH)
                    }
                    if (bmp != null) {
                        viewBinding.imgWallpaper.setImageBitmap(bmp)
                        viewBinding.imgWallpaper.visibility = View.VISIBLE
                        appliedWallpaperKey = key
                        updateWallpaperPalette(sampleBitmapColor(bmp))
                    }
                } else {
                    // Also the fallback when a custom wallpaper is configured but its file is
                    // gone (cache clear / failed pick) - better a preset than a black screen.
                    val preset = WALLPAPERS.getOrElse(
                        config.wallpaper.coerceIn(0, WALLPAPERS.size - 1)
                    ) { WALLPAPERS[DEFAULT_WALLPAPER_INDEX] }
                    viewBinding.imgWallpaper.setImageDrawable(
                        GradientDrawable(
                            GradientDrawable.Orientation.TL_BR,
                            preset.colors.toIntArray(),
                        )
                    )
                    viewBinding.imgWallpaper.visibility = View.VISIBLE
                    appliedWallpaperKey = key
                    updateWallpaperPalette(averageColor(preset.colors))
                }
            }

            // Scrim mode: 0 = full screen, 1 = top edge, 2 = bottom edge, 3 = both edges, 4 = off.
            // Mode 0 used to be documented (and advertised in Settings) as "Full Screen Scrim"
            // while drawing exactly the same top+bottom edges as mode 3.
            val full = config.scrimMode == 0
            val top = config.scrimMode == 0 || config.scrimMode == 1 || config.scrimMode == 3
            val bottom = config.scrimMode == 0 || config.scrimMode == 2 || config.scrimMode == 3
            viewBinding.viewScrimFull.visibility = if (full) View.VISIBLE else View.GONE
            viewBinding.viewScrimTop.visibility = if (top) View.VISIBLE else View.GONE
            viewBinding.viewScrimBottom.visibility = if (bottom) View.VISIBLE else View.GONE
        }
    }

    /**
     * Decodes [file] at most as large as the screen, halving the sample size until the result
     * would be smaller than the target. Keeps a 4000x3000 phone photo at ~2 MP instead of 12 MP.
     */
    private fun decodeSampledWallpaper(file: File, reqWidth: Int, reqHeight: Int): Bitmap? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            var sample = 1
            if (bounds.outWidth > 0 && bounds.outHeight > 0 && reqWidth > 0 && reqHeight > 0) {
                while (bounds.outWidth / (sample * 2) >= reqWidth &&
                    bounds.outHeight / (sample * 2) >= reqHeight
                ) {
                    sample *= 2
                }
            }
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }.getOrNull()

    /** Average colour of a gradient preset (cheap palette for palette-accent mode). */
    private fun averageColor(colors: List<Int>): Int {
        if (colors.isEmpty()) return ACCENTS[0]
        var r = 0L; var g = 0L; var b = 0L
        for (c in colors) {
            r += (c shr 16 and 0xFF); g += (c shr 8 and 0xFF); b += (c and 0xFF)
        }
        val n = colors.size
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    /** Samples a 16x16 downscale of [bmp] and averages it — on the caller's (main) frame,
        this is 256 getPixel calls, so it's only run when the wallpaper actually changes. */
    private fun sampleBitmapColor(bmp: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bmp, 16, 16, true)
        var r = 0L; var g = 0L; var b = 0L
        for (x in 0 until small.width) {
            for (y in 0 until small.height) {
                val p = small.getPixel(x, y)
                r += (p shr 16 and 0xFF); g += (p shr 8 and 0xFF); b += (p and 0xFF)
            }
        }
        val n = (small.width * small.height).coerceAtLeast(1)
        if (small !== bmp) small.recycle()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    private fun updateWallpaperPalette(color: Int) {
        if (wallpaperPaletteColor == color) return
        wallpaperPaletteColor = color
        if (currentConfig.followWallpaperPalette) updateDimensionsAndStyling()
    }

    fun onRescanRequested() {
        if (_binding == null || !isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            loadApps()
        }
    }

    private suspend fun loadApps() {
        val context = requireContext().applicationContext
        // scan() pins itself to Dispatchers.IO; the fragment only touches the result.
        allApps = AppRepository.scan(context)
        hasLoadedApps = true
        rebuildAppList()
    }

    private fun updateDimensionsAndStyling() {
        val density = resources.displayMetrics.density
        val gapPx = (GAP_SIZES.getOrElse(currentConfig.spacing.coerceIn(0, GAP_SIZES.size - 1)) { GAP_SIZES[2] } * density).toInt()
        val spanCount = gridSpanCount()
        val (widthPx, heightPx) = calculateCardDimensions(gapPx, density, spanCount)
        val radiusPx = (CORNER_RADII.getOrElse(currentConfig.cornerRadius.coerceIn(0, CORNER_RADII.size - 1)) { CORNER_RADII[2] } * density)
        val accentColor = accentColor()
        val theme = theme()

        // Column count can change (layout mode / gridColumns) - the decoration and the
        // layout manager must be rebuilt, not just restyled.
        val glm = binding.recyclerCategories.layoutManager as? GridLayoutManager
        if (glm != null && glm.spanCount != spanCount) {
            glm.spanCount = spanCount
        }

        appCardAdapter?.updateConfig(
            widthPx = widthPx,
            heightPx = heightPx,
            radiusPx = radiusPx,
            accent = accentColor,
            labels = currentConfig.showTileLabels,
            gridMode = true,
            locked = currentConfig.lockedApps,
            moving = null,
            themeSurface = theme.surface,
            focusScalePct = currentConfig.focusScalePct.coerceIn(100, 140),
            focusEffect = currentConfig.focusEffect.coerceIn(0, 3),
            animDurationMs = animDurationMs(),
            labelSizeSp = currentConfig.labelSizeSp.coerceIn(10, 16),
        )
        quickRowAdapter?.updateConfig(
            widthPx = (150 * density).toInt(),
            heightPx = (84 * density).toInt(),
            radiusPx = radiusPx,
            accent = accentColor,
            labels = true,
            gridMode = false,
            locked = currentConfig.lockedApps,
            moving = null,
            themeSurface = theme.surface,
            focusScalePct = currentConfig.focusScalePct.coerceIn(100, 140),
            focusEffect = currentConfig.focusEffect.coerceIn(0, 3),
            animDurationMs = animDurationMs(),
            labelSizeSp = currentConfig.labelSizeSp.coerceIn(10, 16),
        )
    }

    private fun rebuildAppList() {
        // Skip only the emissions that arrive *before* the first scan. An empty list after a
        // scan is a real state (everything hidden / uninstalled) and has to clear the grid -
        // the old `if (allApps.isEmpty()) return` left stale tiles on screen.
        if (!hasLoadedApps) return

        val visible = if (currentConfig.showHidden) allApps else allApps.filter { it.pkg !in currentConfig.hidden }

        // Sort mode (0 = name A-Z, 1 = install date, 2 = recently updated, 3 = manual).
        val baseSorted = when (currentConfig.appSort.coerceIn(0, 3)) {
            1 -> visible.sortedBy { it.firstInstall }
            2 -> visible.sortedByDescending { it.stamp }
            3 -> visible.sortedBy { it.firstInstall }
            else -> visible.sortedBy { it.label.lowercase() }
        }

        val ordered = if (currentConfig.order.isNotEmpty()) {
            val explicit = currentConfig.order.values.flatten()
            if (explicit.isNotEmpty()) {
                val byPkg = baseSorted.associateBy { it.pkg }
                val orderedFirst = explicit.mapNotNull { byPkg[it] }
                val remaining = baseSorted.filter { it.pkg !in explicit.toSet() }
                orderedFirst + remaining
            } else {
                baseSorted
            }
        } else {
            baseSorted
        }
        appCardAdapter?.submitList(ordered)
        rebuildQuickRow()
    }

    /** Favourites first, then recently launched apps (both honour their settings toggles). */
    private fun rebuildQuickRow() {
        val adapter = quickRowAdapter ?: return
        val byPkg = allApps.associateBy { it.pkg }
        val items = LinkedHashMap<String, AppEntry>()
        if (currentConfig.showFavoritesRow) {
            for (pkg in currentConfig.favorites) {
                byPkg[pkg]?.let { items[pkg] = it }
            }
        }
        if (currentConfig.showRecentRow) {
            for (pkg in com.gothwad.launcher.data.AppLaunchTracker.recent()) {
                if (items.size >= 12) break
                if (pkg !in items) byPkg[pkg]?.let { items[pkg] = it }
            }
        }
        val list = items.values.toList()
        binding.recyclerQuickRow.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        // The grid slides down when the strip is visible so tiles never sit under it.
        val density = resources.displayMetrics.density
        binding.recyclerCategories.setPadding(
            binding.recyclerCategories.paddingLeft,
            if (list.isEmpty()) (84 * density).toInt() else (196 * density).toInt(),
            binding.recyclerCategories.paddingRight,
            binding.recyclerCategories.paddingBottom,
        )
        adapter.submitList(list)
    }

    fun scrollToTop() {
        _binding?.recyclerCategories?.smoothScrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        /** File name the Settings photo picker writes the custom wallpaper to. */
        const val WALLPAPER_FILE = "wallpaper.jpg"
    }
}
