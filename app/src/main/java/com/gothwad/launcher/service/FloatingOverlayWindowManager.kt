package com.gothwad.launcher.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.gothwad.launcher.apps.files.FileManagerView
import com.gothwad.launcher.apps.webapp.WebAppView
import com.gothwad.launcher.databinding.LayoutWindowFrameBinding
import com.gothwad.launcher.ui.AppIcons
import java.io.File

/**
 * Manages floating overlay windows (TYPE_APPLICATION_OVERLAY) displayed over other apps.
 * Allows users to open File Manager, Web Browser, etc. directly on top of Chrome, YouTube,
 * or any external app without leaving it or being thrown back to the desktop.
 */
class FloatingOverlayWindowManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val activeWindows = mutableMapOf<String, OverlayWindowHolder>()

    var onWindowMinimized: ((String) -> Unit)? = null

    private class OverlayWindowHolder(
        val id: String,
        val frameBinding: LayoutWindowFrameBinding,
        val params: WindowManager.LayoutParams,
        var isMinimized: Boolean = false,
        var isMaximized: Boolean = false,
        var normalX: Int = 0,
        var normalY: Int = 0,
        var normalW: Int = 0,
        var normalH: Int = 0
    )

    fun hasActiveWindows(): Boolean = activeWindows.isNotEmpty()

    fun getMinimizedWindowId(): String? = activeWindows.entries.firstOrNull { it.value.isMinimized }?.key

    fun openFileManager(initialDir: File? = null) {
        val id = "overlay_files"
        val existing = activeWindows[id]
        if (existing != null) {
            if (existing.isMinimized) {
                restoreWindow(existing)
            }
            return
        }

        val fileView = FileManagerView(context, initialDir)
        createFloatingWindow(
            id = id,
            title = "File Manager",
            iconDrawable = AppIcons.createDrawable(AppIcons.PATH_FOLDER, 0xFF4FA7FA.toInt()),
            contentView = fileView.getView(),
            defaultWidthRatio = 0.88f,
            defaultHeightRatio = 0.72f
        )
    }

    fun openWebApp(url: String = "https://www.google.com", title: String = "Web Browser") {
        val id = "overlay_webapp"
        val existing = activeWindows[id]
        if (existing != null) {
            if (existing.isMinimized) {
                restoreWindow(existing)
            }
            return
        }

        val webView = WebAppView(context, initialUrl = url)
        createFloatingWindow(
            id = id,
            title = title,
            iconDrawable = AppIcons.createDrawable(AppIcons.PATH_GLOBE, 0xFF60A5FA.toInt()),
            contentView = webView.binding.root,
            defaultWidthRatio = 0.88f,
            defaultHeightRatio = 0.75f
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingWindow(
        id: String,
        title: String,
        iconDrawable: Drawable,
        contentView: View,
        defaultWidthRatio: Float = 0.85f,
        defaultHeightRatio: Float = 0.70f
    ) {
        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels
        val density = displayMetrics.density

        val initialW = (screenW * defaultWidthRatio).toInt().coerceAtLeast((280 * density).toInt()).coerceAtMost((720 * density).toInt())
        val initialH = (screenH * defaultHeightRatio).toInt().coerceAtLeast((320 * density).toInt()).coerceAtMost((520 * density).toInt())
        val initialX = ((screenW - initialW) / 2).coerceAtLeast(10)
        val initialY = ((screenH - initialH) / 3).coerceAtLeast(20)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            initialW,
            initialH,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        val frameBinding = LayoutWindowFrameBinding.inflate(LayoutInflater.from(context))
        frameBinding.imgWindowIcon.setImageDrawable(iconDrawable)
        frameBinding.tvWindowTitle.text = title
        frameBinding.windowContentFrame.addView(contentView)

        // Set window control vector icons
        frameBinding.btnWinMinimize.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_MINIMIZE, android.graphics.Color.WHITE)
        )
        frameBinding.btnWinMaximize.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_MAXIMIZE, android.graphics.Color.WHITE)
        )
        frameBinding.btnWinClose.setImageDrawable(
            AppIcons.createDrawable(AppIcons.PATH_CLOSE, android.graphics.Color.WHITE)
        )

        val holder = OverlayWindowHolder(
            id = id,
            frameBinding = frameBinding,
            params = params,
            normalX = initialX,
            normalY = initialY,
            normalW = initialW,
            normalH = initialH
        )

        // Close
        frameBinding.btnWinClose.setOnClickListener {
            closeWindow(id)
        }

        // Minimize
        frameBinding.btnWinMinimize.setOnClickListener {
            minimizeWindow(holder)
        }

        // Maximize / Restore
        frameBinding.btnWinMaximize.setOnClickListener {
            toggleMaximize(holder)
        }

        // Window dragging via title bar
        var initialTouchX = 0f
        var initialTouchY = 0f
        var initialParamX = 0
        var initialParamY = 0

        frameBinding.windowTitleBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialParamX = params.x
                    initialParamY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!holder.isMaximized) {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        params.x = (initialParamX + dx).coerceIn(0, screenW - 80)
                        params.y = (initialParamY + dy).coerceIn(0, screenH - 80)
                        holder.normalX = params.x
                        holder.normalY = params.y
                        runCatching { windowManager.updateViewLayout(frameBinding.root, params) }
                    }
                    true
                }
                else -> false
            }
        }

        frameBinding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                releaseWindowFocus(holder)
            } else if (event.action == MotionEvent.ACTION_DOWN) {
                requestWindowFocus(holder)
            }
            false
        }

        runCatching {
            windowManager.addView(frameBinding.root, params)
            activeWindows[id] = holder
        }
    }

    private fun requestWindowFocus(holder: OverlayWindowHolder) {
        if ((holder.params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
            holder.params.flags = holder.params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            runCatching { windowManager.updateViewLayout(holder.frameBinding.root, holder.params) }
        }
    }

    private fun releaseWindowFocus(holder: OverlayWindowHolder) {
        if ((holder.params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
            holder.params.flags = holder.params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            runCatching { windowManager.updateViewLayout(holder.frameBinding.root, holder.params) }
        }
    }

    fun restoreWindow(id: String) {
        val holder = activeWindows[id] ?: return
        restoreWindow(holder)
    }

    private fun minimizeWindow(holder: OverlayWindowHolder) {
        holder.isMinimized = true
        holder.frameBinding.root.visibility = View.GONE
        onWindowMinimized?.invoke(holder.id)
    }

    private fun restoreWindow(holder: OverlayWindowHolder) {
        holder.isMinimized = false
        holder.frameBinding.root.visibility = View.VISIBLE
        runCatching { windowManager.updateViewLayout(holder.frameBinding.root, holder.params) }
    }

    private fun toggleMaximize(holder: OverlayWindowHolder) {
        val displayMetrics = context.resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels
        val density = displayMetrics.density

        val statusBarHeight = (28 * density).toInt()
        val taskbarHeight = (48 * density).toInt()

        if (!holder.isMaximized) {
            // Maximize strictly within usable screen bounds, leaving taskbar and status bar visible
            holder.isMaximized = true
            holder.params.x = 0
            holder.params.y = statusBarHeight
            holder.params.width = screenW
            holder.params.height = (screenH - statusBarHeight - taskbarHeight).coerceAtLeast(300)
            holder.frameBinding.btnWinMaximize.setImageDrawable(
                AppIcons.createDrawable(AppIcons.PATH_RESTORE, android.graphics.Color.WHITE)
            )
        } else {
            // Restore
            holder.isMaximized = false
            holder.params.x = holder.normalX
            holder.params.y = holder.normalY
            holder.params.width = holder.normalW
            holder.params.height = holder.normalH
            holder.frameBinding.btnWinMaximize.setImageDrawable(
                AppIcons.createDrawable(AppIcons.PATH_MAXIMIZE, android.graphics.Color.WHITE)
            )
        }
        runCatching { windowManager.updateViewLayout(holder.frameBinding.root, holder.params) }
    }

    fun closeWindow(id: String) {
        val holder = activeWindows.remove(id) ?: return
        runCatching {
            windowManager.removeView(holder.frameBinding.root)
        }
    }

    fun closeAll() {
        for (holder in activeWindows.values) {
            runCatching {
                windowManager.removeView(holder.frameBinding.root)
            }
        }
        activeWindows.clear()
    }
}
