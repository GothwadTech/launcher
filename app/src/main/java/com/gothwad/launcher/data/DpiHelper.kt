package com.gothwad.launcher.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources

/**
 * Helper to manage per-app custom screen density (DPI) independently from the system DPI.
 * Ensures the app can scale UI elements smaller or larger based on user preference
 * while preserving the native device DPI whenever custom DPI is disabled.
 */
object DpiHelper {
    const val PREFS_NAME = "dpi_prefs"
    const val KEY_CUSTOM_DPI_ENABLED = "custom_dpi_enabled"
    const val KEY_CUSTOM_DPI_VALUE = "custom_dpi_value"

    const val MIN_DPI = 120
    const val MAX_DPI = 720

    /**
     * Retrieves the true hardware/system default DPI from Resources.getSystem().
     * This is unaffected by app-level configuration overrides.
     */
    fun getDeviceDefaultDpi(): Int {
        val systemDpi = Resources.getSystem().displayMetrics.densityDpi
        return if (systemDpi > 0) systemDpi else 320
    }

    /**
     * Checks if custom DPI is enabled by the user.
     */
    fun isCustomDpiEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CUSTOM_DPI_ENABLED, false)
    }

    /**
     * Returns the custom DPI value stored, or the device default if not set or invalid.
     */
    fun getCustomDpiValue(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getInt(KEY_CUSTOM_DPI_VALUE, 0)
        return if (value in MIN_DPI..MAX_DPI) value else getDeviceDefaultDpi()
    }

    /**
     * Returns the active effective DPI (custom if enabled, otherwise device default).
     */
    fun getEffectiveDpi(context: Context): Int {
        return if (isCustomDpiEnabled(context)) {
            getCustomDpiValue(context)
        } else {
            getDeviceDefaultDpi()
        }
    }

    /**
     * Saves custom DPI settings to SharedPreferences.
     */
    fun setCustomDpi(context: Context, enabled: Boolean, dpiValue: Int) {
        val clampedDpi = dpiValue.coerceIn(MIN_DPI, MAX_DPI)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_CUSTOM_DPI_ENABLED, enabled)
            .putInt(KEY_CUSTOM_DPI_VALUE, clampedDpi)
            .apply()
    }

    /**
     * Wraps the base context with the custom DPI configuration if custom DPI is enabled.
     */
    fun wrapContext(base: Context): Context {
        if (!isCustomDpiEnabled(base)) {
            return base
        }
        val targetDpi = getCustomDpiValue(base)
        val config = Configuration(base.resources.configuration)
        config.densityDpi = targetDpi
        val newContext = base.createConfigurationContext(config)
        updateDisplayMetrics(newContext.resources, targetDpi)
        return newContext
    }

    /**
     * Updates resources DisplayMetrics and Configuration to target DPI.
     */
    fun applyToResources(resources: Resources, targetDpi: Int) {
        val clampedDpi = targetDpi.coerceIn(MIN_DPI, MAX_DPI)
        val config = resources.configuration
        config.densityDpi = clampedDpi
        val dm = resources.displayMetrics
        dm.densityDpi = clampedDpi
        dm.density = clampedDpi / 160f
        dm.scaledDensity = dm.density * (config.fontScale.takeIf { it > 0 } ?: 1.0f)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, dm)
    }

    /**
     * Restores resources DisplayMetrics and Configuration to device default.
     */
    fun restoreDeviceDpi(resources: Resources) {
        val defaultDpi = getDeviceDefaultDpi()
        applyToResources(resources, defaultDpi)
    }

    private fun updateDisplayMetrics(resources: Resources, targetDpi: Int) {
        val dm = resources.displayMetrics
        dm.densityDpi = targetDpi
        dm.density = targetDpi / 160f
        dm.scaledDensity = dm.density * (resources.configuration.fontScale.takeIf { it > 0 } ?: 1.0f)
    }
}
