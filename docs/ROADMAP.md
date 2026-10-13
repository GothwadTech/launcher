# Roadmap

Known remaining work, ordered by (user impact ÷ risk). Each item states why it is not already
done — mostly "needs a device or a compiler the review environment does not have".

## Soon

### 1. Finish string extraction (260 literals)
Mechanical, no design decisions, but it must be staged because there is no local compiler to catch
a wrong `R.string` id. Plan and per-file inventory in [I18N.md](I18N.md). Start with the PIN
dialogs (security screens), then the dashboard and notifications sheet, then `sheet_settings.xml`.

### 2. Lint gate
`lint { abortOnError = false }` today, because the existing baseline is not clean. Once the string
work above lands (that is most of the `HardcodedText` volume), generate a baseline and flip it:

```kotlin
lint { abortOnError = true; baseline = file("lint-baseline.xml"); warningsAsErrors = false }
```

Then CI blocks regressions instead of only reporting them.

### 3. Release integrity check
Publish a SHA-256 per release asset (e.g. a `checksums.txt` alongside the APK) and verify it in
`AppUpdateManager` after download, before handing the file to the installer. Current guarantee is
HTTPS + the installer's signature check, which is real but not independent.

### 4. Baseline profile generated, not hand-written
The shipped `baseline-prof.txt` is hand-authored because there is no Macrobenchmark module.
Setting one up, plus a startup-time benchmark, is described step by step in
[baseline-profile.md](baseline-profile.md). Needs a device or emulator with the benchmark build
type — CI cannot do it today.

## Later

### 5. Split `sheet_settings.xml` (2 110 lines, ~200 views)
Inflate cost and a single monolithic binding class. The blocker is that ViewBinding does not
expose views inside an id-less `<include>`, so every split changes `binding.someView` to
`binding.pageDisplay.someView` across `SettingsBottomSheetFragment` **and all six delegates** —
hundreds of references. Do it as a standalone PR with nothing else in it, ideally with a compiler
available.

### 6. One app list, not two scans
`MainActivity.refreshAppsList()` and `TvLauncherFragment` both call `AppRepository.scan()` at
startup. The second hits the memory-cache fast-path so it is cheap, but the right shape is a
repository-level `StateFlow<List<AppEntry>>` that both observe, with invalidation from the package
broadcasts.

### 7. `BootShieldService` resilience
It is a plain started service that runs 45–90 s during boot. Aggressive firmware can stop it early.
Options, none free: a foreground service with a notification (visible during boot — worse), a
`WorkManager` expedited job (not direct-boot aware), or shortening the guard window and leaning on
the watchdog. Needs a real device to evaluate.

### 8. Wallpaper decode on pick
`SettingsBottomSheetFragment.photoPicker` copies the full-resolution JPEG from SAF into
`filesDir/wallpaper.jpg` unsampled; the down-sampling happens later at render time. Decoding to
screen size at pick time would cut peak memory on 4K source images.

### 9. `networkStatusFlow.compute()` off the collector's dispatcher
Binder calls run on whatever dispatcher collects — currently Main. Add `flowOn(Dispatchers.Default)`.

### 10. Expose or remove the inert config fields
`accent`, `spacing` and `order` are rendered but **no settings screen writes them**, so they are
stuck at their defaults. Either add the controls (an accent picker, a spacing slider, drag-to-
reorder in the grid) or delete the fields — do not leave the third state.

## Explicit non-goals

* **Jetpack Compose** — never. See [AGENTS.md](../AGENTS.md).
* **Cloud sync / accounts / analytics** — the launcher is offline-first by design.
* **Weather, sports scores or any content widget** — needs network permissions and a backend the
  project does not want to run.
* **Biometric unlock** — TV boxes have no biometric hardware; the Quick Dashboard would need a
  different factor entirely.

## Nice-to-have, unstarted

* A `values-night/` palette (TV panels in a dark room) — colours are already centralised in
  `Theme.kt`, so this is cheap.
* Drag-to-reorder in the grid, writing `LauncherConfig.order` (would also close item 10).
* Per-app "uninstall / app info / play store" long-press menu in the grid (the `Actions` helpers
  already exist and now set `FLAG_ACTIVITY_NEW_TASK` correctly for non-Activity callers).
* Instrumented tests for `AppLockGate` and the accessibility enforcement path — requires a device
  farm or an emulator with an a11y-capable system image, neither of which CI has today.

## Recently completed (kept here so nobody redoes them)

* `Actions.openAppInfo` / `uninstall` now add `FLAG_ACTIVITY_NEW_TASK` for non-Activity contexts
  through a shared `newTaskIfRequired` helper — previously the `AndroidRuntimeException` was
  swallowed and both silently did nothing from a service or overlay.
* `BootShieldService`'s RAM log line uses `Locale.ROOT` instead of the default locale.
* Removed the last unused import (`android.content.Intent` in `GothwadApplication.kt`).
