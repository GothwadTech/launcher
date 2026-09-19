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
import com.gothwad.launcher.GothwadApplication
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.AppRepository
import com.gothwad.launcher.data.CORNER_RADII
import com.gothwad.launcher.data.CategoryCfg
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.GAP_SIZES
import com.gothwad.launcher.data.ICON_SIZES
import com.gothwad.launcher.data.LAYOUT_GRID
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.FragmentTvLauncherBinding
import com.gothwad.launcher.ui.ACCENTS
import com.gothwad.launcher.ui.WALLPAPERS
import com.gothwad.launcher.ui.dialogs.PinEntryDialogFragment
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

    private fun calculateCardDimensions(isGrid: Boolean, gapPx: Int, density: Float): Pair<Int, Int> {
        return if (isGrid) {
            val screenWidthPx = resources.displayMetrics.widthPixels
            val horizontalPaddingPx = (48 * 2 * density).toInt() // 48dp on each side
            val columns = 6
            val availableWidthPx = screenWidthPx - horizontalPaddingPx - ((columns - 1) * gapPx)
            val cardWidth = (availableWidthPx / columns).coerceAtLeast((100 * density).toInt())
            val cardHeight = (cardWidth * 9f / 16f).toInt()
            Pair(cardWidth, cardHeight)
        } else {
            val defaultWidthDp = ICON_SIZES.getOrElse(currentConfig.iconScale.coerceIn(0, ICON_SIZES.size - 1)) { ICON_SIZES[2] }
            val widthPx = (defaultWidthDp * density).toInt()
            val heightPx = (widthPx * 9f / 16f).toInt()
            Pair(widthPx, heightPx)
        }
    }

    private fun setupRecyclerView() {
        val density = resources.displayMetrics.density
        val isGrid = currentConfig.layout == LAYOUT_GRID
        val gapPx = (GAP_SIZES.getOrElse(currentConfig.spacing.coerceIn(0, GAP_SIZES.size - 1)) { GAP_SIZES[2] } * density).toInt()
        val (defaultWidthPx, defaultHeightPx) = calculateCardDimensions(isGrid, gapPx, density)
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
            isGridMode = isGrid,
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

    private fun handleAppLaunch(app: AppEntry, skipLock: Boolean = false) {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        // UX-only in-launcher check to avoid overlay flicker on first click.
        // The authoritative, unbypassable security enforcement layer is in LauncherAccessibilityService.
        if (!skipLock && currentConfig.appLock.enabled && currentConfig.appLock.value.isNotEmpty() && app.pkg in currentConfig.lockedApps) {
            PinEntryDialogFragment.newInstance(
                title = "App Locked",
                subtitle = "Enter PIN/Password to launch ${app.label}",
                credential = currentConfig.appLock,
                isCancelable = true,
                onSuccess = {
                    com.gothwad.launcher.service.LauncherAccessibilityService.unlockedPackagesSession.add(app.pkg)
                    handleAppLaunch(app, skipLock = true)
                }
            ).show(parentFragmentManager, PinEntryDialogFragment.TAG)
        } else {
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
                        val firstRun = !config.setupDone && currentConfig.setupDone
                        currentConfig = config
                        updateDimensionsAndStyling()
                        applyWallpaper()
                        rebuildCategorizedList()

                        if (firstRun) {
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

    fun applyWallpaper() {
        lifecycleScope.launch {
            if (currentConfig.useCustomWallpaper) {
                val file = File(requireContext().filesDir, "wallpaper.jpg")
                if (file.exists()) {
                    val bmp = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                    if (bmp != null) {
                        binding.imgWallpaper.setImageBitmap(bmp)
                        binding.imgWallpaper.visibility = View.VISIBLE
                    }
                }
            } else {
                val preset = WALLPAPERS.getOrElse(currentConfig.wallpaper.coerceIn(0, WALLPAPERS.size - 1)) { WALLPAPERS[0] }
                val colors = preset.colors.toIntArray()

                val gradient = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
                binding.imgWallpaper.setImageDrawable(gradient)
                binding.imgWallpaper.visibility = View.VISIBLE
            }

            // Scrim mode: 0 = top & bottom, 1 = top, 2 = bottom, 3 = full, 4 = off
            when (currentConfig.scrimMode) {
                0 -> {
                    binding.viewScrimTop.visibility = View.VISIBLE
                    binding.viewScrimBottom.visibility = View.VISIBLE
                }
                1 -> {
                    binding.viewScrimTop.visibility = View.VISIBLE
                    binding.viewScrimBottom.visibility = View.GONE
                }
                2 -> {
                    binding.viewScrimTop.visibility = View.GONE
                    binding.viewScrimBottom.visibility = View.VISIBLE
                }
                3 -> {
                    binding.viewScrimTop.visibility = View.VISIBLE
                    binding.viewScrimBottom.visibility = View.VISIBLE
                }
                else -> {
                    binding.viewScrimTop.visibility = View.GONE
                    binding.viewScrimBottom.visibility = View.GONE
                }
            }
        }
    }

    fun onRescanRequested() {
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
        val isGrid = currentConfig.layout == LAYOUT_GRID
        val gapPx = (GAP_SIZES.getOrElse(currentConfig.spacing.coerceIn(0, GAP_SIZES.size - 1)) { GAP_SIZES[2] } * density).toInt()
        val (widthPx, heightPx) = calculateCardDimensions(isGrid, gapPx, density)
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
            gridMode = isGrid,
            locked = currentConfig.lockedApps,
            moving = null
        )
    }

    private fun rebuildCategorizedList() {
        if (allApps.isEmpty()) return

        val nonHidden = allApps.filter { it.pkg !in currentConfig.hidden }
        val categories = currentConfig.categories.ifEmpty {
            listOf(CategoryCfg("apps", "Apps"))
        }

        val items = categories.mapNotNull { cat ->
            val inCat = nonHidden.filter { app ->
                val assigned = currentConfig.sections[app.pkg] ?: setOf(categories.first().id)
                cat.id in assigned
            }
            val explicit = currentConfig.order[cat.id]
            val ordered = if (explicit == null) inCat else {
                val byPkg = inCat.associateBy { it.pkg }
                val fromOrder = explicit.mapNotNull { byPkg[it] }
                val remaining = inCat.filter { it.pkg !in explicit.toSet() }
                fromOrder + remaining
            }

            if (ordered.isNotEmpty()) {
                CategoryRowItem(category = cat, apps = ordered)
            } else null
        }

        categoryAdapter?.submitList(items)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
