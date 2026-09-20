package com.gothwad.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import com.gothwad.launcher.ui.tileColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

data class AppEntry(
    val pkg: String,
    val label: String,
    /** 16:9 leanback banner if the app ships one, pre-rasterized */
    val banner: Bitmap?,
    val icon: Bitmap?,
    val autoCategory: String,
    /** Tile color precomputed at scan time — ARGB int. */
    val tile: Int,
    /** lastUpdateTime of the package — used to reuse entries across rescans. */
    val stamp: Long,
    /** firstInstallTime — tiebreak so a newly installed app sorts last in its section. */
    val firstInstall: Long,
)

object AppRepository {

    /** Reserved id for the auto-filled "All apps" section (every launchable app). */
    const val ALL_APPS_ID = "__all__"

    /** Well-known packages -> category id */
    private val KNOWN = mapOf(
        "com.netflix.ninja" to "streaming",
        "com.google.android.youtube.tv" to "streaming",
        "com.amazon.amazonvideo.livingroom" to "streaming",
        "com.disney.disneyplus" to "streaming",
        "com.hbo.hbonow" to "streaming",
        "com.wbd.stream" to "streaming",
        "com.plexapp.android" to "streaming",
        "org.videolan.vlc" to "streaming",
        "tv.emby.embyatv" to "streaming",
        "com.jellyfin.androidtv" to "streaming",
        "com.spotify.tv.android" to "music",
        "com.pandora.android.atv" to "music",
        "deezer.android.tv" to "music",
        "com.retroarch" to "games",
        "com.retroarch.aarch64" to "games",
        "org.ppsspp.ppsspp" to "games",
        "org.dolphinemu.dolphinemu" to "games",
        "com.valvesoftware.steamlink" to "games",
        "com.nvidia.geforcenow" to "games",
        "com.moonlight_stream.moonlight" to "games",
    )

    private var memoryCache: List<AppEntry>? = null

    /**
     * Scans launchable apps. Banners and icons are pre-rasterized and cached to
     * disk as LOSSLESS WebP so subsequent cold starts only do disk decodes, not
     * package-manager IPC + drawable inflations.
     *
     * Returns the same List instance if the set of installed packages and their
     * timestamps didn't change, letting downstream collectors skip work.
     */
    suspend fun scan(context: Context): List<AppEntry> = coroutineScope {
        val pm = context.packageManager
        val cacheDir = File(context.cacheDir, "app_art").apply { mkdirs() }

        // Both intent filters — TV leanback first, touch/phone as fallback
        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            0,
        )
        val regular = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0,
        )

        // Deduplicate by package name, prefer leanback resolve info
        val byPkg = LinkedHashMap<String, android.content.pm.ResolveInfo>()
        for (r in leanback) byPkg[r.activityInfo.packageName] = r
        for (r in regular) if (!byPkg.containsKey(r.activityInfo.packageName)) {
            byPkg[r.activityInfo.packageName] = r
        }
        // Exclude ourselves
        byPkg.remove(context.packageName)

        // Pre-fetch timestamps in batch — cheap, avoids per-item PM roundtrips later
        val meta = byPkg.keys.associateWith { pkg ->
            runCatching {
                val pi = pm.getPackageInfo(pkg, 0)
                pi.lastUpdateTime to pi.firstInstallTime
            }.getOrElse { 0L to 0L }
        }

        // Fast path: if the cached list matches packages + timestamps, return it
        val cached = memoryCache
        if (cached != null && cached.size == byPkg.size) {
            val valid = cached.all { e ->
                val pair = meta[e.pkg]
                pair != null && pair.first == e.stamp && pair.second == e.firstInstall
            }
            if (valid) return@coroutineScope cached
        }

        val validCacheNames = HashSet<String>(byPkg.size * 2)

        val entries = byPkg.values.map { ri ->
            async(Dispatchers.IO) {
                val pkg = ri.activityInfo.packageName
                val (stamp, firstInstall) = meta[pkg] ?: (0L to 0L)
                val ai = ri.activityInfo

                // Leanback banner (16:9, fixed height 180px is plenty for 1080p/4K TV grids)
                val bannerName = "${pkg}_b_$stamp.webp"
                validCacheNames.add(bannerName)
                val banner = cachedBitmap(cacheDir, bannerName, Bitmap.Config.RGB_565) {
                    runCatching {
                        val d = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
                        d?.let {
                            val w = 320
                            val h = 180
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                            val canvas = android.graphics.Canvas(bmp)
                            it.setBounds(0, 0, w, h)
                            it.draw(canvas)
                            bmp
                        }
                    }.getOrNull()
                }

                // App icon rendered with continuous squircle (square curve) corners, preventing circle clipping
                val iconName = "${pkg}_sq_$stamp.webp"
                val icon = cachedBitmap(cacheDir, iconName, Bitmap.Config.ARGB_8888) {
                    runCatching {
                        val d = ai.loadIcon(pm) ?: ai.applicationInfo.loadIcon(pm) ?: return@runCatching null
                        renderSquareCurveBitmap(d, 128)
                    }.getOrNull()
                }
                if (icon != null) validCacheNames.add(iconName)
                // Upload textures ahead of first draw so the GPU never stalls mid-frame
                banner?.prepareToDraw()
                icon?.prepareToDraw()

                AppEntry(
                    pkg = pkg,
                    label = runCatching { ri.loadLabel(pm)?.toString() }.getOrNull() ?: pkg,
                    banner = banner,
                    icon = icon,
                    autoCategory = autoCategory(ai.applicationInfo),
                    tile = tileColor(pkg),
                    stamp = stamp,
                    firstInstall = firstInstall,
                )
            }
        }.awaitAll()

        // Drop cache entries for uninstalled or updated apps
        cacheDir.listFiles()?.forEach { f ->
            if (f.name !in validCacheNames) runCatching { f.delete() }
        }
        val result = entries.sortedBy { it.label.lowercase() }
        if (result == memoryCache) return@coroutineScope memoryCache!!
        memoryCache = result
        result
    }

    /**
     * Reads a bitmap from [dir]/[name]; on miss, runs [create] and persists it
     * as LOSSLESS WebP.
     */
    private fun cachedBitmap(
        dir: File,
        name: String,
        config: Bitmap.Config,
        create: () -> Bitmap?,
    ): Bitmap? {
        val f = File(dir, name)
        if (f.exists()) {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = config }
            BitmapFactory.decodeFile(f.absolutePath, opts)?.let { return it }
        }
        val bmp = create() ?: return null
        runCatching {
            f.outputStream().use { out ->
                if (Build.VERSION.SDK_INT >= 30) {
                    bmp.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, out)
                } else {
                    @Suppress("DEPRECATION")
                    bmp.compress(Bitmap.CompressFormat.WEBP, 100, out)
                }
            }
        }
        return bmp
    }

    /**
     * Renders a drawable into a smooth square-curve (squircle) bitmap.
     * Prevents Android's AdaptiveIconDrawable from applying its default round/circle mask.
     */
    private fun renderSquareCurveBitmap(d: Drawable, sizePx: Int = 128): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val w = sizePx.toFloat()
        val h = sizePx.toFloat()

        // 22% continuous corner squircle path (square curve)
        val cornerRadius = w * 0.22f
        val squirclePath = runCatching {
            RoundedPolygon.rectangle(
                width = w,
                height = h,
                rounding = CornerRounding(cornerRadius, 0.6f),
                centerX = w / 2f,
                centerY = h / 2f
            ).toPath()
        }.getOrElse {
            Path().apply {
                addRoundRect(RectF(0f, 0f, w, h), cornerRadius, cornerRadius, Path.Direction.CW)
            }
        }

        canvas.save()
        canvas.clipPath(squirclePath)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d is AdaptiveIconDrawable) {
            // By drawing background and foreground directly rather than d.draw(canvas),
            // we bypass the system's circle mask and cleanly render within our square curve!
            d.background?.let { bg ->
                bg.setBounds(0, 0, sizePx, sizePx)
                bg.draw(canvas)
            }
            d.foreground?.let { fg ->
                fg.setBounds(0, 0, sizePx, sizePx)
                fg.draw(canvas)
            }
        } else {
            d.setBounds(0, 0, sizePx, sizePx)
            d.draw(canvas)
        }

        canvas.restore()
        return bmp
    }

    private fun autoCategory(ai: ApplicationInfo?): String {
        ai ?: return "apps"
        KNOWN[ai.packageName]?.let { return it }
        if (Build.VERSION.SDK_INT >= 26) {
            when (ai.category) {
                ApplicationInfo.CATEGORY_VIDEO -> return "streaming"
                ApplicationInfo.CATEGORY_GAME -> return "games"
                ApplicationInfo.CATEGORY_AUDIO -> return "music"
                ApplicationInfo.CATEGORY_IMAGE -> return "apps"
                ApplicationInfo.CATEGORY_NEWS -> return "apps"
                ApplicationInfo.CATEGORY_MAPS -> return "apps"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "apps"
            }
        }
        if ((ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0) return "games"
        return "apps"
    }

    fun clearMemoryCache() {
        memoryCache = null
    }
}
