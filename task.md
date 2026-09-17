# Gothwad Launcher — Reliability Hardening + Remote Button Mapping

## How to use this file
Same rule as `task.md`: feed **one phase at a time** to Google AI Studio, as its own separate prompt/session. After each phase, build, install on the real test device, confirm nothing regressed, then move on. Do not skip ahead or merge phases — each phase touches memory-sensitive/system-sensitive code and needs to be verified in isolation on the actual STB.

**Global rule for every phase (paste this at the top of every AI Studio prompt below):**

> This is a low-RAM (2GB) Android TV set-top-box launcher (`com.gothwad.launcher`) that must run reliably as the default HOME app on restricted, non-rooted OEM firmware (Jio STB, Airtel Xstream, generic Android TV, and Google TV certified devices). Do not add any dependency that requires root, a system-app install, or a custom ROM. Every new background component must be extremely lightweight — this project already reduced RSS from 400MB+ to under 90MB and that must not regress. Do not reintroduce Jetpack Compose or `AndroidView` interop anywhere (see the existing migration rule in `task.md`).

---

## Phase 1 — Application-level memory guardian (stop the crash before it happens)

**Goal:** Give the launcher its own automatic background-app management, since it can no longer assume a third-party "app optimizer" is installed.

**Context for AI Studio:** Today `Actions.kt` has a `close(pkg)` function that calls `ActivityManager.killBackgroundProcesses(pkg)`, but it is only ever invoked manually by the user from the UI. There is no `Application` subclass and no `onTrimMemory`/`onLowMemory` override anywhere in the project, so the launcher never reacts to system memory pressure on its own.

Prompt for AI Studio:

> Create a new `GothwadApplication : Application()` class (register it as `android:name` in `AndroidManifest.xml`). Inside it, maintain an in-memory, timestamp-ordered LRU list of package names the user has launched via `Actions.launchApp` (add a hook there to report launches to this tracker — keep it a simple singleton object, no DB). Override `onTrimMemory(level: Int)`: when `level >= TRIM_MEMORY_RUNNING_LOW`, call `ActivityManager.killBackgroundProcesses()` on every tracked package **except** `com.gothwad.launcher` itself, the single most-recently-launched package, and a small hardcoded protect-list (system launcher/settings packages). When `level >= TRIM_MEMORY_RUNNING_CRITICAL`, also drop everything except the current foreground package. Also override `onLowMemory()` to do the same critical-level cleanup as a fallback for OEM builds that don't reliably deliver `onTrimMemory`. Do not touch UI code in this phase — this is a pure background service class. Make sure this logic runs on a background thread/coroutine, never blocking the main thread, since the whole point is to prevent the main thread from ever stalling under memory pressure.

---

## Phase 2 — Crash & ANR self-healing watchdog (no reboot required)

**Goal:** If the launcher process ever does die or hang despite Phase 1, it must come back **by itself within seconds**, without the user needing to power-cycle the box.

**Context for AI Studio:** `BootReceiver.kt` only fires on `ACTION_BOOT_COMPLETED`/`QUICKBOOT_POWERON`. There is currently no mechanism that detects "the launcher process died or is hung while the device stayed powered on" and relaunches it. Also, `Thread.setDefaultUncaughtExceptionHandler` is never set, so a crash falls straight through to the default Android crash dialog.

Prompt for AI Studio:

> 1) In `GothwadApplication.onCreate()`, install a `Thread.setDefaultUncaughtExceptionHandler` that: logs the exception to a small rolling file in `filesDir` (for later diagnosis), then schedules an immediate relaunch of `MainActivity` via `AlarmManager.setExactAndAllowWhileIdle` (1-2 seconds out) using the same intent flags `LauncherAccessibilityService.launchHome()` already uses, before calling through to the previous default handler (so the crash still gets reported to the system, but the relaunch is already queued).
> 2) Add a second, minimal always-alive component running in its own process (`android:process=":watchdog"` in the manifest) — a small `Service` (or `WorkManager` periodic worker if `:watchdog` process complicates things — pick whichever is more reliable on Android 8-14) that, every ~10 seconds, checks whether `com.gothwad.launcher`'s main process/`MainActivity` is alive and in the foreground (use `ActivityManager.RunningAppProcessInfo` for the main process's importance, since that check does not have the Android 10+ foreground-task restriction that `getRunningTasks` has). If the main process is dead, or if it has been in the "not responding" state, immediately fire the same relaunch intent. This watchdog process must be trivially small in memory (a plain `Handler`/coroutine loop with `delay()`, no libraries) so it doesn't itself become a memory problem on a 2GB device.
> 3) This watchdog must self-start on boot alongside `BootReceiver`, and must restart itself if the system kills it (`START_STICKY` if using a `Service`).

---

## Phase 3 — Guaranteed 100% boot-time recovery on ANY device (Jio / Airtel / Google TV / Android TV certified / restricted)

**Goal:** If Phases 1-2 somehow still fail to keep it alive, the very next boot must deterministically bring the launcher back to foreground and keep it there, on every device type — including fully Android-TV-certified/restricted firmware where APIs behave differently than on Jio's looser STB fork.

**Context for AI Studio — an important existing bug to fix:** `BootReceiver.kt`'s 45-second "boot shield" loop currently calls `ActivityManager.getRunningTasks(1)` to detect the stock launcher stealing the foreground. **On Android 10 (API 29) and above, `getRunningTasks()` is restricted and only returns the calling app's own tasks for any non-system app** — so on modern, properly-restricted Android TV / Google TV certified builds this check silently does nothing useful; it may only have appeared to work on Jio's box because that OEM fork is looser about this restriction. This needs to be fixed for the feature to actually be reliable "on any device."

Prompt for AI Studio:

> Replace the `getRunningTasks(1)`-based foreground detection in `BootReceiver.kt`'s boot-shield loop with `UsageStatsManager.queryEvents()` (the app already has `PACKAGE_USAGE_STATS` permission and already uses `UsageStatsManager` in `SmartServices.kt`'s `UsageTracker`), watching for `UsageEvents.Event.MOVE_TO_FOREGROUND` events and checking the most recent event's package name — this works correctly on all Android versions and device restriction levels, unlike `getRunningTasks`. Keep a graceful fallback to the old `getRunningTasks` check only for devices where the usage-stats permission isn't granted yet, wrapped in `runCatching`.
> Also make the boot-shield window adaptive instead of a fixed 45 seconds: read `ActivityManager.MemoryInfo` / total RAM at boot, and extend the window (e.g. up to 90 seconds) on devices with 2GB or less RAM, since boot ads and OEM launcher races take longer to resolve on weaker hardware.
> Also register `BootReceiver` for `Intent.ACTION_MY_PACKAGE_REPLACED` in the manifest, so recovery re-arms itself automatically right after any app update, without waiting for a reboot.
> Finally, make the relaunch itself retry with backoff (e.g. up to 5 attempts, doubling delay) instead of a single `startActivity` call, since on some restricted/certified firmware the very first `startActivity` call immediately after boot can silently fail before the system is fully settled.

---

## Phase 4 — Recover from Android's automatic accessibility-service disable

**Goal:** When Android auto-disables `LauncherAccessibilityService` after a crash/ANR (a built-in OS safety behavior, not a bug in this app), the user must be told immediately and clearly instead of discovering it later as "HOME button / auto-return-to-launcher stopped working."

Prompt for AI Studio:

> On every app start (`MainActivity.onCreate`) and inside the Phase 2 watchdog's periodic check, call the existing `LauncherAccessibilityService.isEnabled(context)`. If it returns `false` after previously having been enabled (track this with a simple `SharedPreferences` flag `"accessibility_was_enabled"`), show a persistent, high-visibility on-screen banner/notification (not just a Toast) that stays until dismissed, explaining that Android disabled the accessibility permission after a crash and that it needs to be re-enabled, with a button that calls the existing `Actions.openAccessibilitySettings(context)`. Do not silently keep working in a degraded mode — the user must always know why HOME-button interception has stopped.

---

## Phase 5 — Remote button mapping (Settings feature)

**Goal:** Dedicated hotkeys on different STB remotes (e.g. a physical "YouTube" button) currently do nothing in this launcher, because only `KEYCODE_HOME` is intercepted today. Add both an automatic default mapping and a manual "learn this button" mapping system.

**Context for AI Studio:** `LauncherAccessibilityService` already has `FLAG_REQUEST_FILTER_KEY_EVENTS` set and already implements `onKeyEvent()`, but it only checks for `KeyEvent.KEYCODE_HOME` — every other key currently falls through unused. `LauncherConfig` in `Config.kt` is where all persisted settings already live via the existing `ConfigStore`/DataStore mechanism.

Prompt for AI Studio:

> 1) Add `val buttonMap: Map<Int, String> = emptyMap()` to `LauncherConfig` in `Config.kt` (keyCode → target package name, using the existing `kotlinx.serialization` setup already used for the rest of the config — `Map<Int, String>` serializes fine as-is).
> 2) Seed a hardcoded default map of well-known STB/TV-remote dedicated hotkey codes (e.g. `KeyEvent.KEYCODE_PROG_RED/GREEN/YELLOW/BLUE`, `KEYCODE_GUIDE`, `KEYCODE_CAPTIONS`, `KEYCODE_TV`, and any others you can find in the Android `KeyEvent` reference that commonly map to streaming-app hotkeys) to sensible default target packages (YouTube, Netflix, Prime Video, etc. — only apply a default if that package is actually installed on the device, check via `Actions.isInstalled`). Apply these defaults only once, on first run, and only for keys the user hasn't already mapped.
> 3) In `LauncherAccessibilityService.onKeyEvent()`, before/after the existing `KEYCODE_HOME` check, look up `event.keyCode` in the current `LauncherConfig.buttonMap` (read via `ConfigStore` — cache the latest config in the service via the existing config flow so this lookup is not a blocking read on every keystroke) and if a mapping exists, call `Actions.launchApp(this, mappedPkg)` and consume the event (`return true`), the same way `KEYCODE_HOME` does. Never intercept keys with no mapping — let them pass through as today.
> 4) Add a new **"Button Mapping"** section inside `SettingsBottomSheetFragment.kt` (or its own `DialogFragment`/`BottomSheetDialogFragment` if that fits the existing settings structure better) with:
>    - A list of currently mapped buttons (shown as "Button code NNN → App label", with the default hotkey names shown in plain English where known, e.g. "Red button → Netflix") and a remove (✕) action per row.
>    - An "Add mapping" flow: tapping it shows a "Press the button on your remote now…" prompt; the very next raw key event the `LauncherAccessibilityService` receives (add a temporary listen-mode flag the service checks, communicated via a `SharedFlow`/broadcast back to the settings UI so it isn't tied to `KEYCODE_HOME`-only handling) is captured — including unknown/vendor-specific key codes — and shown to the user as "Detected button code: NNN". The user then picks any installed app from a list (`AppRepository`'s existing scanned list) to map it to, and it's saved into `LauncherConfig.buttonMap` via `ConfigStore.update`.
>    - Make sure `KEYCODE_HOME`, `KEYCODE_BACK`, and D-pad navigation keys cannot be remapped (skip/reject them in the "press a button" capture step with a clear message), since those are reserved for core launcher navigation.

---

## Phase 6 — Verification checklist (do this after every phase, on the real box)

Prompt for AI Studio (use as a final review pass after all phases are merged):

> Review the full diff across Phases 1-5 together and confirm: (a) nothing added here can block the main/UI thread even transiently — all `ActivityManager`/`UsageStatsManager`/DataStore calls involved must be off the main thread; (b) the new `:watchdog` process, `GothwadApplication`'s LRU tracker, and the button-mapping cache together add no more than a few MB of steady-state RAM, consistent with the project's 90MB budget; (c) every new permission-gated code path (usage stats, accessibility) degrades gracefully with `runCatching`/null-checks when the permission isn't granted, instead of crashing; (d) `buttonMap` defaults and the accessibility re-enable banner both work correctly on a device where `PACKAGE_USAGE_STATS` has not been granted at all.

Manually re-test this exact scenario after Phase 1-3 are done: open Chrome, then YouTube (without closing Chrome), then the sideloaded browser, then Spotify, then try to open a 5th app — the launcher must not hang; if it ever does, it must recover to a working home screen within a few seconds without any manual ADB intervention or reboot.
