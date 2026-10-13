# Performance & memory

The design target is a **2 GB Android TV / set-top box**: cold start to a scrollable grid, and a
home screen that survives a long session without being killed. Every rule below exists because
something measurable went wrong before.

## 1. Hard rules

1. **No Jetpack Compose.** Measured regression when Compose was mixed into the Views UI:
   graphics memory 81 MB → 152 MB, code memory 73 MB → 107 MB. See [AGENTS.md](../AGENTS.md).
2. **No package-manager or bitmap work on the main thread.** `AppRepository.scan()` pins itself to
   `Dispatchers.IO` with `withContext`, so a caller cannot accidentally run two
   `queryIntentActivities` calls plus N `getPackageInfo` round-trips on the UI thread.
3. **No blocking subprocess on the main thread.** The root/shell probe (`su -c id`) can block for
   as long as a root prompt waits for the user; it runs behind `detectInstallCapability()` on IO.
4. **Opt-in only for destructive work.** Killing other apps to reclaim memory is off by default
   (`aggressiveMemoryTrim`, issue #34) — a launcher must not murder the user's music for a few MB.
5. **Never invent state.** Fake "connected remote, 85 %" or a `getRunningAppProcesses() == null`
   → "the launcher is dead" conclusion both caused real bugs (the second one hijacked the screen
   every 10 seconds).

## 2. Startup path

```
Application.onCreate
  ├─ DensityAdapter.init          (sync, cheap: one screen-metrics read + a SharedPreferences read)
  ├─ ProcessHeartbeat.start       (writes a few bytes, then every 5 s)
  ├─ crash handler install        (no IO)
  └─ LauncherWatchdogService.start (startService, returns immediately)

MainActivity.onCreate
  ├─ DensityAdapter.apply
  ├─ registerReceiver ×2
  ├─ inflate activity_main.xml    (small: NavHostFragment + StatusBarView)
  ├─ lifecycleScope: legacy credential migration (async)
  ├─ lifecycleScope: safe-mode toast + markHealthy after 20 s
  └─ MainStatusBarController.setup → 5 collectors inside repeatOnLifecycle(STARTED)

TvLauncherFragment
  └─ AppRepository.scan (IO) → submitList (DiffUtil)
```

`sheet_settings.xml` is only inflated when the user opens Settings — it is **not** part of the
cold-start path.

### Baseline profile

`src/main/baseline-prof.txt` ships with `androidx.profileinstaller`, so the startup and scroll
paths are AOT-compiled on first run instead of JIT'd. This is the fix for "the screen appears but
scrolling is not smooth yet". If you add a hot path, add it to the profile.

## 3. App art cache

`AppRepository.scan()`:

* Banners are rasterised once to **320×180 RGB_565** and icons to **128×128 ARGB_8888**, then
  stored as **lossless WebP** in `cacheDir/app_art/<pkg>_b_<lastUpdateTime>.webp`.
* A cold start after the first run is therefore disk decodes, not PM IPC + drawable inflation.
* `prepareToDraw()` is called on the IO thread so the GPU upload happens before the first frame.
* The memory cache is `@Volatile` and returns **the same list instance** when packages and
  timestamps are unchanged, letting downstream collectors skip work.
* `validCacheNames` is a `Collections.synchronizedSet`: it is filled from concurrent
  `async(Dispatchers.IO)` blocks and then used to prune the cache directory. A plain `HashSet`
  there could corrupt and delete artwork that is still live.

Custom wallpapers are decoded **down-sampled to the physical screen size** and only when the
source file actually changed (`appliedWallpaperKey`). Previously every config emission re-decoded
the full-resolution JPEG and left the old bitmap to the GC.

## 4. RecyclerView

* `GridLayoutManager`, 6 columns, `setHasFixedSize(true)`, `setItemViewCacheSize(12)`.
* `AppCardDiffCallback` + `submitList` — no `notifyDataSetChanged()` anywhere in the codebase.
* `updateConfig()` compares old/new geometry and only notifies when something actually changed,
  and never while the RecyclerView is computing a layout (issue #28).
* Card backgrounds are `SmoothCornerDrawable` (androidx.graphics.shapes squircle), not nested
  CardViews — one drawable per card instead of a view hierarchy (issue #36).
* Icons come from `AppIcons`/`VectorPathDrawable`, which parse a path string once into a `Path`.

## 5. Status bar

Five collectors run inside `repeatOnLifecycle(STARTED)`, so they stop when the launcher is not
visible:

* network — `callbackFlow` over `ConnectivityManager.NetworkCallback`, no polling;
* Bluetooth — `callbackFlow` over ACL broadcasts plus a **20 s** poll (so a freshly granted
  `BLUETOOTH_CONNECT` shows up without waiting for a broadcast);
* notifications — a StateFlow from the notification listener;
* clock — 1 s `delay`, and it writes the foreground heartbeat every 5th tick instead of running a
  second timer;
* config — the DataStore flow.

Cluster backgrounds are built once per config change as `StateListDrawable`s (focus/hover/base),
from a single builder rather than three copies of the same code.

## 6. Watchdog cost

`:watchdog` is a separate process on purpose: it has to survive the main process dying. It is a
pure `Handler` loop with a 15 s interval, no libraries, and reads two tiny files. Steady-state
cost is a few MB of PSS and one file read per interval.

The main process writes the heartbeat file every 5 s from the main looper. It is a few bytes on
internal storage; if that ever shows up in a trace, move it to a background dispatcher — but keep
it in the same storage area the watchdog reads.

## 7. Known costs / open items

| Item | Impact | Status |
| --- | --- | --- |
| `sheet_settings.xml` is one 2 000-line layout (~200 views) inflated in a single pass | Jank when opening Settings on a slow box | Open — needs `<include>`/`ViewStub` splitting, which changes ViewBinding field paths for ~6 delegates. See [ROADMAP.md](ROADMAP.md) |
| Two full app scans can happen at startup (`MainActivity` for search/settings, `TvLauncherFragment` for the grid) | Second scan hits the memory cache fast-path, so it is cheap but not free | Open — could share one list through a repository-level flow |
| `networkStatusFlow.compute()` does binder calls on the collecting dispatcher | Small; collected on Main | Open — add `flowOn(Dispatchers.Default)` if it ever shows in a trace |
| Lint runs with `abortOnError = false` | Regressions are reported, not blocked | Open — see [CI_RELEASE.md](CI_RELEASE.md) |

## 8. Measuring

```bash
# memory of the running launcher (TV over adb)
adb shell dumpsys meminfo com.gothwad.launcher

# cold start
adb shell am force-stop com.gothwad.launcher
adb shell am start -W -n com.gothwad.launcher/.MainActivity

# is the baseline profile installed?
adb shell dumpsys package dexopt | grep -A2 com.gothwad.launcher
```

Record numbers in the PR description when you touch the startup path, the adapter, or anything
that allocates bitmaps. "It feels the same" is not a measurement on a 2 GB box.
