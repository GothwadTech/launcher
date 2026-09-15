package com.gothwad.launcher.ui.dialogs

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.databinding.ItemSearchAppBinding
import com.gothwad.launcher.ui.AppIcons

class SearchAppAdapter(
    private val onAppClick: (AppEntry) -> Unit
) : ListAdapter<AppEntry, SearchAppAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSearchAppBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.imgLaunchArrow.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PLAY, 0xFF4C8DFF.toInt()))
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onAppClick(getItem(pos))
                }
            }
        }

        fun bind(app: AppEntry) {
            binding.tvAppLabel.text = app.label
            binding.tvAppPkg.text = app.pkg

            val bmp = app.icon ?: app.banner
            if (bmp != null) {
                binding.imgAppIcon.setImageBitmap(bmp)
            } else {
                binding.imgAppIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_APPS, Color.WHITE))
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppEntry>() {
            override fun areItemsTheSame(oldItem: AppEntry, newItem: AppEntry) = oldItem.pkg == newItem.pkg
            override fun areContentsTheSame(oldItem: AppEntry, newItem: AppEntry) = oldItem == newItem
        }
    }
}
