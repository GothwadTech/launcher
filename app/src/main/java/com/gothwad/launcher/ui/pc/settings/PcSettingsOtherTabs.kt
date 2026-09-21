package com.gothwad.launcher.ui.pc.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.gothwad.launcher.R
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.ItemPcSettingCardBinding
import com.gothwad.launcher.databinding.LayoutPcSettingsGenericBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PcSettingsOtherTabs(
    private val context: Context,
    private val scope: CoroutineScope,
    private var config: LauncherConfig,
    private val onOpenLockSetup: () -> Unit
) {

    fun createPageView(inflater: LayoutInflater, tabId: Int): View {
        val binding = LayoutPcSettingsGenericBinding.inflate(inflater, null, false)
        when (tabId) {
            PcSettingsConstants.TAB_BLUETOOTH -> buildBluetoothPage(binding)
            PcSettingsConstants.TAB_NETWORK -> buildNetworkPage(binding)
            PcSettingsConstants.TAB_APPS -> buildAppsPage(binding)
            PcSettingsConstants.TAB_ACCOUNTS -> buildAccountsPage(binding)
            PcSettingsConstants.TAB_TIME -> buildTimePage(binding)
            PcSettingsConstants.TAB_GAMING -> buildGamingPage(binding)
            PcSettingsConstants.TAB_ACCESSIBILITY -> buildAccessibilityPage(binding)
            PcSettingsConstants.TAB_PRIVACY -> buildPrivacyPage(binding)
            PcSettingsConstants.TAB_UPDATE -> buildUpdatePage(binding)
        }
        return binding.root
    }

    private fun buildBluetoothPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Bluetooth & devices"
        addCard(binding, R.drawable.ic_win_bluetooth, "Bluetooth", "Discoverable as Gothwad-PC") {
            openIntent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }
        addCard(binding, R.drawable.ic_add, "Add device", "Pair Bluetooth audio, mouse, keyboard") {
            openIntent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }
        addCard(binding, R.drawable.ic_win_system, "Devices", "Mouse, keyboard, pen, audio, displays")
        addCard(binding, R.drawable.ic_win_system, "Printers & scanners", "Print preferences, network printers")
        addCard(binding, R.drawable.ic_win_system, "Cameras", "Connected cameras, privacy shutter")
        addCard(binding, R.drawable.ic_win_system, "Mouse & Touchpad", "Pointer speed, scrolling, gestures")
        addCard(binding, R.drawable.ic_win_system, "USB", "USB connection notifications and charging")
    }

    private fun buildNetworkPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Network & internet"
        addCard(binding, R.drawable.ic_win_network, "Wi-Fi", "Connected, secured • Manage known networks") {
            openIntent(Settings.ACTION_WIFI_SETTINGS)
        }
        addCard(binding, R.drawable.ic_win_privacy, "VPN", "Configure virtual private networks") {
            openIntent(Settings.ACTION_VPN_SETTINGS)
        }
        addCard(binding, R.drawable.ic_win_network, "Mobile hotspot", "Share internet connection over Wi-Fi") {
            openIntent(Settings.ACTION_WIRELESS_SETTINGS)
        }
        addCard(binding, R.drawable.ic_win_system, "Flight mode", "Turn off all wireless communications") {
            openIntent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        }
        addCard(binding, R.drawable.ic_win_system, "Proxy", "Proxy server for Wi-Fi and ethernet")
        addCard(binding, R.drawable.ic_win_network, "Advanced network settings", "View all network adapters")
    }

    private fun buildAppsPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Apps"
        addCard(binding, R.drawable.ic_win_apps, "Installed apps", "Manage applications, storage, permissions") {
            openIntent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
        }
        addCard(binding, R.drawable.ic_win_system, "Default apps", "Defaults for browser, email, music, video") {
            openIntent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        }
        addCard(binding, R.drawable.ic_win_apps, "Apps for websites", "Web links and desktop web apps (PWA)")
        addCard(binding, R.drawable.ic_win_system, "Video playback", "Hardware video acceleration")
        addCard(binding, R.drawable.ic_win_update, "Startup apps", "Configure apps that start automatically")
    }

    private fun buildAccountsPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Accounts"
        addCard(binding, R.drawable.ic_win_accounts, "Local Account", "Administrator • Gothwad Desktop")
        addCard(binding, R.drawable.ic_win_privacy, "Sign-in options", "PIN lock, device credentials, app lock") {
            onOpenLockSetup()
        }
        addCard(binding, R.drawable.ic_win_accounts, "Your info", "Account profile and avatar")
        addCard(binding, R.drawable.ic_win_network, "Email & accounts", "Sync accounts and cloud backup")
        addCard(binding, R.drawable.ic_win_system, "Family & other users", "Guest profiles and multi-user access")
    }

    private fun buildTimePage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Time & language"
        val now = Date()
        val timeStr = SimpleDateFormat(if (config.h24) "HH:mm" else "hh:mm a", Locale.ENGLISH).format(now)
        val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH).format(now)

        addCard(binding, R.drawable.ic_win_time, "Current time", "$timeStr • $dateStr")
        val h24Text = if (config.h24) "24-hour clock (Enabled)" else "12-hour clock (AM/PM)"
        addCard(binding, R.drawable.ic_win_time, "Clock format", h24Text) {
            scope.launch {
                val newH24 = !config.h24
                ConfigStore(context).update { it.copy(h24 = newH24) }
                config = config.copy(h24 = newH24)
                Toast.makeText(context, if (newH24) "Switched to 24-hour" else "Switched to 12-hour", Toast.LENGTH_SHORT).show()
            }
        }
        addCard(binding, R.drawable.ic_win_time, "Date & time settings", "Set time automatically, time zone") {
            openIntent(Settings.ACTION_DATE_SETTINGS)
        }
        addCard(binding, R.drawable.ic_win_network, "Language & region", "System language, regional formats") {
            openIntent(Settings.ACTION_LOCALE_SETTINGS)
        }
    }

    private fun buildGamingPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Gaming"
        addCard(binding, R.drawable.ic_win_gaming, "Game Bar", "Controller shortcuts and overlays")
        addCard(binding, R.drawable.ic_win_system, "Captures", "Screenshots, recording locations")
        addCard(binding, R.drawable.ic_win_gaming, "Game Mode", "Optimize PC performance for games")
    }

    private fun buildAccessibilityPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Accessibility"
        addCard(binding, R.drawable.ic_win_accessibility, "Accessibility settings", "TalkBack, high contrast, captions") {
            openIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        addCard(binding, R.drawable.ic_win_personalisation, "Text size & visual effects", "Magnification, large text")
        addCard(binding, R.drawable.ic_win_system, "Mouse pointer and touch", "Custom cursor size, touch indicators")
        addCard(binding, R.drawable.ic_win_gaming, "Audio & captions", "Mono audio, live captions")
    }

    private fun buildPrivacyPage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Privacy & security"
        addCard(binding, R.drawable.ic_win_privacy, "Device security", "PIN & biometric authentication") {
            onOpenLockSetup()
        }
        addCard(binding, R.drawable.ic_win_privacy, "App permissions", "Camera, microphone, location access") {
            openIntent(Settings.ACTION_APPLICATION_SETTINGS)
        }
        addCard(binding, R.drawable.ic_win_system, "Files & storage access", "Permissions for photos, music, docs")
        addCard(binding, R.drawable.ic_win_network, "Diagnostics & feedback", "Usage analytics and feedback")
    }

    private fun buildUpdatePage(binding: LayoutPcSettingsGenericBinding) {
        binding.tvPageTitle.text = "Windows Update"
        addCard(binding, R.drawable.ic_win_update, "Check for updates", "You're up to date • Last checked: Today") {
            Toast.makeText(context, "You're up to date! Launcher version 1.0.4", Toast.LENGTH_SHORT).show()
        }
        addCard(binding, R.drawable.ic_win_time, "Pause updates", "Pause for 1 week")
        addCard(binding, R.drawable.ic_win_system, "Update history", "View installed updates and changelog")
        addCard(binding, R.drawable.ic_win_system, "Advanced options", "Delivery optimization, optional updates")
    }

    private fun addCard(
        pageBinding: LayoutPcSettingsGenericBinding,
        iconRes: Int,
        title: String,
        subtitle: String,
        onClick: (() -> Unit)? = null
    ) {
        val cardBinding = ItemPcSettingCardBinding.inflate(
            LayoutInflater.from(context), pageBinding.containerCards, false
        )
        cardBinding.cardRowIcon.setImageResource(iconRes)
        cardBinding.cardRowTitle.text = title
        cardBinding.cardRowSubtitle.text = subtitle

        if (onClick != null) {
            cardBinding.cardRowRoot.setOnClickListener { onClick() }
        } else {
            cardBinding.cardRowChevron.visibility = View.INVISIBLE
        }
        pageBinding.containerCards.addView(cardBinding.root)
    }

    private fun openIntent(action: String) {
        runCatching {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
