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
        val cardHeight = (cardWidth * 9f / 16f).toInt()
        return Pair(cardWidth, cardHeight)
    }

    private fun setupRecyclerView() {
        val density = resources.displayMetrics.density
        val gapPx = (GAP_SIZES.getOrElse(currentConfig.spacing.coerceIn(0, GAP_SIZES.size - 1)) { GAP_SIZES[2] } * density).toInt()
        val spanCount = 6
        val (defaultWidthPx, defaultHeightPx) = calculateCardDimensions(gapPx, density, spanCount)
        val defaultRadiusPx = (CORNER_RADII.getOrElse(currentConfig.cornerRadius.coerceIn(0, CORNER_RADII.size - 1)) { CORNER_RADII[2] } * density)
        val accentColor = ACCENTS.getOrElse(currentConfig.accent.coerceIn(0, ACCENTS.size - 1)) { ACCENTS[0] }

        appCardAdapter = AppCardAdapter(
            cardWidthPx = defaultWidthPx,
            cardHeightPx = defaultHeightPx,
            cornerRadiusPx = defaultRadiusPx,
            accentColor = accentColor,
            showLabels = true,
            isGridMode = true,
            lockedPackages = currentConfig.lockedApps,
            movingPackage = null,
            onLaunchApp = { app ->
                handleAppLaunch(app)
            },
            onAppMenu = { app ->
                openAppDetails(app.pkg)
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
        val spanCount = 6
        val (widthPx, heightPx) = calculateCardDimensions(gapPx, density, spanCount)
        val radiusPx = (CORNER_RADII.getOrElse(currentConfig.cornerRadius.coerceIn(0, CORNER_RADII.size - 1)) { CORNER_RADII[2] } * density)
        val accentColor = ACCENTS.getOrElse(currentConfig.accent.coerceIn(0, ACCENTS.size - 1)) { ACCENTS[0] }

        appCardAdapter?.updateConfig(
            widthPx = widthPx,
            heightPx = heightPx,
            radiusPx = radiusPx,
            accent = accentColor,
            labels = true,
            gridMode = true,
            locked = currentConfig.lockedApps,
            moving = null
        )
    }

    private fun rebuildAppList() {
        // Skip only the emissions that arrive *before* the first scan. An empty list after a
        // scan is a real state (everything hidden / uninstalled) and has to clear the grid -
        // the old `if (allApps.isEmpty()) return` left stale tiles on screen.
        if (!hasLoadedApps) return

        val visible = if (currentConfig.showHidden) allApps else allApps.filter { it.pkg !in currentConfig.hidden }
        val ordered = if (currentConfig.order.isNotEmpty()) {
            val explicit = currentConfig.order.values.flatten()
            if (explicit.isNotEmpty()) {
                val byPkg = visible.associateBy { it.pkg }
                val orderedFirst = explicit.mapNotNull { byPkg[it] }
                val remaining = visible.filter { it.pkg !in explicit.toSet() }
                orderedFirst + remaining
            } else {
                visible
            }
        } else {
            visible
        }
        appCardAdapter?.submitList(ordered)
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
