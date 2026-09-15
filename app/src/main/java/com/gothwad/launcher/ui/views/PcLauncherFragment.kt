package com.gothwad.launcher.ui.views

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.gothwad.launcher.Actions
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.AppRepository
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.MODE_TV
import com.gothwad.launcher.databinding.FragmentPcLauncherBinding
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
    private var currentConfig: LauncherConfig = LauncherConfig()
    private var allApps: List<AppEntry> = emptyList()

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
        setupRecyclerView()
        observeData()
        startClockUpdates()
    }

    private fun setupTaskbar() {
        binding.btnStart.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WINDOWS, 0xFF4C8DFF.toInt()))
        binding.btnSearch.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, Color.WHITE))
        binding.btnNotifications.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, Color.WHITE))
        binding.btnTvMode.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_TV, 0xFF60A5FA.toInt()))
        binding.btnSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, Color.WHITE))

        binding.btnStart.setOnClickListener {
            openSearchDialog()
        }

        binding.btnSearch.setOnClickListener {
            openSearchDialog()
        }

        binding.btnNotifications.setOnClickListener {
            NotificationBottomSheetFragment.newInstance()
                .show(parentFragmentManager, NotificationBottomSheetFragment.TAG)
        }

        binding.btnTvMode.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                ConfigStore(requireContext()).update { it.copy(launcherMode = MODE_TV) }
            }
        }

        binding.btnSettings.setOnClickListener {
            SettingsBottomSheetFragment.newInstance(
                config = currentConfig,
                apps = allApps,
                onWallpaperChanged = { applyWallpaper() },
                onRerunWizard = {
                    SetupWizardDialogFragment.newInstance {}.show(parentFragmentManager, SetupWizardDialogFragment.TAG)
                },
                onModeSelected = {}
            ).show(parentFragmentManager, SettingsBottomSheetFragment.TAG)
        }

        binding.btnShowDesktop.setOnClickListener {
            binding.recyclerDesktopGrid.smoothScrollToPosition(0)
        }
    }

    private fun openSearchDialog() {
        SearchDialogFragment.newInstance(
            apps = allApps,
            config = currentConfig,
            onLaunch = { app -> handleAppLaunch(app) }
        ).show(parentFragmentManager, SearchDialogFragment.TAG)
    }

    private fun setupRecyclerView() {
        val widthPixels = resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density
        val widthDp = widthPixels / density
        val spanCount = (widthDp / 104).toInt().coerceIn(4, 12)

        desktopAdapter = PcDesktopIconAdapter(
            onLaunchApp = { app ->
                handleAppLaunch(app)
            },
            onAppMenu = { app ->
                openAppDetails(app.pkg)
            }
        )

        binding.recyclerDesktopGrid.apply {
            layoutManager = GridLayoutManager(requireContext(), spanCount)
            adapter = desktopAdapter
            setHasFixedSize(true)
        }
    }

    private fun handleAppLaunch(app: AppEntry, skipLock: Boolean = false) {
        if (!skipLock && currentConfig.appLockEnabled && currentConfig.appLockPin.isNotEmpty() && app.pkg in currentConfig.lockedApps) {
            PinEntryDialogFragment.newInstance(
                title = "App Locked",
                subtitle = "Enter PIN to launch ${app.label}",
                correctPin = currentConfig.appLockPin,
                pinLength = currentConfig.appLockPinLength,
                isCancelable = true,
                onSuccess = {
                    handleAppLaunch(app, skipLock = true)
                }
            ).show(parentFragmentManager, PinEntryDialogFragment.TAG)
        } else {
            Actions.launchApp(requireContext(), app.pkg)
        }
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

    private fun observeData() {
        val configStore = ConfigStore(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    configStore.flow.collectLatest { config ->
                        currentConfig = config
                        applyWallpaper()
                        updateVisibleApps()
                    }
                }

                launch {
                    loadApps()
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
                    val timePattern = if (currentConfig.h24) "HH:mm" else "h:mm a"
                    val timeStr = SimpleDateFormat(timePattern, Locale.getDefault()).format(now)
                    val dateStr = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(now)

                    binding.tvTaskbarTime.text = timeStr
                    binding.tvTaskbarDate.text = dateStr
                    delay(1000)
                }
            }
        }
    }

    private suspend fun loadApps() {
        allApps = AppRepository.scan(requireContext())
        updateVisibleApps()
    }

    private fun updateVisibleApps() {
        if (allApps.isEmpty()) return
        val visible = allApps.filter { it.pkg !in currentConfig.hidden }
        desktopAdapter?.submitList(visible)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
