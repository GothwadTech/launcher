package com.gothwad.launcher.ui.view

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.launcher.data.AppEntry
import com.gothwad.launcher.data.CategoryCfg
import com.gothwad.launcher.data.CORNER_RADII
import com.gothwad.launcher.data.LauncherConfig
import com.gothwad.launcher.databinding.ViewTvLauncherGridBinding

@Composable
fun TvNativeAppGrid(
    modifier: Modifier = Modifier,
    categorized: List<Pair<CategoryCfg, List<AppEntry>>>,
    config: LauncherConfig,
    accent: Color,
    cardWidth: Dp,
    gap: Dp,
    movePkg: String?,
    onLaunchApp: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    val density = LocalDensity.current
    val cardWidthPx = with(density) { cardWidth.roundToPx() }
    val cardHeightPx = (cardWidthPx * 9) / 16
    val gapPx = with(density) { gap.roundToPx() }
    val cornerRadiusDp = CORNER_RADII[config.cornerRadius.coerceIn(0, CORNER_RADII.size - 1)]
    val cornerRadiusPx = with(density) { cornerRadiusDp.toPx() }
    val accentInt = accent.toArgb()

    val recycledPool = remember { RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, 30) } }

    val rowItems = remember(categorized) {
        categorized.map { (cat, apps) -> CategoryRowItem(cat, apps) }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val binding = ViewTvLauncherGridBinding.inflate(LayoutInflater.from(context))
            val layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            binding.recyclerCategories.layoutManager = layoutManager
            binding.recyclerCategories.setHasFixedSize(true)

            val adapter = TvCategoryAdapter(
                recycledViewPool = recycledPool,
                cardWidthPx = cardWidthPx,
                cardHeightPx = cardHeightPx,
                cornerRadiusPx = cornerRadiusPx,
                gapPx = gapPx,
                accentColor = accentInt,
                showCategoryNames = config.showCategoryNames,
                showAppLabels = config.showAppLabels,
                lockedPackages = emptySet(),
                movingPackage = movePkg,
                onLaunchApp = onLaunchApp,
                onAppMenu = onAppMenu,
            )
            binding.recyclerCategories.adapter = adapter
            adapter.submitList(rowItems)
            binding.root.tag = adapter
            binding.root
        },
        update = { view ->
            val adapter = view.tag as? TvCategoryAdapter ?: return@AndroidView
            adapter.updateConfig(
                widthPx = cardWidthPx,
                heightPx = cardHeightPx,
                radiusPx = cornerRadiusPx,
                gap = gapPx,
                accent = accentInt,
                categoryNames = config.showCategoryNames,
                appLabels = config.showAppLabels,
                locked = emptySet(),
                moving = movePkg,
            )
            adapter.submitList(rowItems)
        }
    )
}
