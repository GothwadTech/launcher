package com.gothwad.launcher.ui.dialogs

import android.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.gothwad.launcher.data.CORNER_RADII
import com.gothwad.launcher.data.ConfigStore
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

    fun updateCornerPreview(cornerIdx: Int) {
        val density = fragment.resources.displayMetrics.density
        val dpRadius = CORNER_RADII.getOrElse(cornerIdx) { 10f }
        val pxRadius = dpRadius * density
        binding.cardCornerPreview.radius = pxRadius
        binding.cardHeroBanner.radius = pxRadius
    }

    fun bind() {
        val cornerButtons = listOf(
            binding.btnCorner0 to 0,
            binding.btnCorner1 to 1,
            binding.btnCorner2 to 2,
            binding.btnCorner3 to 3,
            binding.btnCorner4 to 4
        )

        fun highlightSelectedCorner(selectedIdx: Int) {
            updateCornerPreview(selectedIdx)
            for ((btn, idx) in cornerButtons) {
                if (idx == selectedIdx) {
                    btn.setTextColor(Color.parseColor("#4DD0E1"))
                } else {
                    btn.setTextColor(Color.parseColor("#FFFFFF"))
                }
            }
        }

        highlightSelectedCorner(getConfig().cornerRadius)

        for ((btn, cornerIdx) in cornerButtons) {
            btn.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    updateCornerPreview(cornerIdx)
                } else {
                    updateCornerPreview(getConfig().cornerRadius)
                }
            }
            btn.setOnClickListener {
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    store.update { it.copy(cornerRadius = cornerIdx) }
                    val newConfig = getConfig().copy(cornerRadius = cornerIdx)
                    updateConfig(newConfig)
                    highlightSelectedCorner(cornerIdx)
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
        fun highlightSelectedScale(selectedIdx: Int) {
            for ((btn, idx) in uiScaleButtons) {
                if (idx == selectedIdx) {
                    btn.setTextColor(Color.parseColor("#4DD0E1"))
                } else {
                    btn.setTextColor(Color.parseColor("#FFFFFF"))
                }
            }
        }
        highlightSelectedScale(getConfig().uiScale)

        for ((btn, scaleIdx) in uiScaleButtons) {
            btn.setOnClickListener {
                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    store.update { it.copy(uiScale = scaleIdx) }
                    val newConfig = getConfig().copy(uiScale = scaleIdx)
                    updateConfig(newConfig)
                    highlightSelectedScale(scaleIdx)
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
    }
}
