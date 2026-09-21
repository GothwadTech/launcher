package com.gothwad.launcher.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.gothwad.launcher.Actions
import com.gothwad.launcher.GothwadApplication
import com.gothwad.launcher.MainActivity
import com.gothwad.launcher.data.BackgroundMediaTracker
import com.gothwad.launcher.data.ButtonMappingManager
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.LockCredential
import com.gothwad.launcher.data.LockSecurity
import com.gothwad.launcher.ui.view.SystemLockOverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Accessibility Service to handle Home key capture, OEM launcher overrides,
 * Remote button hotkey mapping, Boot Ad / Stock Launcher suppression on Jio, Airtel, Google TV, and locked STBs,
 * and Authoritative, Unbypassable App Lock Enforcement (Phase 3).
 */
class LauncherAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Cached button mappings from ConfigStore to ensure ZERO blocking reads on keystrokes
    @Volatile
    private var cachedButtonMap: Map<Int, String> = emptyMap()

    @Volatile
    private var cachedConfig: LauncherConfig = LauncherConfig()

    // Overlay state for system-wide app lock
    private var currentLockOverlay: SystemLockOverlayView? = null
    @Volatile
    private var pendingLockedPkg: String? = null

    // Track the package currently in foreground
    @Volatile
    private var lastForegroundPkg: String? = null

    override fun onCreate() {
        super.onCreate()
        val store = ConfigStore(applicationContext)
        serviceScope.launch {
            val installedPackages = runCatching {
                packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
            }.getOrDefault(emptySet())
            ButtonMappingManager.seedDefaultMappings(store, installedPackages)

            store.flow.collectLatest { cfg ->
                cachedButtonMap = cfg.buttonMap
                cachedConfig = cfg
            }
        }
    }

    override fun onDestroy() {
        dismissLockOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            // If user left the previously unlocked package, invalidate its session
            val previousPkg = lastForegroundPkg
            if (previousPkg != null && previousPkg != pkg && previousPkg != packageName) {
                unlockedPackagesSession.remove(previousPkg)
            }
            lastForegroundPkg = pkg
            lastKnownForegroundPackage = pkg

            if (isStockTvLauncher(this, pkg) && pkg != packageName) {
                // The OEM/Jio/Airtel launcher or boot ad was brought to foreground; return to Gothwad Launcher
                BackgroundMediaTracker.silenceAudio(this)
                launchHome(this)
                return
            }

            // Phase 3: Authoritative, Unbypassable App Lock Enforcement
            checkAndEnforceAppLock(pkg)
        }
    }

    /**
     * Checks if the foreground package requires app lock, regardless of whether it was opened
     * from our launcher, Android Settings, notifications, recents switcher, or external intents.
     */
    private fun checkAndEnforceAppLock(pkg: String) {
        val config = cachedConfig
        if (pkg == packageName) {
            dismissLockOverlay()
            return
        }

        // Hidden apps are a vault: they must not be reachable from another launcher,
        // Android Settings, notifications or the recents switcher either. We only allow
        // them once this process has explicitly unlocked them (vault launch).
        val isHidden = pkg in config.hidden
        val hiddenAllowed = !isHidden || unlockedPackagesSession.contains(pkg)

        if (!hiddenAllowed) {
            // The vault has its own credential (2nd layer) and its own throttling scope.
            // With one configured we show the unbypassable overlay; otherwise we simply
            // send the user back Home instead of revealing the app.
            if (config.hiddenAppsLock.ready && !unlockedPackagesSession.contains(pkg)) {
                mainHandler.post {
                    showAppLockOverlay(
                        pkg = pkg,
                        credential = config.hiddenAppsLock,
                        scope = LockSecurity.SCOPE_VAULT,
                        title = "Hidden App",
                    )
                }
            } else {
                mainHandler.post { launchHome(this) }
            }
            return
        }

        val isAppLocked = config.appLock.enabled &&
            config.appLock.ready &&
            pkg in config.lockedApps

        if (!isAppLocked) {
            // Not a locked app
            if (pendingLockedPkg == pkg) {
                dismissLockOverlay()
            }
            return
        }

        // Check if package is already unlocked in current foreground session
        if (unlockedPackagesSession.contains(pkg)) {
            return
        }

        // App is locked and not unlocked in current session: Show unbypassable overlay
        mainHandler.post {
            showAppLockOverlay(
                pkg = pkg,
                credential = config.appLock,
                scope = LockSecurity.SCOPE_APP,
                title = "App Locked",
            )
        }
    }

    private fun showAppLockOverlay(
        pkg: String,
        credential: LockCredential,
        scope: String,
        title: String,
    ) {
        if (currentLockOverlay != null && pendingLockedPkg == pkg) {
            return // Overlay already showing for this package
        }

        dismissLockOverlay()

        val appName = runCatching {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(pkg)

        pendingLockedPkg = pkg

        currentLockOverlay = SystemLockOverlayView(
            context = this@LauncherAccessibilityService,
            credential = credential,
            title = title,
            subtitle = "Enter credential to open $appName",
            lockScope = scope,
            onSuccess = {
                // Mark package as unlocked for current session
                unlockedPackagesSession.add(pkg)
                pendingLockedPkg = null
                currentLockOverlay = null
            },
            onDismissOrBack = {
                // On dismiss, back key, or cancel, escape to Home to prevent revealing the underlying app
                pendingLockedPkg = null
                currentLockOverlay = null
                launchHome(this@LauncherAccessibilityService)
            }
        ).also { overlay ->
            // Fail SECURE: if the overlay window could not be attached (some firmware
            // refuses TYPE_ACCESSIBILITY_OVERLAY and SYSTEM_ALERT_WINDOW is not granted),
            // send the user Home instead of leaving the locked app on screen.
            val attached = overlay.show()
            if (!attached) {
                Log.e("LauncherA11y", "Lock overlay could not attach - failing secure (going Home)")
                pendingLockedPkg = null
                currentLockOverlay = null
                launchHome(this@LauncherAccessibilityService)
            }
        }
    }

    private fun dismissLockOverlay() {
        mainHandler.post {
            currentLockOverlay?.dismiss()
            currentLockOverlay = null
            pendingLockedPkg = null
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        // 0. Authoritative DEVICE LOCK guard.
        //    While the device lock is active and has not been satisfied in this process,
        //    nothing may be launched. Without this guard a mapped remote hotkey
        //    (red/green/yellow/blue buttons, GUIDE, TV ... which are auto-seeded with
        //    YouTube/Netflix/Prime/Hotstar on first run) or the capture/listen mode would
        //    start an app straight past the lock screen.
        val cfg = cachedConfig
        if (cfg.deviceLock.enabled && cfg.deviceLock.ready &&
            !GothwadApplication.hasUnlockedDeviceThisProcess
        ) {
            if (keyCode == KeyEvent.KEYCODE_HOME && event.action == KeyEvent.ACTION_UP) {
                launchHome(this)
            }
            // Let BACK through so the lock screen itself stays dismissible/handled by the
            // in-activity lock UI; swallow everything else.
            return keyCode != KeyEvent.KEYCODE_BACK
        }

        // 1. If ButtonMappingManager is currently in "listen-mode" for learning a new button,
        // capture the raw key event (on ACTION_UP to prevent double captures) and notify UI
        if (ButtonMappingManager.isListening()) {
            if (event.action == KeyEvent.ACTION_UP) {
                val captured = ButtonMappingManager.onKeyCaptured(keyCode)
                if (captured) return true
            } else if (event.action == KeyEvent.ACTION_DOWN) {
                // Consume DOWN event as well during capture mode
                return true
            }
        }

        // 2. Reserved Home key interception
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_UP) {
                launchHome(this)
            }
            return true // Consume the HOME key event so locked stock TV launcher cannot react
        }

        // 3. Custom Button Mapping hotkeys (Phase 5)
        val mappedPkg = cachedButtonMap[keyCode]
        if (!mappedPkg.isNullOrEmpty()) {
            if (event.action == KeyEvent.ACTION_UP) {
                Log.i("LauncherA11y", "Intercepted mapped hotkey $keyCode -> launching $mappedPkg")
                Actions.launchApp(this, mappedPkg)
            }
            return true // Consume mapped hotkey event so underlying app doesn't receive it
        }

        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {}

    companion object {
        /**
         * Last package seen by TYPE_WINDOW_STATE_CHANGED, published for other components
         * that cannot read UsageStats (e.g. the boot-ad shield). Null means "unknown",
         * never "the stock launcher is in front".
         */
        @Volatile
        var lastKnownForegroundPackage: String? = null

        /**
         * Curated list of *home-screen* packages (stock / operator launchers and boot-ad
         * shims) that this launcher replaces.
         *
         * These are matched EXACTLY (see [isStockTvLauncher]) - never by prefix. The list
         * used to contain streaming apps such as `com.jio.jiotv`, `com.jio.media.ondemand`,
         * `com.airtel.tv` and `com.tatasky.binge`, and the prefix match meant that opening
         * JioTV / Airtel Xstream / Tata Play Binge from this launcher was instantly
         * "corrected" back to Home: those apps could never be opened.
         */
        val STOCK_LAUNCHERS = setOf(
            // Google TV / Android TV
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.tungsten.setupwraith",
            // JioFiber / Jio STB launchers
            "com.jio.media.stblauncher",
            "com.jio.media.jiohome",
            "com.ril.jio.stb",
            // Airtel Xstream STB launchers
            "com.airtel.smartbox",
            "tv.airtel.smartbox.launcher",
            "com.airtel.tv.launcher",
            // Tata Play / Dish / D2H / regional operator launchers
            "com.tatasky.stb",
            "com.dishtv.smrt",
            "com.d2h.stream",
            "com.nes.tvlauncher",
            "com.sdmc.launcher",
            // Chipset / OEM TV launchers
            "com.geniatech.launcher",
            "com.amlogic.tvlauncher",
            "com.realtek.tvlauncher",
            "com.amazon.tv.launcher",
            "com.amazon.firehomestarter",
            "com.xiaomi.mitv.tvhome",
            "com.mitv.tvhome",
            "com.hisense.tv.launcher",
            "com.droidlogic.tv.launcher"
        )

        /** Cache of packages that currently hold the system HOME role (checked per event is too costly). */
        @Volatile
        private var homeHandlerCache: Pair<Long, Set<String>> = 0L to emptySet()

        /** Packages that resolve the CATEGORY_HOME intent right now (60s cache). */
        private fun homeHandlerPackages(context: Context): Set<String> {
            val (stamp, cached) = homeHandlerCache
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - stamp < 60_000L && cached.isNotEmpty()) return cached

            val packages = runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                context.packageManager.queryIntentActivities(intent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .toSet()
            }.getOrDefault(emptySet())

            homeHandlerCache = now to packages
            return packages
        }

        /** Exact-match check against the curated list (kept for callers without a Context). */
        fun isStockTvLauncher(pkg: String): Boolean =
            STOCK_LAUNCHERS.any { it.equals(pkg, ignoreCase = true) }

        /**
         * As above, but also treats any other installed package that currently handles the
         * HOME intent as a stock launcher. This catches OEM launchers that are not in the
         * curated list without ever mis-classifying a normal app as a launcher.
         */
        fun isStockTvLauncher(context: Context, pkg: String): Boolean {
            if (pkg.equals(context.packageName, ignoreCase = true)) return false
            if (isStockTvLauncher(pkg)) return true
            return homeHandlerPackages(context).any { it.equals(pkg, ignoreCase = true) }
        }

        fun launchHome(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            context.startActivity(intent)
        }

        suspend fun launchHomeWithRetry(context: Context, maxAttempts: Int = 5) {
            var delayMs = 150L
            for (attempt in 1..maxAttempts) {
                val success = runCatching {
                    launchHome(context)
                }.isSuccess
                if (success) {
                    if (attempt > 1) {
                        android.util.Log.i("LauncherA11y", "launchHome succeeded on retry attempt $attempt")
                    }
                    return
                }
                kotlinx.coroutines.delay(delayMs)
                delayMs *= 2 // Exponential backoff (150ms, 300ms, 600ms, 1200ms, 2400ms)
            }
        }

        fun isEnabled(context: Context): Boolean {
            val expectedComponent = "${context.packageName}/${LauncherAccessibilityService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.split(':').any {
                it.equals(expectedComponent, ignoreCase = true) ||
                    (it.contains(context.packageName, ignoreCase = true) &&
                        it.contains("LauncherAccessibilityService", ignoreCase = true))
            }
        }

        /** In-memory set of unlocked packages for current foreground session */
        val unlockedPackagesSession: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    }
}
