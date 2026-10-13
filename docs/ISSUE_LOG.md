# Issue log

The `#NN` numbers referenced in code comments. These came from an internal review of the launcher
(previously tracked in an `ISSUE-REPORT.md` that was never committed); this file is the durable
record so the comments in the source stay meaningful.

Numbers are review findings from that internal report, not GitHub issue numbers. The set below is
exactly the numbers still (or formerly) quoted in the repository — `7, 15, 19, 26, 27, 28, 30, 31,
34, 35, 36, 38, 44, 45` — plus the findings that were fixed without ever getting a number.

| # | Title | Severity | Status | Where |
| --- | --- | --- | --- | --- |
| 7 | Hidden-apps reveal code stored in plain text and echoed into the settings field | P1 | **Fixed** | `LockSecurity.hashRevealCode/verifyRevealCode`, `ConfigStore.applyRevealCode`, `SettingsSecurityDelegate` |
| 15 | Every app dumped into the first section on each scan | P1 | **Fixed (removed)** | `MainActivity.refreshAppsList`, `CategoryAssigner` + its test deleted (they were the only remaining references to this number) |
| 19 | UI text hard-coded in English; no string resources for locks, search, dashboard, settings | P2 | **Partly fixed** | ~29 new strings; 260 literals remain → [I18N.md](I18N.md) |
| 26 | `BootReceiver` / `:watchdog` read credential-encrypted storage before first unlock, so the watchdog thought the launcher was dead | P1 | **Fixed** | `ProcessHeartbeat.sharedDir()`, `directBootAware`, `gothwad_relaunch_policy` |
| 27 | `ActivityManager.getRunningAppProcesses()` treated as authoritative; returns null on many builds and caused screen hijacking | P1 | **Fixed** | `BootShieldService`, `ProcessHelper` |
| 28 | `AppCardAdapter` rebuilt every card on any settings change, and could notify during layout | P2 | **Fixed** | `AppCardAdapter.updateConfig`, DiffUtil |
| 30 | Notification listener re-read every event on the main thread | P2 | **Fixed** | `TvNotificationListenerService` |
| 31 | The same "is this app locked → show PIN" logic copy-pasted in `MainActivity` and `TvLauncherFragment` | P2 | **Fixed** | `ui/AppLockGate.kt` |
| 34 | Aggressive memory trim killed other apps (including playing media) by default | P1 | **Fixed** | `aggressiveMemoryTrim`, opt-in, `AppLaunchTracker` |
| 35 | Dead strings left behind after UI rework | P2 | **Fixed** | 5 removed from `values/strings.xml` |
| 36 | Nested CardView per app tile (extra view hierarchy per card) | P2 | **Fixed** | `SmoothCornerDrawable` |
| 38 | No unit tests for config decoding or credential storage | P2 | **Fixed** | `ConfigSerializationTest` (5), `LockSecurityTest` (10) |
| 44 | A `debug.keystore` could be committed to the repository | P1 | **Fixed** | `.gitignore` blocks `*.jks` / `*.keystore`; CI generates its own under `~/.android/` |
| 45 | System font scale was forced to 1.0×, breaking accessibility text sizes | P2 | **Fixed** | `DensityAdapter.effectiveFontScale`, `respectSystemFontScale` |

## Additional findings from the same review, fixed without a number

| Finding | Severity | Fix |
| --- | --- | --- |
| UI scale setting had no effect — stored and read back, never applied | P0 | `DensityAdapter` derives density from a 960 dp design width × `UI_SCALES[uiScale]`; `MainStatusBarController.applyDensityPolicy()` pushes config in and recreates on change |
| Notification dot drawn with zero alpha (invisible) | P0 | `StatusBarView` |
| Watchdog and boot shield relaunched HOME with an intent that Android 10+ background-activity-launch rules dropped | P1 | shared `homeIntent()` / `launchHomeCompat` |
| `AppRepository.scan()` ran PackageManager queries on the caller's dispatcher | P0 | `withContext(Dispatchers.IO)` inside `scan()` |
| `validCacheNames` mutated from concurrent `async(IO)` blocks | P0 | `Collections.synchronizedSet` |
| `memoryCache!!` non-volatile field + `clearMemoryCache()` race | P0 | `@Volatile`, null-safe return of the same list instance |
| App-lock enforcement failed **open** before the first DataStore emission | P1 | `configLoaded` + `pendingEnforcementPkg` re-check |
| `BLUETOOTH_CONNECT` never requested on Android 12+ | P1 | runtime request when the dashboard opens; "unknown" when denied |
| Wallpaper file name drifted between writer and reader | P1 | single constant |
| Version string hard-coded in the About page | P1 | `packageManager.getPackageInfo` |
| `rebuildAppList()` early-return meant the grid could never become empty | P2 | guard removed |
| Root/ADB probe (`su -c id`) ran on the main thread → ANR when opening Settings | P1 | `InstallCapability` + `detectInstallCapability()` on IO, async badge |
| APK download did not follow redirects, ignored non-2xx, never disconnected, and dispatched progress per 8 KB chunk | P2 | `openFollowingRedirects` (scheme-validated), failure on non-2xx, `disconnect()` in `finally`, percent-throttled progress on Main |
| `installViaAdbShell` used unbounded subprocesses and reported success without checking exit code | P2 | bounded `runShell` (8 s), success only on exit 0 + "Success" |
| Corrupt config JSON reset the launcher silently | P2 | `Log.e` with the decode failure, then defaults |
| Two workflows published GitHub Releases with colliding `v1.0.<run_number>` tags | P1 | `release.yml` is the only publisher; version code derived from the version name |
| Release AAB signed with `jarsigner` (v1 only) | P1 | Gradle/AGP signs both artifacts; only `apksigner verify` remains |
| Custom `debugConfig` signing config broke every CI build (AGP does not auto-generate a keystore for it) | P1 | `keytool -genkeypair` step in all three workflows |
| `System.getenv` for signing config under the configuration cache | P2 | `providers.environmentVariable(...)` |
| `-PlocalSign` pointed at AGP's built-in `debug` config instead of `debugConfig` | P2 | corrected |
| `Actions.openAppInfo` / `uninstall` lacked `FLAG_ACTIVITY_NEW_TASK`, so both threw (and were swallowed) from a non-Activity context | P2 | shared `newTaskIfRequired` helper |
| `"%.2f".format` in `BootShieldService` was locale-dependent | P3 | `String.format(Locale.ROOT, …)` |
| Unused `import android.content.Intent` in `GothwadApplication.kt` | P3 | removed |
| `README.md` linked to a `docs/baseline-profile.md` that did not exist | P3 | written |
| No `LICENSE` file, although the README declared Apache-2.0 | P2 | added (canonical Apache 2.0 text) |

## Post-1.0.79 field crash (reported from a TV)

| Finding | Severity | Fix |
| --- | --- | --- |
| `DensityAdapter.hydrate()` ran from `GothwadApplication.attachBaseContext()` (via `wrapContext`) and read `context.applicationContext.getSharedPreferences(...)`. `applicationContext` is `null` for the whole attach window (`LoadedApk.mApplication` is assigned only after `Instrumentation.newApplication()` returns), so **every** cold start died with a `NullPointerException` - as the default HOME app, that is a crash dialog over a black screen | P0 | `prefs()` uses the context it is given and returns null on failure; `wrapContext`/`apply`/`init` are best-effort and log instead of throwing; `tools/ktcheck.py` now follows the call chain from `Application.attachBaseContext` and fails on the pattern |

Related, same update: `uiScale` written before 1.0.79 was stored in an index base (`1..5`,
`3 = 1.0x`) that the now-working setting no longer uses (`0..4`, `2 = 1.0x`), so an untouched
config would have resized the UI by 15% on first launch after the update.
`LauncherConfig.configVersion` + `normalizeConfig()` now neutralise such a config once - see
[CONFIG.md](CONFIG.md#migration).

## Deliberately *not* fixed (with reasons)

| Finding | Decision |
| --- | --- |
| `sheet_settings.xml` is one 2 000-line layout with ~200 views | Documented in [ROADMAP.md](ROADMAP.md). Splitting into `<include>`/`ViewStub` breaks ViewBinding field paths for `SettingsBottomSheetFragment` and all six delegates — hundreds of references, no local compile check. Do it in a PR whose only purpose is that refactor. |
| Remaining hard-coded strings (260) | Staged on purpose; see [I18N.md](I18N.md). Bulk-editing that many references with no compiler available is how you ship a first-frame crash. |
| No APK checksum/signature pinning after download | HTTPS + the installer's own signature verification is the current guarantee. A published SHA-256 per release is a roadmap item, not a silent omission. |
| `BootShieldService` is a plain started service running 45–90 s | Foreground service types on API 34 would need a user-visible notification for a boot-time guard, which is worse UX than the risk. Documented in [ROADMAP.md](ROADMAP.md). |
