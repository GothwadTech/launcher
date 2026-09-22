package com.gothwad.launcher.ui.view

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath

/**
 * Android native Drawable implementing iOS-style continuous ("squircle") corners
 * via androidx.graphics.shapes.
 */
class SmoothCornerDrawable(
    cornerRadiusPx: Float = 0f,
    fillColor: Int = 0,
    strokeColor: Int = 0,
    strokeWidthPx: Float = 0f,
    private val smoothing: Float = 0.6f
) : Drawable() {

    var cornerRadiusPx: Float = cornerRadiusPx
        set(value) {
            if (field != value) {
                field = value
                invalidatePath()
            }
        }

    var fillColor: Int = fillColor
        set(value) {
            if (field != value) {
                field = value
                fillPaint.color = value
                invalidateSelf()
            }
        }

    var strokeColor: Int = strokeColor
        set(value) {
            if (field != value) {
                field = value
                strokePaint.color = value
                invalidateSelf()
            }
        }

    var strokeWidthPx: Float = strokeWidthPx
        set(value) {
            if (field != value) {
                field = value
                strokePaint.strokeWidth = value
                invalidateSelf()
            }
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = strokeColor
        strokeWidth = strokeWidthPx
    }

    private var cachedPath: Path? = null
    private var lastWidth = -1
    private var lastHeight = -1

    private fun invalidatePath() {
        cachedPath = null
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (bounds.width() != lastWidth || bounds.height() != lastHeight) {
            lastWidth = bounds.width()
            lastHeight = bounds.height()
            invalidatePath()
        }
    }

    private fun obtainPath(width: Float, height: Float): Path {
        cachedPath?.let { return it }

        val minDim = minOf(width, height)
        val r = cornerRadiusPx.coerceIn(0f, minDim / 2f)
        val path = if (minDim < 1f || r < 0.5f) {
            Path().apply { addRect(0f, 0f, width, height, Path.Direction.CW) }
        } else {
            runCatching {
                RoundedPolygon.rectangle(
                    width = width,
                    height = height,
                    rounding = CornerRounding(r, smoothing),
                    centerX = width / 2f,
                    centerY = height / 2f,
                ).toPath()
            }.getOrElse {
                Path().apply { addRoundRect(0f, 0f, width, height, r, r, Path.Direction.CW) }
            }
        }
        cachedPath = path
        return path
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())

        val path = obtainPath(w, h)

        if (fillPaint.color != 0) {
            canvas.drawPath(path, fillPaint)
        }

        if (strokePaint.strokeWidth > 0f && strokePaint.color != 0) {
            canvas.drawPath(path, strokePaint)
        }

        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * ViewOutlineProvider for clipping views to smooth continuous squircle corners.
 */
class SmoothOutlineProvider(
    var cornerRadiusPx: Float = 0f,
    private val smoothing: Float = 0.6f
) : ViewOutlineProvider() {

    override fun getOutline(view: View, outline: android.graphics.Outline) {
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return

        val minDim = minOf(w, h).toFloat()
        val r = cornerRadiusPx.coerceIn(0f, minDim / 2f)

        if (r < 1f) {
            outline.setRect(0, 0, w, h)
        } else {
            outline.setRoundRect(0, 0, w, h, r)
        }
    }
}
