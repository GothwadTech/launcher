package com.gothwad.launcher.ui.pc

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.databinding.ItemPcTaskbarPinnedBinding
import com.gothwad.launcher.ui.AppIcons

/**
 * Adapter for running windows in taskbar - shows open apps with active indicator
 * Similar to Windows taskbar running apps
 */
class PcTaskbarRunningAdapter(
    private val onClickWindow: (PcWindow) -> Unit,
    private val onCloseWindow: (PcWindow) -> Unit
) : ListAdapter<PcWindow, PcTaskbarRunningAdapter.VH>(DIFF) {

    private var focusedWindowId: String? = null

    fun setFocusedWindow(windowId: String?) {
        focusedWindowId = windowId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPcTaskbarPinnedBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), getItem(position).id == focusedWindowId)
    }

    inner class VH(private val binding: ItemPcTaskbarPinnedBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(win: PcWindow, isFocused: Boolean) {
            // Icon
            if (win.app.icon != null) {
                binding.imgTaskbarPinnedIcon.setImageBitmap(win.app.icon)
                binding.imgTaskbarPinnedIcon.visibility = View.VISIBLE
                binding.tvTaskbarPinnedFallback.visibility = View.GONE
            } else {
                binding.imgTaskbarPinnedIcon.visibility = View.GONE
                binding.tvTaskbarPinnedFallback.text = win.app.label.take(1).uppercase()
                binding.tvTaskbarPinnedFallback.visibility = View.VISIBLE
            }

            // Background - focused gets highlighted
            binding.root.alpha = if (win.isMinimized) 0.7f else 1f

            if (isFocused && !win.isMinimized) {
                binding.root.setBackgroundResource(com.gothwad.launcher.R.drawable.bg_pc_pinned_app)
                binding.viewPinnedIndicator.visibility = View.VISIBLE
                binding.viewPinnedIndicator.setBackgroundColor(0xFF4FA7FA.toInt())
                binding.root.background?.setTint(0x44FFFFFF)
            } else {
                binding.root.setBackgroundResource(com.gothwad.launcher.R.drawable.bg_pc_taskbar_btn)
                binding.viewPinnedIndicator.visibility = if (win.isMinimized) View.INVISIBLE else View.VISIBLE
                binding.viewPinnedIndicator.setBackgroundColor(if (win.isMinimized) 0x66FFFFFF else 0xFF8AB4F8.toInt())
            }

            // Click to restore/focus or minimize
            binding.root.setOnClickListener {
                onClickWindow(win)
            }

            // Long press to close
            binding.root.setOnLongClickListener {
                onCloseWindow(win)
                true
            }

            // Show minimized badge
            binding.root.contentDescription = "${win.app.label} - ${if (win.isMinimized) "Minimized" else "Running"}"
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<PcWindow>() {
            override fun areItemsTheSame(old: PcWindow, new: PcWindow): Boolean = old.id == new.id
            override fun areContentsTheSame(old: PcWindow, new: PcWindow): Boolean =
                old.isMinimized == new.isMinimized && old.isMaximized == new.isMaximized &&
                old.zIndex == new.zIndex && old.app.pkg == new.app.pkg
        }
    }
}
