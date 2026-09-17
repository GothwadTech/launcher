package com.gothwad.launcher.receiver

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import com.gothwad.launcher.data.BackgroundMediaTracker
import com.gothwad.launcher.service.LauncherAccessibilityService
import com.gothwad.launcher.service.LauncherWatchdogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Intercepts TV and Set-Top-Box boot events and package update events
 * (Jio STB, Airtel Xstream, Google TV, Android TV certified devices, etc.)
 * to launch Gothwad Launcher immediately and suppress unwanted OEM boot ads / launcher grabs.
 *
 * Fully supports Android 10+ restricted firmware using UsageStatsManager.queryEvents()
 * with graceful fallback to getRunningTasks(). Features adaptive shield duration based on device RAM.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.i(TAG, "Boot/Replaced action received: $action")

            // 1. Immediately launch Watchdog Service
            LauncherWatchdogService.start(context)

            // 2. Start adaptive Boot Shield Watchdog asynchronously with retry backoff
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    // Initial launch with exponential backoff retry (up to 5 attempts)
                    LauncherAccessibilityService.launchHomeWithRetry(context, maxAttempts = 5)

                    // Adaptive boot guard duration:
                    // Read total RAM; if <= 2GB, extend window up to 90 seconds. Otherwise, standard 45 seconds.
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val memInfo = ActivityManager.MemoryInfo()
                    am?.getMemoryInfo(memInfo)
                    val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

                    val timeout = if (totalRamGb <= 2.2) {
                        Log.i(TAG, "Low RAM device detected (${"%.2f".format(totalRamGb)} GB). Extending boot shield to 90s.")
                        90_000L
                    } else {
                        45_000L
                    }

                    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val startTime = System.currentTimeMillis()

                    while (System.currentTimeMillis() - startTime < timeout) {
                        delay(600L)

                        val topPackage = getForegroundPackage(context, am, usm)

                        if (topPackage != null &&
                            LauncherAccessibilityService.isStockTvLauncher(topPackage) &&
                            topPackage != context.packageName
                        ) {
                            Log.w(TAG, "Stock launcher / boot ad attempted to steal screen: $topPackage. Suppressing!")
                            BackgroundMediaTracker.silenceAudio(context)
                            LauncherAccessibilityService.launchHomeWithRetry(context, maxAttempts = 3)
                        }

                        // If background ad music suddenly starts playing during boot period, silence it
                        if (audioManager?.isMusicActive == true) {
                            BackgroundMediaTracker.silenceAudio(context)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in BootReceiver guard loop", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    /**
     * Determines current foreground package name reliably across Android 8-14+ and restricted firmware.
     * Uses UsageStatsManager.queryEvents() (MOVE_TO_FOREGROUND) when available,
     * with graceful fallback to ActivityManager.getRunningTasks(1).
     */
    private fun getForegroundPackage(
        context: Context,
        am: ActivityManager?,
        usm: UsageStatsManager?
    ): String? {
        // 1. Primary: UsageStatsManager (Accurate on Android 10+ certified/restricted Android TV)
        if (usm != null) {
            val fromTime = System.currentTimeMillis() - 5000L
            val toTime = System.currentTimeMillis()
            val events = runCatching { usm.queryEvents(fromTime, toTime) }.getOrNull()

            if (events != null) {
                val event = UsageEvents.Event()
                var latestFgPackage: String? = null
                var latestFgTimestamp = 0L

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                    ) {
                        if (event.timeStamp >= latestFgTimestamp) {
                            latestFgTimestamp = event.timeStamp
                            latestFgPackage = event.packageName
                        }
                    }
                }
                if (!latestFgPackage.isNullOrEmpty()) {
                    return latestFgPackage
                }
            }
        }

        // 2. Fallback: getRunningTasks (legacy or devices without granted usage permission)
        val runningTasks = runCatching { am?.getRunningTasks(1) }.getOrNull()
        return runningTasks?.firstOrNull()?.topActivity?.packageName
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
