package com.gothwad.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object Actions {

    fun toast(context: Context, msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun launchApp(context: Context, pkg: String) {
        launchAppInternal(context, pkg, tryFreeform = false)
    }

    /**
     * Launch app in windowed mode if possible.
     * On Android 12L+ / Samsung DeX / devices with freeform enabled, this will open as real floating window.
     * Otherwise falls back to normal fullscreen launch but keeps window tracking for our simulated windowing.
     */
    fun launchAppInWindowedMode(context: Context, pkg: String): Boolean {
        return launchAppInternal(context, pkg, tryFreeform = true)
    }

    private fun launchAppInternal(context: Context, pkg: String, tryFreeform: Boolean, taskbarHeightPx: Int = 0): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            return runCatching {
                if (context !is android.app.Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                // Try freeform windowing on supported devices
                if (tryFreeform) {
                    try {
                        // Android 7+ freeform windowing mode
                        val options = android.app.ActivityOptions.makeBasic()
                        // WINDOWING_MODE_FREEFORM = 5
                        options.javaClass.getMethod("setLaunchWindowingMode", Int::class.java)
                            .invoke(options, 5)
                        // Set bounds excluding taskbar area so app doesn't hide behind taskbar
                        // This is the correct fix for "taskbar hides app content"
                        val displayMetrics = context.resources.displayMetrics
                        val tbHeight = if (taskbarHeightPx > 0) taskbarHeightPx else (44 * displayMetrics.density).toInt()
                        val width = (displayMetrics.widthPixels * 0.75f).toInt()
                        val height = (displayMetrics.heightPixels - tbHeight - (20 * displayMetrics.density).toInt())
                        val left = (displayMetrics.widthPixels - width) / 2
                        val top = (20 * displayMetrics.density).toInt()
                        try {
                            val rect = android.graphics.Rect(left, top, left + width, top + height)
                            options.javaClass.getMethod("setLaunchBounds", android.graphics.Rect::class.java)
                                .invoke(options, rect)
                        } catch (_: Exception) {}

                        if (context is android.app.Activity) {
                            context.startActivity(intent, options.toBundle())
                        } else {
                            context.startActivity(intent, options.toBundle())
                        }
                    } catch (e: Exception) {
                        // Fallback to normal launch if freeform not supported
                        context.startActivity(intent)
                    }
                } else {
                    context.startActivity(intent)
                }
                com.gothwad.launcher.data.AppLaunchTracker.onAppLaunched(pkg)
                true
            }.onFailure { 
                toast(context, context.getString(R.string.toast_cannot_open)) 
                false
            }.getOrDefault(false)
        } else {
            toast(context, context.getString(R.string.toast_no_launchable))
            return false
        }
    }

    /**
     * Launch with taskbar-aware bounds - app opens above taskbar, not behind it
     */
    fun launchAppWithTaskbarBounds(context: Context, pkg: String, taskbarHeightPx: Int): Boolean {
        return launchAppInternal(context, pkg, tryFreeform = true, taskbarHeightPx = taskbarHeightPx)
    }

    fun openAppInfo(context: Context, pkg: String) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$pkg")
                )
            )
        }
    }

    fun uninstall(context: Context, pkg: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
        }
    }

    /** Best-effort: clears the app from cached memory. True force-stop is in App info. */
    fun close(context: Context, pkg: String) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        runCatching { am.killBackgroundProcesses(pkg) }
    }

    fun openSystemSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    fun openNetworkSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            .onFailure { openSystemSettings(context) }
    }

    fun openBluetoothSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            .onFailure { openSystemSettings(context) }
    }

    fun openAccessibilitySettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { openSystemSettings(context) }
    }

    fun openNotificationAccessSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        )
        for (intent in intents) {
            val success = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.isSuccess
            if (success) return
        }
        openSystemSettings(context)
    }

    /** AerialViews screensaver (github.com/theothernt/AerialViews) */
    const val AERIAL_PKG = "com.neilturner.aerialviews"

    fun isInstalled(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    fun openAppStore(context: Context, pkg: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        }.onFailure {
            toast(context, context.getString(R.string.toast_no_store))
        }
    }

}
