# Gothwad Launcher — Full Compose → Android Views Migration Plan

## How to use this file

This is a **6-phase migration**, not a single task. Feed **one phase at a time** to Google AI Studio, as its own separate prompt/session. After each phase:
1. Build the app.
2. Install and run it on the actual test device.
3. Confirm the app doesn't crash and the converted screen(s) look/behave correctly.
4. Only then move to the next phase's prompt.

Do this for two reasons:
- **It protects your AI Studio usage credits** — six focused prompts cost far less total than one giant one, and if a session runs out of quota mid-way, you only lose that one phase's progress, not the whole migration.
- **It prevents another hybrid-mess regression.** The previous attempt silently mixed Compose and Views (via `AndroidView`) because the task scope wasn't broken down — this plan explicitly forbids that in every phase.

**Global rule for every phase (repeat this at the top of whatever you paste into AI Studio, it's included in each phase prompt below):**

> This project is being fully migrated off Jetpack Compose to classic Android Views (XML layouts + Fragments/Activities + ViewBinding). Do not use `androidx.compose.ui.viewinterop.AndroidView`, `ComposeView`, or any other Compose/View interop bridge at any point, even temporarily — this creates a documented memory regression (confirmed via `adb shell dumpsys meminfo -d`: Graphics/GPU memory went from 81MB to 152MB and Code memory from 73MB to 107MB when a partial Compose+RecyclerView hybrid was attempted). Every screen must be either fully Compose (temporarily, until its phase is reached) or fully Views (once migrated) — never both at once for the same screen.

---

## Phase 1 — Foundation: Gradle setup + MainActivity shell

**Goal:** Get the project building with a View-based `MainActivity` shell and the necessary Views/AndroidX dependencies in place, without touching any actual screen content yet. The existing Compose screens can still be launched from the old code path temporarily during this phase only — full removal of Compose happens in Phase 6.

Prompt for AI Studio:

> Add the necessary Android Views dependencies to `app/build.gradle.kts` for a Views-based UI: `androidx.constraintlayout:constraintlayout`, `com.google.android.material:material`, `androidx.viewpager2:viewpager2` (if needed for any swipeable UI later), and confirm `androidx.recyclerview:recyclerview` (already present) stays. Enable `viewBinding = true` in the `buildFeatures` block. Do not remove any existing Compose dependencies yet — that happens in a later phase. Create a new `MainActivity` entry point (or prepare the existing `MainActivity.kt` to be converted) that will eventually host a single-Activity, multiple-Fragment architecture using the Jetpack Navigation component (`androidx.navigation:navigation-fragment-ktx`, `androidx.navigation:navigation-ui-ktx`) — set up a basic `nav_graph.xml` with one placeholder empty Fragment destination for now, just to confirm the navigation shell builds and runs without crashing. Do not migrate any real screen content in this phase. Confirm the app builds and launches to a blank placeholder screen.

---

## Phase 2 — App grid screens (TV + PC) as native Views

**Goal:** Convert the core home-screen app grid (`LauncherScreen.kt`, `TvLauncherScreen.kt`, `PcLauncherScreen.kt`) into Fragments with XML layouts. The project already has a partial start on this (`TvAppGridRecyclerView.kt`, `AppCardAdapter.kt`, `AppCardDiffCallback.kt`, `TvCategoryAdapter.kt`) — reuse and complete this work, but remove the `AndroidView` Compose-interop wrapper currently in `TvAppGridRecyclerView.kt` since the RecyclerView will now live directly inside a native Fragment's XML layout, with no Compose involved at all for this screen.

Prompt for AI Studio:

> Convert the TV home-screen app grid to a fully native Fragment (e.g. `TvLauncherFragment`) with an XML layout containing a `RecyclerView` directly (no `AndroidView`/Compose interop — remove the `AndroidView` wrapper currently in `TvAppGridRecyclerView.kt` and inline that RecyclerView setup directly into the new Fragment using ViewBinding). Reuse the existing `AppCardAdapter.kt`, `AppCardDiffCallback.kt`, and `TvCategoryAdapter.kt` — they're already Views-based and don't need Compose removed from them, just wire them into the new native Fragment instead of into a Compose `AndroidView` host. Do the same for the PC/tablet variant (a `PcLauncherFragment` equivalent to `PcLauncherScreen.kt`'s layout, e.g. a different grid arrangement or `ViewPager2`/`RecyclerView` layout manager suited to touch/mouse navigation instead of D-pad). Preserve all existing behavior: category switching, focus/D-pad navigation between app cards, app launching on click/select, long-press context actions if any exist in the current Compose version. Wire the appropriate Fragment (TV vs PC layout) into the navigation graph from Phase 1, replacing the placeholder destination, chosen based on the same device-type detection logic the current Compose code uses. Do not touch dialogs, settings, search, or any other screen in this phase — only the core app-grid home screen.

---

## Phase 3 — App card visuals, icons, and shapes as native Views/Drawables

**Goal:** Convert `AppCard.kt`'s Compose visuals (if any part of it still needs converting beyond the already-native adapter from Phase 2), `Icons.kt`, and `SmoothCornerShape.kt` into native View/Drawable equivalents.

Prompt for AI Studio:

> Convert `SmoothCornerShape.kt` (the iOS-style continuous/squircle corner shape currently implemented as a Compose `Shape`) into an equivalent Android `Drawable` (e.g. a custom `Drawable` subclass drawing the same squircle path via `android.graphics.Path`, or an XML `<shape>`/`<vector>` approximation if visually close enough) usable as a View background. Convert any remaining Compose-specific icon rendering in `Icons.kt` into a plain Kotlin object/function that returns `Drawable`/`Bitmap` resources for use in `ImageView`s within the Views-based `AppCardAdapter` from Phase 2. Ensure app card focus/selection visual states (scale, highlight, border) are implemented using native View animation APIs (`ViewPropertyAnimator`, `StateListAnimator`, or `AnimatedVectorDrawable`) rather than Compose animation APIs, and confirm these do not use excessive `setLayerType(LAYER_TYPE_HARDWARE, ...)` calls per-item (batch/share hardware layers only where genuinely needed for animation smoothness, consistent with the project's memory-reduction goals). Update `AppCardAdapter.kt`'s `onBindViewHolder` to use these new native shape/icon/animation implementations.

---

## Phase 4 — Dialogs and sheets as DialogFragments/BottomSheetDialogFragments

**Goal:** Convert all remaining dialog/sheet screens to native Views-based dialogs. This is the largest remaining phase — consider splitting it further into 2-3 sub-sessions if AI Studio's usage runs low (e.g. "Settings + Search" as one sub-session, "Security + SetupWizard" as another, "Notifications + QuickDashboard + WeatherDetails + VoiceSearch + BackgroundMediaDialog" as a third).

Prompt for AI Studio (do this list, or split into sub-batches as noted above):

> Convert the following Compose dialogs/sheets into native Views-based equivalents, each as a `DialogFragment` or `BottomSheetDialogFragment` (using `com.google.android.material.bottomsheet.BottomSheetDialogFragment` where the current Compose version behaves like a bottom sheet) with its own XML layout and ViewBinding, preserving all existing functionality and options exactly:
> - `SettingsSheet.kt` → settings screen (all existing toggles/options, including the wallpaper, icon, and behavior settings that remain after the video-wallpaper removal)
> - `SearchDialog.kt` → app/content search
> - `SecurityDialogs.kt` and `SecuritySettings.kt` → PIN lock setup/entry screens
> - `SetupWizard.kt` → first-run setup flow
> - `ModeSelectionDialog.kt` → TV vs PC mode selection
> - `NotificationSheet.kt` → notification list sheet
> - `QuickDashboardDialog.kt` → quick dashboard overlay
> - `WeatherDetailsDialog.kt` → weather detail popup
> - `VoiceSearchDialog.kt` → voice search UI (keep this wired to the existing `VoiceSearchHelper.kt` logic, which is unrelated to Compose and needs no changes)
> - `BackgroundMediaDialog.kt` → background media detection dialog
>
> For each, remove the Compose-based implementation only after its native replacement is confirmed working, and update whatever code currently shows/launches these Compose dialogs (from `LauncherScreen.kt`/`MainActivity`/the Fragments from Phase 2) to instead show the new `DialogFragment`/`BottomSheetDialogFragment`. Do not use `ComposeView` or `AndroidView` as an intermediate step for any of these — build them as plain XML layouts from the start.

---

## Phase 5 — Theme and status bar

**Goal:** Convert `Theme.kt` (Compose `MaterialTheme`/color scheme) and `StatusBar.kt` into native `styles.xml`/`themes.xml` and a native status bar View (likely a persistent View in the main Activity layout or a small Fragment).

Prompt for AI Studio:

> Convert `Theme.kt`'s color scheme, typography, and shape definitions into equivalent `res/values/themes.xml` and `res/values/colors.xml`/`styles.xml` definitions for use by the native Views/Fragments built in Phases 1-4. Convert `StatusBar.kt` (the persistent clock/status/notification-icons bar, if that's what it is — inspect its current Compose implementation first and describe what it shows) into a native View or small Fragment hosted permanently in the main Activity's XML layout, functionally identical to its current behavior. Update all previously-converted Fragments/DialogFragments from Phases 2-4 to use the new native theme resources instead of any remaining Compose theme references.

---

## Phase 6 — Full Compose removal and final verification

**Goal:** Remove Jetpack Compose entirely from the project and confirm the memory improvement.

Prompt for AI Studio:

> Search the entire codebase for any remaining Compose imports (`androidx.compose.*`), `@Composable` functions, `setContent { }` calls, `ComposeView`, or `AndroidView` usage — there should be none left after Phases 1-5. Delete any leftover now-unused Compose source files (the original `.kt` files for `LauncherScreen.kt`, `TvLauncherScreen.kt`, `PcLauncherScreen.kt`, `AppCard.kt`, and all the dialog files from Phase 4, if their content was fully ported and they're no longer referenced anywhere). Remove all Compose-related dependencies from `app/build.gradle.kts`: the `compose-bom` platform, `androidx.compose.ui:ui`, `androidx.compose.foundation:foundation`, `androidx.compose.material3:material3`, `androidx.tv:tv-material`, `androidx.graphics:graphics-shapes` (only if nothing non-Compose still needs it — check first), `androidx.activity:activity-compose`, `androidx.lifecycle:lifecycle-runtime-compose`. Remove the `buildFeatures { compose = true }` flag and the Compose compiler plugin/extension configuration from `build.gradle.kts`. Confirm the project builds successfully after all removals with zero remaining Compose references. Confirm `isMinifyEnabled = true` and `isShrinkResources = true` remain enabled for release builds.

**After Phase 6, re-measure and compare:**

```
adb shell dumpsys meminfo com.gothwad.launcher -d
```

Capture this in the same "everything loaded" state used for previous measurements (all categories opened, a dialog or two shown) and compare the "Graphics" and "Code" categories against the two previous measurements (81MB/73MB before any changes, 152MB/107MB after the hybrid regression) to confirm the full-Views version is now meaningfully lower than both — this is the actual proof the migration achieved its goal.

---

## Notes

- If any phase's AI Studio session runs low on usage credit mid-phase, stop, let quota reset, and re-run the *same* phase's prompt again (each phase prompt is self-contained and scoped to specific files, so resuming is safe) rather than trying to combine it with the next phase.
- Do not skip ahead to a later phase while an earlier phase still has Compose/Views mixed for the same screen — verify each phase builds and runs cleanly first.
- Keep `AGENTS.md` (from the browser project, or create an equivalent for the launcher if one doesn't exist yet) updated with a note once this migration is complete, so future AI Studio sessions know the launcher is Views-only and should never reintroduce Compose or `AndroidView`/`ComposeView` bridges.
