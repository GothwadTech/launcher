package com.gothwad.launcher.ui.pc

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.card.MaterialCardView
import com.gothwad.launcher.databinding.LayoutPcWindowBinding
import com.gothwad.launcher.ui.AppIcons
import kotlin.math.max

/**
 * Windows 11 style window view with titlebar (icon + title + minimize/maximize/close)
 * Supports drag to move and bottom-right corner drag to resize.
 */
class PcWindowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var binding: LayoutPcWindowBinding
    private var windowData: PcWindow? = null

    var onAction: ((PcWindow, PcWindowAction) -> Unit)? = null

    // Drag handling
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var windowStartX = 0f
    private var windowStartY = 0f
    private var isDragging = false

    // Resize handling
    private var resizeStartX = 0f
    private var resizeStartY = 0f
    private var windowStartW = 0
    private var windowStartH = 0
    private var isResizing = false

    init {
        val inflater = LayoutInflater.from(context)
        binding = LayoutPcWindowBinding.inflate(inflater, this, true)

        // Setup window control icons
        binding.imgMinimize.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_MINUS, Color.WHITE))
        binding.imgMaximize.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_MAXIMIZE, Color.WHITE))
        binding.imgClose.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))
        binding.imgResizeIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_RESIZE, Color.WHITE))

        setupDragHandling()
        setupResizeHandling()
        setupClickListeners()
    }

    fun bind(window: PcWindow, isFocused: Boolean) {
        windowData = window

        // Icon
        if (window.app.icon != null) {
            binding.imgWindowIcon.setImageBitmap(window.app.icon)
            binding.imgContentIcon.setImageBitmap(window.app.icon)
        } else {
            binding.imgWindowIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE))
            binding.imgContentIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE))
        }

        // Title
        binding.tvWindowTitle.text = window.app.label
        binding.tvContentTitle.text = window.app.label
        binding.tvContentPkg.text = window.app.pkg

        // State badge
        binding.tvWindowState.text = if (window.isMaximized) "Maximized" else "Running"
        binding.tvWindowState.visibility = View.VISIBLE

        // Maximize icon changes based on state
        if (window.isMaximized) {
            binding.imgMaximize.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_RESTORE, Color.WHITE))
        } else {
            binding.imgMaximize.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_MAXIMIZE, Color.WHITE))
        }

        // Focus visual
        val card = binding.windowRoot as MaterialCardView
        if (isFocused) {
            card.cardElevation = 20f
            card.strokeColor = 0x66FFFFFF
            card.strokeWidth = 2
            binding.viewFocusBorder.visibility = View.GONE
        } else {
            card.cardElevation = 8f
            card.strokeColor = 0x33FFFFFF
            card.strokeWidth = 1
            binding.viewFocusBorder.visibility = View.GONE
        }

        // Position and size
        applyWindowBounds(window)

        // Show/hide based on minimized
        visibility = if (window.isMinimized) View.GONE else View.VISIBLE
    }

    private fun applyWindowBounds(window: PcWindow) {
        val lp = layoutParams as? LayoutParams ?: LayoutParams(window.width, window.height)
        lp.width = window.width
        lp.height = window.height
        lp.leftMargin = window.x.toInt()
        lp.topMargin = window.y.toInt()
        layoutParams = lp

        // Also update card size
        val cardLp = binding.windowRoot.layoutParams
        cardLp.width = window.width
        cardLp.height = window.height
        binding.windowRoot.layoutParams = cardLp

        // For maximized, remove margins radius tweak
        val card = binding.windowRoot as MaterialCardView
        card.radius = if (window.isMaximized) 0f else 8f * resources.displayMetrics.density / 3f
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragHandling() {
        binding.windowTitlebar.setOnTouchListener { _, event ->
            val win = windowData ?: return@setOnTouchListener false
            if (win.isMaximized) return@setOnTouchListener false // Can't drag when maximized

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    windowStartX = win.x
                    windowStartY = win.y
                    isDragging = false
                    // Bring to front on touch down
                    onAction?.invoke(win, PcWindowAction.FOCUS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragStartX
                    val dy = event.rawY - dragStartY
                    if (!isDragging && (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6)) {
                        isDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (isDragging) {
                        val newX = windowStartX + dx
                        val newY = windowStartY + dy
                        // Clamp to screen
                        val parentView = parent as? ViewGroup
                        val maxX = (parentView?.width ?: 1920) - 100
                        val maxY = (parentView?.height ?: 1080) - 80
                        val clampedX = newX.coerceIn(-win.width * 0.6f, maxX.toFloat())
                        val clampedY = newY.coerceIn(0f, maxY.toFloat())

                        // Update UI immediately for smoothness
                        val lp = layoutParams as LayoutParams
                        lp.leftMargin = clampedX.toInt()
                        lp.topMargin = clampedY.toInt()
                        layoutParams = lp

                        // Update model
                        windowData?.x = clampedX
                        windowData?.y = clampedY
                        onAction?.invoke(win, PcWindowAction.MOVE)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        windowData?.let { w ->
                            PcWindowManager.updateWindowPosition(w.id, w.x, w.y)
                        }
                    }
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    // If not dragged, treat as focus
                    if (event.actionMasked == MotionEvent.ACTION_UP && kotlin.math.abs(event.rawX - dragStartX) < 6) {
                        // Just a click on titlebar -> focus
                    }
                    true
                }
                else -> false
            }
        }

        // Click on content also focuses window
        binding.windowContent.setOnClickListener {
            windowData?.let { w -> onAction?.invoke(w, PcWindowAction.FOCUS) }
        }
        binding.windowRoot.setOnClickListener {
            windowData?.let { w -> onAction?.invoke(w, PcWindowAction.FOCUS) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupResizeHandling() {
        val resizeTouchListener = View.OnTouchListener { _, event ->
            val win = windowData ?: return@OnTouchListener false
            if (win.isMaximized) return@OnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resizeStartX = event.rawX
                    resizeStartY = event.rawY
                    windowStartW = win.width
                    windowStartH = win.height
                    isResizing = false
                    onAction?.invoke(win, PcWindowAction.FOCUS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - resizeStartX
                    val dy = event.rawY - resizeStartY
                    if (!isResizing && (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6)) {
                        isResizing = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (isResizing) {
                        val newW = max(320, (windowStartW + dx).toInt())
                        val newH = max(220, (windowStartH + dy).toInt())

                        val lp = layoutParams as LayoutParams
                        lp.width = newW
                        lp.height = newH
                        layoutParams = lp

                        val cardLp = binding.windowRoot.layoutParams
                        cardLp.width = newW
                        cardLp.height = newH
                        binding.windowRoot.layoutParams = cardLp

                        windowData?.width = newW
                        windowData?.height = newH
                        onAction?.invoke(win, PcWindowAction.RESIZE)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isResizing) {
                        windowData?.let { w ->
                            PcWindowManager.updateWindowSize(w.id, w.width, w.height)
                        }
                    }
                    isResizing = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }

        binding.viewResizeHandle.setOnTouchListener(resizeTouchListener)
        binding.imgResizeIcon.setOnTouchListener(resizeTouchListener)
    }

    private fun setupClickListeners() {
        binding.btnWindowMinimize.setOnClickListener {
            windowData?.let { w -> onAction?.invoke(w, PcWindowAction.MINIMIZE) }
        }
        binding.btnWindowMaximize.setOnClickListener {
            windowData?.let { w -> onAction?.invoke(w, PcWindowAction.MAXIMIZE_RESTORE) }
        }
        binding.btnWindowClose.setOnClickListener {
            windowData?.let { w -> onAction?.invoke(w, PcWindowAction.CLOSE) }
        }
        binding.btnContentOpen.setOnClickListener {
            windowData?.let { w -> onAction?.invoke(w, PcWindowAction.LAUNCH) }
        }
        binding.btnContentInfo.setOnClickListener {
            // Open app info
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", windowData?.app?.pkg, null)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    // Custom LayoutParams that supports x,y as margins
    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return LayoutParams(context, attrs)
    }

    class LayoutParams : FrameLayout.LayoutParams {
        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs)
        constructor(w: Int, h: Int) : super(w, h)
        constructor(w: Int, h: Int, g: Int) : super(w, h, g)
    }
}
