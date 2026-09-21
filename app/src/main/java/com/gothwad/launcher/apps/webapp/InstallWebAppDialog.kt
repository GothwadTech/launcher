package com.gothwad.launcher.apps.webapp

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.gothwad.launcher.databinding.DialogInstallWebAppBinding
import com.gothwad.launcher.ui.AppIcons
import kotlinx.coroutines.launch

class InstallWebAppDialog(
    private val prefillUrl: String = "",
    private val prefillTitle: String = "",
    private val prefillIcon: Bitmap? = null,
    private val onInstalled: ((pkg: String) -> Unit)? = null
) : DialogFragment() {

    private var _binding: DialogInstallWebAppBinding? = null
    private val binding get() = _binding!!

    private var currentIcon: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val input = requireContext().contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(input)
                input?.close()
                if (bmp != null) {
                    currentIcon = bmp
                    updateIconPreview(bmp)
                    binding.tvIconStatus.text = "Custom logo selected"
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogInstallWebAppBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90f).toInt().coerceAtMost((480 * resources.displayMetrics.density).toInt()),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imgDialogIcon.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_GLOBE, 0xFF4FA7FA.toInt())
        )
        binding.btnCloseDialog.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE)
        )

        // Set initial values
        if (prefillUrl.isNotEmpty()) {
            binding.etWebappUrl.setText(prefillUrl)
        }
        if (prefillTitle.isNotEmpty()) {
            binding.etWebappTitle.setText(prefillTitle)
        }
        if (prefillIcon != null) {
            currentIcon = prefillIcon
            updateIconPreview(prefillIcon)
        } else if (prefillUrl.isNotEmpty()) {
            triggerAutoFetch(prefillUrl)
        }

        // Close
        binding.btnCloseDialog.setOnClickListener { dismiss() }
        binding.btnCancel.setOnClickListener { dismiss() }

        // Auto Fetch Button
        binding.btnAutofetch.setOnClickListener {
            val url = binding.etWebappUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                triggerAutoFetch(url)
            } else {
                Toast.makeText(requireContext(), "Please enter a URL first", Toast.LENGTH_SHORT).show()
            }
        }

        // Choose custom logo
        binding.btnPickCustomLogo.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Quick presets
        binding.presetTelegram.setOnClickListener {
            setPreset("https://web.telegram.org", "Telegram Web")
        }
        binding.presetWhatsapp.setOnClickListener {
            setPreset("https://web.whatsapp.com", "WhatsApp Web")
        }
        binding.presetTwitter.setOnClickListener {
            setPreset("https://x.com", "X / Twitter")
        }
        binding.presetGithub.setOnClickListener {
            setPreset("https://github.com", "GitHub")
        }
        binding.presetChatgpt.setOnClickListener {
            setPreset("https://chatgpt.com", "ChatGPT")
        }

        // Install Button
        binding.btnInstallWebApp.setOnClickListener {
            val url = binding.etWebappUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a web app URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val title = binding.etWebappTitle.text.toString().trim().ifEmpty {
                "Web App"
            }

            lifecycleScope.launch {
                binding.btnInstallWebApp.isEnabled = false
                val pkg = WebAppManager.installWebApp(
                    context = requireContext(),
                    url = url,
                    title = title,
                    icon = currentIcon
                )
                Toast.makeText(requireContext(), "Installed '$title' to Desktop!", Toast.LENGTH_SHORT).show()
                onInstalled?.invoke(pkg)
                dismiss()
            }
        }
    }

    private fun setPreset(url: String, title: String) {
        binding.etWebappUrl.setText(url)
        binding.etWebappTitle.setText(title)
        triggerAutoFetch(url)
    }

    private fun triggerAutoFetch(url: String) {
        binding.progressFetchingIcon.visibility = View.VISIBLE
        binding.tvIconStatus.text = "Fetching website icon..."

        lifecycleScope.launch {
            val (fetchedTitle, fetchedIcon) = WebAppManager.fetchFaviconAndTitle(url)
            binding.progressFetchingIcon.visibility = View.GONE

            if (fetchedTitle != null && binding.etWebappTitle.text.isNullOrEmpty()) {
                binding.etWebappTitle.setText(fetchedTitle)
            }
            if (fetchedIcon != null) {
                currentIcon = fetchedIcon
                updateIconPreview(fetchedIcon)
                binding.tvIconStatus.text = "Auto-fetched favicon"
            } else {
                val fallback = WebAppManager.generateFallbackSquircleIcon(
                    binding.etWebappTitle.text.toString().ifEmpty { "W" }
                )
                currentIcon = fallback
                updateIconPreview(fallback)
                binding.tvIconStatus.text = "Generated app squircle icon"
            }
        }
    }

    private fun updateIconPreview(icon: Bitmap) {
        binding.imgIconPreview.setImageBitmap(icon)
        binding.imgIconPreview.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
