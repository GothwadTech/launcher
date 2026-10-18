package com.gothwad.launcher.data

import com.gothwad.launcher.ui.ACCENTS
import com.gothwad.launcher.ui.THEMES
import com.gothwad.launcher.ui.WALLPAPERS
import com.gothwad.launcher.ui.blendColors
import com.gothwad.launcher.ui.isValidThemeIndex
import com.gothwad.launcher.ui.isValidWallpaperIndex
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the customization registries + config defaults (themes/layout/icons/backup). */
class CustomizationConfigTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun themeRegistryHasTenUniqueThemes() {
        assertEquals(10, THEMES.size)
        assertEquals(10, THEMES.map { it.id }.distinct().size)
        assertTrue(THEMES.all { it.name.isNotBlank() })
    }

    @Test
    fun wallpaperRegistryHasTenPresets() {
        assertEquals(10, WALLPAPERS.size)
        assertTrue(WALLPAPERS.all { it.colors.size >= 2 })
        assertTrue(isValidWallpaperIndex(0))
        assertTrue(isValidWallpaperIndex(WALLPAPERS.size - 1))
    }

    @Test
    fun accentRegistryHasTenColours() {
        assertEquals(10, ACCENTS.size)
        assertEquals(10, ACCENTS.distinct().size)
    }

    @Test
    fun themeIndexValidation() {
        assertTrue(isValidThemeIndex(0))
        assertTrue(isValidThemeIndex(THEMES.size - 1))
        assertTrue(!isValidThemeIndex(-1))
        assertTrue(!isValidThemeIndex(THEMES.size))
    }

    @Test
    fun blendColorsEndpoints() {
        val base = 0xFF102030.toInt()
        val over = 0xFFF0E0D0.toInt()
        assertEquals(base, blendColors(base, over, 0))
        assertEquals(over, blendColors(base, over, 255))
        // Alpha is always forced opaque.
        assertEquals(0xFF, blendColors(base, over, 128) ushr 24)
    }

    @Test
    fun newCustomizationFieldsRoundTrip() {
        val config = LauncherConfig(
            themeIndex = 3,
            followWallpaperPalette = true,
            clockStyle = 2,
            dateStyle = 1,
            layoutMode = 2,
            gridColumns = 5,
            iconSizePct = 125,
            showTileLabels = false,
            labelSizeSp = 14,
            focusScalePct = 120,
            focusEffect = 1,
            openAnimation = 3,
            animSpeed = 2,
            reduceMotion = true,
            showFavoritesRow = true,
            favorites = listOf("com.example.tv"),
            showRecentRow = true,
            appSort = 2,
            nightLight = true,
            nightLightStrength = 70,
            perfProfile = 0,
        )
        val decoded = json.decodeFromString(
            LauncherConfig.serializer(),
            json.encodeToString(LauncherConfig.serializer(), config),
        )
        assertEquals(config, decoded)
    }

    @Test
    fun defaultsAreSafeForExistingUsers() {
        val d = LauncherConfig()
        assertTrue(isValidThemeIndex(d.themeIndex))
        assertTrue(isValidWallpaperIndex(d.wallpaper))
        assertTrue(d.gridColumns in 3..8)
        assertTrue(d.iconSizePct in 60..160)
        assertTrue(d.focusScalePct in 100..140)
        assertTrue(d.layoutMode in 0..3)
        assertTrue(d.focusEffect in 0..3)
        assertTrue(d.perfProfile in 0..2)
    }
}
