package com.gothwad.launcher.ui.view

import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
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
import com.gothwad.launcher.ui.blendColors

class AppCardAdapter(
    private var cardWidthPx: Int,
    private var cardHeightPx: Int,
    private var cornerRadiusPx: Float,
    private var accentColor: Int,
    private var showLabels: Boolean,
    private var isGridMode: Boolean = true,
    private var lockedPackages: Set<String> = emptySet(),
    private var movingPackage: String? = null,
    /** Theme tile fill (behind art / banner-less cards). */
    private var themeSurface: Int = 0xFF141720.toInt(),
    /** Focus scale in percent (100..140). */
    private var focusScalePct: Int = 108,
    /** 0 = scale, 1 = glow, 2 = outline, 3 = fill. */
    private var focusEffect: Int = 0,
    /** Focus/hover animation duration in ms (0 = instant). */
    private var animDurationMs: Int = 150,
    /** Tile label size in sp. */
    private var labelSizeSp: Int = 12,
    private val onLaunchApp: (AppEntry) -> Unit,
    private val onAppMenu: (AppEntry) -> Unit,
) : ListAdapter<AppEntry, AppCardAdapter.AppCardViewHolder>(AppCardDiffCallback()) {

    /**
     * Hover (mouse) support is only wired up on devices that actually have a pointer.
     * On a plain TV remote `ACTION_HOVER_*` never fires, so attaching the listeners was
     * pure overhead for every card (issue #36).
     */
    private var hoverSupported: Boolean = false // resolved in onAttachedToRecyclerView

    private var hoverSupportResolved = false

    fun updateConfig(
        widthPx: Int,
        heightPx: Int,
        radiusPx: Float,
        accent: Int,
        labels: Boolean,
        gridMode: Boolean = true,
        locked: Set<String>,
        moving: String?,
        themeSurface: Int = 0xFF141720.toInt(),
        focusScalePct: Int = 108,
        focusEffect: Int = 0,
        animDurationMs: Int = 150,
        labelSizeSp: Int = 12,
    ) {
        val sizeChanged = cardWidthPx != widthPx || cardHeightPx != heightPx || isGridMode != gridMode
        val visualChanged = cornerRadiusPx != radiusPx || accentColor != accent || showLabels != labels ||
            this.themeSurface != themeSurface || this.focusScalePct != focusScalePct ||
            this.focusEffect != focusEffect || this.animDurationMs != animDurationMs ||
            this.labelSizeSp != labelSizeSp
        cardWidthPx = widthPx
        cardHeightPx = heightPx
        cornerRadiusPx = radiusPx
        accentColor = accent
        showLabels = labels
        isGridMode = gridMode
        lockedPackages = locked
        movingPackage = moving
        this.themeSurface = themeSurface
        this.focusScalePct = focusScalePct
        this.focusEffect = focusEffect
        this.animDurationMs = animDurationMs
        this.labelSizeSp = labelSizeSp
        if (!sizeChanged && !visualChanged) return

        // Never notify while RecyclerView is mid-layout/scroll: that throws
        // "Cannot call this method while RecyclerView is computing a layout or scrolling".
        if (sizeChanged) {
            safeNotify { notifyItemRangeChanged(0, itemCount) }
        } else {
            // visual-only change (accent / corner radius / labels): payload rebind skips
            // the expensive banner/icon work for every visible card.
            safeNotify { notifyItemRangeChanged(0, itemCount, PAYLOAD_CONFIG) }
        }
    }

    private inline fun safeNotify(block: () -> Unit) {
        runCatching { block() }.onFailure { Log.w(TAG, "Adapter notify skipped: ${it.message}") }
    }

    /**
     * Bounds-safe item access for click/key listeners.
     *
     * `bindingAdapterPosition` can stop pointing at a valid index between the check and
     * the `getItem()` call (an uninstall, a rescan or a "hide app" flow can shrink the
     * list in between), and `ListAdapter.getItem()` throws `IndexOutOfBoundsException` -
     * which, combined with a HOME-role crash handler, used to mean a launcher restart.
     */
    private fun itemAt(position: Int): AppEntry? =
        if (position == RecyclerView.NO_POSITION) null else currentList.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppCardViewHolder {
        val binding = ItemAppCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppCardViewHolder(binding)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (!hoverSupportResolved) {
            hoverSupportResolved = true
            val pm = recyclerView.context.packageManager
            hoverSupported =
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) ||
                    pm.hasSystemFeature("android.hardware.type.pc")
        }
    }

    override fun onBindViewHolder(holder: AppCardViewHolder, position: Int) {
        val app = currentList.getOrNull(position) ?: return
        holder.bind(app)
    }

    /**
     * Config-only rebind: the payload path re-applies geometry/colours without touching
     * bitmaps or recycled drawables. DiffUtil keeps owning structural updates, so a
     * settings change no longer tears down the whole grid (issue #28).
     */
    override fun onBindViewHolder(holder: AppCardViewHolder, position: Int, payloads: MutableList<Any>) {
        val app = currentList.getOrNull(position) ?: return
        if (payloads.isEmpty() || payloads.contains(PAYLOAD_CONFIG)) {
            if (payloads.isEmpty()) holder.bind(app) else holder.applyConfig()
        }
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
                val app = itemAt(bindingAdapterPosition) ?: return@setOnClickListener
                onLaunchApp(app)
            }

            itemView.setOnLongClickListener {
                longPressTriggeredAt = SystemClock.uptimeMillis()
                val app = itemAt(bindingAdapterPosition) ?: return@setOnLongClickListener false
                onAppMenu(app)
                true
            }

            itemView.setOnKeyListener { _, keyCode, event ->
                val isSelect = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                val isMenu = keyCode == KeyEvent.KEYCODE_MENU

                if (isMenu && event.action == KeyEvent.ACTION_UP) {
                    val app = itemAt(bindingAdapterPosition)
                    if (app != null) {
                        onAppMenu(app)
                        return@setOnKeyListener true
                    }
                }

                if (isSelect) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (event.repeatCount == 0) {
                            longPressTriggeredAt = 0L
                        } else if (event.repeatCount > 0 && longPressTriggeredAt == 0L) {
                            longPressTriggeredAt = SystemClock.uptimeMillis()
                            val app = itemAt(bindingAdapterPosition)
                            if (app != null) {
                                onAppMenu(app)
                                return@setOnKeyListener true
                            }
                        }
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        if (longPressTriggeredAt != 0L && (SystemClock.uptimeMillis() - longPressTriggeredAt < 800L)) {
                            return@setOnKeyListener true
                        }
                        itemAt(bindingAdapterPosition)?.let { onLaunchApp(it) }
                        return@setOnKeyListener true
                    }
                }
                false
            }

            itemView.setOnFocusChangeListener { _, hasFocus ->
                applyVisualState(hasFocus, isHovered, itemAt(bindingAdapterPosition))
            }

            if (hoverSupported) itemView.setOnHoverListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_HOVER_ENTER -> {
                        isHovered = true
                        applyVisualState(itemView.isFocused, true, itemAt(bindingAdapterPosition))
                        true
                    }
                    MotionEvent.ACTION_HOVER_EXIT -> {
                        isHovered = false
                        applyVisualState(itemView.isFocused, false, itemAt(bindingAdapterPosition))
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
                    val app = itemAt(bindingAdapterPosition)
                    if (app != null) {
                        onAppMenu(app)
                        true
                    } else false
                } else false
            }
        }

        private fun applyVisualState(hasFocus: Boolean, hovered: Boolean, app: AppEntry?) {
            val isMoving = app != null && app.pkg == movingPackage
            val scaleEffect = focusEffect == 0 || focusEffect == 1
            val focusScale = 1f + (focusScalePct - 100) / 100f
            val targetScale = when {
                isMoving -> focusScale
                hasFocus && scaleEffect -> focusScale
                hasFocus -> 1.03f
                hovered -> 1.03f
                else -> 1.0f
            }

            val glowBoost = if (focusEffect == 1) 1.8f else 1f
            val targetElevation = when {
                hasFocus -> 12f * glowBoost * itemView.resources.displayMetrics.density
                hovered -> 6f * itemView.resources.displayMetrics.density
                else -> 0f
            }

            itemView.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .translationZ(targetElevation)
                .setDuration(animDurationMs.toLong())
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
            val baseFill = when {
                app != null && app.banner != null -> blendColors(themeSurface, 0xFF000000.toInt(), 60)
                app != null -> blendColors(themeSurface, 0xFFFFFFFF.toInt(), 16)
                else -> blendColors(themeSurface, 0xFFFFFFFF.toInt(), 30)
            }

            val density = itemView.resources.displayMetrics.density
            val strokeWidth: Float
            val strokeColor: Int
            val fillColor: Int = when {
                // Fill effect: the whole card tints toward the accent while focused.
                focusEffect == 3 && (hasFocus || isMoving) -> blendColors(baseFill, accentColor, 70)
                else -> baseFill
            }
            when {
                isMoving -> {
                    strokeWidth = 3.5f * density
                    strokeColor = accentColor
                }
                hasFocus && focusEffect == 1 -> {
                    // Glow: thin bright edge; the elevated translationZ does the rest.
                    strokeWidth = 1.5f * density
                    strokeColor = 0xFFFFFFFF.toInt()
                }
                hasFocus && focusEffect == 3 -> {
                    strokeWidth = 1.5f * density
                    strokeColor = accentColor
                }
                hasFocus -> {
                    // 0 (scale) and 2 (outline) share the accent outline.
                    strokeWidth = if (focusEffect == 2) 3.5f * density else 2.5f * density
                    strokeColor = accentColor
                }
                hovered -> {
                    strokeWidth = 1.5f * density
                    strokeColor = (accentColor and 0x00FFFFFF) or 0xCC000000.toInt()
                }
                else -> {
                    strokeWidth = 0f
                    strokeColor = 0
                }
            }

            var drawable = cardDrawable
            if (drawable == null) {
                drawable = SmoothCornerDrawable(
                    cornerRadiusPx = cornerRadiusPx,
                    fillColor = fillColor,
                    strokeColor = strokeColor,
                    strokeWidthPx = strokeWidth,
                )
                cardDrawable = drawable
                binding.cardInner.background = drawable
            } else {
                drawable.cornerRadiusPx = cornerRadiusPx
                drawable.fillColor = fillColor
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

        /** Re-applies only what a config change affects (size, radius, accent, labels). */
        fun applyConfig() {
            applyDimensions()
            val app = itemAt(bindingAdapterPosition)
            updateCardBackground(app, itemView.isFocused, isHovered, app != null && app.pkg == movingPackage)
            if (app != null && app.banner == null) {
                binding.txtLabel.text = app.label
            }
            applyLabelStyle()
        }

        private fun applyLabelStyle() {
            binding.txtLabel.setTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                labelSizeSp.toFloat(),
            )
            binding.txtLabel.visibility =
                if (showLabels) View.VISIBLE else View.GONE
        }

        private fun applyDimensions() {
            // MATCH_PARENT in grid mode so the columns fit exactly, fixed width in carousel
            val targetWidth = if (isGridMode) ViewGroup.LayoutParams.MATCH_PARENT else cardWidthPx
            val lp = itemView.layoutParams ?: ViewGroup.LayoutParams(targetWidth, cardHeightPx)
            if (lp.width != targetWidth || lp.height != cardHeightPx) {
                lp.width = targetWidth
                lp.height = cardHeightPx
                itemView.layoutParams = lp
            }
        }

        fun bind(app: AppEntry) {
            applyDimensions()

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
                binding.txtLabel.text = app.label
            }
            applyLabelStyle()
        }
    }

    companion object {
        private const val TAG = "AppCardAdapter"

        /** Payload used for a style-only config change (see [updateConfig]). */
        const val PAYLOAD_CONFIG = 1
    }
}
