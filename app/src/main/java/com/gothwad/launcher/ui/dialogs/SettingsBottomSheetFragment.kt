package com.gothwad.launcher.ui.dialogs

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
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
import com.gothwad.launcher.databinding.SheetSettingsBinding
import com.gothwad.launcher.ui.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsBottomSheetFragment : DialogFragment() {

    private var _binding: SheetSettingsBinding? = null
    private val binding get() = _binding!!

    var config: LauncherConfig = LauncherConfig()
    var apps: List<AppEntry> = emptyList()
    var onWallpaperChanged: (() -> Unit)? = null
    var onRerunWizard: (() -> Unit)? = null
    var onModeSelected: ((Int) -> Unit)? = null

    private lateinit var store: ConfigStore

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
                onWallpaperChanged?.invoke()
                Actions.toast(context, "Custom wallpaper set")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
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

        binding.imgSettingsHeaderIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, Color.WHITE))
        binding.btnCloseSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))

        binding.btnCloseSettings.setOnClickListener {
            dismiss()
        }

        setupNavigation()
        bindWallpaperSettings()
        bindDisplaySettings()
        bindStatusBarSettings()
        bindAppsSettings()
        bindSecuritySettings()
        bindExperienceSettings()
        bindPermissionsSettings()
        bindAboutSettings()

        selectCategory(0)
    }

    private fun setupNavigation() {
        val navButtons = listOf(
            binding.navWallpaper,
            binding.navDisplay,
            binding.navStatusbar,
            binding.navApps,
            binding.navSecurity,
            binding.navExperience,
            binding.navPermissions,
            binding.navAbout
        )

        navButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                selectCategory(index)
            }
        }
    }

    private fun selectCategory(index: Int) {
        val pages = listOf(
            binding.pageWallpaper,
            binding.pageDisplay,
            binding.pageStatusbar,
            binding.pageApps,
            binding.pageSecurity,
            binding.pageExperience,
            binding.pagePermissions,
            binding.pageAbout
        )

        val navButtons = listOf(
            binding.navWallpaper,
            binding.navDisplay,
            binding.navStatusbar,
            binding.navApps,
            binding.navSecurity,
            binding.navExperience,
            binding.navPermissions,
            binding.navAbout
        )

        pages.forEachIndexed { i, page ->
            page.visibility = if (i == index) View.VISIBLE else View.GONE
        }

        navButtons.forEachIndexed { i, btn ->
            if (i == index) {
                btn.setTextColor(0xFF4C8DFF.toInt())
            } else {
                btn.setTextColor(Color.WHITE)
            }
        }
    }

    private fun bindWallpaperSettings() {
        val presetButtons = listOf(
            binding.btnWpPreset0 to 0,
            binding.btnWpPreset1 to 1,
            binding.btnWpPreset2 to 2,
            binding.btnWpPreset3 to 3,
            binding.btnWpPreset4 to 4
        )

        for ((btn, presetIdx) in presetButtons) {
            btn.setOnClickListener {
                lifecycleScope.launch {
                    store.update {
                        it.copy(wallpaper = presetIdx, useCustomWallpaper = false)
                    }
                    onWallpaperChanged?.invoke()
                    Actions.toast(requireContext(), "Wallpaper updated")
                }
            }
        }

        binding.btnPickPhoto.setOnClickListener {
            runCatching {
                photoPicker.launch(arrayOf("image/*"))
            }.onFailure {
                Actions.toast(requireContext(), getString(R.string.toast_no_picker))
            }
        }

        binding.btnDimOff.setOnClickListener {
            lifecycleScope.launch { store.update { it.copy(scrimMode = 4) } }
        }
        binding.btnDimTopBottom.setOnClickListener {
            lifecycleScope.launch { store.update { it.copy(scrimMode = 0) } }
        }
        binding.btnDimFull.setOnClickListener {
            lifecycleScope.launch { store.update { it.copy(scrimMode = 3) } }
        }
    }

    private fun bindDisplaySettings() {
        binding.btnLayoutCarousel.setOnClickListener {
            lifecycleScope.launch { store.update { it.copy(layout = LAYOUT_CAROUSEL) } }
        }
        binding.btnLayoutGrid.setOnClickListener {
            lifecycleScope.launch { store.update { it.copy(layout = LAYOUT_GRID) } }
        }
        binding.btnLayoutDock.setOnClickListener {
            lifecycleScope.launch { store.update { it.copy(layout = LAYOUT_DOCK) } }
        }

        binding.btnSizeS.setOnClickListener {
            lifecycleScope.launch { store.update { it.copy(iconScale = 0) } }
        }
        binding.btnSizeNormal.setOnClickListener {
            lifecycleScope.launch { store.update { it.copy(iconScale = 1) } }
        }
        binding.btnSizeL.setOnClickListener {
            lifecycleScope.launch { store.update { it.copy(iconScale = 2) } }
        }

        binding.btnRoundS.setOnClickListener {
            lifecycleScope.launch { store.update { it.copy(cornerRadius = 1) } }
        }
        binding.btnRoundM.setOnClickListener {
            lifecycleScope.launch { store.update { it.copy(cornerRadius = 2) } }
        }
        binding.btnRoundFull.setOnClickListener {
            lifecycleScope.launch { store.update { it.copy(cornerRadius = 4) } }
        }
    }

    private fun bindStatusBarSettings() {
        binding.chkShowStatusbar.isChecked = config.showStatusBar
        binding.chkClock24h.isChecked = config.h24
        binding.chkShowVpn.isChecked = config.showVpnButton
        binding.chkShowWeather.visibility = View.GONE
        binding.chkShowQuickDashboard.visibility = View.GONE
        binding.chkShowAdShield.visibility = View.GONE

        binding.chkShowStatusbar.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { store.update { it.copy(showStatusBar = checked) } }
        }
        binding.chkClock24h.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { store.update { it.copy(h24 = checked) } }
        }
        binding.chkShowVpn.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { store.update { it.copy(showVpnButton = checked) } }
        }
    }

    private fun bindAppsSettings() {
        binding.chkShowAppLabels.isChecked = config.showAppLabels
        binding.chkAutoLaunchSearch.visibility = View.GONE

        binding.chkShowAppLabels.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { store.update { it.copy(showAppLabels = checked) } }
        }

        binding.tvHiddenCount.text = "${config.hidden.size} hidden apps in secret vault"
        binding.btnUnhideAll.setOnClickListener {
            lifecycleScope.launch {
                store.update { it.copy(hidden = emptySet()) }
                binding.tvHiddenCount.text = "0 hidden apps in secret vault"
                Actions.toast(requireContext(), "All apps unhidden")
            }
        }
    }

    private fun bindSecuritySettings() {
        binding.chkDeviceLock.isChecked = config.deviceLockEnabled
        binding.chkAppLock.isChecked = config.appLockEnabled

        binding.chkDeviceLock.setOnCheckedChangeListener { _, checked ->
            if (checked && config.deviceLockPin.isEmpty()) {
                showPinSetupDialog { pin ->
                    lifecycleScope.launch {
                        store.update { it.copy(deviceLockEnabled = true, deviceLockPin = pin) }
                    }
                }
            } else {
                lifecycleScope.launch { store.update { it.copy(deviceLockEnabled = checked) } }
            }
        }

        binding.btnSetupDevicePin.setOnClickListener {
            showPinSetupDialog { pin ->
                lifecycleScope.launch {
                    store.update { it.copy(deviceLockPin = pin, deviceLockEnabled = true) }
                    Actions.toast(requireContext(), "Device PIN updated")
                }
            }
        }

        binding.chkAppLock.setOnCheckedChangeListener { _, checked ->
            if (checked && config.appLockPin.isEmpty()) {
                showPinSetupDialog { pin ->
                    lifecycleScope.launch {
                        store.update { it.copy(appLockEnabled = true, appLockPin = pin) }
                    }
                }
            } else {
                lifecycleScope.launch { store.update { it.copy(appLockEnabled = checked) } }
            }
        }

        binding.btnSetupAppPin.setOnClickListener {
            showPinSetupDialog { pin ->
                lifecycleScope.launch {
                    store.update { it.copy(appLockPin = pin, appLockEnabled = true) }
                    Actions.toast(requireContext(), "App Lock PIN updated")
                }
            }
        }

        val secret = if (config.hideAppsCode.isNotEmpty()) config.hideAppsCode else if (config.hideAppsPin.isNotEmpty()) config.hideAppsPin else "none"
        binding.tvSecretCodeStatus.text = "Secret keyword: $secret"
    }

    private fun showPinSetupDialog(onSaved: (String) -> Unit) {
        PinSetupDialogFragment.newInstance(initialLength = 4, onSaved = onSaved)
            .show(parentFragmentManager, PinSetupDialogFragment.TAG)
    }

    private fun bindExperienceSettings() {
        binding.btnSwitchMode.setOnClickListener {
            dismiss()
            ModeSelectionDialogFragment.newInstance(
                currentMode = config.launcherMode,
                onSelect = { mode ->
                    lifecycleScope.launch {
                        store.update { it.copy(launcherMode = mode) }
                        onModeSelected?.invoke(mode)
                    }
                }
            ).show(parentFragmentManager, ModeSelectionDialogFragment.TAG)
        }

        binding.btnRerunWizard.setOnClickListener {
            dismiss()
            onRerunWizard?.invoke()
        }
    }

    private fun bindPermissionsSettings() {
        binding.btnPermHome.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }.onFailure {
                Actions.openSystemSettings(requireContext())
            }
        }

        binding.btnPermAccessibility.setOnClickListener {
            Actions.openAccessibilitySettings(requireContext())
        }

        binding.btnPermNotifications.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }.onFailure {
                Actions.openSystemSettings(requireContext())
            }
        }

        binding.btnPermAndroidSettings.setOnClickListener {
            Actions.openSystemSettings(requireContext())
        }
    }

    private fun bindAboutSettings() {
        binding.btnResetDefaults.setOnClickListener {
            lifecycleScope.launch {
                store.update { LauncherConfig() }
                Actions.toast(requireContext(), "Settings reset to defaults")
                dismiss()
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
