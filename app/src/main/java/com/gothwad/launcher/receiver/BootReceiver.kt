package com.gothwad.launcher.receiver

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.gothwad.launcher.data.BackgroundMediaTracker
import com.gothwad.launcher.service.LauncherAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Intercepts TV and Set-Top-Box boot events (Jio STB, Airtel Xstream, Google TV, etc.)
 * to launch Gothwad Launcher immediately and suppress unwanted 30-60 second OEM boot ads.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            // 1. Immediately launch Gothwad Launcher
            LauncherAccessibilityService.launchHome(context)

            // 2. Start a 45-second Boot Shield Watchdog.
            // On Jio/Airtel STB, stock launcher tries to reclaim foreground and play video ads 5-20s after boot.
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val startTime = System.currentTimeMillis()
                    val timeout = 45_000L // 45 seconds boot guard window

                    while (System.currentTimeMillis() - startTime < timeout) {
                        delay(600L)

                        // Check running tasks / processes
                        val runningTasks = runCatching { am?.getRunningTasks(1) }.getOrNull()
                        val topPackage = runningTasks?.firstOrNull()?.topActivity?.packageName

                        if (topPackage != null &&
                            LauncherAccessibilityService.isStockTvLauncher(topPackage) &&
                            topPackage != context.packageName
                        ) {
                            // Stock launcher or boot ad attempted to steal screen; suppress it
                            BackgroundMediaTracker.silenceAudio(context)
                            LauncherAccessibilityService.launchHome(context)
                        }

                        // If background ad music suddenly starts playing during boot period, silence it
                        if (audioManager?.isMusicActive == true) {
                            BackgroundMediaTracker.silenceAudio(context)
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
