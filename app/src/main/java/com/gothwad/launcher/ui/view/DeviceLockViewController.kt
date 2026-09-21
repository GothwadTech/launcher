package com.gothwad.launcher.ui.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.gothwad.launcher.data.LockCredential
import com.gothwad.launcher.data.LockCredentialType
import com.gothwad.launcher.databinding.DialogPinEntryBinding
import com.gothwad.launcher.ui.AppIcons

/**
 * Solid in-activity controller for Device Lock (Phase 4).
 * Renders directly inside MainActivity's device_lock_container without requiring
 * SYSTEM_ALERT_WINDOW or overlay permissions, completely eliminating blackout errors.
 * Consumes all touches and remote/keyboard navigation until unlocked.
 */
class DeviceLockViewController(
    private val container: FrameLayout,
    private val credential: LockCredential,
    private val onUnlocked: () -> Unit
) {
    private val context: Context = container.context
    private val binding: DialogPinEntryBinding =
        DialogPinEntryBinding.inflate(LayoutInflater.from(context), container, false)

    private val enteredDigits = StringBuilder()
    private val dotViews = mutableListOf<View>()
    private var isPasswordVisible: Boolean = false
    private var inputModeTv: Boolean = true
    private var isUnlocked: Boolean = false

    fun show() {
        container.removeAllViews()
        container.addView(binding.root)
        container.visibility = View.VISIBLE
        container.bringToFront()

        // Block all clicks, right-clicks, long clicks, and touches from reaching views underneath
        container.isClickable = true
        container.isFocusable = true
        container.isFocusableInTouchMode = true
        container.setOnTouchListener { _, _ -> true }
        container.setOnContextClickListener { true }
        container.setOnLongClickListener { true }

        setupUi()
    }

    private fun setupUi() {
        binding.imgPinIcon.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4C8DFF.toInt())
        )
        binding.tvPinTitle.text = "Device Locked"
        binding.tvPinSubtitle.text = "Enter credential to access device"

        // Device lock is mandatory on cold boot; cannot be cancelled or escaped
        binding.btnCancel.visibility = View.GONE

        if (credential.type == LockCredentialType.NUMERIC) {
            setupNumericUi()
        } else {
            setupPasswordUi()
        }
    }

    // =========================================================================
    // NUMERIC PIN FLOW
    // =========================================================================

    private fun setupNumericUi() {
        binding.layoutPinSection.visibility = View.VISIBLE
        binding.layoutPasswordSection.visibility = View.GONE
        binding.layoutInputModeToggle.visibility = View.GONE

        setupDots()
        setupKeypad()

        binding.btnKey1.post {
            binding.btnKey1.requestFocus()
        }
    }

    private fun setupDots() {
        binding.layoutPinDots.removeAllViews()
        dotViews.clear()

        val density = context.resources.displayMetrics.density
        val sizePx = (16 * density).toInt()
        val marginPx = (8 * density).toInt()
        val length = if (credential.pinLength in listOf(4, 6)) credential.pinLength else 4

        for (i in 0 until length) {
            val dot = View(context).apply {
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

    private fun checkNumericPin() {
        val candidate = enteredDigits.toString()
        if (candidate == credential.value) {
            unlockSuccess()
        } else {
            updateDotsUi(error = true)
            binding.tvPinSubtitle.text = "Incorrect PIN. Try again."
            binding.tvPinSubtitle.setTextColor(0xFFFF5252.toInt())
            shakeCard()

            binding.root.postDelayed({
                clearDigits()
                binding.tvPinSubtitle.text = "Enter credential to access device"
                binding.tvPinSubtitle.setTextColor(0x99FFFFFF.toInt())
            }, 800)
        }
    }

    // =========================================================================
    // PASSWORD FLOW
    // =========================================================================

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

        binding.etPassword.setOnEditorActionListener { _, _, _ ->
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
        if (candidate == credential.value) {
            unlockSuccess()
        } else {
            binding.tvPinSubtitle.text = "Incorrect password. Try again."
            binding.tvPinSubtitle.setTextColor(0xFFFF5252.toInt())
            shakeCard()

            binding.root.postDelayed({
                binding.etPassword.setText("")
                binding.tvPinSubtitle.text = "Enter credential to access device"
                binding.tvPinSubtitle.setTextColor(0x99FFFFFF.toInt())
            }, 800)
        }
    }

    // =========================================================================
    // KEY EVENT & UNLOCK HANDLING
    // =========================================================================

    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (isUnlocked) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                shakeCard()
                binding.tvPinSubtitle.text = "Device is locked. Enter credential to unlock."
                binding.tvPinSubtitle.setTextColor(0xFFFFB300.toInt())
                return true
            }

            if (credential.type == LockCredentialType.NUMERIC) {
                when (keyCode) {
                    KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { appendDigit('0'); return true }
                    KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> { appendDigit('1'); return true }
                    KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> { appendDigit('2'); return true }
                    KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> { appendDigit('3'); return true }
                    KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> { appendDigit('4'); return true }
                    KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> { appendDigit('5'); return true }
                    KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> { appendDigit('6'); return true }
                    KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> { appendDigit('7'); return true }
                    KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> { appendDigit('8'); return true }
                    KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> { appendDigit('9'); return true }
                    KeyEvent.KEYCODE_DEL -> { removeDigit(); return true }
                    KeyEvent.KEYCODE_CLEAR -> { clearDigits(); return true }
                }
            } else {
                if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    checkPassword()
                    return true
                }
            }
        }
        return false
    }

    private fun shakeCard() {
        val card = binding.cardDialogContainer
        card.animate()
            .translationX(16f)
            .setDuration(40)
            .withEndAction {
                card.animate()
                    .translationX(-16f)
                    .setDuration(40)
                    .withEndAction {
                        card.animate()
                            .translationX(10f)
                            .setDuration(40)
                            .withEndAction {
                                card.animate()
                                    .translationX(-6f)
                                    .setDuration(40)
                                    .withEndAction {
                                        card.translationX = 0f
                                    }.start()
                            }.start()
                    }.start()
            }.start()
    }

    private fun unlockSuccess() {
        isUnlocked = true
        container.animate()
            .alpha(0f)
            .setDuration(220)
            .withEndAction {
                container.visibility = View.GONE
                container.removeAllViews()
                container.alpha = 1f
                onUnlocked()
            }
            .start()
    }
}
