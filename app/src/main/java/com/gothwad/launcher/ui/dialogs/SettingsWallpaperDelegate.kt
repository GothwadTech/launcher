package com.gothwad.launcher.ui.dialogs

import androidx.lifecycle.lifecycleScope
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.SheetSettingsBinding
import kotlinx.coroutines.launch

class SettingsWallpaperDelegate(
    private val fragment: SettingsBottomSheetFragment,
    private val binding: SheetSettingsBinding,
    private val store: ConfigStore,
    private val getConfig: () -> LauncherConfig,
    private val updateConfig: (LauncherConfig) -> Unit,
    private val onWallpaperChanged: (() -> Unit)?,
    private val openPhotoPicker: () -> Unit,
    private val updateSubtitles: () -> Unit
) {

    fun bind() {
        val wpButtons = listOf(
            binding.btnWp0 to 0,
            binding.btnWp1 to 1,
            binding.btnWp2 to 2,
            binding.btnWp3 to 3,
            binding.btnWp4 to 4
        )

        for ((btn, wpIdx) in wpButtons) {
            btn.setOnClickListener {
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    store.update {
                        it.copy(wallpaper = wpIdx, useCustomWallpaper = false)
                    }
                    val newConfig = getConfig().copy(wallpaper = wpIdx, useCustomWallpaper = false)
                    updateConfig(newConfig)
                    updateSubtitles()
                    onWallpaperChanged?.invoke()
                }
            }
        }

        binding.btnPickCustomWallpaper.setOnClickListener {
            openPhotoPicker()
        }

        val scrimButtons = listOf(
            binding.btnScrim0 to 0,
            binding.btnScrim1 to 1,
            binding.btnScrim2 to 2,
            binding.btnScrim3 to 3,
            binding.btnScrim4 to 4
        )

        for ((btn, scrimMode) in scrimButtons) {
            btn.setOnClickListener {
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    store.update { it.copy(scrimMode = scrimMode) }
                    val newConfig = getConfig().copy(scrimMode = scrimMode)
                    updateConfig(newConfig)
                    updateSubtitles()
                    onWallpaperChanged?.invoke()
                }
            }
        }
    }
}
