package com.gothwad.launcher.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.gothwad.launcher.MainActivity
import com.gothwad.launcher.R
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.MODE_PC
import com.gothwad.launcher.data.MODE_TV
import com.gothwad.launcher.databinding.LayoutFloatingTaskbarOverlayBinding
import com.gothwad.launcher.databinding.LayoutFloatingTaskbarTriggerBinding
import com.gothwad.launcher.ui.AppIcons
import com.gothwad.launcher.ui.view.SmoothCornerDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * System Alert Window Service providing the floating launcher icon across other apps in PC mode.
 * - Shows an Android squircle floating bubble that can be dragged anywhere on screen.
 * - Tapping the bubble opens a quick assist menu (or directly restores a minimized window).
 * - Opens File Manager and Web Browser directly on top of Chrome or any active app in a floating window.
 * - Single unified taskbar lives on the desktop; no redundant floating taskbar.
 */
class FloatingTaskbarService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager

    // Views & Bindings
    private var triggerBinding: LayoutFloatingTaskbarTriggerBinding? = null
    private var triggerView: View? = null
    private var triggerParams: WindowManager.LayoutParams? = null

    private var overlayBinding: LayoutFloatingTaskbarOverlayBinding? = null
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    // State
    private var isMenuVisible = false
    private var isTriggerAttached = false
    private var isOverlayAttached = false

    private lateinit var overlayWindowManager: FloatingOverlayWindowManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayWindowManager = FloatingOverlayWindowManager(this)

        setupTriggerView()
        setupOverlayView()

        // Observe config
        serviceScope.launch {
            ConfigStore(applicationContext).flow.collectLatest { config ->
                handleConfigChange(config)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_MENU -> toggleMenu()
            ACTION_STOP_SERVICE -> stopSelf()
        }
        return START_STICKY
    }

    private fun handleConfigChange(config: LauncherConfig) {
        if (!canDrawOverlays(this) || config.launcherMode != MODE_PC || !config.pcOverlayTaskbarEnabled) {
            hideTrigger()
            hideOverlay()
            return
        }

        showTrigger()
    }

    // =========================================================================
    // 1. FLOATING BUBBLE (Squircle Trigger)
    // =========================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTriggerView() {
        val binding = LayoutFloatingTaskbarTriggerBinding.inflate(LayoutInflater.from(this))
        triggerBinding = binding
        triggerView = binding.root

        val density = resources.displayMetrics.density
        val smoothBubbleDrawable = SmoothCornerDrawable(
            cornerRadiusPx = 14f * density,
            fillColor = 0xF0181B22.toInt(),
            strokeColor = 0x664FA7FA.toInt(),
            strokeWidthPx = 1.5f * density,
            smoothing = 0.6f
        )
        binding.btnFloatingBubble.background = smoothBubbleDrawable

        val appIconDrawable = runCatching {
            packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_launcher)

        binding.imgFloatingIcon.setImageDrawable(appIconDrawable)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val displayMetrics = resources.displayMetrics
        val screenW = displayMetrics.widthPixels
        val screenH = displayMetrics.heightPixels
        val initialX = (screenW - (56 * density)).toInt()
        val initialY = (screenH - (56 * density)).toInt()

        triggerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        var initialTouchX = 0f
        var initialTouchY = 0f
        var initialParamX = 0
        var initialParamY = 0
        var isMoving = false

        binding.btnFloatingBubble.setOnTouchListener { _, event ->
            val params = triggerParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialParamX = params.x
                    initialParamY = params.y
                    isMoving = false
                    binding.btnFloatingBubble.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        isMoving = true
                        params.x = (initialParamX + dx).coerceIn(0, screenW - (44 * density).toInt())
                        params.y = (initialParamY + dy).coerceIn(0, screenH - (44 * density).toInt())
                        runCatching { windowManager.updateViewLayout(triggerView, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    binding.btnFloatingBubble.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (!isMoving) {
                        // Tapped: If a window was minimized, restore it; otherwise toggle quick assist menu
                        val minId = overlayWindowManager.getMinimizedWindowId()
                        if (minId != null) {
                            overlayWindowManager.restoreWindow(minId)
                        } else {
                            toggleMenu()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showTrigger() {
        if (isTriggerAttached || triggerView == null || triggerParams == null) return
        runCatching {
            windowManager.addView(triggerView, triggerParams)
            isTriggerAttached = true
        }.onFailure { e ->
            android.util.Log.e("FloatingTaskbarService", "Failed to add triggerView: ${e.message}")
        }
    }

    private fun hideTrigger() {
        if (!isTriggerAttached || triggerView == null) return
        runCatching {
            windowManager.removeView(triggerView)
            isTriggerAttached = false
        }
    }

    // =========================================================================
    // 2. QUICK ASSIST MENU
    // =========================================================================

    private fun setupOverlayView() {
        val binding = LayoutFloatingTaskbarOverlayBinding.inflate(LayoutInflater.from(this))
        overlayBinding = binding
        overlayView = binding.root

        val appIconDrawable = runCatching {
            packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_launcher)

        binding.imgPanelHeaderIcon.setImageDrawable(appIconDrawable)
        binding.btnCloseMenu.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))
        binding.imgIconHome.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HOME, 0xFF60A5FA.toInt()))
        binding.imgIconFileMgr.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_FOLDER, 0xFF4FA7FA.toInt()))
        binding.imgIconWebApp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GLOBE, 0xFF60A5FA.toInt()))
        binding.imgIconSearch.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, Color.WHITE))
        binding.imgIconNotifs.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, Color.WHITE))
        binding.imgIconVolume.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_VOLUME, Color.WHITE))
        binding.imgIconTv.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_TV, 0xFF60A5FA.toInt()))
        binding.imgIconSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, Color.WHITE))

        // Scrim barrier dismisses menu
        binding.viewOverlayScrim.setOnClickListener {
            closeMenu()
        }
        binding.btnCloseMenu.setOnClickListener {
            closeMenu()
        }

        // 1. Go to Desktop / Home
        binding.itemMenuHome.setOnClickListener {
            closeMenu()
            launchHome()
        }

        // 2. File Manager (opens directly on top of Chrome/other apps in floating window!)
        binding.itemMenuFileManager.setOnClickListener {
            closeMenu()
            overlayWindowManager.openFileManager()
        }

        // 3. Web Browser
        binding.itemMenuWebApp.setOnClickListener {
            closeMenu()
            overlayWindowManager.openWebApp()
        }

        // 4. Search Apps
        binding.itemMenuSearch.setOnClickListener {
            closeMenu()
            launchHomeWithAction("ACTION_SEARCH")
        }

        // 5. Notifications
        binding.itemMenuNotifications.setOnClickListener {
            closeMenu()
            launchHomeWithAction("ACTION_NOTIFICATIONS")
        }

        // 6. Volume / Quick Settings
        binding.itemMenuVolume.setOnClickListener {
            closeMenu()
            launchHomeWithAction("ACTION_QUICK_SETTINGS")
        }

        // 7. TV Mode
        binding.itemMenuTvMode.setOnClickListener {
            closeMenu()
            serviceScope.launch {
                ConfigStore(applicationContext).update { it.copy(launcherMode = MODE_TV) }
            }
            launchHome()
        }

        // 8. Settings
        binding.itemMenuSettings.setOnClickListener {
            closeMenu()
            launchHomeWithAction("ACTION_SETTINGS")
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
        }
    }

    private fun showOverlay() {
        if (isOverlayAttached || overlayView == null || overlayParams == null) return
        runCatching {
            windowManager.addView(overlayView, overlayParams)
            isOverlayAttached = true
        }.onFailure { e ->
            android.util.Log.e("FloatingTaskbarService", "Failed to add overlayView: ${e.message}")
        }
    }

    private fun hideOverlay() {
        if (!isOverlayAttached || overlayView == null) return
        runCatching {
            windowManager.removeView(overlayView)
            isOverlayAttached = false
        }
    }

    private fun updateOverlayState() {
        val binding = overlayBinding ?: return
        if (isMenuVisible) {
            if (!isOverlayAttached) showOverlay()
            binding.viewOverlayScrim.visibility = View.VISIBLE
            binding.layoutOverlayMenu.visibility = View.VISIBLE
        } else {
            binding.viewOverlayScrim.visibility = View.GONE
            binding.layoutOverlayMenu.visibility = View.GONE
            hideOverlay()
        }
    }

    fun toggleMenu() {
        isMenuVisible = !isMenuVisible
        updateOverlayState()
    }

    fun closeMenu() {
        if (isMenuVisible) {
            isMenuVisible = false
            updateOverlayState()
        }
    }

    private fun launchHome() {
        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(homeIntent)
        }
    }

    private fun launchHomeWithAction(actionType: String) {
        runCatching {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = actionType
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayWindowManager.closeAll()
        hideTrigger()
        hideOverlay()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_TOGGLE_MENU = "com.gothwad.launcher.TOGGLE_MENU"
        const val ACTION_STOP_SERVICE = "com.gothwad.launcher.STOP_SERVICE"

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun startIfEnabled(context: Context) {
            if (canDrawOverlays(context)) {
                val intent = Intent(context, FloatingTaskbarService::class.java)
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingTaskbarService::class.java)
            context.stopService(intent)
        }

        fun requestOverlayPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
}
