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
import com.gothwad.launcher.data.BackgroundMediaState
import com.gothwad.launcher.data.BackgroundMediaTracker
import com.gothwad.launcher.data.BluetoothDeviceStatus
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.NetStatus
import com.gothwad.launcher.data.ProcessHeartbeat
import com.gothwad.launcher.data.bluetoothStatusFlow
import com.gothwad.launcher.data.networkStatusFlow
import com.gothwad.launcher.databinding.ActivityMainBinding
import com.gothwad.launcher.service.NotificationManagerBridge
import com.gothwad.launcher.ui.dialogs.BackgroundMediaDialogFragment
import com.gothwad.launcher.ui.dialogs.NotificationBottomSheetFragment
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
    private var currentMediaState: BackgroundMediaState = BackgroundMediaState()

    private var clockFormatter: SimpleDateFormat? = null
    private var clockPatternInUse: String? = null
    private val dateFormatter = SimpleDateFormat("d MMM • EEE", Locale.ENGLISH)

    fun setup() {
        setupStatusBarClicks()
        observeStatusBarData()
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

            onBackgroundMediaClick = {
                BackgroundMediaDialogFragment.newInstance(
                    state = currentMediaState
                ).show(activity.supportFragmentManager, BackgroundMediaDialogFragment.TAG)
            }

            onNetworkClick = {
                Actions.openNetworkSettings(activity)
            }

            onVpnClick = {
                val pkg = getCurrentConfig().vpnApp
                if (pkg.isNotEmpty() && Actions.isInstalled(activity, pkg)) {
                    Actions.launchApp(activity, pkg)
                } else {
                    Actions.openVpnSettings(activity)
                }
            }

            onNotificationsClick = {
                NotificationBottomSheetFragment.newInstance()
                    .show(activity.supportFragmentManager, NotificationBottomSheetFragment.TAG)
            }

            onSettingsClick = {
                openSettingsDialog()
            }
        }
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
                        if (com.gothwad.launcher.ui.DensityAdapter.respectSystemFontScale != config.respectSystemFontScale) {
                            com.gothwad.launcher.ui.DensityAdapter.respectSystemFontScale =
                                config.respectSystemFontScale
                            runCatching { com.gothwad.launcher.ui.DensityAdapter.apply(activity) }
                        }

                        binding.mainStatusBar.applyConfig(config)
                        binding.mainStatusBar.visibility =
                            if (!config.showStatusBar) View.GONE else View.VISIBLE
                        binding.mainStatusBar.setVpnStatus(currentNetStatus.vpn, config.showVpnButton)
                    }
                }

                // 2. Network status flow
                launch {
                    networkStatusFlow(activity).collectLatest { net ->
                        currentNetStatus = net
                        binding.mainStatusBar.setNetStatus(net)
                        binding.mainStatusBar.setVpnStatus(net.vpn, getCurrentConfig().showVpnButton)
                    }
                }

                // 3. Bluetooth status flow
                launch {
                    bluetoothStatusFlow(activity).flowOn(Dispatchers.IO).collectLatest { bt ->
                        currentBtStatus = bt
                        binding.mainStatusBar.setBluetoothStatus(bt)
                    }
                }

                // 4. Background media flow
                launch {
                    BackgroundMediaTracker.backgroundMediaFlow(activity).collectLatest { media ->
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
                    var heartbeatTick = 0
                    while (isActive) {
                        if (heartbeatTick++ % 5 == 0) {
                            ProcessHeartbeat.touch(activity, foreground = true)
                        }
                        val now = Date()
                        val cfg = getCurrentConfig()
                        val timePattern = if (cfg.h24) "HH:mm" else "hh:mm a"
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
