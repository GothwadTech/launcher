package com.gothwad.launcher.ui.dialogs

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.gothwad.launcher.databinding.DialogPinEntryBinding
import com.gothwad.launcher.ui.AppIcons

class PinEntryDialogFragment : DialogFragment() {

    private var _binding: DialogPinEntryBinding? = null
    private val binding get() = _binding!!

    var targetTitle: String = "Enter PIN"
    var targetSubtitle: String = "Enter your PIN to unlock"
    var correctPin: String = ""
    var pinLength: Int = 4
    var isCancelableDialog: Boolean = true

    var onSuccess: (() -> Unit)? = null
    var onCancelled: (() -> Unit)? = null

    private val enteredDigits = StringBuilder()
    private val dotViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        isCancelable = isCancelableDialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPinEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imgPinIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4C8DFF.toInt()))
        binding.tvPinTitle.text = targetTitle
        binding.tvPinSubtitle.text = targetSubtitle

        binding.btnCancel.visibility = if (isCancelableDialog) View.VISIBLE else View.GONE
        binding.btnCancel.setOnClickListener {
            onCancelled?.invoke()
            dismiss()
        }

        setupDots()
        setupKeypad()

        // Handle hardware remote number keys and backspace
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { appendDigit('0'); true }
                    KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> { appendDigit('1'); true }
                    KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> { appendDigit('2'); true }
                    KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> { appendDigit('3'); true }
                    KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> { appendDigit('4'); true }
                    KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> { appendDigit('5'); true }
                    KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> { appendDigit('6'); true }
                    KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> { appendDigit('7'); true }
                    KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> { appendDigit('8'); true }
                    KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> { appendDigit('9'); true }
                    KeyEvent.KEYCODE_DEL -> { removeDigit(); true }
                    KeyEvent.KEYCODE_CLEAR -> { clearDigits(); true }
                    else -> false
                }
            } else {
                false
            }
        }

        binding.btnKey1.requestFocus()
    }

    private fun setupDots() {
        binding.layoutPinDots.removeAllViews()
        dotViews.clear()

        val density = resources.displayMetrics.density
        val sizePx = (16 * density).toInt()
        val marginPx = (8 * density).toInt()

        for (i in 0 until pinLength) {
            val dot = View(requireContext()).apply {
                val params = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                }
                layoutParams = params
                background = createDotDrawable(filled = false, error = false)
            }
            dotViews.add(dot)
            binding.layoutPinDots.addView(dot)
        }
    }

    private fun createDotDrawable(filled: Boolean, error: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (error) {
                setColor(0xFFFF5252.toInt())
                setStroke(2, 0xFFFF5252.toInt())
            } else if (filled) {
                setColor(0xFF4C8DFF.toInt())
                setStroke(2, 0xFF4C8DFF.toInt())
            } else {
                setColor(0x33FFFFFF.toInt())
                setStroke(2, 0x66FFFFFF.toInt())
            }
        }
    }

    private fun updateDotsUi(error: Boolean = false) {
        val count = enteredDigits.length
        for (i in dotViews.indices) {
            dotViews[i].background = createDotDrawable(filled = i < count, error = error)
        }
    }

    private fun setupKeypad() {
        val digitButtons = listOf(
            binding.btnKey0 to '0',
            binding.btnKey1 to '1',
            binding.btnKey2 to '2',
            binding.btnKey3 to '3',
            binding.btnKey4 to '4',
            binding.btnKey5 to '5',
            binding.btnKey6 to '6',
            binding.btnKey7 to '7',
            binding.btnKey8 to '8',
            binding.btnKey9 to '9'
        )

        for ((btn, digit) in digitButtons) {
            btn.setOnClickListener {
                appendDigit(digit)
            }
        }

        binding.btnKeyClear.setOnClickListener {
            clearDigits()
        }

        binding.btnKeyBackspace.setOnClickListener {
            removeDigit()
        }
    }

    private fun appendDigit(d: Char) {
        if (enteredDigits.length < pinLength) {
            enteredDigits.append(d)
            updateDotsUi()

            if (enteredDigits.length == pinLength) {
                checkPin()
            }
        }
    }

    private fun removeDigit() {
        if (enteredDigits.isNotEmpty()) {
            enteredDigits.deleteCharAt(enteredDigits.length - 1)
            updateDotsUi()
        }
    }

    private fun clearDigits() {
        enteredDigits.clear()
        updateDotsUi()
    }

    private fun checkPin() {
        val candidate = enteredDigits.toString()
        if (candidate == correctPin) {
            onSuccess?.invoke()
            dismiss()
        } else {
            // Error shake/red
            updateDotsUi(error = true)
            binding.tvPinSubtitle.text = "Incorrect PIN. Try again."
            binding.tvPinSubtitle.setTextColor(0xFFFF5252.toInt())

            binding.root.postDelayed({
                if (_binding != null) {
                    clearDigits()
                    binding.tvPinSubtitle.text = targetSubtitle
                    binding.tvPinSubtitle.setTextColor(0x99FFFFFF.toInt())
                }
            }, 800)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        onCancelled?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PinEntryDialog"

        fun newInstance(
            title: String,
            subtitle: String,
            correctPin: String,
            pinLength: Int = 4,
            isCancelable: Boolean = true,
            onSuccess: () -> Unit,
            onCancelled: (() -> Unit)? = null
        ): PinEntryDialogFragment {
            return PinEntryDialogFragment().apply {
                this.targetTitle = title
                this.targetSubtitle = subtitle
                this.correctPin = correctPin
                this.pinLength = pinLength
                this.isCancelableDialog = isCancelable
                this.onSuccess = onSuccess
                this.onCancelled = onCancelled
            }
        }
    }
}
