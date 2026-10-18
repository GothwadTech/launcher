package com.gothwad.launcher.ui.dialogs

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.databinding.ItemWallpaperTileBinding
import com.gothwad.launcher.ui.THEMES

/**
 * Visual theme tiles: each tile renders the theme's own surface/background colours, so
 * the user picks a look, not a name. Reuses the wallpaper tile layout.
 */
class ThemeTileAdapter(
    private val onSelect: (index: Int) -> Unit,
) : RecyclerView.Adapter<ThemeTileAdapter.Holder>() {

    var selectedIndex: Int = 0

    fun select(index: Int) {
        val old = selectedIndex
        selectedIndex = index
        if (old in THEMES.indices) notifyItemChanged(old)
        if (index in THEMES.indices) notifyItemChanged(index)
    }

    inner class Holder(private val binding: ItemWallpaperTileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(index: Int) {
            val theme = THEMES[index]
            val density = binding.root.resources.displayMetrics.density
            binding.tileName.text = theme.displayLabel()
            binding.tileGradient.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(theme.surface, theme.background, theme.accent),
            ).apply { cornerRadius = 8 * density }
            binding.tileCheck.visibility =
                if (index == selectedIndex) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onSelect(index) }
            binding.root.setOnFocusChangeListener { _, hasFocus ->
                val gd = binding.tileGradient.background as? GradientDrawable ?: return@setOnFocusChangeListener
                if (hasFocus) gd.setStroke((2.5f * density).toInt(), 0xFFFFFFFF.toInt())
                else gd.setStroke(0, 0)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemWallpaperTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = THEMES.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(position)
}
