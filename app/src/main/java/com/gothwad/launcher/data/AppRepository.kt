package com.gothwad.launcher.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import com.gothwad.launcher.ui.tileColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

data class AppEntry(
    val pkg: String,
    val label: String,
    /** 16:9 leanback banner if the app ships one, pre-rasterized */
    val banner: Bitmap?,
    val icon: Bitmap?,
    /** Tile color precomputed at scan time — ARGB int. */
    val tile: Int,
    /** lastUpdateTime of the package — used to reuse entries across rescans. */
    val stamp: Long,
    /** firstInstallTime — tiebreak so a newly installed app sorts last in its section. */
    val firstInstall: Long,
)

object AppRepository {

    /**
     * Read from whichever thread calls [scan] and written from an IO coroutine, so it must
     * be `@Volatile` - otherwise a main-thread caller can keep observing a stale list.
     */
    @Volatile
    private var memoryCache: List<AppEntry>? = null

    /**
     * Serialises whole scans. `MainActivity` and `TvLauncherFragment` both scan on a cold
     * start; without this the two full PackageManager + bitmap passes ran concurrently
     * (doubling the work on the exact path this project optimises) and raced each other's
     * cache-prune step. The loser now waits and hits the memory-cache fast path instead.
     */
    private val scanMutex = Mutex()

    /**
     * Caps how many apps are decoded/rasterised at once. Unbounded `async(IO)` over ~200
     * packages used to inflate hundreds of drawables concurrently - on a 2 GB box that is
     * a needless memory spike during the scan.
     */
    private val decodeSemaphore = Semaphore(6)

    /**
     * Scans launchable apps. Banners and icons are pre-rasterized and cached to
     * disk as LOSSLESS WebP so subsequent cold starts only do disk decodes, not
     * package-manager IPC + drawable inflations.
     *
     * Returns the same List instance if the set of installed packages and their
     * timestamps didn't change, letting downstream collectors skip work.
     */
    suspend fun scan(context: Context): List<AppEntry> = scanMutex.withLock {
        withContext(Dispatchers.IO) {
            scanInternal(context)
        }
    }

    private suspend fun scanInternal(context: Context): List<AppEntry> = coroutineScope {
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

        // Mutated from several `async(Dispatchers.IO)` blocks at once - a plain HashSet
        // would corrupt (and then the prune step below deletes artwork that is still live).
        val validCacheNames = java.util.Collections.synchronizedSet(HashSet<String>(byPkg.size * 2))

        val entries = byPkg.values.map { ri ->
            async(Dispatchers.IO) {
                decodeSemaphore.withPermit {
                    val pkg = ri.activityInfo.packageName
                    val (stamp, firstInstall) = meta[pkg] ?: (0L to 0L)
                    val ai = ri.activityInfo

                    // Leanback banner (16:9, fixed height 180px is plenty for 1080p/4K TV grids)
                    val bannerName = "${pkg}_b_$stamp.webp"
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
                    // Only keep cache files that decoded/produced a bitmap: a stale corrupt
                    // file must be prunable, not protected by a name that never loads.
                    if (banner != null) validCacheNames.add(bannerName)

                    // App icon (only decoded/saved if there's no banner, or as square fallback)
                    val iconName = "${pkg}_i_$stamp.webp"
                    val icon = cachedBitmap(cacheDir, iconName, Bitmap.Config.ARGB_8888) {
                        runCatching {
                            val d = ai.loadIcon(pm) ?: ai.applicationInfo.loadIcon(pm)
                            d.toBitmap(width = 128, height = 128, config = Bitmap.Config.ARGB_8888)
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
                        tile = tileColor(pkg),
                        stamp = stamp,
                        firstInstall = firstInstall,
                    )
                }
            }
        }.awaitAll()

        // Drop cache entries for uninstalled or updated apps
        cacheDir.listFiles()?.forEach { f ->
            if (f.name !in validCacheNames) runCatching { f.delete() }
        }
        val result = entries.sortedBy { it.label.lowercase() }
        // Single volatile read: `clearMemoryCache()` on another thread must not be able to
        // turn this into a NullPointerException.
        val previous = memoryCache
        if (previous != null && previous == result) return@coroutineScope previous
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

    fun clearMemoryCache() {
        memoryCache = null
    }
}
