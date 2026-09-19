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
import com.gothwad.launcher.MainActivity
import com.gothwad.launcher.data.BackgroundMediaTracker
import com.gothwad.launcher.data.ButtonMappingManager
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        dismissLockOverlay()
        serviceScope.cancel()
        super.onDestroy()
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

            if (isStockTvLauncher(pkg) && pkg != packageName) {
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

        val isAppLocked = config.appLock.enabled &&
            config.appLock.value.isNotEmpty() &&
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
            showAppLockOverlay(pkg, config)
        }
    }

    private fun showAppLockOverlay(pkg: String, config: LauncherConfig) {
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
            context = applicationContext,
            credential = config.appLock,
            title = "App Locked",
            subtitle = "Enter credential to open $appName",
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
        ).also {
            it.show()
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
        val STOCK_LAUNCHERS = setOf(
            // Google TV / Android TV
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.tungsten.setupwraith",
            // JioFiber / Jio STB packages
            "com.jio.media.stblauncher",
            "com.jio.media.jiohome",
            "com.jio.media.ondemand",
            "com.jio.jiotv",
            "com.ril.jio.stb",
            // Airtel Xstream STB packages
            "com.airtel.tv",
            "com.airtel.xstream",
            "com.airtel.android.tv",
            "com.airtel.smartbox",
            "tv.airtel.smartbox.launcher",
            "com.airtel.tv.launcher",
            // Tata Play Binge / Dish SMRT / D2H / Asianet / Hathway
            "com.tatasky.binge",
            "com.tatasky.stb",
            "com.dishtv.smrt",
            "com.d2h.stream",
            "com.nes.tvlauncher",
            "com.nes.operator",
            "com.sdmc.launcher",
            "com.geniatech.launcher",
            "com.amlogic.tvlauncher",
            "com.realtek.tvlauncher",
            // Fire TV & OEM TV Launchers
            "com.amazon.tv.launcher",
            "com.amazon.firehomestarter",
            "com.xiaomi.mitv.tvhome",
            "com.mitv.tvhome",
            "com.tcl.tvplayer",
            "com.hisense.tv.launcher",
            "com.droidlogic.tv.launcher"
        )

        fun isStockTvLauncher(pkg: String): Boolean =
            STOCK_LAUNCHERS.any { pkg.startsWith(it, ignoreCase = true) }

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

        @Volatile
        var instance: LauncherAccessibilityService? = null
            internal set

        /**
         * Global action to return to Home immediately if foreground is displaying any external app
         */
        fun forceReturnHome(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }
}
