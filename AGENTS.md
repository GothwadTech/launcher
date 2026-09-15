# Gothwad Launcher Developer & Architecture Rules

## Architecture: Native Android Views Only (No Jetpack Compose)

This project has been **fully migrated from Jetpack Compose to 100% native Android Views** (XML layouts + Fragments/Activities + ViewBinding + RecyclerView + DialogFragment/BottomSheetDialogFragment).

### Strictly Forbidden:
- **DO NOT** add or use `androidx.compose.*` dependencies or `@Composable` functions.
- **DO NOT** use `androidx.compose.ui.viewinterop.AndroidView`, `ComposeView`, or any Compose/View interop bridge. Mixing Compose and Views created a severe documented memory regression (Graphics memory doubled from 81MB to 152MB and Code memory increased from 73MB to 107MB).
- **DO NOT** enable `buildFeatures { compose = true }` or the Compose compiler plugin.

### Standard Practices:
- Use ViewBinding (`buildFeatures { viewBinding = true }`) for layout binding.
- Use XML layouts under `res/layout/`.
- Use `DialogFragment` and `BottomSheetDialogFragment` with ViewBinding for all modals, sheets, and popups.
- Use `RecyclerView` with optimized `DiffUtil.ItemCallback` and `RecyclerView.RecycledViewPool` for app grids and lists.
- Use `SmoothCornerDrawable` (backed by `androidx.graphics.shapes`) and `AppIcons` for high-performance vector rendering.
- State management uses Kotlin Coroutines, `StateFlow`, and lifecycle-aware coroutines (`repeatOnLifecycle`, `lifecycleScope`).
