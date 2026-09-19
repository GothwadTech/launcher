package com.gothwad.launcher.ui.pc

import com.gothwad.launcher.data.AppEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Manages all PC windows - similar to a Window Manager in Windows/Linux.
 * Handles creation, z-order, minimize/maximize/close.
 * 
 * Architecture:
 * - Each app launch creates a PcWindow
 * - Windows are rendered as cards with titlebar (close/min/max)
 * - Minimized windows go to taskbar as running apps
 * - Maximized fills desktop area above taskbar
 * - Freeform launch attempted on supported devices
 */
object PcWindowManager {

    private val _windows = MutableStateFlow<List<PcWindow>>(emptyList())
    val windows: StateFlow<List<PcWindow>> = _windows

    private var zCounter = 100
    private var windowOffset = 0

    fun getWindows(): List<PcWindow> = _windows.value

    fun getRunningPackages(): Set<String> = _windows.value.map { it.app.pkg }.toSet()

    fun getMinimizedWindows(): List<PcWindow> = _windows.value.filter { it.isMinimized }

    fun getVisibleWindows(): List<PcWindow> = _windows.value.filter { !it.isMinimized }.sortedBy { it.zIndex }

    /**
     * Create or focus existing window for app.
     * If app already has a window, bring it to front (restore if minimized).
     */
    fun openWindow(app: AppEntry, screenWidth: Int, screenHeight: Int, taskbarHeight: Int): PcWindow {
        val existing = _windows.value.find { it.app.pkg == app.pkg }
        if (existing != null) {
            // Bring to front and restore if minimized
            existing.isMinimized = false
            existing.zIndex = ++zCounter
            // If maximized, keep maximized, else ensure visible
            if (!existing.isMaximized) {
                // Slight cascade if overlapping too much
                ensureVisible(existing, screenWidth, screenHeight, taskbarHeight)
            }
            _windows.value = _windows.value.toList() // trigger flow
            return existing
        }

        // Calculate default size - 55% of screen, but at least 360x240
        val defaultW = max(380, (screenWidth * 0.52f).toInt())
        val defaultH = max(280, ((screenHeight - taskbarHeight) * 0.58f).toInt())

        // Cascade position - each new window offset by 32dp
        windowOffset = (windowOffset + 1) % 12
        val cascadeOffset = windowOffset * 32f
        val startX = min(120f + cascadeOffset, (screenWidth - defaultW - 20).toFloat().coerceAtLeast(10f))
        val startY = min(80f + cascadeOffset, (screenHeight - taskbarHeight - defaultH - 20).toFloat().coerceAtLeast(10f))

        val newWindow = PcWindow(
            id = "${app.pkg}_${System.currentTimeMillis()}",
            app = app,
            x = startX,
            y = startY,
            width = defaultW,
            height = defaultH,
            zIndex = ++zCounter,
            restoreX = startX,
            restoreY = startY,
            restoreWidth = defaultW,
            restoreHeight = defaultH
        )

        _windows.value = _windows.value + newWindow
        return newWindow
    }

    fun closeWindow(windowId: String) {
        _windows.value = _windows.value.filter { it.id != windowId }
    }

    fun closeByPackage(pkg: String) {
        _windows.value = _windows.value.filter { it.app.pkg != pkg }
    }

    fun minimizeWindow(windowId: String) {
        val list = _windows.value.toMutableList()
        val idx = list.indexOfFirst { it.id == windowId }
        if (idx != -1) {
            list[idx].isMinimized = true
            _windows.value = list.toList()
        }
    }

    fun restoreWindow(windowId: String) {
        val list = _windows.value.toMutableList()
        val idx = list.indexOfFirst { it.id == windowId }
        if (idx != -1) {
            list[idx].isMinimized = false
            list[idx].zIndex = ++zCounter
            _windows.value = list.toList()
        }
    }

    fun toggleMaximize(windowId: String, screenWidth: Int, screenHeight: Int, taskbarHeight: Int) {
        val list = _windows.value.toMutableList()
        val idx = list.indexOfFirst { it.id == windowId }
        if (idx == -1) return
        val win = list[idx]
        if (win.isMaximized) {
            // Restore
            win.x = win.restoreX
            win.y = win.restoreY
            win.width = win.restoreWidth
            win.height = win.restoreHeight
            win.isMaximized = false
        } else {
            // Save current as restore
            win.restoreX = win.x
            win.restoreY = win.y
            win.restoreWidth = win.width
            win.restoreHeight = win.height
            // Maximize to fill desktop (above taskbar)
            win.x = 0f
            win.y = 0f
            win.width = screenWidth
            win.height = screenHeight - taskbarHeight
            win.isMaximized = true
        }
        win.zIndex = ++zCounter
        _windows.value = list.toList()
    }

    fun focusWindow(windowId: String) {
        val list = _windows.value.toMutableList()
        val idx = list.indexOfFirst { it.id == windowId }
        if (idx != -1) {
            list[idx].zIndex = ++zCounter
            list[idx].isMinimized = false
            _windows.value = list.toList()
        }
    }

    fun updateWindowPosition(windowId: String, x: Float, y: Float) {
        val list = _windows.value.toMutableList()
        val idx = list.indexOfFirst { it.id == windowId }
        if (idx != -1) {
            if (!list[idx].isMaximized) {
                list[idx].x = x
                list[idx].y = y
            }
            _windows.value = list.toList()
        }
    }

    fun updateWindowSize(windowId: String, width: Int, height: Int) {
        val list = _windows.value.toMutableList()
        val idx = list.indexOfFirst { it.id == windowId }
        if (idx != -1) {
            if (!list[idx].isMaximized) {
                list[idx].width = max(300, width)
                list[idx].height = max(200, height)
            }
            _windows.value = list.toList()
        }
    }

    fun clearAll() {
        _windows.value = emptyList()
        zCounter = 100
        windowOffset = 0
    }

    private fun ensureVisible(win: PcWindow, screenWidth: Int, screenHeight: Int, taskbarHeight: Int) {
        if (win.x < -win.width * 0.7f) win.x = 20f
        if (win.y < -20f) win.y = 20f
        if (win.x > screenWidth - 60) win.x = (screenWidth - win.width - 20).toFloat().coerceAtLeast(10f)
        if (win.y > screenHeight - taskbarHeight - 60) win.y = (screenHeight - taskbarHeight - win.height - 20).toFloat().coerceAtLeast(10f)
    }
}
