package com.gothwad.launcher.ui

val ACCENTS: List<Int> = listOf(
    0xFF8AB4F8.toInt(),
    0xFF81C995.toInt(),
    0xFFFDD663.toInt(),
    0xFFF28B82.toInt(),
    0xFFD7AEFB.toInt(),
    0xFF78D9EC.toInt(),
    0xFFFF6FD8.toInt(),
    0xFFFFB74D.toInt(),
    0xFFB6FFB0.toInt(),
    0xFFE0E0E0.toInt(),
)

/**
 * A built-in gradient wallpaper.
 *
 * This list is the single source of truth for both the rendered gradient *and* its name -
 * the Settings wallpaper tiles and every subtitle are labelled from here, so a preset can
 * no longer be advertised under a name it does not render.
 */
data class WallpaperPreset(val name: String, val emoji: String, val colors: List<Int>) {
    /** Label shown in pickers, e.g. "✨ Aurora". */
    fun displayLabel(): String = if (emoji.isEmpty()) name else "$emoji $name"
}

val WALLPAPERS: List<WallpaperPreset> = listOf(
    WallpaperPreset("Midnight", "\uD83C\uDF0C", listOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt())),
    WallpaperPreset("Aurora", "✨", listOf(0xFF13547A.toInt(), 0xFF2B825B.toInt(), 0xFF80D0C7.toInt())),
    WallpaperPreset("Sunset", "\uD83C\uDF05", listOf(0xFF41295A.toInt(), 0xFF8F4A3E.toInt(), 0xFFD76D77.toInt())),
    WallpaperPreset("Deep", "\uD83C\uDF0A", listOf(0xFF000428.toInt(), 0xFF004683.toInt())),
    WallpaperPreset("Charcoal", "\uD83D\uDC5A", listOf(0xFF16161A.toInt(), 0xFF232526.toInt(), 0xFF2F3437.toInt())),
    WallpaperPreset("Ember", "\uD83D\uDD25", listOf(0xFF200122.toInt(), 0xFF6F0000.toInt(), 0xFFFF8A00.toInt())),
    WallpaperPreset("Mint", "\uD83C\uDF3F", listOf(0xFF093028.toInt(), 0xFF237A57.toInt(), 0xFF86CFAF.toInt())),
    WallpaperPreset("Lavender", "\uD83C\uDF38", listOf(0xFF3A1C71.toInt(), 0xFF8E54E9.toInt(), 0xFFE96443.toInt())),
    WallpaperPreset("Slate", "\uD83D\uDCA8", listOf(0xFF1F1C2C.toInt(), 0xFF4A4A5A.toInt(), 0xFF8E9AAF.toInt())),
    WallpaperPreset("Candy", "\uD83C\uDF6D", listOf(0xFFFF61D2.toInt(), 0xFFB44AFF.toInt(), 0xFF5A69FF.toInt())),
)

/** Preset used on a fresh install (matches `LauncherConfig.wallpaper`'s default). */
const val DEFAULT_WALLPAPER_INDEX = 1

/** True when [index] is safe to use as a [WALLPAPERS] position. */
fun isValidWallpaperIndex(index: Int): Boolean = index in WALLPAPERS.indices

/**
 * A full launcher theme: the colours every surface on the home screen derives from.
 *
 * The registry is the single source of truth (same rule as [WALLPAPERS]): the Settings
 * theme picker labels every entry from here, so a theme can never be advertised under a
 * name it does not render.
 */
data class LauncherTheme(
    val id: String,
    val name: String,
    val emoji: String,
    /** Tile/card fill for banner-less cards and behind app art. */
    val surface: Int,
    /** Home background behind the grid (when no wallpaper shows through). */
    val background: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    /** Focus ring / selection accent of this theme. */
    val accent: Int,
) {
    fun displayLabel(): String = if (emoji.isEmpty()) name else "$emoji $name"
}

val THEMES: List<LauncherTheme> = listOf(
    LauncherTheme("midnight", "Midnight", "\uD83C\uDF19", 0xFF141720.toInt(), 0xFF0F1420.toInt(), 0xFFE8EAED.toInt(), 0xFF9AA0A6.toInt(), 0xFF8AB4F8.toInt()),
    LauncherTheme("amoled", "AMOLED Black", "⬛", 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF9E9E9E.toInt(), 0xFF80CBC4.toInt()),
    LauncherTheme("arctic", "Arctic", "❄️", 0xFF1E2430.toInt(), 0xFF141A24.toInt(), 0xFFF4F7FB.toInt(), 0xFFB6C2D2.toInt(), 0xFF78D9EC.toInt()),
    LauncherTheme("cyber", "Cyberpunk", "⚡", 0xFF1A1030.toInt(), 0xFF120A22.toInt(), 0xFFFF6FD8.toInt(), 0xFFB39DDB.toInt(), 0xFFFF6FD8.toInt()),
    LauncherTheme("solar", "Solar Flare", "\uD83D\uDD25", 0xFF22140E.toInt(), 0xFF180D08.toInt(), 0xFFFFE9D6.toInt(), 0xFFE0A98A.toInt(), 0xFFF28B82.toInt()),
    LauncherTheme("forest", "Forest", "\uD83C\uDF3F", 0xFF0F1F16.toInt(), 0xFF0A1510.toInt(), 0xFFE3F2E8.toInt(), 0xFF9CC5A4.toInt(), 0xFF81C995.toInt()),
    LauncherTheme("royal", "Royal", "\uD83D\uDC51", 0xFF171026.toInt(), 0xFF100A1C.toInt(), 0xFFEFE6FF.toInt(), 0xFFB9A6D9.toInt(), 0xFFD7AEFB.toInt()),
    LauncherTheme("retro", "Retro CRT", "\uD83D\uDCFA", 0xFF0B1410.toInt(), 0xFF060C09.toInt(), 0xFFB6FFB0.toInt(), 0xFF6FBF73.toInt(), 0xFFB6FFB0.toInt()),
    LauncherTheme("mono", "Monochrome", "◽", 0xFF121212.toInt(), 0xFF0A0A0A.toInt(), 0xFFFAFAFA.toInt(), 0xFFBDBDBD.toInt(), 0xFFE0E0E0.toInt()),
    LauncherTheme("ocean", "Ocean", "\uD83C\uDF0A", 0xFF0A1A24.toInt(), 0xFF061018.toInt(), 0xFFE0F4FF.toInt(), 0xFF8FB8CB.toInt(), 0xFF4FC3F7.toInt()),
)

/** Index into [THEMES] used on a fresh install. */
const val DEFAULT_THEME_INDEX = 0

/** True when [index] is safe to use as a [THEMES] position. */
fun isValidThemeIndex(index: Int): Boolean = index in THEMES.indices

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

/** Blends [over] onto [base] with [alpha] (0..255). Used for fill-style focus effects. */
fun blendColors(base: Int, over: Int, alpha: Int): Int {
    val a = alpha.coerceIn(0, 255)
    val inv = 255 - a
    val r = (((base shr 16 and 0xFF) * inv + (over shr 16 and 0xFF) * a) / 255)
    val g = (((base shr 8 and 0xFF) * inv + (over shr 8 and 0xFF) * a) / 255)
    val b = (((base and 0xFF) * inv + (over and 0xFF) * a) / 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
