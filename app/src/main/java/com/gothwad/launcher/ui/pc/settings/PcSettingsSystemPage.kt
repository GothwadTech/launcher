package com.gothwad.launcher.ui.pc.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.gothwad.launcher.R
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.MODE_TV
import com.gothwad.launcher.databinding.LayoutPcSettingsSystemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PcSettingsSystemPage(
    private val context: Context,
    private val scope: CoroutineScope,
    private var config: LauncherConfig,
    private val onSwitchToTvMode: () -> Unit
) {
    private var _binding: LayoutPcSettingsSystemBinding? = null
    val binding get() = _binding!!

    fun createView(inflater: LayoutInflater): View {
        _binding = LayoutPcSettingsSystemBinding.inflate(inflater, null, false)
        setupHeader()
        setupCards()
        return binding.root
    }

    fun updateConfig(newConfig: LauncherConfig) {
        config = newConfig
        updateCardLabels()
    }

    private fun setupHeader() {
        val model = Build.MODEL ?: "Android PC"
        binding.tvSystemPcName.text = "Gothwad-PC ($model)"
        binding.tvSystemModel.text = "Desktop Mode • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        binding.btnSwitchTvMode.setOnClickListener {
            scope.launch {
                ConfigStore(context).update { it.copy(launcherMode = MODE_TV) }
                onSwitchToTvMode()
            }
        }
    }

    private fun updateCardLabels() {
        val displaySub = "Scale: ${(config.pcUiScale * 100).toInt()}% • Custom DPI: ${if (config.useCustomDpi) "${config.customDpi} DPI" else "Default"}"
        binding.cardSysDisplay.findViewById<TextView>(R.id.card_row_subtitle)?.text = displaySub

        val multiSub = "Icon size: ${config.pcIconSize}dp • Spacing: ${config.pcGridSpacing}dp • Labels: ${if (config.pcShowLabels) "On" else "Off"}"
        binding.cardSysMultitasking.findViewById<TextView>(R.id.card_row_subtitle)?.text = multiSub
    }

    private fun setupCards() {
        // 1. Display
        setupCardRow(
            root = binding.cardSysDisplay,
            iconRes = R.drawable.ic_win_system,
            title = "Display",
            subtitle = "Scale: ${(config.pcUiScale * 100).toInt()}% • Custom DPI: ${if (config.useCustomDpi) "${config.customDpi} DPI" else "Default"}"
        ) {
            val scales = listOf(0.75f, 0.85f, 1.0f, 1.15f)
            val currentIdx = scales.indexOfFirst { kotlin.math.abs(it - config.pcUiScale) < 0.05f }
            val nextScale = scales[(if (currentIdx == -1) 1 else currentIdx + 1) % scales.size]
            scope.launch {
                ConfigStore(context).update { it.copy(pcUiScale = nextScale) }
                config = config.copy(pcUiScale = nextScale)
                updateCardLabels()
            }
        }

        // 2. Sound
        setupCardRow(
            root = binding.cardSysSound,
            iconRes = R.drawable.ic_win_gaming,
            title = "Sound",
            subtitle = "Volume levels, output and input audio"
        ) {
            openSystemIntent(Settings.ACTION_SOUND_SETTINGS)
        }

        // 3. Notifications
        setupCardRow(
            root = binding.cardSysNotifications,
            iconRes = R.drawable.ic_win_system,
            title = "Notifications",
            subtitle = "Alerts from applications and system"
        ) {
            openSystemIntent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

        // 4. Focus assist
        setupCardRow(
            root = binding.cardSysFocus,
            iconRes = R.drawable.ic_win_time,
            title = "Focus assist",
            subtitle = "Notifications, automatic quiet rules"
        )

        // 5. Power & battery
        setupCardRow(
            root = binding.cardSysPower,
            iconRes = R.drawable.ic_win_system,
            title = "Power & battery",
            subtitle = "Sleep, battery usage, battery saver"
        ) {
            openSystemIntent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        }

        // 6. Storage
        setupCardRow(
            root = binding.cardSysStorage,
            iconRes = R.drawable.ic_win_system,
            title = "Storage",
            subtitle = "Storage space, internal drives, cache"
        ) {
            openSystemIntent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        }

        // 7. Nearby sharing
        setupCardRow(
            root = binding.cardSysNearby,
            iconRes = R.drawable.ic_win_network,
            title = "Nearby sharing",
            subtitle = "Discoverability, received files location"
        )

        // 8. Multi-tasking
        setupCardRow(
            root = binding.cardSysMultitasking,
            iconRes = R.drawable.ic_win_system,
            title = "Multi-tasking",
            subtitle = "Icon size: ${config.pcIconSize}dp • Spacing: ${config.pcGridSpacing}dp • Labels: ${if (config.pcShowLabels) "On" else "Off"}"
        ) {
            val sizes = listOf(36, 44, 52)
            val nextSize = sizes[(sizes.indexOf(config.pcIconSize) + 1).coerceAtLeast(0) % sizes.size]
            scope.launch {
                ConfigStore(context).update { it.copy(pcIconSize = nextSize) }
                config = config.copy(pcIconSize = nextSize)
                updateCardLabels()
            }
        }

        // 9. Activation
        setupCardRow(
            root = binding.cardSysActivation,
            iconRes = R.drawable.ic_win_update,
            title = "Activation",
            subtitle = "Activated with Android Desktop license"
        )

        // 10. Troubleshoot
        setupCardRow(
            root = binding.cardSysTroubleshoot,
            iconRes = R.drawable.ic_win_system,
            title = "Troubleshoot",
            subtitle = "Recommended troubleshooters, diagnostics"
        )

        // 11. Recovery
        setupCardRow(
            root = binding.cardSysRecovery,
            iconRes = R.drawable.ic_win_update,
            title = "Recovery",
            subtitle = "Reset launcher preferences to defaults"
        ) {
            scope.launch {
                ConfigStore(context).update { LauncherConfig() }
            }
        }

        // 12. Projecting to this PC
        setupCardRow(
            root = binding.cardSysProjecting,
            iconRes = R.drawable.ic_win_network,
            title = "Projecting to this PC",
            subtitle = "Wireless display, screen cast"
        ) {
            openSystemIntent(Settings.ACTION_CAST_SETTINGS)
        }

        // 13. Remote Desktop
        setupCardRow(
            root = binding.cardSysRemote,
            iconRes = R.drawable.ic_win_system,
            title = "Remote Desktop",
            subtitle = "Remote connection permissions"
        )

        // 14. Clipboard
        setupCardRow(
            root = binding.cardSysClipboard,
            iconRes = R.drawable.ic_win_personalisation,
            title = "Clipboard",
            subtitle = "Cut and copy history, sync across apps"
        )

        // 15. About
        setupCardRow(
            root = binding.cardSysAbout,
            iconRes = R.drawable.ic_win_system,
            title = "About",
            subtitle = "Device: ${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}"
        ) {
            openSystemIntent(Settings.ACTION_DEVICE_INFO_SETTINGS)
        }
    }

    private fun openSystemIntent(action: String) {
        runCatching {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun setupCardRow(
        root: View,
        iconRes: Int,
        title: String,
        subtitle: String,
        onClick: (() -> Unit)? = null
    ) {
        val icon = root.findViewById<ImageView>(R.id.card_row_icon)
        val tvTitle = root.findViewById<TextView>(R.id.card_row_title)
        val tvSub = root.findViewById<TextView>(R.id.card_row_subtitle)
        icon?.setImageResource(iconRes)
        tvTitle?.text = title
        tvSub?.text = subtitle

        if (onClick != null) {
            root.setOnClickListener { onClick() }
        }
    }
}
