package com.gothwad.launcher.ui.dialogs

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.gothwad.launcher.Actions
import com.gothwad.launcher.R
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.ButtonMappingManager
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.ItemButtonMappingBinding
import com.gothwad.launcher.databinding.ItemPickAppBinding
import com.gothwad.launcher.databinding.SheetSettingsBinding
import com.gothwad.launcher.ui.AppIcons
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsButtonMappingDelegate(
    private val fragment: SettingsBottomSheetFragment,
    private val binding: SheetSettingsBinding,
    private val store: ConfigStore,
    private val getConfig: () -> LauncherConfig,
    private val updateConfig: (LauncherConfig) -> Unit,
    private val getApps: () -> List<AppEntry>,
    private val updateSubtitles: () -> Unit,
    private val navigateToSubPage: (View, String) -> Unit
) {

    var capturedKeyCodeForMapping: Int? = null

    fun bind() {
        renderButtonMappingsList()

        binding.btnAddButtonMapping.setOnClickListener {
            startKeyListening()
        }

        binding.btnCancelListening.setOnClickListener {
            cancelKeyListening()
        }

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            ButtonMappingManager.keyCaptureFlow.collectLatest { keyCode ->
                handleKeyCaptured(keyCode)
            }
        }
    }

    private fun startKeyListening() {
        ButtonMappingManager.startListening()
        binding.cardListeningForButton.visibility = View.VISIBLE
        binding.txtListeningStatus.text = fragment.getString(R.string.button_mapping_listening)
        binding.btnAddButtonMapping.visibility = View.GONE
        binding.btnCancelListening.requestFocus()
    }

    fun cancelKeyListening() {
        ButtonMappingManager.stopListening()
        binding.cardListeningForButton.visibility = View.GONE
        binding.btnAddButtonMapping.visibility = View.VISIBLE
        binding.btnAddButtonMapping.requestFocus()
    }

    private fun handleKeyCaptured(keyCode: Int) {
        if (ButtonMappingManager.isReservedKey(keyCode)) {
            val keyName = ButtonMappingManager.getKeyName(keyCode)
            Actions.toast(fragment.requireContext(), fragment.getString(R.string.button_mapping_reserved_error, keyName))
            cancelKeyListening()
            return
        }

        ButtonMappingManager.stopListening()
        binding.cardListeningForButton.visibility = View.GONE
        binding.btnAddButtonMapping.visibility = View.VISIBLE

        capturedKeyCodeForMapping = keyCode
        openAppPickerForMapping(keyCode)
    }

    fun openAppPickerForMapping(keyCode: Int) {
        val buttonName = ButtonMappingManager.getKeyName(keyCode)
        binding.txtPickAppHeader.text = "Detected Key: $buttonName (Keycode $keyCode)"
        populateAppPickerList(keyCode)
        navigateToSubPage(binding.subpagePickApp, "Assign App to Button")
    }

    private fun populateAppPickerList(targetKeyCode: Int) {
        val container = binding.layoutPickAppList
        container.removeAllViews()

        val sortedApps = getApps().sortedBy { it.label.lowercase() }
        val inflater = LayoutInflater.from(fragment.requireContext())

        for (app in sortedApps) {
            val itemBinding = ItemPickAppBinding.inflate(inflater, container, false)
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
                assignMapping(targetKeyCode, app.pkg, app.label)
            }

            container.addView(itemBinding.root)
        }
    }

    private fun assignMapping(keyCode: Int, packageName: String, appLabel: String) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val updatedMap = getConfig().buttonMap.toMutableMap()
            updatedMap[keyCode] = packageName

            store.update { it.copy(buttonMap = updatedMap) }
            updateConfig(getConfig().copy(buttonMap = updatedMap))

            updateSubtitles()
            renderButtonMappingsList()

            Actions.toast(
                fragment.requireContext(),
                fragment.getString(
                    R.string.button_mapping_saved,
                    ButtonMappingManager.getKeyName(keyCode),
                    appLabel
                )
            )

            navigateToSubPage(binding.subpageButtonMapping, "Remote Button Mapping")
        }
    }

    private fun removeMapping(keyCode: Int) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val updatedMap = getConfig().buttonMap.toMutableMap()
            updatedMap.remove(keyCode)

            store.update { it.copy(buttonMap = updatedMap) }
            updateConfig(getConfig().copy(buttonMap = updatedMap))

            updateSubtitles()
            renderButtonMappingsList()

            Actions.toast(fragment.requireContext(), fragment.getString(R.string.button_mapping_deleted))
        }
    }

    fun renderButtonMappingsList() {
        val container = binding.layoutButtonMappingsList
        container.removeAllViews()

        val mappings = getConfig().buttonMap
        if (mappings.isEmpty()) {
            binding.txtButtonMappingsEmpty.visibility = View.VISIBLE
            return
        }

        binding.txtButtonMappingsEmpty.visibility = View.GONE
        val inflater = LayoutInflater.from(fragment.requireContext())

        for ((keyCode, pkg) in mappings) {
            val itemBinding = ItemButtonMappingBinding.inflate(inflater, container, false)
            val buttonName = ButtonMappingManager.getKeyName(keyCode)
            itemBinding.txtButtonName.text = buttonName

            val matchingApp = getApps().find { it.pkg == pkg }
            if (matchingApp != null) {
                itemBinding.txtMappedAppName.text = "${matchingApp.label} • Key $keyCode"
                if (matchingApp.icon != null) {
                    itemBinding.imgMappedAppIcon.setImageBitmap(matchingApp.icon)
                } else {
                    itemBinding.imgMappedAppIcon.setImageDrawable(
                        AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE)
                    )
                }
            } else {
                itemBinding.txtMappedAppName.text = "$pkg • Key $keyCode"
                itemBinding.imgMappedAppIcon.setImageDrawable(
                    AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE)
                )
            }

            itemBinding.btnDeleteMapping.setOnClickListener {
                removeMapping(keyCode)
            }

            itemBinding.root.setOnClickListener {
                openAppPickerForMapping(keyCode)
            }

            container.addView(itemBinding.root)
        }
    }
}
