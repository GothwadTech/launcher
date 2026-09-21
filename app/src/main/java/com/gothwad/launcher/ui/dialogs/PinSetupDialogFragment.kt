package com.gothwad.launcher.ui.dialogs

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.gothwad.launcher.data.LockCredential
import com.gothwad.launcher.data.LockCredentialType
import com.gothwad.launcher.data.LockSecurity
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

    private var step: Int = 1 // 1 = Enter new PIN/Password, 2 = Confirm
    private var firstEnteredSecret: String = ""

    private val currentDigits = StringBuilder()
    private var dotsHelper: PinDotIndicatorHelper? = null
    private var keypadHelper: PinKeypadHelper? = null

    private var isPasswordVisible: Boolean = false
    private var inputModeTv: Boolean = true

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

        if (onCredentialSaved == null) {
            dismiss()
            return
        }

        binding.imgPinIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4C8DFF.toInt()))

        selectedType = initialCredentialType
        pinLength = if (initialPinLength in listOf(4, 6)) initialPinLength else 4

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnTypePin.setOnClickListener {
            selectedType = LockCredentialType.NUMERIC
            resetFlow()
        }

        binding.btnTypePassword.setOnClickListener {
            selectedType = LockCredentialType.ALPHANUMERIC
            resetFlow()
        }

        binding.btnLength4.setOnClickListener {
            pinLength = 4
            resetFlow()
        }

        binding.btnLength6.setOnClickListener {
            pinLength = 6
            resetFlow()
        }

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
            dotsHelper = PinDotIndicatorHelper(binding.layoutPinDots, pinLength)
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
        val isNumeric = selectedType == LockCredentialType.NUMERIC
        binding.btnTypePin.setTextColor(if (isNumeric) 0xFF4C8DFF.toInt() else 0x99FFFFFF.toInt())
        binding.btnTypePassword.setTextColor(if (isNumeric) 0x99FFFFFF.toInt() else 0xFF4C8DFF.toInt())
    }

    private fun updateLengthSelectorUi() {
        val is4 = pinLength == 4
        binding.btnLength4.setTextColor(if (is4) 0xFF4C8DFF.toInt() else 0x99FFFFFF.toInt())
        binding.btnLength6.setTextColor(if (is4) 0x99FFFFFF.toInt() else 0xFF4C8DFF.toInt())
    }

    private fun updateInputModeToggleUi() {
        binding.btnModeTvGrid.setTextColor(if (inputModeTv) 0xFF4C8DFF.toInt() else 0x99FFFFFF.toInt())
        binding.btnModeKeyboard.setTextColor(if (inputModeTv) 0x99FFFFFF.toInt() else 0xFF4C8DFF.toInt())
        binding.onScreenKeyboard.visibility = if (inputModeTv) View.VISIBLE else View.GONE
        if (!inputModeTv) binding.etPassword.requestFocus()
    }

    private fun setupNumericKeypad() {
        val digitButtons = listOf(
            binding.btnKey0 to '0', binding.btnKey1 to '1', binding.btnKey2 to '2',
            binding.btnKey3 to '3', binding.btnKey4 to '4', binding.btnKey5 to '5',
            binding.btnKey6 to '6', binding.btnKey7 to '7', binding.btnKey8 to '8',
            binding.btnKey9 to '9'
        )

        keypadHelper = PinKeypadHelper(
            buttons = digitButtons,
            btnClear = binding.btnKeyClear,
            btnBackspace = binding.btnKeyBackspace,
            onDigit = { appendDigit(it) },
            onBackspace = { removeDigit() },
            onClear = { clearDigits() }
        )
        keypadHelper?.attachDialogKeyListener(dialog) { selectedType == LockCredentialType.NUMERIC }
    }

    private fun appendDigit(d: Char) {
        if (currentDigits.length < pinLength) {
            currentDigits.append(d)
            dotsHelper?.update(currentDigits.length)
            if (currentDigits.length == pinLength) {
                handleNumericComplete()
            }
        }
    }

    private fun removeDigit() {
        if (currentDigits.isNotEmpty()) {
            currentDigits.deleteCharAt(currentDigits.length - 1)
            dotsHelper?.update(currentDigits.length)
        }
    }

    private fun clearDigits() {
        currentDigits.clear()
        dotsHelper?.update(currentDigits.length)
    }

    private fun handleNumericComplete() {
        if (step == 1) {
            firstEnteredSecret = currentDigits.toString()
            step = 2
            currentDigits.clear()
            binding.layoutTypeSelector.visibility = View.GONE
            binding.layoutLengthSelector.visibility = View.GONE
            binding.tvSetupStep.text = "Step 2 of 2: Confirm your $pinLength-digit PIN"
            dotsHelper?.update(0)
        } else {
            val confirmed = currentDigits.toString()
            if (confirmed == firstEnteredSecret) {
                val cred = LockSecurity.createCredential(
                    secret = confirmed,
                    type = LockCredentialType.NUMERIC,
                    pinLength = pinLength,
                )
                onCredentialSaved?.invoke(cred)
                dismiss()
            } else {
                dotsHelper?.update(currentDigits.length, isError = true)
                binding.tvSetupStep.text = "PINs do not match! Try again."
                binding.tvSetupStep.setTextColor(0xFFFF5252.toInt())

                binding.root.postDelayed({
                    if (_binding != null) resetFlow()
                }, 1000)
            }
        }
    }

    private fun setupPasswordComponents() {
        binding.btnTogglePasswordVisibility.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4C8DFF.toInt())
        )
        binding.btnTogglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val sel = binding.etPassword.selectionEnd
            val (inputType, icon) = if (isPasswordVisible) {
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) to AppIcons.PATH_LOCK_OPEN
            } else {
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) to AppIcons.PATH_LOCK
            }
            binding.etPassword.inputType = inputType
            binding.btnTogglePasswordVisibility.setImageDrawable(AppIcons.createDrawable(icon, 0xFF4C8DFF.toInt()))
            binding.etPassword.setSelection(sel.coerceAtLeast(0))
        }

        binding.onScreenKeyboard.onKeyPress = { ch ->
            binding.etPassword.append(ch.toString())
        }
        binding.onScreenKeyboard.onBackspace = {
            val cur = binding.etPassword.text.toString()
            if (cur.isNotEmpty()) binding.etPassword.setText(cur.dropLast(1))
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }
        binding.onScreenKeyboard.onClear = { binding.etPassword.setText("") }
        binding.onScreenKeyboard.onEnter = { handlePasswordStep() }
        binding.btnNextPassword.setOnClickListener { handlePasswordStep() }
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
                    if (_binding != null) resetFlow()
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
