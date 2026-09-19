package com.gothwad.launcher.ui.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import com.gothwad.launcher.R
import com.gothwad.launcher.data.LockCredential
import com.gothwad.launcher.data.LockCredentialType
import com.gothwad.launcher.databinding.DialogPinEntryBinding
import com.gothwad.launcher.ui.AppIcons

/**
 * Full-screen System Alert Window Overlay hosting the security lock UI directly via WindowManager.
 * Used by LauncherAccessibilityService to block access to locked apps system-wide
 * (Settings app, notifications, recents switcher, etc.) and by MainActivity for Device Lock.
 */
class SystemLockOverlayView(
    private val context: Context,
    private val credential: LockCredential,
    private val title: String,
    private val subtitle: String,
    private val onSuccess: () -> Unit,
    private val onDismissOrBack: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val binding: DialogPinEntryBinding = DialogPinEntryBinding.inflate(LayoutInflater.from(context))
    private val root: View = binding.root

    private val enteredDigits = StringBuilder()
    private val dotViews = mutableListOf<View>()
    private var isPasswordVisible: Boolean = false
    private var inputModeTv: Boolean = true
    private var isAttached = false

    init {
        setupUi()
    }

    private fun setupUi() {
        binding.imgPinIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4C8DFF.toInt()))
        binding.tvPinTitle.text = title
        binding.tvPinSubtitle.text = subtitle

        binding.btnCancel.text = "Exit / Back"
        binding.btnCancel.visibility = View.VISIBLE
        binding.btnCancel.setOnClickListener {
            dismiss()
            onDismissOrBack()
        }

        // Intercept hardware Back key and remote number keys on the root overlay view
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dismiss()
                    onDismissOrBack()
                    return@setOnKeyListener true
                }
                if (credential.type == LockCredentialType.NUMERIC) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { appendDigit('0'); return@setOnKeyListener true }
                        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> { appendDigit('1'); return@setOnKeyListener true }
                        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> { appendDigit('2'); return@setOnKeyListener true }
                        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> { appendDigit('3'); return@setOnKeyListener true }
                        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> { appendDigit('4'); return@setOnKeyListener true }
                        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> { appendDigit('5'); return@setOnKeyListener true }
                        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> { appendDigit('6'); return@setOnKeyListener true }
                        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> { appendDigit('7'); return@setOnKeyListener true }
                        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> { appendDigit('8'); return@setOnKeyListener true }
                        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> { appendDigit('9'); return@setOnKeyListener true }
                        KeyEvent.KEYCODE_DEL -> { removeDigit(); return@setOnKeyListener true }
                        KeyEvent.KEYCODE_CLEAR -> { clearDigits(); return@setOnKeyListener true }
                    }
                }
            }
            false
        }

        if (credential.type == LockCredentialType.NUMERIC) {
            setupNumericUi()
        } else {
            setupPasswordUi()
        }
    }

    private fun setupNumericUi() {
        binding.layoutPinSection.visibility = View.VISIBLE
        binding.layoutPasswordSection.visibility = View.GONE
        binding.layoutInputModeToggle.visibility = View.GONE

        setupDots()
        setupKeypad()
        binding.btnKey1.requestFocus()
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
            dismiss()
            onSuccess()
        } else {
            updateDotsUi(error = true)
            binding.tvPinSubtitle.text = "Incorrect PIN. Try again."
            binding.tvPinSubtitle.setTextColor(0xFFFF5252.toInt())

            root.postDelayed({
                clearDigits()
                binding.tvPinSubtitle.text = subtitle
                binding.tvPinSubtitle.setTextColor(0x99FFFFFF.toInt())
            }, 800)
        }
    }

    private fun setupPasswordUi() {
        binding.layoutPinSection.visibility = View.GONE
        binding.layoutPasswordSection.visibility = View.VISIBLE
        binding.layoutInputModeToggle.visibility = View.VISIBLE

        updateInputModeToggleUi()

        binding.btnModeTvGrid.setOnClickListener {
            inputModeTv = true
            updateInputModeToggleUi()
        }

        binding.btnModePcKeyboard.setOnClickListener {
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
            binding.btnModePcKeyboard.setTextColor(0x99FFFFFF.toInt())
            binding.onScreenKeyboard.visibility = View.VISIBLE
        } else {
            binding.btnModeTvGrid.setTextColor(0x99FFFFFF.toInt())
            binding.btnModePcKeyboard.setTextColor(0xFF4C8DFF.toInt())
            binding.onScreenKeyboard.visibility = View.GONE
            binding.etPassword.requestFocus()
        }
    }

    private fun checkPassword() {
        val candidate = binding.etPassword.text.toString()
        if (candidate == credential.value) {
            dismiss()
            onSuccess()
        } else {
            binding.tvPinSubtitle.text = "Incorrect password. Try again."
            binding.tvPinSubtitle.setTextColor(0xFFFF5252.toInt())

            root.postDelayed({
                binding.etPassword.setText("")
                binding.tvPinSubtitle.text = subtitle
                binding.tvPinSubtitle.setTextColor(0x99FFFFFF.toInt())
            }, 800)
        }
    }

    fun show() {
        if (isAttached) return

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        runCatching {
            windowManager.addView(root, params)
            isAttached = true
            root.requestFocus()
        }
    }

    fun dismiss() {
        if (!isAttached) return
        runCatching {
            windowManager.removeViewImmediate(root)
        }
        isAttached = false
    }

    fun isShowing(): Boolean = isAttached
}
