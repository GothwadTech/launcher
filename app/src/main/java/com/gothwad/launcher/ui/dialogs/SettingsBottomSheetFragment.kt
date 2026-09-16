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
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LAYOUT_CAROUSEL
import com.gothwad.launcher.data.LAYOUT_DOCK
import com.gothwad.launcher.data.LAYOUT_GRID
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.MODE_PC
import com.gothwad.launcher.data.MODE_TV
import com.gothwad.launcher.databinding.ItemRecentAppBinding
import com.gothwad.launcher.databinding.SheetSettingsBinding
import com.gothwad.launcher.ui.AppIcons
import kotlinx.coroutines.Dispatchers
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
    var onModeSelected: ((Int) -> Unit)? = null

    private lateinit var store: ConfigStore
    private var currentSubPage: View? = null

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val context = requireContext()
            lifecycleScope.launch {
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
        bindDevicePrefsSettings()
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
            navigateToRoot()
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

        binding.iconMode.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_TV, iconColor))

        binding.iconNetwork.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI, iconColor))
        binding.chevronNetwork.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconDevicePrefs.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, iconColor))
        binding.chevronDevicePrefs.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconPermissions.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SHIELD, iconColor))
        binding.chevronPermissions.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconWizard.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIZARD, iconColor))

        binding.iconAbout.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_INFO, iconColor))
        binding.chevronAbout.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        // Device Prefs Icons
        binding.iconSysTime.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_TIME, iconColor))
        binding.iconSysSound.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_VOLUME, iconColor))
        binding.iconSysStorage.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_STORAGE, iconColor))
        binding.iconSysAccessibility.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_ACCESSIBILITY, iconColor))
        binding.iconSysNotifications.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, iconColor))
        binding.iconSysFullSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, iconColor))
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
        binding.txtDisplaySubtitle.text = "$layoutName layout • $scaleName"

        // Status bar subtitle
        val sbVisible = if (config.showStatusBar) "Visible" else "Hidden"
        val clockFormat = if (config.h24) "24h format" else "12h format"
        binding.txtStatusbarSubtitle.text = "$sbVisible • $clockFormat"

        // Apps subtitle
        val hiddenCount = config.hidden.size
        binding.txtAppsSubtitle.text = if (hiddenCount > 0) "$hiddenCount apps protected" else "Recent apps • Auto-sort"
        binding.txtHiddenAppsCount.text = "$hiddenCount apps configured in secret vault"

        // Security subtitle
        val secStatus = when {
            config.deviceLockEnabled && config.appLockEnabled -> "Device & App PIN Active"
            config.deviceLockEnabled -> "Device Lock Active"
            config.appLockEnabled -> "App Lock Active"
            else -> "No PIN set"
        }
        binding.txtSecuritySubtitle.text = secStatus

        // Mode subtitle
        if (config.launcherMode == MODE_PC) {
            binding.txtModeSubtitle.text = "PC Desktop Mode"
            binding.badgeMode.text = "PC"
        } else {
            binding.txtModeSubtitle.text = "Android TV Mode"
            binding.badgeMode.text = "TV"
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
            navigateToSubPage(binding.pageSecurity, "Security & PIN Lock")
        }

        binding.rowMode.setOnClickListener {
            val newMode = if (config.launcherMode == MODE_TV) MODE_PC else MODE_TV
            lifecycleScope.launch {
                store.update { it.copy(launcherMode = newMode) }
                config = config.copy(launcherMode = newMode)
                updateSubtitles()
                onModeSelected?.invoke(newMode)
                Actions.toast(
                    requireContext(),
                    if (newMode == MODE_PC) "Switched to PC Desktop Mode" else "Switched to Android TV Mode"
                )
            }
        }

        binding.rowNetwork.setOnClickListener {
            safeStartActivity(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
        }

        binding.rowDevicePrefs.setOnClickListener {
            navigateToSubPage(binding.pageDevicePrefs, "Device Preferences")
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
        binding.pageDevicePrefs.visibility = View.GONE
        binding.pageAbout.visibility = View.GONE
    }

    private fun setupBackKeyHandling() {
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
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
        binding.btnLayoutDock.setOnClickListener { updateLayout(LAYOUT_DOCK) }

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
    }

    // =========================================================================
    // SUB-PAGE 5: SECURITY & PIN
    // =========================================================================
    private fun bindSecuritySettings() {
        binding.switchDeviceLock.isChecked = config.deviceLockEnabled
        binding.rowToggleDeviceLock.setOnClickListener {
            val newVal = !binding.switchDeviceLock.isChecked
            binding.switchDeviceLock.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(deviceLockEnabled = newVal) }
                config = config.copy(deviceLockEnabled = newVal)
                updateSubtitles()
            }
        }

        binding.switchAppLock.isChecked = config.appLockEnabled
        binding.rowToggleAppLock.setOnClickListener {
            val newVal = !binding.switchAppLock.isChecked
            binding.switchAppLock.isChecked = newVal
            lifecycleScope.launch {
                store.update { it.copy(appLockEnabled = newVal) }
                config = config.copy(appLockEnabled = newVal)
                updateSubtitles()
            }
        }

        binding.btnSetupPin.setOnClickListener {
            PinSetupDialogFragment.newInstance { pin ->
                lifecycleScope.launch {
                    store.update { it.copy(appLockPin = pin, deviceLockPin = pin) }
                    config = config.copy(appLockPin = pin, deviceLockPin = pin)
                    updateSubtitles()
                    Actions.toast(requireContext(), "Security PIN updated")
                }
            }.show(parentFragmentManager, "PinSetupDialog")
        }
    }

    // =========================================================================
    // SUB-PAGE 6: DEVICE PREFERENCES
    // =========================================================================
    private fun bindDevicePrefsSettings() {
        binding.rowSysDateTime.setOnClickListener {
            safeStartActivity(Settings.ACTION_DATE_SETTINGS, Settings.ACTION_SETTINGS)
        }

        binding.rowSysSound.setOnClickListener {
            safeStartActivity(Settings.ACTION_SOUND_SETTINGS, Settings.ACTION_SETTINGS)
        }

        binding.rowSysStorage.setOnClickListener {
            safeStartActivity(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, Settings.ACTION_SETTINGS)
        }

        binding.rowSysAccessibility.setOnClickListener {
            safeStartActivity(Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS)
        }

        binding.rowSysNotifications.setOnClickListener {
            safeStartActivity(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, Settings.ACTION_SETTINGS)
        }

        binding.rowSysFullSettings.setOnClickListener {
            safeStartActivity(Settings.ACTION_SETTINGS)
        }
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
            onRerunWizard: () -> Unit,
            onModeSelected: (Int) -> Unit
        ): SettingsBottomSheetFragment {
            return SettingsBottomSheetFragment().apply {
                this.config = config
                this.apps = apps
                this.onWallpaperChanged = onWallpaperChanged
                this.onRerunWizard = onRerunWizard
                this.onModeSelected = onModeSelected
            }
        }
    }
}
