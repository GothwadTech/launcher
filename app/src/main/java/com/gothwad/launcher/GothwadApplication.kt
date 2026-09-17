package com.gothwad.launcher

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.gothwad.launcher.data.AppLaunchTracker
import com.gothwad.launcher.service.LauncherWatchdogService
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom Application class for Gothwad Launcher.
 * Acts as an application-level memory guardian on low-RAM (2GB) STB/TV devices,
 * automatically trimming tracked background processes under system memory pressure,
 * and installing a crash self-healing watchdog.
 */
class GothwadApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val currentProc = com.gothwad.launcher.data.ProcessHelper.currentProcessName()
        Log.i(TAG, "GothwadApplication initialized in process: $currentProc")

        // Only initialize main process handlers & watchdog from the main process
        if (currentProc.isEmpty() || currentProc == packageName) {
            setupCrashSelfHealing()
            LauncherWatchdogService.start(this)
        }
    }

    private fun setupCrashSelfHealing() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "FATAL CRASH detected on thread ${thread.name}! Triggering self-healing recovery.", throwable)

                // 1. Log crash to small rolling file in filesDir for diagnosis
                logCrashToFile(throwable)

                // 2. Schedule immediate relaunch of MainActivity via AlarmManager (1-2 seconds out)
                scheduleEmergencyRelaunch()
            } catch (e: Exception) {
                Log.e(TAG, "Error in crash handler", e)
            } finally {
                // 3. Call previous handler so OS standard crash reporting / dump still completes
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun logCrashToFile(throwable: Throwable) {
        runCatching {
            val crashFile = File(filesDir, CRASH_LOG_FILE)
            // Keep crash file bounded: if it exceeds 64KB, truncate it
            if (crashFile.exists() && crashFile.length() > 64 * 1024) {
                crashFile.delete()
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            crashFile.appendText("[$timestamp] CRASH:\n$sw\n--------------------\n")
        }
    }

    private fun scheduleEmergencyRelaunch() {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                RELAUNCH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null) {
                val triggerAtMillis = SystemClock.elapsedRealtime() + 1500L // 1.5s after crash
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.i(TAG, "Emergency launcher restart scheduled in 1500ms via AlarmManager")
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w(TAG, "onTrimMemory: TRIM_MEMORY_RUNNING_CRITICAL (level=$level), triggering critical trim")
                AppLaunchTracker.performMemoryTrim(this, isCritical = true)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.i(TAG, "onTrimMemory: TRIM_MEMORY_RUNNING_LOW (level=$level), triggering background trim")
                AppLaunchTracker.performMemoryTrim(this, isCritical = false)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // Background process cleanup when launcher itself is backgrounded
                Log.d(TAG, "onTrimMemory: TRIM_MEMORY_COMPLETE (level=$level)")
                AppLaunchTracker.performMemoryTrim(this, isCritical = false)
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // Fallback for OEM builds (e.g. older Android versions or TV forks) that do not reliably deliver onTrimMemory
        Log.w(TAG, "onLowMemory triggered! Running critical memory trim")
        AppLaunchTracker.performMemoryTrim(this, isCritical = true)
    }

    companion object {
        private const val TAG = "GothwadApplication"
        private const val CRASH_LOG_FILE = "gothwad_crash.log"
        private const val RELAUNCH_REQUEST_CODE = 4401
    }
}
