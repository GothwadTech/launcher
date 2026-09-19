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
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.MODE_PC
import com.gothwad.launcher.data.NetStatus
import com.gothwad.launcher.data.bluetoothStatusFlow
import com.gothwad.launcher.data.networkStatusFlow
import com.gothwad.launcher.databinding.ActivityMainBinding
import com.gothwad.launcher.service.NotificationManagerBridge
import com.gothwad.launcher.ui.dialogs.BackgroundMediaDialogFragment
import com.gothwad.launcher.ui.dialogs.NotificationBottomSheetFragment
import com.gothwad.launcher.ui.dialogs.PinEntryDialogFragment
import com.gothwad.launcher.ui.dialogs.QuickDashboardDialogFragment
import com.gothwad.launcher.ui.dialogs.SearchDialogFragment
import com.gothwad.launcher.ui.dialogs.SettingsBottomSheetFragment
import com.gothwad.launcher.ui.dialogs.SetupWizardDialogFragment
import com.gothwad.launcher.ui.dialogs.VoiceSearchDialogFragment
import com.gothwad.launcher.ui.tv.TvLauncherFragment
import com.gothwad.launcher.ui.view.DeviceLockViewController
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
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
    private var currentNetStatus: NetStatus = NetStatus()
    private var currentBtStatus: BluetoothDeviceStatus = BluetoothDeviceStatus()
    private var currentMediaState: BackgroundMediaState = BackgroundMediaState()
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
                notifyFragmentRescan()
                refreshAppsList()
            }
        }
    }

    private val a11yAlertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            checkAccessibilityState()
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
            allApps = AppRepository.scan(this@MainActivity)
            val store = ConfigStore(this@MainActivity)
            val installedPackages = allApps.map { it.pkg }.toSet()
            com.gothwad.launcher.data.ButtonMappingManager.seedDefaultMappings(store, installedPackages)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
            .getString(LOCALE_KEY, "").orEmpty()
        super.attachBaseContext(if (lang.isEmpty()) newBase else applyLocale(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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

        // Phase 4: Device Lock on cold launcher process start
        checkDeviceLockOnColdStart()

        setupA11yRecoveryBanner()
        setupNavigation()
        setupStatusBar()
        observeStatusBarData()
        refreshAppsList()
    }

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
            if (config.deviceLock.enabled && config.deviceLock.value.isNotEmpty()) {
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
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        val navController = navHostFragment.navController

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                ConfigStore(this@MainActivity).flow.collectLatest { config ->
                    val targetDest = if (config.launcherMode == MODE_PC) {
                        R.id.pcLauncherFragment
                    } else {
                        R.id.tvLauncherFragment
                    }

                    if (navController.currentDestination?.id != targetDest) {
                        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
                        navGraph.setStartDestination(targetDest)
                        navController.graph = navGraph
                    }
                }
            }
        }
    }

    private fun setupStatusBar() {
        binding.mainStatusBar.apply {
            onDashboardClick = {
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
                    apps = allApps,
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

            onNotificationsClick = {
                NotificationBottomSheetFragment.newInstance()
                    .show(supportFragmentManager, NotificationBottomSheetFragment.TAG)
            }

            onSettingsClick = {
                openSettingsDialog()
            }
        }
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
            onRerunWizard = { showSetupWizard() },
            onModeSelected = { /* handled by ConfigStore */ }
        ).show(supportFragmentManager, SettingsBottomSheetFragment.TAG)
    }

    private fun showSetupWizard() {
        SetupWizardDialogFragment.newInstance {
            lifecycleScope.launch {
                ConfigStore(this@MainActivity).update { it.copy(setupDone = true) }
            }
        }.show(supportFragmentManager, SetupWizardDialogFragment.TAG)
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
                    // Mark package as unlocked in the session so Accessibility Service won't re-prompt immediately
                    com.gothwad.launcher.service.LauncherAccessibilityService.unlockedPackagesSession.add(app.pkg)
                    handleAppLaunch(app, skipLock = true)
                }
            ).show(supportFragmentManager, PinEntryDialogFragment.TAG)
        } else {
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
                        binding.mainStatusBar.applyConfig(config)
                        binding.mainStatusBar.visibility =
                            if (config.launcherMode == MODE_PC || !config.showStatusBar) View.GONE else View.VISIBLE
                    }
                }

                // 2. Network status flow
                launch {
                    networkStatusFlow(this@MainActivity).collectLatest { net ->
                        currentNetStatus = net
                        binding.mainStatusBar.setNetStatus(net)
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
                    while (isActive) {
                        val now = Date()
                        val timePattern = if (currentConfig.h24) "HH:mm" else "hh:mm a"
                        val dateFormatted = SimpleDateFormat("d MMM • EEE", Locale.ENGLISH).format(now)
                        val timeFormatted = SimpleDateFormat(timePattern, Locale.ENGLISH).format(now)
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

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        runCatching { unregisterReceiver(a11yAlertReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val LOCALE_PREFS = "locale"
        private const val LOCALE_KEY = "lang"

        fun persistLocale(context: Context, lang: String) {
            context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .edit().putString(LOCALE_KEY, lang).apply()
        }

        fun currentLocalePref(context: Context): String =
            context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .getString(LOCALE_KEY, "").orEmpty()

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
