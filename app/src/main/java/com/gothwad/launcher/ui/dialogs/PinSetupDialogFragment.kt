package com.gothwad.launcher.ui.dialogs

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.gothwad.launcher.data.LockCredential
import com.gothwad.launcher.data.LockSecurity
import com.gothwad.launcher.data.LockCredentialType
import com.gothwad.launcher.databinding.DialogPinSetupBinding
import com.gothwad.launcher.ui.AppIcons

class PinSetupDialogFragment : DialogFragment() {

    private var _binding: DialogPinSetupBinding? = null
    private val binding get() = _binding!!

    var initialCredentialType: LockCredentialType = LockCredentialType.NUMERIC
    var initialPinLength: Int = 4
    var onCredentialSaved: ((LockCredential) -> Unit)? = null

    private var selectedType: LockCredentialType = LockCredentialType.NUMERIC
    private var pinLength: Int = 4

    private var step: Int = 1 // 1 = Enter new PIN/Password, 2 = Confirm new PIN/Password
    private var firstEnteredSecret: String = ""

    // Numeric mode state
    private val currentDigits = StringBuilder()
    private val dotViews = mutableListOf<View>()

    // Password mode state
    private var isPasswordVisible: Boolean = false
    private var inputModeTv: Boolean = true // true = on-screen grid, false = direct EditText

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

        if (onCredentialSaved == null) { // recreated after process death
            dismiss()
            return
        }

        binding.imgPinIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4C8DFF.toInt()))

        selectedType = initialCredentialType
        pinLength = if (initialPinLength in listOf(4, 6)) initialPinLength else 4

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        // Credential Type selector
        binding.btnTypePin.setOnClickListener {
            selectedType = LockCredentialType.NUMERIC
            resetFlow()
        }

        binding.btnTypePassword.setOnClickListener {
            selectedType = LockCredentialType.ALPHANUMERIC
            resetFlow()
        }

        // PIN Length selector
        binding.btnLength4.setOnClickListener {
            pinLength = 4
            resetFlow()
        }

        binding.btnLength6.setOnClickListener {
            pinLength = 6
            resetFlow()
        }

        // Input Mode toggle
        binding.btnModeTvGrid.setOnClickListener {
            inputModeTv = true
            updateInputModeToggleUi()
        }

        binding.btnModeKeyboard.setOnClickListener {
            inputModeTv = false
            updateInputModeToggleUi()
        }

        setupNumericKeypad()
        setupPasswordComponents()

        resetFlow()
    }

    private fun resetFlow() {
        step = 1
        firstEnteredSecret = ""
        currentDigits.clear()
        _binding?.etPassword?.setText("")

        updateTypeSelectorUi()
        updateLengthSelectorUi()
        updateInputModeToggleUi()

        if (selectedType == LockCredentialType.NUMERIC) {
            binding.layoutTypeSelector.visibility = View.VISIBLE
            binding.layoutLengthSelector.visibility = View.VISIBLE
            binding.layoutInputModeToggle.visibility = View.GONE
            binding.layoutPinSection.visibility = View.VISIBLE
            binding.layoutPasswordSection.visibility = View.GONE

            binding.tvSetupTitle.text = "Set Up PIN"
            binding.tvSetupStep.text = "Step 1 of 2: Choose your $pinLength-digit PIN"
            binding.tvSetupStep.setTextColor(0x99FFFFFF.toInt())
            setupDots()
            binding.btnKey1.requestFocus()
        } else {
            binding.layoutTypeSelector.visibility = View.VISIBLE
            binding.layoutLengthSelector.visibility = View.GONE
            binding.layoutInputModeToggle.visibility = View.VISIBLE
            binding.layoutPinSection.visibility = View.GONE
            binding.layoutPasswordSection.visibility = View.VISIBLE

            binding.tvSetupTitle.text = "Set Up Password"
            binding.tvSetupStep.text = "Step 1 of 2: Enter your alphanumeric password"
            binding.tvSetupStep.setTextColor(0x99FFFFFF.toInt())
            binding.btnNextPassword.text = "Next ➔"

            if (inputModeTv) {
                binding.onScreenKeyboard.requestFirstFocus()
            } else {
                binding.etPassword.requestFocus()
            }
        }
    }

    private fun updateTypeSelectorUi() {
        if (selectedType == LockCredentialType.NUMERIC) {
            binding.btnTypePin.setTextColor(0xFF4C8DFF.toInt())
            binding.btnTypePassword.setTextColor(0x99FFFFFF.toInt())
        } else {
            binding.btnTypePin.setTextColor(0x99FFFFFF.toInt())
            binding.btnTypePassword.setTextColor(0xFF4C8DFF.toInt())
        }
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

    private fun updateInputModeToggleUi() {
        if (inputModeTv) {
            binding.btnModeTvGrid.setTextColor(0xFF4C8DFF.toInt())
            binding.btnModeKeyboard.setTextColor(0x99FFFFFF.toInt())
            binding.onScreenKeyboard.visibility = View.VISIBLE
        } else {
            binding.btnModeTvGrid.setTextColor(0x99FFFFFF.toInt())
            binding.btnModeKeyboard.setTextColor(0xFF4C8DFF.toInt())
            binding.onScreenKeyboard.visibility = View.GONE
            binding.etPassword.requestFocus()
        }
    }

    // -------------------------------------------------------------
    // NUMERIC PIN LOGIC
    // -------------------------------------------------------------
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

    private fun setupNumericKeypad() {
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

        // Hardware number keys
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (selectedType == LockCredentialType.NUMERIC && event.action == KeyEvent.ACTION_DOWN) {
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
    }

    private fun appendDigit(d: Char) {
        if (currentDigits.length < pinLength) {
            currentDigits.append(d)
            updateDotsUi()

            if (currentDigits.length == pinLength) {
                handleNumericComplete()
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

    private fun handleNumericComplete() {
        if (step == 1) {
            firstEnteredSecret = currentDigits.toString()
            step = 2
            currentDigits.clear()
            binding.layoutTypeSelector.visibility = View.GONE
            binding.layoutLengthSelector.visibility = View.GONE
            binding.tvSetupStep.text = "Step 2 of 2: Confirm your $pinLength-digit PIN"
            updateDotsUi()
        } else {
            val confirmed = currentDigits.toString()
            if (confirmed == firstEnteredSecret) {
                // Store a salted PBKDF2 hash - never the PIN itself.
                val cred = LockSecurity.createCredential(
                    secret = confirmed,
                    type = LockCredentialType.NUMERIC,
                    pinLength = pinLength,
                )
                onCredentialSaved?.invoke(cred)
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

    // -------------------------------------------------------------
    // ALPHANUMERIC PASSWORD LOGIC
    // -------------------------------------------------------------
    private fun setupPasswordComponents() {
        binding.btnTogglePasswordVisibility.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4C8DFF.toInt())
        )
        binding.btnTogglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val sel = binding.etPassword.selectionEnd
            if (isPasswordVisible) {
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.btnTogglePasswordVisibility.setImageDrawable(
                    AppIcons.createDrawable(AppIcons.PATH_LOCK_OPEN, 0xFF4C8DFF.toInt())
                )
            } else {
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.btnTogglePasswordVisibility.setImageDrawable(
                    AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4C8DFF.toInt())
                )
            }
            binding.etPassword.setSelection(sel.coerceAtLeast(0))
        }

        binding.onScreenKeyboard.onKeyPress = { ch ->
            val cur = binding.etPassword.text.toString()
            binding.etPassword.setText(cur + ch)
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }
        binding.onScreenKeyboard.onBackspace = {
            val cur = binding.etPassword.text.toString()
            if (cur.isNotEmpty()) {
                binding.etPassword.setText(cur.substring(0, cur.length - 1))
                binding.etPassword.setSelection(binding.etPassword.text.length)
            }
        }
        binding.onScreenKeyboard.onClear = {
            binding.etPassword.setText("")
        }
        binding.onScreenKeyboard.onEnter = {
            handlePasswordStep()
        }

        binding.btnNextPassword.setOnClickListener {
            handlePasswordStep()
        }

        binding.etPassword.setOnEditorActionListener { _, _, _ ->
            handlePasswordStep()
            true
        }
    }

    private fun handlePasswordStep() {
        val entered = binding.etPassword.text.toString().trim()
        if (entered.isEmpty()) {
            binding.tvSetupStep.text = "Password cannot be empty!"
            binding.tvSetupStep.setTextColor(0xFFFF5252.toInt())
            return
        }

        if (step == 1) {
            firstEnteredSecret = entered
            step = 2
            binding.etPassword.setText("")
            binding.layoutTypeSelector.visibility = View.GONE
            binding.tvSetupStep.text = "Step 2 of 2: Confirm your password"
            binding.tvSetupStep.setTextColor(0x99FFFFFF.toInt())
            binding.btnNextPassword.text = "Save Password ✓"
            if (inputModeTv) binding.onScreenKeyboard.requestFirstFocus() else binding.etPassword.requestFocus()
        } else {
            if (entered == firstEnteredSecret) {
                // Store a salted PBKDF2 hash - never the password itself.
                val cred = LockSecurity.createCredential(
                    secret = entered,
                    type = LockCredentialType.ALPHANUMERIC,
                    pinLength = 4,
                )
                onCredentialSaved?.invoke(cred)
                dismiss()
            } else {
                binding.tvSetupStep.text = "Passwords do not match! Try again."
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
            initialType: LockCredentialType = LockCredentialType.NUMERIC,
            initialPinLength: Int = 4,
            onSaved: (LockCredential) -> Unit
        ): PinSetupDialogFragment {
            return PinSetupDialogFragment().apply {
                this.initialCredentialType = initialType
                this.initialPinLength = initialPinLength
                this.onCredentialSaved = onSaved
            }
        }
    }
}
