package com.gothwad.launcher.ui.dialogs

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.gothwad.launcher.Actions
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.AppLaunchTracker
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.ItemRecentAppBinding
import com.gothwad.launcher.databinding.ItemToggleAppBinding
import com.gothwad.launcher.databinding.SheetSettingsBinding
import com.gothwad.launcher.ui.AppIcons
import kotlinx.coroutines.launch

class SettingsAppsDelegate(
    private val fragment: SettingsBottomSheetFragment,
    private val binding: SheetSettingsBinding,
    private val store: ConfigStore,
    private val getConfig: () -> LauncherConfig,
    private val updateConfig: (LauncherConfig) -> Unit,
    private val getApps: () -> List<AppEntry>,
    private val updateSubtitles: () -> Unit,
    private val navigateToSubPage: (View, String) -> Unit
) {

    fun bind() {
        populateRecentApps()

        binding.btnUnhideAllApps.setOnClickListener {
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(hidden = emptySet()) }
                val newConfig = getConfig().copy(hidden = emptySet())
                updateConfig(newConfig)
                updateSubtitles()
                Actions.toast(fragment.requireContext(), "All apps unhidden")
            }
        }

        binding.switchShowHidden.isChecked = getConfig().showHidden
        binding.rowToggleShowHidden.setOnClickListener {
            val newVal = !binding.switchShowHidden.isChecked
            binding.switchShowHidden.isChecked = newVal
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(showHidden = newVal) }
                val newConfig = getConfig().copy(showHidden = newVal)
                updateConfig(newConfig)
                updateSubtitles()
            }
        }

        binding.switchAggressiveTrim.isChecked = getConfig().aggressiveMemoryTrim
        binding.rowToggleAggressiveTrim.setOnClickListener {
            val newVal = !binding.switchAggressiveTrim.isChecked
            binding.switchAggressiveTrim.isChecked = newVal
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(aggressiveMemoryTrim = newVal) }
                val newConfig = getConfig().copy(aggressiveMemoryTrim = newVal)
                updateConfig(newConfig)
                AppLaunchTracker.aggressiveTrimEnabled = newVal
                updateSubtitles()
            }
        }

        binding.switchLaunchOnBoot.isChecked = getConfig().launchOnBoot
        binding.rowToggleLaunchOnBoot.setOnClickListener {
            val newVal = !binding.switchLaunchOnBoot.isChecked
            binding.switchLaunchOnBoot.isChecked = newVal
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(launchOnBoot = newVal) }
                val newConfig = getConfig().copy(launchOnBoot = newVal)
                updateConfig(newConfig)
                updateSubtitles()
            }
        }
    }

    private fun populateRecentApps() {
        binding.layoutRecentApps.removeAllViews()
        val recentSample = getApps().take(5)
        val inflater = LayoutInflater.from(fragment.requireContext())

        for (app in recentSample) {
            val itemBinding = ItemRecentAppBinding.inflate(inflater, binding.layoutRecentApps, false)
            itemBinding.txtAppLabel.text = app.label
            itemBinding.txtAppPackage.text = app.pkg
            if (app.icon != null) {
                itemBinding.imgAppIcon.setImageBitmap(app.icon)
            } else {
                itemBinding.imgAppIcon.setImageDrawable(
                    AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE)
                )
            }

            itemBinding.root.setOnClickListener {
                fragment.dismiss()
                Actions.launchApp(fragment.requireContext(), app.pkg)
            }

            binding.layoutRecentApps.addView(itemBinding.root)
        }
    }

    fun openManageAppsScreen(isForHidden: Boolean) {
        val title = if (isForHidden) "Manage Hidden Apps" else "Manage Locked Apps"
        val subtitle = if (isForHidden) {
            "Selected apps will be hidden from launcher grid and only shown via reveal code in search"
        } else {
            "Selected apps will require App Lock credential to open"
        }

        binding.txtToggleAppsHeader.text = title
        binding.txtToggleAppsSub.text = subtitle

        val container = binding.layoutToggleAppsList
        container.removeAllViews()
        val inflater = LayoutInflater.from(fragment.requireContext())

        val currentConfig = getConfig()
        for (app in getApps()) {
            val itemBinding = ItemToggleAppBinding.inflate(inflater, container, false)
            itemBinding.txtAppLabel.text = app.label
            itemBinding.txtAppPackage.text = app.pkg

            if (app.icon != null) {
                itemBinding.imgAppIcon.setImageBitmap(app.icon)
            } else {
                itemBinding.imgAppIcon.setImageDrawable(
                    AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE)
                )
            }

            val isSelected = if (isForHidden) app.pkg in currentConfig.hidden else app.pkg in currentConfig.lockedApps
            itemBinding.switchAppSelected.isChecked = isSelected

            itemBinding.root.setOnClickListener {
                val currentlyChecked = itemBinding.switchAppSelected.isChecked
                val nextChecked = !currentlyChecked
                itemBinding.switchAppSelected.isChecked = nextChecked

                fragment.viewLifecycleOwner.lifecycleScope.launch {
                    val cfg = getConfig()
                    if (isForHidden) {
                        val newSet = if (nextChecked) cfg.hidden + app.pkg else cfg.hidden - app.pkg
                        store.update { it.copy(hidden = newSet) }
                        updateConfig(cfg.copy(hidden = newSet))
                    } else {
                        val newSet = if (nextChecked) cfg.lockedApps + app.pkg else cfg.lockedApps - app.pkg
                        store.update { it.copy(lockedApps = newSet) }
                        updateConfig(cfg.copy(lockedApps = newSet))
                    }
                    updateSubtitles()
                }
            }

            container.addView(itemBinding.root)
        }

        navigateToSubPage(binding.subpageToggleApps, title)
    }
}
