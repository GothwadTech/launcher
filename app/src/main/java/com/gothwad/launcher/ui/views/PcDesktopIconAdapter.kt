package com.gothwad.launcher.ui.views

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.ItemPcDesktopIconBinding
import java.util.Collections

class PcDesktopIconAdapter(
    private val onLaunchApp: (AppEntry) -> Unit,
    private val onAppContextMenu: (AppEntry, View, Float, Float) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
) : RecyclerView.Adapter<PcDesktopIconAdapter.PcIconViewHolder>() {

    private val appsList = mutableListOf<AppEntry>()
    private var config: LauncherConfig = LauncherConfig()

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newApps: List<AppEntry>, newConfig: LauncherConfig) {
        appsList.clear()
        appsList.addAll(newApps)
        config = newConfig
        notifyDataSetChanged()
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition in appsList.indices && toPosition in appsList.indices) {
            Collections.swap(appsList, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }
    }

    fun getCurrentList(): List<AppEntry> = appsList.toList()

    override fun getItemCount(): Int = appsList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PcIconViewHolder {
        val binding = ItemPcDesktopIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PcIconViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PcIconViewHolder, position: Int) {
        holder.bind(appsList[position])
    }

    inner class PcIconViewHolder(
        val binding: ItemPcDesktopIconBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("ClickableViewAccessibility")
        fun bind(app: AppEntry) {
            val density = binding.root.resources.displayMetrics.density
            val scale = config.pcUiScale.coerceIn(0.6f, 1.5f)
            val iconSizeDp = config.pcIconSize.coerceIn(32, 64)

            // Scaled dimensions
            val itemWidthPx = ((iconSizeDp + 26) * scale * density).toInt()
            val containerSizePx = (iconSizeDp * scale * density).toInt()
            val imgSizePx = ((iconSizeDp - 6) * scale * density).toInt().coerceAtLeast((24 * density).toInt())
            val cornerRadiusPx = 10f * scale * density

            // Adjust root item width and padding
            val rootLp = binding.pcIconRoot.layoutParams
            rootLp.width = itemWidthPx
            binding.pcIconRoot.layoutParams = rootLp
            binding.pcIconRoot.setPadding(
                (4 * scale * density).toInt(),
                (6 * scale * density).toInt(),
                (4 * scale * density).toInt(),
                (6 * scale * density).toInt()
            )

            // Adjust icon container dimensions
            val containerLp = binding.iconContainer.layoutParams
            containerLp.width = containerSizePx
            containerLp.height = containerSizePx
            binding.iconContainer.layoutParams = containerLp

            // Rounded background for icon container
            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerRadiusPx
                setColor(0x14FFFFFF)
            }
            binding.iconContainer.background = iconBg
            binding.iconContainer.clipToOutline = true

            // Adjust inner ImageView dimensions
            val imgLp = binding.imgAppIcon.layoutParams
            imgLp.width = imgSizePx
            imgLp.height = imgSizePx
            binding.imgAppIcon.layoutParams = imgLp

            // Custom Display Name or Default
            val displayName = config.pcCustomLabels[app.pkg] ?: app.label
            binding.tvAppLabel.text = displayName

            // Show or Hide labels
            if (config.pcShowLabels) {
                binding.tvAppLabel.visibility = View.VISIBLE
                binding.tvAppLabel.textSize = (10f * scale).coerceIn(8f, 14f)
                val labelLp = binding.tvAppLabel.layoutParams
                labelLp.width = itemWidthPx - (4 * density).toInt()
                binding.tvAppLabel.layoutParams = labelLp
            } else {
                binding.tvAppLabel.visibility = View.GONE
            }

            // App Icon
            val bitmap = app.icon
            if (bitmap != null) {
                binding.imgAppIcon.setImageBitmap(bitmap)
                binding.imgAppIcon.visibility = View.VISIBLE
                binding.tvFallbackLetter.visibility = View.GONE
            } else {
                binding.imgAppIcon.visibility = View.GONE
                binding.tvFallbackLetter.text = displayName.take(1).uppercase()
                binding.tvFallbackLetter.textSize = (16f * scale).coerceIn(12f, 22f)
                binding.tvFallbackLetter.visibility = View.VISIBLE
            }

            // Left Click to Launch
            binding.root.setOnClickListener {
                onLaunchApp(app)
            }

            var lastTouchX = 0f
            var lastTouchY = 0f

            // Track touch / mouse coordinates for context menu positioning
            binding.root.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                }
                false
            }

            // Right-Click (Mouse Secondary Button) via standard Android OnContextClickListener
            binding.root.setOnContextClickListener { v ->
                onAppContextMenu(app, v, lastTouchX, lastTouchY)
                true
            }

            // Touch Long Click / Hold triggers Drag OR Context Menu
            binding.root.setOnLongClickListener { v ->
                onAppContextMenu(app, v, lastTouchX, lastTouchY)
                true
            }
        }
    }
}
