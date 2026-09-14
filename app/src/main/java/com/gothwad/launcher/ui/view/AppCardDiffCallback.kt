package com.gothwad.launcher.ui.view

import androidx.recyclerview.widget.DiffUtil
import com.gothwad.launcher.data.AppEntry

class AppCardDiffCallback : DiffUtil.ItemCallback<AppEntry>() {
    override fun areItemsTheSame(oldItem: AppEntry, newItem: AppEntry): Boolean {
        return oldItem.pkg == newItem.pkg
    }

    override fun areContentsTheSame(oldItem: AppEntry, newItem: AppEntry): Boolean {
        return oldItem.label == newItem.label &&
            oldItem.stamp == newItem.stamp &&
            oldItem.tile == newItem.tile &&
            (oldItem.banner == null) == (newItem.banner == null)
    }
}
