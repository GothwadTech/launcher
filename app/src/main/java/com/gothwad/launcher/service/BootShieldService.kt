package com.gothwad.launcher.service

import android.app.ActivityManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import com.gothwad.launcher.data.BackgroundMediaTracker
import com.gothwad.launcher.data.UsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Boot-ad / stock-launcher shield.
 *
 * This used to live inside `BootReceiver.onReceive()` behind `goAsync()` with a 45-90
 * second loop, which is far beyond the broadcast execution window (~10s foreground /
 * ~60s background) - every boot produced an
 * "ANR in BroadcastReceiver: BOOT_COMPLETED". The guard loop now runs in this service,
 * where long-running work is allowed, and the receiver returns immediately.
 *
 * The watch window is adaptive: low-RAM (<= 2.2 GB) STBs get 90 seconds, everything else
 * 45 seconds, after which the service stops itself.
 */
class BootShieldService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var guardJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (guardJob?.isActive == true) return START_NOT_STICKY

        guardJob = scope.launch {
            try {
                runGuardLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Error in boot shield guard loop", e)
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runGuardLoop() {
        val context = applicationContext

        // Initial launch with exponential backoff retry (up to 5 attempts)
        LauncherAccessibilityService.launchHomeWithRetry(context, maxAttempts = 5)

        val timeout = bootShieldWindowMs(context)
        Log.i(TAG, "Boot shield active for ${timeout / 1000}s")

        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        // PACKAGE_USAGE_STATS is a special access; without it queryEvents returns an empty
        // list and the old code silently fell back to getRunningTasks(1) - which, for a
        // third-party app, only ever reports its own task, so the whole detection was
        // dead weight (issue #27). The accessibility service is the reliable second
        // source and reports "unknown" otherwise.
        val hasUsageAccess = UsageTracker.hasPermission(this)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val startTime = System.currentTimeMillis()
        var lastAudioCheck = 0L

        while (scope.isActive && System.currentTimeMillis() - startTime < timeout) {
            delay(700L)

            val topPackage = getForegroundPackage(usm, hasUsageAccess)
            if (topPackage != null &&
                LauncherAccessibilityService.isStockTvLauncher(context, topPackage) &&
                topPackage != packageName
            ) {
                Log.w(TAG, "Stock launcher / boot ad tried to take the screen: $topPackage - suppressing")
                BackgroundMediaTracker.silenceAudio(context)
                LauncherAccessibilityService.launchHomeWithRetry(context, maxAttempts = 3)
            }

            // If an ad starts playing audio during the boot window, silence it - but only
            // every couple of seconds instead of on every tick.
            val now = System.currentTimeMillis()
            if (now - lastAudioCheck > 2_000L) {
                lastAudioCheck = now
                if (audioManager?.isMusicActive == true) {
                    BackgroundMediaTracker.silenceAudio(context)
                }
            }
        }
        Log.i(TAG, "Boot shield window finished")
    }

    /**
     * Adaptive shield duration: 90s on low-RAM (<= 2.2 GB) boxes, 45s otherwise.
     */
    private fun bootShieldWindowMs(context: Context): Long {
        val totalRamGb = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        }.getOrDefault(4.0)

        return if (totalRamGb <= 2.2) {
            Log.i(TAG, "Low RAM device detected (${"%.2f".format(totalRamGb)} GB). Extending boot shield to 90s.")
            90_000L
        } else {
            45_000L
        }
    }

    /**
     * Current foreground package, or null when it cannot be determined.
     *
     * Source 1: UsageStatsManager (needs the special usage-access grant).
     * Source 2: the accessibility service's last TYPE_WINDOW_STATE_CHANGED package -
     * available whenever the user enabled this launcher's accessibility service, which
     * is the documented setup for the app lock / boot shield.
     *
     * `ActivityManager.getRunningTasks(1)` is intentionally gone: on Android 5+ it only
     * returns the caller's own tasks, so the "fallback" could never see a stock launcher.
     */
    private fun getForegroundPackage(
        usm: UsageStatsManager?,
        hasUsageAccess: Boolean
    ): String? {
        if (usm != null && hasUsageAccess) {
            val toTime = System.currentTimeMillis()
            val fromTime = toTime - 5_000L
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
                if (!latestFgPackage.isNullOrEmpty()) return latestFgPackage
            }
        }

        return LauncherAccessibilityService.lastKnownForegroundPackage
    }

    override fun onDestroy() {
        guardJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BootShieldService"

        fun start(context: Context) {
            runCatching {
                context.startService(Intent(context, BootShieldService::class.java))
            }.onFailure {
                Log.w(TAG, "Could not start BootShieldService: ${it.message}")
            }
        }
    }
}
