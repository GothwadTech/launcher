package com.gothwad.launcher.ui.dialogs

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import com.gothwad.launcher.data.LauncherConfig
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

    private lateinit var store: ConfigStore
    private var currentSubPage: View? = null

    private lateinit var wallpaperDelegate: SettingsWallpaperDelegate
    private lateinit var displayDelegate: SettingsDisplayDelegate
    private lateinit var statusBarDelegate: SettingsStatusBarDelegate
    private lateinit var appsDelegate: SettingsAppsDelegate
    private lateinit var securityDelegate: SettingsSecurityDelegate
    private lateinit var buttonMappingDelegate: SettingsButtonMappingDelegate

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
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
                store.update { it.copy(useCustomWallpaper = true) }
                config = config.copy(useCustomWallpaper = true)
                updateSubtitles()
                onWallpaperChanged?.invoke()
                Actions.toast(context, "Custom wallpaper applied")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_LiteTV_FullScreenDialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#16171A")))
            window.setGravity(Gravity.CENTER)
            window.setWindowAnimations(android.R.style.Animation_Activity)
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

        if (onWallpaperChanged == null && onRerunWizard == null) {
            dismiss()
            return
        }

        store = ConfigStore(requireContext())

        setupBackdropAndHeader()
        setupMenuIcons()
        initDelegates()
        updateSubtitles()
        setupRootMenuClicks()
        setupFocusDescriptions()
        bindAboutSettings()
        setupBackKeyHandling()
    }

    private fun initDelegates() {
        wallpaperDelegate = SettingsWallpaperDelegate(
            fragment = this,
            binding = binding,
            store = store,
            getConfig = { config },
            updateConfig = { config = it },
            onWallpaperChanged = onWallpaperChanged,
            openPhotoPicker = { photoPicker.launch(arrayOf("image/*")) },
            updateSubtitles = { updateSubtitles() }
        ).also { it.bind() }

        displayDelegate = SettingsDisplayDelegate(
            fragment = this,
            binding = binding,
            store = store,
            getConfig = { config },
            updateConfig = { config = it },
            updateSubtitles = { updateSubtitles() }
        ).also { it.bind() }

        statusBarDelegate = SettingsStatusBarDelegate(
            fragment = this,
            binding = binding,
            store = store,
            getConfig = { config },
            updateConfig = { config = it },
            getApps = { apps },
            updateSubtitles = { updateSubtitles() }
        ).also { it.bind() }

        appsDelegate = SettingsAppsDelegate(
            fragment = this,
            binding = binding,
            store = store,
            getConfig = { config },
            updateConfig = { config = it },
            getApps = { apps },
            updateSubtitles = { updateSubtitles() },
            navigateToSubPage = { page, title -> navigateToSubPage(page, title) }
        ).also { it.bind() }

        securityDelegate = SettingsSecurityDelegate(
            fragment = this,
            binding = binding,
            store = store,
            getConfig = { config },
            updateConfig = { config = it },
            updateSubtitles = { updateSubtitles() },
            openManageAppsScreen = { isForHidden -> appsDelegate.openManageAppsScreen(isForHidden) }
        ).also { it.bind() }

        buttonMappingDelegate = SettingsButtonMappingDelegate(
            fragment = this,
            binding = binding,
            store = store,
            getConfig = { config },
            updateConfig = { config = it },
            getApps = { apps },
            updateSubtitles = { updateSubtitles() },
            navigateToSubPage = { page, title -> navigateToSubPage(page, title) }
        ).also { it.bind() }
    }

    private fun setupBackdropAndHeader() {
        binding.scrimBackdrop.setOnClickListener { dismiss() }

        binding.btnCloseSettings.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE)
        )
        binding.btnCloseSettings.setOnClickListener { dismiss() }

        binding.btnBack.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_BACK, Color.WHITE)
        )
        binding.btnBack.setOnClickListener {
            if (currentSubPage == binding.subpagePickApp) {
                navigateToSubPage(binding.subpageButtonMapping, "Remote Button Mapping")
            } else if (currentSubPage == binding.subpageToggleApps) {
                navigateToSubPage(binding.pageSecurity, "Security & Locks")
            } else {
                navigateToRoot()
            }
        }
    }

    private fun setupMenuIcons() {
        SettingsMenuHelper.setupMenuIcons(binding)
    }

    fun updateSubtitles() {
        if (!isAdded || _binding == null) return
        SettingsMenuHelper.updateSubtitles(binding, config, requireContext())
    }

    private fun setupRootMenuClicks() {
        binding.rowWallpaper.setOnClickListener { navigateToSubPage(binding.pageWallpaper, "Wallpaper & Theme") }
        binding.rowDisplay.setOnClickListener { navigateToSubPage(binding.pageDisplay, "Display & Layout") }
        binding.rowStatusbar.setOnClickListener { navigateToSubPage(binding.pageStatusbar, "Status Bar & Clock") }
        binding.rowApps.setOnClickListener { navigateToSubPage(binding.pageApps, "Apps & Secret Vault") }
        binding.rowSecurity.setOnClickListener { navigateToSubPage(binding.pageSecurity, "Security & Locks") }
        binding.rowButtonMapping.setOnClickListener { navigateToSubPage(binding.subpageButtonMapping, "Remote Button Mapping") }
        binding.rowNetwork.setOnClickListener { Actions.openNetworkSettings(requireContext()) }
        binding.rowDevicePrefs.setOnClickListener { safeStartActivity(Settings.ACTION_SETTINGS) }
        binding.rowPermissions.setOnClickListener { Actions.openAppInfo(requireContext(), requireContext().packageName) }
        binding.rowWizard.setOnClickListener {
            dismiss()
            onRerunWizard?.invoke()
        }
        binding.rowAbout.setOnClickListener { navigateToSubPage(binding.pageAbout, "About & System") }
    }

    private fun setupFocusDescriptions() {
        binding.rowWallpaper.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Choose dynamic gradient themes, solid colors, or set a personal custom wallpaper."
        }
        binding.rowDisplay.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Adjust corner rounding and UI layout scale for your TV screen."
        }
        binding.rowStatusbar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Configure digital clock format, glass styling, and status bar appearance."
        }
        binding.rowApps.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Manage installed apps, hide sensitive apps into a PIN-protected vault, and set sorting."
        }
        binding.rowSecurity.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Protect apps and vault settings with secure PIN or pattern locks."
        }
        binding.rowButtonMapping.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Map dedicated TV remote hotkeys to directly launch your favorite apps."
        }
        binding.rowNetwork.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Configure Wi-Fi, Ethernet, and network proxy connections."
        }
        binding.rowDevicePrefs.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Open full device Android system preferences, display, audio, and accounts."
        }
        binding.rowPermissions.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Review and manage system permissions, overlay rights, and storage access."
        }
        binding.rowWizard.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Rerun the first-time setup guide to reconfigure your home screen."
        }
        binding.rowAbout.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "View version, device information, restart launcher, or set as default home."
        }
    }

    private fun bindAboutSettings() {
        binding.btnSetDefaultHome.setOnClickListener {
            safeStartActivity(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_SETTINGS)
        }
        binding.btnRestartLauncher.setOnClickListener {
            dismiss()
            activity?.recreate()
        }
    }

    private fun setupBackKeyHandling() {
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                handleBackPress()
            } else {
                false
            }
        }
    }

    private fun handleBackPress(): Boolean {
        return when {
            currentSubPage == binding.subpagePickApp -> {
                navigateToSubPage(binding.subpageButtonMapping, "Remote Button Mapping")
                true
            }
            currentSubPage == binding.subpageToggleApps -> {
                navigateToSubPage(binding.pageSecurity, "Security & Locks")
                true
            }
            currentSubPage != null -> {
                navigateToRoot()
                true
            }
            else -> {
                dismiss()
                true
            }
        }
    }

    private fun navigateToSubPage(subPage: View, title: String) {
        binding.layoutRootMenu.visibility = View.GONE
        binding.pageWallpaper.visibility = View.GONE
        binding.pageDisplay.visibility = View.GONE
        binding.pageStatusbar.visibility = View.GONE
        binding.pageApps.visibility = View.GONE
        binding.pageSecurity.visibility = View.GONE
        binding.pageAbout.visibility = View.GONE
        binding.subpageToggleApps.visibility = View.GONE
        binding.subpageButtonMapping.visibility = View.GONE
        binding.subpagePickApp.visibility = View.GONE

        subPage.visibility = View.VISIBLE
        currentSubPage = subPage

        if (subPage == binding.pageDisplay) {
            binding.cardHeroIcon.visibility = View.GONE
            binding.cardHeroBanner.visibility = View.VISIBLE
            displayDelegate.updateCornerPreview(config.cornerRadius)
        } else {
            binding.cardHeroIcon.visibility = View.VISIBLE
            binding.cardHeroBanner.visibility = View.GONE
        }

        binding.btnBack.visibility = View.VISIBLE
        binding.txtSettingsTitle.text = title

        val heroDesc = when (subPage) {
            binding.pageWallpaper -> "Select from dynamic background gradients, solid hues, or upload a custom TV wallpaper."
            binding.pageDisplay -> "Fine-tune app icon sizing, UI scale density, and layout geometry for your display."
            binding.pageStatusbar -> "Customize clock time format, network indicators, Bluetooth controls, and VPN shortcuts."
            binding.pageApps -> "Reorder apps, manage auto-categorization, or hide private apps in the secret vault."
            binding.pageSecurity -> "Configure lock types, PIN entry codes, biometric access, and secure application protection."
            binding.subpageButtonMapping -> "Map dedicated colored buttons or app hotkeys on your TV remote."
            binding.pageAbout -> "Launcher version details, credits, system restart, and default home screen settings."
            else -> "Customize your launcher settings and system preferences."
        }
        binding.txtHeroSubtitle.text = heroDesc
        binding.scrollContainer.smoothScrollTo(0, 0)
    }

    private fun navigateToRoot() {
        currentSubPage = null
        binding.pageWallpaper.visibility = View.GONE
        binding.pageDisplay.visibility = View.GONE
        binding.pageStatusbar.visibility = View.GONE
        binding.pageApps.visibility = View.GONE
        binding.pageSecurity.visibility = View.GONE
        binding.pageAbout.visibility = View.GONE
        binding.subpageToggleApps.visibility = View.GONE
        binding.subpageButtonMapping.visibility = View.GONE
        binding.subpagePickApp.visibility = View.GONE

        binding.cardHeroIcon.visibility = View.VISIBLE
        binding.cardHeroBanner.visibility = View.GONE

        binding.layoutRootMenu.visibility = View.VISIBLE
        binding.btnBack.visibility = View.GONE
        binding.txtSettingsTitle.text = "Settings"
        binding.txtHeroSubtitle.text = "Customize launcher display, apps, locks, and system preferences"
        binding.scrollContainer.smoothScrollTo(0, 0)
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
