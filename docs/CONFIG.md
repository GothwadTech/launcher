# Persisted configuration

One `LauncherConfig` object is serialised with **kotlinx.serialization** into a single JSON
string under the key `config_json` of a Preferences DataStore named `launcher_config`.

```kotlin
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
```

* `ignoreUnknownKeys = true` is what makes **removing** a field safe: configs written by older
  versions still decode, the dropped keys are ignored.
* `encodeDefaults = true` keeps the stored JSON self-describing.
* A decode failure is logged (`ConfigStore`, tag `ConfigStore`) and falls back to defaults. The
  raw JSON stays in the DataStore file, so it is recoverable from a bug report.

**Only the main process may touch `ConfigStore`** — Preferences DataStore is single-process by
design. `:watchdog` gets what it needs from small files instead (`ProcessHeartbeat`).

---

## Migration

| Field | Type / default | Notes |
| --- | --- | --- |
| `configVersion` | `Int` = `0` in a stored config that predates the marker, `CONFIG_VERSION` after a write | never read for behaviour, only to decide whether the *meaning* of a field changed |

`normalizeConfig()` runs on every read (`ConfigStore.flow`) and every write (`ConfigStore.update`,
which stamps the result with `CONFIG_VERSION`). A config without the marker is treated as legacy.

**Version 1: `uiScale`.** The setting was stored, displayed and read back but *never applied*
until the release that also moved its index base from `1..5` (`3 = 1.0x`) to today's `0..4`
(`2 = 1.0x`). A legacy value therefore means "a number the user never saw rendered", and reading
it under the new meaning would resize the whole UI by up to ±15% on every device that updates.
`normalizeConfig()` resets it to `UI_SCALE_NEUTRAL_INDEX` (1.0x) once; values written after the
update carry the marker and are honoured.

---

## Fields

Legend for the *UI* column: **W** = a settings screen writes it, **R** = something renders it.

### Home grid

| Field | Type / default | Read by | Written by |
| --- | --- | --- | --- |
| `order` | `Map<String, List<String>>` = `{}` | `TvLauncherFragment.rebuildAppList` (explicit packages first, the rest by `firstInstall`) | **nothing yet** — no reorder UI exists |
| `hidden` | `Set<String>` = `{}` | grid filter, `AppLockGate`, a11y vault guard, Settings subtitle | Settings → Apps → Secret vault |
| `showHidden` | `Boolean` = `false` | grid filter | Settings → Apps |
| `setupDone` | `Boolean` = `false` | gates the first-run wizard | `SetupWizardDialogFragment`, Settings → About → Rerun wizard |

### Wallpaper & scrim

| Field | Type / default | Read by | Written by |
| --- | --- | --- | --- |
| `wallpaper` | `Int` = `1` (index into `WALLPAPERS`, see `DEFAULT_WALLPAPER_INDEX`) | `TvLauncherFragment.applyWallpaper`, Settings subtitles | Settings → Wallpaper |
| `useCustomWallpaper` | `Boolean` = `false` | same; falls back to the preset when `files/wallpaper.jpg` is missing | Settings → Wallpaper → Pick image (SAF) |
| `scrimMode` | `Int` = `0` — 0 full, 1 top, 2 bottom, 3 both edges, 4 none | `TvLauncherFragment.applyWallpaper` (`view_scrim_full/top/bottom`) | Settings → Wallpaper |
| `accent` | `Int` = `0` (index into `ACCENTS`) | `AppCardAdapter` tile accent | **nothing yet** |

### Layout & scaling

| Field | Type / default | Read by | Written by |
| --- | --- | --- | --- |
| `spacing` | `Int` = `2`, index into `GAP_SIZES` (4/10/16/24/32 dp) | grid `ItemDecoration` + card size maths | **nothing yet** |
| `cornerRadius` | `Int` = `2`, index into `CORNER_RADII` (0/4/10/18/28 dp) | `AppCardAdapter`, corner preview, status-bar clusters (when `headerMatchIconCorners`) | Settings → Display |
| `uiScale` | `Int` = `2`, index into `UI_SCALES` (0.75/0.9/**1.0**/1.15/1.3) | `DensityAdapter.userUiScale` via `MainStatusBarController.applyDensityPolicy` | Settings → Display (recreates the activity to apply) |
| `respectSystemFontScale` | `Boolean` = `true` | `DensityAdapter.effectiveFontScale()` (clamped 0.85–1.30) | Settings → Display |

`uiScale` defaults to index **2 = 1.0×**. It used to default to 3 while the comment claimed
"3 = 1.0x" — harmless only because the value was never applied to anything. Now that it is, the
default must be the neutral step.

### Status bar

| Field | Type / default | Read by | Written by |
| --- | --- | --- | --- |
| `showStatusBar` | `Boolean` = `true` | `MainStatusBarController` (View.GONE when off) | Settings → Status bar |
| `statusBarGlass` | `Boolean` = `true` | `StatusBarView.applyConfig` (glass vs solid clusters) | Settings → Status bar |
| `headerMatchIconCorners` | `Boolean` = `true` | status-bar cluster corner radius follows `cornerRadius` instead of a fixed 20 dp | Settings → Status bar |
| `h24` | `Boolean` = `false` | clock pattern (`HH:mm:ss` vs `hh:mm:ss a`) | Settings → Status bar |

### Security

| Field | Type / default | Read by | Written by |
| --- | --- | --- | --- |
| `lockedApps` | `Set<String>` = `{}` | `AppLockGate`, a11y enforcement, card lock badge | Settings → Apps / Security |
| `appLock` | `LockCredential` | same | Settings → Security |
| `hiddenAppsLock` | `LockCredential` | vault prompt (search) + a11y vault guard | Settings → Security |
| `hiddenAppsRevealCodeHash` | `String` = `""` (Base64 PBKDF2) | `LockSecurity.verifyRevealCode` from `SearchDialogFragment` | Settings → Security → Reveal code |
| `hiddenAppsRevealCodeSalt` | `String` = `""` (Base64, 16 bytes) | same | same |
| `hiddenAppsRevealCode` | `String` = `""` | **legacy only** — migration input | never (cleared by `applyRevealCode`) |

`LockCredential` holds `credentialHash` + `credentialSalt` and a **legacy** plain-text `value`
that `LockSecurity.migrateLegacyCredentials()` hashes and clears on first launch. `ready` is true
for either form so a lock is never treated as unconfigured during the migration window.
See [SECURITY.md](SECURITY.md).

### Behaviour

| Field | Type / default | Read by | Written by |
| --- | --- | --- | --- |
| `launchOnBoot` | `Boolean` = `true` | `BootReceiver`; mirrored to the watchdog via `ProcessHeartbeat.setRelaunchAllowed` | Settings → Apps |
| `aggressiveMemoryTrim` | `Boolean` = `false` (opt-in, issue #34) | `AppLaunchTracker.performMemoryTrim` gate | Settings → Apps |
| `buttonMap` | `Map<Int, String>` = `{}` (keycode → package) | `LauncherAccessibilityService.onKeyEvent` | Settings → Remote button mapping |
| `buttonMapDefaultsApplied` | `Boolean` = `false` | idempotence flag | `ButtonMappingManager.seedDefaultMappings` |

---

## Removed fields

Dropped because nothing rendered them; `ignoreUnknownKeys` keeps old configs readable.
A regression test (`ConfigSerializationTest.keys removed in a newer version are ignored, not
fatal`) feeds a JSON document containing all of them and asserts it still decodes.

| Field | Why it went |
| --- | --- |
| `categories`, `sections`, `knownApps`, `autoCategoryOnInstall` | The category grid was replaced by one flat 6-column list. `MainActivity` still wrote an auto-category assignment for every new install — a JSON encode plus a DataStore write on every scan, for data no screen read. `CategoryAssigner` and its tests went with it. |
| `iconScale` (+ `ICON_SIZES`) | Card size is derived from the screen width and the column count. |
| `showCategoryNames`, `showAppLabels` | Labels are always on (`AppCardAdapter(showLabels = true)`); there was no toggle. |
| `layout` (+ `LAYOUT_GRID`) | Only one layout exists. `AppCardAdapter.isGridMode` is kept as an adapter capability. |
| `vpnApp`, `showVpnButton` | No VPN button in the status bar any more (`cd_vpn` was the last leftover). |

## Adding a field — checklist

1. Add it to `LauncherConfig` **with a default** (never a nullable "new in this version" field).
2. If it is a secret: store a hash + salt, keep the plain-text field only for migration, and add
   a case to `LockSecurity.migrateLegacyCredentials`.
3. Add a row to the table above.
4. If a settings screen writes it: write through `store.update { it.copy(...) }` **and**
   `updateConfig(...)` so the sheet's cached copy stays in sync.
5. If it affects rendering: read it from the config flow, never from a second persisted copy —
   except density/font policy, which needs the synchronous mirror described in
   [ARCHITECTURE.md §5](ARCHITECTURE.md#5-display-model--densityadapter).
6. Add or extend a `ConfigSerializationTest` case.
