package com.gothwad.launcher.ui.dialogs

import android.app.Dialog
import android.view.KeyEvent
import android.widget.Button

/**
 * Helper to wire up 0-9 numeric keys, clear, backspace, and hardware key listeners for PIN entry/setup.
 */
class PinKeypadHelper(
    private val buttons: List<Pair<Button, Char>>,
    private val btnClear: Button?,
    private val btnBackspace: Button?,
    private val onDigit: (Char) -> Unit,
    private val onBackspace: () -> Unit,
    private val onClear: () -> Unit,
) {
    init {
        for ((btn, digit) in buttons) {
            btn.setOnClickListener { onDigit(digit) }
        }
        btnClear?.setOnClickListener { onClear() }
        btnBackspace?.setOnClickListener { onBackspace() }
    }

    fun attachDialogKeyListener(dialog: Dialog?, isNumericActive: () -> Boolean) {
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (isNumericActive() && event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { onDigit('0'); true }
                    KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> { onDigit('1'); true }
                    KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> { onDigit('2'); true }
                    KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> { onDigit('3'); true }
                    KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> { onDigit('4'); true }
                    KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> { onDigit('5'); true }
                    KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> { onDigit('6'); true }
                    KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> { onDigit('7'); true }
                    KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> { onDigit('8'); true }
                    KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> { onDigit('9'); true }
                    KeyEvent.KEYCODE_DEL -> { onBackspace(); true }
                    KeyEvent.KEYCODE_CLEAR -> { onClear(); true }
                    else -> false
                }
            } else {
                false
            }
        }
    }
}
