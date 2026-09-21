package com.gothwad.launcher.ui.view

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.gothwad.launcher.R
import com.gothwad.launcher.data.BackgroundMediaState
import com.gothwad.launcher.data.BluetoothDeviceStatus
import com.gothwad.launcher.data.CORNER_RADII
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.data.NetStatus
import com.gothwad.launcher.databinding.ViewStatusBarBinding
import com.gothwad.launcher.ui.AppIcons

class StatusBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    val binding: ViewStatusBarBinding =
        ViewStatusBarBinding.inflate(LayoutInflater.from(context), this, true)

    var onHomeClick: (() -> Unit)? = null
    var onSearchClick: (() -> Unit)? = null
    var onBluetoothClick: (() -> Unit)? = null
    var onBackgroundMediaClick: (() -> Unit)? = null
    var onNetworkClick: (() -> Unit)? = null
    var onVpnClick: (() -> Unit)? = null
    var onNotificationsClick: (() -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null

    private var mediaPulseAnimator: ObjectAnimator? = null

    init {
        setupStaticIcons()
        setupListeners()
    }

    private fun setupStaticIcons() {
        binding.btnHome.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HOME, Color.WHITE))
        binding.btnSearch.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, Color.WHITE))
        binding.btnBluetooth.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BLUETOOTH, 0xFF64B5F6.toInt()))
        binding.btnNetwork.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI, Color.WHITE))
        binding.btnVpn.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_KEY, 0xFF81C995.toInt()))
        binding.btnNotifications.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, Color.WHITE))
        binding.btnSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, Color.WHITE))
    }

    private fun setupListeners() {
        binding.btnHome.setOnClickListener { onHomeClick?.invoke() }
        binding.btnSearch.setOnClickListener { onSearchClick?.invoke() }
        binding.btnBluetooth.setOnClickListener { onBluetoothClick?.invoke() }
        binding.btnBgMedia.setOnClickListener { onBackgroundMediaClick?.invoke() }
        binding.btnNetwork.setOnClickListener { onNetworkClick?.invoke() }
        binding.btnVpn.setOnClickListener { onVpnClick?.invoke() }
        binding.btnNotifications.setOnClickListener { onNotificationsClick?.invoke() }
        binding.btnSettings.setOnClickListener { onSettingsClick?.invoke() }
    }

    fun applyConfig(config: LauncherConfig) {
        val density = context.resources.displayMetrics.density
        val radiusPx = if (config.headerMatchIconCorners) {
            val cornerIdx = config.cornerRadius.coerceIn(0, CORNER_RADII.size - 1)
            CORNER_RADII[cornerIdx] * density
        } else {
            20f * density
        }
        val strokePx = (1f * density).toInt()

        val bgLeft = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            if (config.statusBarGlass) {
                setColor(ContextCompat.getColor(context, R.color.surface_glass))
                setStroke(strokePx, ContextCompat.getColor(context, R.color.surface_glass_stroke))
            } else {
                setColor(Color.parseColor("#212124"))
                setStroke(strokePx, Color.parseColor("#33FFFFFF"))
            }
        }
        val bgRight = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            if (config.statusBarGlass) {
                setColor(ContextCompat.getColor(context, R.color.surface_glass))
                setStroke(strokePx, ContextCompat.getColor(context, R.color.surface_glass_stroke))
            } else {
                setColor(Color.parseColor("#212124"))
                setStroke(strokePx, Color.parseColor("#33FFFFFF"))
            }
        }
        binding.clusterLeft.background = bgLeft
        binding.clusterRight.background = bgRight
    }

    fun setNetStatus(net: NetStatus) {
        val (iconPath, color) = when {
            net.ethernet -> AppIcons.PATH_ETHERNET to Color.WHITE
            net.wifi -> AppIcons.PATH_WIFI to Color.WHITE
            else -> AppIcons.PATH_WIFI_OFF to 0x80FFFFFF.toInt()
        }
        binding.btnNetwork.setImageDrawable(AppIcons.createDrawable(iconPath, color))
    }

    fun setBluetoothStatus(bt: BluetoothDeviceStatus) {
        if (!bt.connected) {
            binding.btnBluetooth.visibility = View.GONE
            return
        }
        binding.btnBluetooth.visibility = View.VISIBLE
        binding.btnBluetooth.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BLUETOOTH, 0xFF64B5F6.toInt()))
    }

    /** Shows/hides the VPN shortcut (only while a VPN transport is actually up). */
    fun setVpnStatus(vpnActive: Boolean, buttonEnabled: Boolean) {
        binding.btnVpn.visibility = if (vpnActive && buttonEnabled) View.VISIBLE else View.GONE
    }

    fun setBackgroundMedia(media: BackgroundMediaState) {
        if (media.isPlaying) {
            binding.btnBgMedia.visibility = View.VISIBLE
            val path = if (media.isStockAdCandidate) AppIcons.PATH_SHIELD else AppIcons.PATH_EQUALIZER
            val tint = if (media.isStockAdCandidate) 0xFFFF7043.toInt() else 0xFF81D4FA.toInt()
            binding.btnBgMedia.setImageDrawable(AppIcons.createDrawable(path, tint))

            if (mediaPulseAnimator == null) {
                mediaPulseAnimator = ObjectAnimator.ofFloat(binding.btnBgMedia, "alpha", 0.55f, 1f).apply {
                    duration = 850
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
        } else {
            binding.btnBgMedia.visibility = View.GONE
            mediaPulseAnimator?.cancel()
            mediaPulseAnimator = null
            binding.btnBgMedia.alpha = 1f
        }
    }

    fun setNotificationCount(count: Int, hasPermission: Boolean) {
        val bellPath = if (count > 0) AppIcons.PATH_BELL_ACTIVE else AppIcons.PATH_BELL
        val bellColor = if (count > 0 || !hasPermission) Color.WHITE else 0x73FFFFFF.toInt()
        binding.btnNotifications.setImageDrawable(AppIcons.createDrawable(bellPath, bellColor))

        if (count > 0) {
            binding.viewNotificationDot.visibility = View.VISIBLE
            binding.viewNotificationDot.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_notification_badge)
        } else if (!hasPermission) {
            binding.viewNotificationDot.visibility = View.VISIBLE
            binding.viewNotificationDot.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_warning)
        } else {
            binding.viewNotificationDot.visibility = View.GONE
        }
    }

    fun setClockTime(formattedDateTime: String) {
        binding.tvClock.text = formattedDateTime
    }

    override fun onDetachedFromWindow() {
        mediaPulseAnimator?.cancel()
        mediaPulseAnimator = null
        super.onDetachedFromWindow()
    }
}
