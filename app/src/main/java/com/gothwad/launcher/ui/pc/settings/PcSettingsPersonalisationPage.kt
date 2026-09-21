package com.gothwad.launcher.ui.pc.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gothwad.launcher.R
import com.gothwad.launcher.data.ConfigStore
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.LayoutPcSettingsPersonalisationBinding
import com.gothwad.launcher.ui.ACCENTS
import com.gothwad.launcher.ui.PC_WALLPAPERS
import com.gothwad.launcher.ui.PcWallpaperPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PcSettingsPersonalisationPage(
    private val context: Context,
    private val scope: CoroutineScope,
    private var config: LauncherConfig,
    private val onWallpaperChanged: () -> Unit,
    private val onPickCustomPhoto: () -> Unit,
    private val onOpenLockSetup: () -> Unit
) {
    private var _binding: LayoutPcSettingsPersonalisationBinding? = null
    val binding get() = _binding!!

    private lateinit var wallpaperAdapter: PcWallpaperAdapter

    fun createView(inflater: LayoutInflater): View {
        _binding = LayoutPcSettingsPersonalisationBinding.inflate(inflater, null, false)
        setupHeroPreview()
        setupWallpaperList()
        setupAccentColors()
        setupCards()
        return binding.root
    }

    fun updateConfig(newConfig: LauncherConfig) {
        config = newConfig
        updateHeroPreview()
        if (::wallpaperAdapter.isInitialized) {
            wallpaperAdapter.setSelection(config.pcWallpaper, config.pcUseCustomWallpaper)
        }
    }

    private fun setupHeroPreview() {
        updateHeroPreview()

        binding.btnBrowseCustomWallpaper.setOnClickListener {
            onPickCustomPhoto()
        }

        binding.btnResetDefaultWallpaper.setOnClickListener {
            applyPresetWallpaper(PC_WALLPAPERS[0])
        }
    }

    private fun updateHeroPreview() {
        if (config.pcUseCustomWallpaper) {
            val file = File(context.filesDir, "wallpaper_pc.jpg")
            val fallback = File(context.filesDir, "wallpaper.jpg")
            val target = if (file.exists()) file else fallback
            if (target.exists()) {
                scope.launch {
                    val bmp = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(target.absolutePath)
                    }
                    if (bmp != null) {
                        binding.imgHeroPreview.setImageBitmap(bmp)
                        binding.tvCurrentWallpaperName.text = "Custom Image"
                    }
                }
                return
            }
        }

        val idx = config.pcWallpaper.coerceIn(0, PC_WALLPAPERS.size - 1)
        val preset = PC_WALLPAPERS[idx]
        binding.imgHeroPreview.setImageResource(preset.resId)
        binding.tvCurrentWallpaperName.text = preset.name
    }

    private fun setupWallpaperList() {
        wallpaperAdapter = PcWallpaperAdapter(
            selectedId = config.pcWallpaper,
            isCustomSelected = config.pcUseCustomWallpaper
        ) { preset ->
            applyPresetWallpaper(preset)
        }
        binding.recyclerWallpapers.adapter = wallpaperAdapter
    }

    private fun applyPresetWallpaper(preset: PcWallpaperPreset) {
        scope.launch {
            ConfigStore(context).update {
                it.copy(pcWallpaper = preset.id, pcUseCustomWallpaper = false)
            }
            config = config.copy(pcWallpaper = preset.id, pcUseCustomWallpaper = false)
            updateHeroPreview()
            wallpaperAdapter.setSelection(preset.id, false)
            onWallpaperChanged()
        }
    }

    private fun setupAccentColors() {
        val container = binding.layoutAccentDots
        container.removeAllViews()
        val density = context.resources.displayMetrics.density
        val size = (32 * density).toInt()
        val margin = (6 * density).toInt()

        ACCENTS.forEachIndexed { index, color ->
            val dot = View(context).apply {
                val lp = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = margin
                }
                layoutParams = lp
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (config.accent == index) {
                        setStroke((3 * density).toInt(), Color.WHITE)
                    }
                }
                setOnClickListener {
                    scope.launch {
                        ConfigStore(context).update { it.copy(accent = index) }
                        config = config.copy(accent = index)
                        setupAccentColors()
                    }
                }
            }
            container.addView(dot)
        }
    }

    private fun setupCards() {
        setupCardRow(
            root = binding.cardBackground,
            iconRes = R.drawable.ic_win_personalisation,
            title = "Background",
            subtitle = "Background image, colour, slideshow"
        ) {
            binding.recyclerWallpapers.smoothScrollToPosition(0)
        }

        setupCardRow(
            root = binding.cardColours,
            iconRes = R.drawable.ic_win_personalisation,
            title = "Colours",
            subtitle = "Accent colours, transparency effects"
        ) {
            binding.containerAccentColors.visibility =
                if (binding.containerAccentColors.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        setupCardRow(
            root = binding.cardThemes,
            iconRes = R.drawable.ic_win_personalisation,
            title = "Themes",
            subtitle = "Install, create, manage presets"
        )

        setupCardRow(
            root = binding.cardLockScreen,
            iconRes = R.drawable.ic_win_privacy,
            title = "Lock screen",
            subtitle = "Lock screen credentials, PIN protection"
        ) {
            onOpenLockSetup()
        }

        setupCardRow(
            root = binding.cardTouchKeyboard,
            iconRes = R.drawable.ic_win_accessibility,
            title = "Touch keyboard",
            subtitle = "Theme, size, handwriting input"
        )

        setupCardRow(
            root = binding.cardStart,
            iconRes = R.drawable.ic_win_apps,
            title = "Start",
            subtitle = "Show recently added apps, folder layouts"
        ) {
            scope.launch {
                val newAuto = !config.autoCategoryOnInstall
                ConfigStore(context).update { it.copy(autoCategoryOnInstall = newAuto) }
                config = config.copy(autoCategoryOnInstall = newAuto)
            }
        }

        setupCardRow(
            root = binding.cardTaskbar,
            iconRes = R.drawable.ic_win_system,
            title = "Taskbar",
            subtitle = "Taskbar behaviours, system pins, center alignment"
        ) {
            scope.launch {
                val newCenter = !config.pcTaskbarCenter
                ConfigStore(context).update { it.copy(pcTaskbarCenter = newCenter) }
                config = config.copy(pcTaskbarCenter = newCenter)
            }
        }

        setupCardRow(
            root = binding.cardFonts,
            iconRes = R.drawable.ic_win_personalisation,
            title = "Fonts",
            subtitle = "Installed system typography"
        )

        setupCardRow(
            root = binding.cardDeviceUsage,
            iconRes = R.drawable.ic_win_system,
            title = "Device usage",
            subtitle = "Select all the ways you plan to use your device"
        )
    }

    private fun setupCardRow(
        root: View,
        iconRes: Int,
        title: String,
        subtitle: String,
        onClick: (() -> Unit)? = null
    ) {
        val icon = root.findViewById<ImageView>(R.id.card_row_icon)
        val tvTitle = root.findViewById<TextView>(R.id.card_row_title)
        val tvSub = root.findViewById<TextView>(R.id.card_row_subtitle)
        icon?.setImageResource(iconRes)
        tvTitle?.text = title
        tvSub?.text = subtitle

        if (onClick != null) {
            root.setOnClickListener { onClick() }
        }
    }
}
