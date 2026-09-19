package com.gothwad.launcher.ui.pc

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.databinding.ItemPcFileBinding
import com.gothwad.launcher.ui.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PcFileManagerAdapter(
    private val onFileClick: (PcFileItem) -> Unit,
    private val onFileLongClick: (PcFileItem, View) -> Unit
) : ListAdapter<PcFileItem, PcFileManagerAdapter.VH>(DIFF) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPcFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemPcFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PcFileItem) {
            binding.tvFileName.text = item.name
            val dateStr = dateFormat.format(Date(item.lastModified))
            binding.tvFileDetails.text = "${item.getFormattedSize()} • $dateStr"

            // Icon based on type
            val iconPath = when {
                item.isDirectory -> AppIcons.PATH_FOLDER
                item.isImage -> AppIcons.PATH_IMAGE
                item.isVideo -> AppIcons.PATH_TV
                item.isAudio -> AppIcons.PATH_MUSIC
                item.isApk -> AppIcons.PATH_APPS
                item.isZip -> AppIcons.PATH_SAVE
                item.isDoc -> AppIcons.PATH_SAVE
                else -> AppIcons.PATH_SAVE
            }

            val iconColor = when {
                item.isDirectory -> 0xFF4FA7FA.toInt()
                item.isImage -> 0xFF66BB6A.toInt()
                item.isVideo -> 0xFFEF5350.toInt()
                item.isAudio -> 0xFFAB47BC.toInt()
                item.isApk -> 0xFF26C6DA.toInt()
                item.isZip -> 0xFFFFCA28.toInt()
                else -> 0xFFB0BEC5.toInt()
            }

            binding.imgFileIcon.setImageDrawable(AppIcons.createDrawable(iconPath, iconColor))
            binding.imgFileMore.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, 0x66FFFFFF))

            binding.root.setOnClickListener {
                onFileClick(item)
            }

            binding.root.setOnLongClickListener { v ->
                onFileLongClick(item, v)
                true
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<PcFileItem>() {
            override fun areItemsTheSame(old: PcFileItem, new: PcFileItem): Boolean =
                old.file.absolutePath == new.file.absolutePath

            override fun areContentsTheSame(old: PcFileItem, new: PcFileItem): Boolean =
                old.name == new.name && old.size == new.size && old.lastModified == new.lastModified
        }
    }
}

class PcSidebarAdapter(
    private val onStorageClick: (PcFileManager.StorageInfo) -> Unit
) : ListAdapter<PcFileManager.StorageInfo, PcSidebarAdapter.VH>(DIFF) {

    private var selectedPath: String? = null

    fun setSelectedPath(path: String) {
        selectedPath = path
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPcFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), getItem(position).path.absolutePath == selectedPath)
    }

    inner class VH(private val binding: ItemPcFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(info: PcFileManager.StorageInfo, isSelected: Boolean) {
            binding.tvFileName.text = info.name
            binding.tvFileDetails.text = info.path.absolutePath
            binding.tvFileDetails.maxLines = 1
            binding.tvFileDetails.setTextColor(0x66FFFFFF)

            val iconPath = when {
                info.isPrimary -> AppIcons.PATH_STORAGE
                info.isRemovable -> AppIcons.PATH_SAVE
                info.name.contains("Download") -> AppIcons.PATH_DOWN
                info.name.contains("Picture") -> AppIcons.PATH_IMAGE
                info.name.contains("Movie") -> AppIcons.PATH_TV
                info.name.contains("Music") -> AppIcons.PATH_MUSIC
                info.name.contains("Document") -> AppIcons.PATH_SAVE
                else -> AppIcons.PATH_FOLDER
            }

            val color = if (isSelected) 0xFF4FA7FA.toInt() else 0xFFCCCCCC.toInt()
            binding.imgFileIcon.setImageDrawable(AppIcons.createDrawable(iconPath, color))
            binding.imgFileMore.visibility = View.GONE

            binding.root.setBackgroundColor(
                if (isSelected) 0x224FA7FA else Color.TRANSPARENT
            )

            binding.root.setOnClickListener {
                onStorageClick(info)
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<PcFileManager.StorageInfo>() {
            override fun areItemsTheSame(old: PcFileManager.StorageInfo, new: PcFileManager.StorageInfo): Boolean =
                old.path.absolutePath == new.path.absolutePath

            override fun areContentsTheSame(old: PcFileManager.StorageInfo, new: PcFileManager.StorageInfo): Boolean =
                old.name == new.name && old.path.absolutePath == new.path.absolutePath
        }
    }
}
