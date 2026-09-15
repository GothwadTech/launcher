package com.gothwad.launcher.ui.view

import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.databinding.ItemAppCardBinding
import com.gothwad.launcher.ui.AppIcons

class AppCardAdapter(
    private var cardWidthPx: Int,
    private var cardHeightPx: Int,
    private var cornerRadiusPx: Float,
    private var accentColor: Int,
    private var showLabels: Boolean,
    private var isGridMode: Boolean = true,
    private var lockedPackages: Set<String> = emptySet(),
    private var movingPackage: String? = null,
    private val onLaunchApp: (AppEntry) -> Unit,
    private val onAppMenu: (AppEntry) -> Unit,
) : ListAdapter<AppEntry, AppCardAdapter.AppCardViewHolder>(AppCardDiffCallback()) {

    // Shared lock badge drawable cached for all holders
    private val lockDrawable by lazy {
        AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFFFFD54F.toInt())
    }

    fun updateConfig(
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float,
        accent: Int,
        labels: Boolean,
        gridMode: Boolean = true,
        locked: Set<String>,
        moving: String?,
    ) {
        val sizeChanged = cardWidthPx != widthPx || cardHeightPx != heightPx || isGridMode != gridMode
        val visualChanged = cornerRadiusPx != radiusPx || accentColor != accent || showLabels != labels
        cardWidthPx = widthPx
        cardHeightPx = heightPx
        cornerRadiusPx = radiusPx
        accentColor = accent
        showLabels = labels
        isGridMode = gridMode
        lockedPackages = locked
        movingPackage = moving
        if (sizeChanged || visualChanged) {
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
        private val binding: ItemAppCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var cardDrawable: SmoothCornerDrawable? = null
        private var outlineProvider: SmoothOutlineProvider? = null

        private var isHovered: Boolean = false
        private var longPressTriggeredAt: Long = 0L

        init {
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false
            itemView.isClickable = true

            itemView.setOnClickListener {
                if (SystemClock.uptimeMillis() - longPressTriggeredAt < 600L) {
                    return@setOnClickListener
                }
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onLaunchApp(getItem(pos))
                }
            }

            itemView.setOnLongClickListener {
                longPressTriggeredAt = SystemClock.uptimeMillis()
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onAppMenu(getItem(pos))
                    true
                } else false
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                val isSelect = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                val isMenu = keyCode == KeyEvent.KEYCODE_MENU

                if (isMenu && event.action == KeyEvent.ACTION_UP) {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onAppMenu(getItem(pos))
                        return@setOnKeyListener true
                    }
                }

                if (isSelect) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (event.repeatCount == 0) {
                            longPressTriggeredAt = 0L
                        } else if (event.repeatCount > 0 && longPressTriggeredAt == 0L) {
                            longPressTriggeredAt = SystemClock.uptimeMillis()
                            val pos = bindingAdapterPosition
                            if (pos != RecyclerView.NO_POSITION) {
                                onAppMenu(getItem(pos))
                                return@setOnKeyListener true
                            }
                        }
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        if (longPressTriggeredAt != 0L && (SystemClock.uptimeMillis() - longPressTriggeredAt < 800L)) {
                            return@setOnKeyListener true
                        }
                        val pos = bindingAdapterPosition
                        if (pos != RecyclerView.NO_POSITION) {
                            onLaunchApp(getItem(pos))
                        }
                        return@setOnKeyListener true
                    }
                }
                false
            }

            itemView.setOnFocusChangeListener { _, hasFocus ->
                val pos = bindingAdapterPosition
                val app = if (pos != RecyclerView.NO_POSITION) getItem(pos) else null
                applyVisualState(hasFocus, isHovered, app)
            }

            itemView.setOnHoverListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_HOVER_ENTER -> {
                        isHovered = true
                        val pos = bindingAdapterPosition
                        val app = if (pos != RecyclerView.NO_POSITION) getItem(pos) else null
                        applyVisualState(itemView.isFocused, true, app)
                        true
                    }
                    MotionEvent.ACTION_HOVER_EXIT -> {
                        isHovered = false
                        val pos = bindingAdapterPosition
                        val app = if (pos != RecyclerView.NO_POSITION) getItem(pos) else null
                        applyVisualState(itemView.isFocused, false, app)
                        true
                    }
                    else -> false
                }
            }

            itemView.setOnGenericMotionListener { _, event ->
                if (event.action == MotionEvent.ACTION_BUTTON_PRESS &&
                    (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0)
                ) {
                    longPressTriggeredAt = SystemClock.uptimeMillis()
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onAppMenu(getItem(pos))
                        true
                    } else false
                } else false
            }

            // Lock badge icon
            binding.imgLockBadge.setImageDrawable(lockDrawable)
        }

        private fun applyVisualState(hasFocus: Boolean, hovered: Boolean, app: AppEntry?) {
            val isMoving = app != null && app.pkg == movingPackage
            val targetScale = when {
                isMoving -> 1.05f
                hasFocus -> 1.08f
                hovered -> 1.03f
                else -> 1.0f
            }

            val targetElevation = when {
                hasFocus -> 12f * itemView.resources.displayMetrics.density
                hovered -> 6f * itemView.resources.displayMetrics.density
                else -> 0f
            }

            itemView.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .translationZ(targetElevation)
                .setDuration(150)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .start()

            updateCardBackground(app, hasFocus, hovered, isMoving)
        }

        private fun updateCardBackground(
            app: AppEntry?,
            hasFocus: Boolean,
            hovered: Boolean,
            isMoving: Boolean
        ) {
            val tileColor = if (app != null) {
                if (app.banner != null) 0xFF141720.toInt() else app.tile
            } else 0xFF212638.toInt()

            val density = itemView.resources.displayMetrics.density
            val strokeWidth = when {
                isMoving -> 3.5f * density
                hasFocus -> 2.5f * density
                hovered -> 1.5f * density
                else -> 0f
            }
            val strokeColor = when {
                isMoving || hasFocus -> accentColor
                hovered -> (accentColor and 0x00FFFFFF) or 0xCC000000.toInt()
                else -> 0
            }

            var drawable = cardDrawable
            if (drawable == null) {
                drawable = SmoothCornerDrawable(
                    cornerRadiusPx = cornerRadiusPx,
                    fillColor = tileColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeWidth,
                )
                cardDrawable = drawable
                binding.cardInner.background = drawable
            } else {
                drawable.cornerRadiusPx = cornerRadiusPx
                drawable.fillColor = tileColor
                drawable.strokeColor = strokeColor
                drawable.strokeWidthPx = strokeWidth
            }

            // Update squircle outline clipping provider
            var provider = outlineProvider
            if (provider == null) {
                provider = SmoothOutlineProvider(cornerRadiusPx)
                outlineProvider = provider
                binding.cardInner.outlineProvider = provider
                binding.cardInner.clipToOutline = true
            } else {
                provider.cornerRadiusPx = cornerRadiusPx
                binding.cardInner.invalidateOutline()
            }
        }

        fun bind(app: AppEntry) {
            // Apply dimensions: MATCH_PARENT in grid mode so 6 columns fit exactly, fixed width in carousel
            val targetWidth = if (isGridMode) ViewGroup.LayoutParams.MATCH_PARENT else cardWidthPx
            val lp = itemView.layoutParams ?: ViewGroup.LayoutParams(targetWidth, cardHeightPx)
            if (lp.width != targetWidth || lp.height != cardHeightPx) {
                lp.width = targetWidth
                lp.height = cardHeightPx
                itemView.layoutParams = lp
            }

            val hasFocus = itemView.isFocused
            val isMoving = app.pkg == movingPackage
            updateCardBackground(app, hasFocus, isHovered, isMoving)

            // Banner vs fallback
            if (app.banner != null) {
                binding.imgBanner.visibility = View.VISIBLE
                binding.layoutFallback.visibility = View.GONE
                binding.imgBanner.setImageBitmap(app.banner)
            } else {
                binding.imgBanner.visibility = View.GONE
                binding.layoutFallback.visibility = View.VISIBLE
                if (app.icon != null) {
                    binding.imgIcon.setImageBitmap(app.icon)
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
