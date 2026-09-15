package com.gothwad.launcher.ui.views

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.databinding.ItemPcDesktopIconBinding
import com.gothwad.launcher.ui.view.AppCardDiffCallback

class PcDesktopIconAdapter(
    private val onLaunchApp: (AppEntry) -> Unit,
    private val onAppMenu: (AppEntry) -> Unit,
) : ListAdapter<AppEntry, PcDesktopIconAdapter.PcIconViewHolder>(AppCardDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PcIconViewHolder {
        val binding = ItemPcDesktopIconBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PcIconViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PcIconViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PcIconViewHolder(
        private val binding: ItemPcDesktopIconBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppEntry) {
            binding.tvAppLabel.text = app.label

            // Rounded background for icon container
            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * binding.root.resources.displayMetrics.density
                setColor(0x14FFFFFF)
            }
            binding.iconContainer.background = iconBg
            binding.iconContainer.clipToOutline = true

            val bitmap = app.icon
            if (bitmap != null) {
                binding.imgAppIcon.setImageBitmap(bitmap)
                binding.imgAppIcon.visibility = View.VISIBLE
                binding.tvFallbackLetter.visibility = View.GONE
            } else {
                binding.imgAppIcon.visibility = View.GONE
                binding.tvFallbackLetter.text = app.label.take(1).uppercase()
                binding.tvFallbackLetter.visibility = View.VISIBLE
            }

            binding.root.setOnClickListener {
                onLaunchApp(app)
            }

            binding.root.setOnLongClickListener {
                onAppMenu(app)
                true
            }
        }
    }
}
