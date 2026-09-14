package com.gothwad.launcher.ui.dialogs

import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.databinding.ItemNotificationBinding
import com.gothwad.launcher.service.TvNotificationItem
import com.gothwad.launcher.ui.AppIcons

class NotificationAdapter(
    private val onClick: (TvNotificationItem) -> Unit,
    private val onDismiss: (TvNotificationItem) -> Unit
) : ListAdapter<TvNotificationItem, NotificationAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.btnDismissNotif.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onClick(getItem(pos))
                }
            }
            binding.btnDismissNotif.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDismiss(getItem(pos))
                }
            }
        }

        fun bind(item: TvNotificationItem) {
            binding.tvNotifTitle.text = item.title
            binding.tvNotifBody.text = if (item.text.isNotBlank()) item.text else item.subText.orEmpty()

            val timeAgo = DateUtils.getRelativeTimeSpanString(
                item.postTime,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            binding.tvNotifTime.text = timeAgo

            if (item.nativeBitmap != null) {
                binding.imgNotifAppIcon.setImageBitmap(item.nativeBitmap)
            } else {
                binding.imgNotifAppIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, Color.WHITE))
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TvNotificationItem>() {
            override fun areItemsTheSame(oldItem: TvNotificationItem, newItem: TvNotificationItem) = oldItem.key == newItem.key
            override fun areContentsTheSame(oldItem: TvNotificationItem, newItem: TvNotificationItem) = oldItem == newItem
        }
    }
}
