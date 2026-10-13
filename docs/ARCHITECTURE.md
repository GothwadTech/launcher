# Architecture

Native Android Views only — XML layouts, Fragments/Activities, ViewBinding, RecyclerView,
Coroutines + StateFlow. **No Jetpack Compose anywhere** (see [AGENTS.md](../AGENTS.md): mixing
Compose into this app doubled graphics memory, 81 MB → 152 MB).

Target: Android TV / Google TV and low-RAM (≈2 GB) set-top boxes, minSdk 21, targetSdk 34,
Kotlin 2.0.20, AGP 8.5.2.

---

## 1. Package map

```
com.gothwad.launcher
├── GothwadApplication      density init, heartbeat, crash self-healing, memory trimming
├── MainActivity            the HOME activity; owns config + app list, hosts the nav graph
├── Actions                 intent helpers (launch app, open settings, toast)
│
├── data/                   pure-ish state and IO. No views in here.
│   ├── Config              LauncherConfig + ConfigStore (Preferences DataStore, JSON blob)
│   ├── AppRepository       package scan, banner/icon rasterisation, WebP disk cache
│   ├── LockSecurity        PBKDF2 hashing, verification, brute-force throttle
│   ├── ProcessHeartbeat    cross-process liveness + watchdog policy (device-protected storage)
│   ├── ProcessHelper       "which process am I" (main vs :watchdog)
│   ├── SmartServices       networkStatusFlow, bluetoothStatusFlow, TvControlHelper
│   ├── BackgroundMediaTracker  MediaSession/"what is playing", boot-ad audio muting
│   ├── ButtonMappingManager    remote hotkey → package map, capture mode
│   ├── AppLaunchTracker    LRU of launched packages, opt-in background trim
│   ├── AppUpdateManager    GitHub Releases check/download/install
│   └── AccessibilityStateTracker  "was a11y enabled before the crash?"
│
├── receiver/BootReceiver   BOOT_COMPLETED / LOCKED_BOOT / QUICKBOOT / MY_PACKAGE_REPLACED
│
├── service/
│   ├── LauncherAccessibilityService  HOME key, volume hold, hotkeys, app-lock enforcement
│   ├── LauncherWatchdogService       :watchdog process, crash/ANR recovery
│   ├── BootShieldService             45–90 s boot-ad / stock-launcher suppression
│   ├── StockLauncherDetector         which packages count as "a launcher we replace"
│   └── TvNotificationListenerService notification mirror for the TV status bar
│
└── ui/
    ├── DensityAdapter      DPI independence + UI scale + font-scale policy
    ├── Theme / Icons       colour palette, wallpaper presets, vector path drawables
    ├── MainStatusBarController  wires the persistent status bar to config/flows
    ├── AppLockGate         the one place the UI asks "may this app launch?"
    ├── tv/TvLauncherFragment    the home grid (flat 6-column RecyclerView)
    ├── view/               StatusBarView, AppCardAdapter, SmoothCornerDrawable,
    │                       lock overlays, OnScreenKeyboardView, DeviceLockViewController
    └── dialogs/            Settings sheet + per-page delegates, Search, PIN entry/setup,
                            Quick Dashboard, Notifications, Setup Wizard, Voice Search
```

The settings sheet is split into **delegates** (`SettingsWallpaperDelegate`,
`SettingsDisplayDelegate`, `SettingsStatusBarDelegate`, `SettingsAppsDelegate`,
`SettingsSecurityDelegate`, `SettingsButtonMappingDelegate`) because `sheet_settings.xml` is a
single 2 000-line layout; each delegate owns one page of it and receives
`(fragment, binding, store, getConfig, updateConfig, …)`.

---

## 2. Process model

| Process | Components | Notes |
| --- | --- | --- |
| `com.gothwad.launcher` (main) | Application, MainActivity, a11y service, boot shield, notification listener, `ConfigStore` | The **only** process allowed to touch the DataStore. |
| `com.gothwad.launcher:watchdog` | `LauncherWatchdogService` | `directBootAware=true`, a few MB, pure `Handler` loop, no libraries. |

`GothwadApplication.onCreate()` branches on `ProcessHelper.currentProcessName()`: heartbeat,
crash handler and watchdog start **only** in the main process.

### Cross-process state (device-protected storage)

The watchdog must work before the user unlocks the device, so shared state lives in
`createDeviceProtectedStorageContext().filesDir` — see `ProcessHeartbeat.sharedDir()`:

| File | Writer | Reader | Content |
| --- | --- | --- | --- |
| `gothwad_heartbeat` | main process (every 5 s, plus a foreground beat from the status-bar clock loop) | `:watchdog` | `processBeat\nforegroundBeat` (`elapsedRealtime`) |
| `gothwad_relaunch_policy` | main process, from `LauncherConfig.launchOnBoot` | `:watchdog` | `1`/`0` — may the watchdog pull HOME forward? |
| `gothwad_selfheal` | both (crash handler and watchdog) | both | `relaunchCount\nwindowStart\nsafeMode` |

Credential-encrypted `filesDir` is **not** used for these: reading it pre-unlock throws, the
throw is swallowed, and the watchdog concludes "main process dead" and hijacks the screen.

`gothwad_crash.log` stays in normal `filesDir` (main process only, diagnostics).

---

## 3. Data flow

### Config (single source of truth)

```
Preferences DataStore ("launcher_config" → one JSON string)
        │  ConfigStore.flow : Flow<LauncherConfig>
        ├──► MainStatusBarController   → status bar, density policy, watchdog policy, trim flag
        ├──► TvLauncherFragment        → grid geometry, wallpaper, scrim, order, hidden filter
        ├──► LauncherAccessibilityService → cachedConfig / cachedButtonMap (hotkeys, locks)
        └──► Settings delegates        → read through getConfig(), write through store.update{}
```

Every writer goes through `ConfigStore.update { it.copy(...) }` (a DataStore transaction), then
the flow re-emits to everyone. UI layers must never keep a second copy of the truth — the
Settings sheet holds a `config` field only as a *cache for its own delegates*, refreshed from
the same flow.

### Apps

```
PackageManager
   │ AppRepository.scan(context)        ← always Dispatchers.IO (withContext inside)
   │   • queryIntentActivities(LEANBACK_LAUNCHER) + (LAUNCHER), dedup, drop self
   │   • batch-read lastUpdateTime/firstInstallTime
   │   • memory cache fast-path when packages + timestamps are unchanged
   │   • per app (async on IO): banner 320×180 RGB_565, icon 128×128 ARGB_8888,
   │     cached as lossless WebP in cacheDir/app_art, prepareToDraw()
   ▼
MainActivity.allApps (@Volatile)  ──► Search, Settings "manage apps"
TvLauncherFragment.allApps        ──► AppCardAdapter.submitList (DiffUtil)
```

Rescans are triggered by `ACTION_PACKAGE_ADDED/REMOVED/CHANGED` and debounced 600 ms
(`MainActivity.scheduleAppsRescan`).

### Status bar

`MainStatusBarController.observeStatusBarData()` runs five sibling collectors inside
`repeatOnLifecycle(STARTED)`: config, network (`callbackFlow` on `ConnectivityManager`),
Bluetooth (`callbackFlow` on ACL broadcasts + a 20 s poll), notifications
(`NotificationManagerBridge.notifications` StateFlow), and a 1 s clock loop that also writes the
foreground heartbeat every 5 ticks.

---

## 4. Lock enforcement — three layers

Detailed threat model in [SECURITY.md](SECURITY.md). Summary:

1. **UX layer** — `AppLockGate.evaluate()` is the single decision point used by `MainActivity`
   and `TvLauncherFragment`. Shows `PinEntryDialogFragment`, remembers the unlocked package for
   the foreground session.
2. **Authoritative layer** — `LauncherAccessibilityService.checkAndEnforceAppLock()` reacts to
   `TYPE_WINDOW_STATE_CHANGED`, so a locked or hidden app opened from *another* launcher, the
   recents switcher, a notification or ADB is still covered by `AppLockOverlayView`
   (`TYPE_ACCESSIBILITY_OVERLAY`, falling back to `SYSTEM_ALERT_WINDOW`). If the overlay cannot
   be attached it **fails secure** and sends the user HOME.
3. **In-activity layer** — `DeviceLockViewController` for device lock, drawn inside
   MainActivity (no overlay permission needed).

Enforcement **fails closed on startup**: until the first config emission lands,
`configLoaded == false` and the foreground package is parked in `pendingEnforcementPkg` and
re-checked, because an empty default config would mean "nothing is locked".

---

## 5. Display model — `DensityAdapter`

TV firmware ships with wildly different (and sometimes corrupt) DPI values, so the launcher does
not trust `densityDpi`. It anchors the UI to a **960 dp design width**:

```
baseline = screenWidthPx / 960
density  = baseline × userUiScale          // Settings → Display → UI Scale
screenWidthDp = screenWidthPx / density    // 960 at 1.0x, ~738 at 1.3x
scaledDensity = density × fontScale        // fontScale honours the system setting if enabled
```

`userUiScale` and `respectSystemFontScale` are mirrored into a tiny SharedPreferences
(`gothwad_density`) so the **first frame after a cold start is already correct** — the DataStore
config is asynchronous and arrives too late for that. `LauncherConfig` remains the source of
truth; `MainStatusBarController.applyDensityPolicy()` pushes it into the adapter on every
emission and recreates the activity if the effective scale actually changed (density is baked
into views at inflate time).

Applied from `attachBaseContext` (via `wrapContext`), `Application.onCreate`, every activity
lifecycle callback, and on configuration change.

---

## 6. Startup paths

**Cold launch (user pressed HOME)**
`GothwadApplication.onCreate` → density init → heartbeat start → crash handler →
watchdog start → `MainActivity.onCreate` → density apply → receivers → inflate
`activity_main.xml` → legacy credential migration → safe-mode toast + `markHealthy` after 20 s →
`MainStatusBarController.setup()` → `refreshAppsList()` (IO) → `TvLauncherFragment` scans and
renders the grid.

**Boot**
`BootReceiver` (`directBootAware`, priority 1000) → always starts the watchdog → for a real boot
(not `MY_PACKAGE_REPLACED`) reads `launchOnBoot` inside `goAsync()` →
`BootShieldService` (45 s, or 90 s when total RAM ≤ 2.2 GB) loops: if a stock launcher / boot-ad
shim takes the screen, mute audio and bring HOME back. The receiver returns immediately — an
earlier version ran the guard loop inside `goAsync()` and produced an ANR on every boot.

**Crash**
`Thread.setDefaultUncaughtExceptionHandler` → append to `gothwad_crash.log` (truncated past
64 KB) → `SelfHealGuard.shouldRelaunch()` (3 relaunches per 10 min, then **safe mode**) →
AlarmManager relaunch of HOME in 1.5 s (exact alarm only when permitted) → the previous handler
still runs so the OS crash dialog/dump is unaffected. `MainActivity` calls
`SelfHealGuard.markHealthy()` after 20 s of uptime to reset the budget.

---

## 7. Invariants worth protecting

- **No Compose, no ComposeView, no interop bridge.** (AGENTS.md)
- **Only the main process touches `ConfigStore`.**
- **`AppRepository.scan()` is IO-confined**; callers must not do PM work on the main thread.
- **Security decisions live in one place each**: `AppLockGate` (UX), the a11y service
  (enforcement), `LockSecurity` (credentials). Do not add a fourth.
- **Fail secure, never fail open**: if a lock overlay cannot attach, or the config has not loaded
  yet, the user goes HOME / waits — the locked app is never revealed.
- **Never invent device state.** Bluetooth off means "unknown", not "TV Remote, 85 %".
  `ActivityManager.getRunningAppProcesses()` returning null means "unknown", not "dead".
- **Honest options**: anything aggressive (killing other apps) is opt-in and off by default.
