package com.gothwad.launcher.ui.dialogs

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.gothwad.launcher.Actions
import com.gothwad.launcher.R
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.ui.DensityAdapter
import com.gothwad.launcher.data.AppLaunchTracker
import com.gothwad.launcher.data.ButtonMappingManager
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LAYOUT_CAROUSEL
import com.gothwad.launcher.data.LAYOUT_DOCK
import com.gothwad.launcher.data.LAYOUT_GRID
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.LockSecurity
import com.gothwad.launcher.data.UI_SCALES
import com.gothwad.launcher.databinding.DialogEditTextBinding
import com.gothwad.launcher.databinding.ItemButtonMappingBinding
import com.gothwad.launcher.databinding.ItemPickAppBinding
import com.gothwad.launcher.databinding.ItemRecentAppBinding
import com.gothwad.launcher.databinding.ItemToggleAppBinding
import com.gothwad.launcher.databinding.SheetSettingsBinding
import com.gothwad.launcher.ui.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android TV Right-Side Overlay Settings Panel
 * Matches the native Android TV / Google TV slide-in settings drawer.
 */
class SettingsBottomSheetFragment : DialogFragment() {

    private var _binding: SheetSettingsBinding? = null
    private val binding get() = _binding!!

    var config: LauncherConfig = LauncherConfig()
    var apps: List<AppEntry> = emptyList()
    var onWallpaperChanged: (() -> Unit)? = null
    var onRerunWizard: (() -> Unit)? = null

    private lateinit var store: ConfigStore
    private var currentSubPage: View? = null

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        // The picker result can arrive after the sheet was dismissed - never touch the
        // view (or requireContext()) without checking first, and follow the view lifecycle.
        if (uri != null && _binding != null && isAdded) {
            val context = requireContext()
            viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        File(context.filesDir, "wallpaper.jpg").outputStream().use { out ->
                            input.copyTo(out)
                        }
                    }
                }
                store.update {
                    it.copy(useCustomWallpaper = true)
                }
                config = config.copy(useCustomWallpaper = true)
                updateSubtitles()
                onWallpaperChanged?.invoke()
                Actions.toast(context, "Custom wallpaper applied")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_LiteTV_Dialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setGravity(Gravity.END)
            window.setWindowAnimations(R.style.Animation_TvSettingsPanel)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Recreated after process death: the host callbacks are gone, so every toggle
        // would still write config but nothing would refresh (and the app/hidden lists
        // are empty). Close instead of presenting a half-dead panel.
        if (onWallpaperChanged == null && onRerunWizard == null) {
            dismiss()
            return
        }

        store = ConfigStore(requireContext())

        setupBackdropAndHeader()
        setupMenuIcons()
        updateSubtitles()
        setupRootMenuClicks()
        bindWallpaperSettings()
        bindDisplaySettings()
        bindStatusBarSettings()
        bindAppsSettings()
        bindSecuritySettings()
        bindButtonMappingSettings()
        bindAboutSettings()
        setupBackKeyHandling()
    }

    private fun setupBackdropAndHeader() {
        binding.scrimBackdrop.setOnClickListener {
            dismiss()
        }

        binding.btnCloseSettings.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE)
        )
        binding.btnCloseSettings.setOnClickListener {
            dismiss()
        }

        binding.btnBack.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_BACK, Color.WHITE)
        )
        binding.btnBack.setOnClickListener {
            if (currentSubPage == binding.subpagePickApp) {
                if (pickAppMode == PickAppMode.VPN_SHORTCUT) {
                    navigateToSubPage(binding.pageStatusbar, "Status Bar & Clock")
                } else {
                    navigateToSubPage(binding.subpageButtonMapping, "Remote Button Mapping")
                }
            } else if (currentSubPage == binding.subpageToggleApps) {
                navigateToSubPage(binding.pageSecurity, "Security & Locks")
            } else {
                navigateToRoot()
            }
        }
    }

    private fun setupMenuIcons() {
        val iconColor = Color.WHITE
        val chevronColor = Color.parseColor("#64748B")

        // Root Menu Icons
        binding.iconWallpaper.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_IMAGE, iconColor))
        binding.chevronWallpaper.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconDisplay.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DISPLAY, iconColor))
        binding.chevronDisplay.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconStatusbar.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_TIME, iconColor))
        binding.chevronStatusbar.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconApps.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, iconColor))
        binding.chevronApps.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconSecurity.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK, iconColor))
        binding.chevronSecurity.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconButtonMapping.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_ACCESSIBILITY, iconColor))
        binding.chevronButtonMapping.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconNetwork.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI, iconColor))
        binding.chevronNetwork.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconDevicePrefs.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, iconColor))
        binding.chevronDevicePrefs.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconPermissions.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SHIELD, iconColor))
        binding.chevronPermissions.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconWizard.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIZARD, iconColor))

        binding.iconAbout.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_INFO, iconColor))
        binding.chevronAbout.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))
    }

    private fun updateSubtitles() {
        // Wallpaper subtitle
        val wpName = if (config.useCustomWallpaper) "Custom Image" else when (config.wallpaper) {
            0 -> "Deep Space"
            1 -> "Aurora"
            2 -> "Cyberpunk"
            3 -> "Sunset"
            4 -> "Obsidian"
            else -> "Preset ${config.wallpaper}"
        }
        val scrimName = when (config.scrimMode) {
            0 -> "Full Scrim"
            1 -> "Top Only"
            2 -> "Bottom Only"
            3 -> "Both"
            4 -> "None"
            else -> "Normal"
        }
        binding.txtWallpaperSubtitle.text = "$wpName • $scrimName"

        // Display subtitle
        val layoutName = when (config.layout) {
            LAYOUT_CAROUSEL -> "Carousel"
            LAYOUT_DOCK -> "Dock"
            else -> "Grid"
        }
        val scaleName = when (config.iconScale) {
            0 -> "Small (120dp)"
            1 -> "Medium (150dp)"
            2 -> "Normal (190dp)"
            3 -> "Large (230dp)"
            4 -> "Huge (270dp)"
            else -> "Normal"
        }
        val uiScaleName = UI_SCALES.getOrElse(config.uiScale.coerceIn(0, UI_SCALES.size - 1)) { 1.0f }
        binding.txtDisplaySubtitle.text = "$layoutName layout • $scaleName • UI ${uiScaleName}x"

        // Status bar subtitle
        val sbVisible = if (config.showStatusBar) "Visible" else "Hidden"
        val clockFormat = if (config.h24) "24h format" else "12h format"
        binding.txtStatusbarSubtitle.text = "$sbVisible • $clockFormat"

        // Apps subtitle
        val hiddenCount = config.hidden.size
        binding.txtAppsSubtitle.text = if (hiddenCount > 0) "$hiddenCount apps protected" else "Recent apps • Auto-sort"
        binding.txtHiddenAppsCount.text = "$hiddenCount apps configured in secret vault"

        // Security subtitle (independent status summary per section)
        val activeLocks = mutableListOf<String>()
        if (config.appLock.enabled) activeLocks.add("App")
        if (config.hiddenAppsLock.enabled) activeLocks.add("Vault")
        binding.txtSecuritySubtitle.text = if (activeLocks.isNotEmpty()) {
            activeLocks.joinToString(" & ") + " Lock Active"
        } else {
            "No Locks Active"
        }

        // Button Mapping Subtitle
        val mappingCount = config.buttonMap.size
        binding.txtButtonMappingSubtitle.text = if (mappingCount > 0) {
            "$mappingCount buttons customized"
        } else {
            "Map dedicated TV remote hotkeys to apps"
        }

        // Network Subtitle
        try {
            val wifi = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifi?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "") ?: ""
            if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                binding.txtNetworkSubtitle.text = ssid
            } else {
                binding.txtNetworkSubtitle.text = "Wi-Fi and Ethernet"
            }
        } catch (_: Exception) {
            binding.txtNetworkSubtitle.text = "Wi-Fi and Ethernet"
        }
    }

    private fun setupRootMenuClicks() {
        binding.rowWallpaper.setOnClickListener {
            navigateToSubPage(binding.pageWallpaper, "Wallpaper & Theme")
        }

        binding.rowDisplay.setOnClickListener {
            navigateToSubPage(binding.pageDisplay, "Display & Layout")
        }

        binding.rowStatusbar.setOnClickListener {
            navigateToSubPage(binding.pageStatusbar, "Status Bar & Clock")
        }

        binding.rowApps.setOnClickListener {
            navigateToSubPage(binding.pageApps, "Apps & Secret Vault")
        }

        binding.rowSecurity.setOnClickListener {
            val primaryLock = when {
                config.appLock.enabled && config.appLock.ready -> config.appLock
                config.hiddenAppsLock.enabled && config.hiddenAppsLock.ready -> config.hiddenAppsLock
                else -> null
            }
            if (primaryLock != null) {
                PinEntryDialogFragment.newInstance(
                    title = "Security Settings",
                    subtitle = "Enter credential to access security settings",
                    credential = primaryLock,
                    isCancelable = true,
                    onSuccess = {
                        navigateToSubPage(binding.pageSecurity, "Security & PIN Lock")
                    }
                ).show(parentFragmentManager, PinEntryDialogFragment.TAG)
            } else {
                navigateToSubPage(binding.pageSecurity, "Security & PIN Lock")
            }
        }

        binding.rowButtonMapping.setOnClickListener {
            navigateToSubPage(binding.subpageButtonMapping, "Remote Button Mapping")
        }

        binding.rowNetwork.setOnClickListener {
            safeStartActivity(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
        }

        binding.rowDevicePrefs.setOnClickListener {
            safeStartActivity(Settings.ACTION_SETTINGS)
        }

        binding.rowPermissions.setOnClickListener {
            safeStartActivity(Settings.ACTION_APPLICATION_SETTINGS, Settings.ACTION_SETTINGS)
        }

        binding.rowWizard.setOnClickListener {
            dismiss()
            onRerunWizard?.invoke()
        }

        binding.rowAbout.setOnClickListener {
            navigateToSubPage(binding.pageAbout, "About Gothwad Launcher")
        }
    }

    private fun navigateToSubPage(page: View, title: String) {
        currentSubPage = page
        binding.layoutRootMenu.visibility = View.GONE
        hideAllSubPages()
        page.visibility = View.VISIBLE
        binding.txtSettingsTitle.text = title
        binding.btnBack.visibility = View.VISIBLE
        binding.scrollContainer.scrollTo(0, 0)
    }

    private fun navigateToRoot() {
        currentSubPage = null
        hideAllSubPages()
        binding.layoutRootMenu.visibility = View.VISIBLE
        binding.txtSettingsTitle.text = "Settings"
        binding.btnBack.visibility = View.GONE
        binding.scrollContainer.scrollTo(0, 0)
    }

    private fun hideAllSubPages() {
        binding.pageWallpaper.visibility = View.GONE
        binding.pageDisplay.visibility = View.GONE
        binding.pageStatusbar.visibility = View.GONE
        binding.pageApps.visibility = View.GONE
        binding.pageSecurity.visibility = View.GONE
        binding.pageAbout.visibility = View.GONE
        binding.subpageButtonMapping.visibility = View.GONE
        binding.subpagePickApp.visibility = View.GONE
        binding.subpageToggleApps.visibility = View.GONE
    }

    private fun setupBackKeyHandling() {
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (binding.cardListeningForButton.visibility == View.VISIBLE) {
                    cancelKeyListening()
                    return@setOnKeyListener true
                }
                if (currentSubPage == binding.subpagePickApp) {
                    navigateToSubPage(binding.subpageButtonMapping, "Remote Button Mapping")
                    return@setOnKeyListener true
                }
                if (currentSubPage == binding.subpageToggleApps) {
                    navigateToSubPage(binding.pageSecurity, "Security & Locks")
                    return@setOnKeyListener true
                }
                if (currentSubPage != null) {
                    navigateToRoot()
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    // =========================================================================
    // SUB-PAGE 1: WALLPAPER
    // =========================================================================
    private fun bindWallpaperSettings() {
        val wpButtons = listOf(
            binding.btnWp0 to 0,
            binding.btnWp1 to 1,
            binding.btnWp2 to 2,
            binding.btnWp3 to 3,
            binding.btnWp4 to 4
        )

        for ((btn, presetIdx) in wpButtons) {
            btn.setOnClickListener {
                lifecycleScope.launch {
                    store.update {
                        it.copy(wallpaper = presetIdx, useCustomWallpaper = false)
                    }
                    config = config.copy(wallpaper = presetIdx, useCustomWallpaper = false)
                    updateSubtitles()
                    onWallpaperChanged?.invoke()
                    Actions.toast(requireContext(), "Wallpaper applied")
                }
            }
        }

        binding.btnPickCustomWallpaper.setOnClickListener {
            runCatching {
                photoPicker.launch(arrayOf("image/*"))
            }.onFailure {
                Actions.toast(requireContext(), "Photo picker not available on this device")
            }
        }

        val scrimButtons = listOf(
            binding.btnScrim0 to 0,
            binding.btnScrim1 to 1,
            binding.btnScrim2 to 2,
            binding.btnScrim3 to 3,
            binding.btnScrim4 to 4
        )

        for ((btn, scrimMode) in scrimButtons) {
            btn.setOnClickListener {
                lifecycleScope.launch {
                    store.update { it.copy(scrimMode = scrimMode) }
                    config = config.copy(scrimMode = scrimMode)
                    updateSubtitles()
                    onWallpaperChanged?.invoke()
                }
            }
        }
    }

    // =========================================================================
    // SUB-PAGE 2: DISPLAY & LAYOUT
    // =========================================================================
    private fun bindDisplaySettings() {
        binding.btnLayoutGrid.setOnClickListener { updateLayout(LAYOUT_GRID) }
        binding.btnLayoutCarousel.setOnClickListener { updateLayout(LAYOUT_CAROUSEL) }
        // Dock is hidden in the layout until it is actually implemented (it used to
        // behave identically to Carousel). Legacy configs with layout == DOCK are
        // rendered as a carousel row.
        binding.btnLayoutDock.visibility = View.GONE

        val scaleButtons = listOf(
            binding.btnScale0 to 0,
            binding.btnScale1 to 1,
            binding.btnScale2 to 2,
            binding.btnScale3 to 3,
            binding.btnScale4 to 4
        )
        for ((btn, scaleIdx) in scaleButtons) {
            btn.setOnClickListener {
                lifecycleScope.launch {
                    store.update { it.copy(iconScale = scaleIdx) }
                    config = config.copy(iconScale = scaleIdx)
                    updateSubtitles()
                }
            }
        }

        val uiScaleButtons = listOf(
            binding.btnUiscale0 to 0,
            binding.btnUiscale1 to 1,
            binding.btnUiscale2 to 2,
            binding.btnUiscale3 to 3,
            binding.btnUiscale4 to 4
        )
        for ((btn, scaleIdx) in uiScaleButtons) {
            btn.setOnClickListener {
                lifecycleScope.launch {
                    store.update { it.copy(uiScale = scaleIdx) }
                    config = config.copy(uiScale = scaleIdx)
                    updateSubtitles()
                }
            }
        }

        binding.switchSystemFont.isChecked = config.respectSystemFontScale
        binding.rowToggleSystemFont.setOnClickListener {
            val newVal = !binding.switchSystemFont.isChecked
            binding.switchSystemFont.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(respectSystemFontScale = newVal) }
                config = config.copy(respectSystemFontScale = newVal)
                DensityAdapter.respectSystemFontScale = newVal
                // Re-apply so the change is visible without restarting the launcher.
                runCatching { DensityAdapter.apply(requireContext()) }
                updateSubtitles()
            }
        }

        val cornerButtons = listOf(
            binding.btnCorner0 to 0,
            binding.btnCorner1 to 1,
            binding.btnCorner2 to 2,
            binding.btnCorner3 to 3,
            binding.btnCorner4 to 4
        )
        for ((btn, cornerIdx) in cornerButtons) {
            btn.setOnClickListener {
                lifecycleScope.launch {
                    store.update { it.copy(cornerRadius = cornerIdx) }
                    config = config.copy(cornerRadius = cornerIdx)
                    updateSubtitles()
                }
            }
        }

        binding.switchAppLabels.isChecked = config.showAppLabels
        binding.rowToggleAppLabels.setOnClickListener {
            val newVal = !binding.switchAppLabels.isChecked
            binding.switchAppLabels.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(showAppLabels = newVal) }
                config = config.copy(showAppLabels = newVal)
            }
        }

        binding.switchCategoryNames.isChecked = config.showCategoryNames
        binding.rowToggleCategoryNames.setOnClickListener {
            val newVal = !binding.switchCategoryNames.isChecked
            binding.switchCategoryNames.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(showCategoryNames = newVal) }
                config = config.copy(showCategoryNames = newVal)
            }
        }
    }

    private fun updateLayout(newLayout: Int) {
        lifecycleScope.launch {
            store.update { it.copy(layout = newLayout) }
            config = config.copy(layout = newLayout)
            updateSubtitles()
        }
    }

    // =========================================================================
    // SUB-PAGE 3: STATUS BAR
    // =========================================================================
    private fun bindStatusBarSettings() {
        binding.switchShowStatusbar.isChecked = config.showStatusBar
        binding.rowToggleShowStatusbar.setOnClickListener {
            val newVal = !binding.switchShowStatusbar.isChecked
            binding.switchShowStatusbar.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(showStatusBar = newVal) }
                config = config.copy(showStatusBar = newVal)
                updateSubtitles()
            }
        }

        binding.switch24hClock.isChecked = config.h24
        binding.rowToggle24hClock.setOnClickListener {
            val newVal = !binding.switch24hClock.isChecked
            binding.switch24hClock.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(h24 = newVal) }
                config = config.copy(h24 = newVal)
                updateSubtitles()
            }
        }

        binding.switchStatusbarGlass.isChecked = config.statusBarGlass
        binding.rowToggleStatusbarGlass.setOnClickListener {
            val newVal = !binding.switchStatusbarGlass.isChecked
            binding.switchStatusbarGlass.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(statusBarGlass = newVal) }
                config = config.copy(statusBarGlass = newVal)
            }
        }

        binding.switchStatusbarMatchCorners.isChecked = config.headerMatchIconCorners
        binding.rowToggleStatusbarMatchCorners.setOnClickListener {
            val newVal = !binding.switchStatusbarMatchCorners.isChecked
            binding.switchStatusbarMatchCorners.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(headerMatchIconCorners = newVal) }
                config = config.copy(headerMatchIconCorners = newVal)
            }
        }

        binding.switchVpnButton.isChecked = config.showVpnButton
        binding.rowToggleVpnButton.setOnClickListener {
            val newVal = !binding.switchVpnButton.isChecked
            binding.switchVpnButton.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(showVpnButton = newVal) }
                config = config.copy(showVpnButton = newVal)
                updateVpnSubtitle()
            }
        }

        updateVpnSubtitle()
        binding.rowPickVpnApp.setOnClickListener {
            openAppPickerForVpn()
        }
    }

    private fun updateVpnSubtitle() {
        val pkg = config.vpnApp
        val label = if (pkg.isEmpty()) {
            "System VPN settings"
        } else {
            apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
        }
        binding.txtVpnAppValue.text = label
    }

    // =========================================================================
    // SUB-PAGE 4: APPS & SECRET VAULT
    // =========================================================================
    private fun bindAppsSettings() {
        // Populate recent apps list
        binding.layoutRecentApps.removeAllViews()
        val recentSample = apps.take(5)
        val inflater = LayoutInflater.from(requireContext())

        for (app in recentSample) {
            val itemBinding = ItemRecentAppBinding.inflate(inflater, binding.layoutRecentApps, false)
            itemBinding.txtAppLabel.text = app.label
            itemBinding.txtAppPackage.text = app.pkg
            if (app.icon != null) {
                itemBinding.imgAppIcon.setImageBitmap(app.icon)
            } else {
                itemBinding.imgAppIcon.setImageDrawable(
                    AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE)
                )
            }

            itemBinding.root.setOnClickListener {
                dismiss()
                Actions.launchApp(requireContext(), app.pkg)
            }

            binding.layoutRecentApps.addView(itemBinding.root)
        }

        binding.btnUnhideAllApps.setOnClickListener {
            lifecycleScope.launch {
                store.update { it.copy(hidden = emptySet()) }
                config = config.copy(hidden = emptySet())
                updateSubtitles()
                Actions.toast(requireContext(), "All apps unhidden")
            }
        }

        binding.switchAutoCategory.isChecked = config.autoCategoryOnInstall
        binding.rowToggleAutoCategory.setOnClickListener {
            val newVal = !binding.switchAutoCategory.isChecked
            binding.switchAutoCategory.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(autoCategoryOnInstall = newVal) }
                config = config.copy(autoCategoryOnInstall = newVal)
            }
        }

        binding.switchShowHidden.isChecked = config.showHidden
        binding.rowToggleShowHidden.setOnClickListener {
            val newVal = !binding.switchShowHidden.isChecked
            binding.switchShowHidden.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(showHidden = newVal) }
                config = config.copy(showHidden = newVal)
                updateSubtitles()
            }
        }

        binding.switchAggressiveTrim.isChecked = config.aggressiveMemoryTrim
        binding.rowToggleAggressiveTrim.setOnClickListener {
            val newVal = !binding.switchAggressiveTrim.isChecked
            binding.switchAggressiveTrim.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(aggressiveMemoryTrim = newVal) }
                config = config.copy(aggressiveMemoryTrim = newVal)
                AppLaunchTracker.aggressiveTrimEnabled = newVal
                updateSubtitles()
            }
        }

        binding.switchLaunchOnBoot.isChecked = config.launchOnBoot
        binding.rowToggleLaunchOnBoot.setOnClickListener {
            val newVal = !binding.switchLaunchOnBoot.isChecked
            binding.switchLaunchOnBoot.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(launchOnBoot = newVal) }
                config = config.copy(launchOnBoot = newVal)
                updateSubtitles()
            }
        }
    }

    // =========================================================================
    // SUB-PAGE 5: SECURITY & LOCKS (App Lock & Hidden Apps Vault)
    // =========================================================================
    private fun bindSecuritySettings() {
        // --- 1. APP LOCK ---
        binding.switchAppLock.isChecked = config.appLock.enabled
        binding.rowToggleAppLock.setOnClickListener {
            if (!config.appLock.enabled) {
                if (!config.appLock.ready) {
                    PinSetupDialogFragment.newInstance(
                        initialType = config.appLock.type,
                        initialPinLength = config.appLock.pinLength
                    ) { newCred ->
                        lifecycleScope.launch {
                            val updatedCred = newCred.copy(enabled = true)
                            store.update { it.copy(appLock = updatedCred) }
                            config = config.copy(appLock = updatedCred)
                            binding.switchAppLock.isChecked = true
                            updateSubtitles()
                            Actions.toast(requireContext(), "App Lock Enabled")
                        }
                    }.show(parentFragmentManager, PinSetupDialogFragment.TAG)
                } else {
                    val updated = config.appLock.copy(enabled = true)
                    binding.switchAppLock.isChecked = true
                    lifecycleScope.launch {
                        store.update { it.copy(appLock = updated) }
                        config = config.copy(appLock = updated)
                        updateSubtitles()
                    }
                }
            } else {
                // Must verify current credential to disable App Lock
                PinEntryDialogFragment.newInstance(
                    title = "Confirm App Lock",
                    subtitle = "Enter current PIN/password to disable App Lock",
                    credential = config.appLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_APP,
                    onSuccess = {
                        val updated = config.appLock.copy(enabled = false)
                        binding.switchAppLock.isChecked = false
                        lifecycleScope.launch {
                            store.update { it.copy(appLock = updated) }
                            config = config.copy(appLock = updated)
                            updateSubtitles()
                            Actions.toast(requireContext(), "App Lock Disabled")
                        }
                    },
                    onCancelled = {
                        binding.switchAppLock.isChecked = true
                    }
                ).show(parentFragmentManager, PinEntryDialogFragment.TAG)
            }
        }

        binding.btnSetupAppLock.setOnClickListener {
            val showSetup = {
                PinSetupDialogFragment.newInstance(
                    initialType = config.appLock.type,
                    initialPinLength = config.appLock.pinLength
                ) { newCred ->
                    lifecycleScope.launch {
                        val updatedCred = newCred.copy(enabled = true)
                        store.update { it.copy(appLock = updatedCred) }
                        config = config.copy(appLock = updatedCred)
                        binding.switchAppLock.isChecked = true
                        updateSubtitles()
                        Actions.toast(requireContext(), "App Lock credential updated")
                    }
                }.show(parentFragmentManager, PinSetupDialogFragment.TAG)
            }

            if (config.appLock.enabled && config.appLock.ready) {
                PinEntryDialogFragment.newInstance(
                    title = "Confirm Current Credential",
                    subtitle = "Enter current PIN/password to change App Lock",
                    credential = config.appLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_APP,
                    onSuccess = { showSetup() }
                ).show(parentFragmentManager, PinEntryDialogFragment.TAG)
            } else {
                showSetup()
            }
        }

        binding.btnManageLockedApps.setOnClickListener {
            if (config.appLock.enabled && config.appLock.ready) {
                PinEntryDialogFragment.newInstance(
                    title = "Manage Locked Apps",
                    subtitle = "Enter credential to manage locked apps",
                    credential = config.appLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_APP,
                    onSuccess = {
                        openManageAppsScreen(isForHidden = false)
                    }
                ).show(parentFragmentManager, PinEntryDialogFragment.TAG)
            } else {
                openManageAppsScreen(isForHidden = false)
            }
        }

        // --- 3. HIDDEN APPS VAULT ---
        binding.switchHiddenLock.isChecked = config.hiddenAppsLock.enabled
        binding.rowToggleHiddenLock.setOnClickListener {
            if (!config.hiddenAppsLock.enabled) {
                if (!config.hiddenAppsLock.ready) {
                    PinSetupDialogFragment.newInstance(
                        initialType = config.hiddenAppsLock.type,
                        initialPinLength = config.hiddenAppsLock.pinLength
                    ) { newCred ->
                        lifecycleScope.launch {
                            val updatedCred = newCred.copy(enabled = true)
                            store.update { it.copy(hiddenAppsLock = updatedCred) }
                            config = config.copy(hiddenAppsLock = updatedCred)
                            binding.switchHiddenLock.isChecked = true
                            updateSubtitles()
                            Actions.toast(requireContext(), "Hidden Apps 2nd Layer Lock Enabled")
                        }
                    }.show(parentFragmentManager, PinSetupDialogFragment.TAG)
                } else {
                    val updated = config.hiddenAppsLock.copy(enabled = true)
                    binding.switchHiddenLock.isChecked = true
                    lifecycleScope.launch {
                        store.update { it.copy(hiddenAppsLock = updated) }
                        config = config.copy(hiddenAppsLock = updated)
                        updateSubtitles()
                    }
                }
            } else {
                // Must verify current credential to disable Hidden Apps Lock
                PinEntryDialogFragment.newInstance(
                    title = "Confirm Hidden Apps Lock",
                    subtitle = "Enter current PIN/password to disable Hidden Apps Lock",
                    credential = config.hiddenAppsLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_VAULT,
                    onSuccess = {
                        val updated = config.hiddenAppsLock.copy(enabled = false)
                        binding.switchHiddenLock.isChecked = false
                        lifecycleScope.launch {
                            store.update { it.copy(hiddenAppsLock = updated) }
                            config = config.copy(hiddenAppsLock = updated)
                            updateSubtitles()
                            Actions.toast(requireContext(), "Hidden Apps Lock Disabled")
                        }
                    },
                    onCancelled = {
                        binding.switchHiddenLock.isChecked = true
                    }
                ).show(parentFragmentManager, PinEntryDialogFragment.TAG)
            }
        }

        updateRevealCodeLabel()
        binding.rowHiddenRevealCode.setOnClickListener {
            if (config.hiddenAppsLock.enabled && config.hiddenAppsLock.ready) {
                PinEntryDialogFragment.newInstance(
                    title = "Change Reveal Code",
                    subtitle = "Enter credential to change reveal code",
                    credential = config.hiddenAppsLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_VAULT,
                    onSuccess = {
                        showEditRevealCodeDialog()
                    }
                ).show(parentFragmentManager, PinEntryDialogFragment.TAG)
            } else {
                showEditRevealCodeDialog()
            }
        }

        binding.btnSetupHiddenLock.setOnClickListener {
            val showSetup = {
                PinSetupDialogFragment.newInstance(
                    initialType = config.hiddenAppsLock.type,
                    initialPinLength = config.hiddenAppsLock.pinLength
                ) { newCred ->
                    lifecycleScope.launch {
                        val updatedCred = newCred.copy(enabled = true)
                        store.update { it.copy(hiddenAppsLock = updatedCred) }
                        config = config.copy(hiddenAppsLock = updatedCred)
                        binding.switchHiddenLock.isChecked = true
                        updateSubtitles()
                        Actions.toast(requireContext(), "Hidden Apps Vault Credential updated")
                    }
                }.show(parentFragmentManager, PinSetupDialogFragment.TAG)
            }

            if (config.hiddenAppsLock.enabled && config.hiddenAppsLock.ready) {
                PinEntryDialogFragment.newInstance(
                    title = "Confirm Current Credential",
                    subtitle = "Enter current PIN/password to change Hidden Apps Lock",
                    credential = config.hiddenAppsLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_VAULT,
                    onSuccess = { showSetup() }
                ).show(parentFragmentManager, PinEntryDialogFragment.TAG)
            } else {
                showSetup()
            }
        }

        binding.btnManageHiddenApps.setOnClickListener {
            if (config.hiddenAppsLock.enabled && config.hiddenAppsLock.ready) {
                PinEntryDialogFragment.newInstance(
                    title = "Manage Hidden Apps",
                    subtitle = "Enter credential to manage hidden apps",
                    credential = config.hiddenAppsLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_VAULT,
                    onSuccess = {
                        openManageAppsScreen(isForHidden = true)
                    }
                ).show(parentFragmentManager, PinEntryDialogFragment.TAG)
            } else {
                openManageAppsScreen(isForHidden = true)
            }
        }
    }

    private fun updateRevealCodeLabel() {
        // Never render the code itself - a shared TV would expose the whole vault.
        binding.txtHiddenRevealCodeValue.text = if (config.hiddenAppsRevealCode.isNotEmpty()) {
            "Active: •••••• (type the code in search\u2026 click to change)"
        } else {
            "Not set — click to set reveal code"
        }
    }

    private fun showEditRevealCodeDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_text, null)
        val dialogBinding = DialogEditTextBinding.bind(dialogView)
        dialogBinding.etRevealCode.setText(config.hiddenAppsRevealCode)
        dialogBinding.etRevealCode.setSelection(dialogBinding.etRevealCode.text.length)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_LiteTV_Dialog)
            .setView(dialogView)
            .create()

        dialogBinding.btnCancelRevealCode.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveRevealCode.setOnClickListener {
            val code = dialogBinding.etRevealCode.text.toString().trim()
            if (_binding == null || !isAdded) return@setOnClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(hiddenAppsRevealCode = code) }
                config = config.copy(hiddenAppsRevealCode = code)
                updateRevealCodeLabel()
                Actions.toast(requireContext(), if (code.isNotEmpty()) "Reveal code saved" else "Reveal code cleared")
                dialog.dismiss()
            }
        }

        // TV: make sure the dialog window takes D-pad focus and the text field is focused,
        // otherwise the dialog can appear without any focusable target on some firmware.
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.setLayout(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            dialogBinding.etRevealCode.requestFocus()
        }
        dialog.show()
    }

    private fun openManageAppsScreen(isForHidden: Boolean) {
        val title = if (isForHidden) "Manage Hidden Apps" else "Manage Locked Apps"
        val subtitle = if (isForHidden) {
            "Selected apps will be hidden from launcher grid and only shown via reveal code in search"
        } else {
            "Selected apps will require App Lock credential to open"
        }

        binding.txtToggleAppsHeader.text = title
        binding.txtToggleAppsSub.text = subtitle

        val container = binding.layoutToggleAppsList
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        for (app in apps) {
            val itemBinding = ItemToggleAppBinding.inflate(inflater, container, false)
            itemBinding.txtAppLabel.text = app.label
            itemBinding.txtAppPackage.text = app.pkg

            if (app.icon != null) {
                itemBinding.imgAppIcon.setImageBitmap(app.icon)
            } else {
                itemBinding.imgAppIcon.setImageDrawable(
                    AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE)
                )
            }

            val isSelected = if (isForHidden) app.pkg in config.hidden else app.pkg in config.lockedApps
            itemBinding.switchAppSelected.isChecked = isSelected

            itemBinding.root.setOnClickListener {
                val currentlyChecked = itemBinding.switchAppSelected.isChecked
                val nextChecked = !currentlyChecked
                itemBinding.switchAppSelected.isChecked = nextChecked

                lifecycleScope.launch {
                    if (isForHidden) {
                        val newSet = if (nextChecked) config.hidden + app.pkg else config.hidden - app.pkg
                        store.update { it.copy(hidden = newSet) }
                        config = config.copy(hidden = newSet)
                    } else {
                        val newSet = if (nextChecked) config.lockedApps + app.pkg else config.lockedApps - app.pkg
                        store.update { it.copy(lockedApps = newSet) }
                        config = config.copy(lockedApps = newSet)
                    }
                    updateSubtitles()
                }
            }

            container.addView(itemBinding.root)
        }

        navigateToSubPage(binding.subpageToggleApps, title)
    }

    // =========================================================================
    // SUB-PAGE 7: ABOUT
    // =========================================================================
    private fun bindAboutSettings() {
        binding.btnSetDefaultHome.setOnClickListener {
            safeStartActivity(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_SETTINGS)
        }

        binding.btnRestartLauncher.setOnClickListener {
            dismiss()
            activity?.recreate()
        }
    }

    // =========================================================================
    // SUB-PAGE 8: REMOTE BUTTON MAPPING (Phase 5)
    // =========================================================================
    private var capturedKeyCodeForMapping: Int? = null

    /** What the shared app-picker sub-page is currently selecting an app for. */
    private enum class PickAppMode { BUTTON_MAP, VPN_SHORTCUT }

    private var pickAppMode: PickAppMode = PickAppMode.BUTTON_MAP

    private fun bindButtonMappingSettings() {
        renderButtonMappingsList()

        binding.btnAddButtonMapping.setOnClickListener {
            startKeyListening()
        }

        binding.btnCancelListening.setOnClickListener {
            cancelKeyListening()
        }

        // Listen for raw key events captured by LauncherAccessibilityService
        viewLifecycleOwner.lifecycleScope.launch {
            ButtonMappingManager.keyCaptureFlow.collectLatest { keyCode ->
                handleKeyCaptured(keyCode)
            }
        }
    }

    private fun startKeyListening() {
        ButtonMappingManager.startListening()
        binding.cardListeningForButton.visibility = View.VISIBLE
        binding.txtListeningStatus.text = getString(R.string.button_mapping_listening)
        binding.btnAddButtonMapping.visibility = View.GONE
        binding.btnCancelListening.requestFocus()
    }

    private fun cancelKeyListening() {
        ButtonMappingManager.stopListening()
        binding.cardListeningForButton.visibility = View.GONE
        binding.btnAddButtonMapping.visibility = View.VISIBLE
        binding.btnAddButtonMapping.requestFocus()
    }

    private fun handleKeyCaptured(keyCode: Int) {
        if (ButtonMappingManager.isReservedKey(keyCode)) {
            val keyName = ButtonMappingManager.getKeyName(keyCode)
            Actions.toast(requireContext(), getString(R.string.button_mapping_reserved_error, keyName))
            cancelKeyListening()
            return
        }

        ButtonMappingManager.stopListening()
        binding.cardListeningForButton.visibility = View.GONE
        binding.btnAddButtonMapping.visibility = View.VISIBLE

        capturedKeyCodeForMapping = keyCode
        openAppPickerForMapping(keyCode)
    }

    private fun openAppPickerForMapping(keyCode: Int) {
        pickAppMode = PickAppMode.BUTTON_MAP
        val buttonName = ButtonMappingManager.getKeyName(keyCode)
        binding.txtPickAppHeader.text = "Detected Key: $buttonName (Keycode $keyCode)"
        populateAppPickerList(keyCode)
        navigateToSubPage(binding.subpagePickApp, "Assign App to Button")
    }

    /** Picks the app the status-bar VPN shortcut should open. */
    private fun openAppPickerForVpn() {
        pickAppMode = PickAppMode.VPN_SHORTCUT
        binding.txtPickAppHeader.text = "Choose the app the VPN shortcut opens"
        populateAppPickerList(targetKeyCode = -1)
        navigateToSubPage(binding.subpagePickApp, "VPN Shortcut App")
    }

    private fun populateAppPickerList(targetKeyCode: Int) {
        val container = binding.layoutPickAppList
        container.removeAllViews()

        val sortedApps = apps.sortedBy { it.label.lowercase() }
        val inflater = LayoutInflater.from(requireContext())

        // VPN mode: offer "no app" (system VPN settings) as the first choice.
        if (pickAppMode == PickAppMode.VPN_SHORTCUT) {
            val systemDefault = ItemPickAppBinding.inflate(inflater, container, false)
            systemDefault.txtAppLabel.text = "System VPN settings"
            systemDefault.txtAppPackage.text = "Default — open Android VPN settings"
            systemDefault.imgAppIcon.setImageDrawable(
                AppIcons.createDrawable(AppIcons.PATH_GEAR, Color.WHITE)
            )
            systemDefault.root.setOnClickListener {
                lifecycleScope.launch {
                    store.update { it.copy(vpnApp = "") }
                    config = config.copy(vpnApp = "")
                    updateVpnSubtitle()
                    navigateToSubPage(binding.pageStatusbar, "Status Bar & Clock")
                }
            }
            container.addView(systemDefault.root)
        }

        for (app in sortedApps) {
            val itemBinding = ItemPickAppBinding.inflate(inflater, container, false)
            itemBinding.txtAppLabel.text = app.label
            itemBinding.txtAppPackage.text = app.pkg
            if (app.icon != null) {
                itemBinding.imgAppIcon.setImageBitmap(app.icon)
            } else {
                itemBinding.imgAppIcon.setImageDrawable(
                    AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE)
                )
            }

            itemBinding.root.setOnClickListener {
                if (pickAppMode == PickAppMode.VPN_SHORTCUT) {
                    lifecycleScope.launch {
                        store.update { it.copy(vpnApp = app.pkg) }
                        config = config.copy(vpnApp = app.pkg)
                        updateVpnSubtitle()
                        Actions.toast(requireContext(), "VPN shortcut set to ${app.label}")
                        navigateToSubPage(binding.pageStatusbar, "Status Bar & Clock")
                    }
                } else {
                    assignMapping(targetKeyCode, app.pkg, app.label)
                }
            }

            container.addView(itemBinding.root)
        }
    }

    private fun assignMapping(keyCode: Int, packageName: String, appLabel: String) {
        lifecycleScope.launch {
            val updatedMap = config.buttonMap.toMutableMap()
            updatedMap[keyCode] = packageName

            store.update { it.copy(buttonMap = updatedMap) }
            config = config.copy(buttonMap = updatedMap)

            updateSubtitles()
            renderButtonMappingsList()

            Actions.toast(
                requireContext(),
                getString(
                    R.string.button_mapping_saved,
                    ButtonMappingManager.getKeyName(keyCode),
                    appLabel
                )
            )

            navigateToSubPage(binding.subpageButtonMapping, "Remote Button Mapping")
        }
    }

    private fun removeMapping(keyCode: Int) {
        lifecycleScope.launch {
            val updatedMap = config.buttonMap.toMutableMap()
            updatedMap.remove(keyCode)

            store.update { it.copy(buttonMap = updatedMap) }
            config = config.copy(buttonMap = updatedMap)

            updateSubtitles()
            renderButtonMappingsList()

            Actions.toast(requireContext(), getString(R.string.button_mapping_deleted))
        }
    }

    private fun renderButtonMappingsList() {
        val container = binding.layoutButtonMappingsList
        container.removeAllViews()

        val mappings = config.buttonMap
        if (mappings.isEmpty()) {
            binding.txtButtonMappingsEmpty.visibility = View.VISIBLE
            return
        }

        binding.txtButtonMappingsEmpty.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())

        for ((keyCode, pkg) in mappings) {
            val itemBinding = ItemButtonMappingBinding.inflate(inflater, container, false)
            val buttonName = ButtonMappingManager.getKeyName(keyCode)
            itemBinding.txtButtonName.text = buttonName

            val matchingApp = apps.find { it.pkg == pkg }
            if (matchingApp != null) {
                itemBinding.txtMappedAppName.text = "${matchingApp.label} • Key $keyCode"
                if (matchingApp.icon != null) {
                    itemBinding.imgMappedAppIcon.setImageBitmap(matchingApp.icon)
                } else {
                    itemBinding.imgMappedAppIcon.setImageDrawable(
                        AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE)
                    )
                }
            } else {
                itemBinding.txtMappedAppName.text = "$pkg • Key $keyCode"
                itemBinding.imgMappedAppIcon.setImageDrawable(
                    AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE)
                )
            }

            itemBinding.btnDeleteMapping.setOnClickListener {
                removeMapping(keyCode)
            }

            itemBinding.root.setOnClickListener {
                // Clicking on mapped row allows re-mapping to another app
                openAppPickerForMapping(keyCode)
            }

            container.addView(itemBinding.root)
        }
    }

    private fun safeStartActivity(action: String, fallbackAction: String? = null) {
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            if (fallbackAction != null) {
                try {
                    startActivity(Intent(fallbackAction))
                } catch (_: Exception) {
                    Actions.toast(requireContext(), "Cannot open system settings on this device")
                }
            } else {
                Actions.toast(requireContext(), "Cannot open system settings on this device")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingsBottomSheet"

        fun newInstance(
            config: LauncherConfig,
            apps: List<AppEntry>,
            onWallpaperChanged: () -> Unit,
            onRerunWizard: () -> Unit
        ): SettingsBottomSheetFragment {
            return SettingsBottomSheetFragment().apply {
                this.config = config
                this.apps = apps
                this.onWallpaperChanged = onWallpaperChanged
                this.onRerunWizard = onRerunWizard
            }
        }
    }
}
