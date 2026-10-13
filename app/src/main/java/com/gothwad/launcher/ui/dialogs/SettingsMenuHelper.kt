package com.gothwad.launcher.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Build
import com.gothwad.launcher.R
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.UI_SCALES
import com.gothwad.launcher.databinding.SheetSettingsBinding
import com.gothwad.launcher.ui.AppIcons
import com.gothwad.launcher.ui.WALLPAPERS

object SettingsMenuHelper {

    fun setupMenuIcons(binding: SheetSettingsBinding) {
        val chevronColor = Color.parseColor("#94A3B8")
        val chevronDrawable = AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor)

        // 4 Main Categories
        binding.chevronCatAppearance.setImageDrawable(chevronDrawable)
        binding.chevronCatApps.setImageDrawable(chevronDrawable)
        binding.chevronCatRemote.setImageDrawable(chevronDrawable)
        binding.chevronCatDevice.setImageDrawable(chevronDrawable)

        // Sub-page items
        binding.chevronWallpaper.setImageDrawable(chevronDrawable)
        binding.chevronDisplay.setImageDrawable(chevronDrawable)
        binding.chevronStatusbar.setImageDrawable(chevronDrawable)
        binding.chevronApps.setImageDrawable(chevronDrawable)
        binding.chevronSecurity.setImageDrawable(chevronDrawable)
        binding.chevronNetwork.setImageDrawable(chevronDrawable)
        binding.chevronDevicePrefs.setImageDrawable(chevronDrawable)
        binding.chevronPermissions.setImageDrawable(chevronDrawable)
        binding.chevronCatAbout.setImageDrawable(chevronDrawable)
    }

    fun updateSubtitles(binding: SheetSettingsBinding, config: LauncherConfig, context: Context) {
        // Read the name from the preset that is actually rendered - this list used to be a
        // second, stale copy of the wallpaper names.
        val wpName = if (config.useCustomWallpaper) {
            context.getString(R.string.wallpaper_custom)
        } else {
            WALLPAPERS.getOrElse(config.wallpaper.coerceIn(0, WALLPAPERS.size - 1)) {
                WALLPAPERS.first()
            }.name
        }
        val scrimName = context.getString(
            when (config.scrimMode) {
                1 -> R.string.scrim_top_only
                2 -> R.string.scrim_bottom_only
                3 -> R.string.scrim_both_edges
                4 -> R.string.scrim_none
                else -> R.string.scrim_full
            }
        )
        binding.txtWallpaperSubtitle.text = "$wpName • $scrimName"

        val uiScaleName = UI_SCALES.getOrElse(config.uiScale.coerceIn(0, UI_SCALES.size - 1)) { 1.0f }
        binding.txtDisplaySubtitle.text = "6-Column Grid • UI ${uiScaleName}x"

        val sbVisible = if (config.showStatusBar) "Visible" else "Hidden"
        val clockFormat = if (config.h24) "24h format" else "12h format"
        binding.txtStatusbarSubtitle.text = "$sbVisible • $clockFormat"

        val hiddenCount = config.hidden.size
        binding.txtAppsSubtitle.text = if (hiddenCount > 0) "$hiddenCount apps protected" else "Recent apps • Auto-sort"
        binding.txtHiddenAppsCount.text = "$hiddenCount apps configured in secret vault"

        val activeLocks = mutableListOf<String>()
        if (config.appLock.enabled) activeLocks.add("App")
        if (config.hiddenAppsLock.enabled) activeLocks.add("Vault")
        binding.txtSecuritySubtitle.text = if (activeLocks.isNotEmpty()) {
            activeLocks.joinToString(" & ") + " Lock Active"
        } else {
            "No Locks Active"
        }

        val mappingCount = config.buttonMap.size

        var networkLabel = "Wi-Fi and Ethernet"
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifi?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "") ?: ""
            if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                networkLabel = ssid
                binding.txtNetworkSubtitle.text = ssid
            } else {
                binding.txtNetworkSubtitle.text = "Wi-Fi and Ethernet"
            }
        } catch (_: Exception) {
            binding.txtNetworkSubtitle.text = "Wi-Fi and Ethernet"
        }

        // Subtitles for the 4 Main Root Categories
        binding.txtCatAppearanceSubtitle.text = "$wpName • UI ${uiScaleName}x • $clockFormat"
        binding.txtCatAppsSubtitle.text = if (activeLocks.isNotEmpty() || hiddenCount > 0) {
            "$hiddenCount protected apps • ${if (activeLocks.isNotEmpty()) activeLocks.joinToString("&") + " Lock" else "Vault"}"
        } else {
            "Apps vault, recent apps & PIN locks"
        }
        binding.txtCatRemoteSubtitle.text = if (mappingCount > 0) {
            "$mappingCount hotkeys customized"
        } else {
            "Map TV remote hotkeys to favorite apps"
        }
        binding.txtCatDeviceSubtitle.text = if (networkLabel != "Wi-Fi and Ethernet") {
            "$networkLabel • Android TV & Launcher Info"
        } else {
            "Wi-Fi, Android TV settings, launcher info & wizard"
        }
        // Was hard-coded as "v1.0.6 (Build 7)", which went stale the moment CI bumped the
        // version (the app was reporting 1.0.6 while running 1.0.2x).
        val (versionName, versionCode) = appVersion(context)
        binding.txtCatAboutSubtitle.text = context.getString(
            R.string.settings_about_subtitle,
            versionName,
            versionCode.toString(),
        )
    }

    /**
     * Version name + code of the running APK, read from the PackageManager.
     *
     * `BuildConfig` is not generated in this project (AGP 8 leaves `buildFeatures.buildConfig`
     * off), so the package info is the only source that cannot go stale.
     */
    private fun appVersion(context: Context): Pair<String, Int> = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
        (info.versionName ?: "?") to code
    }.getOrDefault("?" to 0)
}
