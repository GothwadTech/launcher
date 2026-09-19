package com.gothwad.launcher.ui.pc

import com.gothwad.launcher.data.AppEntry

/**
 * Represents a windowed app in PC mode - similar to Windows/Linux windowing.
 * Each window has position, size, state (minimized/maximized/normal), z-order.
 */
data class PcWindow(
    val id: String, // unique id = pkg + timestamp
    val app: AppEntry,
    var x: Float = 100f,
    var y: Float = 100f,
    var width: Int = 480,
    var height: Int = 320,
    var isMinimized: Boolean = false,
    var isMaximized: Boolean = false,
    var zIndex: Int = 0,
    // Store pre-maximize bounds for restore
    var restoreX: Float = 100f,
    var restoreY: Float = 100f,
    var restoreWidth: Int = 480,
    var restoreHeight: Int = 320,
    var isRunning: Boolean = true
)

enum class PcWindowAction {
    CLOSE, MINIMIZE, MAXIMIZE_RESTORE, FOCUS, MOVE, RESIZE, LAUNCH
}
