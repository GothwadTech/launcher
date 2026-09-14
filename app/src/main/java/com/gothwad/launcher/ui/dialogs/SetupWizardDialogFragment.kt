package com.gothwad.launcher.ui.dialogs

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.gothwad.launcher.Actions
import com.gothwad.launcher.R
import com.gothwad.launcher.databinding.DialogSetupWizardBinding
import com.gothwad.launcher.service.LauncherAccessibilityService
import com.gothwad.launcher.service.NotificationManagerBridge

class SetupWizardDialogFragment : DialogFragment() {

    private var _binding: DialogSetupWizardBinding? = null
    private val binding get() = _binding!!

    var onDone: (() -> Unit)? = null

    private var currentStep: Int = 0

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateStepUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSetupWizardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnWizardBack.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                updateStepUi()
            }
        }

        binding.btnWizardNext.setOnClickListener {
            if (currentStep < 2) {
                currentStep++
                updateStepUi()
            } else {
                onDone?.invoke()
                dismiss()
            }
        }

        updateStepUi()
    }

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolve = requireContext().packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolve?.activityInfo?.packageName == requireContext().packageName
    }

    private fun requestDefaultHome() {
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                val ok = runCatching {
                    roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }.isSuccess
                if (ok) return
            }
        }
        runCatching {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }.onFailure {
            Actions.toast(context, getString(R.string.wizard_no_chooser))
        }
    }

    private fun updateStepUi() {
        val context = requireContext()
        binding.btnWizardBack.visibility = if (currentStep > 0) View.VISIBLE else View.GONE
        binding.btnWizardNext.text = if (currentStep == 2) getString(R.string.done) else getString(R.string.next)

        when (currentStep) {
            0 -> {
                binding.tvStepTitle.text = getString(R.string.wizard_welcome_title)
                binding.tvStepBody.text = getString(R.string.wizard_welcome_body)
                binding.tvStepSecondary.visibility = View.VISIBLE
                binding.tvStepSecondary.text = getString(R.string.wizard_privacy_body)
                binding.layoutActionArea.visibility = View.GONE
                binding.btnWizardNext.requestFocus()
            }
            1 -> {
                binding.tvStepTitle.text = getString(R.string.wizard_home_title)
                val isDef = isDefaultHome()
                val isA11y = LauncherAccessibilityService.isEnabled(context)

                if (isDef) {
                    binding.tvStepBody.text = getString(R.string.wizard_already_default)
                    binding.tvStepSecondary.visibility = View.GONE
                    binding.layoutActionArea.visibility = View.GONE
                } else if (isA11y) {
                    binding.tvStepBody.text = getString(R.string.accessibility_status_enabled)
                    binding.tvStepSecondary.visibility = View.GONE
                    binding.layoutActionArea.visibility = View.VISIBLE
                    binding.btnActionPrimary.text = getString(R.string.accessibility_enable_btn)
                    binding.btnActionPrimary.setOnClickListener {
                        Actions.openAccessibilitySettings(context)
                    }
                    binding.btnActionSecondary.visibility = View.VISIBLE
                    binding.btnActionSecondary.text = getString(R.string.wizard_check_again)
                    binding.btnActionSecondary.setOnClickListener {
                        updateStepUi()
                    }
                } else {
                    binding.tvStepBody.text = getString(R.string.wizard_aosp_body)
                    binding.tvStepSecondary.visibility = View.VISIBLE
                    binding.tvStepSecondary.text = getString(R.string.wizard_no_dialog_hint)
                    binding.layoutActionArea.visibility = View.VISIBLE
                    binding.btnActionPrimary.text = getString(R.string.wizard_set_default)
                    binding.btnActionPrimary.setOnClickListener {
                        requestDefaultHome()
                    }
                    binding.btnActionSecondary.visibility = View.VISIBLE
                    binding.btnActionSecondary.text = getString(R.string.wizard_check_again)
                    binding.btnActionSecondary.setOnClickListener {
                        updateStepUi()
                    }
                }
                binding.btnActionPrimary.requestFocus()
            }
            2 -> {
                binding.tvStepTitle.text = getString(R.string.notifications_title)
                val notifGranted = NotificationManagerBridge.isNotificationAccessGranted(context)

                if (notifGranted) {
                    binding.tvStepBody.text = "Notification access is active! You can manage notifications directly on your home screen."
                    binding.tvStepSecondary.visibility = View.GONE
                    binding.layoutActionArea.visibility = View.GONE
                } else {
                    binding.tvStepBody.text = "Gothwad Launcher can show active TV notifications and detect background OEM ads. Enable notification listener access to activate this feature."
                    binding.tvStepSecondary.visibility = View.GONE
                    binding.layoutActionArea.visibility = View.VISIBLE
                    binding.btnActionPrimary.text = "Grant Notification Access"
                    binding.btnActionPrimary.setOnClickListener {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    }
                    binding.btnActionSecondary.visibility = View.VISIBLE
                    binding.btnActionSecondary.text = getString(R.string.wizard_check_again)
                    binding.btnActionSecondary.setOnClickListener {
                        updateStepUi()
                    }
                }
                binding.btnWizardNext.requestFocus()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SetupWizardDialog"

        fun newInstance(onDone: () -> Unit): SetupWizardDialogFragment {
            return SetupWizardDialogFragment().apply {
                this.onDone = onDone
            }
        }
    }
}
