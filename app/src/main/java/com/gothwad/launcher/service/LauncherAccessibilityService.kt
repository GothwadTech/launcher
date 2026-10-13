package com.gothwad.launcher.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.gothwad.launcher.Actions
import com.gothwad.launcher.GothwadApplication
import com.gothwad.launcher.R
import com.gothwad.launcher.MainActivity
import com.gothwad.launcher.data.BackgroundMediaTracker
import com.gothwad.launcher.data.ButtonMappingManager
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.LockCredential
import com.gothwad.launcher.data.LockSecurity
import com.gothwad.launcher.ui.view.AppLockOverlayView
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
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Volume repeat acceleration state (solves Android TV accessibility volume hold bug)
    @Volatile
    private var activeVolumeKeyCode: Int? = null
    private var volumeRepeatCount: Int = 0

    private val volumeRepeatRunnable = object : Runnable {
        override fun run() {
            val key = activeVolumeKeyCode ?: return
            volumeRepeatCount++
            if (volumeRepeatCount > 120) {
                // Safety cutoff after ~8-9 seconds of continuous holding
                stopVolumeRepeat()
                return
            }
            adjustVolume(key)
            // 70ms interval provides smooth, responsive volume acceleration
            mainHandler.postDelayed(this, 70L)
        }
    }

    private fun adjustVolume(keyCode: Int) {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            else -> return
        }
        try {
            audioManager.adjustSuggestedStreamVolume(
                direction,
                AudioManager.USE_DEFAULT_STREAM_TYPE,
                AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
            )
        } catch (e: Exception) {
            Log.e("LauncherA11y", "Failed to adjust volume for keyCode $keyCode", e)
        }
    }

    private fun startVolumeRepeat(keyCode: Int) {
        if (activeVolumeKeyCode == keyCode) return
        stopVolumeRepeat()
        activeVolumeKeyCode = keyCode
        volumeRepeatCount = 0
        // Wait 280ms before beginning continuous repeat acceleration (standard long-press threshold)
        mainHandler.postDelayed(volumeRepeatRunnable, 280L)
    }

    private fun stopVolumeRepeat() {
        if (activeVolumeKeyCode != null) {
            activeVolumeKeyCode = null
            volumeRepeatCount = 0
            mainHandler.removeCallbacks(volumeRepeatRunnable)
        }
    }

    // Cached button mappings from ConfigStore to ensure ZERO blocking reads on keystrokes
    @Volatile
    private var cachedButtonMap: Map<Int, String> = emptyMap()

    @Volatile
    private var cachedConfig: LauncherConfig = LauncherConfig()

    /**
     * False until the first config emission arrived. `cachedConfig` starts out as an *empty*
     * default, in which no app is locked and nothing is hidden, so enforcing from it would
     * fail open: a locked app opened in the first moments after the service binds would go
     * straight through. Enforcement is parked until the real config lands (a few ms) instead.
     */
    @Volatile
    private var configLoaded = false

    /** Foreground package seen while [configLoaded] was still false; re-checked once it flips. */
    @Volatile
    private var pendingEnforcementPkg: String? = null

    // Overlay state for system-wide app lock
    private var currentLockOverlay: AppLockOverlayView? = null
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
                val firstLoad = !configLoaded
                configLoaded = true
                if (firstLoad) {
                    // Re-run the enforcement check that had to be parked while the config was
                    // still unknown.
                    val pkg = pendingEnforcementPkg
                    pendingEnforcementPkg = null
                    if (pkg != null) {
                        mainHandler.post { checkAndEnforceAppLock(pkg) }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        stopVolumeRepeat()
        dismissLockOverlay()
        pendingEnforcementPkg = null
        configLoaded = false
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
        stopVolumeRepeat()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            // Never intercept or kick back to Home if the user is in Android TV settings or system dialogs
            if (StockLauncherDetector.isSystemOrSettingsPackage(pkg)) {
                return
            }

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

        // Fail closed: with no config yet we cannot know what is locked or hidden, so park
        // this package and decide as soon as the first emission lands (see [configLoaded]).
        if (!configLoaded) {
            pendingEnforcementPkg = pkg
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
                        title = getString(R.string.lock_title_hidden),
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
                title = getString(R.string.lock_title_app),
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

        currentLockOverlay = AppLockOverlayView(
            context = this@LauncherAccessibilityService,
            credential = credential,
            title = title,
            subtitle = getString(R.string.lock_subtitle_overlay, appName),
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

        // 2. Continuous Volume Adjustment & Hold Handling (Solves Android TV accessibility hold bug)
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (activeVolumeKeyCode != keyCode) {
                    adjustVolume(keyCode)
                    startVolumeRepeat(keyCode)
                }
                return true
            } else if (event.action == KeyEvent.ACTION_UP) {
                stopVolumeRepeat()
                return true
            } else {
                stopVolumeRepeat()
                return true
            }
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_MUTE || keyCode == KeyEvent.KEYCODE_MUTE) {
            stopVolumeRepeat()
            if (event.action == KeyEvent.ACTION_DOWN) {
                try {
                    audioManager.adjustSuggestedStreamVolume(
                        AudioManager.ADJUST_TOGGLE_MUTE,
                        AudioManager.USE_DEFAULT_STREAM_TYPE,
                        AudioManager.FLAG_SHOW_UI
                    )
                } catch (e: Exception) {
                    Log.e("LauncherA11y", "Failed to toggle mute", e)
                }
            }
            return true
        }

        // For any other key event, cancel pending volume repeats
        stopVolumeRepeat()

        // 3. Reserved Home key interception
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_UP) {
                launchHome(this)
            }
            return true // Consume the HOME key event so locked stock TV launcher cannot react
        }

        // 4. Custom Button Mapping hotkeys (Phase 5)
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

    override fun onInterrupt() {
        stopVolumeRepeat()
    }

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
        val STOCK_LAUNCHERS get() = StockLauncherDetector.STOCK_LAUNCHERS

        fun isStockTvLauncher(pkg: String): Boolean =
            StockLauncherDetector.isStockTvLauncher(pkg)

        fun isStockTvLauncher(context: Context, pkg: String): Boolean =
            StockLauncherDetector.isStockTvLauncher(context, pkg)

        /** The HOME intent this launcher answers. Shared by every recovery path. */
        fun homeIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }

        fun launchHome(context: Context) {
            context.startActivity(homeIntent(context))
        }

        /**
         * Brings HOME to the front from a *background* component: the `:watchdog` process, the
         * boot shield service, or the crash handler.
         *
         * Android 10+ blocks background activity starts, and it fails **silently** -
         * `startActivity` returns normally while the system drops the start, so the old
         * `runCatching { launchHome(...) }.isSuccess` reported success and the recovery path did
         * nothing. Handing the same intent to AlarmManager makes the system fire it, which is
         * one of the documented exemptions; the direct start is kept as the fast path for the
         * case where the launcher is already allowed to start activities.
         */
        fun launchHomeCompat(context: Context): Boolean {
            val intent = homeIntent(context)

            val scheduled = runCatching {
                val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    ?: return@runCatching false
                val pending = PendingIntent.getActivity(
                    context,
                    HOME_REQUEST_CODE,
                    intent,
                    // FLAG_IMMUTABLE only exists from API 23 and minSdk is 21.
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            PendingIntent.FLAG_IMMUTABLE
                        } else {
                            0
                        },
                )
                am.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + HOME_ALARM_DELAY_MS,
                    pending,
                )
                true
            }.getOrDefault(false)

            val direct = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)

            if (!scheduled && !direct) {
                Log.w(TAG, "Could not bring HOME to the front (background activity start blocked?)")
            }
            return scheduled || direct
        }

        suspend fun launchHomeWithRetry(context: Context, maxAttempts: Int = 5) {
            var delayMs = 150L
            for (attempt in 1..maxAttempts) {
                val success = launchHomeCompat(context)
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

        private const val TAG = "LauncherA11y"
        private const val HOME_REQUEST_CODE = 4402
        private const val HOME_ALARM_DELAY_MS = 120L
    }
}
