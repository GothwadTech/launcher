package com.gothwad.launcher.ui.view

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.gothwad.launcher.R
import com.gothwad.launcher.data.BackgroundMediaState
import com.gothwad.launcher.data.BluetoothDeviceStatus
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

    var onDashboardClick: (() -> Unit)? = null
    var onRefreshClick: (() -> Unit)? = null
    var onSearchClick: (() -> Unit)? = null
    var onVoiceSearchClick: (() -> Unit)? = null
    var onBluetoothClick: (() -> Unit)? = null
    var onBackgroundMediaClick: (() -> Unit)? = null
    var onNetworkClick: (() -> Unit)? = null
    var onNotificationsClick: (() -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null

    private var mediaPulseAnimator: ObjectAnimator? = null

    init {
        setupStaticIcons()
        setupListeners()
    }

    private fun setupStaticIcons() {
        binding.btnDashboard.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DASHBOARD, Color.WHITE))
        binding.btnRefresh.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_REFRESH, 0xFF4DD0E1.toInt()))
        binding.btnSearch.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, Color.WHITE))
        binding.btnVoiceSearch.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_MIC, Color.WHITE))
        binding.imgBluetooth.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BLUETOOTH, 0xFF64B5F6.toInt()))
        binding.btnNetwork.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI, Color.WHITE))
        binding.btnNotifications.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, Color.WHITE))
        binding.btnSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, Color.WHITE))
    }

    private fun setupListeners() {
        binding.btnDashboard.setOnClickListener { onDashboardClick?.invoke() }
        binding.btnRefresh.setOnClickListener { onRefreshClick?.invoke() }
        binding.btnSearch.setOnClickListener { onSearchClick?.invoke() }
        binding.btnVoiceSearch.setOnClickListener { onVoiceSearchClick?.invoke() }
        binding.layoutBluetoothPill.setOnClickListener { onBluetoothClick?.invoke() }
        binding.btnBgMedia.setOnClickListener { onBackgroundMediaClick?.invoke() }
        binding.btnNetwork.setOnClickListener { onNetworkClick?.invoke() }
        binding.btnNotifications.setOnClickListener { onNotificationsClick?.invoke() }
        binding.btnSettings.setOnClickListener { onSettingsClick?.invoke() }
    }

    fun applyConfig(config: LauncherConfig) {
        val glassDrawable = if (config.statusBarGlass) {
            ContextCompat.getDrawable(context, R.drawable.bg_glass_cluster)
        } else {
            null
        }
        binding.clusterLeft.background = glassDrawable
        binding.clusterRight.background = glassDrawable
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
        if (bt.connected || bt.batteryLevel >= 0) {
            binding.layoutBluetoothPill.visibility = View.VISIBLE
            val alphaColor = if (bt.connected) 0xFF64B5F6.toInt() else 0x8064B5F6.toInt()
            binding.imgBluetooth.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BLUETOOTH, alphaColor))

            if (bt.batteryLevel >= 0) {
                binding.tvBtBattery.visibility = View.VISIBLE
                binding.tvBtBattery.text = "${bt.batteryLevel}%"
                val batteryColor = if (bt.batteryLevel in 0..20) 0xFFE57373.toInt() else 0xD9FFFFFF.toInt()
                binding.tvBtBattery.setTextColor(batteryColor)
            } else {
                binding.tvBtBattery.visibility = View.GONE
            }
        } else {
            binding.layoutBluetoothPill.visibility = View.GONE
        }
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
            binding.tvNotificationBadge.visibility = View.VISIBLE
            binding.tvNotificationBadge.text = if (count > 9) "9+" else count.toString()
            binding.viewNotificationDot.visibility = View.GONE
        } else if (!hasPermission) {
            binding.tvNotificationBadge.visibility = View.GONE
            binding.viewNotificationDot.visibility = View.VISIBLE
        } else {
            binding.tvNotificationBadge.visibility = View.GONE
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
