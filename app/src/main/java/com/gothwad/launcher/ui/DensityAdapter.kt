package com.gothwad.launcher.ui

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Universal DPI Independence Engine for Gothwad Launcher.
 *
 * Ensures that regardless of whatever custom, corrupted, or non-standard DPI
 * is configured at the system level (e.g. via 'wm density', developer options,
 * or manufacturer ROM defaults), the launcher UI always scales pixel-perfectly
 * against the universal Android TV reference baseline (960dp width).
 *
 * Baseline:
 * - 1080p (1920x1080) -> Target Density: 2.0 (320 dpi) -> 960x540 dp
 * - 4K UHD (3840x2160) -> Target Density: 4.0 (640 dpi) -> 960x540 dp
 * - 720p (1280x720)   -> Target Density: 1.333 (213 dpi) -> 960x540 dp
 *
 * Even if system DPI is 120, 160, 240, 480, 560, etc., the launcher renders
 * identically and immune to system DPI changes.
 */
object DensityAdapter {

    private const val TAG = "DensityAdapter"

    /**
     * Standard Android TV reference design width in dp (Leanback / 1080p TV specification).
     */
    const val DESIGN_WIDTH_DP = 960f

    @Volatile
    private var isInitialized = false

    /**
     * When true (default) the system font size / accessibility setting is respected for
     * `sp` text instead of being forced to 1.0x (issue #45). Density is still normalised -
     * only the font scale is passed through.
     */
    @Volatile
    var respectSystemFontScale: Boolean = true

    /** System font scale, clamped so a broken ROM value cannot render the UI unusable. */
    private fun effectiveFontScale(): Float =
        if (respectSystemFontScale) {
            Resources.getSystem().configuration.fontScale.coerceIn(0.85f, 1.30f)
        } else {
            1.0f
        }

    data class AdaptedMetrics(
        val screenWidthPx: Int,
        val screenHeightPx: Int,
        val targetDensity: Float,
        val targetDensityDpi: Int,
        val screenWidthDp: Int,
        val screenHeightDp: Int
    )

    /**
     * Obtains the true physical screen dimensions in pixels, ensuring landscape
     * orientation (width is the longer edge).
     */
    fun getRealScreenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.maximumWindowMetrics.bounds
                val w = bounds.width()
                val h = bounds.height()
                if (w > 0 && h > 0) {
                    return Pair(maxOf(w, h), minOf(w, h))
                }
            } else {
                val dm = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay?.getRealMetrics(dm)
                if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                    return Pair(maxOf(dm.widthPixels, dm.heightPixels), minOf(dm.widthPixels, dm.heightPixels))
                }
            }
        }
        val fallbackDm = context.resources.displayMetrics
        return Pair(
            maxOf(fallbackDm.widthPixels, fallbackDm.heightPixels),
            minOf(fallbackDm.widthPixels, fallbackDm.heightPixels)
        )
    }

    /**
     * Calculates the target density and DPI required to anchor the display to [DESIGN_WIDTH_DP].
     */
    fun calculateMetrics(context: Context): AdaptedMetrics {
        val (screenWidthPx, screenHeightPx) = getRealScreenSize(context)
        val targetDensity = (screenWidthPx.toFloat() / DESIGN_WIDTH_DP).coerceAtLeast(0.5f)
        val targetDensityDpi = (targetDensity * 160f).roundToInt()
        val screenWidthDp = (screenWidthPx / targetDensity).roundToInt()
        val screenHeightDp = (screenHeightPx / targetDensity).roundToInt()

        return AdaptedMetrics(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            targetDensity = targetDensity,
            targetDensityDpi = targetDensityDpi,
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp
        )
    }

    /**
     * Applies the DPI-independent metrics directly to the given [Resources].
     */
    fun applyToResources(res: Resources, metrics: AdaptedMetrics) {
        val dm = res.displayMetrics
        val fontScale = effectiveFontScale()
        dm.density = metrics.targetDensity
        dm.densityDpi = metrics.targetDensityDpi
        // scaledDensity drives sp -> px, so text follows the user's font-size setting.
        dm.scaledDensity = metrics.targetDensity * fontScale
        dm.xdpi = metrics.targetDensityDpi.toFloat()
        dm.ydpi = metrics.targetDensityDpi.toFloat()

        val config = res.configuration
        config.densityDpi = metrics.targetDensityDpi
        config.fontScale = fontScale
        config.screenWidthDp = metrics.screenWidthDp
        config.screenHeightDp = metrics.screenHeightDp
        config.smallestScreenWidthDp = minOf(metrics.screenWidthDp, metrics.screenHeightDp)

        @Suppress("DEPRECATION")
        res.updateConfiguration(config, dm)
    }

    /**
     * Wraps a base context with a Configuration that enforces our target DPI.
     * Ideal for [Activity.attachBaseContext].
     */
    fun wrapContext(baseContext: Context): Context {
        val metrics = calculateMetrics(baseContext)
        val config = Configuration(baseContext.resources.configuration).apply {
            densityDpi = metrics.targetDensityDpi
            fontScale = effectiveFontScale()
            screenWidthDp = metrics.screenWidthDp
            screenHeightDp = metrics.screenHeightDp
            smallestScreenWidthDp = minOf(metrics.screenWidthDp, metrics.screenHeightDp)
        }

        val wrapped = baseContext.createConfigurationContext(config)
        applyToResources(wrapped.resources, metrics)
        return wrapped
    }

    /**
     * Enforces the target DPI on the provided [Context] (Activity or Application).
     */
    fun apply(context: Context) {
        val metrics = calculateMetrics(context)
        applyToResources(context.resources, metrics)
    }

    /**
     * Enforces the target DPI on an [Activity], its Window decor, and Application resources.
     */
    fun apply(activity: Activity) {
        val metrics = calculateMetrics(activity)
        applyToResources(activity.resources, metrics)
        activity.window?.decorView?.resources?.let { applyToResources(it, metrics) }
        applyToResources(activity.applicationContext.resources, metrics)
    }

    /**
     * Initializes the global density adapter on the [Application] instance.
     * Hooks into Activity lifecycle and system component configuration callbacks
     * so any system-level DPI change is immediately corrected.
     */
    fun init(application: Application) {
        if (isInitialized) return
        isInitialized = true

        val metrics = calculateMetrics(application)
        applyToResources(application.resources, metrics)
        Log.i(
            TAG,
            "Initialized DensityAdapter: Physical ${metrics.screenWidthPx}x${metrics.screenHeightPx}px, " +
                "Enforced DPI=${metrics.targetDensityDpi}, Density=${metrics.targetDensity}, " +
                "Canvas=${metrics.screenWidthDp}x${metrics.screenHeightDp}dp (System DPI immune)"
        )

        // Monitor activity lifecycle to ensure every activity and its decor are adapted
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                apply(activity)
            }

            override fun onActivityStarted(activity: Activity) {
                apply(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                apply(activity)
            }

            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Intercept any runtime system configuration changes (e.g. user toggles display size or font scale)
        application.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val currentMetrics = calculateMetrics(application)
                applyToResources(application.resources, currentMetrics)
                Log.d(TAG, "System configuration changed; re-enforced DPI=${currentMetrics.targetDensityDpi}")
            }

            override fun onLowMemory() {}
        })
    }
}
