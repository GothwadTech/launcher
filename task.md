# AI Studio Prompt — Gothwad Launcher: Extreme Lightweight Pass (Remove Video/Aerial Features, Cut Memory Footprint)

You are working on the Gothwad Launcher project (Android TV, Jetpack Compose). The goal of this task is to make this launcher as memory-lightweight as physically possible, even at the cost of visual polish or feature richness. The person building this has confirmed via adb shell dumpsys meminfo -d that the app currently uses 200-460MB RSS depending on state, and wants an aggressive, no-compromise reduction. Do not preserve any feature "just in case" — if a feature is not essential to a bare, fast, functional TV launcher, remove it entirely (code, UI, dependencies, assets), not just disable it behind a flag.

Do not leave partial implementations or TODOs. After each task, the app must still build and function correctly for its core purpose: showing installed apps, launching them, and basic settings.

## Task 1 — Completely remove the video/aerial wallpaper system (highest impact)
The live-video-wallpaper feature (WallpaperView.kt's VideoWallpaper composable, BuiltinAerials.kt, BackgroundMediaTracker-adjacent aerial rotation logic in LauncherScreen.kt, PcLauncherScreen.kt, TvLauncherScreen.kt) uses ExoPlayer/Media3 to stream and decode 1080p video continuously. Even when disabled via config flags, the Media3/ExoPlayer library and its native decoder libraries remain bundled in the app, contributing significantly to the "Code" memory category (.so/.jar/.dex mmap) shown in dumpsys meminfo.

1. Delete WallpaperView.kt (the VideoWallpaper composable, trustAllHttpClient) entirely.
2. Delete BuiltinAerials.kt and the aerials.json asset file entirely.
3. Remove all references to aerialWallpaper, config.useBuiltinAerials, config.builtinSource, config.useVideoWallpaper, config.videoUri, wallpaperVersion, and the VIDEO_SPEEDS/config.videoSpeed handling from LauncherScreen.kt, PcLauncherScreen.kt, and TvLauncherScreen.kt.
4. Remove any Settings UI (in SettingsSheet.kt, ModeSelectionDialog.kt, or wherever it lives) that lets the user pick a video wallpaper, choose an aerial source, or set video speed.
5. Remove the Media3/ExoPlayer Gradle dependencies entirely from build.gradle/build.gradle.kts (androidx.media3:media3-exoplayer, androidx.media3:media3-datasource-okhttp, and any other media3-* artifacts) if nothing else in the app uses them after this removal — verify with a project-wide search for any remaining androidx.media3 imports first.
6. The only remaining wallpaper option should be the existing static-image wallpaper (config.useCustomWallpaper, the decodeDownsampled-based sharp/blurred bitmap pair) and the flat preset-color/gradient backgrounds (WALLPAPERS/presetBrush) — both of these are cheap and should stay.
7. Also remove BackgroundMediaDialog.kt if its only purpose was tied to the video-wallpaper/aerial system — check its actual usage first; if it's used for the unrelated "detect and silence background audio ads" feature (BackgroundMediaTracker), keep that part and only remove wallpaper-specific pieces.

## Task 2 — Reduce Jetpack Compose's GPU/graphics memory footprint
1. Search the entire ui/ package for Modifier.graphicsLayer, Modifier.shadow, elevation =, Modifier.blur, and CompositingStrategy usage. Each of these can force a composable onto its own GPU-backed hardware layer/texture. Remove or simplify any that are purely decorative (e.g., unnecessary card elevation/shadows on TV, where drop shadows are barely visible and costly) — flat/no-shadow surfaces should be the default unless a shadow is functionally important (e.g., focus indication).
2. Check AppCard.kt, SmoothCornerShape.kt, and any focus/scale animation code for repeated composables (one per app icon in a grid) that each individually apply graphicsLayer/scale/elevation — with many apps on screen, this multiplies GPU layer count. Where possible, share a single overlay/highlight layer for the focused item instead of giving every single item its own animated layer.
3. Ensure enableR8FullMode/full minification and resource shrinking are enabled for release builds in build.gradle.kts (isMinifyEnabled = true, isShrinkResources = true) so unused Compose/library code doesn't inflate the "Code" memory category.

## Task 3 — Optimize bitmap memory and decoding
1. Reduce icon decode size from 128×128 to 96×96 (still sharp enough on a TV at normal viewing distance) and confirm banners are decoded at the smallest size that still looks acceptable in the actual UI (check where bannerW/bannerH are used and reduce if there's headroom).
2. memoryCache: List<AppEntry>? currently holds every installed app's decoded bitmap in memory indefinitely with no eviction. If the device can have many installed apps, consider whether all of them need their bitmaps fully decoded and held at once, versus decoding lazily as each app becomes visible/scrolled-to and evicting bitmaps for apps that scroll far off-screen. Only implement lazy/evicting loading if it doesn't add meaningful complexity or jank — if the typical app count is small (a TV usually has far fewer apps than a phone), keeping this as-is with the smaller icon size from step 1 may be sufficient; make a judgment call and note which you chose and why.

## Task 4 — Review background polling and listeners
BackgroundMediaTracker.backgroundMediaFlow() polls MediaSessionManager/AudioManager every 1.5 seconds in an infinite loop whenever collected. Confirm this flow is only being collected while the launcher is actually in the foreground and visible (not from a service or while backgrounded) — if it's already scoped correctly, leave the interval as-is; if it's being collected in a scope that outlives the visible launcher UI, fix the scoping so it stops entirely when the launcher isn't the active screen.

## Task 5 — Evaluate whether AccessibilityService / NotificationListenerService are essential
If either is only needed for a specific, occasional feature (e.g., detecting the stock launcher's identity, reading one specific notification type), confirm it isn't doing any continuous heavy processing while otherwise idle.
Do not remove these services outright without understanding their purpose first — summarize what each currently does and confirm with reasoning whether keeping them active is necessary, rather than assuming they should be cut.

## Task 6 (larger, optional — only if Task 1-5 don't reach the target) — Evaluate migrating from Jetpack Compose to Android Views
If, after implementing Tasks 1-5, the app is still meaningfully above a ~100-150MB RSS baseline (measured via adb shell dumpsys meminfo com.gothwad.launcher -d in a representative "everything loaded" state), the remaining overhead is very likely Jetpack Compose's inherent runtime and rendering cost compared to classic Android Views (XML layouts + RecyclerView + View-based animations). This is a substantial rewrite, not a quick fix — do not attempt this in the same pass as Tasks 1-5. If asked to proceed with this separately: it would involve reimplementing LauncherScreen.kt, TvLauncherScreen.kt, PcLauncherScreen.kt, AppCard.kt, and the dialog/sheet composables as XML layouts + Fragments/Activities + a RecyclerView (with RecyclerView.Adapter, following DiffUtil best practices) for the app grid, while preserving all existing functionality (search, categories, settings, security PIN, notifications). Flag this clearly as a separate, larger follow-up task rather than folding it into this pass.

## Verification checklist
- No androidx.media3.* imports remain anywhere in the codebase (unless something else genuinely still needs them — confirm before removing the dependency).
- No Settings UI references video wallpaper, aerials, or video speed anymore.
- App builds and runs correctly; static image wallpaper and preset-color backgrounds still work.

Implement Tasks 1-5 fully in this session. For Task 6, only produce the summary/plan described above — do not start the rewrite unless explicitly asked to in a follow-up.
