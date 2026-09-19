package com.gothwad.launcher.data

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.gothwad.launcher.MainActivity
import com.gothwad.launcher.service.LauncherAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * System-level Deep Refresh & Instant Reboot Simulation Engine.
 *
 * In 1-2 seconds, this performs:
 * 1. Audio silencing & background media termination.
 * 2. Background task & cached process termination across all installed non-essential apps.
 * 3. Cache clearing of temporary app images/drawables and memory GC.
 * 4. Scheduling an instant cold relaunch of Gothwad Launcher via AlarmManager (or clean restart).
 * 5. Clean process termination of the launcher, delivering a pristine post-boot state with freed RAM.
 */
object SystemRefreshEngine {
    private const val TAG = "SystemRefreshEngine"
    private const val RESTART_REQUEST_CODE = 9988

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

        // Provide immediate visual feedback
        Toast.makeText(appContext, "⚡ System Refreshing… Freeing RAM & background apps", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                // 1. Terminate audio playback
                BackgroundMediaTracker.silenceAudio(appContext)

                // 2. Kill all non-system background apps and cached tasks
                val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val pm = appContext.packageManager
                val myPkg = appContext.packageName

                if (am != null) {
                    val installedPkgs = runCatching {
                        pm.getInstalledPackages(0).map { it.packageName }
                    }.getOrDefault(emptyList())

                    for (pkg in installedPkgs) {
                        if (pkg != myPkg &&
                            pkg !in PROTECTED_PACKAGES &&
                            !LauncherAccessibilityService.isStockTvLauncher(pkg)
                        ) {
                            runCatching {
                                am.killBackgroundProcesses(pkg)
                            }
                        }
                    }
                }

                // 3. Clear application memory caches
                runCatching {
                    appContext.cacheDir?.deleteRecursively()
                    appContext.codeCacheDir?.deleteRecursively()
                }

                // 4. Invalidate temporary session states
                LauncherAccessibilityService.unlockedPackagesSession.clear()

                // 5. Force garbage collection
                System.gc()
                Runtime.getRuntime().gc()

                // 6. Schedule instant cold relaunch (within 350ms) and restart process
                mainHandler.postDelayed({
                    scheduleRelaunchAndExit(appContext)
                }, 350L)

            } catch (e: Throwable) {
                Log.e(TAG, "Error performing system refresh", e)
                mainHandler.post {
                    scheduleRelaunchAndExit(appContext)
                }
            }
        }
    }

    private fun scheduleRelaunchAndExit(context: Context) {
        runCatching {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                RESTART_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null) {
                val triggerAtMillis = SystemClock.elapsedRealtime() + 400L
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.i(TAG, "Scheduled pristine restart via AlarmManager in 400ms")
            } else {
                context.startActivity(intent)
            }
        }

        // Exit process immediately to release all graphics, native, and JVM memory
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                Process.killProcess(Process.myPid())
                System.exit(0)
            } catch (t: Throwable) {
                Log.e(TAG, "Process kill error", t)
            }
        }, 150L)
    }
}
