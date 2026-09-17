package com.gothwad.launcher.data

import android.content.Context
import android.view.KeyEvent
import com.gothwad.launcher.Actions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manager and helper for Remote Button Mapping (Phase 5).
 * Holds in-memory caches, capture-mode listeners, friendly key labels, and default STB mappings.
 */
object ButtonMappingManager {

    // Capture mode flag: when true, the very next key event received by LauncherAccessibilityService
    // is captured and emitted to keyCaptureFlow instead of standard handling.
    private val isListeningForCapture = AtomicBoolean(false)

    private val _keyCaptureFlow = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val keyCaptureFlow = _keyCaptureFlow.asSharedFlow()

    fun startListening() {
        isListeningForCapture.set(true)
    }

    fun stopListening() {
        isListeningForCapture.set(false)
    }

    fun isListening(): Boolean = isListeningForCapture.get()

    /**
     * Called by LauncherAccessibilityService when a key is pressed.
     * Returns true if the key was consumed by capture mode.
     */
    fun onKeyCaptured(keyCode: Int): Boolean {
        if (isListeningForCapture.getAndSet(false)) {
            _keyCaptureFlow.tryEmit(keyCode)
            return true
        }
        return false
    }

    /**
     * Checks if a key is reserved for system / launcher navigation and cannot be remapped.
     */
    fun isReservedKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER -> true
            else -> false
        }
    }

    /**
     * Returns a human-friendly name for well-known TV/STB remote hotkeys.
     */
    fun getFriendlyKeyName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_PROG_RED -> "Red Button"
            KeyEvent.KEYCODE_PROG_GREEN -> "Green Button"
            KeyEvent.KEYCODE_PROG_YELLOW -> "Yellow Button"
            KeyEvent.KEYCODE_PROG_BLUE -> "Blue Button"
            KeyEvent.KEYCODE_GUIDE -> "Guide / EPG"
            KeyEvent.KEYCODE_CAPTIONS -> "Captions / Subtitles"
            KeyEvent.KEYCODE_TV -> "TV Button"
            KeyEvent.KEYCODE_TV_INPUT -> "TV Input"
            KeyEvent.KEYCODE_AVR_INPUT -> "AV Input"
            KeyEvent.KEYCODE_INFO -> "Info Button"
            KeyEvent.KEYCODE_BOOKMARK -> "Bookmark"
            KeyEvent.KEYCODE_WINDOW -> "Window / Split"
            KeyEvent.KEYCODE_LAST_CHANNEL -> "Last Channel"
            KeyEvent.KEYCODE_CHANNEL_UP -> "Channel Up"
            KeyEvent.KEYCODE_CHANNEL_DOWN -> "Channel Down"
            KeyEvent.KEYCODE_MEDIA_PLAY -> "Media Play"
            KeyEvent.KEYCODE_MEDIA_PAUSE -> "Media Pause"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Media Play/Pause"
            KeyEvent.KEYCODE_MEDIA_STOP -> "Media Stop"
            KeyEvent.KEYCODE_MEDIA_NEXT -> "Media Next"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Media Previous"
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "Fast Forward"
            KeyEvent.KEYCODE_MEDIA_REWIND -> "Rewind"
            KeyEvent.KEYCODE_SEARCH -> "Search"
            KeyEvent.KEYCODE_HELP -> "Help"
            KeyEvent.KEYCODE_EXPLORER -> "Browser"
            KeyEvent.KEYCODE_APP_SWITCH -> "App Switcher"
            // Vendor/Streaming app dedicated keycodes (if supported by Android API level)
            165 -> "Info"
            166 -> "Channel Up"
            167 -> "Channel Down"
            170 -> "TV"
            else -> {
                val label = KeyEvent.keyCodeToString(keyCode)
                if (label.startsWith("KEYCODE_")) {
                    label.removePrefix("KEYCODE_").replace('_', ' ').lowercase()
                        .split(' ')
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                } else {
                    "Key $keyCode"
                }
            }
        }
    }

    /**
     * Seeds default mappings directly into ConfigStore if not yet applied.
     */
    suspend fun seedDefaultMappings(store: ConfigStore, installedPackages: Set<String>) {
        store.update { cfg ->
            if (!cfg.buttonMapDefaultsApplied) {
                val defaults = mutableMapOf<Int, String>()
                val candidates = listOf(
                    KeyEvent.KEYCODE_PROG_RED to listOf(
                        "com.google.android.youtube.tv",
                        "com.google.android.youtube",
                        "org.schabi.newpipe"
                    ),
                    KeyEvent.KEYCODE_PROG_GREEN to listOf(
                        "com.netflix.ninja",
                        "com.netflix.mediaclient"
                    ),
                    KeyEvent.KEYCODE_PROG_YELLOW to listOf(
                        "com.amazon.amazonvideo.livingroom",
                        "com.amazon.avod.thirdpartyclient"
                    ),
                    KeyEvent.KEYCODE_PROG_BLUE to listOf(
                        "in.startv.hotstar",
                        "com.disney.disneyplus"
                    ),
                    KeyEvent.KEYCODE_GUIDE to listOf(
                        "com.jio.jiotv",
                        "com.airtel.tv",
                        "com.google.android.tv"
                    ),
                    KeyEvent.KEYCODE_TV to listOf(
                        "com.jio.jiotv",
                        "com.airtel.tv"
                    )
                )
                for ((keyCode, pkgList) in candidates) {
                    val matching = pkgList.firstOrNull { it in installedPackages }
                    if (matching != null) {
                        defaults[keyCode] = matching
                    }
                }
                val merged = defaults + cfg.buttonMap
                cfg.copy(buttonMap = merged, buttonMapDefaultsApplied = true)
            } else {
                cfg
            }
        }
    }

    /**
     * Alias for getFriendlyKeyName.
     */
    fun getKeyName(keyCode: Int): String = getFriendlyKeyName(keyCode)
}
