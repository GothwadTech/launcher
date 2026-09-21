package com.gothwad.launcher.ui.tv

import android.graphics.Rect
import android.util.Log
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
    private var gridSpanCount: Int = 6,
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
        spanCount: Int = gridSpanCount,
        locked: Set<String>,
        moving: String?,
    ) {
        val sizeChanged = cardWidthPx != widthPx || cardHeightPx != heightPx || gapPx != gap ||
                gridSpanCount != spanCount
        val visualChanged = cornerRadiusPx != radiusPx || accentColor != accent ||
                showCategoryNames != categoryNames || showAppLabels != appLabels ||
                lockedPackages != locked || movingPackage != moving

        cardWidthPx = widthPx
        cardHeightPx = heightPx
        gridSpanCount = spanCount
        cornerRadiusPx = radiusPx
        gapPx = gap
        accentColor = accent
        showCategoryNames = categoryNames
        showAppLabels = appLabels
        lockedPackages = locked
        movingPackage = moving

        if (!sizeChanged && !visualChanged) return

        runCatching {
            if (sizeChanged) notifyItemRangeChanged(0, itemCount)
            else notifyItemRangeChanged(0, itemCount, PAYLOAD_CONFIG)
        }.onFailure { Log.w(TAG, "Category notify skipped: ${it.message}") }
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = currentList.getOrNull(position) ?: return
        if (payloads.isEmpty()) holder.bind(item) else holder.applyConfig()
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
        val item = currentList.getOrNull(position) ?: return
        holder.bind(item)
    }

    inner class CategoryViewHolder(
        private val binding: ItemTvCategoryRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var childAdapter: AppCardAdapter? = null

        init {
            binding.recyclerCarousel.setRecycledViewPool(recycledViewPool)
            binding.recyclerCarousel.isNestedScrollingEnabled = false
        }

        private fun applyLayoutManager() {
            val currentLm = binding.recyclerCarousel.layoutManager as? GridLayoutManager
            if (currentLm == null || currentLm.spanCount != gridSpanCount) {
                binding.recyclerCarousel.layoutManager =
                    GridLayoutManager(binding.root.context, gridSpanCount)
            }
            val lp = binding.recyclerCarousel.layoutParams
            if (lp != null && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                binding.recyclerCarousel.layoutParams = lp
            }
        }

        /** Style-only rebind (payload path): re-applies geometry/decorations, no re-bind of cards. */
        fun applyConfig() {
            applyLayoutManager()
        }

        fun bind(item: CategoryRowItem) {
            applyLayoutManager()

            binding.txtCategoryTitle.text = item.category.name
            binding.txtCategoryTitle.visibility = if (showCategoryNames) View.VISIBLE else View.GONE

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
                    isGridMode = true,
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
                    gridMode = true,
                    locked = lockedPackages,
                    moving = movingPackage
                )
            }

            childAdapter?.submitList(item.apps)
        }
    }

    companion object {
        private const val TAG = "TvCategoryAdapter"
        private const val FOCUS_HEADROOM_DP = 8f
        const val PAYLOAD_CONFIG = 1
    }
}
