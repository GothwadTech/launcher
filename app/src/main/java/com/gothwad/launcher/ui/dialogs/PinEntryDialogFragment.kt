package com.gothwad.launcher.ui.dialogs

import android.content.DialogInterface
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
import com.gothwad.launcher.data.LockCredentialType
import com.gothwad.launcher.data.LockSecurity
import com.gothwad.launcher.databinding.DialogPinEntryBinding
import com.gothwad.launcher.ui.AppIcons

class PinEntryDialogFragment : DialogFragment() {

    private var _binding: DialogPinEntryBinding? = null
    private val binding get() = _binding!!

    var targetTitle: String = "Enter PIN"
    var targetSubtitle: String = "Enter your PIN to unlock"
    var credential: LockCredential = LockCredential()
    var isCancelableDialog: Boolean = true

    /** Throttle bucket this dialog reports to: "device", "app" or "vault". */
    var lockScope: String = LockSecurity.SCOPE_DEFAULT

    var onSuccess: (() -> Unit)? = null
    var onCancelled: (() -> Unit)? = null

    private val enteredDigits = StringBuilder()
    private val dotViews = mutableListOf<View>()
    private var isPasswordVisible: Boolean = false
    private var inputModeTv: Boolean = true // true = on-screen grid, false = direct EditText

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

        // Recreated after process death: without the host callback a correct PIN could not
        // unlock anything, so close instead of leaving an inert lock screen on the TV.
        if (onSuccess == null || !credential.ready) {
            dismiss()
            return
        }

        binding.imgPinIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4C8DFF.toInt()))
        binding.tvPinTitle.text = targetTitle
        binding.tvPinSubtitle.text = targetSubtitle

        binding.btnCancel.visibility = if (isCancelableDialog) View.VISIBLE else View.GONE
        binding.btnCancel.setOnClickListener {
            onCancelled?.invoke()
            dismiss()
        }

        if (credential.type == LockCredentialType.NUMERIC) {
            setupNumericUi()
        } else {
            setupPasswordUi()
        }
    }

    // -------------------------------------------------------------
    // NUMERIC PIN FLOW
    // -------------------------------------------------------------
    private fun setupNumericUi() {
        binding.layoutPinSection.visibility = View.VISIBLE
        binding.layoutPasswordSection.visibility = View.GONE
        binding.layoutInputModeToggle.visibility = View.GONE

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
        val length = if (credential.pinLength in listOf(4, 6)) credential.pinLength else 4

        for (i in 0 until length) {
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
        val targetLen = if (credential.pinLength in listOf(4, 6)) credential.pinLength else 4
        if (enteredDigits.length < targetLen) {
            enteredDigits.append(d)
            updateDotsUi()

            if (enteredDigits.length == targetLen) {
                checkNumericPin()
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

    /**
     * Verifies against the stored PBKDF2 hash and applies the brute-force throttle.
     * Also refuses to verify while the scope is locked out.
     */
    private fun verifyCandidate(candidate: String): Boolean {
        val context = context ?: return false
        val remaining = LockSecurity.lockoutRemainingMs(context, lockScope)
        if (remaining > 0L) {
            showError("Too many attempts. Try again in ${(remaining / 1000) + 1}s.")
            return false
        }
        return if (LockSecurity.verify(credential, candidate)) {
            LockSecurity.recordSuccess(context, lockScope)
            true
        } else {
            LockSecurity.recordFailure(context, lockScope)
            false
        }
    }

    private fun showError(message: String) {
        if (_binding == null) return
        binding.tvPinSubtitle.text = message
        binding.tvPinSubtitle.setTextColor(0xFFFF5252.toInt())
        binding.root.postDelayed({
            if (_binding != null) {
                binding.tvPinSubtitle.text = targetSubtitle
                binding.tvPinSubtitle.setTextColor(0x99FFFFFF.toInt())
            }
        }, 1_200)
    }

    private fun checkNumericPin() {
        val candidate = enteredDigits.toString()
        if (verifyCandidate(candidate)) {
            onSuccess?.invoke()
            dismiss()
        } else {
            updateDotsUi(error = true)
            showError("Incorrect PIN. Try again.")

            binding.root.postDelayed({
                if (_binding != null) {
                    clearDigits()
                }
            }, 800)
        }
    }

    // -------------------------------------------------------------
    // ALPHANUMERIC PASSWORD FLOW
    // -------------------------------------------------------------
    private fun setupPasswordUi() {
        binding.layoutPinSection.visibility = View.GONE
        binding.layoutPasswordSection.visibility = View.VISIBLE
        binding.layoutInputModeToggle.visibility = View.VISIBLE

        updateInputModeToggleUi()

        binding.btnModeTvGrid.setOnClickListener {
            inputModeTv = true
            updateInputModeToggleUi()
        }

        binding.btnModeKeyboard.setOnClickListener {
            inputModeTv = false
            updateInputModeToggleUi()
        }

        binding.btnTogglePasswordVisibility.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4C8DFF.toInt())
        )
        binding.btnTogglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val selection = binding.etPassword.selectionEnd
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
            binding.etPassword.setSelection(selection.coerceAtLeast(0))
        }

        // Connect on-screen keyboard
        binding.onScreenKeyboard.onKeyPress = { ch ->
            val curText = binding.etPassword.text.toString()
            binding.etPassword.setText(curText + ch)
            binding.etPassword.setSelection(binding.etPassword.text.length)
        }
        binding.onScreenKeyboard.onBackspace = {
            val curText = binding.etPassword.text.toString()
            if (curText.isNotEmpty()) {
                binding.etPassword.setText(curText.substring(0, curText.length - 1))
                binding.etPassword.setSelection(binding.etPassword.text.length)
            }
        }
        binding.onScreenKeyboard.onClear = {
            binding.etPassword.setText("")
        }
        binding.onScreenKeyboard.onEnter = {
            checkPassword()
        }

        binding.btnSubmitPassword.setOnClickListener {
            checkPassword()
        }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            checkPassword()
            true
        }

        if (inputModeTv) {
            binding.onScreenKeyboard.requestFirstFocus()
        } else {
            binding.etPassword.requestFocus()
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

    private fun checkPassword() {
        val candidate = binding.etPassword.text.toString()
        if (verifyCandidate(candidate)) {
            onSuccess?.invoke()
            dismiss()
        } else {
            showError("Incorrect password. Try again.")

            binding.root.postDelayed({
                if (_binding != null) {
                    binding.etPassword.setText("")
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
            credential: LockCredential,
            isCancelable: Boolean = true,
            lockScope: String = LockSecurity.SCOPE_DEFAULT,
            onSuccess: () -> Unit,
            onCancelled: (() -> Unit)? = null
        ): PinEntryDialogFragment {
            return PinEntryDialogFragment().apply {
                this.targetTitle = title
                this.targetSubtitle = subtitle
                this.credential = credential
                this.isCancelableDialog = isCancelable
                this.lockScope = lockScope
                this.onSuccess = onSuccess
                this.onCancelled = onCancelled
            }
        }
    }
}
