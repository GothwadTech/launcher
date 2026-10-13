package com.gothwad.launcher.ui

val ACCENTS: List<Int> = listOf(
    0xFF8AB4F8.toInt(),
    0xFF81C995.toInt(),
    0xFFFDD663.toInt(),
    0xFFF28B82.toInt(),
    0xFFD7AEFB.toInt(),
    0xFF78D9EC.toInt(),
)

/**
 * A built-in gradient wallpaper.
 *
 * This list is the single source of truth for both the rendered gradient *and* its name -
 * the Settings wallpaper buttons and every subtitle are labelled from here, so a preset can
 * no longer be advertised under a name it does not render (the buttons used to promise
 * "Deep Space / Cyberpunk Neon / Obsidian Minimal" while drawing Midnight / Sunset /
 * Charcoal).
 */
data class WallpaperPreset(val name: String, val emoji: String, val colors: List<Int>) {
    /** Label shown in pickers, e.g. "✨ Aurora". */
    fun displayLabel(): String = if (emoji.isEmpty()) name else "$emoji $name"
}

val WALLPAPERS: List<WallpaperPreset> = listOf(
    WallpaperPreset("Midnight", "🌌", listOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt())),
    WallpaperPreset("Aurora", "✨", listOf(0xFF13547A.toInt(), 0xFF2B825B.toInt(), 0xFF80D0C7.toInt())),
    WallpaperPreset("Sunset", "🌅", listOf(0xFF41295A.toInt(), 0xFF8F4A3E.toInt(), 0xFFD76D77.toInt())),
    WallpaperPreset("Deep", "🌊", listOf(0xFF000428.toInt(), 0xFF004683.toInt())),
    WallpaperPreset("Charcoal", "🖤", listOf(0xFF16161A.toInt(), 0xFF232526.toInt(), 0xFF2F3437.toInt())),
)

/** Preset used on a fresh install (matches `LauncherConfig.wallpaper`'s default). */
const val DEFAULT_WALLPAPER_INDEX = 1

/** Deterministic tile color for apps that ship no banner artwork. */
fun tileColor(pkg: String): Int {
    val palette = listOf(
        0xFF37474F.toInt(), 0xFF4E342E.toInt(), 0xFF1B5E20.toInt(),
        0xFF0D47A1.toInt(), 0xFF4A148C.toInt(), 0xFF880E4F.toInt(),
        0xFF3E2723.toInt(), 0xFF263238.toInt(), 0xFF33691E.toInt(),
    )
    var h = 0
    for (c in pkg) h = h * 31 + c.code
    return palette[((h % palette.size) + palette.size) % palette.size]
}
