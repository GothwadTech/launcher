package com.gothwad.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.gothwad.launcher.service.LauncherAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lightweight, in-memory LRU tracker of packages launched by the user.
 * Provides automated background memory optimization without blocking the main thread.
 *
 * Specifically designed for low-RAM (2GB) STB/TV devices where background apps
 * can cause OOM crashes of the home launcher.
 */
object AppLaunchTracker {
    private const val TAG = "AppLaunchTracker"

    // Hardcoded protected packages that should never be killed during background trims
    private val PROTECTED_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.tv.settings",
        "com.android.systemui",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller"
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Timestamp-ordered list of launched packages. The most recent is at index 0.
    private val lock = Any()
    private val lruList = mutableListOf<String>()

    /**
     * Records an app launch. Moves [pkg] to the top (index 0) of the LRU list.
     */
    fun onAppLaunched(pkg: String) {
        if (pkg.isBlank()) return
        synchronized(lock) {
            lruList.remove(pkg)
            lruList.add(0, pkg)
            // Cap history size to prevent unnecessary memory footprint
            if (lruList.size > 30) {
                lruList.removeAt(lruList.lastIndex)
            }
        }
    }

    /**
     * Executes memory trimming on a background thread.
     *
     * @param context Application context
     * @param isCritical If true (e.g. TRIM_MEMORY_RUNNING_CRITICAL or onLowMemory),
     *                   kills all background processes except the single current foreground package.
     *                   If false (e.g. TRIM_MEMORY_RUNNING_LOW), preserves the single most recent app
     *                   and protected packages.
     */
    /**
     * Set from the config flow (MainActivity). OFF by default: the trim used to kill the
     * user's other background apps on every `onTrimMemory(RUNNING_LOW)`, which on a TV
     * means their music or a paused stream dies for a few MB of headroom (issue #34).
     */
    @Volatile
    var aggressiveTrimEnabled: Boolean = false

    fun performMemoryTrim(context: Context, isCritical: Boolean) {
        if (!aggressiveTrimEnabled) {
            Log.d(TAG, "Memory trim skipped (aggressive trim disabled in settings)")
            return
        }
        scope.launch {
            try {
                val myPkg = context.packageName
                val targetsToKill = mutableListOf<String>()

                synchronized(lock) {
                    if (lruList.isEmpty()) return@synchronized

                    if (isCritical) {
                        // Drop everything except the single most recently launched app
                        // (which is presumably in the foreground) and self
                        val foregroundPkg = lruList.firstOrNull()
                        for (pkg in lruList) {
                            if (pkg != myPkg && pkg != foregroundPkg) {
                                targetsToKill.add(pkg)
                            }
                        }
                    } else {
                        // TRIM_MEMORY_RUNNING_LOW:
                        // Keep self, the single most-recently-launched app, and protected packages
                        val mostRecentPkg = lruList.firstOrNull()
                        for (i in 1 until lruList.size) {
                            val pkg = lruList[i]
                            if (pkg != myPkg &&
                                pkg != mostRecentPkg &&
                                pkg !in PROTECTED_PACKAGES &&
                                !LauncherAccessibilityService.isStockTvLauncher(pkg)
                            ) {
                                targetsToKill.add(pkg)
                            }
                        }
                    }
                }

                if (targetsToKill.isNotEmpty()) {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    if (am != null) {
                        for (pkg in targetsToKill) {
                            runCatching {
                                am.killBackgroundProcesses(pkg)
                            }
                        }
                        Log.d(TAG, "Trimmed background apps (critical=$isCritical): $targetsToKill")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Memory trim execution error", e)
            }
        }
    }
}
