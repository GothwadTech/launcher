package com.gothwad.launcher.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.Actions
import com.gothwad.launcher.R
import com.gothwad.launcher.data.AppRepository
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.ui.AppIcons
import com.gothwad.launcher.ui.pc.PcTaskbarPinnedAdapter
import com.gothwad.launcher.ui.pc.PcWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent Taskbar Overlay Service - Shows Windows-style taskbar over other apps
 * 
 * Problem: Display over other apps hides app content behind taskbar
 * Solution:
 * - When freeform enabled: launch apps with bounds excluding taskbar area
 * - When not freeform: taskbar auto-hides, or shows with transparency
 * - User can toggle overlay on/off
 * 
 * This service shows taskbar as system overlay (TYPE_APPLICATION_OVERLAY)
 * It stays visible even when other apps are open, like Windows taskbar
 */
class PcTaskbarOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var taskbarView: View? = null
    private var serviceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, PcTaskbarOverlayService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PcTaskbarOverlayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showTaskbarOverlay()
        observeConfig()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceJob?.cancel()
        taskbarView?.let { windowManager?.removeView(it) }
        taskbarView = null
    }

    private fun observeConfig() {
        serviceJob?.cancel()
        serviceJob = scope.launch {
            ConfigStore(this@PcTaskbarOverlayService).flow.collectLatest { config ->
                // If overlay disabled or not in PC mode, hide
                if (!config.pcTaskbarOverlayEnabled || config.launcherMode != 1) {
                    taskbarView?.visibility = View.GONE
                } else {
                    taskbarView?.visibility = View.VISIBLE
                    // Update taskbar height if changed
                    updateTaskbarHeight(config.pcTaskbarHeight)
                }
            }
        }
    }

    private fun updateTaskbarHeight(heightDp: Int) {
        taskbarView?.let { view ->
            val density = resources.displayMetrics.density
            val heightPx = (heightDp * density).toInt()
            val params = view.layoutParams as? WindowManager.LayoutParams
            params?.height = heightPx
            windowManager?.updateViewLayout(view, params)
        }
    }

    private fun showTaskbarOverlay() {
        if (taskbarView != null) return

        try {
            val inflater = LayoutInflater.from(this)
            // Reuse taskbar layout but simplified for overlay
            val view = inflater.inflate(R.layout.layout_pc_taskbar_overlay, null)

            // Setup taskbar view
            setupOverlayTaskbar(view)

            val density = resources.displayMetrics.density
            val taskbarHeight = (44 * density).toInt()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                taskbarHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                x = 0
                y = 0
            }

            windowManager?.addView(view, params)
            taskbarView = view

            // Start clock updates
            startClockUpdates(view)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupOverlayTaskbar(view: View) {
        val btnStart = view.findViewById<ImageButton>(R.id.btn_overlay_start)
        val recyclerPinned = view.findViewById<RecyclerView>(R.id.recycler_overlay_pinned)
        val recyclerRunning = view.findViewById<RecyclerView>(R.id.recycler_overlay_running)
        val btnShowDesktop = view.findViewById<View>(R.id.btn_overlay_show_desktop)

        // Use launcher's own icon instead of Windows icon
        try {
            btnStart?.setImageResource(R.mipmap.ic_launcher)
            btnStart?.setBackgroundResource(R.drawable.bg_pc_start_btn)
        } catch (_: Exception) {
            btnStart?.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, 0xFF4FA7FA.toInt()))
        }

        btnStart?.setOnClickListener {
            // Go home - opens launcher
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }

        btnShowDesktop?.setOnClickListener {
            // Minimize all windows if in launcher, else go home
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }

        // Load pinned apps
        scope.launch(Dispatchers.IO) {
            val apps = AppRepository.scan(this@PcTaskbarOverlayService)
            val config = ConfigStore(this@PcTaskbarOverlayService).flow
            // For simplicity, just show first 6 apps as pinned in overlay
            launch(Dispatchers.Main) {
                val pinnedAdapter = PcTaskbarPinnedAdapter(
                    onLaunchApp = { app ->
                        // Launch with bounds excluding taskbar
                        launchAppWithTaskbarBounds(app.pkg)
                    },
                    onUnpinApp = { _, _ -> }
                )
                recyclerPinned?.apply {
                    layoutManager = LinearLayoutManager(this@PcTaskbarOverlayService, LinearLayoutManager.HORIZONTAL, false)
                    adapter = pinnedAdapter
                }
                pinnedAdapter.submitList(apps.take(6))
            }
        }
    }

    private fun launchAppWithTaskbarBounds(pkg: String) {
        // Try to launch with bounds that exclude taskbar area
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val taskbarHeight = (44 * density).toInt()
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val pm = packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                val options = android.app.ActivityOptions.makeBasic()
                // Set launch bounds to exclude taskbar
                val bounds = android.graphics.Rect(0, 0, screenWidth, screenHeight - taskbarHeight)
                try {
                    options.javaClass.getMethod("setLaunchBounds", android.graphics.Rect::class.java)
                        .invoke(options, bounds)
                    options.javaClass.getMethod("setLaunchWindowingMode", Int::class.java)
                        .invoke(options, 5) // FREEFORM
                } catch (_: Exception) {}

                startActivity(it, options.toBundle())
            } catch (e: Exception) {
                startActivity(it)
            }
        }
    }

    private fun startClockUpdates(view: View) {
        val tvTime = view.findViewById<TextView>(R.id.tv_overlay_time)
        scope.launch {
            while (true) {
                val now = Date()
                val timeStr = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(now)
                tvTime?.text = timeStr
                kotlinx.coroutines.delay(1000)
            }
        }
    }
}
