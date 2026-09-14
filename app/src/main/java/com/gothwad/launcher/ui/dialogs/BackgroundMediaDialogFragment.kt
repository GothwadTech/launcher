package com.gothwad.launcher.ui.dialogs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.gothwad.launcher.data.BackgroundMediaState
import com.gothwad.launcher.data.BackgroundMediaTracker
import com.gothwad.launcher.databinding.DialogBackgroundMediaBinding
import com.gothwad.launcher.ui.AppIcons

class BackgroundMediaDialogFragment : DialogFragment() {

    private var _binding: DialogBackgroundMediaBinding? = null
    private val binding get() = _binding!!

    var mediaState: BackgroundMediaState = BackgroundMediaState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogBackgroundMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isAd = mediaState.isStockAdCandidate
        val iconColor = if (isAd) 0xFFFF5252.toInt() else 0xFF8AB4F8.toInt()
        val iconPath = if (isAd) AppIcons.PATH_SHIELD else AppIcons.PATH_EQUALIZER
        binding.imgShieldIcon.setImageDrawable(AppIcons.createDrawable(iconPath, iconColor))

        binding.tvShieldTitle.text = if (isAd) "Background Ad Shield" else "Background Audio Active"
        binding.tvShieldSubtitle.text = if (isAd) {
            "Detected potential OEM / Operator background ad"
        } else {
            "An app is currently playing audio in background"
        }

        binding.tvSourceApp.text = "Source: " + (mediaState.appName ?: "Unknown Audio Player")
        if (!mediaState.title.isNullOrBlank()) {
            binding.tvMediaTitle.visibility = View.VISIBLE
            binding.tvMediaTitle.text = "Title: " + mediaState.title
        } else {
            binding.tvMediaTitle.visibility = View.GONE
        }

        if (!mediaState.packageName.isNullOrBlank()) {
            binding.tvPackageName.visibility = View.VISIBLE
            binding.tvPackageName.text = mediaState.packageName
            binding.btnKill.visibility = View.VISIBLE
            binding.btnAppInfo.visibility = View.VISIBLE
        } else {
            binding.tvPackageName.visibility = View.GONE
            binding.btnKill.visibility = View.GONE
            binding.btnAppInfo.visibility = View.GONE
        }

        binding.btnSilence.setOnClickListener {
            BackgroundMediaTracker.silenceAudio(requireContext())
            Toast.makeText(requireContext(), "Audio silenced", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        binding.btnStop.setOnClickListener {
            BackgroundMediaTracker.stopActiveMedia(requireContext())
            Toast.makeText(requireContext(), "Media playback stopped", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        binding.btnKill.setOnClickListener {
            val pkg = mediaState.packageName
            if (!pkg.isNullOrBlank()) {
                BackgroundMediaTracker.killPackage(requireContext(), pkg)
                Toast.makeText(requireContext(), "Background process killed", Toast.LENGTH_SHORT).show()
            }
            dismiss()
        }

        binding.btnAppInfo.setOnClickListener {
            val pkg = mediaState.packageName
            if (!pkg.isNullOrBlank()) {
                BackgroundMediaTracker.openAppInfo(requireContext(), pkg)
            }
            dismiss()
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnSilence.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "BackgroundMediaDialog"

        fun newInstance(state: BackgroundMediaState): BackgroundMediaDialogFragment {
            return BackgroundMediaDialogFragment().apply {
                this.mediaState = state
            }
        }
    }
}
