package com.gothwad.launcher.ui

val ACCENTS: List<Int> = listOf(
    0xFF8AB4F8.toInt(),
    0xFF81C995.toInt(),
    0xFFFDD663.toInt(),
    0xFFF28B82.toInt(),
    0xFFD7AEFB.toInt(),
    0xFF78D9EC.toInt(),
)

data class WallpaperPreset(val name: String, val colors: List<Int>)

val WALLPAPERS: List<WallpaperPreset> = listOf(
    WallpaperPreset("Midnight", listOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt())),
    WallpaperPreset("Aurora", listOf(0xFF13547A.toInt(), 0xFF2B825B.toInt(), 0xFF80D0C7.toInt())),
    WallpaperPreset("Sunset", listOf(0xFF41295A.toInt(), 0xFF8F4A3E.toInt(), 0xFFD76D77.toInt())),
    WallpaperPreset("Deep", listOf(0xFF000428.toInt(), 0xFF004683.toInt())),
    WallpaperPreset("Charcoal", listOf(0xFF16161A.toInt(), 0xFF232526.toInt(), 0xFF2F3437.toInt())),
)

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
