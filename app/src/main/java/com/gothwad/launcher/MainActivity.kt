package com.gothwad.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.AppRepository
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.ui.AppLockGate
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.LockSecurity
import com.gothwad.launcher.data.SelfHealGuard
import com.gothwad.launcher.databinding.ActivityMainBinding
import com.gothwad.launcher.ui.MainStatusBarController
import com.gothwad.launcher.ui.tv.TvLauncherFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var currentConfig: LauncherConfig = LauncherConfig()
    private var rescanJob: Job? = null
    private var statusBarController: MainStatusBarController? = null

    /** Written from an IO coroutine, read from the UI thread - keep it volatile. */
    @Volatile
    private var allApps: List<AppEntry> = emptyList()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Uninstall/update: no PM probe needed - the package is already gone or being
            // replaced, and the rescan is what rebuilds the grid anyway.
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED) {
                scheduleAppsRescan()
                return
            }
            val pkg = intent.data?.schemeSpecificPart ?: return
            val launchable =
                packageManager.getLeanbackLaunchIntentForPackage(pkg) != null ||
                    packageManager.getLaunchIntentForPackage(pkg) != null
            if (launchable) {
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

    /**
     * Rescans the installed apps and seeds the remote-button defaults.
     *
     * This used to also write an auto-category assignment for every newly installed package
     * (`config.sections` + `config.knownApps`) - a JSON encode and a DataStore write on every
     * scan, for a category grid that the home screen no longer renders and no settings screen
     * exposes. That machinery is gone; the grid is a single flat list ordered by
     * `config.order`.
     */
    private fun refreshAppsList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val apps = AppRepository.scan(this@MainActivity)
            allApps = apps
            val store = ConfigStore(this@MainActivity)
            com.gothwad.launcher.data.ButtonMappingManager.seedDefaultMappings(
                store,
                apps.map { it.pkg }.toSet(),
            )
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // NOTE: there is no locale override here any more. The old code read a
        // "locale"/"lang" SharedPreferences entry that nothing ever wrote, so it was dead
        // code pretending to be an i18n feature. Real localization needs translated
        // string resources first (see docs/ISSUE_LOG.md, issue #19).
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

        // Night-light overlay follows the config flow for the activity's whole life.
        lifecycleScope.launch {
            ConfigStore(this@MainActivity).flow.collect { cfg ->
                binding.viewNightLight.alpha =
                    if (cfg.nightLight) cfg.nightLightStrength.coerceIn(10, 80) / 100f * 0.45f
                    else 0f
            }
        }

        setupA11yWatchdogReceiver()
        statusBarController = MainStatusBarController(
            activity = this,
            binding = binding,
            getCurrentConfig = { currentConfig },
            onConfigChanged = { currentConfig = it },
            getAllApps = { allApps },
            onAppLaunch = { handleAppLaunch(it) }
        ).also { it.setup() }
        refreshAppsList()
    }

    private val bluetoothPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { /* status is refreshed by the BT flow */ }

    /** Asked at most once per process; a denial is respected and never re-prompted. */
    private var bluetoothPermissionRequested = false

    /**
     * Requests `BLUETOOTH_CONNECT` on Android 12+.
     *
     * The permission is declared in the manifest but is a *runtime* permission there, and
     * nothing ever asked for it - so `bluetoothStatusFlow` could only report
     * `connected = false, isRemote = false` ("unknown") and the dashboard's remote name and
     * battery readout never worked on a modern TV. Called when the Quick Dashboard opens.
     */
    fun ensureBluetoothPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (bluetoothPermissionRequested) return
        val permission = Manifest.permission.BLUETOOTH_CONNECT
        if (ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bluetoothPermissionRequested = true
        runCatching { bluetoothPermissionLauncher.launch(permission) }
    }

    private fun setupA11yWatchdogReceiver() {
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
        statusBarController?.updateA11yStatus()
    }

    private fun handleAppLaunch(app: AppEntry) {
        // App lock & hidden vault gate
        AppLockGate.evaluate(
            context = this,
            fragmentManager = supportFragmentManager,
            app = app,
            config = currentConfig,
        ) {
            Actions.launchApp(this, app.pkg)
            // App-open transition style (0 none / 1 fade / 2 zoom / 3 slide).
            if (!currentConfig.reduceMotion && currentConfig.perfProfile != 0) {
                @Suppress("DEPRECATION")
                when (currentConfig.openAnimation.coerceIn(0, 3)) {
                    1 -> overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    2 -> overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    3 -> overridePendingTransition(
                        android.R.anim.slide_in_left, android.R.anim.slide_out_right
                    )
                }
            }
        }
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
