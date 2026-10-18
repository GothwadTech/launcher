package com.gothwad.launcher.data

import com.gothwad.launcher.ui.WALLPAPERS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic coverage for the auto-change wallpaper rotation (no Android runtime needed).
 */
class WallpaperRotatorTest {

    private val now = 1_000_000_000L

    private fun config(
        wallpaper: Int = 0,
        autoMinutes: Int = 60,
        lastMs: Long = 0L,
        useCustom: Boolean = false,
    ) = LauncherConfig(
        wallpaper = wallpaper,
        wallpaperAutoMinutes = autoMinutes,
        wallpaperAutoLastMs = lastMs,
        useCustomWallpaper = useCustom,
    )

    @Test
    fun offInterval_neverRotates() {
        assertNull(WallpaperRotator.rotateIfDue(config(autoMinutes = 0, lastMs = 0L), now))
    }

    @Test
    fun customWallpaper_isNeverReplaced() {
        assertNull(
            WallpaperRotator.rotateIfDue(
                config(autoMinutes = 60, useCustom = true, lastMs = 0L),
                now,
            )
        )
    }

    @Test
    fun beforePeriod_doesNotRotate() {
        val cfg = config(wallpaper = 1, autoMinutes = 60, lastMs = now - 59 * 60_000L)
        assertNull(WallpaperRotator.rotateIfDue(cfg, now))
    }

    @Test
    fun afterPeriod_rotatesToNextPreset() {
        val cfg = config(wallpaper = 1, autoMinutes = 60, lastMs = now - 61 * 60_000L)
        val rotated = WallpaperRotator.rotateIfDue(cfg, now)
        assertNotNull(rotated)
        assertEquals(2, rotated!!.wallpaper)
        assertEquals(now, rotated.wallpaperAutoLastMs)
    }

    @Test
    fun rotationWrapsAroundAtEndOfPresets() {
        val last = WALLPAPERS.size - 1
        val cfg = config(wallpaper = last, autoMinutes = 15, lastMs = now - 16 * 60_000L)
        assertEquals(0, WallpaperRotator.rotateIfDue(cfg, now)!!.wallpaper)
    }

    @Test
    fun longAbsence_advancesExactlyOneStep() {
        // Process dead for 10 hours with a 15-minute interval: still one step, no burst.
        val cfg = config(wallpaper = 1, autoMinutes = 15, lastMs = now - 10 * 3600_000L)
        assertEquals(2, WallpaperRotator.rotateIfDue(cfg, now)!!.wallpaper)
    }
}
