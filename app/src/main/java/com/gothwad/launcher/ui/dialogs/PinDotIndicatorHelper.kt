package com.gothwad.launcher.ui.dialogs

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout

/**
 * Helper to manage PIN dot views (idle, filled, error).
 */
class PinDotIndicatorHelper(
    private val container: LinearLayout,
    private val pinLength: Int,
) {
    private val dotViews = mutableListOf<View>()

    init {
        setupDots()
    }

    private fun setupDots() {
        container.removeAllViews()
        dotViews.clear()

        val density = container.resources.displayMetrics.density
        val sizePx = (16 * density).toInt()
        val marginPx = (8 * density).toInt()

        for (i in 0 until pinLength) {
            val dot = View(container.context).apply {
                val params = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                }
                layoutParams = params
                background = createDotDrawable(filled = false, error = false)
            }
            dotViews.add(dot)
            container.addView(dot)
        }
    }

    fun update(count: Int, isError: Boolean = false) {
        for (i in dotViews.indices) {
            dotViews[i].background = createDotDrawable(filled = i < count, error = isError)
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
}
