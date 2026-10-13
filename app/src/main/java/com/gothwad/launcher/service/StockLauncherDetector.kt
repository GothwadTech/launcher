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

    /**
     * Checks if a package is a system utility, settings, system UI, or installer that
     * must NEVER be suppressed as a stock launcher.
     */
    fun isSystemOrSettingsPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        if (lower.contains("settings")) return true
        if (lower.contains("packageinstaller")) return true
        if (lower.contains("permissioncontroller")) return true
        if (lower.contains("inputmethod") || lower.contains(".ime")) return true
        if (lower == "com.android.systemui" ||
            lower == "com.android.vending" ||
            lower == "com.google.android.gms") return true
        return false
    }

    @Volatile
    private var homeHandlerCache: Pair<Long, Set<String>> = 0L to emptySet()

    private fun homeHandlerPackages(context: Context): Set<String> {
        val (stamp, cached) = homeHandlerCache
        val now = SystemClock.elapsedRealtime()
        if (now - stamp < 60_000L && cached.isNotEmpty()) return cached

        val packages = runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { resolveInfo ->
                    val actInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                    val actName = actInfo.name ?: ""
                    val pkgName = actInfo.packageName ?: return@mapNotNull null
                    // Filter out AOSP FallbackHome stub and any settings/system packages
                    if (actName.contains("FallbackHome", ignoreCase = true)) return@mapNotNull null
                    if (isSystemOrSettingsPackage(pkgName)) return@mapNotNull null
                    if (pkgName.equals(context.packageName, ignoreCase = true)) return@mapNotNull null
                    pkgName
                }
                .toSet()
        }.getOrDefault(emptySet())

        homeHandlerCache = now to packages
        return packages
    }

    /**
     * True when this package is the one Android currently resolves for the HOME intent, i.e.
     * the user picked Gothwad Launcher as the default launcher.
     *
     * Shared by `BootReceiver` and the watchdog: whether it is acceptable to pull the launcher
     * to the foreground depends on it.
     */
    fun isDefaultHomeApp(context: Context): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(intent, 0)
            ?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)

    fun isStockTvLauncher(pkg: String): Boolean {
        if (isSystemOrSettingsPackage(pkg)) return false
        return STOCK_LAUNCHERS.any { it.equals(pkg, ignoreCase = true) }
    }

    fun isStockTvLauncher(context: Context, pkg: String): Boolean {
        if (pkg.equals(context.packageName, ignoreCase = true)) return false
        if (isSystemOrSettingsPackage(pkg)) return false
        if (isStockTvLauncher(pkg)) return true
        return homeHandlerPackages(context).any { it.equals(pkg, ignoreCase = true) }
    }
}
