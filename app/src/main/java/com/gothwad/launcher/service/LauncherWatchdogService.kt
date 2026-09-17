package com.gothwad.launcher.service

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.gothwad.launcher.data.AccessibilityStateTracker
import com.gothwad.launcher.data.ProcessHelper

/**
 * Ultra-lightweight crash & ANR self-healing watchdog running in its own `:watchdog` process.
 * Memory footprint: under 3-5MB steady state (pure Handler loop, zero external libraries).
 *
 * Every 10 seconds, it verifies if:
 * 1) The main process (com.gothwad.launcher) is alive and healthy. If dead or in an ANR condition,
 *    it immediately triggers a relaunch of MainActivity to restore the TV home screen.
 * 2) The accessibility service was disabled by Android after a crash; if so, notifies the main UI/system.
 */
class LauncherWatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isChecking = false

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
        isChecking = true
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
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val targetProcessName = packageName // "com.gothwad.launcher"

        // 1. Inspect running app processes (Works reliably on Android 8-14 without foreground task restrictions)
        val runningProcesses = runCatching { am.runningAppProcesses }.getOrNull()

        val mainProcInfo = runningProcesses?.firstOrNull { it.processName == targetProcessName }

        if (mainProcInfo == null) {
            // Main process is dead! Relaunch immediately
            Log.w(TAG, "Main process '$targetProcessName' is not running! Triggering relaunch.")
            relaunchLauncher()
            return
        }

        // 2. Check if the process has entered an ANR / Not Responding state
        val errorProcesses = runCatching { am.processesInErrorState }.getOrNull()
        val errorInfo = errorProcesses?.firstOrNull { it.processName == targetProcessName }

        if (errorInfo != null && errorInfo.condition == ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING) {
            Log.e(TAG, "Main process '$targetProcessName' is in NOT_RESPONDING condition (ANR)! Relaunching...")
            relaunchLauncher()
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

    private fun relaunchLauncher() {
        runCatching {
            LauncherAccessibilityService.launchHome(applicationContext)
        }.onFailure { e ->
            Log.e(TAG, "Failed to relaunch launcher from watchdog", e)
        }
    }

    companion object {
        private const val TAG = "LauncherWatchdog"
        private const val CHECK_INTERVAL_MS = 10_000L // 10 seconds check loop
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
