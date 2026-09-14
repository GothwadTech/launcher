package com.gothwad.launcher.ui.view

import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.databinding.ItemAppCardBinding

class AppCardAdapter(
    private var cardWidthPx: Int,
    private var cardHeightPx: Int,
    private var cornerRadiusPx: Float,
    private var accentColor: Int,
    private var showLabels: Boolean,
    private var lockedPackages: Set<String> = emptySet(),
    private var movingPackage: String? = null,
    private val onLaunchApp: (AppEntry) -> Unit,
    private val onAppMenu: (AppEntry) -> Unit,
) : ListAdapter<AppEntry, AppCardAdapter.AppCardViewHolder>(AppCardDiffCallback()) {

    fun updateConfig(
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float,
        accent: Int,
        labels: Boolean,
        locked: Set<String>,
        moving: String?,
    ) {
        val sizeChanged = cardWidthPx != widthPx || cardHeightPx != heightPx
        cardWidthPx = widthPx
        cardHeightPx = heightPx
        cornerRadiusPx = radiusPx
        accentColor = accent
        showLabels = labels
        lockedPackages = locked
        movingPackage = moving
        if (sizeChanged) {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppCardViewHolder {
        val binding = ItemAppCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppCardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppCardViewHolder(
        val binding: ItemAppCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onLaunchApp(getItem(pos))
                }
            }

            itemView.setOnLongClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onAppMenu(getItem(pos))
                    true
                } else false
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                        if (event.isLongPress) {
                            val pos = bindingAdapterPosition
                            if (pos != RecyclerView.NO_POSITION) {
                                onAppMenu(getItem(pos))
                                return@setOnKeyListener true
                            }
                        }
                    }
                }
                false
            }

            itemView.setOnFocusChangeListener { view, hasFocus ->
                val pos = bindingAdapterPosition
                val app = if (pos != RecyclerView.NO_POSITION) getItem(pos) else null
                applyFocusState(hasFocus, app)
            }
        }

        private fun applyFocusState(hasFocus: Boolean, app: AppEntry?) {
            val isMoving = app != null && app.pkg == movingPackage
            val targetScale = if (hasFocus) 1.10f else 1.0f
            itemView.elevation = if (hasFocus) 16f else 0f
            itemView.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(140)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()

            updateCardBackground(app, hasFocus || isMoving, isMoving)
        }

        private fun updateCardBackground(app: AppEntry?, hasFocus: Boolean, isMoving: Boolean) {
            val tileColor = if (app != null) {
                if (app.banner != null) 0xFF141720.toInt() else app.tile.toArgb()
            } else 0xFF212638.toInt()

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerRadiusPx
                setColor(tileColor)
                if (hasFocus) {
                    setStroke(if (isMoving) 5 else 3, accentColor)
                } else {
                    setStroke(0, 0)
                }
            }
            binding.cardInner.background = bg
        }

        fun bind(app: AppEntry) {
            // Apply dimensions
            val lp = itemView.layoutParams ?: ViewGroup.LayoutParams(cardWidthPx, cardHeightPx)
            lp.width = cardWidthPx
            lp.height = cardHeightPx
            itemView.layoutParams = lp

            val hasFocus = itemView.isFocused
            val isMoving = app.pkg == movingPackage
            updateCardBackground(app, hasFocus || isMoving, isMoving)

            // Banner vs fallback
            if (app.banner != null) {
                binding.imgBanner.visibility = View.VISIBLE
                binding.layoutFallback.visibility = View.GONE
                binding.imgBanner.setImageBitmap(app.banner.asAndroidBitmap())
            } else {
                binding.imgBanner.visibility = View.GONE
                binding.layoutFallback.visibility = View.VISIBLE
                if (app.icon != null) {
                    binding.imgIcon.setImageBitmap(app.icon.asAndroidBitmap())
                    binding.imgIcon.visibility = View.VISIBLE
                } else {
                    binding.imgIcon.visibility = View.GONE
                }
                if (showLabels) {
                    binding.txtLabel.text = app.label
                    binding.txtLabel.visibility = View.VISIBLE
                } else {
                    binding.txtLabel.visibility = View.GONE
                }
            }

            // Lock badge
            val isLocked = app.pkg in lockedPackages
            binding.badgeLock.visibility = if (isLocked) View.VISIBLE else View.GONE
        }
    }
}
