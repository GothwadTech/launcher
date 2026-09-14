package com.gothwad.launcher.ui.dialogs

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.gothwad.launcher.databinding.DialogPinSetupBinding
import com.gothwad.launcher.ui.AppIcons

class PinSetupDialogFragment : DialogFragment() {

    private var _binding: DialogPinSetupBinding? = null
    private val binding get() = _binding!!

    var pinLength: Int = 4
    var onPinSaved: ((String) -> Unit)? = null

    private var step: Int = 1 // 1 = Enter new PIN, 2 = Confirm new PIN
    private var firstEnteredPin: String = ""
    private val currentDigits = StringBuilder()
    private val dotViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPinSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imgPinIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4C8DFF.toInt()))

        updateLengthSelectorUi()
        setupDots()
        setupKeypad()

        binding.btnLength4.setOnClickListener {
            pinLength = 4
            updateLengthSelectorUi()
            resetFlow()
        }

        binding.btnLength6.setOnClickListener {
            pinLength = 6
            updateLengthSelectorUi()
            resetFlow()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        // Handle hardware number keys
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

    private fun updateLengthSelectorUi() {
        if (pinLength == 4) {
            binding.btnLength4.setTextColor(0xFF4C8DFF.toInt())
            binding.btnLength6.setTextColor(0x99FFFFFF.toInt())
        } else {
            binding.btnLength4.setTextColor(0x99FFFFFF.toInt())
            binding.btnLength6.setTextColor(0xFF4C8DFF.toInt())
        }
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
        val count = currentDigits.length
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
        if (currentDigits.length < pinLength) {
            currentDigits.append(d)
            updateDotsUi()

            if (currentDigits.length == pinLength) {
                handlePinComplete()
            }
        }
    }

    private fun removeDigit() {
        if (currentDigits.isNotEmpty()) {
            currentDigits.deleteCharAt(currentDigits.length - 1)
            updateDotsUi()
        }
    }

    private fun clearDigits() {
        currentDigits.clear()
        updateDotsUi()
    }

    private fun resetFlow() {
        step = 1
        firstEnteredPin = ""
        currentDigits.clear()
        binding.layoutLengthSelector.visibility = View.VISIBLE
        binding.tvSetupStep.text = "Step 1 of 2: Choose your PIN"
        binding.tvSetupStep.setTextColor(0x99FFFFFF.toInt())
        setupDots()
    }

    private fun handlePinComplete() {
        if (step == 1) {
            firstEnteredPin = currentDigits.toString()
            step = 2
            currentDigits.clear()
            binding.layoutLengthSelector.visibility = View.GONE
            binding.tvSetupStep.text = "Step 2 of 2: Confirm your PIN"
            updateDotsUi()
        } else {
            val confirmed = currentDigits.toString()
            if (confirmed == firstEnteredPin) {
                onPinSaved?.invoke(confirmed)
                dismiss()
            } else {
                updateDotsUi(error = true)
                binding.tvSetupStep.text = "PINs do not match! Try again."
                binding.tvSetupStep.setTextColor(0xFFFF5252.toInt())

                binding.root.postDelayed({
                    if (_binding != null) {
                        resetFlow()
                    }
                }, 1000)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PinSetupDialog"

        fun newInstance(
            initialLength: Int = 4,
            onSaved: (String) -> Unit
        ): PinSetupDialogFragment {
            return PinSetupDialogFragment().apply {
                this.pinLength = initialLength
                this.onPinSaved = onSaved
            }
        }
    }
}
