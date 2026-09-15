package com.gothwad.launcher.ui.views

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.databinding.ItemPcTaskbarPinnedBinding
import com.gothwad.launcher.ui.view.AppCardDiffCallback

class PcTaskbarPinnedAdapter(
    private val onLaunchApp: (AppEntry) -> Unit,
    private val onUnpinApp: (AppEntry, View) -> Unit,
) : ListAdapter<AppEntry, PcTaskbarPinnedAdapter.PinnedViewHolder>(AppCardDiffCallback()) {

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
