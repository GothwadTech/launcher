# Gothwad Launcher Developer & Architecture Rules

Short, non-negotiable rules. The reasoning and the detail live in [`docs/`](docs/README.md).

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

---

## The other rules that have each cost a real bug

### 1. One process owns the DataStore
`ConfigStore` is read and written **only from the main process**. The `:watchdog` process is a
separate process and must never touch it — Preferences DataStore is single-process and two writers
can corrupt the file. Cross-process state goes through `ProcessHeartbeat`'s small files.

### 2. Boot-critical state lives in device-protected storage
Anything read before first unlock (`gothwad_heartbeat`, `gothwad_relaunch_policy`,
`gothwad_selfheal`) must come from `createDeviceProtectedStorageContext().filesDir` via
`ProcessHeartbeat.sharedDir()`. Reading credential-encrypted `filesDir` there throws, the throw
gets swallowed, and the watchdog concludes the launcher died and hijacks the screen (issue #26).

### 3. No blocking work on the main thread
No `PackageManager` queries, no bitmap decode, no `Runtime.exec`. Specifically:
- `AppRepository.scan()` pins itself to `Dispatchers.IO` — do not remove that or call PM APIs
  around it on Main.
- The root/shell probe (`su -c id`) can block for as long as a root prompt waits for the user; it
  runs behind `AppUpdateManager.detectInstallCapability()` on IO, and the Settings badge resolves
  asynchronously with a "Checking…" state.
- `BootReceiver` starts a service and returns; never run the boot-shield loop inside `goAsync()`.

### 4. Fail secure, never fail open
If a lock overlay cannot be attached, or the config has not loaded yet, the user waits or is sent
HOME — a locked app is never revealed. `AppLockGate` (UX), `LauncherAccessibilityService`
(enforcement) and `LockSecurity` (credentials) each own their decision exactly once; do not add a
fourth copy of the rule (issue #31).

### 5. Never invent device state
Bluetooth unavailable means report **unknown**, not a placeholder remote name and battery level.
`ActivityManager.getRunningAppProcesses()` returning null means **unknown**, not "the process is
dead" (issue #27).

### 6. Destructive behaviour is opt-in
Killing other apps to reclaim memory (`aggressiveMemoryTrim`) defaults to **false** and must stay
that way (issue #34). A launcher must not murder the user's music for a few MB of headroom.

### 7. No `BuildConfig`
`buildFeatures` enables ViewBinding only. Read the app version from
`packageManager.getPackageInfo`, not from a generated constant.

### 8. minSdk is 21
Guard newer APIs at the call site (`SDK_INT >= M` for `PendingIntent.FLAG_IMMUTABLE`, notification
channels, `setExactAndAllowWhileIdle` permissions, …).

### 9. User-visible text is a resource
`R.string.*` / `@string/*`, never a literal, never string concatenation — use format strings.
There is an existing backlog of ~260 literals: do not add to it. Status and inventory:
[docs/I18N.md](docs/I18N.md).

### 10. Density and UI scale go through `DensityAdapter` only
Density is derived from a 960 dp design width, **not** from the firmware's `densityDpi`. The
`uiScale` and `respectSystemFontScale` mirror in the `gothwad_density` SharedPreferences is
intentional (it makes the first frame after a cold start correct); `LauncherConfig` remains the
source of truth and `MainStatusBarController.applyDensityPolicy()` pushes it in. Keep the neutral
default at `uiScale = 2` (1.0×) — index 2 *is* 1.0× in `UI_SCALES`.

Two rules learned from the 1.0.79 release, both now enforced by `tools/ktcheck.py`:

* **Never read anything through `context.applicationContext` inside `attachBaseContext()`** (or in
  anything it calls). While an `Application` is being attached the framework has not registered it
  yet — `LoadedApk.mApplication` is assigned only after `Instrumentation.newApplication()` returns
  — so `applicationContext` is `null` and the call throws on **every** cold start. Use the context
  you were handed.
* **The density engine must never be able to stop the launcher.** `wrapContext`/`apply`/`init` are
  best-effort: log the failure and fall back to the firmware density. A HOME app that cannot start
  is a black TV.

### 11. Signing and versioning are Gradle's job
No `jarsigner`/`apksigner sign` steps in workflows (AGP produces v1+v2+v3 for both APK and AAB).
Signing config is read with `providers.environmentVariable(...)` — **not** `System.getenv` — because
the configuration cache is enabled. `versionCode` is derived from the version name
(`MAJOR*10000 + MINOR*100 + PATCH`) so it stays monotonic. Any workflow that runs `assembleDebug`
must create `~/.android/debug.keystore` first (the custom `debugConfig` is not auto-generated).
Details: [docs/CI_RELEASE.md](docs/CI_RELEASE.md).

### 12. Keep minification working
`isMinifyEnabled` + `isShrinkResources` are on for release. kotlinx.serialization needs the keep
rules in `app/proguard-rules.pro`; if you add another reflection-based API, add its rules and build
`assembleRelease` locally. `dependenciesInfo` stays disabled for F-Droid reproducibility.

### 13. `metadata.json` is platform metadata
It belongs to the tooling that hosts this project, not to the app. Do not delete it, and do not
treat its contents as app configuration.

---

## Before you push

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew assembleRelease     # minification is a different failure mode from compiling
```

Then update whatever your change invalidates: `CHANGELOG.md` (under **Unreleased**),
`docs/CONFIG.md` for any config field, `docs/SECURITY.md` for anything touching locks or
credentials, and this file if you are adding a rule. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the full checklist.
