package com.gothwad.launcher.ui.dialogs

import androidx.lifecycle.lifecycleScope
import com.gothwad.launcher.Actions
import com.gothwad.launcher.R
import com.gothwad.launcher.data.CORNER_RADII
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.UI_SCALES
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

        /**
         * Selection is drawn with `isSelected` + the shared state lists
         * (`bg_settings_row` cyan chip, `color_settings_btn_text` dark label).
         * The old code force-set static text colours (`#4DD0E1` / `#FFFFFF`), which
         * overrode the focus-aware state list and made the label invisible on the white
         * focus highlight - "focus pata hi nahi chalta".
         */
        fun highlightSelectedCorner(selectedIdx: Int) {
            updateCornerPreview(selectedIdx)
            for ((btn, idx) in cornerButtons) {
                btn.isSelected = idx == selectedIdx
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
                btn.isSelected = idx == selectedIdx
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

                    // Actually apply it. Persisting alone used to leave this control a no-op:
                    // density is baked into every view at inflate time, so the host activity
                    // has to be recreated for the new scale to reach the whole UI. The sheet
                    // dismisses itself on recreation (see SettingsBottomSheetFragment).
                    val multiplier = UI_SCALES.getOrElse(scaleIdx) { 1.0f }
                    val context = fragment.requireContext()
                    if (DensityAdapter.setUiScale(context, multiplier)) {
                        runCatching { DensityAdapter.apply(context) }
                        val activity = fragment.requireActivity()
                        Actions.toast(context, context.getString(R.string.toast_ui_scale_applied))
                        activity.recreate()
                    }
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
                DensityAdapter.setRespectSystemFontScale(fragment.requireContext(), newVal)
                runCatching { DensityAdapter.apply(fragment.requireContext()) }
                updateSubtitles()
            }
        }
    }
}
