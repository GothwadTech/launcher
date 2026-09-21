package com.gothwad.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.AppRepository
import com.gothwad.launcher.data.BackgroundMediaState
import com.gothwad.launcher.data.BackgroundMediaTracker
import com.gothwad.launcher.data.BluetoothDeviceStatus
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.ui.AppLockGate
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.LockSecurity
import com.gothwad.launcher.data.ProcessHeartbeat
import com.gothwad.launcher.data.SelfHealGuard
import com.gothwad.launcher.data.NetStatus
import com.gothwad.launcher.data.bluetoothStatusFlow
import com.gothwad.launcher.data.networkStatusFlow
import com.gothwad.launcher.databinding.ActivityMainBinding
import com.gothwad.launcher.service.LauncherAccessibilityService
import com.gothwad.launcher.service.NotificationManagerBridge
import com.gothwad.launcher.ui.dialogs.BackgroundMediaDialogFragment
import com.gothwad.launcher.ui.dialogs.NotificationBottomSheetFragment
import com.gothwad.launcher.ui.dialogs.QuickDashboardDialogFragment
import com.gothwad.launcher.ui.dialogs.SearchDialogFragment
import com.gothwad.launcher.ui.dialogs.SettingsBottomSheetFragment
import com.gothwad.launcher.ui.dialogs.SetupWizardDialogFragment
import com.gothwad.launcher.ui.dialogs.VoiceSearchDialogFragment
import com.gothwad.launcher.ui.tv.TvLauncherFragment
import com.gothwad.launcher.ui.view.DeviceLockViewController
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Bumped whenever a package is installed/removed so the app grid rescans. */
    var rescanTick: Int = 0
        private set

    private var currentConfig: LauncherConfig = LauncherConfig()

    // Clock formatters are cached: this loop runs once per second, 24x7 on a TV, and used
    // to allocate two SimpleDateFormat + Date objects every tick (issue #32).
    private var clockFormatter: SimpleDateFormat? = null
    private var clockPatternInUse: String? = null

    /** Debounce for package add/remove bursts (issue #33). */
    private var rescanJob: Job? = null

    private var currentNetStatus: NetStatus = NetStatus()
    private var currentBtStatus: BluetoothDeviceStatus = BluetoothDeviceStatus()
    private var currentMediaState: BackgroundMediaState = BackgroundMediaState()

    /** Written from an IO coroutine, read from the UI thread - keep it volatile. */
    @Volatile
    private var allApps: List<AppEntry> = emptyList()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart
            val launchable = pkg != null && (
                packageManager.getLeanbackLaunchIntentForPackage(pkg) != null ||
                    packageManager.getLaunchIntentForPackage(pkg) != null
                )
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED || launchable) {
                rescanTick++
                scheduleAppsRescan()
            }
        }
    }

    private val a11yAlertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            checkAccessibilityState()
        }
    }

    /**
     * Debounces install/uninstall bursts (a batch update fires dozens of broadcasts) and
     * keeps the rescan off the broadcast thread. The fragment callback runs on the main
     * dispatcher, and the scan itself reuses cached entries via `AppRepository`.
     */
    private fun scheduleAppsRescan() {
        rescanJob?.cancel()
        rescanJob = lifecycleScope.launch {
            delay(RESCAN_DEBOUNCE_MS)
            refreshAppsList()
            notifyFragmentRescan()
        }
    }

    private fun notifyFragmentRescan() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
        if (currentFragment is TvLauncherFragment) {
            currentFragment.onRescanRequested()
        }
    }

    private fun refreshAppsList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val apps = AppRepository.scan(this@MainActivity)
            allApps = apps
            val store = ConfigStore(this@MainActivity)
            val installedPackages = apps.map { it.pkg }.toSet()
            com.gothwad.launcher.data.ButtonMappingManager.seedDefaultMappings(store, installedPackages)

            // Newly installed apps: drop them into their real auto-category instead of
            // always into the first section. `knownApps` (previously a dead config field)
            // is what makes "new since the last scan" detectable.
            val config = runCatching { store.flow.first() }.getOrNull() ?: return@launch
            val newPackages = installedPackages - config.knownApps
            if (newPackages.isEmpty()) {
                if (config.knownApps != installedPackages) {
                    store.update { it.copy(knownApps = installedPackages) }
                }
                return@launch
            }

            store.update { cfg ->
                val updatedSections = cfg.sections.toMutableMap()
                if (cfg.autoCategoryOnInstall) {
                    val fallbackId = cfg.categories.firstOrNull()?.id
                    for (pkg in newPackages) {
                        if (updatedSections.containsKey(pkg)) continue
                        val auto = apps.firstOrNull { it.pkg == pkg }?.autoCategory
                        val target = when {
                            auto != null && cfg.categories.any { it.id == auto } -> auto
                            else -> fallbackId
                        }
                        if (target != null) updatedSections[pkg] = setOf(target)
                    }
                }
                cfg.copy(sections = updatedSections, knownApps = installedPackages)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // NOTE: there is no locale override here any more. The old code read a
        // "locale"/"lang" SharedPreferences entry that nothing ever wrote, so it was dead
        // code pretending to be an i18n feature. Real localization needs translated
        // string resources first (see ISSUE-REPORT.md, issue #19).
        super.attachBaseContext(com.gothwad.launcher.ui.DensityAdapter.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.gothwad.launcher.ui.DensityAdapter.apply(this)
        super.onCreate(savedInstanceState)

        // A home screen never exits on Back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* no-op */ }
        })

        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Migrate any legacy plain-text PIN/password to salted PBKDF2 hashes.
        lifecycleScope.launch {
            runCatching { LockSecurity.migrateLegacyCredentials(ConfigStore(this@MainActivity)) }
        }

        // Crash-loop guard: if the launcher just came back from repeated crashes, say so
        // instead of silently restarting forever; and once we have been stable for a
        // while, clear the relaunch budget again.
        lifecycleScope.launch {
            if (SelfHealGuard.isInSafeMode(this@MainActivity)) {
                Actions.toast(this@MainActivity, getString(R.string.safe_mode_active))
            }
            delay(SelfHealGuard.healthyDelayMs())
            SelfHealGuard.markHealthy(this@MainActivity)
        }

        // Phase 4: Device Lock on cold launcher process start
        checkDeviceLockOnColdStart()

        setupA11yRecoveryBanner()
        setupNavigation()
        setupStatusBar()
        observeStatusBarData()
        refreshAppsList()
    }

    private val bluetoothPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { /* status is refreshed by the BT flow */ }

    private var deviceLockController: DeviceLockViewController? = null

    private fun checkDeviceLockOnColdStart() {
        if (GothwadApplication.hasUnlockedDeviceThisProcess) {
            binding.deviceLockContainer.visibility = View.GONE
            return
        }

        // Show solid lock container immediately so not even a single frame of home UI is leaked
        binding.deviceLockContainer.visibility = View.VISIBLE
        binding.deviceLockContainer.bringToFront()

        lifecycleScope.launch {
            val store = ConfigStore(this@MainActivity)
            val config = store.flow.first()
            currentConfig = config
            if (config.deviceLock.enabled && config.deviceLock.ready) {
                deviceLockController = DeviceLockViewController(
                    container = binding.deviceLockContainer,
                    credential = config.deviceLock,
                    onUnlocked = {
                        GothwadApplication.hasUnlockedDeviceThisProcess = true
                        deviceLockController = null
                    }
                )
                deviceLockController?.show()
            } else {
                GothwadApplication.hasUnlockedDeviceThisProcess = true
                binding.deviceLockContainer.visibility = View.GONE
            }
        }
    }

    private fun setupA11yRecoveryBanner() {
        binding.bannerA11yRecovery.btnA11yAction.setOnClickListener {
            Actions.openAccessibilitySettings(this)
        }
        binding.bannerA11yRecovery.btnA11yDismiss.setOnClickListener {
            binding.bannerA11yRecovery.cardA11yBanner.visibility = View.GONE
        }

        // Register receiver for watchdog alerts
        ContextCompat.registerReceiver(
            this,
            a11yAlertReceiver,
            IntentFilter(com.gothwad.launcher.service.LauncherWatchdogService.ACTION_ACCESSIBILITY_DISABLED_ALERT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityState()
    }

    private fun checkAccessibilityState() {
        val disabledAfterCrash = com.gothwad.launcher.data.AccessibilityStateTracker
            .checkAccessibilityDisabledAfterCrash(this)

        binding.bannerA11yRecovery.cardA11yBanner.visibility =
            if (disabledAfterCrash) View.VISIBLE else View.GONE
    }

    private fun setupNavigation() {
        // NavHostFragment loads tvLauncherFragment as startDestination from nav_graph.xml
    }

    private fun setupStatusBar() {
        binding.mainStatusBar.apply {
            onDashboardClick = {
                ensureBluetoothPermission()
                QuickDashboardDialogFragment.newInstance(
                    net = currentNetStatus,
                    bt = currentBtStatus,
                    onOpenSettings = { openSettingsDialog() }
                ).show(supportFragmentManager, QuickDashboardDialogFragment.TAG)
            }

            onSearchClick = {
                SearchDialogFragment.newInstance(
                    apps = allApps,
                    config = currentConfig,
                    onLaunch = { app -> handleAppLaunch(app) }
                ).show(supportFragmentManager, SearchDialogFragment.TAG)
            }

            onVoiceSearchClick = {
                VoiceSearchDialogFragment.newInstance(
                    // The vault stays closed for voice search too: hidden apps are only
                    // listed once they were deliberately opened in this session.
                    apps = allApps.filter { app ->
                        app.pkg !in currentConfig.hidden ||
                            LauncherAccessibilityService.unlockedPackagesSession.contains(app.pkg)
                    },
                    onLaunch = { app -> handleAppLaunch(app) }
                ).show(supportFragmentManager, VoiceSearchDialogFragment.TAG)
            }

            onBluetoothClick = {
                QuickDashboardDialogFragment.newInstance(
                    net = currentNetStatus,
                    bt = currentBtStatus,
                    onOpenSettings = { openSettingsDialog() }
                ).show(supportFragmentManager, QuickDashboardDialogFragment.TAG)
            }

            onBackgroundMediaClick = {
                BackgroundMediaDialogFragment.newInstance(
                    state = currentMediaState
                ).show(supportFragmentManager, BackgroundMediaDialogFragment.TAG)
            }

            onNetworkClick = {
                Actions.openNetworkSettings(this@MainActivity)
            }

            onVpnClick = {
                // Opens the user's chosen VPN app when configured, else system VPN settings.
                val pkg = currentConfig.vpnApp
                if (pkg.isNotEmpty() && Actions.isInstalled(this@MainActivity, pkg)) {
                    Actions.launchApp(this@MainActivity, pkg)
                } else {
                    Actions.openVpnSettings(this@MainActivity)
                }
            }

            onNotificationsClick = {
                NotificationBottomSheetFragment.newInstance()
                    .show(supportFragmentManager, NotificationBottomSheetFragment.TAG)
            }

            onSettingsClick = {
                openSettingsDialog()
            }
        }
    }

    /**
     * BLUETOOTH_CONNECT is a runtime permission on Android 12+. Without it the remote
     * battery / device name can never be read, so ask for it the first time the user opens
     * a screen that shows Bluetooth information (previously it was only *checked*).
     */
    private fun ensureBluetoothPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.BLUETOOTH_CONNECT
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) return
        runCatching { bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT) }
    }

    private fun openSettingsDialog() {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        SettingsBottomSheetFragment.newInstance(
            config = currentConfig,
            apps = allApps,
            onWallpaperChanged = {
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
                if (currentFragment is TvLauncherFragment) {
                    currentFragment.applyWallpaper()
                }
            },
            onRerunWizard = { showSetupWizard() }
        ).show(supportFragmentManager, SettingsBottomSheetFragment.TAG)
    }

    private fun showSetupWizard() {
        SetupWizardDialogFragment.newInstance {
            lifecycleScope.launch {
                ConfigStore(this@MainActivity).update { it.copy(setupDone = true) }
            }
        }.show(supportFragmentManager, SetupWizardDialogFragment.TAG)
    }

    private fun handleAppLaunch(app: AppEntry) {
        // All lock decisions (device lock, app lock, hidden vault) go through one place -
        // see AppLockGate. The unbypassable enforcement still lives in the accessibility
        // service; this only keeps the prompt UX consistent and avoids double-prompting.
        AppLockGate.evaluate(
            fragmentManager = supportFragmentManager,
            app = app,
            config = currentConfig,
            deviceUnlockedThisProcess = GothwadApplication.hasUnlockedDeviceThisProcess,
        ) {
            Actions.launchApp(this, app.pkg)
        }
    }


    private fun observeStatusBarData() {
        val configStore = ConfigStore(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Config flow
                launch {
                    configStore.flow.collectLatest { config ->
                        currentConfig = config

                        // Keep the memory guardian and the density adapter in sync with
                        // the user's preferences (both default to the safe/off choice).
                        com.gothwad.launcher.data.AppLaunchTracker.aggressiveTrimEnabled =
                            config.aggressiveMemoryTrim
                        if (com.gothwad.launcher.ui.DensityAdapter.respectSystemFontScale != config.respectSystemFontScale) {
                            com.gothwad.launcher.ui.DensityAdapter.respectSystemFontScale =
                                config.respectSystemFontScale
                            runCatching { com.gothwad.launcher.ui.DensityAdapter.apply(this@MainActivity) }
                        }

                        binding.mainStatusBar.applyConfig(config)
                        binding.mainStatusBar.visibility =
                            if (!config.showStatusBar) View.GONE else View.VISIBLE
                        binding.mainStatusBar.setVpnStatus(currentNetStatus.vpn, config.showVpnButton)
                    }
                }

                // 2. Network status flow
                launch {
                    networkStatusFlow(this@MainActivity).collectLatest { net ->
                        currentNetStatus = net
                        binding.mainStatusBar.setNetStatus(net)
                        binding.mainStatusBar.setVpnStatus(net.vpn, currentConfig.showVpnButton)
                    }
                }

                // 3. Bluetooth status flow
                launch {
                    bluetoothStatusFlow(this@MainActivity).flowOn(Dispatchers.IO).collectLatest { bt ->
                        currentBtStatus = bt
                        binding.mainStatusBar.setBluetoothStatus(bt)
                    }
                }

                // 4. Background media flow
                launch {
                    BackgroundMediaTracker.backgroundMediaFlow(this@MainActivity).collectLatest { media ->
                        currentMediaState = media
                        binding.mainStatusBar.setBackgroundMedia(media)
                    }
                }

                // 5. Notifications flow
                launch {
                    NotificationManagerBridge.notifications.collectLatest { notifs ->
                        val hasPermission = NotificationManagerBridge.isServiceConnected.value
                        binding.mainStatusBar.setNotificationCount(notifs.size, hasPermission)
                    }
                }

                // 6. Clock & Date loop (Format: 16 Sep • Wed • 01:26 AM)
                launch {
                    var heartbeatTick = 0
                    while (isActive) {
                        // Publish a foreground heartbeat every ~5s (cheap, tiny file) so the
                        // watchdog can tell "process alive" from "process dead/hung".
                        if (heartbeatTick++ % 5 == 0) {
                            ProcessHeartbeat.touch(this@MainActivity, foreground = true)
                        }
                        val now = Date()
                        val timePattern = if (currentConfig.h24) PATTERN_24H else PATTERN_12H
                        if (clockPatternInUse != timePattern || clockFormatter == null) {
                            clockFormatter = SimpleDateFormat(timePattern, Locale.ENGLISH)
                            clockPatternInUse = timePattern
                        }
                        val dateFormatted = dateFormatter.format(now)
                        val timeFormatted = clockFormatter!!.format(now)
                        val fullDateTime = "$dateFormatted • $timeFormatted"
                        binding.mainStatusBar.setClockTime(fullDateTime)
                        delay(1000)
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            if (deviceLockController?.handleKeyEvent(event.keyCode, event) == true) {
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        com.gothwad.launcher.ui.DensityAdapter.apply(this)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        runCatching { unregisterReceiver(a11yAlertReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val RESCAN_DEBOUNCE_MS = 600L
        private const val PATTERN_24H = "HH:mm"
        private const val PATTERN_12H = "hh:mm a"

        /** Main-thread only (see the status bar clock loop). */
        private val dateFormatter = SimpleDateFormat("d MMM • EEE", Locale.ENGLISH)

        /**
         * Applies a locale for the current context. Kept as the single entry point for a
         * future language picker, but NOT driven by a phantom preference any more.
         */
        fun applyLocale(context: Context, lang: String): Context {
            val locale = if (lang.contains('-')) {
                val parts = lang.split('-')
                Locale(parts[0], parts.getOrElse(1) { "" })
            } else Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }
    }
}
