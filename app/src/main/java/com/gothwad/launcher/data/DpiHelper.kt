package com.gothwad.launcher.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.Log

/**
 * Makes launcher UI independent from system DPI settings.
 * 
 * Problem: User changes DPI in Developer Options (320/400/200) -> launcher UI breaks
 * Solution: Force our own density, ignore system density.
 * 
 * How it works:
 * - System DPI 320 = density 2.0, 400 = 2.5, 200 = 1.25
 * - We force density to fixed value (e.g., 2.0 = 320dpi) regardless of system
 * - All dp calculations use our fixed density, so UI stays exact same
 * - pcUiScale still works on top for user preference
 * 
 * This is same as how PC OS handles: app can have its own scaling independent of system.
 */
object DpiHelper {

    private const val TAG = "DpiHelper"

    // Fixed baseline - 320 dpi (density 2.0) is good for TV/Box - looks same on all devices
    // You can change to 240 (1.5) or 280 (1.75) as per your design
    const val FIXED_DENSITY_DPI = 320
    const val FIXED_DENSITY = FIXED_DENSITY_DPI / 160f // 2.0

    // Alternative: Use 280 dpi for slightly smaller UI, 240 for compact
    const val FIXED_DENSITY_DPI_COMPACT = 240
    const val FIXED_DENSITY_COMPACT = 1.5f

    /**
     * Apply fixed DPI to context - call in attachBaseContext
     */
    fun applyFixedDensity(context: Context, forceDpi: Int = FIXED_DENSITY_DPI): Context {
        return try {
            val resources = context.resources
            val config = Configuration(resources.configuration)
            
            // Force our DPI
            config.densityDpi = forceDpi
            
            // Create new context with forced config
            val newContext = context.createConfigurationContext(config)
            
            // Also force displayMetrics
            val metrics = newContext.resources.displayMetrics
            val fixedDensity = forceDpi / 160f
            
            // Override density fields via reflection if needed, or set directly
            // For most devices, createConfigurationContext is enough
            // But we also patch the metrics for safety
            try {
                val dm = resources.displayMetrics
                dm.density = fixedDensity
                dm.scaledDensity = fixedDensity
                dm.densityDpi = forceDpi
                // Also patch new context metrics
                metrics.density = fixedDensity
                metrics.scaledDensity = fixedDensity
                metrics.densityDpi = forceDpi
            } catch (e: Exception) {
                Log.w(TAG, "Failed to patch displayMetrics", e)
            }

            Log.i(TAG, "Applied fixed DPI: $forceDpi (density $fixedDensity), system was ${resources.configuration.densityDpi}")
            newContext
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply fixed density", e)
            context
        }
    }

    /**
     * Patch existing Resources to fixed density - call in onCreate / onConfigurationChanged
     */
    fun patchResources(resources: Resources, forceDpi: Int = FIXED_DENSITY_DPI) {
        try {
            val fixedDensity = forceDpi / 160f
            val metrics = resources.displayMetrics
            val config = resources.configuration
            
            if (config.densityDpi != forceDpi) {
                config.densityDpi = forceDpi
                // Update configuration
                resources.updateConfiguration(config, metrics)
            }
            
            metrics.density = fixedDensity
            metrics.scaledDensity = fixedDensity
            metrics.densityDpi = forceDpi
            
            Log.d(TAG, "Patched resources to DPI $forceDpi, density $fixedDensity")
        } catch (e: Exception) {
            Log.e(TAG, "patchResources failed", e)
        }
    }

    /**
     * Get fixed density independent display metrics for layout calculations
     * Use this instead of context.resources.displayMetrics when you want DPI-independent sizing
     */
    fun getFixedMetrics(context: Context, forceDpi: Int = FIXED_DENSITY_DPI): DisplayMetrics {
        val systemMetrics = context.resources.displayMetrics
        val fixedDensity = forceDpi / 160f
        
        return DisplayMetrics().apply {
            widthPixels = systemMetrics.widthPixels
            heightPixels = systemMetrics.heightPixels
            density = fixedDensity
            scaledDensity = fixedDensity
            densityDpi = forceDpi
            xdpi = forceDpi.toFloat()
            ydpi = forceDpi.toFloat()
        }
    }

    /**
     * Convert dp to px using fixed density (not system density)
     */
    fun dpToPxFixed(dp: Float, forceDpi: Int = FIXED_DENSITY_DPI): Int {
        return (dp * (forceDpi / 160f)).toInt()
    }

    /**
     * Convert px to dp using fixed density
     */
    fun pxToDpFixed(px: Int, forceDpi: Int = FIXED_DENSITY_DPI): Float {
        return px / (forceDpi / 160f)
    }

    /**
     * Check if system DPI is different from our fixed DPI
     */
    fun isSystemDpiOverridden(context: Context, forceDpi: Int = FIXED_DENSITY_DPI): Boolean {
        return context.resources.configuration.densityDpi != forceDpi
    }

    /**
     * Get current system DPI for logging
     */
    fun getSystemDpiInfo(context: Context): String {
        val config = context.resources.configuration
        val metrics = context.resources.displayMetrics
        return "System DPI: ${config.densityDpi}, density: ${metrics.density}, fixed: $FIXED_DENSITY_DPI/$FIXED_DENSITY"
    }
}
