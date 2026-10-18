# Changelog

All notable changes to Gothwad Launcher. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow semver, and the
`versionCode` is derived from it (`MAJOR*10000 + MINOR*100 + PATCH`).

Unreleased changes accumulate under **Unreleased** and are moved into a version entry when the tag
is cut.

## [Unreleased]

### Fixed — correctness & stability

- **Cold-start crash on every device: the density mirror was read through `applicationContext`
  while the `Application` was still being attached.** 1.0.79 added `hydrate()` to
  `DensityAdapter.wrapContext()`, which `GothwadApplication.attachBaseContext()` calls, and the
  prefs helper did `context.applicationContext.getSharedPreferences(...)`. Inside
  `attachBaseContext()` that value is **null** by design - `LoadedApk.makeApplication()` assigns
  `mApplication = app` only after `Instrumentation.newApplication()` (which is what calls
  `attachBaseContext`) returns, and `ContextImpl.getApplicationContext()` is literally
  `mPackageInfo.getApplication()` (verified in AOSP 5.1/9/11/14). Result: a `NullPointerException`
  before the first frame, on every start, so the TV showed "Gothwad Launcher keeps stopping" over
  a black screen. `prefs()` now uses the context it is given and returns null instead of throwing
  (pre-unlock the `:watchdog` process cannot read credential-encrypted storage at all), and the
  density entry points (`wrapContext`, `apply`, `init`) are best-effort: a density problem must
  never make HOME unbootable. `tools/ktcheck.py` gained a check that follows the call chain from
  `Application.attachBaseContext` and fails on this pattern.
- **A pre-1.0.79 `uiScale` is no longer interpreted under the new meaning.** `uiScale` was stored,
  shown and read back but never applied, and its index base was `1..5` with `3 = 1.0x` against
  today's `0..4` with `2 = 1.0x`. Reading `3` under the new meaning would have rendered the whole
  UI at 1.15x for every device that updates. `LauncherConfig.configVersion` + `normalizeConfig()`
  reset such a config to the neutral step (1.0x) once; anything picked after the update is
  stamped and kept. Unrelated fields are untouched.
- **UI Scale now actually scales the UI.** The setting was stored and read back but never applied
  to anything; `DensityAdapter` now derives density from a 960 dp design width times the chosen
  scale, and the activity recreates when the effective scale changes. The default moved from index
  3 to index 2 so 1.0× stays neutral.
- **App scanning no longer runs on the main thread.** `AppRepository.scan()` pins itself to
  `Dispatchers.IO`, so a caller cannot accidentally do two `queryIntentActivities` plus N
  `getPackageInfo` round-trips on the UI thread.
- **Two concurrency bugs in the app-art cache:** `validCacheNames` was a plain `HashSet` mutated
  from concurrent `async(IO)` blocks (now synchronised), and `memoryCache!!` could NPE / tear under
  `clearMemoryCache()` (now `@Volatile`, returning the same list instance).
- **Notification dot was invisible** (drawn with zero alpha).
- **Grid could never become empty:** `rebuildAppList()` returned early when the new list was empty.
- **App lock failed open on cold start.** Before the first DataStore emission the effective config
  was an empty default in which nothing is locked; the foreground package is now parked and
  re-checked once the real config arrives.
- **Watchdog no longer hijacks the screen before first unlock.** Shared state moved to
  device-protected storage, and relaunching HOME honours a persisted
  `launchOnBoot || isDefaultHomeApp` policy instead of assuming.
- **Watchdog / boot-shield relaunch worked on Android 10+**, where background-activity-launch rules
  were dropping the old intent. Both now use a shared `homeIntent()` + `launchHomeCompat`.
- **`getRunningAppProcesses()` is no longer treated as authoritative** — it returns null on many
  builds, which the boot shield read as "the launcher died".
- **Opening Settings no longer ANRs.** The root/ADB probe (`su -c id`) ran on the main thread and
  can block for as long as a root prompt waits for the user; the capability badge now resolves on
  IO and shows a "Checking…" state.
- **Corrupt config JSON is logged** before falling back to defaults, instead of resetting the
  launcher silently.
- **`BootReceiver` no longer runs the guard loop inside `goAsync()`** (ANR on every boot); it
  starts the service and returns.
- **`onTrimMemory` dispatched nearly every level as a *critical* trim.** The framework's levels
  are three groups, not one "higher is worse" scale: `UI_HIDDEN` (20) is "our UI just went away"
  and `BACKGROUND/MODERATE/COMPLETE` (40/60/80) are "we are backgrounded", but the old
  `level >= RUNNING_CRITICAL` (15) test caught them all first — so with aggressive trim enabled,
  simply opening Netflix from the launcher killed the user's other background apps (their music
  included). The `TRIM_MEMORY_COMPLETE` branch was unreachable dead code for the same reason.
  Now: 15 = critical, 10 = gentle, 20 = no-op (the user is *using* other apps), 40/60/80 =
  gentle trim while backgrounded.
- **Heartbeat/policy/self-heal files are written atomically** (temp file + `rename`). A plain
  `writeText` truncates first, so the `:watchdog` polling mid-write could read an empty file,
  parse `0`, and conclude "the launcher is dead" — yanking the TV back to HOME mid-movie. The
  shared-file writes also moved off the main thread onto a dedicated heartbeat thread
  (project rule #3: no blocking work on the main thread, not even "a few bytes").
- **The boot shield no longer silences user media.** The 45–90 s guard window silenced *any*
  active audio every 2 s — including a Netflix stream or music the user deliberately started
  after boot. It now stays silent unless the audio plausibly belongs to a boot ad (stock
  launcher in front, our home screen in front, or no identifiable foreground).
- **Cold start no longer runs two full app scans concurrently.** `MainActivity` and
  `TvLauncherFragment` both scan; the scans are serialised now, so the second caller waits and
  hits the memory-cache fast path instead of doubling PackageManager + bitmap work and racing
  the cache-prune step. Per-app artwork decoding is capped at 6 concurrent ops (was unbounded
  `async(IO)` over every installed package — a needless memory spike on 2 GB boxes).
- **`PACKAGE_REMOVED` broadcasts no longer do PackageManager lookups** on the main thread for a
  package that is already gone; corrupt app-art cache files are no longer protected from pruning
  by a cache-name entry for a bitmap that never loaded.
- **Settings: focus was invisible on most controls ("remote navigation pata hi nahi chalta").**
  27 of 33 buttons hard-coded white text while the focused background is solid white — the
  label disappeared exactly when the control received focus. All buttons now use the
  `color_settings_btn_text` state list (dark on the white highlight). The chevron icons and the
  back/close icons had the same bug (fixed-colour drawables that never flipped on focus — the
  chevron state list was even being overwritten with a static-colour drawable), and the
  selected state of a settings row used to look identical to the focused state. Selected now
  draws as the accent-cyan chip, focused stays the white highlight.
- **Settings: entering a sub-page left D-pad focus on the hidden row**, so the remote looked
  dead until a direction key re-triggered focus search. Every page change now moves focus to
  its first control.

### Fixed — security & privacy

- **The hidden-apps reveal code is no longer stored in plain text.** It now uses the same salted
  PBKDF2 primitive as the PINs, is compared in constant time, and is not echoed back into the
  settings field (the dialog says the current code cannot be shown). Existing plain-text codes are
  hashed and cleared on first launch after upgrade.
- **`BLUETOOTH_CONNECT` is actually requested** on Android 12+ when the Quick Dashboard opens.
  When denied, the dashboard reports "unknown" instead of a placeholder remote name and battery.
- **Self-update download hardened:** redirects are followed manually and only to `http(s)` targets
  (the result feeds a package installer), non-2xx responses fail the download, connections are
  disconnected in `finally`, and progress is dispatched on the main thread only when the integer
  percentage changes (it used to dispatch per 8 KB chunk).
- **`installViaAdbShell` uses bounded subprocesses** (8 s) and reports success only on exit code 0
  with a "Success" response, accumulating the last error otherwise.
- **Aggressive memory trimming is now opt-in and off by default.** It used to kill other apps —
  including whatever was playing — on every launch.

### Removed

- Dead configuration fields: `categories`, `sections`, `knownApps`, `autoCategoryOnInstall`,
  `iconScale`, `showCategoryNames`, `showAppLabels`, `layout`, `vpnApp`, `showVpnButton`. Old
  configs still decode (`ignoreUnknownKeys`); a test locks that in.
- `MainActivity` no longer wrote an auto-category assignment for every new install — a JSON encode
  plus a DataStore write per scan, for data no screen read.
- Dead `MainActivity` code: a `dispatchKeyEvent` override that only called `super`, and an empty
  `setupNavigation()` stub (NavHostFragment loads the start destination itself).
- `CategoryAssigner` and its test, `layout_a11y_banner.xml`, 8 unused drawables, the orphaned
  `cd_vpn` content description and 4 banner strings.
- Dead signing steps: `jarsigner` (produced v1-only signatures for the AAB) and `apksigner sign`
  (Gradle signs both artifacts itself; only `apksigner verify --print-certs` remains).
- The duplicate GitHub Release step in `build_apk.yml`, and its push-to-`main` trigger — two
  workflows were publishing releases with colliding `v1.0.<run_number>` tags.

### Changed

- `DensityAdapter.setUiScale` / `setRespectSystemFontScale` persist only on a real change; both
  run on every config emission, and re-writing the same value each time was pointless flash wear.
- Wallpaper decoding is down-sampled to the physical screen size and only re-decodes when the
  source file changed; it used to re-decode the full-resolution JPEG on every config emission.
- Status-bar clusters get focus/hover feedback via a `StateListDrawable` built from one helper.
- ~29 hard-coded English strings moved to resources (locks, accessibility alert, search, Quick
  Dashboard, settings titles and subtitles, version text, self-update capability badge). 260
  literals remain — tracked in [docs/I18N.md](docs/I18N.md).
- `Config.uiScale` documented as an index (0..4), not a multiplier.
- Signing env vars are read through `providers.environmentVariable` so the configuration cache
  tracks them; `-PlocalSign` now uses the project's `debugConfig` rather than AGP's built-in
  `debug`.
- Unit tests return default values for unmocked framework calls
  (`unitTests.isReturnDefaultValues = true`).

### Added

- **Visual wallpaper picker:** the five name-only buttons (whose labels had drifted away from the
  gradients they rendered) are now tiles that show the *actual* gradient, with a live preview box
  above and a check badge on the committed choice — pick "this picture", not "a name".
- **Auto-change wallpapers:** Off / 15 min / 1 hour / 6 hours / Daily. Rotates through the preset
  gradients via the existing 1 Hz clock loop (no alarms, no services); a custom photo is never
  auto-replaced, and a long process absence advances exactly one step.
- **Theme & Style settings page** (the 100-feature customization wave, see
  `docs/FEATURE_ROADMAP_100.md`): 10 full themes with visual tiles, 10 accent colours,
  wallpaper-palette accent, 5 new wallpapers (10 total), 4 layout modes (banner / compact /
  large / icon grid), 3–8 columns, app size 60–160%, tile label show + size, 4 focus effects
  (scale / glow / outline / fill) with focus scale, animation speed + reduced-motion +
  app-open transitions, performance profiles, clock styles + date formats + time-of-day
  greeting, night-light warm overlay, 4 app sort modes, favourites + recents quick row,
  long-press app quick menu (launch / info / uninstall / favourite / hide / lock), and
  settings backup export/import as JSON through the system file picker.
- **CI workflow** (`ci.yml`): every pull request and push to `main` builds debug **and release**
  (minified), runs unit tests and lint, and publishes the debug APK plus reports as artifacts.
- Composite actions `resolve-version` (monotonic version code from the version name) and
  `prepare-signing` (keystore from secrets, alias auto-detection, always-cleaned temp file).
- Unit tests: `ConfigSerializationTest` (5), `LockSecurityTest` (10),
  `ProcessHeartbeatAtomicWriteTest` (3, locks in the atomic shared-file write),
  `WallpaperRotatorTest` (6, auto-change rotation logic) and
  `CustomizationConfigTest` (7, theme/wallpaper/accent registries + config round-trip).
- Developer documentation under `docs/`: architecture, config reference, security, performance,
  CI & release, testing, troubleshooting, i18n, issue log, roadmap.
- Issue and pull-request templates, `CONTRIBUTING.md`, and this changelog.

## [1.0.6]

- Status bar centre cluster (clock/date between the left and right groups).
- Previous release as published; no changelog was kept before this file existed.

[Unreleased]: https://github.com/GothwadTech/launcher/compare/main...HEAD
