package com.gothwad.launcher.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.gothwad.launcher.R
import com.gothwad.launcher.Actions
import com.gothwad.launcher.MainActivity
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.AppRepository
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.MODE_PC
import com.gothwad.launcher.data.MODE_TV
import com.gothwad.launcher.databinding.LayoutFloatingTaskbarOverlayBinding
import com.gothwad.launcher.databinding.LayoutFloatingTaskbarTriggerBinding
import com.gothwad.launcher.ui.AppIcons
import com.gothwad.launcher.ui.pc.PcTaskbarPinnedAdapter
import com.gothwad.launcher.ui.view.SmoothCornerDrawable
import com.gothwad.launcher.ui.view.SmoothOutlineProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * System Alert Window Service that provides a floating launcher icon across all apps in PC mode.
 * - Shows an iOS/Windows-style squircle floating bubble that can be dragged or tapped.
 * - When tapped, displays a rich quick-action panel (similar to the desktop right-click menu)
 *   and allows toggling the full bottom taskbar on/off over any active application.
 */
class FloatingTaskbarService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager

    // Views & Bindings
    private var triggerBinding: LayoutFloatingTaskbarTriggerBinding? = null
    private var triggerView: View? = null
    private var triggerParams: WindowManager.LayoutParams? = null

    private var overlayBinding: LayoutFloatingTaskbarOverlayBinding? = null
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    // State
    private var isTaskbarVisible = false
    private var isMenuVisible = false
    private var isTriggerAttached = false
    private var isOverlayAttached = false

    private var cachedConfig = LauncherConfig()
    private var allApps: List<AppEntry> = emptyList()
    private var pinnedAdapter: PcTaskbarPinnedAdapter? = null

    // Battery Broadcast Receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryStatus(intent)
        }
    }

    // Time ticker for overlay clock
    private val timeTicker = object : Runnable {
        override fun run() {
            updateClock()
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        setupTriggerView()
        setupOverlayView()

        // Register battery receiver
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        mainHandler.post(timeTicker)

        // Observe config and load apps
        serviceScope.launch {
            allApps = AppRepository.scan(applicationContext)
            ConfigStore(applicationContext).flow.collectLatest { config ->
                cachedConfig = config
                handleConfigChange(config)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_TASKBAR -> showTaskbar(true)
            ACTION_HIDE_TASKBAR -> showTaskbar(false)
            ACTION_TOGGLE_TASKBAR -> toggleTaskbar()
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

        // In PC mode with permission, show floating trigger button
        showTrigger()
        updatePinnedApps()
    }

    // =========================================================================
    // 1. FLOATING BUBBLE (Squircle Trigger)
    // =========================================================================

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTriggerView() {
        val binding = LayoutFloatingTaskbarTriggerBinding.inflate(LayoutInflater.from(this))
        triggerBinding = binding
        triggerView = binding.root

        // Continuous Smooth Squircle Background for the floating icon
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
        val initialX = (displayMetrics.widthPixels - (70 * density)).toInt()
        val initialY = (displayMetrics.heightPixels - (120 * density)).toInt()

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

        // Draggable touch listener with smooth click detection
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
                    // Slightly scale down on touch for tactile feedback
                    binding.btnFloatingBubble.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isMoving = true
                        params.x = initialParamX + dx
                        params.y = initialParamY + dy
                        runCatching { windowManager.updateViewLayout(triggerView, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    binding.btnFloatingBubble.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (!isMoving) {
                        // It was a tap! Open Quick Control Menu
                        toggleMenu()
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
    // 2. FULL OVERLAY (Quick Assist Menu + Persistent Bottom Taskbar)
    // =========================================================================

    private fun setupOverlayView() {
        val binding = LayoutFloatingTaskbarOverlayBinding.inflate(LayoutInflater.from(this))
        overlayBinding = binding
        overlayView = binding.root

        // Icons
        val appIconDrawable = runCatching {
            packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: androidx.core.content.ContextCompat.getDrawable(this, R.mipmap.ic_launcher)

        binding.imgPanelHeaderIcon.setImageDrawable(appIconDrawable)
        binding.btnCloseMenu.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))
        binding.imgIconToggleTaskbar.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DESKTOP, 0xFF4FA7FA.toInt()))
        binding.imgIconHome.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HOME, 0xFF60A5FA.toInt()))
        binding.imgIconFileMgr.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_FOLDER, 0xFF4FA7FA.toInt()))
        binding.imgIconWebApp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GLOBE, 0xFF60A5FA.toInt()))
        binding.imgIconSearch.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, Color.WHITE))
        binding.imgIconNotifs.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, Color.WHITE))
        binding.imgIconVolume.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_VOLUME, Color.WHITE))
        binding.imgIconTv.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_TV, 0xFF60A5FA.toInt()))
        binding.imgIconSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, Color.WHITE))

        // Taskbar Controls
        val density = resources.displayMetrics.density
        binding.btnOverlayStart.outlineProvider = SmoothOutlineProvider(7.5f * density, 0.6f)
        binding.btnOverlayStart.clipToOutline = true
        binding.btnOverlayStart.setImageDrawable(appIconDrawable)
        binding.imgOverlaySearchIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0xCCFFFFFF.toInt()))
        binding.btnOverlayNotifications.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, Color.WHITE))
        binding.imgOverlayTrayVolume.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_VOLUME, 0xFFCCCCCC.toInt()))
        binding.imgOverlayTrayNetwork.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI, 0xFFCCCCCC.toInt()))
        binding.imgHideTaskbarIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DOWN, 0xFFCCCCCC.toInt()))

        // Scrim barrier dismisses menu
        binding.viewOverlayScrim.setOnClickListener {
            closeMenu()
        }
        binding.btnCloseMenu.setOnClickListener {
            closeMenu()
        }

        // 1. Toggle Taskbar Visibility Option
        binding.itemMenuToggleTaskbar.setOnClickListener {
            closeMenu()
            toggleTaskbar()
        }

        // 2. Go to Desktop / Home
        binding.itemMenuHome.setOnClickListener {
            closeMenu()
            launchHome()
        }

        // 3. File Manager
        binding.itemMenuFileManager.setOnClickListener {
            closeMenu()
            openAppOrLauncher("com.gothwad.launcher.files")
        }

        // 4. Web Browser
        binding.itemMenuWebApp.setOnClickListener {
            closeMenu()
            openAppOrLauncher("com.gothwad.launcher.webapp")
        }

        // 5. Search Apps
        binding.itemMenuSearch.setOnClickListener {
            closeMenu()
            launchHomeWithAction("ACTION_SEARCH")
        }

        // 6. Notifications
        binding.itemMenuNotifications.setOnClickListener {
            closeMenu()
            launchHomeWithAction("ACTION_NOTIFICATIONS")
        }

        // 7. Volume / Quick Settings
        binding.itemMenuVolume.setOnClickListener {
            closeMenu()
            launchHomeWithAction("ACTION_QUICK_SETTINGS")
        }

        // 8. TV Mode
        binding.itemMenuTvMode.setOnClickListener {
            closeMenu()
            serviceScope.launch {
                ConfigStore(applicationContext).update { it.copy(launcherMode = MODE_TV) }
            }
            launchHome()
        }

        // 9. Settings
        binding.itemMenuSettings.setOnClickListener {
            closeMenu()
            launchHomeWithAction("ACTION_SETTINGS")
        }

        // Overlay Taskbar Events
        binding.btnOverlayStart.setOnClickListener {
            toggleMenu()
        }
        binding.btnOverlaySearch.setOnClickListener {
            launchHomeWithAction("ACTION_SEARCH")
        }
        binding.btnOverlayNotificationsContainer.setOnClickListener {
            launchHomeWithAction("ACTION_NOTIFICATIONS")
        }
        binding.layoutOverlayTrayCluster.setOnClickListener {
            launchHomeWithAction("ACTION_QUICK_SETTINGS")
        }
        binding.layoutOverlayClockCluster.setOnClickListener {
            launchHomeWithAction("ACTION_QUICK_SETTINGS")
        }
        binding.btnHideOverlayTaskbar.setOnClickListener {
            showTaskbar(false)
        }

        // Pinned Apps Recycler in Overlay Taskbar
        pinnedAdapter = PcTaskbarPinnedAdapter(
            onLaunchApp = { app ->
                showTaskbar(false)
                Actions.launchApp(applicationContext, app.pkg)
            },
            onUnpinApp = { _, _ -> }
        )
        binding.recyclerOverlayPinned.apply {
            layoutManager = LinearLayoutManager(this@FloatingTaskbarService, LinearLayoutManager.HORIZONTAL, false)
            adapter = pinnedAdapter
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
        if (isMenuVisible || isTaskbarVisible) {
            if (!isOverlayAttached) showOverlay()
            binding.viewOverlayScrim.visibility = if (isMenuVisible) View.VISIBLE else View.GONE
            binding.layoutOverlayMenu.visibility = if (isMenuVisible) View.VISIBLE else View.GONE
            binding.layoutOverlayTaskbar.visibility = if (isTaskbarVisible) View.VISIBLE else View.GONE
            binding.tvToggleTaskbarLabel.text = if (isTaskbarVisible) "Hide Taskbar" else "Show Taskbar"
            binding.tvTaskbarStatusBadge.text = if (isTaskbarVisible) "Visible" else "Hidden"
        } else {
            binding.viewOverlayScrim.visibility = View.GONE
            binding.layoutOverlayMenu.visibility = View.GONE
            binding.layoutOverlayTaskbar.visibility = View.GONE
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

    fun toggleTaskbar() {
        isTaskbarVisible = !isTaskbarVisible
        updateOverlayState()
    }

    fun showTaskbar(show: Boolean) {
        isTaskbarVisible = show
        updateOverlayState()
    }

    private fun updatePinnedApps() {
        val adapter = pinnedAdapter ?: return
        val pinnedPkgs = cachedConfig.pcPinnedApps
        val pinnedEntries = pinnedPkgs.mapNotNull { pkg ->
            allApps.find { it.pkg == pkg }
        }
        adapter.submitList(pinnedEntries)
    }

    private fun updateBatteryStatus(intent: Intent?) {
        val binding = overlayBinding ?: return
        if (intent == null) return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val text = if (isCharging) "$pct% ⚡" else "$pct%"
        binding.tvOverlayTrayBatteryPct.text = text
        val iconColor = if (pct <= 15) 0xFFFF5252.toInt() else 0xFFCCCCCC.toInt()
        binding.imgOverlayTrayBattery.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BATTERY, iconColor))
    }

    private fun updateClock() {
        val binding = overlayBinding ?: return
        val now = Date()
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFmt = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        binding.tvOverlayTaskbarTime.text = timeFmt.format(now)
        binding.tvOverlayTaskbarDate.text = dateFmt.format(now)
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

    private fun openAppOrLauncher(pkg: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "OPEN_APP"
            putExtra("EXTRA_PKG", pkg)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideTrigger()
        hideOverlay()
        mainHandler.removeCallbacks(timeTicker)
        runCatching { unregisterReceiver(batteryReceiver) }
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_SHOW_TASKBAR = "com.gothwad.launcher.SHOW_TASKBAR"
        const val ACTION_HIDE_TASKBAR = "com.gothwad.launcher.HIDE_TASKBAR"
        const val ACTION_TOGGLE_TASKBAR = "com.gothwad.launcher.TOGGLE_TASKBAR"
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
