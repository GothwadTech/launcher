package com.gothwad.launcher.ui.view

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.gothwad.launcher.R

/**
 * Clean on-screen keyboard for TV remote / D-Pad navigation.
 * Standard QWERTY layout with Shift, Backspace, Space, Clear, and Enter.
 */
class OnScreenKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onKeyPress: ((Char) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onClear: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null

    private var isShifted: Boolean = false
    private val keyButtons = mutableListOf<Button>()

    private val row1Keys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0')
    private val row2Letters = listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p')
    private val row3Letters = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l')
    private val row4Letters = listOf('z', 'x', 'c', 'v', 'b', 'n', 'm')

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        buildKeyboard()
    }

    private fun buildKeyboard() {
        removeAllViews()
        keyButtons.clear()

        val density = resources.displayMetrics.density
        val keyHeight = (38 * density).toInt()
        val keyMargin = (2 * density).toInt()

        fun createKeyBtn(text: String, weight: Float = 1f): Button {
            return Button(context).apply {
                this.text = text
                this.textSize = 13f
                this.setTextColor(Color.WHITE)
                this.setBackgroundResource(R.drawable.bg_dialog_button)
                this.isFocusable = true
                this.isFocusableInTouchMode = true
                this.isAllCaps = false
                this.setPadding(0, 0, 0, 0)
                val params = LayoutParams(0, keyHeight, weight).apply {
                    setMargins(keyMargin, keyMargin, keyMargin, keyMargin)
                }
                this.layoutParams = params
            }
        }

        // Row 1: Numbers
        val row1 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        for (num in row1Keys) {
            val btn = createKeyBtn(num.toString()).apply {
                setOnClickListener { onKeyPress?.invoke(num) }
            }
            keyButtons.add(btn)
            row1.addView(btn)
        }
        addView(row1)

        // Row 2: Q W E R T Y U I O P
        val row2 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        for (char in row2Letters) {
            val btn = createKeyBtn(if (isShifted) char.uppercaseChar().toString() else char.toString()).apply {
                setOnClickListener { onKeyPress?.invoke(if (isShifted) char.uppercaseChar() else char) }
            }
            keyButtons.add(btn)
            row2.addView(btn)
        }
        addView(row2)

        // Row 3: A S D F G H J K L
        val row3 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        for (char in row3Letters) {
            val btn = createKeyBtn(if (isShifted) char.uppercaseChar().toString() else char.toString()).apply {
                setOnClickListener { onKeyPress?.invoke(if (isShifted) char.uppercaseChar() else char) }
            }
            keyButtons.add(btn)
            row3.addView(btn)
        }
        addView(row3)

        // Row 4: Shift, Z X C V B N M, Backspace
        val row4 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        val shiftBtn = createKeyBtn(if (isShifted) "⇪ ON" else "⇪", weight = 1.3f).apply {
            setTextColor(if (isShifted) 0xFF4FA7FA.toInt() else Color.WHITE)
            setOnClickListener {
                isShifted = !isShifted
                buildKeyboard()
            }
        }
        keyButtons.add(shiftBtn)
        row4.addView(shiftBtn)

        for (char in row4Letters) {
            val btn = createKeyBtn(if (isShifted) char.uppercaseChar().toString() else char.toString()).apply {
                setOnClickListener { onKeyPress?.invoke(if (isShifted) char.uppercaseChar() else char) }
            }
            keyButtons.add(btn)
            row4.addView(btn)
        }

        val backspaceBtn = createKeyBtn("⌫", weight = 1.3f).apply {
            setOnClickListener { onBackspace?.invoke() }
        }
        keyButtons.add(backspaceBtn)
        row4.addView(backspaceBtn)
        addView(row4)

        // Row 5: Clear, Space, Special characters (@._-), Done/Enter
        val row5 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        val clearBtn = createKeyBtn("Clear", weight = 1.3f).apply {
            setTextColor(0xFFFF5252.toInt())
            setOnClickListener { onClear?.invoke() }
        }
        keyButtons.add(clearBtn)
        row5.addView(clearBtn)

        val symbols = listOf('@', '.', '_', '-')
        for (sym in symbols) {
            val btn = createKeyBtn(sym.toString(), weight = 0.8f).apply {
                setOnClickListener { onKeyPress?.invoke(sym) }
            }
            keyButtons.add(btn)
            row5.addView(btn)
        }

        val spaceBtn = createKeyBtn("Space", weight = 2.5f).apply {
            setOnClickListener { onKeyPress?.invoke(' ') }
        }
        keyButtons.add(spaceBtn)
        row5.addView(spaceBtn)

        val enterBtn = createKeyBtn("Done ↵", weight = 1.6f).apply {
            setTextColor(0xFF4FA7FA.toInt())
            setOnClickListener { onEnter?.invoke() }
        }
        keyButtons.add(enterBtn)
        row5.addView(enterBtn)
        addView(row5)
    }

    fun requestFirstFocus(): Boolean {
        return if (keyButtons.isNotEmpty()) {
            keyButtons[0].requestFocus()
        } else {
            false
        }
    }
}
