# Troubleshooting

Symptoms reported from real TV / set-top-box firmware, with cause and fix. Check here before
filing an issue — most "the launcher does X" reports are one of these.

---

## Boot & startup

### "Gothwad Launcher keeps stopping" after an update, and the screen is black

**Cause:** 1.0.79 read its own preferences from `applicationContext` while the `Application` was
still being attached - where that value is `null` by design (the framework registers the
Application only *after* `attachBaseContext()` returns). Every cold start died with a
`NullPointerException` before the first frame; since the launcher is normally the default HOME
app, the TV showed the crash dialog over a black screen. Fixed in the next release: `hydrate()`
can no longer throw, and `wrapContext`/`apply`/`init` degrade to the firmware's density instead
of killing the process.

**Recovering the TV right now** (any one of these):

1. **ADB** (Developer options → USB debugging is enough):
   `adb install -r -d GothwadLauncher-v1.0.78-Release.apk`
   - the previous build, from this project's GitHub releases. `-d` allows the downgrade.
2. **Uninstall the update from the TV UI:** Settings → Apps → Gothwad Launcher → Uninstall (or
   *Clear data*), then make sure a working launcher is selected in
   Settings → Device Preferences → **Home screen**.
3. **Install the fixed build** the same way (`adb install -r GothwadLauncher-v<newer>-Release.apk`)
   - it keeps the settings, locks and hidden-app list.

**Diagnosing it on the device:** the app's own `gothwad_crash.log` is written by
`GothwadApplication.onCreate()`, which never runs when the crash is *before* it - so for this
class of bug use `adb logcat -b crash` (or `adb logcat | grep -i "gothwad\\|AndroidRuntime"`).
The launcher's crash dialog itself is the system's "keeps stopping" ANR/crash reporter, not ours.

### "The UI got bigger/smaller by itself after updating to 1.0.79"

**Cause:** the UI Scale setting did nothing before 1.0.79 (stored, displayed, never applied) and
its index base moved from `1..5` (`3 = 1.0x`) to `0..4` (`2 = 1.0x`) in the release that made it
work. An untouched old config therefore held `3`, which under the new meaning is `1.15x`.

**Fix:** handled automatically since the next release (`configVersion` + `normalizeConfig()` reset
a pre-1.0.79 value to the neutral 1.0x once). If you are on 1.0.79 itself: Settings → Display →
**UI Scale** → pick `1.0x`.

### "An ad plays for ~90 seconds after every reboot"

**Cause:** the stock launcher / a preinstalled promo app owns the screen during boot, and it is
also where the firmware plays its boot advertisement.

**Fix:** Settings → Apps → *Launch on boot* must be **on** (it is by default). The launcher then
runs `BootShieldService` for 45 s (90 s on boxes with ≤ 2.2 GB RAM) and pulls HOME to the front
whenever a stock launcher or boot-ad shim takes the screen. Audio from the ad is muted while the
shield is active.

If it still shows: the box's own boot animation/ad runs in a separate window layer that no
launcher can cover — that part needs firmware, or root plus disabling the offending package.

### "The launcher hijacks the screen every 10 seconds / before I unlock the device"

Fixed. The `:watchdog` process used to read a heartbeat file from credential-encrypted storage,
which throws before first unlock; the exception was swallowed and the watchdog concluded the main
process was dead. Shared state now lives in **device-protected storage**
(`ProcessHeartbeat.sharedDir()`), and the watchdog only pulls HOME forward when the policy file
says `launchOnBoot || isDefaultHomeApp`.

If you are on an old build, update — do not work around it by disabling the watchdog.

### "Every boot shows a safe-mode toast"

**Cause:** three crashes within 10 minutes puts the launcher in safe mode
(`SelfHealGuard`). Safe mode disables the aggressive recovery loop, not the launcher.

**Fix:** pull `gothwad_crash.log` (see [TESTING.md](TESTING.md#debugging-aids-on-the-device)) and
attach it to an issue. 20 s of healthy uptime resets the budget, so a one-off crash does not leave
you in safe mode.

### "After an update the launcher is no longer the default home"

Firmware can reset the HOME role on `MY_PACKAGE_REPLACED`. Press the HOME button, pick Gothwad
Launcher, choose *Always*. If the box refuses, check Settings → Apps → *Default home app* (the
launcher offers a shortcut to that screen).

---

## Display

### "Everything is enormous / tiny"

**Cause:** TV firmware ships with nonsense `densityDpi` values (some report the panel's physical
size as DPI).

**Fix:** Settings → Display → **UI Scale** (0.75× … 1.3×). The launcher derives density from a
960 dp design width rather than trusting the reported DPI, and the scale is applied to
`attachBaseContext`, so the very first frame after the change is correct. The activity recreates
when the effective scale changes — that is expected, not a crash.

### "Text got huge after I changed the system font size"

Settings → Display → **Respect system font scale**. On, the system setting is honoured (clamped
0.85–1.30 so a 2.0× accessibility setting cannot push the grid off screen). Off, the launcher
pins 1.0×. Issue #45.

### "The status bar is gone"

Settings → Status bar → **Show status bar**. It is also hidden while a full-screen video surface
is in front — bring the launcher to the foreground.

### "Wallpaper looks stretched / blurry"

Custom wallpapers are decoded down-sampled to the physical screen size. A very small source image
will be upscaled; use something at least as wide as the panel (1920 px for 1080p).
Settings → Wallpaper → *Scrim* controls the dark overlay behind the grid (full / top / bottom /
both edges / none) — that is what makes text readable over a busy photo.

---

## Locks & security

### "App lock does not work when I open the app from somewhere else"

**Cause:** enforcement outside our own grid runs through the **accessibility service**. If it is
disabled, only launches from the home grid are gated.

**Fix:** Settings → Security → the accessibility alert, or the system banner. On many TV builds:
Settings → Accessibility → Gothwad Launcher → enable. Some OEMs kill it after a reboot — the
status bar shows a warning icon whenever it is off.

### "The lock screen flashes and then the app opens anyway"

That would be a fail-open bug. Current behaviour when the overlay cannot be attached is to send
you HOME instead. If you see the locked app, capture a screen recording and the device model —
this is a genuine defect worth an issue.

### "I forgot the PIN"

There is no recovery path by design: only a salted PBKDF2 hash is stored, `allowBackup=false`,
and nothing is uploaded. Clearing app data (system Settings → Apps → Gothwad Launcher → Storage →
Clear data) resets everything, including the lock and the hidden list.

### "The hidden-apps reveal code no longer shows in settings"

Intentional. The field used to be prefilled with the plain-text code; now only the hash is stored,
so the dialog explains that the current code cannot be shown. Set a new one to change it.
If you upgraded from an older build, the existing plain-text code was hashed and cleared on first
launch and still works.

### "It says 'too many attempts'"

5 free attempts per scope (app / vault / device), then 30 s doubling up to a 5-minute cap. The
timer uses `elapsedRealtime`, so changing the clock does not shorten it. A correct entry resets it.

---

## Remote & input

### "The HOME button does nothing / opens the stock launcher"

The accessibility service intercepts HOME (`flagRequestFilterKeyEvents`). Enable it as above. On
firmware that hard-codes HOME to its own launcher, nothing an app can do will change it — the
watchdog will still bring our HOME forward when it detects a stock launcher.

### "My remote's buttons do not work in apps"

Button mapping only applies **while the launcher is in the foreground**; key events are handed
back untouched otherwise. Settings → Remote button mapping → capture mode: press the button you
want to map, then pick the app.

### "Quick Dashboard shows 'Remote: unknown'"

Bluetooth permission. On Android 12+ the dashboard requests `BLUETOOTH_CONNECT` when it opens; if
you denied it, the launcher reports *unknown* rather than inventing a name and battery level.

---

## Updates

### "'Check for updates' says an update exists but install fails"

Two install paths exist:

1. root / `pm install -r` — silent, needs `su` or a shell that can reach `pm`;
2. the system Package Installer — needs `REQUEST_INSTALL_PACKAGES` ("Install unknown apps")
   granted for Gothwad Launcher.

The badge in Settings → About tells you which capability was detected (root shell / package
installer / needs permission). If it says *needs permission*, grant it and retry.

### "The update check hangs the settings screen"

Fixed — the root/ADB probe used to run on the main thread (`su -c id` can block for as long as a
root prompt waits). The badge now shows "Checking…" and resolves on IO.

### "I installed the debug APK and now updates fail"

Debug and release builds are signed with different keys; Android refuses an in-place upgrade
across keys. Uninstall the debug build first (this loses config and locks). The GitHub Release
assets are the signed release APK and the Play AAB only — the debug APK is a workflow artifact.

---

## Still broken / known gaps

Tracked in [ROADMAP.md](ROADMAP.md). Highlights: no APK checksum verification after download
(HTTPS + the installer's signature check are the current guarantee), some UI strings are still
hard-coded English (see [I18N.md](I18N.md)), and `BootShieldService` runs as a plain started
service for up to 90 s which background-execution limits may cut short on aggressive firmware.
