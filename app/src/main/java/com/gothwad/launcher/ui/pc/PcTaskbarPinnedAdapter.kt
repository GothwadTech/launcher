package com.gothwad.launcher.ui.pc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.databinding.ItemPcTaskbarPinnedBinding
import com.gothwad.launcher.ui.view.AppCardDiffCallback

class PcTaskbarPinnedAdapter(
    private var itemSizePx: Int = 0,
    private var iconSizePx: Int = 0,
    private val onLaunchApp: (AppEntry) -> Unit,
    private val onUnpinApp: (AppEntry, View) -> Unit,
) : ListAdapter<AppEntry, PcTaskbarPinnedAdapter.PinnedViewHolder>(AppCardDiffCallback()) {

    fun updateSizes(itemPx: Int, iconPx: Int) {
        if (itemSizePx != itemPx || iconSizePx != iconPx) {
            itemSizePx = itemPx
            iconSizePx = iconPx
            notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinnedViewHolder {
        val binding = ItemPcTaskbarPinnedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PinnedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PinnedViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PinnedViewHolder(
        private val binding: ItemPcTaskbarPinnedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppEntry) {
            // Apply dynamic sizing if configured
            if (itemSizePx > 0) {
                val rootLp = binding.rootTaskbarPinned.layoutParams
                rootLp.width = itemSizePx
                rootLp.height = itemSizePx
                binding.rootTaskbarPinned.layoutParams = rootLp
            }
            if (iconSizePx > 0) {
                val iconLp = binding.imgTaskbarPinnedIcon.layoutParams
                iconLp.width = iconSizePx
                iconLp.height = iconSizePx
                binding.imgTaskbarPinnedIcon.layoutParams = iconLp

                val fallbackLp = binding.tvTaskbarPinnedFallback.layoutParams
                fallbackLp.width = iconSizePx
                fallbackLp.height = iconSizePx
                binding.tvTaskbarPinnedFallback.layoutParams = fallbackLp
            }

            val bitmap = app.icon
            if (bitmap != null) {
                binding.imgTaskbarPinnedIcon.setImageBitmap(bitmap)
                binding.imgTaskbarPinnedIcon.visibility = View.VISIBLE
                binding.tvTaskbarPinnedFallback.visibility = View.GONE
            } else {
                binding.imgTaskbarPinnedIcon.visibility = View.GONE
                binding.tvTaskbarPinnedFallback.text = app.label.take(1).uppercase()
                binding.tvTaskbarPinnedFallback.visibility = View.VISIBLE
            }

            binding.root.setOnClickListener {
                onLaunchApp(app)
            }

            // Right click / Context click to unpin
            binding.root.setOnContextClickListener { v ->
                onUnpinApp(app, v)
                true
            }

            // Long press to unpin
            binding.root.setOnLongClickListener { v ->
                onUnpinApp(app, v)
                true
            }
        }
    }
}
