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
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Bumped whenever a package is installed/removed so the app grid rescans. */
    var rescanTick: Int = 0
        private set

    private var currentConfig: LauncherConfig = LauncherConfig()
    private var rescanJob: Job? = null
    private var statusBarController: MainStatusBarController? = null

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

        setupA11yRecoveryBanner()
        setupNavigation()
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

    private fun handleAppLaunch(app: AppEntry) {
        // App lock & hidden vault gate
        AppLockGate.evaluate(
            fragmentManager = supportFragmentManager,
            app = app,
            config = currentConfig,
        ) {
            Actions.launchApp(this, app.pkg)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
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
