package com.gothwad.launcher.ui.dialogs

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.gothwad.launcher.data.MODE_PC
import com.gothwad.launcher.data.MODE_TV
import com.gothwad.launcher.databinding.DialogModeSelectionBinding
import com.gothwad.launcher.ui.AppIcons

class ModeSelectionDialogFragment : DialogFragment() {

    private var _binding: DialogModeSelectionBinding? = null
    private val binding get() = _binding!!

    var currentMode: String = MODE_TV
    var onSelectMode: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogModeSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imgIconTv.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_TV, 0xFF3B82F6.toInt()))
        binding.imgIconPc.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DESKTOP, 0xFF10B981.toInt()))
        binding.imgIconPhone.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PHONE, 0xFFF59E0B.toInt()))

        updateActiveModeUi()

        binding.cardModeTv.setOnClickListener {
            onSelectMode?.invoke(MODE_TV)
            dismiss()
        }

        binding.cardModePc.setOnClickListener {
            onSelectMode?.invoke(MODE_PC)
            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        // Focus TV or PC based on current selection
        if (currentMode == MODE_PC) {
            binding.cardModePc.requestFocus()
        } else {
            binding.cardModeTv.requestFocus()
        }
    }

    private fun updateActiveModeUi() {
        if (currentMode == MODE_TV) {
            binding.statusTv.visibility = View.VISIBLE
            binding.statusPc.visibility = View.GONE
        } else {
            binding.statusTv.visibility = View.GONE
            binding.statusPc.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ModeSelectionDialog"

        fun newInstance(
            currentMode: String,
            onSelect: (String) -> Unit
        ): ModeSelectionDialogFragment {
            return ModeSelectionDialogFragment().apply {
                this.currentMode = currentMode
                this.onSelectMode = onSelect
            }
        }
    }
}
