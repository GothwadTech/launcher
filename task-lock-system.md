# Gothwad Launcher — Privacy Lock System (Device Lock / App Lock / Hidden Apps)

## How to use this file
Same rule as `task.md`: feed **one phase at a time** to Google AI Studio, in its own session. Build + install on the real box after each phase, confirm the specific bypass tests at the end of that phase actually fail to bypass, then move to the next phase. Do not merge phases.

**Global rule — paste at the top of every prompt below:**

> This is `com.gothwad.launcher` (`com.gothwad.launcher` package), a low-RAM (2GB) Android TV launcher. It already has a partial, incomplete lock system in `LauncherConfig` (`data/Config.kt`): `lockedApps`, `appLockPin`, `appLockPinLength`, `appLockEnabled`, `deviceLockEnabled`, `deviceLockPin`, `hideAppsCode`, `hideAppsPin`, and a `hidden: Set<String>` set. Numeric-only PIN entry/setup dialogs already exist (`PinEntryDialogFragment`, `PinSetupDialogFragment`). App-lock is currently checked independently and redundantly in three places — `MainActivity.kt`, `ui/tv/TvLauncherFragment.kt`, and `ui/pc/PcLauncherFragment.kt` — each with its own copy of `if (!skipLock && currentConfig.appLockEnabled && ...)`. Device lock is stored in config but **is never actually enforced anywhere** — it needs to be built from scratch. Hidden-apps reveal via the search bar (`ui/dialogs/SearchDialogFragment.kt`) already works, but currently shares no separate re-lock on the hidden apps themselves. Do not reintroduce Compose. Keep everything working across TV mode and PC mode (`launcherMode`).

---

## Phase 1 — Correct, fully-separated data model

**Goal:** Today `SettingsBottomSheetFragment.kt` (line ~632) sets `appLockPin` and `deviceLockPin` to the **same value** when the user sets one PIN — that's wrong; the three lock types must be completely independent credentials, and each must support two independent credential *types* (numeric PIN or alphanumeric password), not just PIN.

Prompt for AI Studio:

> In `LauncherConfig` (`data/Config.kt`), replace the current flat lock fields with three self-contained, independent lock configs (keep it a plain serializable data class, no new library):
> ```kotlin
> enum class LockCredentialType { NUMERIC, ALPHANUMERIC }
>
> @Serializable
> data class LockCredential(
>     val enabled: Boolean = false,
>     val type: LockCredentialType = LockCredentialType.NUMERIC,
>     val value: String = "",       // the PIN digits or the alphanumeric password
>     val pinLength: Int = 4,       // only meaningful when type == NUMERIC (4 or 6)
> )
> ```
> Add three fields to `LauncherConfig`: `val deviceLock: LockCredential = LockCredential()`, `val appLock: LockCredential = LockCredential()`, `val hiddenAppsLock: LockCredential = LockCredential()`. Keep `lockedApps: Set<String>` (which apps app-lock applies to) and `hidden: Set<String>` (which apps are hidden) as-is — those are independent of which *credential* protects them. Remove the old flat fields (`appLockPin`, `appLockPinLength`, `appLockEnabled`, `deviceLockEnabled`, `deviceLockPin`, `hideAppsCode`, `hideAppsPin`) and fix every reference across `MainActivity.kt`, `TvLauncherFragment.kt`, `PcLauncherFragment.kt`, `SettingsBottomSheetFragment.kt`, and `SearchDialogFragment.kt` to use the new nested fields instead (e.g. `config.appLock.enabled`, `config.appLock.value`, `config.appLock.pinLength`). Do not add a migration step for old configs — this project doesn't need to preserve old lock settings across this change.

---

## Phase 2 — Alphanumeric + numeric entry UI with a TV/keyboard input-mode switch

**Goal:** Support both a 4-or-6-digit numeric PIN keypad (today's `PinEntryDialogFragment`) **and** a full alphanumeric password field, selectable when setting up each of the three locks, with a way to switch input mode on the same screen.

Prompt for AI Studio:

> 1) Extend `PinSetupDialogFragment` (the setup/creation flow) with a `LockCredentialType` selector at the top (two toggle buttons/tabs: "PIN" and "Password"). When "PIN" is selected, show a second small toggle for 4-digit vs 6-digit, then reuse the existing numeric keypad UI. When "Password" is selected, replace the numeric keypad with a standard `EditText` (`inputType` = text/visible-password, TV-remote-focusable) plus an on-screen QWERTY keyboard grid for pure-remote-control input (no physical keyboard assumed) — reuse whatever on-screen keyboard component already exists elsewhere in the project if there is one (check `ui/` for anything IME-like used in search or settings text fields); otherwise build a minimal focusable on-screen QWERTY grid consistent with the existing dialog styling (see `createDotDrawable`/`AppIcons` usage in `PinEntryDialogFragment` for the visual language to match).
> 2) Add an explicit **input-mode switch** on this same setup screen — a button/toggle labeled to switch between "Remote / TV keypad" and "Keyboard / PC" entry layouts (matching the existing `MODE_TV`/`MODE_PC` concept already in `Config.kt`), so a user on a TV remote can still comfortably type an alphanumeric password via the on-screen grid, or, on a PC-mode device with a real keyboard attached, type directly into the `EditText`. This switch only changes the *input widget shown*, not what's being set up.
> 3) Extend `PinEntryDialogFragment` (the verification/unlock flow, used everywhere a lock is *checked*) the same way: it must read a `LockCredential` and render either the numeric-dot keypad (existing behavior, unchanged) or a password field + on-screen keyboard, and validate against `LockCredential.value` accordingly (exact string match for alphanumeric, same as today's numeric `checkPin`).
> 4) Both dialogs should take a `LockCredential` object directly (instead of separate `correctPin`/`pinLength` params) to avoid the three call sites getting out of sync now that there are two credential types.

---

## Phase 3 — App Lock that cannot be bypassed (fix the Settings-app / notification / recents hole)

**Goal:** Today app-lock is only checked when the user taps an app **inside this launcher's own grid**. Opening the same app via Android Settings → Apps → [app] → Open, or a notification, or the recents/overview switcher, or another app's share/launch intent, bypasses it completely, because nothing outside the launcher's own click handlers ever checks `lockedApps`.

Prompt for AI Studio:

> Move app-lock enforcement out of the three duplicated UI click-handlers and into `LauncherAccessibilityService`, which already receives `TYPE_WINDOW_STATE_CHANGED` for every foreground app change system-wide (it already uses this to catch the stock TV launcher stealing focus) — this is the one place that cannot be bypassed by *how* the app was opened.
> In `onAccessibilityEvent`, when the foregrounded `pkg` is not `packageName` itself, is in `config.appLock.enabled ? config.lockedApps : emptySet()`, and has not already been unlocked in the current "session" (track per-package unlocked state in-memory with a timestamp — e.g. stays unlocked for that foreground session until the app goes to background again, so the user isn't re-prompted on every window change inside the same app), immediately show a **full-screen `SYSTEM_ALERT_WINDOW` overlay** (the manifest already has `SYSTEM_ALERT_WINDOW` permission) hosting a `PinEntryDialogFragment`-equivalent view rendered directly via `WindowManager` (a dialog fragment needs an activity, so build this as a plain `WindowManager`-added view, not a `DialogFragment`, since the accessibility service has no activity context) that: blocks all interaction with the app underneath, requires the correct `appLock` credential, and on success removes the overlay (marking that package unlocked for the session) or on Back/attempts to escape, sends the user Home instead of revealing the underlying app. This must trigger regardless of whether the app was opened from this launcher, from Android's own Settings app, from a notification shortcut, from the recents/overview switcher, or from another app's launch intent — since the check happens on the foreground-window event itself, not on the launch action.
> Once this is in place, simplify `MainActivity.kt`, `TvLauncherFragment.kt`, and `PcLauncherFragment.kt`: they can keep their existing in-launcher PIN prompt for a *nicer* first-tap experience (avoids the overlay flashing on screen for a split second), but the accessibility-service check is now the authoritative, unbypassable enforcement layer — mark the existing three call sites clearly as "UX-only, not the security boundary" in a comment.

**Bypass tests to run after this phase, on the real box:** lock an app, then (a) open it from Android Settings → Apps → that app → Open, (b) open it from a notification if it posts one, (c) switch to it via the recents/overview button if the remote has one, (d) long-press Home / any app-switcher gesture the OEM remote has. All four must show the PIN/password overlay before the app's content is visible.

---

## Phase 4 — Device Lock (build from scratch — currently unused)

**Goal:** `deviceLock`/old `deviceLockPin` fields exist in config today but nothing ever reads them. The lock must trigger whenever the launcher itself starts fresh — first boot, or the launcher process being relaunched after a crash/kill/restart (per the reliability work already done in `GothwadApplication`/the watchdog) — before the home screen becomes visible or interactive.

Prompt for AI Studio:

> In `MainActivity.onCreate` (the launcher's own entry point), before any home-screen UI becomes interactive, check `config.deviceLock.enabled`. If enabled, and this is a "cold" start of the launcher process (not just the activity being brought back to front after a normal app-switch — use a simple `Application`-level in-memory flag in `GothwadApplication`, e.g. `hasUnlockedThisProcess`, set once the device-lock PIN is entered correctly, so the user is only prompted once per launcher-process lifetime, not on every `onResume`), show the same full-screen lock UI as Phase 3 (can reuse the same overlay/dialog component) with `config.deviceLock` as the credential, blocking all launcher interaction — including the app grid, search, and settings — until it is entered correctly. This must also correctly trigger after the Phase-2/Phase-3 reliability watchdog force-relaunches the launcher process following a crash or ANR, since that is exactly the "restart mid-state" case that needs to be covered — verify it does by triggering a forced crash and confirming the device-lock screen appears on the automatic recovery relaunch, not just on a manual reboot.

**Bypass tests:** enable device lock, force-kill the launcher process via `adb shell am force-stop com.gothwad.launcher` while the box stays on, confirm the lock screen appears the moment it's relaunched (both by the watchdog automatically and if you manually relaunch it) — not just after a full power-cycle reboot.

---

## Phase 5 — Hidden Apps with its own separate re-lock

**Goal:** The search-bar reveal-by-code already works (`SearchDialogFragment.kt` line ~82-96, matching `hideAppsCode`/`hideAppsPin`), but once revealed, tapping a hidden app launches it directly with no further check — there is currently no separate lock *on the hidden apps themselves* the way you want (a distinct PIN just for entering the hidden-apps view, separate from the reveal code).

Prompt for AI Studio:

> Rename/repoint the existing secret-code match in `SearchDialogFragment.updateAppList` to use the new `config.hiddenAppsLock` credential (from Phase 1) as the *reveal* code — typing it into the search bar still reveals the hidden-apps list inline, as today. Additionally, add a distinct **second-layer confirmation**: right after `isSecretMatch` becomes true and the hidden list is about to render, if `config.hiddenAppsLock.enabled`, immediately show the same PIN/password overlay from Phase 2's `PinEntryDialogFragment`, re-prompting with the same `hiddenAppsLock` credential (or, if you want the "reveal code" and the "confirm lock" to genuinely be two separate secrets as you described, add one more field, `hiddenAppsRevealCode: String`, to `LauncherConfig` purely for what's typed in the search bar, while `hiddenAppsLock` — a full `LockCredential`, numeric or alphanumeric, set up like the other two — is the second confirmation before the list actually renders and before any hidden app can be tapped/launched). Implement the two-separate-secrets version, since that matches what was asked for. Wire up the Settings screen (Phase 6) to let the user set both independently.

---

## Phase 6 — Settings screen: three clearly separated lock sections

**Goal:** Give each of the three lock types its own dedicated, unambiguous settings section, instead of today's single shared PIN setup.

Prompt for AI Studio:

> In `SettingsBottomSheetFragment.kt`, replace today's single "Device Lock" / "App Lock" switch pair (which currently share one PIN — see the code around line 607-633) with three clearly-separated sections, each structured identically:
> - **Device Lock** — enable switch, credential-type picker (PIN 4/6-digit or Password, via Phase 2's setup dialog), "Set/Change PIN" button.
> - **App Lock** — enable switch, credential-type picker + setup button (independent credential from Device Lock), plus the existing "choose which apps are locked" list bound to `lockedApps`.
> - **Hidden Apps** — enable switch, a "Reveal code" field (`hiddenAppsRevealCode`, what's typed into search — can stay simple, no keypad needed since it's typed on the real search field already), a separate credential-type picker + setup button for the `hiddenAppsLock` second-layer confirmation, plus the existing "choose which apps are hidden" list bound to `hidden`.
> Keep the existing status text logic (`"Device & App PIN Active"` etc. around line 241-243) but update it to reflect that the three are now independent, e.g. show a short summary line per section instead of one combined line.

---

## Phase 7 — Full verification checklist

Prompt for AI Studio (final review pass once Phases 1-6 are merged):

> Review the full diff and confirm: (a) the three `LockCredential`s are never cross-readable — setting one never touches another; (b) the Phase-3 overlay and Phase-4 device-lock screen both correctly block *all* input to whatever is underneath (test with D-pad/remote navigation, not just touch) and cannot be dismissed with Back/Home without either entering the correct credential or, for app-lock, being sent Home instead of into the app; (c) alphanumeric password entry via the on-screen keyboard is fully usable with only a D-pad remote (no physical keyboard assumed) in TV mode; (d) none of this new code touches the main thread with blocking work — all config reads/writes stay on background dispatchers, consistent with the reliability work already done.

Manually re-run, on the real box: set all three locks with different credentials of different types (e.g. Device Lock = 6-digit PIN, App Lock = password, Hidden Apps = 4-digit PIN + separate reveal code) and confirm each behaves completely independently, and repeat the Phase-3 bypass tests (Settings-app Open, notification, recents switcher) one more time at the end to make sure nothing in later phases reopened the hole.
