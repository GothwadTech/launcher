package com.gothwad.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import com.gothwad.launcher.data.AppLaunchTracker

/**
 * Custom Application class for Gothwad Launcher.
 * Acts as an application-level memory guardian on low-RAM (2GB) STB/TV devices,
 * automatically trimming tracked background processes under system memory pressure.
 */
class GothwadApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "GothwadApplication initialized")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w(TAG, "onTrimMemory: TRIM_MEMORY_RUNNING_CRITICAL (level=$level), triggering critical trim")
                AppLaunchTracker.performMemoryTrim(this, isCritical = true)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.i(TAG, "onTrimMemory: TRIM_MEMORY_RUNNING_LOW (level=$level), triggering background trim")
                AppLaunchTracker.performMemoryTrim(this, isCritical = false)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // Background process cleanup when launcher itself is backgrounded
                Log.d(TAG, "onTrimMemory: TRIM_MEMORY_COMPLETE (level=$level)")
                AppLaunchTracker.performMemoryTrim(this, isCritical = false)
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // Fallback for OEM builds (e.g. older Android versions or TV forks) that do not reliably deliver onTrimMemory
        Log.w(TAG, "onLowMemory triggered! Running critical memory trim")
        AppLaunchTracker.performMemoryTrim(this, isCritical = true)
    }

    companion object {
        private const val TAG = "GothwadApplication"
    }
}
