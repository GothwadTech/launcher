package com.gothwad.launcher.ui.dialogs

import android.widget.Button
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.SheetSettingsBinding
import kotlinx.coroutines.launch

/**
 * Theme & Style page: themes, accent, layout, icons, focus/animations, clock, night
 * light, sorting, quick rows, performance profile and backup entry points.
 *
 * Every control writes through [ConfigStore] and mirrors into the fragment's local
 * config copy; the home grid re-renders from the config flow, so nothing here touches
 * home-screen views directly.
 */
class SettingsThemeDelegate(
    private val fragment: SettingsBottomSheetFragment,
    private val binding: SheetSettingsBinding,
    private val store: ConfigStore,
    private val getConfig: () -> LauncherConfig,
    private val updateConfig: (LauncherConfig) -> Unit,
    private val updateSubtitles: () -> Unit,
    private val onExportBackup: () -> Unit,
    private val onImportBackup: () -> Unit,
) {

    private var themeAdapter: ThemeTileAdapter? = null

    private fun persist(transform: (LauncherConfig) -> LauncherConfig) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            store.update(transform)
            updateConfig(transform(getConfig()))
            updateSubtitles()
        }
    }

    /** Binds a row of mutually-exclusive buttons to an Int config value. */
    private fun bindChoice(
        buttons: List<Pair<Button, Int>>,
        current: (LauncherConfig) -> Int,
        apply: (LauncherConfig, Int) -> LauncherConfig,
    ) {
        fun sync() {
            val value = current(getConfig())
            for ((btn, v) in buttons) btn.isSelected = v == value
        }
        sync()
        for ((btn, v) in buttons) {
            btn.setOnClickListener {
                persist { apply(it, v) }
                // Optimistic local sync so the chip flips without waiting for DataStore.
                for ((b, bv) in buttons) b.isSelected = bv == v
            }
        }
    }

    private fun bindSwitch(
        row: android.view.View,
        switch: com.google.android.material.switchmaterial.SwitchMaterial,
        current: (LauncherConfig) -> Boolean,
        apply: (LauncherConfig, Boolean) -> LauncherConfig,
    ) {
        switch.isChecked = current(getConfig())
        row.setOnClickListener {
            val newVal = !switch.isChecked
            switch.isChecked = newVal
            persist { apply(it, newVal) }
        }
    }

    fun bind() {
        val ctx = fragment.requireContext()

        // ----- themes -----
        themeAdapter = ThemeTileAdapter { index ->
            persist { it.copy(themeIndex = index) }
            themeAdapter?.select(index)
        }
        binding.recyclerThemes.layoutManager =
            LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerThemes.adapter = themeAdapter
        binding.recyclerThemes.itemAnimator = null
        themeAdapter?.select(getConfig().themeIndex)

        bindSwitch(
            binding.rowToggleFollowWallpaper, binding.switchFollowWallpaper,
            { it.followWallpaperPalette }, { c, v -> c.copy(followWallpaperPalette = v) },
        )

        // ----- accent (10 colours in ACCENTS order) -----
        bindChoice(
            listOf(
                binding.btnAccent0 to 0, binding.btnAccent1 to 1, binding.btnAccent2 to 2,
                binding.btnAccent3 to 3, binding.btnAccent4 to 4, binding.btnAccent5 to 5,
                binding.btnAccent6 to 6, binding.btnAccent7 to 7, binding.btnAccent8 to 8,
                binding.btnAccent9 to 9,
            ),
            { it.accent }, { c, v -> c.copy(accent = v, followWallpaperPalette = false) },
        )

        // ----- layout / columns / size -----
        bindChoice(
            listOf(
                binding.btnLayout0 to 0, binding.btnLayout1 to 1,
                binding.btnLayout2 to 2, binding.btnLayout3 to 3,
            ),
            { it.layoutMode }, { c, v -> c.copy(layoutMode = v) },
        )
        bindChoice(
            listOf(
                binding.btnCol3 to 3, binding.btnCol4 to 4, binding.btnCol5 to 5,
                binding.btnCol6 to 6, binding.btnCol7 to 7, binding.btnCol8 to 8,
            ),
            { it.gridColumns }, { c, v -> c.copy(gridColumns = v) },
        )
        bindChoice(
            listOf(
                binding.btnIconSize0 to 80, binding.btnIconSize1 to 100,
                binding.btnIconSize2 to 125, binding.btnIconSize3 to 150,
            ),
            { it.iconSizePct }, { c, v -> c.copy(iconSizePct = v) },
        )

        // ----- labels -----
        bindSwitch(
            binding.rowToggleLabels, binding.switchLabels,
            { it.showTileLabels }, { c, v -> c.copy(showTileLabels = v) },
        )
        bindChoice(
            listOf(
                binding.btnLabelSize0 to 11, binding.btnLabelSize1 to 13,
                binding.btnLabelSize2 to 15,
            ),
            { it.labelSizeSp }, { c, v -> c.copy(labelSizeSp = v) },
        )

        // ----- focus -----
        bindChoice(
            listOf(
                binding.btnFocus0 to 0, binding.btnFocus1 to 1,
                binding.btnFocus2 to 2, binding.btnFocus3 to 3,
            ),
            { it.focusEffect }, { c, v -> c.copy(focusEffect = v) },
        )
        bindChoice(
            listOf(
                binding.btnFocusScale0 to 100, binding.btnFocusScale1 to 108,
                binding.btnFocusScale2 to 120, binding.btnFocusScale3 to 135,
            ),
            { it.focusScalePct }, { c, v -> c.copy(focusScalePct = v) },
        )

        // ----- animations -----
        bindChoice(
            listOf(
                binding.btnOpenAnim0 to 0, binding.btnOpenAnim1 to 1,
                binding.btnOpenAnim2 to 2, binding.btnOpenAnim3 to 3,
            ),
            { it.openAnimation }, { c, v -> c.copy(openAnimation = v) },
        )
        bindChoice(
            listOf(
                binding.btnAnimSpeed0 to 0, binding.btnAnimSpeed1 to 1,
                binding.btnAnimSpeed2 to 2,
            ),
            { it.animSpeed }, { c, v -> c.copy(animSpeed = v) },
        )
        bindSwitch(
            binding.rowToggleReduceMotion, binding.switchReduceMotion,
            { it.reduceMotion }, { c, v -> c.copy(reduceMotion = v) },
        )

        // ----- clock / date / greeting -----
        bindChoice(
            listOf(
                binding.btnClock0 to 0, binding.btnClock1 to 1, binding.btnClock2 to 2,
            ),
            { it.clockStyle }, { c, v -> c.copy(clockStyle = v) },
        )
        bindChoice(
            listOf(
                binding.btnDate0 to 0, binding.btnDate1 to 1,
                binding.btnDate2 to 2, binding.btnDate3 to 3,
            ),
            { it.dateStyle }, { c, v -> c.copy(dateStyle = v) },
        )
        bindSwitch(
            binding.rowToggleGreeting, binding.switchGreeting,
            { it.showGreeting }, { c, v -> c.copy(showGreeting = v) },
        )

        // ----- night light -----
        bindSwitch(
            binding.rowToggleNightLight, binding.switchNightLight,
            { it.nightLight }, { c, v -> c.copy(nightLight = v) },
        )
        bindChoice(
            listOf(
                binding.btnNight0 to 20, binding.btnNight1 to 40, binding.btnNight2 to 70,
            ),
            { it.nightLightStrength }, { c, v -> c.copy(nightLightStrength = v) },
        )

        // ----- sorting -----
        bindChoice(
            listOf(
                binding.btnSort0 to 0, binding.btnSort1 to 1,
                binding.btnSort2 to 2, binding.btnSort3 to 3,
            ),
            { it.appSort }, { c, v -> c.copy(appSort = v) },
        )

        // ----- quick rows -----
        bindSwitch(
            binding.rowToggleFavRow, binding.switchFavRow,
            { it.showFavoritesRow }, { c, v -> c.copy(showFavoritesRow = v) },
        )
        bindSwitch(
            binding.rowToggleRecentRow, binding.switchRecentRow,
            { it.showRecentRow }, { c, v -> c.copy(showRecentRow = v) },
        )

        // ----- performance -----
        bindChoice(
            listOf(
                binding.btnPerf0 to 0, binding.btnPerf1 to 1, binding.btnPerf2 to 2,
            ),
            { it.perfProfile }, { c, v -> c.copy(perfProfile = v) },
        )

        // ----- backup -----
        binding.btnBackupExport.setOnClickListener { onExportBackup() }
        binding.btnBackupImport.setOnClickListener { onImportBackup() }
    }

    /** Re-syncs theme tiles after an import replaces the whole config. */
    fun syncThemeSelection() {
        themeAdapter?.select(getConfig().themeIndex)
    }
}
