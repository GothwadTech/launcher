package com.gothwad.launcher.data

import android.content.Context
import android.content.SharedPreferences
import com.gothwad.launcher.service.LauncherAccessibilityService

/**
 * Tracks the state of the accessibility service across crashes and system restarts.
 * Detects if Android has automatically disabled the accessibility service (e.g. after a crash/ANR),
 * allowing the UI to prompt the user immediately to re-enable it.
 */
object AccessibilityStateTracker {
    private const val PREFS_NAME = "a11y_tracker_prefs"
    private const val KEY_WAS_ENABLED = "accessibility_was_enabled"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Checks whether accessibility service was disabled by Android after previously having been active.
     * Updates the persistence flag accordingly.
     *
     * @return true if accessibility was previously enabled, but is currently disabled.
     */
    fun checkAccessibilityDisabledAfterCrash(context: Context): Boolean {
        val currentlyEnabled = LauncherAccessibilityService.isEnabled(context)
        val prefs = getPrefs(context)
        val wasEnabled = prefs.getBoolean(KEY_WAS_ENABLED, false)

        if (currentlyEnabled) {
            if (!wasEnabled) {
                prefs.edit().putBoolean(KEY_WAS_ENABLED, true).apply()
            }
            return false
        } else {
            // currentlyEnabled is false
            return wasEnabled // If it was enabled previously, Android or system disabled it!
        }
    }

    /**
     * Updates tracker state when user confirms or re-enables service.
     */
    fun markEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WAS_ENABLED, enabled).apply()
    }
}
