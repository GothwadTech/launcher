package com.gothwad.launcher.apps.files

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.databinding.ItemFileEntryBinding

class FileAdapter(
    private val onItemClick: (FileEntry) -> Unit,
    private val onDeleteClick: (FileEntry) -> Unit
) : ListAdapter<FileEntry, FileAdapter.FileViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(private val binding: ItemFileEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FileEntry) {
            binding.txtFileName.text = item.name
            binding.txtFileMeta.text = "${item.sizeString} • ${item.lastModifiedString}"

            binding.txtFileIcon.text = if (item.isDirectory) {
                "📁"
            } else {
                when (item.extension.lowercase()) {
                    "mp3", "wav", "m4a", "flac" -> "🎵"
                    "mp4", "mkv", "webm", "avi" -> "🎬"
                    "jpg", "jpeg", "png", "webp", "gif" -> "🖼️"
                    "pdf" -> "📕"
                    "txt", "md", "json", "xml" -> "📄"
                    "apk" -> "📦"
                    "zip", "tar", "gz", "7z", "rar" -> "🗜️"
                    else -> "📄"
                }
            }

            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnFileDelete.setOnClickListener { onDeleteClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<FileEntry>() {
        override fun areItemsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean {
            return oldItem.file.absolutePath == newItem.file.absolutePath
        }

        override fun areContentsTheSame(oldItem: FileEntry, newItem: FileEntry): Boolean {
            return oldItem == newItem
        }
    }
}
