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
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.gothwad.launcher.Actions
import com.gothwad.launcher.R
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.AppUpdateManager
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.GitHubReleaseInfo
import com.gothwad.launcher.data.InstallResult
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.UpdateCheckResult
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
                wallpaperDelegate.syncSelection()
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

        // White base glyph + state-list tint: the close/back icons must turn dark when the
        // white focus highlight of bg_settings_row lands on them (a fixed white icon
        // vanished exactly while focused).
        val iconTint = ContextCompat.getColorStateList(requireContext(), R.color.color_settings_icon)
        binding.btnCloseSettings.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE)
        )
        binding.btnCloseSettings.imageTintList = iconTint
        binding.btnCloseSettings.setOnClickListener { dismiss() }

        binding.btnBack.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_BACK, Color.WHITE)
        )
        binding.btnBack.imageTintList = iconTint
        binding.btnBack.setOnClickListener {
            handleBackPress()
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
        binding.rowCatAppearance.setOnClickListener { navigateToSubPage(binding.pageCatAppearance, getString(R.string.page_title_appearance)) }
        binding.rowCatApps.setOnClickListener { navigateToSubPage(binding.pageCatApps, getString(R.string.page_title_apps_security)) }
        binding.rowCatRemote.setOnClickListener { navigateToSubPage(binding.subpageButtonMapping, getString(R.string.button_mapping_title)) }
        binding.rowCatDevice.setOnClickListener { navigateToSubPage(binding.pageCatDevice, getString(R.string.page_title_device)) }

        // Sub-page clicks within categories
        binding.rowWallpaper.setOnClickListener { navigateToSubPage(binding.pageWallpaper, getString(R.string.page_title_wallpaper)) }
        binding.rowDisplay.setOnClickListener { navigateToSubPage(binding.pageDisplay, getString(R.string.page_title_display)) }
        binding.rowStatusbar.setOnClickListener { navigateToSubPage(binding.pageStatusbar, getString(R.string.page_title_statusbar)) }
        binding.rowApps.setOnClickListener { navigateToSubPage(binding.pageApps, getString(R.string.page_title_apps_vault)) }
        binding.rowSecurity.setOnClickListener { navigateToSubPage(binding.pageSecurity, getString(R.string.page_title_security)) }
        binding.rowNetwork.setOnClickListener { Actions.openNetworkSettings(requireContext()) }
        binding.rowDevicePrefs.setOnClickListener { safeStartActivity(Settings.ACTION_SETTINGS) }
        binding.rowPermissions.setOnClickListener { Actions.openAppInfo(requireContext(), requireContext().packageName) }
        binding.rowCatAbout.setOnClickListener { navigateToSubPage(binding.pageAbout, getString(R.string.page_title_about)) }
    }

    private fun setupFocusDescriptions() {
        binding.rowCatAppearance.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_cat_appearance)
        }
        binding.rowCatApps.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_cat_apps)
        }
        binding.rowCatRemote.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_cat_remote)
        }
        binding.rowCatDevice.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_cat_device)
        }

        binding.rowWallpaper.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_wallpaper)
        }
        binding.rowDisplay.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_display)
        }
        binding.rowStatusbar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_statusbar)
        }
        binding.rowApps.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_apps)
        }
        binding.rowSecurity.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_security)
        }
        binding.rowNetwork.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_network)
        }
        binding.rowDevicePrefs.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_device_prefs)
        }
        binding.rowPermissions.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_permissions)
        }
        binding.rowCatAbout.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.setText(R.string.focus_desc_about)
        }
    }

    private var latestRelease: GitHubReleaseInfo? = null
    private var downloadedApkFile: File? = null

    private fun bindAboutSettings() {
        val currentVer = try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            pInfo.versionName ?: "1.0.6"
        } catch (_: Exception) {
            "1.0.6"
        }
        val currentCode = try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) pInfo.longVersionCode.toInt() else pInfo.versionCode
        } catch (_: Exception) {
            7
        }

        binding.txtAboutVersion.text = "Version $currentVer • Build $currentCode"

        updateAdbStatusBadge()

        binding.btnGrantInstallPerm.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = android.net.Uri.parse("package:${requireContext().packageName}")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    safeStartActivity(Settings.ACTION_SECURITY_SETTINGS)
                }
            } else {
                safeStartActivity(Settings.ACTION_SECURITY_SETTINGS)
            }
        }

        binding.btnCheckUpdate.setOnClickListener {
            checkGitHubUpdate(currentVer)
        }

        binding.btnInstallUpdate.setOnClickListener {
            startDownloadAndInstall()
        }

        binding.btnRerunWizard.setOnClickListener {
            dismiss()
            onRerunWizard?.invoke()
        }
        binding.btnSetDefaultHome.setOnClickListener {
            safeStartActivity(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_SETTINGS)
        }
        binding.btnRestartLauncher.setOnClickListener {
            dismiss()
            activity?.recreate()
        }
    }

    /**
     * Shows which install path this device supports.
     *
     * The probe shells out to `su`, which can block for as long as a root prompt waits for the
     * user, so it runs on IO behind a "checking" state instead of on the UI thread - calling it
     * synchronously here was an ANR on any rooted box.
     */
    private fun updateAdbStatusBadge() {
        if (!isAdded || _binding == null) return
        val context = requireContext().applicationContext

        binding.txtAdbStatusBadge.setText(R.string.update_badge_checking)
        binding.txtAdbStatusBadge.setTextColor(Color.parseColor("#94A3B8"))

        viewLifecycleOwner.lifecycleScope.launch {
            val capability = AppUpdateManager.detectInstallCapability(context)
            if (!isAdded || _binding == null) return@launch

            when (capability) {
                AppUpdateManager.InstallCapability.ROOT_SHELL -> {
                    binding.txtAdbStatusBadge.setText(R.string.update_badge_root)
                    binding.txtAdbStatusBadge.setTextColor(Color.parseColor("#81C784"))
                    binding.btnGrantInstallPerm.visibility = View.GONE
                }
                AppUpdateManager.InstallCapability.PACKAGE_INSTALLER -> {
                    binding.txtAdbStatusBadge.setText(R.string.update_badge_installer)
                    binding.txtAdbStatusBadge.setTextColor(Color.parseColor("#64B5F6"))
                    binding.btnGrantInstallPerm.visibility = View.GONE
                }
                AppUpdateManager.InstallCapability.NEEDS_PERMISSION -> {
                    binding.txtAdbStatusBadge.setText(R.string.update_badge_permission)
                    binding.txtAdbStatusBadge.setTextColor(Color.parseColor("#FFB74D"))
                    binding.btnGrantInstallPerm.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun checkGitHubUpdate(currentVer: String) {
        binding.btnCheckUpdate.isEnabled = false
        binding.txtUpdateStatus.text = "Checking GitHub (GothwadTech/launcher) for new release…"
        binding.txtUpdateStatus.setTextColor(Color.parseColor("#8AB4F8"))

        viewLifecycleOwner.lifecycleScope.launch {
            val result = AppUpdateManager.checkLatestRelease(currentVer)
            if (!isAdded || _binding == null) return@launch
            binding.btnCheckUpdate.isEnabled = true

            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    latestRelease = result.release
                    binding.txtUpdateStatus.text = "🎉 New Version Found: v${result.release.versionName}\nRelease: ${result.release.releaseTitle}\nFile: ${result.release.apkFileName ?: "APK"}"
                    binding.txtUpdateStatus.setTextColor(Color.parseColor("#81C784"))
                    binding.btnInstallUpdate.isEnabled = true
                    binding.btnInstallUpdate.text = "🚀 Download & Update (v${result.release.versionName})"
                }
                is UpdateCheckResult.UpToDate -> {
                    latestRelease = null
                    binding.txtUpdateStatus.text = "✓ Gothwad Launcher is up to date (v$currentVer, tag: ${result.latestTag})."
                    binding.txtUpdateStatus.setTextColor(Color.parseColor("#94A3B8"))
                    binding.btnInstallUpdate.isEnabled = false
                    binding.btnInstallUpdate.text = "✓ Up to date"
                }
                is UpdateCheckResult.Error -> {
                    binding.txtUpdateStatus.text = "❌ Update check failed: ${result.message}"
                    binding.txtUpdateStatus.setTextColor(Color.parseColor("#E57373"))
                }
            }
        }
    }

    private fun startDownloadAndInstall() {
        val rel = latestRelease ?: return
        val apkUrl = rel.apkDownloadUrl ?: return
        val fileName = rel.apkFileName ?: "launcher-update.apk"

        binding.btnInstallUpdate.isEnabled = false
        binding.btnCheckUpdate.isEnabled = false
        binding.progressUpdateDownload.visibility = View.VISIBLE
        binding.progressUpdateDownload.progress = 0
        binding.txtUpdateStatus.text = "Downloading APK: 0%…"
        binding.txtUpdateStatus.setTextColor(Color.parseColor("#8AB4F8"))

        viewLifecycleOwner.lifecycleScope.launch {
            val dlResult = AppUpdateManager.downloadApk(
                context = requireContext(),
                downloadUrl = apkUrl,
                targetFileName = fileName,
                onProgress = { percent ->
                    if (isAdded && _binding != null) {
                        binding.progressUpdateDownload.progress = percent
                        binding.txtUpdateStatus.text = "Downloading APK: $percent%…"
                    }
                }
            )

            if (!isAdded || _binding == null) return@launch
            binding.progressUpdateDownload.visibility = View.GONE
            binding.btnCheckUpdate.isEnabled = true

            dlResult.onSuccess { apkFile ->
                downloadedApkFile = apkFile
                binding.txtUpdateStatus.text = "Download complete (${apkFile.length() / 1024} KB). Installing via ADB shell / PackageInstaller…"
                binding.txtUpdateStatus.setTextColor(Color.parseColor("#81C784"))

                // Step 1: Attempt silent self-update via ADB shell / su
                val adbResult = AppUpdateManager.installViaAdbShell(apkFile)
                if (adbResult is InstallResult.SuccessAdb) {
                    binding.txtUpdateStatus.text = "✓ Successfully installed update via ADB/Shell! Restarting launcher…"
                    Actions.toast(requireContext(), "Update installed via ADB!")
                    binding.cardUpdateSection.postDelayed({
                        activity?.recreate()
                    }, 1200)
                } else {
                    // Step 2: Fallback to Android PackageInstaller via FileProvider
                    binding.txtUpdateStatus.text = "ADB shell install not granted. Launching Android Package Installer…"
                    val pkgResult = AppUpdateManager.installViaPackageInstaller(requireContext(), apkFile)
                    when (pkgResult) {
                        is InstallResult.PackageInstallerLaunched -> {
                            binding.txtUpdateStatus.text = "Installer opened. Confirm update on your TV screen."
                            binding.btnInstallUpdate.isEnabled = true
                            binding.btnInstallUpdate.text = "Retry Install"
                        }
                        is InstallResult.Error -> {
                            binding.txtUpdateStatus.text = "Install error: ${pkgResult.message}"
                            binding.txtUpdateStatus.setTextColor(Color.parseColor("#E57373"))
                            binding.btnInstallUpdate.isEnabled = true
                            binding.btnInstallUpdate.text = "Retry Update"
                        }
                        else -> {}
                    }
                }
            }.onFailure { err ->
                binding.txtUpdateStatus.text = "Download failed: ${err.localizedMessage ?: err.javaClass.simpleName}"
                binding.txtUpdateStatus.setTextColor(Color.parseColor("#E57373"))
                binding.btnInstallUpdate.isEnabled = true
                binding.btnInstallUpdate.text = "Retry Download"
            }
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

    private val pageHistory = mutableListOf<Pair<View, String>>()

    private fun hideAllPages() {
        binding.layoutRootMenu.visibility = View.GONE
        binding.pageCatAppearance.visibility = View.GONE
        binding.pageCatApps.visibility = View.GONE
        binding.pageCatDevice.visibility = View.GONE
        binding.pageWallpaper.visibility = View.GONE
        binding.pageDisplay.visibility = View.GONE
        binding.pageStatusbar.visibility = View.GONE
        binding.pageApps.visibility = View.GONE
        binding.pageSecurity.visibility = View.GONE
        binding.pageAbout.visibility = View.GONE
        binding.subpageToggleApps.visibility = View.GONE
        binding.subpageButtonMapping.visibility = View.GONE
        binding.subpagePickApp.visibility = View.GONE
    }

    private fun handleBackPress(): Boolean {
        if (pageHistory.isNotEmpty()) {
            val (prevPage, prevTitle) = pageHistory.removeAt(pageHistory.lastIndex)
            if (prevPage == binding.layoutRootMenu) {
                navigateToRoot()
            } else {
                navigateToSubPage(prevPage, prevTitle, addToHistory = false)
            }
            return true
        } else if (currentSubPage != null) {
            navigateToRoot()
            return true
        } else {
            dismiss()
            return true
        }
    }

    fun navigateToSubPage(subPage: View, title: String, addToHistory: Boolean = true) {
        val previousPage = currentSubPage ?: binding.layoutRootMenu
        val previousTitle = binding.txtSettingsTitle.text.toString()

        if (addToHistory && subPage != previousPage) {
            pageHistory.add(previousPage to previousTitle)
        }

        hideAllPages()
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
            binding.pageCatAppearance -> "Customize wallpaper, live TV banner curves, UI scale, and status bar clock."
            binding.pageCatApps -> "Manage apps, hide apps into secret vault, and configure PIN locks."
            binding.pageCatDevice -> "Manage Wi-Fi, Ethernet, system permissions, and full Android TV settings."
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

        // Put D-pad focus on the page's first control. Before this, entering a sub-page
        // left focus on the now-GONE row; on many TV firmwares the remote then appears
        // dead until a direction key happens to re-trigger focus search.
        binding.scrollContainer.post { focusFirstDescendant(subPage) }
    }

    /** Requests focus on the first visible, focusable descendant of [root]. */
    private fun focusFirstDescendant(root: View): Boolean {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue
                if (child.isFocusable && child.isEnabled && child.requestFocus()) return true
                if (child is ViewGroup && focusFirstDescendant(child)) return true
            }
            return false
        }
        return root.isFocusable && root.isEnabled && root.requestFocus()
    }

    private fun navigateToRoot() {
        pageHistory.clear()
        currentSubPage = null
        hideAllPages()

        binding.cardHeroIcon.visibility = View.VISIBLE
        binding.cardHeroBanner.visibility = View.GONE

        binding.layoutRootMenu.visibility = View.VISIBLE
        binding.btnBack.visibility = View.GONE
        binding.txtSettingsTitle.setText(R.string.settings_root_title)
        binding.txtHeroSubtitle.setText(R.string.settings_root_subtitle)
        binding.scrollContainer.smoothScrollTo(0, 0)
        binding.scrollContainer.post { focusFirstDescendant(binding.layoutRootMenu) }
    }

    private fun safeStartActivity(action: String, fallbackAction: String? = null) {
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            if (fallbackAction != null) {
                try {
                    startActivity(Intent(fallbackAction))
                } catch (_: Exception) {
                    Actions.toast(requireContext(), getString(R.string.toast_cannot_open_settings))
                }
            } else {
                Actions.toast(requireContext(), getString(R.string.toast_cannot_open_settings))
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
