package com.gothwad.launcher.ui.tv

import android.content.Intent
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
     * Applies the configured wallpaper.
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
        if (allApps.isEmpty()) return

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
}
