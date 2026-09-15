package com.gothwad.launcher.ui.views

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.RadioButton
import com.gothwad.launcher.databinding.DialogPcOptionsPickerBinding
import com.gothwad.launcher.databinding.DialogPcRenameShortcutBinding

object PcDialogHelper {

    fun showRenameShortcutDialog(
        context: Context,
        appLabel: String,
        currentCustomLabel: String?,
        onSave: (String) -> Unit,
        onReset: () -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogPcRenameShortcutBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (320 * context.resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        binding.tvDialogTitle.text = "Rename Shortcut"
        binding.tvDialogSubtitle.text = "Original Name: $appLabel"
        binding.etCustomName.setText(currentCustomLabel ?: appLabel)
        binding.etCustomName.selectAll()

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnResetDefault.setOnClickListener {
            onReset()
            dialog.dismiss()
        }

        binding.btnSave.setOnClickListener {
            val newName = binding.etCustomName.text?.toString()?.trim().orEmpty()
            if (newName.isNotEmpty()) {
                onSave(newName)
            } else {
                onReset()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    data class OptionItem(
        val title: String,
        val subtitle: String? = null,
        val tag: Any
    )

    fun showOptionsPickerDialog(
        context: Context,
        title: String,
        subtitle: String,
        options: List<OptionItem>,
        selectedIndex: Int,
        onSelect: (OptionItem) -> Unit
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogPcOptionsPickerBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (320 * context.resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        binding.tvPickerTitle.text = title
        binding.tvPickerSubtitle.text = subtitle

        val density = context.resources.displayMetrics.density
        val tintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                0xFF4FA7FA.toInt(),
                0xFF9AA0A6.toInt()
            )
        )

        options.forEachIndexed { index, opt ->
            val rb = RadioButton(context).apply {
                id = index + 1000
                text = if (opt.subtitle != null) "${opt.title} (${opt.subtitle})" else opt.title
                setTextColor(Color.WHITE)
                textSize = 13f
                buttonTintList = tintList
                setPadding((8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt())
                isChecked = index == selectedIndex
            }
            binding.rgOptions.addView(rb)
        }

        binding.rgOptions.setOnCheckedChangeListener { _, checkedId ->
            val idx = checkedId - 1000
            if (idx in options.indices) {
                onSelect(options[idx])
                dialog.dismiss()
            }
        }

        binding.btnPickerCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
