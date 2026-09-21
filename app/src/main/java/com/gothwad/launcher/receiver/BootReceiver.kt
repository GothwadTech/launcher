package com.gothwad.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.service.BootShieldService
import com.gothwad.launcher.service.LauncherAccessibilityService
import com.gothwad.launcher.service.LauncherWatchdogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles TV / set-top-box boot and package-replacement events
 * (Jio STB, Airtel Xstream, Google TV, Android TV certified, generic OEM boxes)
 * so Gothwad Launcher comes up immediately and OEM boot ads / stock launchers can be
 * suppressed.
 *
 * IMPORTANT: this receiver must return within the broadcast execution window
 * (~10s foreground / ~60s background). All long-running work therefore lives in
 * [BootShieldService]; earlier versions ran a 45-90 second `goAsync()` loop in here,
 * which reliably produced an "ANR in BroadcastReceiver: BOOT_COMPLETED" on every boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        Log.i(TAG, "Boot/Replaced action received: $action")

        // 1. Always keep the self-healing watchdog alive. During LOCKED_BOOT_COMPLETED the
        //    credential-protected storage is not mounted yet, so every read below is
        //    wrapped and falls back to defaults - the shield still runs pre-unlock.
        LauncherWatchdogService.start(context)

        val isBoot = action != Intent.ACTION_MY_PACKAGE_REPLACED
        if (!isBoot) {
            // After an update we just want the watchdog back; never steal the screen with
            // a boot-ad shield.
            return
        }

        // 2. Respect the user's "Launch on boot" preference (default: on, discoverable in
        //    Settings > Apps; the shield is what keeps OEM boot ads off the screen).
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val config = runCatching { ConfigStore(context).flow.first() }.getOrNull()
                val launchOnBoot = config?.launchOnBoot ?: true

                if (launchOnBoot) {
                    BootShieldService.start(context)
                } else {
                    Log.i(TAG, "launchOnBoot disabled - only launching Home if we are the default HOME app")
                    if (isDefaultHomeApp(context)) {
                        LauncherAccessibilityService.launchHomeWithRetry(context, maxAttempts = 2)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while starting boot handling", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** True when this package currently resolves as the system HOME app. */
    private fun isDefaultHomeApp(context: Context): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolve = context.packageManager.resolveActivity(intent, 0)
            resolve?.activityInfo?.packageName == context.packageName
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
