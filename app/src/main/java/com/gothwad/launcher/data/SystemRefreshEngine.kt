package com.gothwad.launcher.data

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.gothwad.launcher.MainActivity
import com.gothwad.launcher.service.LauncherAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * System-level Fast Refresh Engine (Windows F5 style instant desktop refresh).
 *
 * Characteristics:
 * 1. Instant 1-second execution with Windows-style subtle UI blink (no blackout, no launcher crash).
 * 2. Remains solidly on Gothwad Launcher (never drops to OEM/stock TV launcher).
 * 3. Kills background running apps (Termux, Chrome, Youtube, etc.) via ActivityManager.killBackgroundProcesses.
 * 4. Silences background media and terminates rogue audio.
 * 5. Clears unlocked app session tokens and resets memory caches.
 * 6. Triggers immediate GC and rescans all apps/widgets.
 */
object SystemRefreshEngine {
    private const val TAG = "SystemRefreshEngine"

    // Critical system packages that must never be terminated
    private val PROTECTED_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.tv.settings",
        "com.android.systemui",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.inputmethod.latin",
        "com.google.android.tts"
    )

    fun performSystemRefresh(context: Context) {
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())

        // 1. Windows-style Fast Visual Blink on the active MainActivity
        if (context is MainActivity) {
            context.triggerInstantVisualBlink()
        }

        // 2. Ensure launcher is firmly brought to foreground / forced Home
        LauncherAccessibilityService.forceReturnHome()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                // 3. Terminate background audio / media
                BackgroundMediaTracker.silenceAudio(appContext)

                // 4. Force kill background running app processes (Termux, Chrome, browsers, players, etc.)
                val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val pm = appContext.packageManager
                val myPkg = appContext.packageName

                if (am != null) {
                    // Method A: Query running app processes
                    runCatching {
                        val runningProcesses = am.runningAppProcesses
                        if (runningProcesses != null) {
                            for (proc in runningProcesses) {
                                val pkgList = proc.pkgList ?: continue
                                for (pkg in pkgList) {
                                    if (pkg != myPkg &&
                                        pkg !in PROTECTED_PACKAGES &&
                                        !LauncherAccessibilityService.isStockTvLauncher(pkg)
                                    ) {
                                        am.killBackgroundProcesses(pkg)
                                    }
                                }
                            }
                        }
                    }

                    // Method B: Exhaustive kill on all installed non-system packages
                    runCatching {
                        val installedPkgs = pm.getInstalledPackages(0).map { it.packageName }
                        for (pkg in installedPkgs) {
                            if (pkg != myPkg &&
                                pkg !in PROTECTED_PACKAGES &&
                                !LauncherAccessibilityService.isStockTvLauncher(pkg)
                            ) {
                                am.killBackgroundProcesses(pkg)
                            }
                        }
                    }
                }

                // 5. Invalidate temporary session states (lock tokens, temporary states)
                LauncherAccessibilityService.unlockedPackagesSession.clear()

                // 6. Clear image & glide/disk caches in launcher
                runCatching {
                    appContext.cacheDir?.deleteRecursively()
                    appContext.codeCacheDir?.deleteRecursively()
                }

                // 7. Force garbage collection to free RAM immediately
                System.gc()
                Runtime.getRuntime().gc()

                // 8. Refresh launcher apps and state
                mainHandler.post {
                    if (context is MainActivity) {
                        context.notifyFragmentRescan()
                    }
                    Toast.makeText(appContext, "⚡ System Refreshed • Background apps killed & RAM freed", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Throwable) {
                Log.e(TAG, "Error performing system refresh", e)
            }
        }
    }
}
