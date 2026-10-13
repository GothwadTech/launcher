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
- `CategoryAssigner` and its test, `layout_a11y_banner.xml`, 8 unused drawables, the orphaned
  `cd_vpn` content description and 4 banner strings.
- Dead signing steps: `jarsigner` (produced v1-only signatures for the AAB) and `apksigner sign`
  (Gradle signs both artifacts itself; only `apksigner verify --print-certs` remains).
- The duplicate GitHub Release step in `build_apk.yml`, and its push-to-`main` trigger — two
  workflows were publishing releases with colliding `v1.0.<run_number>` tags.

### Changed

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

- **CI workflow** (`ci.yml`): every pull request and push to `main` builds debug **and release**
  (minified), runs unit tests and lint, and publishes the debug APK plus reports as artifacts.
- Composite actions `resolve-version` (monotonic version code from the version name) and
  `prepare-signing` (keystore from secrets, alias auto-detection, always-cleaned temp file).
- Unit tests: `ConfigSerializationTest` (5) and `LockSecurityTest` (10).
- Developer documentation under `docs/`: architecture, config reference, security, performance,
  CI & release, testing, troubleshooting, i18n, issue log, roadmap.
- Issue and pull-request templates, `CONTRIBUTING.md`, and this changelog.

## [1.0.6]

- Status bar centre cluster (clock/date between the left and right groups).
- Previous release as published; no changelog was kept before this file existed.

[Unreleased]: https://github.com/GothwadTech/launcher/compare/main...HEAD
