package com.gothwad.launcher.ui.dialogs

import androidx.lifecycle.lifecycleScope
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LAYOUT_GRID
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.SheetSettingsBinding
import com.gothwad.launcher.ui.DensityAdapter
import kotlinx.coroutines.launch

class SettingsDisplayDelegate(
    private val fragment: SettingsBottomSheetFragment,
    private val binding: SheetSettingsBinding,
    private val store: ConfigStore,
    private val getConfig: () -> LauncherConfig,
    private val updateConfig: (LauncherConfig) -> Unit,
    private val updateSubtitles: () -> Unit
) {

    fun bind() {
        // Enforce Grid view only
        binding.btnLayoutGrid.setOnClickListener {
            updateLayout(LAYOUT_GRID)
        }

        val scaleButtons = listOf(
            binding.btnScale0 to 0,
            binding.btnScale1 to 1,
            binding.btnScale2 to 2,
            binding.btnScale3 to 3,
            binding.btnScale4 to 4
        )
        for ((btn, scaleIdx) in scaleButtons) {
            btn.setOnClickListener {
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    store.update { it.copy(iconScale = scaleIdx) }
                    val newConfig = getConfig().copy(iconScale = scaleIdx)
                    updateConfig(newConfig)
                    updateSubtitles()
                }
            }
        }

        val uiScaleButtons = listOf(
            binding.btnUiscale0 to 0,
            binding.btnUiscale1 to 1,
            binding.btnUiscale2 to 2,
            binding.btnUiscale3 to 3,
            binding.btnUiscale4 to 4
        )
        for ((btn, scaleIdx) in uiScaleButtons) {
            btn.setOnClickListener {
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    store.update { it.copy(uiScale = scaleIdx) }
                    val newConfig = getConfig().copy(uiScale = scaleIdx)
                    updateConfig(newConfig)
                    updateSubtitles()
                }
            }
        }

        binding.switchSystemFont.isChecked = getConfig().respectSystemFontScale
        binding.rowToggleSystemFont.setOnClickListener {
            val newVal = !binding.switchSystemFont.isChecked
            binding.switchSystemFont.isChecked = newVal
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(respectSystemFontScale = newVal) }
                val newConfig = getConfig().copy(respectSystemFontScale = newVal)
                updateConfig(newConfig)
                DensityAdapter.respectSystemFontScale = newVal
                runCatching { DensityAdapter.apply(fragment.requireContext()) }
                updateSubtitles()
            }
        }

        val cornerButtons = listOf(
            binding.btnCorner0 to 0,
            binding.btnCorner1 to 1,
            binding.btnCorner2 to 2,
            binding.btnCorner3 to 3,
            binding.btnCorner4 to 4
        )
        for ((btn, cornerIdx) in cornerButtons) {
            btn.setOnClickListener {
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    store.update { it.copy(cornerRadius = cornerIdx) }
                    val newConfig = getConfig().copy(cornerRadius = cornerIdx)
                    updateConfig(newConfig)
                    updateSubtitles()
                }
            }
        }

        binding.switchAppLabels.isChecked = getConfig().showAppLabels
        binding.rowToggleAppLabels.setOnClickListener {
            val newVal = !binding.switchAppLabels.isChecked
            binding.switchAppLabels.isChecked = newVal
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(showAppLabels = newVal) }
                val newConfig = getConfig().copy(showAppLabels = newVal)
                updateConfig(newConfig)
            }
        }

        binding.switchCategoryNames.isChecked = getConfig().showCategoryNames
        binding.rowToggleCategoryNames.setOnClickListener {
            val newVal = !binding.switchCategoryNames.isChecked
            binding.switchCategoryNames.isChecked = newVal
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(showCategoryNames = newVal) }
                val newConfig = getConfig().copy(showCategoryNames = newVal)
                updateConfig(newConfig)
            }
        }
    }

    private fun updateLayout(newLayout: Int) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            store.update { it.copy(layout = newLayout) }
            val newConfig = getConfig().copy(layout = newLayout)
            updateConfig(newConfig)
            updateSubtitles()
        }
    }
}
