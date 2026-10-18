package com.gothwad.launcher.ui.dialogs

import android.graphics.drawable.GradientDrawable
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gothwad.launcher.R
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.WallpaperRotator
import com.gothwad.launcher.databinding.SheetSettingsBinding
import com.gothwad.launcher.ui.DEFAULT_WALLPAPER_INDEX
import com.gothwad.launcher.ui.WALLPAPERS
import kotlinx.coroutines.launch

/**
 * Wallpaper & Themes page: visual preset tiles with a live preview box, scrim modes and
 * the auto-rotation interval.
 *
 * Picking a wallpaper used to be five text buttons ("Deep Space", "Cyberpunk Neon", ...)
 * whose names had nothing to do with the gradients they rendered - the user could not see
 * what they were choosing. The tiles now *are* the gradients, and the preview box on top
 * shows the focused one full-size before committing with OK.
 */
class SettingsWallpaperDelegate(
    private val fragment: SettingsBottomSheetFragment,
    private val binding: SheetSettingsBinding,
    private val store: ConfigStore,
    private val getConfig: () -> LauncherConfig,
    private val updateConfig: (LauncherConfig) -> Unit,
    private val onWallpaperChanged: (() -> Unit)?,
    private val openPhotoPicker: () -> Unit,
    private val updateSubtitles: () -> Unit,
) {

    private var tileAdapter: WallpaperTileAdapter? = null

    fun bind() {
        val context = fragment.requireContext()

        // ----- visual preset tiles + live preview -----
        tileAdapter = WallpaperTileAdapter(
            onFocused = { item -> showPreview(item.presetIndex) },
            onSelect = { item ->
                if (item.isCustom) {
                    openPhotoPicker()
                } else {
                    selectPreset(item.presetIndex)
                }
            },
        )
        binding.recyclerWallpapers.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerWallpapers.adapter = tileAdapter
        binding.recyclerWallpapers.itemAnimator = null

        syncSelection()

        binding.btnPickCustomWallpaper.setOnClickListener {
            openPhotoPicker()
        }

        // ----- scrim modes -----
        val scrimButtons = listOf(
            binding.btnScrim0 to 0,
            binding.btnScrim1 to 1,
            binding.btnScrim2 to 2,
            binding.btnScrim3 to 3,
            binding.btnScrim4 to 4,
        )
        fun syncScrim() {
            val current = getConfig().scrimMode
            for ((btn, mode) in scrimButtons) btn.isSelected = mode == current
        }
        syncScrim()
        for ((btn, scrimMode) in scrimButtons) {
            btn.setOnClickListener {
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    store.update { it.copy(scrimMode = scrimMode) }
                    updateConfig(getConfig().copy(scrimMode = scrimMode))
                    syncScrim()
                    updateSubtitles()
                    onWallpaperChanged?.invoke()
                }
            }
        }

        // ----- auto-change interval -----
        val autoButtons = listOf(
            binding.btnAutoWp0 to 0,
            binding.btnAutoWp1 to 1,
            binding.btnAutoWp2 to 2,
            binding.btnAutoWp3 to 3,
            binding.btnAutoWp4 to 4,
        )
        fun syncAuto() {
            val current = getConfig().wallpaperAutoMinutes
            for ((btn, idx) in autoButtons) {
                btn.isSelected = WallpaperRotator.INTERVAL_MINUTES[idx] == current
            }
        }
        syncAuto()
        for ((btn, idx) in autoButtons) {
            btn.setOnClickListener {
                val minutes = WallpaperRotator.INTERVAL_MINUTES[idx]
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    store.update {
                        it.copy(
                            wallpaperAutoMinutes = minutes,
                            // Start the window fresh so switching "15 min" does not instantly rotate.
                            wallpaperAutoLastMs = System.currentTimeMillis(),
                        )
                    }
                    updateConfig(getConfig().copy(wallpaperAutoMinutes = minutes))
                    syncAuto()
                    updateSubtitles()
                }
            }
        }
    }

    /** Re-syncs tiles + preview to the committed config (e.g. after the photo picker). */
    fun syncSelection() {
        val config = getConfig()
        val presetIdx = if (config.useCustomWallpaper) {
            WallpaperTileAdapter.TileItem.CUSTOM
        } else {
            config.wallpaper.coerceIn(0, WALLPAPERS.size - 1)
        }
        tileAdapter?.selectPreset(presetIdx)
        showPreview(presetIdx)
    }

    private fun selectPreset(presetIdx: Int) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            store.update {
                it.copy(wallpaper = presetIdx, useCustomWallpaper = false)
            }
            updateConfig(getConfig().copy(wallpaper = presetIdx, useCustomWallpaper = false))
            syncSelection()
            updateSubtitles()
            onWallpaperChanged?.invoke()
        }
    }

    /**
     * Fills the preview box with the gradient for [presetIdx] ([WallpaperTileAdapter.TileItem.CUSTOM]
     * shows the "custom photo" placeholder until one is picked).
     */
    private fun showPreview(presetIdx: Int) {
        val context = fragment.requireContext()
        if (presetIdx == WallpaperTileAdapter.TileItem.CUSTOM) {
            binding.imgWallpaperPreview.setImageDrawable(
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF16161A.toInt(), 0xFF2F3437.toInt()),
                )
            )
            binding.txtWallpaperPreviewName.text = context.getString(R.string.wallpaper_tile_custom)
            return
        }
        val preset = WALLPAPERS.getOrElse(presetIdx) { WALLPAPERS[DEFAULT_WALLPAPER_INDEX] }
        binding.imgWallpaperPreview.setImageDrawable(
            GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                preset.colors.toIntArray(),
            )
        )
        binding.txtWallpaperPreviewName.text = preset.displayLabel()
    }
}
