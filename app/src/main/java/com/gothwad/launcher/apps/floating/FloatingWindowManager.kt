package com.gothwad.launcher.apps.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.databinding.ItemTaskbarWindowChipBinding
import com.gothwad.launcher.databinding.LayoutWindowFrameBinding
import com.gothwad.launcher.ui.AppIcons

/**
 * Native Android View Window Model.
 * Zero Jetpack Compose overhead, 100% Native Views & ViewBinding.
 */
data class FloatingWindow(
    val id: String,
    val title: String,
    val iconDrawable: Drawable,
    val contentView: View,
    var isMinimized: Boolean = false,
    var isMaximized: Boolean = false,
    var x: Int = 80,
    var y: Int = 60,
    var width: Int = 720,
    var height: Int = 500,
    var prevX: Int = 80,
    var prevY: Int = 60,
    var prevWidth: Int = 720,
    var prevHeight: Int = 500,
    var frameBinding: LayoutWindowFrameBinding? = null,
    val onClose: (() -> Unit)? = null
)

/**
 * Taskbar Window Chip Adapter for active minimized/running windows.
 */
class TaskbarWindowAdapter(
    private val onChipClick: (FloatingWindow) -> Unit,
    private val onChipClose: (FloatingWindow) -> Unit
) : RecyclerView.Adapter<TaskbarWindowAdapter.ChipViewHolder>() {

    private val windows = mutableListOf<FloatingWindow>()
    private var activeWindowId: String? = null

    fun setWindows(list: List<FloatingWindow>, activeId: String?) {
        windows.clear()
        windows.addAll(list)
        activeWindowId = activeId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipViewHolder {
        val binding = ItemTaskbarWindowChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChipViewHolder, position: Int) {
        val window = windows[position]
        holder.bind(window, window.id == activeWindowId && !window.isMinimized)
    }

    override fun getItemCount(): Int = windows.size

    inner class ChipViewHolder(private val binding: ItemTaskbarWindowChipBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(window: FloatingWindow, isActive: Boolean) {
            binding.imgChipIcon.setImageDrawable(window.iconDrawable)
            binding.tvChipTitle.text = window.title
            binding.root.isSelected = isActive

            binding.root.setOnClickListener {
                onChipClick(window)
            }

            binding.btnChipClose.setOnClickListener {
                onChipClose(window)
            }
        }
    }
}

/**
 * Floating Window Manager:
 * Handles window lifecycle (open, minimize, maximize, restore, close),
 * titlebar dragging with boundaries, and taskbar synchronization.
 */
class FloatingWindowManager(
    private val context: Context,
    private val windowContainer: FrameLayout,
    private val taskbarChipsRecycler: RecyclerView? = null
) {
    private val activeWindows = mutableListOf<FloatingWindow>()
    private var focusedWindowId: String? = null
    private val chipAdapter: TaskbarWindowAdapter
    var onTaskbarChanged: ((hasWindows: Boolean) -> Unit)? = null

    init {
        chipAdapter = TaskbarWindowAdapter(
            onChipClick = { window ->
                if (window.isMinimized) {
                    restoreWindow(window.id)
                } else if (focusedWindowId == window.id) {
                    minimizeWindow(window.id)
                } else {
                    bringToFront(window.id)
                }
            },
            onChipClose = { window ->
                closeWindow(window.id)
            }
        )
        taskbarChipsRecycler?.adapter = chipAdapter
    }

    fun hasOpenWindows(): Boolean = activeWindows.isNotEmpty()

    fun getActiveWindows(): List<FloatingWindow> = activeWindows.toList()

    @SuppressLint("ClickableViewAccessibility")
    fun openWindow(
        id: String,
        title: String,
        iconDrawable: Drawable,
        contentView: View,
        defaultWidthDp: Int = 680,
        defaultHeightDp: Int = 440,
        onClose: (() -> Unit)? = null
    ): FloatingWindow {
        // If window already open, bring to front / unminimize
        val existing = activeWindows.find { it.id == id }
        if (existing != null) {
            if (existing.isMinimized) {
                restoreWindow(id)
            } else {
                bringToFront(id)
            }
            return existing
        }

        val density = context.resources.displayMetrics.density
        val displayWidth = context.resources.displayMetrics.widthPixels
        val displayHeight = context.resources.displayMetrics.heightPixels

        val initialW = (defaultWidthDp * density).toInt().coerceAtMost((displayWidth * 0.92f).toInt())
        val initialH = (defaultHeightDp * density).toInt().coerceAtMost((displayHeight * 0.85f).toInt())

        // Cascade position based on open window count
        val offsetStep = (28 * density).toInt()
        val cascadeIndex = (activeWindows.size % 6)
        val initialX = ((displayWidth - initialW) / 2) + (cascadeIndex * offsetStep - 40)
        val initialY = ((displayHeight - initialH) / 2) + (cascadeIndex * offsetStep - 60)

        val clampedX = initialX.coerceIn(10, (displayWidth - initialW - 10).coerceAtLeast(10))
        val clampedY = initialY.coerceIn(10, (displayHeight - initialH - 10).coerceAtLeast(10))

        val frameBinding = LayoutWindowFrameBinding.inflate(
            LayoutInflater.from(context),
            windowContainer,
            false
        )

        val window = FloatingWindow(
            id = id,
            title = title,
            iconDrawable = iconDrawable,
            contentView = contentView,
            isMinimized = false,
            isMaximized = false,
            x = clampedX,
            y = clampedY,
            width = initialW,
            height = initialH,
            prevX = clampedX,
            prevY = clampedY,
            prevWidth = initialW,
            prevHeight = initialH,
            frameBinding = frameBinding,
            onClose = onClose
        )

        // Setup Window Views
        frameBinding.imgWindowIcon.setImageDrawable(iconDrawable)
        frameBinding.tvWindowTitle.text = title

        // Attach content view to frame
        if (contentView.parent != null) {
            (contentView.parent as? ViewGroup)?.removeView(contentView)
        }
        frameBinding.windowContentFrame.removeAllViews()
        frameBinding.windowContentFrame.addView(
            contentView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        // Controls
        frameBinding.btnWinMinimize.setOnClickListener {
            minimizeWindow(id)
        }

        frameBinding.btnWinMaximize.setOnClickListener {
            toggleMaximize(id)
        }

        frameBinding.btnWinClose.setOnClickListener {
            closeWindow(id)
        }

        // Window Focus Click
        frameBinding.windowRoot.setOnClickListener {
            bringToFront(id)
        }

        // Draggable Title Bar
        setupDragListener(frameBinding.windowTitleBar, window)

        // Layout Params in Window Container
        val lp = FrameLayout.LayoutParams(initialW, initialH).apply {
            leftMargin = clampedX
            topMargin = clampedY
        }
        windowContainer.addView(frameBinding.root, lp)

        activeWindows.add(window)
        bringToFront(id)
        updateTaskbar()

        return window
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener(dragHandle: View, window: FloatingWindow) {
        var dX = 0f
        var dY = 0f

        dragHandle.setOnTouchListener { _, event ->
            val frame = window.frameBinding?.root ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    bringToFront(window.id)
                    dX = frame.x - event.rawX
                    dY = frame.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (window.isMaximized) {
                        // Dragging a maximized window restores it to normal size anchored to cursor
                        toggleMaximize(window.id)
                    }
                    val containerW = windowContainer.width
                    val containerH = windowContainer.height

                    val newX = (event.rawX + dX).coerceIn(0f, (containerW - 80).toFloat())
                    val newY = (event.rawY + dY).coerceIn(0f, (containerH - 40).toFloat())

                    frame.x = newX
                    frame.y = newY
                    window.x = newX.toInt()
                    window.y = newY.toInt()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    true
                }
                else -> false
            }
        }
    }

    fun minimizeWindow(id: String) {
        val window = activeWindows.find { it.id == id } ?: return
        window.isMinimized = true
        window.frameBinding?.root?.visibility = View.GONE

        // Give focus to another visible window if any
        if (focusedWindowId == id) {
            val next = activeWindows.lastOrNull { !it.isMinimized }
            focusedWindowId = next?.id
            next?.frameBinding?.root?.let {
                windowContainer.bringChildToFront(it)
            }
        }
        updateTaskbar()
    }

    fun restoreWindow(id: String) {
        val window = activeWindows.find { it.id == id } ?: return
        window.isMinimized = false
        window.frameBinding?.root?.visibility = View.VISIBLE
        bringToFront(id)
        updateTaskbar()
    }

    fun toggleMaximize(id: String) {
        val window = activeWindows.find { it.id == id } ?: return
        val frame = window.frameBinding?.root ?: return

        if (!window.isMaximized) {
            // Save restore dimensions
            window.prevX = frame.x.toInt()
            window.prevY = frame.y.toInt()
            window.prevWidth = frame.width
            window.prevHeight = frame.height

            // Maximize to container boundaries
            val containerW = windowContainer.width
            val containerH = windowContainer.height

            val lp = frame.layoutParams as FrameLayout.LayoutParams
            lp.width = containerW
            lp.height = containerH
            lp.leftMargin = 0
            lp.topMargin = 0
            frame.layoutParams = lp
            frame.x = 0f
            frame.y = 0f

            window.isMaximized = true
            window.frameBinding?.btnWinMaximize?.text = "🗗"
        } else {
            // Restore
            val lp = frame.layoutParams as FrameLayout.LayoutParams
            lp.width = window.prevWidth
            lp.height = window.prevHeight
            lp.leftMargin = window.prevX
            lp.topMargin = window.prevY
            frame.layoutParams = lp
            frame.x = window.prevX.toFloat()
            frame.y = window.prevY.toFloat()

            window.isMaximized = false
            window.frameBinding?.btnWinMaximize?.text = "🗖"
        }
    }

    fun bringToFront(id: String) {
        val window = activeWindows.find { it.id == id } ?: return
        if (window.isMinimized) {
            restoreWindow(id)
            return
        }
        focusedWindowId = id
        window.frameBinding?.root?.let {
            windowContainer.bringChildToFront(it)
        }
        updateTaskbar()
    }

    fun closeWindow(id: String) {
        val window = activeWindows.find { it.id == id } ?: return
        window.frameBinding?.root?.let {
            windowContainer.removeView(it)
        }
        window.onClose?.invoke()
        activeWindows.remove(window)

        if (focusedWindowId == id) {
            val next = activeWindows.lastOrNull { !it.isMinimized }
            focusedWindowId = next?.id
            next?.frameBinding?.root?.let {
                windowContainer.bringChildToFront(it)
            }
        }
        updateTaskbar()
    }

    fun closeAll() {
        for (win in activeWindows.toList()) {
            closeWindow(win.id)
        }
    }

    private fun updateTaskbar() {
        chipAdapter.setWindows(activeWindows, focusedWindowId)
        val hasWindows = activeWindows.isNotEmpty()
        taskbarChipsRecycler?.visibility = if (hasWindows) View.VISIBLE else View.GONE
        onTaskbarChanged?.invoke(hasWindows)
    }
}
