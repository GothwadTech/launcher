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
import com.gothwad.launcher.data.WeatherData
import com.gothwad.launcher.data.WeatherRepository
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
import com.gothwad.launcher.ui.dialogs.WeatherDetailsDialogFragment
import com.gothwad.launcher.ui.views.TvLauncherFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
    private var currentWeather: WeatherData = WeatherData()
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

        setupNavigation()
        setupStatusBar()
        observeStatusBarData()
        refreshAppsList()
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

            onWeatherClick = {
                WeatherDetailsDialogFragment.newInstance(
                    weather = currentWeather,
                    onRefresh = { refreshWeather() }
                ).show(supportFragmentManager, WeatherDetailsDialogFragment.TAG)
            }

            onBackgroundMediaClick = {
                BackgroundMediaDialogFragment.newInstance(
                    state = currentMediaState
                ).show(supportFragmentManager, BackgroundMediaDialogFragment.TAG)
            }

            onVpnClick = {
                if (currentConfig.vpnApp.isNotEmpty()) {
                    Actions.launchApp(this@MainActivity, currentConfig.vpnApp)
                } else {
                    Actions.openVpnSettings(this@MainActivity)
                }
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
        if (!skipLock && currentConfig.appLockEnabled && currentConfig.appLockPin.isNotEmpty() && app.pkg in currentConfig.lockedApps) {
            PinEntryDialogFragment.newInstance(
                title = "App Locked",
                subtitle = "Enter PIN to launch ${app.label}",
                correctPin = currentConfig.appLockPin,
                pinLength = currentConfig.appLockPinLength,
                isCancelable = true,
                onSuccess = {
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

                // 6. Clock & Date loop
                launch {
                    while (isActive) {
                        val now = Date()
                        val timePattern = if (currentConfig.h24) "HH:mm" else "h:mm a"
                        val timeStr = SimpleDateFormat(timePattern, Locale.getDefault()).format(now)
                        val dateStr = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)
                        binding.mainStatusBar.setClockTime(timeStr, dateStr)
                        delay(1000)
                    }
                }

                // 7. Periodic Weather
                launch {
                    while (isActive) {
                        refreshWeather()
                        delay(15 * 60 * 1000) // 15 mins
                    }
                }
            }
        }
    }

    private fun refreshWeather() {
        lifecycleScope.launch {
            val weather = WeatherRepository.getWeather(this@MainActivity)
            currentWeather = weather
            binding.mainStatusBar.setWeatherData(weather)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
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
