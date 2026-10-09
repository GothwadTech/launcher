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
        binding.rowCatAppearance.setOnClickListener { navigateToSubPage(binding.pageCatAppearance, "Appearance & Themes") }
        binding.rowCatApps.setOnClickListener { navigateToSubPage(binding.pageCatApps, "Apps & Security") }
        binding.rowCatRemote.setOnClickListener { navigateToSubPage(binding.subpageButtonMapping, "Remote Button Mapping") }
        binding.rowCatDevice.setOnClickListener { navigateToSubPage(binding.pageCatDevice, "Device, Network & System") }

        // Sub-page clicks within categories
        binding.rowWallpaper.setOnClickListener { navigateToSubPage(binding.pageWallpaper, "Wallpaper & Themes") }
        binding.rowDisplay.setOnClickListener { navigateToSubPage(binding.pageDisplay, "Display & App Curves") }
        binding.rowStatusbar.setOnClickListener { navigateToSubPage(binding.pageStatusbar, "Status Bar & Clock") }
        binding.rowApps.setOnClickListener { navigateToSubPage(binding.pageApps, "Apps & Secret Vault") }
        binding.rowSecurity.setOnClickListener { navigateToSubPage(binding.pageSecurity, "Security & PIN Locks") }
        binding.rowNetwork.setOnClickListener { Actions.openNetworkSettings(requireContext()) }
        binding.rowDevicePrefs.setOnClickListener { safeStartActivity(Settings.ACTION_SETTINGS) }
        binding.rowPermissions.setOnClickListener { Actions.openAppInfo(requireContext(), requireContext().packageName) }
        binding.rowCatAbout.setOnClickListener { navigateToSubPage(binding.pageAbout, "System & About") }
    }

    private fun setupFocusDescriptions() {
        binding.rowCatAppearance.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Customize wallpaper, live TV banner curves, UI scale, and status bar clock."
        }
        binding.rowCatApps.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Manage installed apps, hide sensitive apps into secret vault, and set PIN locks."
        }
        binding.rowCatRemote.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Map dedicated TV remote hotkeys to directly launch your favorite apps."
        }
        binding.rowCatDevice.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Manage Wi-Fi, Android TV settings, permissions, setup wizard, and app info."
        }

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
        binding.rowNetwork.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Configure Wi-Fi, Ethernet, and network proxy connections."
        }
        binding.rowDevicePrefs.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Open full device Android system preferences, display, audio, and accounts."
        }
        binding.rowPermissions.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Review and manage system permissions, overlay rights, and storage access."
        }
        binding.rowCatAbout.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtHeroSubtitle.text = "Rerun setup wizard, set default launcher, restart, and view app details."
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

    private fun updateAdbStatusBadge() {
        if (!isAdded || _binding == null) return
        val hasShell = AppUpdateManager.hasRootOrAdbShell()
        val canPkgInstall = AppUpdateManager.canRequestPackageInstalls(requireContext())

        if (hasShell) {
            binding.txtAdbStatusBadge.text = "⚡ Root / ADB Shell: Available (Silent Update)"
            binding.txtAdbStatusBadge.setTextColor(Color.parseColor("#81C784"))
            binding.btnGrantInstallPerm.visibility = View.GONE
        } else if (canPkgInstall) {
            binding.txtAdbStatusBadge.text = "📦 Package Installer: Allowed (ADB/Direct shell fallback)"
            binding.txtAdbStatusBadge.setTextColor(Color.parseColor("#64B5F6"))
            binding.btnGrantInstallPerm.visibility = View.GONE
        } else {
            binding.txtAdbStatusBadge.text = "⚠️ Install Permission: Needed for stock TV installer"
            binding.txtAdbStatusBadge.setTextColor(Color.parseColor("#FFB74D"))
            binding.btnGrantInstallPerm.visibility = View.VISIBLE
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
    }

    private fun navigateToRoot() {
        pageHistory.clear()
        currentSubPage = null
        hideAllPages()

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
