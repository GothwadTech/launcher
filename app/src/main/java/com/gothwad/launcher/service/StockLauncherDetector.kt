package com.gothwad.launcher.service

import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Utility to identify stock/OEM launchers and boot-ad shims.
 */
object StockLauncherDetector {

    val STOCK_LAUNCHERS = setOf(
        // Google TV / Android TV
        "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher",
        "com.google.android.tungsten.setupwraith",
        // JioFiber / Jio STB launchers
        "com.jio.media.stblauncher",
        "com.jio.media.jiohome",
        "com.ril.jio.stb",
        // Airtel Xstream STB launchers
        "com.airtel.smartbox",
        "tv.airtel.smartbox.launcher",
        "com.airtel.tv.launcher",
        // Tata Play / Dish / D2H / regional operator launchers
        "com.tatasky.stb",
        "com.dishtv.smrt",
        "com.d2h.stream",
        "com.nes.tvlauncher",
        "com.sdmc.launcher",
        // Chipset / OEM TV launchers
        "com.geniatech.launcher",
        "com.amlogic.tvlauncher",
        "com.realtek.tvlauncher",
        "com.amazon.tv.launcher",
        "com.amazon.firehomestarter",
        "com.xiaomi.mitv.tvhome",
        "com.mitv.tvhome",
        "com.hisense.tv.launcher",
        "com.droidlogic.tv.launcher"
    )

    @Volatile
    private var homeHandlerCache: Pair<Long, Set<String>> = 0L to emptySet()

    private fun homeHandlerPackages(context: Context): Set<String> {
        val (stamp, cached) = homeHandlerCache
        val now = SystemClock.elapsedRealtime()
        if (now - stamp < 60_000L && cached.isNotEmpty()) return cached

        val packages = runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())

        homeHandlerCache = now to packages
        return packages
    }

    fun isStockTvLauncher(pkg: String): Boolean =
        STOCK_LAUNCHERS.any { it.equals(pkg, ignoreCase = true) }

    fun isStockTvLauncher(context: Context, pkg: String): Boolean {
        if (pkg.equals(context.packageName, ignoreCase = true)) return false
        if (isStockTvLauncher(pkg)) return true
        return homeHandlerPackages(context).any { it.equals(pkg, ignoreCase = true) }
    }
}
