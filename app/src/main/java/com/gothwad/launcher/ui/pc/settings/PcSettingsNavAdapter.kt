package com.gothwad.launcher.ui.pc.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.R
import com.gothwad.launcher.databinding.ItemPcSettingsNavBinding

class PcSettingsNavAdapter(
    private var items: List<PcSettingsNavItem>,
    private var selectedId: Int = PcSettingsConstants.TAB_SYSTEM,
    private val onItemSelected: (PcSettingsNavItem) -> Unit
) : RecyclerView.Adapter<PcSettingsNavAdapter.NavViewHolder>() {

    fun setSelectedId(id: Int) {
        val prevIndex = items.indexOfFirst { it.id == selectedId }
        val newIndex = items.indexOfFirst { it.id == id }
        selectedId = id
        if (prevIndex != -1) notifyItemChanged(prevIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }

    fun updateItems(newItems: List<PcSettingsNavItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NavViewHolder {
        val binding = ItemPcSettingsNavBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return NavViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NavViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class NavViewHolder(
        private val binding: ItemPcSettingsNavBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PcSettingsNavItem) {
            val isSelected = item.id == selectedId
            binding.navItemTitle.text = item.title
            binding.navItemIcon.setImageResource(item.iconRes)

            if (isSelected) {
                binding.navItemRoot.setBackgroundResource(R.drawable.bg_win11_nav_active)
                binding.navActiveIndicator.visibility = View.VISIBLE
                binding.navItemTitle.setTextColor(Color.WHITE)
                binding.navItemIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#0078D4"))
            } else {
                binding.navItemRoot.background = null
                binding.navActiveIndicator.visibility = View.INVISIBLE
                binding.navItemTitle.setTextColor(Color.parseColor("#CCCCCC"))
                binding.navItemIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#8E95A5"))
            }

            binding.navItemRoot.setOnClickListener {
                setSelectedId(item.id)
                onItemSelected(item)
            }
        }
    }
}
