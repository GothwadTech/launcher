package com.gothwad.launcher.ui.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.gothwad.launcher.R
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
    var onNetworkClick: (() -> Unit)? = null
    var onNotificationsClick: (() -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null
    var onA11yAlertClick: (() -> Unit)? = null

    /** Tapping/focusing the centre clock cluster opens the Quick Dashboard. */
    var onDashboardClick: (() -> Unit)? = null

    init {
        setupStaticIcons()
        setupListeners()
    }

    private fun setupStaticIcons() {
        binding.btnHome.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HOME, Color.WHITE))
        binding.btnSearch.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, Color.WHITE))
        binding.btnBluetooth.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_REMOTE, 0xFF64B5F6.toInt()))
        binding.btnNetwork.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI, Color.WHITE))
        binding.btnNotifications.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, Color.WHITE))
        binding.btnSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, Color.WHITE))
        binding.btnA11yAlert.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_warning))
        binding.btnA11yAlert.imageTintList = ContextCompat.getColorStateList(context, R.color.status_warning)
    }

    private fun setupListeners() {
        binding.btnHome.setOnClickListener { onHomeClick?.invoke() }
        binding.btnSearch.setOnClickListener { onSearchClick?.invoke() }
        binding.btnBluetooth.setOnClickListener { onBluetoothClick?.invoke() }
        binding.btnNetwork.setOnClickListener { onNetworkClick?.invoke() }
        binding.btnNotifications.setOnClickListener { onNotificationsClick?.invoke() }
        binding.btnSettings.setOnClickListener { onSettingsClick?.invoke() }
        binding.btnA11yAlert.setOnClickListener { onA11yAlertClick?.invoke() }
        // The clock cluster is the Quick Dashboard entry point (network, Bluetooth remote,
        // volume). That panel existed but nothing in the UI could open it.
        binding.clusterCenter.setOnClickListener { onDashboardClick?.invoke() }
    }

    fun setA11yAlertVisible(visible: Boolean) {
        binding.btnA11yAlert.visibility = if (visible) View.VISIBLE else View.GONE
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

        // All three clusters look the same, so they share one builder (this used to be the
        // same 12-line GradientDrawable block pasted three times). Each view still needs its
        // own Drawable instance - sharing one would share bounds/state/callback.
        val glass = config.statusBarGlass
        binding.clusterLeft.background = clusterBackground(radiusPx, strokePx, glass)
        binding.clusterCenter.background = clusterBackground(radiusPx, strokePx, glass)
        binding.clusterRight.background = clusterBackground(radiusPx, strokePx, glass)
    }

    /**
     * Glass/solid cluster background *with* focus and hover states.
     *
     * The centre cluster is D-pad reachable (it opens the Quick Dashboard), and a plain
     * [GradientDrawable] gives a focused item no visual feedback at all - unusable with a
     * remote. Each call returns a fresh [StateListDrawable]: drawables cannot be shared
     * between views without sharing mutable state (bounds, level, callback).
     */
    private fun clusterBackground(radiusPx: Float, strokePx: Int, glass: Boolean): Drawable {
        fun panel(fill: Int, stroke: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fill)
            setStroke(strokePx, stroke)
        }

        val (fill, stroke) = if (glass) {
            ContextCompat.getColor(context, R.color.surface_glass) to
                ContextCompat.getColor(context, R.color.surface_glass_stroke)
        } else {
            Color.parseColor("#212124") to Color.parseColor("#33FFFFFF")
        }

        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                panel(
                    ContextCompat.getColor(context, R.color.status_pill_focused),
                    ContextCompat.getColor(context, R.color.accent_blue),
                ),
            )
            addState(
                intArrayOf(android.R.attr.state_hovered),
                panel(ContextCompat.getColor(context, R.color.status_pill_hover), stroke),
            )
            addState(intArrayOf(), panel(fill, stroke))
        }
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
        binding.btnBluetooth.visibility = View.VISIBLE
        val color = if (bt.connected) 0xFF64B5F6.toInt() else Color.WHITE
        binding.btnBluetooth.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_REMOTE, color))
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
}
