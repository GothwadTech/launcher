package com.gothwad.launcher.ui.pc

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.Actions
import com.gothwad.launcher.GothwadApplication
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.AppRepository
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.MODE_TV
import com.gothwad.launcher.data.networkStatusFlow
import com.gothwad.launcher.databinding.FragmentPcLauncherBinding
import com.gothwad.launcher.databinding.LayoutPcAppContextMenuBinding
import com.gothwad.launcher.databinding.LayoutPcContextMenuBinding
import com.gothwad.launcher.service.NotificationManagerBridge
import com.gothwad.launcher.service.PcTaskbarOverlayService
import com.gothwad.launcher.ui.AppIcons
import com.gothwad.launcher.ui.WALLPAPERS
import com.gothwad.launcher.ui.dialogs.NotificationBottomSheetFragment
import com.gothwad.launcher.ui.dialogs.PinEntryDialogFragment
import com.gothwad.launcher.ui.dialogs.SearchDialogFragment
import com.gothwad.launcher.ui.dialogs.SettingsBottomSheetFragment
import com.gothwad.launcher.ui.dialogs.SetupWizardDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PcLauncherFragment : Fragment() {

    private var _binding: FragmentPcLauncherBinding? = null
    private val binding get() = _binding!!

    private var desktopAdapter: PcDesktopIconAdapter? = null
    private var pinnedAdapter: PcTaskbarPinnedAdapter? = null
    private var runningAdapter: PcTaskbarRunningAdapter? = null
    private var startMenuAdapter: PcStartMenuAdapter? = null

    private var currentConfig: LauncherConfig = LauncherConfig()
    private var allApps: List<AppEntry> = emptyList()

    private var itemTouchHelper: ItemTouchHelper? = null
    private var activePopupWindow: PopupWindow? = null

    // Window Manager UI cache
    private val windowViews = mutableMapOf<String, PcWindowView>()
    private var focusedWindowId: String? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryStatus(intent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPcLauncherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTaskbar()
        setupStartMenuFlyout()
        setupQuickSettingsFlyout()
        setupRecyclerView()
        setupWindowManager()
        observeData()
        startClockUpdates()
        registerStatusListeners()

        binding.recyclerDesktopGrid.post {
            if (_binding != null) {
                updateGridDimensions()
            }
        }
    }

    private fun registerStatusListeners() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = requireContext().registerReceiver(batteryReceiver, filter)
        updateBatteryStatus(stickyIntent)
    }

    private fun updateBatteryStatus(intent: Intent?) {
        if (_binding == null || intent == null) return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val text = if (isCharging) "$pct% ⚡" else "$pct%"
        binding.tvTrayBatteryPct.text = text
        binding.viewQuickSettings.tvQsBattery.text = text

        val iconColor = if (pct <= 15) 0xFFFF5252.toInt() else 0xFFCCCCCC.toInt()
        binding.imgTrayBattery.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BATTERY, iconColor))
        binding.viewQuickSettings.imgQsBattery.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BATTERY, iconColor))
    }

    // ==================== WINDOW MANAGER SETUP ====================

    private fun setupWindowManager() {
        // Observe windows flow to render window cards
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                PcWindowManager.windows.collectLatest { windows ->
                    renderWindows(windows)
                    updateRunningTaskbar(windows)
                }
            }
        }

        // Click on empty windows layer to focus desktop (deselect windows)
        binding.containerWindowsLayer.setOnClickListener {
            // If clicking empty space, defocus all? Or keep focused
            // For now, close flyouts and defocus
            closeAllFlyouts()
            focusedWindowId = null
            // Update focus visuals
            PcWindowManager.getWindows().forEach { w ->
                windowViews[w.id]?.let { view ->
                    view.bind(w, false)
                }
            }
            runningAdapter?.setFocusedWindow(null)
        }
    }

    private fun renderWindows(windows: List<PcWindow>) {
        if (_binding == null) return

        val container = binding.containerWindowsLayer
        val visibleWindows = windows.filter { !it.isMinimized }.sortedBy { it.zIndex }

        // Remove views for closed windows
        val currentIds = windows.map { it.id }.toSet()
        val toRemove = windowViews.keys.filter { it !in currentIds }
        toRemove.forEach { id ->
            windowViews[id]?.let { view ->
                container.removeView(view)
            }
            windowViews.remove(id)
            if (focusedWindowId == id) focusedWindowId = null
        }

        // Determine focused window (highest zIndex among visible)
        val newFocusedId = visibleWindows.maxByOrNull { it.zIndex }?.id

        // Add or update views
        visibleWindows.forEach { win ->
            var winView = windowViews[win.id]
            if (winView == null) {
                winView = PcWindowView(requireContext()).apply {
                    layoutParams = FrameLayout.LayoutParams(win.width, win.height).apply {
                        leftMargin = win.x.toInt()
                        topMargin = win.y.toInt()
                    }
                    onAction = { window, action ->
                        handleWindowAction(window, action)
                    }
                }
                windowViews[win.id] = winView
                container.addView(winView)
                // Animate in like Windows
                winView.alpha = 0f
                winView.scaleX = 0.92f
                winView.scaleY = 0.92f
                winView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
            }
            val isFocused = win.id == newFocusedId
            winView.bind(win, isFocused)
            winView.bringToFront()
        }

        // Ensure z-order in view hierarchy matches zIndex
        visibleWindows.sortedBy { it.zIndex }.forEach { win ->
            windowViews[win.id]?.bringToFront()
        }

        focusedWindowId = newFocusedId
        runningAdapter?.setFocusedWindow(newFocusedId)

        // Show/hide windows layer
        container.visibility = if (visibleWindows.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateRunningTaskbar(windows: List<PcWindow>) {
        if (_binding == null) return
        val hasRunning = windows.isNotEmpty()
        binding.viewTaskbarSeparator.visibility = if (hasRunning) View.VISIBLE else View.GONE
        binding.recyclerTaskbarRunning.visibility = if (hasRunning) View.VISIBLE else View.GONE
        runningAdapter?.submitList(windows.sortedBy { it.zIndex })
    }

    private fun handleWindowAction(window: PcWindow, action: PcWindowAction) {
        if (_binding == null) return
        val displayMetrics = resources.displayMetrics
        val taskbarHeight = binding.layoutTaskbar.height.takeIf { it > 0 } ?: (44 * displayMetrics.density).toInt()

        when (action) {
            PcWindowAction.CLOSE -> {
                // Animate out then close
                windowViews[window.id]?.let { view ->
                    view.animate()
                        .alpha(0f)
                        .scaleX(0.85f)
                        .scaleY(0.85f)
                        .setDuration(150)
                        .withEndAction {
                            PcWindowManager.closeWindow(window.id)
                            // Optionally kill app background process
                            Actions.close(requireContext(), window.app.pkg)
                        }
                        .start()
                } ?: run {
                    PcWindowManager.closeWindow(window.id)
                    Actions.close(requireContext(), window.app.pkg)
                }
            }
            PcWindowAction.MINIMIZE -> {
                // Minimize animation towards taskbar
                windowViews[window.id]?.let { view ->
                    view.animate()
                        .alpha(0f)
                        .scaleX(0.5f)
                        .scaleY(0.5f)
                        .translationY(100f)
                        .setDuration(200)
                        .withEndAction {
                            PcWindowManager.minimizeWindow(window.id)
                            view.translationY = 0f
                            view.scaleX = 1f
                            view.scaleY = 1f
                            view.alpha = 1f
                        }
                        .start()
                } ?: PcWindowManager.minimizeWindow(window.id)
            }
            PcWindowAction.MAXIMIZE_RESTORE -> {
                PcWindowManager.toggleMaximize(window.id, displayMetrics.widthPixels, displayMetrics.heightPixels, taskbarHeight)
            }
            PcWindowAction.FOCUS -> {
                PcWindowManager.focusWindow(window.id)
            }
            PcWindowAction.MOVE, PcWindowAction.RESIZE -> {
                // Already handled via drag, just ensure focus
            }
            PcWindowAction.LAUNCH -> {
                // Launch app fullscreen (or freeform if supported)
                closeAllFlyouts()
                val ctx = requireContext()
                // Try freeform if device supports, else normal
                val launched = Actions.launchAppInWindowedMode(ctx, window.app.pkg)
                if (!launched) {
                    Actions.launchApp(ctx, window.app.pkg)
                }
                // Keep window but minimize it to show app is running? Or keep visible?
                // For simulated windowing, we minimize our window card when real app opens
                // So user sees taskbar entry as running
                PcWindowManager.minimizeWindow(window.id)
            }
        }
    }

    // ==================== TASKBAR SETUP ====================

    private fun setupTaskbar() {
        // Base taskbar buttons - Use launcher's own square curved icon instead of Windows
        binding.btnStart.setBackgroundResource(com.gothwad.launcher.R.drawable.bg_pc_start_btn)
        try {
            binding.btnStart.setImageResource(com.gothwad.launcher.R.mipmap.ic_launcher)
        } catch (_: Exception) {
            binding.btnStart.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, 0xFF4FA7FA.toInt()))
        }
        binding.btnTaskbarFileManager.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_FOLDER, 0xFFFFCA28.toInt()))
        binding.imgTaskbarSearchIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0xCCFFFFFF.toInt()))
        binding.btnNotifications.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, Color.WHITE))
        binding.imgTrayVolume.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_VOLUME, 0xFFCCCCCC.toInt()))
        binding.imgTrayNetwork.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI, 0xFFCCCCCC.toInt()))

        // File Manager Button - PC Level
        binding.btnTaskbarFileManager.setOnClickListener {
            closeAllFlyouts()
            openFileManager()
        }

        // Start Menu Toggle
        binding.btnStart.setOnClickListener {
            toggleStartMenu()
        }

        // Search Button
        binding.btnSearch.setOnClickListener {
            closeAllFlyouts()
            openSearchDialog()
        }

        // Notifications Button
        binding.btnNotificationsContainer.setOnClickListener {
            closeAllFlyouts()
            NotificationBottomSheetFragment.newInstance()
                .show(parentFragmentManager, NotificationBottomSheetFragment.TAG)
        }

        // Tray Cluster (Quick Settings Action Center)
        binding.layoutTrayCluster.setOnClickListener {
            toggleQuickSettings()
        }

        // Clock Cluster (also opens Quick Settings / Notifications)
        binding.layoutClockCluster.setOnClickListener {
            toggleQuickSettings()
        }

        // Show Desktop Peek Button - Windows style: minimize all windows
        binding.btnShowDesktop.setOnClickListener {
            closeAllFlyouts()
            val windows = PcWindowManager.getWindows()
            if (windows.any { !it.isMinimized }) {
                // Minimize all
                windows.forEach { w ->
                    if (!w.isMinimized) {
                        windowViews[w.id]?.animate()?.alpha(0f)?.scaleX(0.5f)?.scaleY(0.5f)?.setDuration(150)?.start()
                    }
                }
                // Delay then actually minimize in manager
                binding.btnShowDesktop.postDelayed({
                    windows.forEach { PcWindowManager.minimizeWindow(it.id) }
                }, 160)
            } else {
                // Restore all minimized
                windows.forEach { PcWindowManager.restoreWindow(it.id) }
            }
        }

        // Pinned Apps on Taskbar
        pinnedAdapter = PcTaskbarPinnedAdapter(
            onLaunchApp = { app ->
                closeAllFlyouts()
                handleAppLaunch(app)
            },
            onUnpinApp = { app, v ->
                showUnpinPopup(app, v)
            }
        )
        binding.recyclerTaskbarPinned.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = pinnedAdapter
            setHasFixedSize(true)
        }

        // Running Windows (Open Apps) on Taskbar - Windows style
        runningAdapter = PcTaskbarRunningAdapter(
            onClickWindow = { win ->
                closeAllFlyouts()
                if (win.isMinimized) {
                    PcWindowManager.restoreWindow(win.id)
                } else if (win.id == focusedWindowId) {
                    // If already focused, minimize (like Windows)
                    PcWindowManager.minimizeWindow(win.id)
                } else {
                    PcWindowManager.focusWindow(win.id)
                }
            },
            onCloseWindow = { win ->
                handleWindowAction(win, PcWindowAction.CLOSE)
            }
        )
        binding.recyclerTaskbarRunning.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = runningAdapter
            setHasFixedSize(true)
        }

        // Dismiss flyouts when touching desktop wallpaper or grid
        binding.pcLauncherRoot.setOnClickListener {
            closeAllFlyouts()
        }
    }

    private fun showUnpinPopup(app: AppEntry, anchor: View) {
        val popup = PopupWindow(requireContext())
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = androidx.core.content.ContextCompat.getDrawable(context, com.gothwad.launcher.R.drawable.bg_pc_context_menu)
            elevation = 16f
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
        }

        val icon = android.widget.ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams((18 * density).toInt(), (18 * density).toInt())
            setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_UNPIN, Color.WHITE))
        }

        val label = android.widget.TextView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * density).toInt() }
            text = "Unpin from Taskbar"
            setTextColor(Color.WHITE)
            textSize = 12f
        }

        container.addView(icon)
        container.addView(label)

        container.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                ConfigStore(requireContext()).update { cfg ->
                    cfg.copy(pcPinnedApps = cfg.pcPinnedApps.filter { it != app.pkg })
                }
            }
            popup.dismiss()
        }

        popup.contentView = container
        popup.isOutsideTouchable = true
        popup.isFocusable = true
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.showAsDropDown(anchor, 0, (-anchor.height - (36 * density).toInt()))
    }

    private fun setupStartMenuFlyout() {
        val startBinding = binding.viewStartMenu

        startBinding.imgStartSearchIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0xFF8AB4F8.toInt()))
        startBinding.imgUserAvatar.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PERSON, 0xFF4FA7FA.toInt()))
        startBinding.btnStartFileManager.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_FOLDER, 0xFFFFCA28.toInt()))
        startBinding.btnStartTvMode.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_TV, 0xFF60A5FA.toInt()))
        startBinding.btnStartSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, Color.WHITE))

        startBinding.btnStartFileManager.setOnClickListener {
            closeAllFlyouts()
            openFileManager()
        }

        startBinding.startSearchContainer.setOnClickListener {
            closeAllFlyouts()
            openSearchDialog()
        }

        startBinding.btnAllApps.setOnClickListener {
            closeAllFlyouts()
            openSearchDialog()
        }

        startBinding.btnStartTvMode.setOnClickListener {
            closeAllFlyouts()
            viewLifecycleOwner.lifecycleScope.launch {
                ConfigStore(requireContext()).update { it.copy(launcherMode = MODE_TV) }
            }
        }

        startBinding.btnStartSettings.setOnClickListener {
            closeAllFlyouts()
            openFullSettingsDialog()
        }

        startMenuAdapter = PcStartMenuAdapter { app ->
            closeAllFlyouts()
            handleAppLaunch(app)
        }
        startBinding.recyclerStartMenuApps.apply {
            layoutManager = GridLayoutManager(requireContext(), 5)
            adapter = startMenuAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupQuickSettingsFlyout() {
        val qsBinding = binding.viewQuickSettings
        val context = requireContext()

        qsBinding.imgTileWifi.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI, 0xFF4FA7FA.toInt()))
        qsBinding.imgTileHome.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HOME, 0xFF4FA7FA.toInt()))
        qsBinding.imgTileTv.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_TV, 0xFF60A5FA.toInt()))
        qsBinding.imgTileNotifs.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, Color.WHITE))
        qsBinding.imgQsVolume.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_VOLUME, Color.WHITE))
        qsBinding.imgQsWifi.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI, 0xFF8AB4F8.toInt()))

        // Volume control
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volPct = (currentVol * 100 / maxVol)

        qsBinding.seekbarVolume.max = maxVol
        qsBinding.seekbarVolume.progress = currentVol
        qsBinding.tvVolumePct.text = "$volPct%"

        qsBinding.seekbarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    val newPct = (progress * 100 / maxVol)
                    qsBinding.tvVolumePct.text = "$newPct%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Quick tiles
        qsBinding.tileQsWifi.setOnClickListener {
            closeAllFlyouts()
            Actions.openNetworkSettings(requireContext())
        }

        qsBinding.tileQsHome.setOnClickListener {
            closeAllFlyouts()
        }

        qsBinding.tileQsTvMode.setOnClickListener {
            closeAllFlyouts()
            viewLifecycleOwner.lifecycleScope.launch {
                ConfigStore(requireContext()).update { it.copy(launcherMode = MODE_TV) }
            }
        }

        qsBinding.tileQsNotifs.setOnClickListener {
            closeAllFlyouts()
            NotificationBottomSheetFragment.newInstance()
                .show(parentFragmentManager, NotificationBottomSheetFragment.TAG)
        }

        qsBinding.btnQsSettings.setOnClickListener {
            closeAllFlyouts()
            openFullSettingsDialog()
        }
    }

    private fun toggleStartMenu() {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        if (binding.containerStartMenu.visibility == View.VISIBLE) {
            binding.containerStartMenu.visibility = View.GONE
        } else {
            closeAllFlyouts()
            binding.containerStartMenu.alpha = 0f
            binding.containerStartMenu.translationY = 20f
            binding.containerStartMenu.visibility = View.VISIBLE
            binding.containerStartMenu.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(160)
                .start()
        }
    }

    private fun toggleQuickSettings() {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        if (binding.containerQuickSettings.visibility == View.VISIBLE) {
            binding.containerQuickSettings.visibility = View.GONE
        } else {
            closeAllFlyouts()
            binding.containerQuickSettings.alpha = 0f
            binding.containerQuickSettings.translationY = 20f
            binding.containerQuickSettings.visibility = View.VISIBLE
            binding.containerQuickSettings.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(160)
                .start()
        }
    }

    private fun closeAllFlyouts() {
        activePopupWindow?.dismiss()
        activePopupWindow = null
        binding.containerStartMenu.visibility = View.GONE
        binding.containerQuickSettings.visibility = View.GONE
    }

    @SuppressLint(\"ClickableViewAccessibility\")
    private fun setupRecyclerView() {
        desktopAdapter = PcDesktopIconAdapter(
            onLaunchApp = { app ->
                closeAllFlyouts()
                handleAppLaunch(app)
            },
            onAppContextMenu = { app, v, touchX, touchY ->
                closeAllFlyouts()
                showAppContextMenu(app, v, touchX, touchY)
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper?.startDrag(viewHolder)
            }
        )

        // ItemTouchHelper for Mouse and Touch drag-and-drop
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = true

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                desktopAdapter?.moveItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.animate()
                        ?.scaleX(1.08f)
                        ?.scaleY(1.08f)
                        ?.alpha(0.88f)
                        ?.setDuration(140)
                        ?.start()
                    viewHolder?.itemView?.elevation = 16f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(140)
                    .start()
                viewHolder.itemView.elevation = 0f

                // Persist the new order immediately
                saveCurrentDesktopOrder()
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(binding.recyclerDesktopGrid)

        binding.recyclerDesktopGrid.apply {
            adapter = desktopAdapter
            setHasFixedSize(true)
        }

        // Empty desktop right-click and touch long-press listener
        var lastEmptyTouchX = 0f
        var lastEmptyTouchY = 0f

        val desktopTouchListener = View.OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                lastEmptyTouchX = event.rawX
                lastEmptyTouchY = event.rawY
            }
            false
        }

        binding.recyclerDesktopGrid.setOnTouchListener(desktopTouchListener)
        binding.pcLauncherRoot.setOnTouchListener(desktopTouchListener)

        val desktopContextClickListener = View.OnContextClickListener {
            if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
                return@OnContextClickListener true
            }
            closeAllFlyouts()
            showDesktopContextMenu(lastEmptyTouchX, lastEmptyTouchY)
            true
        }

        binding.recyclerDesktopGrid.setOnContextClickListener(desktopContextClickListener)
        binding.pcLauncherRoot.setOnContextClickListener(desktopContextClickListener)

        val desktopLongClickListener = View.OnLongClickListener {
            if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
                return@OnLongClickListener true
            }
            closeAllFlyouts()
            showDesktopContextMenu(lastEmptyTouchX, lastEmptyTouchY)
            true
        }

        binding.recyclerDesktopGrid.setOnLongClickListener(desktopLongClickListener)
        binding.pcLauncherRoot.setOnLongClickListener(desktopLongClickListener)

        updateGridDimensions()
    }

    private fun updateGridDimensions() {
        // Use fixed DPI metrics if DPI independence enabled, else system metrics
        // This makes UI independent from system DPI setting (320/400/200)
        val displayMetrics = if (currentConfig.pcDpiIndependent) {
            com.gothwad.launcher.data.DpiHelper.getFixedMetrics(requireContext(), currentConfig.pcFixedDpi)
        } else {
            resources.displayMetrics
        }
        val density = displayMetrics.density
        val scale = currentConfig.pcUiScale.coerceIn(0.5f, 1.3f)
        val iconSizeDp = currentConfig.pcIconSize.coerceIn(28, 56)
        val spacingDp = currentConfig.pcGridSpacing.coerceIn(4, 20)

        // Item cell dimensions with scale
        val itemHeightDp = (iconSizeDp + 30) * scale
        val effectiveTbHeightDp = currentConfig.pcTaskbarHeight.coerceIn(30, 48)
        val taskbarHeightPx = (effectiveTbHeightDp * scale * density).toInt().coerceAtLeast((28 * density).toInt())

        // Available vertical height for desktop grid
        val screenHeightPixels = displayMetrics.heightPixels
        val recyclerHeightPx = if (binding.recyclerDesktopGrid.height > 0) {
            binding.recyclerDesktopGrid.height
        } else {
            screenHeightPixels - taskbarHeightPx
        }
        val verticalPaddingDp = spacingDp * 2.0f
        val availableHeightDp = (recyclerHeightPx / density) - verticalPaddingDp

        // In HORIZONTAL GridLayoutManager, spanCount is the number of ROWS.
        val rowSpanCount = (availableHeightDp / itemHeightDp).toInt().coerceIn(3, 16)

        val currentLm = binding.recyclerDesktopGrid.layoutManager as? GridLayoutManager
        if (currentLm == null || currentLm.spanCount != rowSpanCount || currentLm.orientation != RecyclerView.HORIZONTAL) {
            binding.recyclerDesktopGrid.layoutManager = GridLayoutManager(
                requireContext(),
                rowSpanCount,
                RecyclerView.HORIZONTAL,
                false
            )
        }

        val padPx = (spacingDp * density).toInt()
        binding.recyclerDesktopGrid.setPadding(padPx, (padPx * 1.1f).toInt(), padPx, padPx)

        // Dynamically adjust taskbar layout height
        val tbLp = binding.layoutTaskbar.layoutParams
        tbLp.height = taskbarHeightPx
        binding.layoutTaskbar.layoutParams = tbLp

        // Adjust Taskbar margin on recycler and windows layer
        val gridLp = binding.recyclerDesktopGrid.layoutParams as ViewGroup.MarginLayoutParams
        gridLp.bottomMargin = taskbarHeightPx
        binding.recyclerDesktopGrid.layoutParams = gridLp

        val winLayerLp = binding.containerWindowsLayer.layoutParams as ViewGroup.MarginLayoutParams
        winLayerLp.bottomMargin = taskbarHeightPx
        binding.containerWindowsLayer.layoutParams = winLayerLp

        // Taskbar Center vs Left Alignment
        val appsLp = binding.layoutTaskbarApps.layoutParams as RelativeLayout.LayoutParams
        if (currentConfig.pcTaskbarCenter) {
            appsLp.removeRule(RelativeLayout.ALIGN_PARENT_START)
            appsLp.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE)
        } else {
            appsLp.removeRule(RelativeLayout.CENTER_HORIZONTAL)
            appsLp.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE)
        }
        binding.layoutTaskbarApps.layoutParams = appsLp

        // Adjust Taskbar button sizes proportionally to prevent oversized elements
        val btnSizePx = (taskbarHeightPx - (8 * density)).toInt().coerceIn((26 * density).toInt(), (40 * density).toInt())
        val btnPadPx = (btnSizePx * 0.20f).toInt().coerceAtLeast((3 * density).toInt())

        val startLp = binding.btnStart.layoutParams
        startLp.width = btnSizePx
        startLp.height = btnSizePx
        binding.btnStart.layoutParams = startLp
        binding.btnStart.setPadding(btnPadPx, btnPadPx, btnPadPx, btnPadPx)

        // Adjust Taskbar search bar size
        val searchLp = binding.btnSearch.layoutParams
        searchLp.height = (btnSizePx * 0.88f).toInt()
        val searchWidthDp = (140 * scale).coerceIn(100f, 170f)
        searchLp.width = (searchWidthDp * density).toInt()
        binding.btnSearch.layoutParams = searchLp

        // Adjust tray buttons and cluster height
        val trayClusterLp = binding.layoutTrayCluster.layoutParams
        trayClusterLp.height = btnSizePx
        binding.layoutTrayCluster.layoutParams = trayClusterLp

        val notifLp = binding.btnNotificationsContainer.layoutParams
        notifLp.width = btnSizePx
        notifLp.height = btnSizePx
        binding.btnNotificationsContainer.layoutParams = notifLp

        // Flyout positions offset above taskbar
        val flyoutMarginBottomPx = taskbarHeightPx + (6 * density).toInt()
        val startMenuLp = binding.containerStartMenu.layoutParams as FrameLayout.LayoutParams
        startMenuLp.bottomMargin = flyoutMarginBottomPx
        binding.containerStartMenu.layoutParams = startMenuLp

        val qsLp = binding.containerQuickSettings.layoutParams as FrameLayout.LayoutParams
        qsLp.bottomMargin = flyoutMarginBottomPx
        binding.containerQuickSettings.layoutParams = qsLp

        // Update pinned adapter sizes to fit taskbar perfectly
        val pinnedItemSizePx = btnSizePx
        val pinnedIconSizePx = (btnSizePx * 0.62f).toInt()
        pinnedAdapter?.updateSizes(pinnedItemSizePx, pinnedIconSizePx)
    }

    private fun saveCurrentDesktopOrder() {
        val currentItems = desktopAdapter?.getCurrentList() ?: return
        val newOrder = currentItems.map { it.pkg }
        viewLifecycleOwner.lifecycleScope.launch {
            ConfigStore(requireContext()).update { it.copy(pcDesktopOrder = newOrder, pcSortOrder = 0) }
        }
    }

    // Windows 11 / Linux Professional Desktop Context Menu
    private fun showDesktopContextMenu(touchX: Float, touchY: Float) {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        val context = requireContext()
        val inflater = LayoutInflater.from(context)
        val menuBinding = LayoutPcContextMenuBinding.inflate(inflater)

        // Professional icons for new menu
        menuBinding.imgMenuHeaderIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DESKTOP, 0xFF4FA7FA.toInt()))
        menuBinding.imgIconView.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, 0xFF8AB4F8.toInt()))
        menuBinding.imgIconSort.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DOWN, 0xFF8AB4F8.toInt()))
        menuBinding.imgIconRefresh.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_REFRESH, 0xFF4DD0E1.toInt()))
        menuBinding.imgIconNew.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_ADD, 0xFF81C784.toInt()))
        menuBinding.imgIconFileManager.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_FOLDER, 0xFFFFCA28.toInt()))
        menuBinding.imgIconTerminal.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_VOLUME, 0xFFB39DDB.toInt())) // Terminal icon
        menuBinding.imgIconScale.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DISPLAY, Color.WHITE))
        menuBinding.imgIconSize.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE))
        menuBinding.imgIconSpacing.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_MOVE, Color.WHITE))
        menuBinding.imgIconLabels.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PENCIL, Color.WHITE))
        menuBinding.imgIconTaskbar.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_STORAGE, Color.WHITE))
        menuBinding.imgIconDisplaySettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DISPLAY, 0xFF4FA7FA.toInt()))
        menuBinding.imgIconPersonalize.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PALETTE, 0xFFCE93D8.toInt()))
        menuBinding.imgIconSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, Color.WHITE))

        // Arrows for submenus
        menuBinding.imgArrowView.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, 0xFF9AA0A6.toInt()))
        menuBinding.imgArrowSort.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, 0xFF9AA0A6.toInt()))
        menuBinding.imgArrowNew.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, 0xFF9AA0A6.toInt()))
        menuBinding.imgArrowScale.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, 0xFF9AA0A6.toInt()))
        menuBinding.imgArrowIconSize.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, 0xFF9AA0A6.toInt()))
        menuBinding.imgArrowTaskbar.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, 0xFF9AA0A6.toInt()))

        // Current Badges
        val scalePct = (currentConfig.pcUiScale * 100).toInt()
        menuBinding.tvCurrentScaleBadge.text = "$scalePct%"

        val sizeName = when (currentConfig.pcIconSize) {
            in 0..34 -> "Small"
            in 35..44 -> "Medium"
            else -> "Large"
        }
        menuBinding.tvCurrentIconSizeBadge.text = sizeName

        val spacingName = when (currentConfig.pcGridSpacing) {
            in 0..7 -> "Compact"
            in 8..13 -> "Normal"
            else -> "Spacious"
        }
        menuBinding.tvCurrentSpacingBadge.text = spacingName

        // Show/Hide labels state
        if (currentConfig.pcShowLabels) {
            menuBinding.imgCheckLabels.setImageDrawable(
                AppIcons.createDrawable(AppIcons.PATH_CHECK, 0xFF4FA7FA.toInt())
            )
            menuBinding.imgCheckLabels.visibility = View.VISIBLE
        } else {
            menuBinding.imgCheckLabels.visibility = View.INVISIBLE
        }

        // Sort state
        menuBinding.tvCurrentSortBadge.text = if (currentConfig.pcSortOrder == 1) "A-Z" else "Custom"

        val popup = PopupWindow(
            menuBinding.root,
            (280 * resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 20f
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        activePopupWindow = popup

        // View > Large/Medium/Small/Auto arrange
        menuBinding.itemView.setOnClickListener {
            popup.dismiss()
            showViewOptionsDialog()
        }

        // Sort by
        menuBinding.itemSortDesktop.setOnClickListener {
            popup.dismiss()
            showSortPickerDialog()
        }

        // Refresh
        menuBinding.itemRefreshDesktop.setOnClickListener {
            popup.dismiss()
            viewLifecycleOwner.lifecycleScope.launch {
                loadApps()
                android.widget.Toast.makeText(context, "Desktop refreshed", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // New > Folder/Document/Shortcut
        menuBinding.itemNew.setOnClickListener {
            popup.dismiss()
            showNewOptionsDialog()
        }

        // File Manager
        menuBinding.itemFileManager.setOnClickListener {
            popup.dismiss()
            openFileManager()
        }

        // Open Terminal
        menuBinding.itemTerminal.setOnClickListener {
            popup.dismiss()
            tryOpenTerminal()
        }

        // Display Scale
        menuBinding.itemPcUiScale.setOnClickListener {
            popup.dismiss()
            showScalePickerDialog()
        }

        // Icon Size
        menuBinding.itemIconSize.setOnClickListener {
            popup.dismiss()
            showIconSizePickerDialog()
        }

        // Spacing
        menuBinding.itemDesktopSpacing.setOnClickListener {
            popup.dismiss()
            showSpacingPickerDialog()
        }

        // Toggle Labels
        menuBinding.itemToggleLabels.setOnClickListener {
            popup.dismiss()
            viewLifecycleOwner.lifecycleScope.launch {
                ConfigStore(context).update { it.copy(pcShowLabels = !it.pcShowLabels) }
            }
        }

        // Taskbar Settings
        menuBinding.itemTaskbarSettings.setOnClickListener {
            popup.dismiss()
            showTaskbarSettingsDialog()
        }

        // Display Settings - opens Android display settings
        menuBinding.itemDisplaySettings.setOnClickListener {
            popup.dismiss()
            try {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    android.widget.Toast.makeText(context, "Display Settings not available", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Personalize
        menuBinding.itemPersonalize.setOnClickListener {
            popup.dismiss()
            openFullSettingsDialog()
        }

        // Launcher Settings
        menuBinding.itemOpenSettings.setOnClickListener {
            popup.dismiss()
            openFullSettingsDialog()
        }

        showPopupAtSafeCoords(popup, touchX, touchY, (280 * resources.displayMetrics.density).toInt(), (520 * resources.displayMetrics.density).toInt())
    }

    private fun showViewOptionsDialog() {
        val options = listOf(
            PcDialogHelper.OptionItem("Large Icons", "Big desktop icons (56dp)", 56),
            PcDialogHelper.OptionItem("Medium Icons", "Standard desktop icons (42dp)", 42),
            PcDialogHelper.OptionItem("Small Icons", "Compact desktop icons (32dp)", 32),
            PcDialogHelper.OptionItem("Auto Arrange Icons", if (currentConfig.pcSortOrder == 1) "✓ Currently enabled" else "Arrange automatically", -1),
            PcDialogHelper.OptionItem("Align Icons to Grid", "Snap icons to grid", -2),
            PcDialogHelper.OptionItem("Show Desktop Icons", if (currentConfig.hidden.isEmpty()) "✓ All icons visible" else "Some icons hidden", -3)
        )
        val selectedIdx = when (currentConfig.pcIconSize) {
            in 0..35 -> 2
            in 36..45 -> 1
            else -> 0
        }
        PcDialogHelper.showOptionsPickerDialog(
            context = requireContext(),
            title = "View",
            subtitle = "Desktop view options - Windows 11 style",
            options = options,
            selectedIndex = selectedIdx,
            onSelect = { opt ->
                val size = opt.tag as Int
                when {
                    size > 0 -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            ConfigStore(requireContext()).update { it.copy(pcIconSize = size) }
                        }
                    }
                    size == -1 -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            ConfigStore(requireContext()).update { it.copy(pcSortOrder = if (it.pcSortOrder == 1) 0 else 1) }
                        }
                    }
                    size == -2 -> {
                        android.widget.Toast.makeText(requireContext(), "Icons aligned to grid", android.widget.Toast.LENGTH_SHORT).show()
                        updateGridDimensions()
                    }
                    size == -3 -> {
                        // Toggle show desktop icons - unhide all
                        viewLifecycleOwner.lifecycleScope.launch {
                            ConfigStore(requireContext()).update { it.copy(hidden = emptySet()) }
                        }
                    }
                }
            }
        )
    }

    private fun showNewOptionsDialog() {
        val options = listOf(
            PcDialogHelper.OptionItem("Folder", "Create new folder (via File Manager)", "folder"),
            PcDialogHelper.OptionItem("Text Document", "Create new text file", "text"),
            PcDialogHelper.OptionItem("Shortcut", "Create app shortcut on desktop", "shortcut"),
            PcDialogHelper.OptionItem("Bitmap Image", "Create new image file", "image")
        )
        PcDialogHelper.showOptionsPickerDialog(
            context = requireContext(),
            title = "New",
            subtitle = "Create new item on desktop",
            options = options,
            selectedIndex = 0,
            onSelect = { opt ->
                when (opt.tag as String) {
                    "folder" -> {
                        android.widget.Toast.makeText(requireContext(), "Open File Manager to create folder", android.widget.Toast.LENGTH_SHORT).show()
                        openFileManager()
                    }
                    "text" -> {
                        android.widget.Toast.makeText(requireContext(), "Open File Manager to create text file", android.widget.Toast.LENGTH_SHORT).show()
                        openFileManager()
                    }
                    "shortcut" -> {
                        android.widget.Toast.makeText(requireContext(), "Long press app to create shortcut", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    "image" -> {
                        android.widget.Toast.makeText(requireContext(), "Open File Manager to create image", android.widget.Toast.LENGTH_SHORT).show()
                        openFileManager()
                    }
                }
            }
        )
    }

    private fun tryOpenTerminal() {
        val terminalPackages = listOf(
            "com.termux",
            "jackpal.androidterm",
            "org.connectbot",
            "com.sonelli.juicessh",
            "com.server.auditor.ssh.client"
        )
        for (pkg in terminalPackages) {
            try {
                val intent = requireContext().packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        // Fallback - try to open via ADB shell or show toast
        android.widget.Toast.makeText(requireContext(), "No terminal app found. Install Termux from Play Store.", android.widget.Toast.LENGTH_LONG).show()
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.termux")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }


    // Windows 11 Professional App Context Menu - Full Windows/Linux level
    private fun showAppContextMenu(app: AppEntry, view: View, touchX: Float, touchY: Float) {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        val context = requireContext()
        val menuBinding = LayoutPcAppContextMenuBinding.inflate(LayoutInflater.from(context))

        val displayName = currentConfig.pcCustomLabels[app.pkg] ?: app.label
        menuBinding.tvAppHeaderLabel.text = displayName
        menuBinding.tvAppHeaderPkg.text = app.pkg

        if (app.icon != null) {
            menuBinding.imgAppHeaderIcon.setImageBitmap(app.icon)
        } else {
            menuBinding.imgAppHeaderIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE))
        }

        menuBinding.imgActionOpen.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PLAY, 0xFF4FA7FA.toInt()))
        val isPinned = app.pkg in currentConfig.pcPinnedApps
        menuBinding.imgActionPin.setImageDrawable(
            if (isPinned) AppIcons.createDrawable(AppIcons.PATH_UNPIN, Color.WHITE)
            else AppIcons.createDrawable(AppIcons.PATH_PIN, Color.WHITE)
        )
        menuBinding.tvActionPin.text = if (isPinned) "Unpin from Taskbar" else "Pin to Taskbar"
        menuBinding.imgActionPinStart.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PIN, 0xFF8AB4F8.toInt()))
        menuBinding.imgActionRename.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PENCIL, Color.WHITE))
        menuBinding.imgActionMoveUp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_UP, Color.WHITE))
        menuBinding.imgActionMoveDown.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DOWN, Color.WHITE))
        menuBinding.imgActionOpenLocation.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_FOLDER, 0xFFFFCA28.toInt()))
        menuBinding.imgActionHide.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HIDE, Color.WHITE))
        menuBinding.imgActionInfo.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_INFO, Color.WHITE))
        menuBinding.imgActionUninstall.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DELETE, 0xFFFF6B6B.toInt()))

        val popup = PopupWindow(
            menuBinding.root,
            (260 * resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 20f
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        activePopupWindow = popup

        menuBinding.itemAppOpen.setOnClickListener {
            popup.dismiss()
            handleAppLaunch(app)
        }

        menuBinding.itemAppPinTaskbar.setOnClickListener {
            popup.dismiss()
            viewLifecycleOwner.lifecycleScope.launch {
                ConfigStore(context).update { cfg ->
                    val newPinned = if (isPinned) {
                        cfg.pcPinnedApps.filter { it != app.pkg }
                    } else {
                        cfg.pcPinnedApps + app.pkg
                    }
                    cfg.copy(pcPinnedApps = newPinned)
                }
            }
        }

        menuBinding.itemAppPinStart.setOnClickListener {
            popup.dismiss()
            // For now, also pin to taskbar as Start pinning is same in this launcher
            viewLifecycleOwner.lifecycleScope.launch {
                ConfigStore(context).update { cfg ->
                    if (app.pkg !in cfg.pcPinnedApps) {
                        cfg.copy(pcPinnedApps = cfg.pcPinnedApps + app.pkg)
                    } else cfg
                }
            }
            android.widget.Toast.makeText(context, "${displayName} pinned to Start", android.widget.Toast.LENGTH_SHORT).show()
        }

        menuBinding.itemAppRename.setOnClickListener {
            popup.dismiss()
            PcDialogHelper.showRenameShortcutDialog(
                context = context,
                appLabel = app.label,
                currentCustomLabel = currentConfig.pcCustomLabels[app.pkg],
                onSave = { newName ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        ConfigStore(context).update { cfg ->
                            cfg.copy(pcCustomLabels = cfg.pcCustomLabels + (app.pkg to newName))
                        }
                    }
                },
                onReset = {
                    viewLifecycleOwner.lifecycleScope.launch {
                        ConfigStore(context).update { cfg ->
                            cfg.copy(pcCustomLabels = cfg.pcCustomLabels - app.pkg)
                        }
                    }
                }
            )
        }

        menuBinding.itemAppMoveUp.setOnClickListener {
            popup.dismiss()
            if (desktopAdapter?.moveItemByPkg(app.pkg, -1) == true) {
                saveCurrentDesktopOrder()
            }
        }

        menuBinding.itemAppMoveDown.setOnClickListener {
            popup.dismiss()
            if (desktopAdapter?.moveItemByPkg(app.pkg, 1) == true) {
                saveCurrentDesktopOrder()
            }
        }

        menuBinding.itemAppOpenLocation.setOnClickListener {
            popup.dismiss()
            // Open app info which shows storage location
            openAppDetails(app.pkg)
        }

        menuBinding.itemAppHide.setOnClickListener {
            popup.dismiss()
            viewLifecycleOwner.lifecycleScope.launch {
                ConfigStore(context).update { cfg ->
                    cfg.copy(hidden = cfg.hidden + app.pkg)
                }
            }
        }

        menuBinding.itemAppInfo.setOnClickListener {
            popup.dismiss()
            openAppDetails(app.pkg)
        }

        menuBinding.itemAppUninstall.setOnClickListener {
            popup.dismiss()
            uninstallApp(app.pkg)
        }

        showPopupAtSafeCoords(popup, touchX, touchY, (260 * resources.displayMetrics.density).toInt(), (460 * resources.displayMetrics.density).toInt())
    }

        private fun showPopupAtSafeCoords(popup: PopupWindow, rawX: Float, rawY: Float, widthPx: Int, heightPx: Int) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val x = if (rawX > 0) rawX.toInt() else (screenWidth / 2 - widthPx / 2)
        val y = if (rawY > 0) rawY.toInt() else (screenHeight / 2 - heightPx / 2)

        val safeX = x.coerceIn(8, screenWidth - widthPx - 8)
        val safeY = y.coerceIn(8, screenHeight - heightPx - 48)

        popup.showAtLocation(binding.pcLauncherRoot, Gravity.NO_GRAVITY, safeX, safeY)
    }

    // Dialog: PC UI Scale Picker (Refined DPI options)
    private fun showScalePickerDialog() {
        val options = listOf(
            PcDialogHelper.OptionItem(\"65% Ultra Compact\", \"Smallest DPI, for high-density PC setups\", 0.65f),
            PcDialogHelper.OptionItem(\"75% Compact (Recommended)\", \"Sleek PC look, removes bulky elements\", 0.75f),
            PcDialogHelper.OptionItem(\"85% Standard PC\", \"Balanced PC desktop scaling\", 0.85f),
            PcDialogHelper.OptionItem(\"100% Large\", \"Medium-large desktop scale\", 1.00f),
            PcDialogHelper.OptionItem(\"115% Extra Large\", \"For very distant viewing\", 1.15f)
        )
        val selectedIdx = when {
            currentConfig.pcUiScale <= 0.70f -> 0
            currentConfig.pcUiScale <= 0.80f -> 1
            currentConfig.pcUiScale <= 0.92f -> 2
            currentConfig.pcUiScale <= 1.08f -> 3
            else -> 4
        }
        PcDialogHelper.showOptionsPickerDialog(
            context = requireContext(),
            title = \"PC UI Scale (DPI)\",
            subtitle = \"Adjust overall desktop and toolbar scaling\",
            options = options,
            selectedIndex = selectedIdx,
            onSelect = { opt ->
                val scale = opt.tag as Float
                viewLifecycleOwner.lifecycleScope.launch {
                    ConfigStore(requireContext()).update { it.copy(pcUiScale = scale) }
                }
            }
        )
    }

    // Dialog: Desktop Icon Size Picker
    private fun showIconSizePickerDialog() {
        val options = listOf(
            PcDialogHelper.OptionItem(\"Small (32dp)\", \"Compact sleek desktop icons\", 32),
            PcDialogHelper.OptionItem(\"Medium (40dp)\", \"Standard desktop view\", 40),
            PcDialogHelper.OptionItem(\"Large (48dp)\", \"Spacious easy-to-tap view\", 48)
        )
        val selectedIdx = when (currentConfig.pcIconSize) {
            in 0..35 -> 0
            in 36..44 -> 1
            else -> 2
        }
        PcDialogHelper.showOptionsPickerDialog(
            context = requireContext(),
            title = \"Desktop Icon Size\",
            subtitle = \"Choose app icon size on desktop\",
            options = options,
            selectedIndex = selectedIdx,
            onSelect = { opt ->
                val size = opt.tag as Int
                viewLifecycleOwner.lifecycleScope.launch {
                    ConfigStore(requireContext()).update { it.copy(pcIconSize = size) }
                }
            }
        )
    }

    // Dialog: Desktop Spacing Picker
    private fun showSpacingPickerDialog() {
        val options = listOf(
            PcDialogHelper.OptionItem(\"Tight Spacing (6dp)\", \"Compact icon placement\", 6),
            PcDialogHelper.OptionItem(\"Normal Spacing (10dp)\", \"Balanced desktop grid\", 10),
            PcDialogHelper.OptionItem(\"Spacious Spacing (16dp)\", \"Wide margins between icons\", 16)
        )
        val selectedIdx = when (currentConfig.pcGridSpacing) {
            in 0..7 -> 0
            in 8..12 -> 1
            else -> 2
        }
        PcDialogHelper.showOptionsPickerDialog(
            context = requireContext(),
            title = \"Grid Spacing\",
            subtitle = \"Choose spacing between desktop icons\",
            options = options,
            selectedIndex = selectedIdx,
            onSelect = { opt ->
                val spacing = opt.tag as Int
                viewLifecycleOwner.lifecycleScope.launch {
                    ConfigStore(requireContext()).update { it.copy(pcGridSpacing = spacing) }
                }
            }
        )
    }

    // Dialog: Sort Picker
    private fun showSortPickerDialog() {
        val options = listOf(
            PcDialogHelper.OptionItem(\"Custom (Drag & Drop)\", \"Arranged manually by user\", 0),
            PcDialogHelper.OptionItem(\"Name (A to Z)\", \"Alphabetical sort\", 1)
        )
        PcDialogHelper.showOptionsPickerDialog(
            context = requireContext(),
            title = \"Sort Desktop Icons\",
            subtitle = \"Choose how apps are arranged\",
            options = options,
            selectedIndex = currentConfig.pcSortOrder,
            onSelect = { opt ->
                val sort = opt.tag as Int
                viewLifecycleOwner.lifecycleScope.launch {
                    ConfigStore(requireContext()).update { it.copy(pcSortOrder = sort) }
                }
            }
        )
    }

    // Dialog: Taskbar Settings
    private fun showTaskbarSettingsDialog() {
        val options = listOf(
            PcDialogHelper.OptionItem(\"Slim Toolbar (34dp)\", \"Minimal height, maximizes screen\", 34 to currentConfig.pcTaskbarCenter),
            PcDialogHelper.OptionItem(\"Compact Toolbar (38dp)\", \"Sleek modern PC taskbar\", 38 to currentConfig.pcTaskbarCenter),
            PcDialogHelper.OptionItem(\"Standard Toolbar (44dp)\", \"Standard taskbar height\", 44 to currentConfig.pcTaskbarCenter),
            PcDialogHelper.OptionItem(\"Center Aligned (Windows 11)\", \"Centered taskbar apps\", currentConfig.pcTaskbarHeight to true),
            PcDialogHelper.OptionItem(\"Left Aligned (Classic)\", \"Left-aligned taskbar apps\", currentConfig.pcTaskbarHeight to false)
        )
        val selectedIdx = when {
            currentConfig.pcTaskbarHeight <= 35 -> 0
            currentConfig.pcTaskbarHeight <= 40 -> 1
            else -> 2
        }
        PcDialogHelper.showOptionsPickerDialog(
            context = requireContext(),
            title = \"Taskbar Settings\",
            subtitle = \"Customize bottom toolbar height & alignment\",
            options = options,
            selectedIndex = selectedIdx,
            onSelect = { opt ->
                val pair = opt.tag as? Pair<*, *>
                val height = (pair?.first as? Int) ?: currentConfig.pcTaskbarHeight
                val center = (pair?.second as? Boolean) ?: currentConfig.pcTaskbarCenter
                viewLifecycleOwner.lifecycleScope.launch {
                    ConfigStore(requireContext()).update {
                        it.copy(pcTaskbarHeight = height, pcTaskbarCenter = center)
                    }
                }
            }
        )
    }

    /**
     * WINDOWED LAUNCH LOGIC (Windows/Linux style):
     * - If windowing disabled: direct launch (old behavior)
     * - If windowing enabled: creates a window card with titlebar (min/max/close)
     *   Window card has: icon + title + minimize/maximize/close buttons
     *   Taskbar shows running windows like Windows
     * - If freeform enabled + device supports: tries real Android freeform window
     *   On Samsung DeX, Android 12L+, ChromeOS etc, gives real floating window
     */
    private fun handleAppLaunch(app: AppEntry, skipLock: Boolean = false) {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        if (!skipLock && currentConfig.appLock.enabled && currentConfig.appLock.value.isNotEmpty() && app.pkg in currentConfig.lockedApps) {
            PinEntryDialogFragment.newInstance(
                title = "App Locked",
                subtitle = "Enter PIN/Password to launch ${app.label}",
                credential = currentConfig.appLock,
                isCancelable = true,
                onSuccess = {
                    com.gothwad.launcher.service.LauncherAccessibilityService.unlockedPackagesSession.add(app.pkg)
                    handleAppLaunch(app, skipLock = true)
                }
            ).show(parentFragmentManager, PinEntryDialogFragment.TAG)
        } else {
            if (!currentConfig.pcWindowingEnabled) {
                Actions.launchApp(requireContext(), app.pkg)
                return
            }

            val displayMetrics = resources.displayMetrics
            val taskbarHeight = binding.layoutTaskbar.height.takeIf { it > 0 } ?: (44 * displayMetrics.density).toInt()

            val win = PcWindowManager.openWindow(
                app = app,
                screenWidth = displayMetrics.widthPixels,
                screenHeight = displayMetrics.heightPixels,
                taskbarHeight = taskbarHeight
            )

            if (currentConfig.pcFreeformEnabled) {
                val launched = Actions.launchAppInWindowedMode(requireContext(), app.pkg)
                if (launched) {
                    PcWindowManager.minimizeWindow(win.id)
                }
            }
        }
    }

    /**
     * Direct launch without windowing (used for Start Menu search etc if needed)
     */
    private fun handleAppLaunchDirect(app: AppEntry) {
        Actions.launchApp(requireContext(), app.pkg)
        // Also create window tracking
        val displayMetrics = resources.displayMetrics
        val taskbarHeight = binding.layoutTaskbar.height.takeIf { it > 0 } ?: (44 * displayMetrics.density).toInt()
        PcWindowManager.openWindow(app, displayMetrics.widthPixels, displayMetrics.heightPixels, taskbarHeight)
    }

    private fun openAppDetails(pkg: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts(\"package\", pkg, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun uninstallApp(pkg: String) {
        try {
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.fromParts(\"package\", pkg, null)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun openSearchDialog() {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        SearchDialogFragment.newInstance(
            apps = allApps,
            config = currentConfig,
            onLaunch = { app -> handleAppLaunch(app) }
        ).show(parentFragmentManager, SearchDialogFragment.TAG)
    }

    private fun openFileManager() {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        // Check storage permission
        try {
            PcFileManagerDialogFragment.newInstance()
                .show(parentFragmentManager, PcFileManagerDialogFragment.TAG)
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), "File Manager: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFullSettingsDialog() {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        SettingsBottomSheetFragment.newInstance(
            config = currentConfig,
            apps = allApps,
            onWallpaperChanged = { applyWallpaper() },
            onRerunWizard = { showSetupWizard() },
            onModeSelected = { /* handled by ConfigStore */ }
        ).show(parentFragmentManager, SettingsBottomSheetFragment.TAG)
    }

    private fun showSetupWizard() {
        SetupWizardDialogFragment.newInstance {
            viewLifecycleOwner.lifecycleScope.launch {
                ConfigStore(requireContext()).update { it.copy(setupDone = true) }
            }
        }.show(parentFragmentManager, SetupWizardDialogFragment.TAG)
    }

    private fun observeData() {
        val configStore = ConfigStore(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    configStore.flow.collectLatest { config ->
                        val prevConfig = currentConfig
                        currentConfig = config
                        applyWallpaper()
                        updateGridDimensions()
                        updateVisibleApps()
                        updatePinnedApps()

                        // Sync DPI prefs for independence (so GothwadApplication can read it in attachBaseContext next launch)
                        try {
                            val dpiPrefs = requireContext().getSharedPreferences("launcher_dpi_prefs", android.content.Context.MODE_PRIVATE)
                            dpiPrefs.edit()
                                .putBoolean("dpi_independent", config.pcDpiIndependent)
                                .putInt("fixed_dpi", config.pcFixedDpi)
                                .apply()
                        } catch (_: Exception) {}

                        // Handle taskbar overlay service (taskbar over apps like Windows)
                        try {
                            if (config.pcTaskbarOverlayEnabled) {
                                if (!PcTaskbarOverlayService.isRunning) {
                                    // Check overlay permission
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        if (android.provider.Settings.canDrawOverlays(requireContext())) {
                                            PcTaskbarOverlayService.start(requireContext())
                                        }
                                    } else {
                                        PcTaskbarOverlayService.start(requireContext())
                                    }
                                }
                            } else {
                                if (PcTaskbarOverlayService.isRunning) {
                                    PcTaskbarOverlayService.stop(requireContext())
                                }
                            }
                        } catch (_: Exception) {}

                        if (prevConfig.pcUiScale != config.pcUiScale ||
                            prevConfig.pcIconSize != config.pcIconSize ||
                            prevConfig.pcShowLabels != config.pcShowLabels ||
                            prevConfig.pcGridSpacing != config.pcGridSpacing ||
                            prevConfig.pcTaskbarHeight != config.pcTaskbarHeight ||
                            prevConfig.pcTaskbarCenter != config.pcTaskbarCenter ||
                            prevConfig.pcDpiIndependent != config.pcDpiIndependent ||
                            prevConfig.pcFixedDpi != config.pcFixedDpi) {
                            updateGridDimensions()
                        }
                    }
                }

                launch {
                    loadApps()
                }

                // Network flow for tray icon
                launch {
                    networkStatusFlow(requireContext()).collectLatest { net ->
                        val iconRes = if (net.wifi) AppIcons.PATH_WIFI else if (net.ethernet) AppIcons.PATH_ETHERNET else AppIcons.PATH_WIFI_OFF
                        binding.imgTrayNetwork.setImageDrawable(AppIcons.createDrawable(iconRes, Color.WHITE))
                        binding.viewQuickSettings.imgQsWifi.setImageDrawable(AppIcons.createDrawable(iconRes, 0xFF4FA7FA.toInt()))
                        binding.viewQuickSettings.tvQsNetwork.text = if (net.connected) (if (net.ssid.isNotEmpty()) net.ssid else \"Connected\") else \"Disconnected\"
                    }
                }

                // Notifications flow for taskbar badge
                launch {
                    NotificationManagerBridge.notifications.collectLatest { notifs ->
                        if (notifs.isNotEmpty()) {
                            binding.tvNotifBadge.text = notifs.size.toString()
                            binding.tvNotifBadge.visibility = View.VISIBLE
                            binding.viewQuickSettings.tvTileNotifCount.text = \"${notifs.size} New\"
                        } else {
                            binding.tvNotifBadge.visibility = View.GONE
                            binding.viewQuickSettings.tvTileNotifCount.text = \"None\"
                        }
                    }
                }
            }
        }
    }

    private fun applyWallpaper() {
        lifecycleScope.launch {
            if (currentConfig.useCustomWallpaper) {
                val file = File(requireContext().filesDir, \"wallpaper.jpg\")
                if (file.exists()) {
                    val bmp = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    }
                    if (bmp != null) {
                        binding.imgWallpaper.setImageBitmap(bmp)
                        binding.imgWallpaper.visibility = View.VISIBLE
                    }
                }
            } else {
                val preset = WALLPAPERS.getOrElse(currentConfig.wallpaper.coerceIn(0, WALLPAPERS.size - 1)) { WALLPAPERS[0] }
                val colors = preset.colors.toIntArray()

                val gradient = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
                binding.imgWallpaper.setImageDrawable(gradient)
                binding.imgWallpaper.visibility = View.VISIBLE
            }
        }
    }

    private fun startClockUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val now = Date()
                    val timePattern = if (currentConfig.h24) \"HH:mm\" else \"hh:mm a\"
                    val timeStr = SimpleDateFormat(timePattern, Locale.ENGLISH).format(now)
                    val dateStr = SimpleDateFormat(\"d MMM • EEE\", Locale.ENGLISH).format(now)

                    binding.tvTaskbarTime.text = timeStr
                    binding.tvTaskbarDate.text = dateStr
                    delay(1000)
                }
            }
        }
    }

    fun onRescanRequested() {
        viewLifecycleOwner.lifecycleScope.launch {
            loadApps()
        }
    }

    private suspend fun loadApps() {
        allApps = AppRepository.scan(requireContext())
        updateVisibleApps()
        updatePinnedApps()
        startMenuAdapter?.submitList(allApps.filter { it.pkg !in currentConfig.hidden })
    }

    private fun updateVisibleApps() {
        if (allApps.isEmpty()) return
        val visible = allApps.filter { it.pkg !in currentConfig.hidden }

        val sorted = if (currentConfig.pcSortOrder == 1) {
            visible.sortedBy { (currentConfig.pcCustomLabels[it.pkg] ?: it.label).lowercase() }
        } else if (currentConfig.pcDesktopOrder.isNotEmpty()) {
            val orderMap = currentConfig.pcDesktopOrder.withIndex().associate { it.value to it.index }
            visible.sortedBy { orderMap[it.pkg] ?: Int.MAX_VALUE }
        } else {
            visible
        }

        desktopAdapter?.updateData(sorted, currentConfig)
    }

    private fun updatePinnedApps() {
        if (allApps.isEmpty()) return
        val pinned = if (currentConfig.pcPinnedApps.isNotEmpty()) {
            currentConfig.pcPinnedApps.mapNotNull { pkg -> allApps.find { it.pkg == pkg } }
        } else {
            // Default top 4 apps pinned
            allApps.take(4)
        }
        pinnedAdapter?.submitList(pinned)
    }

    override fun onDestroyView() {
        requireContext().unregisterReceiver(batteryReceiver)
        activePopupWindow?.dismiss()
        activePopupWindow = null
        super.onDestroyView()
        _binding = null
    }
}
