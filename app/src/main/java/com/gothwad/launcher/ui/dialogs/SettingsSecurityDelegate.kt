package com.gothwad.launcher.ui.dialogs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.gothwad.launcher.Actions
import com.gothwad.launcher.R
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.LockSecurity
import com.gothwad.launcher.databinding.DialogEditTextBinding
import com.gothwad.launcher.databinding.SheetSettingsBinding
import kotlinx.coroutines.launch

class SettingsSecurityDelegate(
    private val fragment: SettingsBottomSheetFragment,
    private val binding: SheetSettingsBinding,
    private val store: ConfigStore,
    private val getConfig: () -> LauncherConfig,
    private val updateConfig: (LauncherConfig) -> Unit,
    private val updateSubtitles: () -> Unit,
    private val openManageAppsScreen: (Boolean) -> Unit
) {

    fun bind() {
        bindAppLock()
        bindHiddenAppsLock()
    }

    private fun bindAppLock() {
        val config = getConfig()
        binding.switchAppLock.isChecked = config.appLock.enabled

        binding.rowToggleAppLock.setOnClickListener {
            val cfg = getConfig()
            if (!cfg.appLock.enabled) {
                if (!cfg.appLock.ready) {
                    PinSetupDialogFragment.newInstance(
                        initialType = cfg.appLock.type,
                        initialPinLength = cfg.appLock.pinLength
                    ) { newCred ->
                        fragment.viewLifecycleOwner.lifecycleScope.launch {
                            val updatedCred = newCred.copy(enabled = true)
                            store.update { it.copy(appLock = updatedCred) }
                            val updated = getConfig().copy(appLock = updatedCred)
                            updateConfig(updated)
                            binding.switchAppLock.isChecked = true
                            updateSubtitles()
                            Actions.toast(fragment.requireContext(), "App Lock Enabled")
                        }
                    }.show(fragment.parentFragmentManager, PinSetupDialogFragment.TAG)
                } else {
                    val updated = cfg.appLock.copy(enabled = true)
                    binding.switchAppLock.isChecked = true
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        store.update { it.copy(appLock = updated) }
                        updateConfig(getConfig().copy(appLock = updated))
                        updateSubtitles()
                    }
                }
            } else {
                PinEntryDialogFragment.newInstance(
                    title = "Confirm App Lock",
                    subtitle = "Enter current PIN/password to disable App Lock",
                    credential = cfg.appLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_APP,
                    onSuccess = {
                        val updated = getConfig().appLock.copy(enabled = false)
                        binding.switchAppLock.isChecked = false
                        fragment.viewLifecycleOwner.lifecycleScope.launch {
                            store.update { it.copy(appLock = updated) }
                            updateConfig(getConfig().copy(appLock = updated))
                            updateSubtitles()
                            Actions.toast(fragment.requireContext(), "App Lock Disabled")
                        }
                    },
                    onCancelled = {
                        binding.switchAppLock.isChecked = true
                    }
                ).show(fragment.parentFragmentManager, PinEntryDialogFragment.TAG)
            }
        }

        binding.btnSetupAppLock.setOnClickListener {
            val cfg = getConfig()
            val showSetup = {
                PinSetupDialogFragment.newInstance(
                    initialType = cfg.appLock.type,
                    initialPinLength = cfg.appLock.pinLength
                ) { newCred ->
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        val updatedCred = newCred.copy(enabled = true)
                        store.update { it.copy(appLock = updatedCred) }
                        updateConfig(getConfig().copy(appLock = updatedCred))
                        binding.switchAppLock.isChecked = true
                        updateSubtitles()
                        Actions.toast(fragment.requireContext(), "App Lock credential updated")
                    }
                }.show(fragment.parentFragmentManager, PinSetupDialogFragment.TAG)
            }

            if (cfg.appLock.enabled && cfg.appLock.ready) {
                PinEntryDialogFragment.newInstance(
                    title = "Confirm Current Credential",
                    subtitle = "Enter current PIN/password to change App Lock",
                    credential = cfg.appLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_APP,
                    onSuccess = { showSetup() }
                ).show(fragment.parentFragmentManager, PinEntryDialogFragment.TAG)
            } else {
                showSetup()
            }
        }

        binding.btnManageLockedApps.setOnClickListener {
            val cfg = getConfig()
            if (cfg.appLock.enabled && cfg.appLock.ready) {
                PinEntryDialogFragment.newInstance(
                    title = "Manage Locked Apps",
                    subtitle = "Enter credential to manage locked apps",
                    credential = cfg.appLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_APP,
                    onSuccess = { openManageAppsScreen(false) }
                ).show(fragment.parentFragmentManager, PinEntryDialogFragment.TAG)
            } else {
                openManageAppsScreen(false)
            }
        }
    }

    private fun bindHiddenAppsLock() {
        val config = getConfig()
        binding.switchHiddenLock.isChecked = config.hiddenAppsLock.enabled

        binding.rowToggleHiddenLock.setOnClickListener {
            val cfg = getConfig()
            if (!cfg.hiddenAppsLock.enabled) {
                if (!cfg.hiddenAppsLock.ready) {
                    PinSetupDialogFragment.newInstance(
                        initialType = cfg.hiddenAppsLock.type,
                        initialPinLength = cfg.hiddenAppsLock.pinLength
                    ) { newCred ->
                        fragment.viewLifecycleOwner.lifecycleScope.launch {
                            val updatedCred = newCred.copy(enabled = true)
                            store.update { it.copy(hiddenAppsLock = updatedCred) }
                            updateConfig(getConfig().copy(hiddenAppsLock = updatedCred))
                            binding.switchHiddenLock.isChecked = true
                            updateSubtitles()
                            Actions.toast(fragment.requireContext(), "Hidden Apps 2nd Layer Lock Enabled")
                        }
                    }.show(fragment.parentFragmentManager, PinSetupDialogFragment.TAG)
                } else {
                    val updated = cfg.hiddenAppsLock.copy(enabled = true)
                    binding.switchHiddenLock.isChecked = true
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        store.update { it.copy(hiddenAppsLock = updated) }
                        updateConfig(getConfig().copy(hiddenAppsLock = updated))
                        updateSubtitles()
                    }
                }
            } else {
                PinEntryDialogFragment.newInstance(
                    title = "Confirm Hidden Apps Lock",
                    subtitle = "Enter current PIN/password to disable Hidden Apps Lock",
                    credential = cfg.hiddenAppsLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_VAULT,
                    onSuccess = {
                        val updated = getConfig().hiddenAppsLock.copy(enabled = false)
                        binding.switchHiddenLock.isChecked = false
                        fragment.viewLifecycleOwner.lifecycleScope.launch {
                            store.update { it.copy(hiddenAppsLock = updated) }
                            updateConfig(getConfig().copy(hiddenAppsLock = updated))
                            updateSubtitles()
                            Actions.toast(fragment.requireContext(), "Hidden Apps Lock Disabled")
                        }
                    },
                    onCancelled = {
                        binding.switchHiddenLock.isChecked = true
                    }
                ).show(fragment.parentFragmentManager, PinEntryDialogFragment.TAG)
            }
        }

        updateRevealCodeLabel()
        binding.rowHiddenRevealCode.setOnClickListener {
            val cfg = getConfig()
            if (cfg.hiddenAppsLock.enabled && cfg.hiddenAppsLock.ready) {
                PinEntryDialogFragment.newInstance(
                    title = "Change Reveal Code",
                    subtitle = "Enter credential to change reveal code",
                    credential = cfg.hiddenAppsLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_VAULT,
                    onSuccess = { showEditRevealCodeDialog() }
                ).show(fragment.parentFragmentManager, PinEntryDialogFragment.TAG)
            } else {
                showEditRevealCodeDialog()
            }
        }

        binding.btnSetupHiddenLock.setOnClickListener {
            val cfg = getConfig()
            val showSetup = {
                PinSetupDialogFragment.newInstance(
                    initialType = cfg.hiddenAppsLock.type,
                    initialPinLength = cfg.hiddenAppsLock.pinLength
                ) { newCred ->
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        val updatedCred = newCred.copy(enabled = true)
                        store.update { it.copy(hiddenAppsLock = updatedCred) }
                        updateConfig(getConfig().copy(hiddenAppsLock = updatedCred))
                        binding.switchHiddenLock.isChecked = true
                        updateSubtitles()
                        Actions.toast(fragment.requireContext(), "Hidden Apps Vault Credential updated")
                    }
                }.show(fragment.parentFragmentManager, PinSetupDialogFragment.TAG)
            }

            if (cfg.hiddenAppsLock.enabled && cfg.hiddenAppsLock.ready) {
                PinEntryDialogFragment.newInstance(
                    title = "Confirm Current Credential",
                    subtitle = "Enter current PIN/password to change Hidden Apps Lock",
                    credential = cfg.hiddenAppsLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_VAULT,
                    onSuccess = { showSetup() }
                ).show(fragment.parentFragmentManager, PinEntryDialogFragment.TAG)
            } else {
                showSetup()
            }
        }

        binding.btnManageHiddenApps.setOnClickListener {
            val cfg = getConfig()
            if (cfg.hiddenAppsLock.enabled && cfg.hiddenAppsLock.ready) {
                PinEntryDialogFragment.newInstance(
                    title = "Manage Hidden Apps",
                    subtitle = "Enter credential to manage hidden apps",
                    credential = cfg.hiddenAppsLock,
                    isCancelable = true,
                    lockScope = LockSecurity.SCOPE_VAULT,
                    onSuccess = { openManageAppsScreen(true) }
                ).show(fragment.parentFragmentManager, PinEntryDialogFragment.TAG)
            } else {
                openManageAppsScreen(true)
            }
        }
    }

    fun updateRevealCodeLabel() {
        val code = getConfig().hiddenAppsRevealCode
        binding.txtHiddenRevealCodeValue.text = if (code.isNotEmpty()) {
            "Active: •••••• (type the code in search… click to change)"
        } else {
            "Not set — click to set reveal code"
        }
    }

    private fun showEditRevealCodeDialog() {
        val context = fragment.requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_text, null)
        val dialogBinding = DialogEditTextBinding.bind(dialogView)
        dialogBinding.etRevealCode.setText(getConfig().hiddenAppsRevealCode)
        dialogBinding.etRevealCode.setSelection(dialogBinding.etRevealCode.text.length)

        val dialog = AlertDialog.Builder(context, R.style.Theme_LiteTV_Dialog)
            .setView(dialogView)
            .create()

        dialogBinding.btnCancelRevealCode.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSaveRevealCode.setOnClickListener {
            val code = dialogBinding.etRevealCode.text.toString().trim()
            if (!fragment.isAdded) return@setOnClickListener
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                store.update { it.copy(hiddenAppsRevealCode = code) }
                updateConfig(getConfig().copy(hiddenAppsRevealCode = code))
                updateRevealCodeLabel()
                Actions.toast(context, if (code.isNotEmpty()) "Reveal code saved" else "Reveal code cleared")
                dialog.dismiss()
            }
        }

        dialog.setOnShowListener {
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            dialogBinding.etRevealCode.requestFocus()
        }
        dialog.show()
    }
}
