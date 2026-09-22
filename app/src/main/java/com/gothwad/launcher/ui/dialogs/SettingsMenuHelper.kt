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
        val chevronColor = Color.parseColor("#94A3B8")
        val chevronDrawable = AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, chevronColor)

        binding.chevronWallpaper.setImageDrawable(chevronDrawable)
        binding.chevronDisplay.setImageDrawable(chevronDrawable)
        binding.chevronStatusbar.setImageDrawable(chevronDrawable)
        binding.chevronApps.setImageDrawable(chevronDrawable)
        binding.chevronSecurity.setImageDrawable(chevronDrawable)
        binding.chevronButtonMapping.setImageDrawable(chevronDrawable)
        binding.chevronNetwork.setImageDrawable(chevronDrawable)
        binding.chevronDevicePrefs.setImageDrawable(chevronDrawable)
        binding.chevronPermissions.setImageDrawable(chevronDrawable)
        binding.chevronWizard.setImageDrawable(chevronDrawable)
        binding.chevronAbout.setImageDrawable(chevronDrawable)
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
