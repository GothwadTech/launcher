package com.gothwad.launcher.ui.tv

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.CategoryCfg
import com.gothwad.launcher.databinding.ItemTvCategoryRowBinding
import com.gothwad.launcher.ui.view.AppCardAdapter

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
    private var isGridMode: Boolean = true,
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
        gridMode: Boolean = true,
        locked: Set<String>,
        moving: String?,
    ) {
        val sizeChanged = cardWidthPx != widthPx || cardHeightPx != heightPx || gapPx != gap || isGridMode != gridMode
        val visualChanged = cornerRadiusPx != radiusPx || accentColor != accent ||
                showCategoryNames != categoryNames || showAppLabels != appLabels ||
                lockedPackages != locked || movingPackage != moving

        cardWidthPx = widthPx
        cardHeightPx = heightPx
        cornerRadiusPx = radiusPx
        gapPx = gap
        accentColor = accent
        showCategoryNames = categoryNames
        showAppLabels = appLabels
        isGridMode = gridMode
        lockedPackages = locked
        movingPackage = moving

        if (sizeChanged || visualChanged) {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemTvCategoryRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CategoryViewHolder(
        private val binding: ItemTvCategoryRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var childAdapter: AppCardAdapter? = null

        init {
            binding.recyclerCarousel.setRecycledViewPool(recycledViewPool)
            binding.recyclerCarousel.setHasFixedSize(true)
        }

        fun bind(item: CategoryRowItem) {
            binding.txtCategoryTitle.text = item.category.name
            binding.txtCategoryTitle.visibility = if (showCategoryNames) View.VISIBLE else View.GONE

            if (isGridMode) {
                val currentLm = binding.recyclerCarousel.layoutManager as? GridLayoutManager
                if (currentLm == null || currentLm.spanCount != 6) {
                    binding.recyclerCarousel.layoutManager = GridLayoutManager(binding.root.context, 6)
                }
            } else {
                val currentLm = binding.recyclerCarousel.layoutManager as? LinearLayoutManager
                if (currentLm == null || currentLm.orientation != LinearLayoutManager.HORIZONTAL) {
                    binding.recyclerCarousel.layoutManager = LinearLayoutManager(
                        binding.root.context,
                        LinearLayoutManager.HORIZONTAL,
                        false
                    )
                }
            }

            while (binding.recyclerCarousel.itemDecorationCount > 0) {
                binding.recyclerCarousel.removeItemDecorationAt(0)
            }
            binding.recyclerCarousel.addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    outRect.right = gapPx
                    outRect.bottom = gapPx
                }
            })

            if (childAdapter == null) {
                childAdapter = AppCardAdapter(
                    cardWidthPx = cardWidthPx,
                    cardHeightPx = cardHeightPx,
                    cornerRadiusPx = cornerRadiusPx,
                    accentColor = accentColor,
                    showLabels = showAppLabels,
                    isGridMode = isGridMode,
                    lockedPackages = lockedPackages,
                    movingPackage = movingPackage,
                    onLaunchApp = onLaunchApp,
                    onAppMenu = onAppMenu,
                )
                binding.recyclerCarousel.adapter = childAdapter
            } else {
                childAdapter?.updateConfig(
                    widthPx = cardWidthPx,
                    heightPx = cardHeightPx,
                    radiusPx = cornerRadiusPx,
                    accent = accentColor,
                    labels = showAppLabels,
                    gridMode = isGridMode,
                    locked = lockedPackages,
                    moving = movingPackage
                )
            }

            childAdapter?.submitList(item.apps)
        }
    }
}
