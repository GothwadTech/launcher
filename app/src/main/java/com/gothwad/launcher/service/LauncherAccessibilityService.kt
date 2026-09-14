package com.gothwad.launcher.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.gothwad.launcher.MainActivity
import com.gothwad.launcher.data.BackgroundMediaTracker

/**
 * Accessibility Service to handle Home key capture, OEM launcher overrides,
 * and Boot Ad / Stock Launcher suppression on Jio, Airtel, Google TV, and locked STBs.
 */
class LauncherAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (isStockTvLauncher(pkg) && pkg != packageName) {
                // The OEM/Jio/Airtel launcher or boot ad was brought to foreground; return to Gothwad Launcher
                BackgroundMediaTracker.silenceAudio(this)
                launchHome(this)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_UP) {
                launchHome(this)
            }
            return true // Consume the HOME key event so locked stock TV launcher cannot react
        }
        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {}

    companion object {
        val STOCK_LAUNCHERS = setOf(
            // Google TV / Android TV
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.tungsten.setupwraith",
            // JioFiber / Jio STB packages
            "com.jio.media.stblauncher",
            "com.jio.media.jiohome",
            "com.jio.media.ondemand",
            "com.jio.jiotv",
            "com.ril.jio.stb",
            // Airtel Xstream STB packages
            "com.airtel.tv",
            "com.airtel.xstream",
            "com.airtel.android.tv",
            "com.airtel.smartbox",
            "tv.airtel.smartbox.launcher",
            "com.airtel.tv.launcher",
            // Tata Play Binge / Dish SMRT / D2H / Asianet / Hathway
            "com.tatasky.binge",
            "com.tatasky.stb",
            "com.dishtv.smrt",
            "com.d2h.stream",
            "com.nes.tvlauncher",
            "com.nes.operator",
            "com.sdmc.launcher",
            "com.geniatech.launcher",
            "com.amlogic.tvlauncher",
            "com.realtek.tvlauncher",
            // Fire TV & OEM TV Launchers
            "com.amazon.tv.launcher",
            "com.amazon.firehomestarter",
            "com.xiaomi.mitv.tvhome",
            "com.mitv.tvhome",
            "com.tcl.tvplayer",
            "com.hisense.tv.launcher",
            "com.droidlogic.tv.launcher"
        )

        fun isStockTvLauncher(pkg: String): Boolean =
            STOCK_LAUNCHERS.any { pkg.startsWith(it, ignoreCase = true) }

        fun launchHome(context: Context) {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            context.startActivity(intent)
        }

        fun isEnabled(context: Context): Boolean {
            val expectedComponent = "${context.packageName}/${LauncherAccessibilityService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.split(':').any {
                it.equals(expectedComponent, ignoreCase = true) ||
                    (it.contains(context.packageName, ignoreCase = true) &&
                        it.contains("LauncherAccessibilityService", ignoreCase = true))
            }
        }
    }
}
