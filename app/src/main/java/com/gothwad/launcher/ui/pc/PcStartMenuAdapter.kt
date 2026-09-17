package com.gothwad.launcher.ui.pc

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.databinding.ItemPcDesktopIconBinding
import com.gothwad.launcher.ui.view.AppCardDiffCallback

class PcStartMenuAdapter(
    private val onLaunchApp: (AppEntry) -> Unit
) : ListAdapter<AppEntry, PcStartMenuAdapter.StartAppViewHolder>(AppCardDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StartAppViewHolder {
        val binding = ItemPcDesktopIconBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StartAppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StartAppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StartAppViewHolder(
        private val binding: ItemPcDesktopIconBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppEntry) {
            val density = binding.root.resources.displayMetrics.density

            // Compact 64dp cell for start menu
            val rootLp = binding.pcIconRoot.layoutParams
            rootLp.width = (64 * density).toInt()
            binding.pcIconRoot.layoutParams = rootLp
            binding.pcIconRoot.setPadding(
                (2 * density).toInt(),
                (4 * density).toInt(),
                (2 * density).toInt(),
                (4 * density).toInt()
            )

            val containerLp = binding.iconContainer.layoutParams
            containerLp.width = (36 * density).toInt()
            containerLp.height = (36 * density).toInt()
            binding.iconContainer.layoutParams = containerLp

            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8 * density
                setColor(0x14FFFFFF)
            }
            binding.iconContainer.background = iconBg
            binding.iconContainer.clipToOutline = true

            val imgLp = binding.imgAppIcon.layoutParams
            imgLp.width = (36 * density).toInt()
            imgLp.height = (36 * density).toInt()
            binding.imgAppIcon.layoutParams = imgLp

            binding.tvAppLabel.text = app.label
            binding.tvAppLabel.visibility = View.VISIBLE
            binding.tvAppLabel.textSize = 10.5f
            val labelLp = binding.tvAppLabel.layoutParams
            labelLp.width = (60 * density).toInt()
            binding.tvAppLabel.layoutParams = labelLp

            val bitmap = app.icon
            if (bitmap != null) {
                binding.imgAppIcon.setImageBitmap(bitmap)
                binding.imgAppIcon.visibility = View.VISIBLE
                binding.tvFallbackLetter.visibility = View.GONE
            } else {
                binding.imgAppIcon.visibility = View.GONE
                binding.tvFallbackLetter.text = app.label.take(1).uppercase()
                binding.tvFallbackLetter.textSize = 14f
                binding.tvFallbackLetter.visibility = View.VISIBLE
            }

            binding.root.setOnClickListener {
                onLaunchApp(app)
            }
        }
    }
}
