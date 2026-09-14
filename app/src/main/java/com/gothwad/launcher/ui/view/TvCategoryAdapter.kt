package com.gothwad.launcher.ui.view

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.CategoryCfg
import com.gothwad.launcher.databinding.ItemTvCategoryRowBinding

data class CategoryRowItem(
    val category: CategoryCfg,
    val apps: List<AppEntry>,
)

class CategoryRowDiffCallback : DiffUtil.ItemCallback<CategoryRowItem>() {
    override fun areItemsTheSame(oldItem: CategoryRowItem, newItem: CategoryRowItem): Boolean {
        return oldItem.category.id == newItem.category.id
    }

    override fun areContentsTheSame(oldItem: CategoryRowItem, newItem: CategoryRowItem): Boolean {
        if (oldItem.category.name != newItem.category.name) return false
        if (oldItem.apps.size != newItem.apps.size) return false
        return oldItem.apps == newItem.apps
    }
}

class TvCategoryAdapter(
    private val recycledViewPool: RecyclerView.RecycledViewPool,
    private var cardWidthPx: Int,
    private var cardHeightPx: Int,
    private var cornerRadiusPx: Float,
    private var gapPx: Int,
    private var accentColor: Int,
    private var showCategoryNames: Boolean,
    private var showAppLabels: Boolean,
    private var lockedPackages: Set<String> = emptySet(),
    private var movingPackage: String? = null,
    private val onLaunchApp: (AppEntry) -> Unit,
    private val onAppMenu: (AppEntry) -> Unit,
) : ListAdapter<CategoryRowItem, TvCategoryAdapter.CategoryViewHolder>(CategoryRowDiffCallback()) {

    fun updateConfig(
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float,
        gap: Int,
        accent: Int,
        categoryNames: Boolean,
        appLabels: Boolean,
        locked: Set<String>,
        moving: String?,
    ) {
        cardWidthPx = widthPx
        cardHeightPx = heightPx
        cornerRadiusPx = radiusPx
        gapPx = gap
        accentColor = accent
        showCategoryNames = categoryNames
        showAppLabels = appLabels
        lockedPackages = locked
        movingPackage = moving
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemTvCategoryRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CategoryViewHolder(
        val binding: ItemTvCategoryRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val appAdapter = AppCardAdapter(
            cardWidthPx = cardWidthPx,
            cardHeightPx = cardHeightPx,
            cornerRadiusPx = cornerRadiusPx,
            accentColor = accentColor,
            showLabels = showAppLabels,
            lockedPackages = lockedPackages,
            movingPackage = movingPackage,
            onLaunchApp = onLaunchApp,
            onAppMenu = onAppMenu,
        )

        private val itemDecoration = object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.right = gapPx
            }
        }

        init {
            val layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
            binding.recyclerCarousel.layoutManager = layoutManager
            binding.recyclerCarousel.setRecycledViewPool(recycledViewPool)
            binding.recyclerCarousel.adapter = appAdapter
            binding.recyclerCarousel.addItemDecoration(itemDecoration)
        }

        fun bind(row: CategoryRowItem) {
            if (showCategoryNames) {
                binding.txtCategoryTitle.text = row.category.name
                binding.txtCategoryTitle.visibility = View.VISIBLE
            } else {
                binding.txtCategoryTitle.visibility = View.GONE
            }

            appAdapter.updateConfig(
                widthPx = cardWidthPx,
                heightPx = cardHeightPx,
                radiusPx = cornerRadiusPx,
                accent = accentColor,
                labels = showAppLabels,
                locked = lockedPackages,
                moving = movingPackage,
            )
            appAdapter.submitList(row.apps)
        }
    }
}
