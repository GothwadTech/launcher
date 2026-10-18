package com.gothwad.launcher.ui.dialogs

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.R
import com.gothwad.launcher.databinding.ItemWallpaperTileBinding
import com.gothwad.launcher.ui.DEFAULT_WALLPAPER_INDEX
import com.gothwad.launcher.ui.WALLPAPERS

/**
 * Visual wallpaper picker tiles: each tile renders the *actual* gradient it selects, so the
 * choice is "this picture" instead of "a name like Moon / Day / Reddish". A custom-photo
 * tile sits after the presets.
 */
class WallpaperTileAdapter(
    private val onFocused: (item: TileItem) -> Unit,
    private val onSelect: (item: TileItem) -> Unit,
) : RecyclerView.Adapter<WallpaperTileAdapter.TileHolder>() {

    /** One picker entry: a preset ([presetIndex] >= 0) or the custom photo ([CUSTOM]). */
    data class TileItem(
        val presetIndex: Int,
        val label: String,
        val colors: List<Int>,
    ) {
        val isCustom: Boolean get() = presetIndex == CUSTOM

        companion object {
            const val CUSTOM = -1
        }
    }

    private val items: List<TileItem> = buildList {
        WALLPAPERS.forEachIndexed { i, p -> add(TileItem(i, p.displayLabel(), p.colors)) }
        add(TileItem(TileItem.CUSTOM, "", listOf(0xFF16161A.toInt(), 0xFF2F3437.toInt())))
    }

    /** Currently committed selection ([TileItem.CUSTOM] or a preset index). */
    var selectedPresetIndex: Int = DEFAULT_WALLPAPER_INDEX

    fun selectPreset(index: Int) {
        val old = selectedPresetIndex
        selectedPresetIndex = index
        items.indexOfFirst { it.presetIndex == old }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        items.indexOfFirst { it.presetIndex == index }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
    }

    inner class TileHolder(
        private val binding: ItemWallpaperTileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TileItem) {
            val ctx = binding.root.context
            binding.tileName.text = if (item.isCustom) {
                ctx.getString(R.string.wallpaper_tile_custom)
            } else {
                item.label
            }
            binding.tileGradient.background = gradientFor(item, binding.root.resources.displayMetrics.density)
            binding.tileCheck.visibility =
                if (item.presetIndex == selectedPresetIndex) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onSelect(item) }
            binding.root.setOnFocusChangeListener { v, hasFocus ->
                applyFocusStroke(v, hasFocus)
                if (hasFocus) onFocused(item)
            }
        }

        private fun applyFocusStroke(view: View, focused: Boolean) {
            val gd = binding.tileGradient.background as? GradientDrawable ?: return
            val density = view.resources.displayMetrics.density
            gd.cornerRadius = 8 * density
            if (focused) {
                gd.setStroke((2.5f * density).toInt(), 0xFFFFFFFF.toInt())
            } else {
                gd.setStroke(0, 0)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileHolder {
        val binding = ItemWallpaperTileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return TileHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TileHolder, position: Int) {
        holder.bind(items[position])
    }

    private fun gradientFor(item: TileItem, density: Float): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            item.colors.toIntArray(),
        ).apply { cornerRadius = 8 * density }
}
