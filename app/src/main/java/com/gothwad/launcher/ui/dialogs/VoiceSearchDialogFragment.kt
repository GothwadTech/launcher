package com.gothwad.launcher.ui.dialogs

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.gothwad.launcher.Actions
import com.gothwad.launcher.R
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.databinding.DialogVoiceSearchBinding
import com.gothwad.launcher.ui.AppIcons

class VoiceSearchDialogFragment : DialogFragment() {

    private var _binding: DialogVoiceSearchBinding? = null
    private val binding get() = _binding!!

    private var allApps: List<AppEntry> = emptyList()
    private var adapter: SearchAppAdapter? = null
    var onLaunchApp: ((AppEntry) -> Unit)? = null

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                binding.etQuery.setText(spokenText)
                binding.etQuery.setSelection(spokenText.length)
                filterApps(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogVoiceSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (onLaunchApp == null) { // recreated after process death - nothing to launch with
            dismiss()
            return
        }

        binding.btnClose.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))
        binding.imgSearchIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0x99FFFFFF.toInt()))
        binding.btnClear.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))
        binding.btnMic.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_MIC, 0xFF4C8DFF.toInt()))

        adapter = SearchAppAdapter { app ->
            onLaunchApp?.invoke(app)
            dismiss()
        }

        binding.recyclerApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerApps.adapter = adapter
        adapter?.submitList(allApps)

        binding.btnMic.setOnClickListener {
            launchSpeechInput()
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnClear.setOnClickListener {
            binding.etQuery.setText("")
        }

        binding.etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString().orEmpty()
                binding.btnClear.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                filterApps(q)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnMic.requestFocus()

        val isAvailable = requireContext().packageManager.queryIntentActivities(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0
        ).isNotEmpty()
        if (isAvailable) {
            view.postDelayed({
                // The view can be destroyed within those 300ms (dialog closed) - never
                // start a speech prompt from a dead fragment.
                if (isAdded && _binding != null) launchSpeechInput()
            }, 300)
        }
    }

    private fun launchSpeechInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_search_listening))
        }
        runCatching {
            speechLauncher.launch(intent)
        }.onFailure {
            Actions.toast(requireContext(), getString(R.string.voice_search_hint))
        }
    }

    private fun filterApps(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            adapter?.submitList(allApps)
        } else {
            val filtered = allApps.filter {
                it.label.contains(trimmed, ignoreCase = true) || it.pkg.contains(trimmed, ignoreCase = true)
            }
            adapter?.submitList(filtered)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "VoiceSearchDialog"

        fun newInstance(
            apps: List<AppEntry>,
            onLaunch: (AppEntry) -> Unit
        ): VoiceSearchDialogFragment {
            return VoiceSearchDialogFragment().apply {
                this.allApps = apps
                this.onLaunchApp = onLaunch
            }
        }
    }
}
