package com.gothwad.launcher.ui

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.gothwad.launcher.Actions
import com.gothwad.launcher.MainActivity
import com.gothwad.launcher.R
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.BluetoothDeviceStatus
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.NetStatus
import com.gothwad.launcher.data.ProcessHeartbeat
import com.gothwad.launcher.data.UI_SCALES
import com.gothwad.launcher.data.bluetoothStatusFlow
import com.gothwad.launcher.data.networkStatusFlow
import com.gothwad.launcher.databinding.ActivityMainBinding
import com.gothwad.launcher.service.LauncherAccessibilityService
import com.gothwad.launcher.service.NotificationManagerBridge
import com.gothwad.launcher.service.StockLauncherDetector
import com.gothwad.launcher.ui.dialogs.NotificationBottomSheetFragment
import com.gothwad.launcher.ui.dialogs.QuickDashboardDialogFragment
import com.gothwad.launcher.ui.dialogs.SearchDialogFragment
import com.gothwad.launcher.ui.dialogs.SettingsBottomSheetFragment
import com.gothwad.launcher.ui.dialogs.SetupWizardDialogFragment
import com.gothwad.launcher.ui.tv.TvLauncherFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainStatusBarController(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val getCurrentConfig: () -> LauncherConfig,
    private val onConfigChanged: (LauncherConfig) -> Unit,
    private val getAllApps: () -> List<AppEntry>,
    private val onAppLaunch: (AppEntry) -> Unit
) {

    private var currentNetStatus: NetStatus = NetStatus()
    private var currentBtStatus: BluetoothDeviceStatus = BluetoothDeviceStatus()

    private var clockFormatter: SimpleDateFormat? = null
    private var clockPatternInUse: String? = null
    private val dateFormatter = SimpleDateFormat("d MMM yyyy • EEE", Locale.ENGLISH)

    fun setup() {
        setupStatusBarClicks()
        observeStatusBarData()
        updateA11yStatus()
    }

    private fun setupStatusBarClicks() {
        binding.mainStatusBar.apply {
            onHomeClick = {
                val navHost = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                val currentFrag = navHost?.childFragmentManager?.fragments?.firstOrNull()
                if (currentFrag is TvLauncherFragment) {
                    currentFrag.scrollToTop()
                }
            }

            onSearchClick = {
                SearchDialogFragment.newInstance(
                    apps = getAllApps(),
                    config = getCurrentConfig(),
                    onLaunch = { app -> onAppLaunch(app) }
                ).show(activity.supportFragmentManager, SearchDialogFragment.TAG)
            }

            onBluetoothClick = {
                Actions.openBluetoothSettings(activity)
            }

            onNetworkClick = {
                Actions.openNetworkSettings(activity)
            }

            onA11yAlertClick = {
                Actions.toast(activity, activity.getString(R.string.a11y_alert_tooltip))
                Actions.openAccessibilitySettings(activity)
            }

            onNotificationsClick = {
                NotificationBottomSheetFragment.newInstance()
                    .show(activity.supportFragmentManager, NotificationBottomSheetFragment.TAG)
            }

            onSettingsClick = {
                openSettingsDialog()
            }

            onDashboardClick = {
                showQuickDashboard()
            }
        }
    }

    /**
     * One-panel overview: network, Bluetooth remote and volume.
     *
     * The panel already existed but had no entry point anywhere in the UI, so the feature the
     * README advertises was unreachable. The clock cluster opens it.
     */
    private fun showQuickDashboard() {
        // Bluetooth details (remote name, battery) need BLUETOOTH_CONNECT on Android 12+;
        // ask now, at the moment the user actually looks at that data.
        activity.ensureBluetoothPermission()
        QuickDashboardDialogFragment.newInstance(
            net = currentNetStatus,
            bt = currentBtStatus,
            onOpenSettings = { openSettingsDialog() },
        ).show(activity.supportFragmentManager, QuickDashboardDialogFragment.TAG)
    }

    fun updateA11yStatus() {
        val isA11yEnabled = LauncherAccessibilityService.isEnabled(activity)
        binding.mainStatusBar.setA11yAlertVisible(!isA11yEnabled)
    }

    private fun openSettingsDialog() {
        SettingsBottomSheetFragment.newInstance(
            config = getCurrentConfig(),
            apps = getAllApps(),
            onWallpaperChanged = {
                val navHostFragment = activity.supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
                if (currentFragment is TvLauncherFragment) {
                    currentFragment.applyWallpaper()
                }
            },
            onRerunWizard = { showSetupWizard() }
        ).show(activity.supportFragmentManager, SettingsBottomSheetFragment.TAG)
    }

    private fun showSetupWizard() {
        SetupWizardDialogFragment.newInstance {
            activity.lifecycleScope.launch {
                ConfigStore(activity).update { it.copy(setupDone = true) }
            }
        }.show(activity.supportFragmentManager, SetupWizardDialogFragment.TAG)
    }

    /**
     * Pushes the persisted UI-scale + font-scale policy into [DensityAdapter].
     *
     * `LauncherConfig` is the source of truth; the adapter keeps its own tiny prefs file so
     * the very first frame after a cold start is already correct. When the two disagree the
     * activity is recreated once, because density is baked into views at inflate time and
     * already-inflated views would otherwise keep their old pixel sizes.
     */
    private fun applyDensityPolicy(config: LauncherConfig) {
        val scale = UI_SCALES.getOrElse(config.uiScale.coerceIn(0, UI_SCALES.size - 1)) { 1.0f }
        val scaleChanged = DensityAdapter.setUiScale(activity, scale)
        val fontChanged = DensityAdapter.setRespectSystemFontScale(
            activity,
            config.respectSystemFontScale,
        )
        if (!scaleChanged && !fontChanged) return
        runCatching { DensityAdapter.apply(activity) }
        if (scaleChanged) {
            runCatching { activity.recreate() }
        }
    }

    /**
     * Tells the `:watchdog` process whether it may pull the launcher to the foreground.
     *
     * The watchdog cannot read the DataStore itself (Preferences DataStore is single-process and
     * two processes on one file can corrupt it), so the policy is mirrored into a tiny file in
     * device-protected storage - see [ProcessHeartbeat.setRelaunchAllowed].
     */
    private fun publishWatchdogPolicy(config: LauncherConfig) {
        val allowed = config.launchOnBoot ||
            StockLauncherDetector.isDefaultHomeApp(activity)
        ProcessHeartbeat.setRelaunchAllowed(activity, allowed)
    }

    private fun observeStatusBarData() {
        val configStore = ConfigStore(activity)

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. Config flow
                launch {
                    configStore.flow.collectLatest { config ->
                        onConfigChanged(config)

                        com.gothwad.launcher.data.AppLaunchTracker.aggressiveTrimEnabled =
                            config.aggressiveMemoryTrim
                        applyDensityPolicy(config)
                        publishWatchdogPolicy(config)

                        binding.mainStatusBar.applyConfig(config)
                        binding.mainStatusBar.visibility =
                            if (!config.showStatusBar) View.GONE else View.VISIBLE
                    }
                }

                // 2. Network status flow
                launch {
                    networkStatusFlow(activity).collectLatest { net ->
                        currentNetStatus = net
                        binding.mainStatusBar.setNetStatus(net)
                    }
                }

                // 3. Bluetooth status flow
                launch {
                    bluetoothStatusFlow(activity).flowOn(Dispatchers.IO).collectLatest { bt ->
                        currentBtStatus = bt
                        binding.mainStatusBar.setBluetoothStatus(bt)
                    }
                }

                // 4. Notifications flow
                launch {
                    NotificationManagerBridge.notifications.collectLatest { notifs ->
                        val hasPermission = NotificationManagerBridge.isServiceConnected.value
                        binding.mainStatusBar.setNotificationCount(notifs.size, hasPermission)
                    }
                }

                // 5. Clock & Date loop
                launch {
                    var heartbeatTick = 0
                    while (isActive) {
                        if (heartbeatTick++ % 5 == 0) {
                            ProcessHeartbeat.touch(activity, foreground = true)
                        }
                        val now = Date()
                        val cfg = getCurrentConfig()
                        val timePattern = if (cfg.h24) "HH:mm:ss" else "hh:mm:ss a"
                        if (clockPatternInUse != timePattern || clockFormatter == null) {
                            clockFormatter = SimpleDateFormat(timePattern, Locale.ENGLISH)
                            clockPatternInUse = timePattern
                        }
                        val dateFormatted = dateFormatter.format(now)
                        val timeFormatted = clockFormatter!!.format(now)
                        binding.mainStatusBar.setClockTime("$dateFormatted • $timeFormatted")
                        delay(1000)
                    }
                }
            }
        }
    }
}
