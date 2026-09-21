package com.gothwad.launcher.service

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.gothwad.launcher.data.AccessibilityStateTracker
import com.gothwad.launcher.data.ProcessHeartbeat
import com.gothwad.launcher.data.ProcessHelper
import com.gothwad.launcher.data.SelfHealGuard

/**
 * Ultra-lightweight crash & ANR self-healing watchdog running in its own `:watchdog` process.
 * Memory footprint: a few MB steady state (pure Handler loop, zero libraries).
 *
 * Every [CHECK_INTERVAL_MS] it verifies:
 * 1) Whether the main process is still publishing its liveness heartbeat
 *    ([ProcessHeartbeat]). If the beat has gone stale the process is genuinely dead or
 *    hung, and MainActivity is relaunched - subject to the [SelfHealGuard] budget.
 * 2) Whether the process has entered an ANR state (`processesInErrorState`), which is
 *    checked defensively: `null` there means "unknown", never "crashed".
 * 3) Whether the accessibility service was disabled by Android after a crash; if so the
 *    main UI gets told so it can show the recovery banner.
 *
 * NOTE: `ActivityManager.getRunningAppProcesses()` is deliberately *not* used as a
 * liveness signal any more. On restricted OEM firmware (Jio/Airtel STBs, several Google
 * TV builds) it returns `null`/a filtered list for third-party apps, and treating that
 * as "the launcher is dead" made this watchdog yank the user back to HOME every 10
 * seconds while they were watching something.
 */
class LauncherWatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isChecking = false
    private var startedAtElapsed = 0L

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkMainProcessHealth()
            checkAccessibilityStatus()
            if (isChecking) {
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Watchdog service started in process: ${ProcessHelper.currentProcessName()}")
        startedAtElapsed = SystemClock.elapsedRealtime()
        isChecking = true
        // Give the main process a full interval plus the staleness window before the
        // first verdict, so a slow cold boot is never mistaken for a dead process.
        handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isChecking) {
            isChecking = true
            handler.removeCallbacks(checkRunnable)
            handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isChecking = false
        handler.removeCallbacks(checkRunnable)
        Log.i(TAG, "Watchdog service onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkMainProcessHealth() {
        // 1. Heartbeat: authoritative, works on every OEM firmware and needs no permissions.
        val upLongEnough = SystemClock.elapsedRealtime() - startedAtElapsed > CHECK_INTERVAL_MS + 5_000L
        if (upLongEnough && ProcessHeartbeat.isMainProcessStale(applicationContext)) {
            val lastBeat = ProcessHeartbeat.lastProcessBeat(applicationContext)
            val ageMs = if (lastBeat > 0L) SystemClock.elapsedRealtime() - lastBeat else -1L
            Log.w(TAG, "Main process heartbeat stale (age=${ageMs}ms). Relaunching launcher.")
            relaunchLauncher("heartbeat stale (age=${ageMs}ms)")
            return
        }

        // 2. ANR detection (defensive: null/empty means unknown, not broken).
        val errorInfo = runCatching {
            (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.processesInErrorState
                ?.firstOrNull { it.processName == packageName }
        }.getOrNull()

        if (errorInfo != null &&
            errorInfo.condition == ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING
        ) {
            Log.e(TAG, "Main process is in NOT_RESPONDING (ANR) state. Relaunching launcher.")
            relaunchLauncher("ANR detected")
        }
    }

    private fun checkAccessibilityStatus() {
        runCatching {
            val disabledAfterCrash = AccessibilityStateTracker.checkAccessibilityDisabledAfterCrash(applicationContext)
            if (disabledAfterCrash) {
                Log.w(TAG, "Accessibility service was disabled after crash! Broadcasting warning to launcher.")
                val intent = Intent(ACTION_ACCESSIBILITY_DISABLED_ALERT).apply {
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            }
        }
    }

    private fun relaunchLauncher(reason: String) {
        if (!SelfHealGuard.shouldRelaunch(applicationContext)) {
            Log.w(TAG, "Skipping relaunch ($reason): self-healing is paused after repeated restarts.")
            return
        }
        runCatching {
            LauncherAccessibilityService.launchHome(applicationContext)
        }.onFailure { e ->
            Log.e(TAG, "Failed to relaunch launcher from watchdog", e)
        }
    }

    companion object {
        private const val TAG = "LauncherWatchdog"
        private const val CHECK_INTERVAL_MS = 15_000L // 15 second check loop (was 10s)
        const val ACTION_ACCESSIBILITY_DISABLED_ALERT = "com.gothwad.launcher.ACCESSIBILITY_DISABLED_ALERT"

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, LauncherWatchdogService::class.java)
                context.startService(intent)
            }.onFailure {
                Log.w(TAG, "Could not start LauncherWatchdogService: ${it.message}")
            }
        }
    }
}
