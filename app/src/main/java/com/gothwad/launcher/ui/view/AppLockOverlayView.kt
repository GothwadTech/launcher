package com.gothwad.launcher.ui.view

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.gothwad.launcher.data.LockCredential
import com.gothwad.launcher.data.LockCredentialType
import com.gothwad.launcher.data.LockSecurity
import com.gothwad.launcher.databinding.DialogPinEntryBinding
import com.gothwad.launcher.ui.AppIcons
import com.gothwad.launcher.ui.dialogs.PinDotIndicatorHelper
import com.gothwad.launcher.ui.dialogs.PinKeypadHelper

/**
 * WindowManager overlay hosting the App Lock and Vault UI.
 * Used by LauncherAccessibilityService to block unauthorized access to locked apps.
 */
class AppLockOverlayView(
    private val context: Context,
    private val credential: LockCredential,
    private val title: String,
    private val subtitle: String,
    private val onSuccess: () -> Unit,
    private val onDismissOrBack: () -> Unit,
    private val lockScope: String = LockSecurity.SCOPE_APP
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val binding: DialogPinEntryBinding = DialogPinEntryBinding.inflate(LayoutInflater.from(context))
    private val root: View = binding.root

    private val enteredDigits = StringBuilder()
    private var dotsHelper: PinDotIndicatorHelper? = null
    private var keypadHelper: PinKeypadHelper? = null
    private var isPasswordVisible: Boolean = false
    private var inputModeTv: Boolean = true
    private var isAttached = false

    init {
        com.gothwad.launcher.ui.DensityAdapter.apply(context)
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

        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dismiss()
                    onDismissOrBack()
                    return@setOnKeyListener true
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

        val length = if (credential.pinLength in listOf(4, 6)) credential.pinLength else 4
        dotsHelper = PinDotIndicatorHelper(binding.layoutPinDots, length)

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
        binding.btnKey1.requestFocus()
    }

    private fun appendDigit(d: Char) {
        val targetLen = if (credential.pinLength in listOf(4, 6)) credential.pinLength else 4
        if (enteredDigits.length < targetLen) {
            enteredDigits.append(d)
            dotsHelper?.update(enteredDigits.length)
            if (enteredDigits.length == targetLen) {
                checkNumericPin()
            }
        }
    }

    private fun removeDigit() {
        if (enteredDigits.isNotEmpty()) {
            enteredDigits.deleteCharAt(enteredDigits.length - 1)
            dotsHelper?.update(enteredDigits.length)
        }
    }

    private fun clearDigits() {
        enteredDigits.clear()
        dotsHelper?.update(0)
    }

    private fun checkNumericPin() {
        val candidate = enteredDigits.toString()
        if (verifyCandidate(candidate)) {
            dismiss()
            onSuccess()
        } else {
            dotsHelper?.update(enteredDigits.length, isError = true)
            showError("Incorrect PIN. Try again.")
            binding.root.postDelayed({
                clearDigits()
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
            val (inputType, icon) = if (isPasswordVisible) {
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) to AppIcons.PATH_LOCK_OPEN
            } else {
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) to AppIcons.PATH_LOCK
            }
            binding.etPassword.inputType = inputType
            binding.btnTogglePasswordVisibility.setImageDrawable(AppIcons.createDrawable(icon, 0xFF4C8DFF.toInt()))
            binding.etPassword.setSelection(selection.coerceAtLeast(0))
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
        binding.onScreenKeyboard.onEnter = { checkPassword() }
        binding.btnSubmitPassword.setOnClickListener { checkPassword() }
        binding.etPassword.setOnEditorActionListener { _, _, _ ->
            checkPassword()
            true
        }

        if (inputModeTv) binding.onScreenKeyboard.requestFirstFocus() else binding.etPassword.requestFocus()
    }

    private fun updateInputModeToggleUi() {
        binding.btnModeTvGrid.setTextColor(if (inputModeTv) 0xFF4C8DFF.toInt() else 0x99FFFFFF.toInt())
        binding.btnModeKeyboard.setTextColor(if (inputModeTv) 0x99FFFFFF.toInt() else 0xFF4C8DFF.toInt())
        binding.onScreenKeyboard.visibility = if (inputModeTv) View.VISIBLE else View.GONE
        if (!inputModeTv) binding.etPassword.requestFocus()
    }

    private fun checkPassword() {
        val candidate = binding.etPassword.text.toString()
        if (verifyCandidate(candidate)) {
            dismiss()
            onSuccess()
        } else {
            showError("Incorrect password. Try again.")
            binding.root.postDelayed({
                binding.etPassword.setText("")
            }, 800)
        }
    }

    private fun verifyCandidate(candidate: String): Boolean {
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
        binding.tvPinSubtitle.text = message
        binding.tvPinSubtitle.setTextColor(0xFFFF5252.toInt())
        binding.root.postDelayed({
            binding.tvPinSubtitle.text = subtitle
            binding.tvPinSubtitle.setTextColor(0x99FFFFFF.toInt())
        }, 1200)
    }

    fun show(): Boolean {
        if (isAttached) return true
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
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        return try {
            windowManager.addView(root, params)
            isAttached = true
            root.requestFocus()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun dismiss() {
        if (!isAttached) return
        try {
            windowManager.removeView(root)
        } catch (_: Exception) {}
        isAttached = false
    }
}
