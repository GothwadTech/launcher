package com.gothwad.launcher.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.net.wifi.WifiManager
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.UI_SCALES
import com.gothwad.launcher.databinding.SheetSettingsBinding
import com.gothwad.launcher.ui.AppIcons

object SettingsMenuHelper {

    fun setupMenuIcons(binding: SheetSettingsBinding) {
        val iconColor = Color.WHITE
        val chevronColor = Color.parseColor("#64748B")

        binding.iconWallpaper.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_IMAGE, iconColor))
        binding.chevronWallpaper.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconDisplay.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DISPLAY, iconColor))
        binding.chevronDisplay.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconStatusbar.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_TIME, iconColor))
        binding.chevronStatusbar.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconApps.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, iconColor))
        binding.chevronApps.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconSecurity.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK, iconColor))
        binding.chevronSecurity.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconButtonMapping.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_ACCESSIBILITY, iconColor))
        binding.chevronButtonMapping.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconNetwork.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI, iconColor))
        binding.chevronNetwork.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconDevicePrefs.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, iconColor))
        binding.chevronDevicePrefs.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconPermissions.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SHIELD, iconColor))
        binding.chevronPermissions.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))

        binding.iconWizard.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIZARD, iconColor))

        binding.iconAbout.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_INFO, iconColor))
        binding.chevronAbout.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor))
    }

    fun updateSubtitles(binding: SheetSettingsBinding, config: LauncherConfig, context: Context) {
        val wpName = if (config.useCustomWallpaper) "Custom Image" else when (config.wallpaper) {
            0 -> "Deep Space"
            1 -> "Aurora"
            2 -> "Cyberpunk"
            3 -> "Sunset"
            4 -> "Obsidian"
            else -> "Preset ${config.wallpaper}"
        }
        val scrimName = when (config.scrimMode) {
            0 -> "Full Scrim"
            1 -> "Top Only"
            2 -> "Bottom Only"
            3 -> "Both"
            4 -> "None"
            else -> "Normal"
        }
        binding.txtWallpaperSubtitle.text = "$wpName • $scrimName"

        val scaleName = when (config.iconScale) {
            0 -> "Small (120dp)"
            1 -> "Medium (150dp)"
            2 -> "Normal (190dp)"
            3 -> "Large (230dp)"
            4 -> "Huge (270dp)"
            else -> "Normal"
        }
        val uiScaleName = UI_SCALES.getOrElse(config.uiScale.coerceIn(0, UI_SCALES.size - 1)) { 1.0f }
        binding.txtDisplaySubtitle.text = "Grid layout • $scaleName • UI ${uiScaleName}x"

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
        binding.txtButtonMappingSubtitle.text = if (mappingCount > 0) {
            "$mappingCount buttons customized"
        } else {
            "Map dedicated TV remote hotkeys to apps"
        }

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifi?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "") ?: ""
            if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
                binding.txtNetworkSubtitle.text = ssid
            } else {
                binding.txtNetworkSubtitle.text = "Wi-Fi and Ethernet"
            }
        } catch (_: Exception) {
            binding.txtNetworkSubtitle.text = "Wi-Fi and Ethernet"
        }
    }
}
