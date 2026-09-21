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
            updateSubtitles = { updateSubtitles() },
            openAppPickerForVpn = { buttonMappingDelegate.openAppPickerForVpn() }
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
            navigateToSubPage = { page, title -> navigateToSubPage(page, title) },
            onVpnAppPicked = { statusBarDelegate.updateVpnSubtitle() }
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
                if (buttonMappingDelegate.pickAppMode == SettingsButtonMappingDelegate.PickAppMode.VPN_SHORTCUT) {
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
                if (buttonMappingDelegate.pickAppMode == SettingsButtonMappingDelegate.PickAppMode.VPN_SHORTCUT) {
                    navigateToSubPage(binding.pageStatusbar, "Status Bar & Clock")
                } else {
                    navigateToSubPage(binding.subpageButtonMapping, "Remote Button Mapping")
                }
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

        binding.btnBack.visibility = View.VISIBLE
        binding.txtSettingsTitle.text = title
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

        binding.layoutRootMenu.visibility = View.VISIBLE
        binding.btnBack.visibility = View.GONE
        binding.txtSettingsTitle.text = "Settings"
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
