package com.gothwad.launcher.ui.tv

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.Actions
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.AppRepository
import com.gothwad.launcher.data.CORNER_RADII
import com.gothwad.launcher.data.CategoryAssigner
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.GAP_SIZES
import com.gothwad.launcher.data.ICON_SIZES
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.UI_SCALES
import com.gothwad.launcher.databinding.FragmentTvLauncherBinding
import com.gothwad.launcher.ui.ACCENTS
import com.gothwad.launcher.ui.AppLockGate
import com.gothwad.launcher.ui.WALLPAPERS
import com.gothwad.launcher.ui.dialogs.SetupWizardDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TvLauncherFragment : Fragment() {

    private var _binding: FragmentTvLauncherBinding? = null
    val binding get() = _binding!!

    private val sharedRecycledViewPool = RecyclerView.RecycledViewPool()
    private var categoryAdapter: TvCategoryAdapter? = null

    private var currentConfig: LauncherConfig = LauncherConfig()
    private var allApps: List<AppEntry> = emptyList()

    /** Guards against re-showing the first-run wizard on every config emission. */
    private var wizardShownThisView: Boolean = false

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

    /** Manual whole-UI scale multiplier from the Display settings (default 1.0x). */
    private fun uiScaleMultiplier(): Float =
        UI_SCALES.getOrElse(currentConfig.uiScale.coerceIn(0, UI_SCALES.size - 1)) { 1.0f }

    /**
     * Grid columns for the current UI scale: a larger scale means fewer (bigger) tiles,
     * a smaller scale more (smaller) ones. Keeps the 16:9 tile aspect ratio intact -
     * scaling only the height would have produced stretched tiles.
     */
    private fun gridSpanCount(): Int = when (currentConfig.uiScale.coerceIn(0, UI_SCALES.size - 1)) {
        0 -> 8
        1 -> 7
        2 -> 6
        3 -> 5
        else -> 4
    }

    private fun calculateCardDimensions(
        gapPx: Int,
        density: Float,
        columns: Int = gridSpanCount(),
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
        val spanCount = gridSpanCount()
        val (defaultWidthPx, defaultHeightPx) = calculateCardDimensions(gapPx, density, spanCount)
        val defaultRadiusPx = (CORNER_RADII.getOrElse(currentConfig.cornerRadius.coerceIn(0, CORNER_RADII.size - 1)) { CORNER_RADII[2] } * density)
        val accentColor = ACCENTS.getOrElse(currentConfig.accent.coerceIn(0, ACCENTS.size - 1)) { ACCENTS[0] }
        val accentArgb = accentColor

        categoryAdapter = TvCategoryAdapter(
            recycledViewPool = sharedRecycledViewPool,
            cardWidthPx = defaultWidthPx,
            cardHeightPx = defaultHeightPx,
            cornerRadiusPx = defaultRadiusPx,
            gapPx = gapPx,
            accentColor = accentArgb,
            showCategoryNames = currentConfig.showCategoryNames,
            showAppLabels = currentConfig.showAppLabels,
            gridSpanCount = spanCount,
            lockedPackages = currentConfig.lockedApps,
            movingPackage = null,
            onLaunchApp = { app ->
                handleAppLaunch(app)
            },
            onAppMenu = { app ->
                openAppDetails(app.pkg)
            }
        )

        binding.recyclerCategories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            adapter = categoryAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(4)
        }
    }

    private fun handleAppLaunch(app: AppEntry) {
        // App lock & hidden vault gate
        AppLockGate.evaluate(
            fragmentManager = parentFragmentManager,
            app = app,
            config = currentConfig,
        ) {
            Actions.launchApp(requireContext(), app.pkg)
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
                        rebuildCategorizedList()

                        // Show the first-run wizard when setup has never been completed.
                        // (Previously this compared against the *previous* config - and
                        // setupDone defaulted to true - so the wizard never appeared on a
                        // fresh install.)
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
     * Applies the configured wallpaper. Called from the config flow *and* from
     * MainActivity, so it must be safe when the view is already gone: it no-ops without a
     * binding and follows the view lifecycle (this used to NPE after the config flow
     * re-emitted post-destroy - issue #23).
     */
    fun applyWallpaper() {
        val viewBinding = _binding ?: return
        if (!isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            if (currentConfig.useCustomWallpaper) {
                val file = File(requireContext().filesDir, "wallpaper.jpg")
                if (file.exists()) {
                    val bmp = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                    if (bmp != null) {
                        viewBinding.imgWallpaper.setImageBitmap(bmp)
                        viewBinding.imgWallpaper.visibility = View.VISIBLE
                    }
                }
            } else {
                val preset = WALLPAPERS.getOrElse(currentConfig.wallpaper.coerceIn(0, WALLPAPERS.size - 1)) { WALLPAPERS[0] }
                val colors = preset.colors.toIntArray()

                val gradient = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
                viewBinding.imgWallpaper.setImageDrawable(gradient)
                viewBinding.imgWallpaper.visibility = View.VISIBLE
            }

            // Scrim mode: 0 = top & bottom, 1 = top, 2 = bottom, 3 = both, 4 = off
            when (currentConfig.scrimMode) {
                0, 3 -> {
                    viewBinding.viewScrimTop.visibility = View.VISIBLE
                    viewBinding.viewScrimBottom.visibility = View.VISIBLE
                }
                1 -> {
                    viewBinding.viewScrimTop.visibility = View.VISIBLE
                    viewBinding.viewScrimBottom.visibility = View.GONE
                }
                2 -> {
                    viewBinding.viewScrimTop.visibility = View.GONE
                    viewBinding.viewScrimBottom.visibility = View.VISIBLE
                }
                else -> {
                    viewBinding.viewScrimTop.visibility = View.GONE
                    viewBinding.viewScrimBottom.visibility = View.GONE
                }
            }
        }
    }

    fun onRescanRequested() {
        if (_binding == null || !isAdded) return
        viewLifecycleOwner.lifecycleScope.launch {
            loadApps()
        }
    }

    private suspend fun loadApps() {
        allApps = AppRepository.scan(requireContext())
        rebuildCategorizedList()
    }

    private fun updateDimensionsAndStyling() {
        val density = resources.displayMetrics.density
        val gapPx = (GAP_SIZES.getOrElse(currentConfig.spacing.coerceIn(0, GAP_SIZES.size - 1)) { GAP_SIZES[2] } * density).toInt()
        val spanCount = gridSpanCount()
        val (widthPx, heightPx) = calculateCardDimensions(gapPx, density, spanCount)
        val radiusPx = (CORNER_RADII.getOrElse(currentConfig.cornerRadius.coerceIn(0, CORNER_RADII.size - 1)) { CORNER_RADII[2] } * density)
        val accentColor = ACCENTS.getOrElse(currentConfig.accent.coerceIn(0, ACCENTS.size - 1)) { ACCENTS[0] }
        val accentArgb = accentColor

        categoryAdapter?.updateConfig(
            widthPx = widthPx,
            heightPx = heightPx,
            radiusPx = radiusPx,
            gap = gapPx,
            accent = accentArgb,
            categoryNames = currentConfig.showCategoryNames,
            appLabels = currentConfig.showAppLabels,
            spanCount = spanCount,
            locked = currentConfig.lockedApps,
            moving = null
        )
    }

    private fun rebuildCategorizedList() {
        if (allApps.isEmpty()) return

        // All visibility + section rules live in the (unit-tested) CategoryAssigner:
        // unassigned apps go to their real auto-category when enabled, hidden/vault apps
        // follow the "show hidden" setting, and "__all__" always shows everything (#15).
        val rows = CategoryAssigner.rows(allApps, currentConfig).map { (category, apps) ->
            CategoryRowItem(category = category, apps = apps)
        }
        categoryAdapter?.submitList(rows)
    }

    fun scrollToTop() {
        _binding?.recyclerCategories?.smoothScrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
