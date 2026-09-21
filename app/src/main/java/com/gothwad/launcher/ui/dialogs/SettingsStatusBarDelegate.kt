package com.gothwad.launcher.ui.dialogs

import androidx.lifecycle.lifecycleScope
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.SheetSettingsBinding
import kotlinx.coroutines.launch

class SettingsStatusBarDelegate(
    private val fragment: SettingsBottomSheetFragment,
    private val binding: SheetSettingsBinding,
    private val store: ConfigStore,
    private val getConfig: () -> LauncherConfig,
    private val updateConfig: (LauncherConfig) -> Unit,
    private val getApps: () -> List<AppEntry>,
    private val updateSubtitles: () -> Unit,
    private val openAppPickerForVpn: () -> Unit
) {

    fun bind() {
        val config = getConfig()
        binding.switchShowStatusbar.isChecked = config.showStatusBar
        binding.rowToggleShowStatusbar.setOnClickListener {
            val newVal = !binding.switchShowStatusbar.isChecked
            binding.switchShowStatusbar.isChecked = newVal
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(showStatusBar = newVal) }
                val newConfig = getConfig().copy(showStatusBar = newVal)
                updateConfig(newConfig)
                updateSubtitles()
            }
        }

        binding.switch24hClock.isChecked = config.h24
        binding.rowToggle24hClock.setOnClickListener {
            val newVal = !binding.switch24hClock.isChecked
            binding.switch24hClock.isChecked = newVal
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(h24 = newVal) }
                val newConfig = getConfig().copy(h24 = newVal)
                updateConfig(newConfig)
                updateSubtitles()
            }
        }

        binding.switchStatusbarGlass.isChecked = config.statusBarGlass
        binding.rowToggleStatusbarGlass.setOnClickListener {
            val newVal = !binding.switchStatusbarGlass.isChecked
            binding.switchStatusbarGlass.isChecked = newVal
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(statusBarGlass = newVal) }
                val newConfig = getConfig().copy(statusBarGlass = newVal)
                updateConfig(newConfig)
            }
        }

        binding.switchStatusbarMatchCorners.isChecked = config.headerMatchIconCorners
        binding.rowToggleStatusbarMatchCorners.setOnClickListener {
            val newVal = !binding.switchStatusbarMatchCorners.isChecked
            binding.switchStatusbarMatchCorners.isChecked = newVal
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(headerMatchIconCorners = newVal) }
                val newConfig = getConfig().copy(headerMatchIconCorners = newVal)
                updateConfig(newConfig)
            }
        }

        binding.switchVpnButton.isChecked = config.showVpnButton
        binding.rowToggleVpnButton.setOnClickListener {
            val newVal = !binding.switchVpnButton.isChecked
            binding.switchVpnButton.isChecked = newVal
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(showVpnButton = newVal) }
                val newConfig = getConfig().copy(showVpnButton = newVal)
                updateConfig(newConfig)
                updateVpnSubtitle()
            }
        }

        updateVpnSubtitle()
        binding.rowPickVpnApp.setOnClickListener {
            openAppPickerForVpn()
        }
    }

    fun updateVpnSubtitle() {
        val pkg = getConfig().vpnApp
        val label = if (pkg.isEmpty()) {
            "System VPN settings"
        } else {
            getApps().firstOrNull { it.pkg == pkg }?.label ?: pkg
        }
        binding.txtVpnAppValue.text = label
    }
}
