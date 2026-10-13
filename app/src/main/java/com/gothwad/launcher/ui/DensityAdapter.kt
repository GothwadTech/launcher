package com.gothwad.launcher.ui

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.SharedPreferences
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
        private set

    /**
     * Whole-UI multiplier from Settings > Display > UI Scale (`UI_SCALES` in Config.kt).
     *
     * It is applied to the *calculated density*: 1.3x makes every dp physically larger, so
     * the design canvas shrinks from 960dp to ~738dp wide and the whole launcher scales up.
     * This used to be a settings toggle that was persisted and labelled but never actually
     * applied to anything - the UI looked identical at 0.75x and 1.3x.
     */
    @Volatile
    var userUiScale: Float = 1.0f
        private set

    /** Persisted so the first frame after a cold start already uses the user's choices. */
    private const val PREFS_NAME = "gothwad_density"
    private const val KEY_UI_SCALE = "ui_scale"
    private const val KEY_RESPECT_FONT_SCALE = "respect_system_font_scale"

    /** Guard rails so a corrupt preference can never render the launcher unusable. */
    const val MIN_UI_SCALE = 0.6f
    const val MAX_UI_SCALE = 1.6f

    /**
     * Opens the persisted mirror of the density policy, or null when it cannot be opened.
     *
     * ## Do not read this through `applicationContext`
     *
     * [hydrate] also runs from `GothwadApplication.attachBaseContext` (see [wrapContext]), and
     * while an `Application` is being attached the framework has **not registered it yet**:
     * `LoadedApk.makeApplication()` assigns `mApplication = app` only *after*
     * `Instrumentation.newApplication()` - which is what calls `attachBaseContext()` - returns,
     * and `ContextImpl.getApplicationContext()` is literally
     * `mPackageInfo.getApplication()`. So `context.applicationContext` is `null` in there, and
     * `context.applicationContext.getSharedPreferences(...)` threw a `NullPointerException`
     * before the launcher could draw its first frame - on every device, on every Android
     * version (verified against AOSP 5.1 / 9 / 11 / 14). Because this app is normally the
     * default HOME, the TV showed "Gothwad Launcher keeps stopping" over a black screen.
     *
     * The context we are handed is a perfectly usable package context of the same app, so use
     * it directly.
     *
     * Reads that legitimately cannot work are handled too: from the `:watchdog` process before
     * the user unlocks the device, credential-encrypted storage is not mounted yet and
     * `getSharedPreferences` throws `IllegalStateException` (targetSdk >= 26). A missing UI
     * preference must not kill a launcher, so a null return simply means "keep the values we
     * already have".
     */
    private fun prefs(context: Context): SharedPreferences? = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }.onFailure { e ->
        Log.w(TAG, "Density preferences unavailable (${e.javaClass.simpleName}: ${e.message})")
    }.getOrNull()

    /**
     * Loads the persisted scale / font policy. Cheap after the first call (SharedPreferences
     * keeps the parsed map in memory); called from every entry point below so a value written
     * by another component is never ignored.
     *
     * Best-effort by design: when the mirror is unreadable the in-memory values (previous
     * hydration, or the 1.0x default) stay in force.
     */
    fun hydrate(context: Context) {
        val sp = prefs(context) ?: return
        userUiScale = sp.getFloat(KEY_UI_SCALE, 1.0f).coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
        respectSystemFontScale = sp.getBoolean(KEY_RESPECT_FONT_SCALE, true)
    }

    /**
     * Persists + applies a new whole-UI scale.
     *
     * NOTE: views that are already inflated keep their old pixel sizes, so the caller must
     * recreate the activity for the change to become visible end-to-end.
     *
     * @return true when the effective value actually changed.
     */
    fun setUiScale(context: Context, multiplier: Float): Boolean {
        val clamped = multiplier.coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
        val changed = kotlin.math.abs(clamped - userUiScale) > 0.001f
        userUiScale = clamped
        prefs(context)?.edit()?.putFloat(KEY_UI_SCALE, clamped)?.apply()
        Log.i(TAG, "UI scale set to ${clamped}x")
        return changed
    }

    /** Persists + applies the "respect system font size" policy. */
    fun setRespectSystemFontScale(context: Context, enabled: Boolean): Boolean {
        val changed = respectSystemFontScale != enabled
        respectSystemFontScale = enabled
        prefs(context)?.edit()?.putBoolean(KEY_RESPECT_FONT_SCALE, enabled)?.apply()
        return changed
    }

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
        val baseline = (screenWidthPx.toFloat() / DESIGN_WIDTH_DP).coerceAtLeast(0.5f)
        // UI Scale multiplies the baseline density: >1 = bigger everything (fewer dp across
        // the screen), <1 = more compact.
        val scale = userUiScale.coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
        val targetDensity = (baseline * scale).coerceAtLeast(0.4f)
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
     *
     * Best-effort: this runs *before* any of our code can catch a throw, in the
     * `Application.attachBaseContext` / `Activity.attachBaseContext` window where an exception
     * means the launcher never starts (and, as HOME, that the TV shows nothing). If the density
     * engine cannot do its job the base context is returned untouched - the user gets the
     * firmware's DPI instead of a dead home screen.
     */
    fun wrapContext(baseContext: Context): Context = runCatching {
        hydrate(baseContext)
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
        wrapped
    }.getOrElse { e ->
        Log.e(TAG, "Could not apply the design-width density to this context", e)
        baseContext
    }

    /**
     * Enforces the target DPI on the provided [Context] (Activity or Application).
     *
     * Best-effort - see [wrapContext]: a density mismatch is a cosmetic problem, a launcher
     * that cannot start is a broken TV.
     */
    fun apply(context: Context) {
        runCatching {
            hydrate(context)
            val metrics = calculateMetrics(context)
            applyToResources(context.resources, metrics)
        }.onFailure { e -> Log.e(TAG, "Could not enforce density on the context", e) }
    }

    /**
     * Enforces the target DPI on an [Activity], its Window decor, and Application resources.
     *
     * Called from the `ActivityLifecycleCallbacks` registered in [init], i.e. on every activity
     * create/start/resume, so it must never throw either.
     */
    fun apply(activity: Activity) {
        runCatching {
            hydrate(activity)
            val metrics = calculateMetrics(activity)
            applyToResources(activity.resources, metrics)
            activity.window?.decorView?.resources?.let { applyToResources(it, metrics) }
            applyToResources(activity.applicationContext.resources, metrics)
        }.onFailure { e -> Log.e(TAG, "Could not enforce density on the activity", e) }
    }

    /**
     * Initializes the global density adapter on the [Application] instance.
     * Hooks into Activity lifecycle and system component configuration callbacks
     * so any system-level DPI change is immediately corrected.
     *
     * Best-effort for the actual density work (logged, never fatal): this runs inside
     * `Application.onCreate()`, where a throw would leave the device without a home screen.
     * The callbacks are registered regardless, so a transient failure is corrected on the next
     * activity or configuration change.
     */
    fun init(application: Application) {
        if (isInitialized) return
        isInitialized = true

        runCatching {
            hydrate(application)
            val metrics = calculateMetrics(application)
            applyToResources(application.resources, metrics)
            Log.i(
                TAG,
                "Initialized DensityAdapter: Physical ${metrics.screenWidthPx}x${metrics.screenHeightPx}px, " +
                    "Enforced DPI=${metrics.targetDensityDpi}, Density=${metrics.targetDensity}, " +
                    "Canvas=${metrics.screenWidthDp}x${metrics.screenHeightDp}dp (System DPI immune)"
            )
        }.onFailure { e ->
            Log.e(TAG, "Density engine could not initialise; keeping the system density", e)
        }

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
