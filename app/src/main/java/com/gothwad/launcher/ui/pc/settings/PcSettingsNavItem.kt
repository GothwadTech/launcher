package com.gothwad.launcher.ui.pc.settings

import androidx.annotation.DrawableRes
import com.gothwad.launcher.R

data class PcSettingsNavItem(
    val id: Int,
    val title: String,
    @DrawableRes val iconRes: Int
)

object PcSettingsConstants {
    const val TAB_SYSTEM = 0
    const val TAB_BLUETOOTH = 1
    const val TAB_NETWORK = 2
    const val TAB_PERSONALISATION = 3
    const val TAB_APPS = 4
    const val TAB_ACCOUNTS = 5
    const val TAB_TIME = 6
    const val TAB_GAMING = 7
    const val TAB_ACCESSIBILITY = 8
    const val TAB_PRIVACY = 9
    const val TAB_UPDATE = 10

    val NAV_ITEMS: List<PcSettingsNavItem> = listOf(
        PcSettingsNavItem(TAB_SYSTEM, "System", R.drawable.ic_win_system),
        PcSettingsNavItem(TAB_BLUETOOTH, "Bluetooth & devices", R.drawable.ic_win_bluetooth),
        PcSettingsNavItem(TAB_NETWORK, "Network & internet", R.drawable.ic_win_network),
        PcSettingsNavItem(TAB_PERSONALISATION, "Personalisation", R.drawable.ic_win_personalisation),
        PcSettingsNavItem(TAB_APPS, "Apps", R.drawable.ic_win_apps),
        PcSettingsNavItem(TAB_ACCOUNTS, "Accounts", R.drawable.ic_win_accounts),
        PcSettingsNavItem(TAB_TIME, "Time & language", R.drawable.ic_win_time),
        PcSettingsNavItem(TAB_GAMING, "Gaming", R.drawable.ic_win_gaming),
        PcSettingsNavItem(TAB_ACCESSIBILITY, "Accessibility", R.drawable.ic_win_accessibility),
        PcSettingsNavItem(TAB_PRIVACY, "Privacy & security", R.drawable.ic_win_privacy),
        PcSettingsNavItem(TAB_UPDATE, "Windows Update", R.drawable.ic_win_update),
    )
}
