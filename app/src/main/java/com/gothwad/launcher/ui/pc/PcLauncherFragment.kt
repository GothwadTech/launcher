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
import com.gothwad.launcher.R
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
import com.gothwad.launcher.ui.AppIcons
import com.gothwad.launcher.ui.WALLPAPERS
import com.gothwad.launcher.ui.dialogs.NotificationBottomSheetFragment
import com.gothwad.launcher.ui.dialogs.PinEntryDialogFragment
import com.gothwad.launcher.ui.dialogs.SearchDialogFragment
import com.gothwad.launcher.ui.dialogs.SettingsBottomSheetFragment
import com.gothwad.launcher.ui.dialogs.SetupWizardDialogFragment
import com.gothwad.launcher.apps.files.FileManagerView
import com.gothwad.launcher.apps.floating.FloatingWindowManager
import com.gothwad.launcher.apps.webapp.WebAppView
import com.gothwad.launcher.service.FloatingTaskbarService
import com.gothwad.launcher.ui.view.SmoothOutlineProvider
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
    private var startMenuAdapter: PcStartMenuAdapter? = null

    private var currentConfig: LauncherConfig = LauncherConfig()
    private var allApps: List<AppEntry> = emptyList()

    private var itemTouchHelper: ItemTouchHelper? = null
    private var activePopupWindow: PopupWindow? = null
    private var floatingWindowManager: FloatingWindowManager? = null
    private var isTaskbarUserHidden: Boolean = false

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

    private fun setupTaskbar() {
        // Base taskbar buttons - Use app launcher icon for the Start / Home button
        val appIconDrawable = runCatching {
            requireContext().packageManager.getApplicationIcon(requireContext().packageName)
        }.getOrNull() ?: androidx.core.content.ContextCompat.getDrawable(requireContext(), R.mipmap.ic_launcher)

        val density = resources.displayMetrics.density
        val squircleRadius = 7.5f * density
        binding.btnStart.outlineProvider = SmoothOutlineProvider(cornerRadiusPx = squircleRadius, smoothing = 0.6f)
        binding.btnStart.clipToOutline = true
        binding.btnStart.setImageDrawable(appIconDrawable)

        binding.btnTaskbarFiles.outlineProvider = SmoothOutlineProvider(cornerRadiusPx = squircleRadius, smoothing = 0.6f)
        binding.btnTaskbarFiles.clipToOutline = true

        binding.btnTaskbarBrowser.outlineProvider = SmoothOutlineProvider(cornerRadiusPx = squircleRadius, smoothing = 0.6f)
        binding.btnTaskbarBrowser.clipToOutline = true

        binding.btnTaskbarCornerLauncher.outlineProvider = SmoothOutlineProvider(cornerRadiusPx = squircleRadius, smoothing = 0.6f)
        binding.btnTaskbarCornerLauncher.clipToOutline = true
        binding.imgCornerLauncherIcon.setImageDrawable(appIconDrawable)

        binding.btnFloatingCornerTab.outlineProvider = SmoothOutlineProvider(cornerRadiusPx = 9f * density, smoothing = 0.6f)
        binding.btnFloatingCornerTab.clipToOutline = true
        binding.imgFloatingCornerIcon.setImageDrawable(appIconDrawable)

        binding.imgTaskbarSearchIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0xCC9AA0A6.toInt()))
        binding.btnNotifications.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, Color.WHITE))
        binding.imgTrayVolume.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_VOLUME, 0xFFCCCCCC.toInt()))
        binding.imgTrayNetwork.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI, 0xFFCCCCCC.toInt()))

        // Start Menu Toggle
        binding.btnStart.setOnClickListener {
            toggleStartMenu()
        }

        // Search Button
        binding.btnSearch.setOnClickListener {
            closeAllFlyouts()
            openSearchDialog()
        }

        // File Manager Direct Taskbar Shortcut
        binding.btnTaskbarFiles.setOnClickListener {
            closeAllFlyouts()
            openFileManagerWindow()
        }

        // Web Browser Direct Taskbar Shortcut
        binding.btnTaskbarBrowser.setOnClickListener {
            closeAllFlyouts()
            openWebAppWindow()
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

        // Show Desktop Peek Button
        binding.btnShowDesktop.setOnClickListener {
            closeAllFlyouts()
            // Minimize all open floating windows if any, or restore
            floatingWindowManager?.let { fwm ->
                val openWins = fwm.getActiveWindows()
                if (openWins.any { !it.isMinimized }) {
                    openWins.forEach { if (!it.isMinimized) fwm.minimizeWindow(it.id) }
                } else if (openWins.isNotEmpty()) {
                    openWins.forEach { if (it.isMinimized) fwm.restoreWindow(it.id) }
                }
            }
            binding.recyclerDesktopGrid.smoothScrollToPosition(0)
        }

        // Right Corner Launcher Icon: Dual launcher icon for toggling taskbar / quick controls
        binding.btnTaskbarCornerLauncher.setOnClickListener {
            toggleTaskbarVisibility()
        }

        // Floating Corner Tab (when taskbar is hidden): tap to reveal full taskbar
        binding.btnFloatingCornerTab.setOnClickListener {
            toggleTaskbarVisibility()
        }

        // Initialize Floating Window Manager & Taskbar window chips
        floatingWindowManager = FloatingWindowManager(
            context = requireContext(),
            windowContainer = binding.containerFloatingWindows,
            taskbarChipsRecycler = binding.recyclerTaskbarWindowChips
        )
        floatingWindowManager?.onTaskbarChanged = { hasWindows ->
            binding.viewSepActive.visibility = if (hasWindows) View.VISIBLE else View.GONE
        }
        binding.recyclerTaskbarWindowChips.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )

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
        startBinding.btnStartTvMode.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_TV, 0xFF60A5FA.toInt()))
        startBinding.btnStartSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, Color.WHITE))

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

    private fun toggleTaskbarVisibility() {
        isTaskbarUserHidden = !isTaskbarUserHidden
        closeAllFlyouts()
        if (isTaskbarUserHidden) {
            // Animate taskbar sliding down and out of sight
            binding.layoutTaskbar.animate()
                .translationY(binding.layoutTaskbar.height.toFloat().coerceAtLeast(100f))
                .alpha(0f)
                .setDuration(180)
                .withEndAction {
                    if (_binding != null) {
                        binding.layoutTaskbar.visibility = View.GONE
                        binding.btnFloatingCornerTab.alpha = 0f
                        binding.btnFloatingCornerTab.visibility = View.VISIBLE
                        binding.btnFloatingCornerTab.animate().alpha(1f).setDuration(160).start()
                    }
                }
                .start()
        } else {
            // Hide floating corner tab and restore taskbar
            binding.btnFloatingCornerTab.visibility = View.GONE
            binding.layoutTaskbar.visibility = View.VISIBLE
            binding.layoutTaskbar.translationY = binding.layoutTaskbar.height.toFloat().coerceAtLeast(100f)
            binding.layoutTaskbar.alpha = 0f
            binding.layoutTaskbar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(180)
                .start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
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
        val density = resources.displayMetrics.density
        val displayMetrics = resources.displayMetrics
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

        // Adjust Taskbar margin on recycler
        val gridLp = binding.recyclerDesktopGrid.layoutParams as ViewGroup.MarginLayoutParams
        gridLp.bottomMargin = taskbarHeightPx
        binding.recyclerDesktopGrid.layoutParams = gridLp

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
        val btnSizePx = (taskbarHeightPx - (6 * density)).toInt().coerceIn((30 * density).toInt(), (44 * density).toInt())
        val btnPadPx = (1.5f * density).toInt()

        val startLp = binding.btnStart.layoutParams
        startLp.width = btnSizePx
        startLp.height = btnSizePx
        binding.btnStart.layoutParams = startLp
        binding.btnStart.setPadding(btnPadPx, btnPadPx, btnPadPx, btnPadPx)

        // Core app shortcuts (Files and Browser)
        val filesLp = binding.btnTaskbarFiles.layoutParams
        filesLp.width = btnSizePx
        filesLp.height = btnSizePx
        binding.btnTaskbarFiles.layoutParams = filesLp
        binding.btnTaskbarFiles.setPadding(btnPadPx, btnPadPx, btnPadPx, btnPadPx)

        val browserLp = binding.btnTaskbarBrowser.layoutParams
        browserLp.width = btnSizePx
        browserLp.height = btnSizePx
        binding.btnTaskbarBrowser.layoutParams = browserLp
        binding.btnTaskbarBrowser.setPadding(btnPadPx, btnPadPx, btnPadPx, btnPadPx)

        // Corner launcher button
        val cornerLp = binding.btnTaskbarCornerLauncher.layoutParams
        cornerLp.width = btnSizePx
        cornerLp.height = btnSizePx
        binding.btnTaskbarCornerLauncher.layoutParams = cornerLp

        // Adjust Taskbar search bar size - takes ~20-25% space with Launcher Icon
        val searchLp = binding.btnSearch.layoutParams
        searchLp.height = (btnSizePx * 0.88f).toInt()
        val screenWidthPx = resources.displayMetrics.widthPixels
        val searchSectionWidthPx = (screenWidthPx * 0.22f).coerceIn(160f * density, 300f * density).toInt()
        val searchWidthPx = (searchSectionWidthPx - btnSizePx - (12 * density)).toInt().coerceAtLeast((120 * density).toInt())
        searchLp.width = searchWidthPx
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

        // Update pinned adapter sizes to fit taskbar perfectly with square curve icons
        val pinnedItemSizePx = btnSizePx
        val pinnedIconSizePx = (btnSizePx * 0.82f).toInt()
        pinnedAdapter?.updateSizes(pinnedItemSizePx, pinnedIconSizePx)
    }

    private fun saveCurrentDesktopOrder() {
        val currentItems = desktopAdapter?.getCurrentList() ?: return
        val newOrder = currentItems.map { it.pkg }
        viewLifecycleOwner.lifecycleScope.launch {
            ConfigStore(requireContext()).update { it.copy(pcDesktopOrder = newOrder, pcSortOrder = 0) }
        }
    }

    // Windows / Linux style Desktop Context Menu
    private fun showDesktopContextMenu(touchX: Float, touchY: Float) {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        val context = requireContext()
        val inflater = LayoutInflater.from(context)
        val menuBinding = LayoutPcContextMenuBinding.inflate(inflater)

        menuBinding.imgMenuHeaderIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DESKTOP, 0xFF4FA7FA.toInt()))
        menuBinding.imgIconScale.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DISPLAY, Color.WHITE))
        menuBinding.imgIconSize.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE))
        menuBinding.imgIconSpacing.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_MOVE, Color.WHITE))
        menuBinding.imgIconLabels.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PENCIL, Color.WHITE))
        menuBinding.imgIconSort.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DOWN, Color.WHITE))
        menuBinding.imgIconTaskbar.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_STORAGE, Color.WHITE))
        menuBinding.imgIconRefresh.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_REFRESH, Color.WHITE))
        menuBinding.imgIconFileMgr.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_FOLDER, 0xFF4FA7FA.toInt()))
        menuBinding.imgIconWebApp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GLOBE, 0xFF60A5FA.toInt()))
        menuBinding.imgIconPersonalize.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PALETTE, Color.WHITE))
        menuBinding.imgIconSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, Color.WHITE))

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
            menuBinding.imgCheckLabels.setImageDrawable(null)
            menuBinding.imgCheckLabels.visibility = View.INVISIBLE
        }

        // Sort state
        menuBinding.tvCurrentSortBadge.text = if (currentConfig.pcSortOrder == 1) "A-Z" else "Custom"

        val popup = PopupWindow(
            menuBinding.root,
            (250 * resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        activePopupWindow = popup

        // 1. PC UI Scale Option
        menuBinding.itemPcUiScale.setOnClickListener {
            popup.dismiss()
            showScalePickerDialog()
        }

        // 2. Icon Size Option
        menuBinding.itemIconSize.setOnClickListener {
            popup.dismiss()
            showIconSizePickerDialog()
        }

        // 3. Spacing Option
        menuBinding.itemDesktopSpacing.setOnClickListener {
            popup.dismiss()
            showSpacingPickerDialog()
        }

        // 4. Toggle Labels Option
        menuBinding.itemToggleLabels.setOnClickListener {
            popup.dismiss()
            viewLifecycleOwner.lifecycleScope.launch {
                ConfigStore(context).update { it.copy(pcShowLabels = !it.pcShowLabels) }
            }
        }

        // 5. Sort Icons Option
        menuBinding.itemSortDesktop.setOnClickListener {
            popup.dismiss()
            showSortPickerDialog()
        }

        // 6. Taskbar Settings Option
        menuBinding.itemTaskbarSettings.setOnClickListener {
            popup.dismiss()
            showTaskbarSettingsDialog()
        }

        // 7. Refresh Desktop Option
        menuBinding.itemRefreshDesktop.setOnClickListener {
            popup.dismiss()
            viewLifecycleOwner.lifecycleScope.launch {
                loadApps()
            }
        }

        // Open File Manager Window
        menuBinding.itemOpenFileManager.setOnClickListener {
            popup.dismiss()
            openFileManagerWindow()
        }

        // Open Web App Browser Window
        menuBinding.itemOpenWebApp.setOnClickListener {
            popup.dismiss()
            openWebAppWindow()
        }

        // 8. Wallpaper / Personalize Option
        menuBinding.itemPersonalize.setOnClickListener {
            popup.dismiss()
            openFullSettingsDialog()
        }

        // 9. Full Settings Option
        menuBinding.itemOpenSettings.setOnClickListener {
            popup.dismiss()
            openFullSettingsDialog()
        }

        showPopupAtSafeCoords(popup, touchX, touchY, (260 * resources.displayMetrics.density).toInt(), (360 * resources.displayMetrics.density).toInt())
    }

    // Windows / Linux style App Icon Context Menu
    private fun showAppContextMenu(app: AppEntry, view: View, touchX: Float, touchY: Float) {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        val context = requireContext()
        val menuBinding = LayoutPcAppContextMenuBinding.inflate(LayoutInflater.from(context))

        val displayName = currentConfig.pcCustomLabels[app.pkg] ?: app.label
        menuBinding.tvAppHeaderLabel.text = displayName

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

        menuBinding.imgActionRename.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PENCIL, Color.WHITE))
        menuBinding.imgActionMoveUp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_UP, Color.WHITE))
        menuBinding.imgActionMoveDown.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DOWN, Color.WHITE))
        menuBinding.imgActionHide.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HIDE, Color.WHITE))
        menuBinding.imgActionInfo.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_INFO, Color.WHITE))
        menuBinding.imgActionUninstall.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DELETE, 0xFFFF6B6B.toInt()))

        val popup = PopupWindow(
            menuBinding.root,
            (220 * resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 16f
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

        showPopupAtSafeCoords(popup, touchX, touchY, (220 * resources.displayMetrics.density).toInt(), (320 * resources.displayMetrics.density).toInt())
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
            PcDialogHelper.OptionItem("65% Ultra Compact", "Smallest DPI, for high-density PC setups", 0.65f),
            PcDialogHelper.OptionItem("75% Compact (Recommended)", "Sleek PC look, removes bulky elements", 0.75f),
            PcDialogHelper.OptionItem("85% Standard PC", "Balanced PC desktop scaling", 0.85f),
            PcDialogHelper.OptionItem("100% Large", "Medium-large desktop scale", 1.00f),
            PcDialogHelper.OptionItem("115% Extra Large", "For very distant viewing", 1.15f)
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
            title = "PC UI Scale (DPI)",
            subtitle = "Adjust overall desktop and toolbar scaling",
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
            PcDialogHelper.OptionItem("Small (32dp)", "Compact sleek desktop icons", 32),
            PcDialogHelper.OptionItem("Medium (40dp)", "Standard desktop view", 40),
            PcDialogHelper.OptionItem("Large (48dp)", "Spacious easy-to-tap view", 48)
        )
        val selectedIdx = when (currentConfig.pcIconSize) {
            in 0..35 -> 0
            in 36..44 -> 1
            else -> 2
        }
        PcDialogHelper.showOptionsPickerDialog(
            context = requireContext(),
            title = "Desktop Icon Size",
            subtitle = "Choose app icon size on desktop",
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
            PcDialogHelper.OptionItem("Tight Spacing (6dp)", "Compact icon placement", 6),
            PcDialogHelper.OptionItem("Normal Spacing (10dp)", "Balanced desktop grid", 10),
            PcDialogHelper.OptionItem("Spacious Spacing (16dp)", "Wide margins between icons", 16)
        )
        val selectedIdx = when (currentConfig.pcGridSpacing) {
            in 0..7 -> 0
            in 8..12 -> 1
            else -> 2
        }
        PcDialogHelper.showOptionsPickerDialog(
            context = requireContext(),
            title = "Grid Spacing",
            subtitle = "Choose spacing between desktop icons",
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
            PcDialogHelper.OptionItem("Custom (Drag & Drop)", "Arranged manually by user", 0),
            PcDialogHelper.OptionItem("Name (A to Z)", "Alphabetical sort", 1)
        )
        PcDialogHelper.showOptionsPickerDialog(
            context = requireContext(),
            title = "Sort Desktop Icons",
            subtitle = "Choose how apps are arranged",
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
            PcDialogHelper.OptionItem("Slim Toolbar (34dp)", "Minimal height, maximizes screen", 34 to currentConfig.pcTaskbarCenter),
            PcDialogHelper.OptionItem("Compact Toolbar (38dp)", "Sleek modern PC taskbar", 38 to currentConfig.pcTaskbarCenter),
            PcDialogHelper.OptionItem("Standard Toolbar (44dp)", "Standard taskbar height", 44 to currentConfig.pcTaskbarCenter),
            PcDialogHelper.OptionItem("Center Aligned (Windows 11)", "Centered taskbar apps", currentConfig.pcTaskbarHeight to true),
            PcDialogHelper.OptionItem("Left Aligned (Classic)", "Left-aligned taskbar apps", currentConfig.pcTaskbarHeight to false),
            PcDialogHelper.OptionItem("Floating Quick Assist Icon", "Show over other apps (System Overlay)", "OVERLAY")
        )
        val selectedIdx = when {
            currentConfig.pcTaskbarHeight <= 35 -> 0
            currentConfig.pcTaskbarHeight <= 40 -> 1
            else -> 2
        }
        PcDialogHelper.showOptionsPickerDialog(
            context = requireContext(),
            title = "Taskbar Settings",
            subtitle = "Customize bottom toolbar height & alignment",
            options = options,
            selectedIndex = selectedIdx,
            onSelect = { opt ->
                if (opt.tag == "OVERLAY") {
                    if (!FloatingTaskbarService.canDrawOverlays(requireContext())) {
                        FloatingTaskbarService.requestOverlayPermission(requireContext())
                    } else {
                        viewLifecycleOwner.lifecycleScope.launch {
                            ConfigStore(requireContext()).update {
                                it.copy(pcOverlayTaskbarEnabled = !it.pcOverlayTaskbarEnabled)
                            }
                        }
                    }
                    return@showOptionsPickerDialog
                }
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

    private fun handleAppLaunch(app: AppEntry, skipLock: Boolean = false) {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }

        // Intercept dedicated built-in apps for floating window display
        if (app.pkg == "com.gothwad.launcher.files") {
            openFileManagerWindow()
            return
        }
        if (app.pkg == "com.gothwad.launcher.webapp" || app.pkg.startsWith("pwa://")) {
            val url = if (app.pkg.startsWith("pwa://")) app.pkg.removePrefix("pwa://") else "https://www.google.com"
            openWebAppWindow(initialUrl = url, title = app.label)
            return
        }

        // UX-only in-launcher check to avoid overlay flicker on first click.
        // The authoritative, unbypassable security enforcement layer is in LauncherAccessibilityService.
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
            Actions.launchApp(requireContext(), app.pkg)
        }
    }

    fun openFileManagerWindow() {
        val fwm = floatingWindowManager ?: return
        val fileView = FileManagerView(requireContext())
        fwm.openWindow(
            id = "app_files",
            title = "File Manager",
            iconDrawable = AppIcons.createDrawable(AppIcons.PATH_FOLDER, 0xFF4FA7FA.toInt()),
            contentView = fileView.getView(),
            defaultWidthDp = 700,
            defaultHeightDp = 460
        )
    }

    fun openWebAppWindow(initialUrl: String = "https://www.google.com", title: String = "Web Browser") {
        val fwm = floatingWindowManager ?: return
        var webAppView: WebAppView? = null
        webAppView = WebAppView(
            context = requireContext(),
            initialUrl = initialUrl,
            onPinShortcut = { siteTitle, siteUrl, iconBmp ->
                // Pin shortcut to desktop by adding to desktop custom list
                viewLifecycleOwner.lifecycleScope.launch {
                    val shortcutPkg = "pwa://$siteUrl"
                    ConfigStore(requireContext()).update { cfg ->
                        val updatedDesktopOrder = cfg.pcDesktopOrder.toMutableList().apply {
                            if (!contains(shortcutPkg)) add(shortcutPkg)
                        }
                        val updatedLabels = cfg.pcCustomLabels.toMutableMap().apply {
                            put(shortcutPkg, siteTitle)
                        }
                        val updatedPinned = cfg.pcPinnedApps.toMutableList().apply {
                            if (!contains(shortcutPkg)) add(shortcutPkg)
                        }
                        cfg.copy(
                            pcDesktopOrder = updatedDesktopOrder,
                            pcCustomLabels = updatedLabels,
                            pcPinnedApps = updatedPinned
                        )
                    }
                    loadApps()
                }
            }
        )

        fwm.openWindow(
            id = "app_web_${System.currentTimeMillis() % 10000}",
            title = title,
            iconDrawable = AppIcons.createDrawable(AppIcons.PATH_GLOBE, 0xFF60A5FA.toInt()),
            contentView = webAppView.getView(),
            defaultWidthDp = 740,
            defaultHeightDp = 500,
            onClose = {
                webAppView?.destroy()
            }
        )
    }

    private fun openAppDetails(pkg: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun uninstallApp(pkg: String) {
        try {
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.fromParts("package", pkg, null)
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

                        if (prevConfig.pcUiScale != config.pcUiScale ||
                            prevConfig.pcIconSize != config.pcIconSize ||
                            prevConfig.pcShowLabels != config.pcShowLabels ||
                            prevConfig.pcGridSpacing != config.pcGridSpacing ||
                            prevConfig.pcTaskbarHeight != config.pcTaskbarHeight ||
                            prevConfig.pcTaskbarCenter != config.pcTaskbarCenter) {
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
                        binding.viewQuickSettings.tvQsNetwork.text = if (net.connected) (if (net.ssid.isNotEmpty()) net.ssid else "Connected") else "Disconnected"
                    }
                }

                // Notifications flow for taskbar badge
                launch {
                    NotificationManagerBridge.notifications.collectLatest { notifs ->
                        if (notifs.isNotEmpty()) {
                            binding.tvNotifBadge.text = notifs.size.toString()
                            binding.tvNotifBadge.visibility = View.VISIBLE
                            binding.viewQuickSettings.tvTileNotifCount.text = "${notifs.size} New"
                        } else {
                            binding.tvNotifBadge.visibility = View.GONE
                            binding.viewQuickSettings.tvTileNotifCount.text = "None"
                        }
                    }
                }
            }
        }
    }

    private fun applyWallpaper() {
        lifecycleScope.launch {
            if (currentConfig.useCustomWallpaper) {
                val file = File(requireContext().filesDir, "wallpaper.jpg")
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
                    val timePattern = if (currentConfig.h24) "HH:mm" else "hh:mm a"
                    val timeStr = SimpleDateFormat(timePattern, Locale.ENGLISH).format(now)
                    val dateStr = SimpleDateFormat("d MMM • EEE", Locale.ENGLISH).format(now)

                    binding.tvTaskbarTime.text = timeStr
                    binding.tvTaskbarDate.text = dateStr
                    delay(1000)
                }
            }
        }
    }

    private suspend fun loadApps() {
        val scanned = AppRepository.scan(requireContext()).toMutableList()

        // Inject File Manager and Web App if not present
        if (scanned.none { it.pkg == "com.gothwad.launcher.files" }) {
            scanned.add(
                0,
                AppEntry(
                    pkg = "com.gothwad.launcher.files",
                    label = "File Manager",
                    banner = null,
                    icon = null,
                    autoCategory = "productivity",
                    tile = 0xFF4FA7FA.toInt(),
                    stamp = System.currentTimeMillis(),
                    firstInstall = System.currentTimeMillis()
                )
            )
        }
        if (scanned.none { it.pkg == "com.gothwad.launcher.webapp" }) {
            scanned.add(
                1,
                AppEntry(
                    pkg = "com.gothwad.launcher.webapp",
                    label = "Web Browser",
                    banner = null,
                    icon = null,
                    autoCategory = "apps",
                    tile = 0xFF60A5FA.toInt(),
                    stamp = System.currentTimeMillis(),
                    firstInstall = System.currentTimeMillis()
                )
            )
        }

        // Add any pinned PWA shortcuts from config
        for ((pkg, label) in currentConfig.pcCustomLabels) {
            if (pkg.startsWith("pwa://") && scanned.none { it.pkg == pkg }) {
                scanned.add(
                    AppEntry(
                        pkg = pkg,
                        label = label,
                        banner = null,
                        icon = null,
                        autoCategory = "apps",
                        tile = 0xFF0284C7.toInt(),
                        stamp = System.currentTimeMillis(),
                        firstInstall = System.currentTimeMillis()
                    )
                )
            }
        }

        allApps = scanned
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

    override fun onResume() {
        super.onResume()
        if (currentConfig.pcOverlayTaskbarEnabled && FloatingTaskbarService.canDrawOverlays(requireContext())) {
            FloatingTaskbarService.startIfEnabled(requireContext())
        }
    }

    override fun onDestroyView() {
        floatingWindowManager?.closeAll()
        floatingWindowManager = null
        requireContext().unregisterReceiver(batteryReceiver)
        activePopupWindow?.dismiss()
        activePopupWindow = null
        super.onDestroyView()
        _binding = null
    }
}
