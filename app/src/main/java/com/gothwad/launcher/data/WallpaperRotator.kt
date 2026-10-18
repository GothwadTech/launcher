package com.gothwad.launcher.data

import android.util.Log
import com.gothwad.launcher.ui.WALLPAPERS
import kotlinx.coroutines.flow.first

/**
 * Automatic wallpaper rotation over the built-in [WALLPAPERS] presets.
 *
 * Runs entirely off the existing 1 Hz status-bar clock loop in the main process - no
 * AlarmManager, no extra service, no wakeups: the launcher is a HOME app that is awake
 * whenever anyone looks at the TV. When the process was dead for a while the rotation
 * advances exactly one step (no "catch-up burst" that flashes through five wallpapers).
 *
 * A custom wallpaper is never replaced by the rotation; the feature simply idles until the
 * user switches back to presets.
 */
object WallpaperRotator {

    private const val TAG = "WallpaperRotator"

    /** Interval choices offered in Settings (minutes; 0 = off). */
    val INTERVAL_MINUTES: List<Int> = listOf(0, 15, 60, 360, 1440)

    /**
     * Pure decision: should a rotation happen at [nowMs], and what does the config become?
     *
     * @return the rotated config, or `null` when nothing should change.
     */
    fun rotateIfDue(config: LauncherConfig, nowMs: Long): LauncherConfig? {
        if (config.wallpaperAutoMinutes <= 0) return null
        if (config.useCustomWallpaper) return null
        if (WALLPAPERS.isEmpty()) return null

        val periodMs = config.wallpaperAutoMinutes * 60_000L
        if (nowMs - config.wallpaperAutoLastMs < periodMs) return null

        val next = (config.wallpaper + 1).mod(WALLPAPERS.size)
        return config.copy(wallpaper = next, wallpaperAutoLastMs = nowMs)
    }

    /** Applies [rotateIfDue] through the store; safe to call on every clock tick. */
    suspend fun tick(store: ConfigStore) {
        val nowMs = System.currentTimeMillis()
        // Cheap no-write path first: ConfigStore.update always re-encodes and persists,
        // and this runs every minute - 99% of ticks must not touch the DataStore.
        val current = runCatching { store.flow.first() }.getOrNull() ?: return
        if (rotateIfDue(current, nowMs) == null) return
        store.update { cfg ->
            val rotated = rotateIfDue(cfg, nowMs) ?: return@update cfg
            Log.i(TAG, "Auto-rotation: wallpaper -> index ${rotated.wallpaper}")
            rotated
        }
    }
}
