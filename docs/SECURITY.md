# Security & privacy

## 1. Threat model

A launcher on a shared family TV / set-top box. Realistic attackers and accidents:

| Threat | Mitigation |
| --- | --- |
| Another household member opens a locked app from the home grid | `AppLockGate` prompts before launch |
| The same, but through **another launcher, recents, a notification or ADB** | `LauncherAccessibilityService` enforcement + `AppLockOverlayView` |
| Someone reads the PIN out of a backup or the data partition | Salted PBKDF2 hashes only, `allowBackup=false`, no plain text |
| Brute force with a remote (a 4-digit PIN is 10 000 combinations) | Per-scope escalating lockout |
| Hidden ("vault") apps discovered by typing into search | Reveal code, now hashed; second-layer vault credential; throttled |
| A crash loop makes the TV unusable | Relaunch budget + safe mode |
| A self-update installs something tampered with | HTTPS + Android's installer signature check (must match the installed key) |

Out of scope: a rooted device with an attacker-controlled superuser (nothing on-device survives
that), and screen recording by another app with overlay permission.

## 2. Credential storage — `LockSecurity`

```
algorithm   PBKDF2WithHmacSHA1   (present on every supported API level)
iterations  12 000
key length  256 bit
salt        16 random bytes (SecureRandom), Base64
compare     MessageDigest.isEqual  (constant time)
encoding    kotlin.io.encoding.Base64 — pure Kotlin, so the core stays JVM-testable
```

Stored per lock as `LockCredential.credentialHash` + `credentialSalt`. `LockCredential.value` is
a **legacy** plain-text field kept only so `migrateLegacyCredentials()` can hash and clear it on
first launch after an upgrade; `ready` returns true for either form so a lock is never treated as
"not configured" during that window.

The hidden-apps **reveal code** used to be stored as plain text next to the hashed PINs and was
echoed back into an editable text field. It now goes through the same primitive
(`applyRevealCode` / `verifyRevealCode`), is normalised with `trim().lowercase(Locale.ROOT)` to
preserve the previous case-insensitive behaviour on a TV remote, and the settings dialog states
that the current code cannot be shown.

If PBKDF2 were ever unavailable, `hash()` returns `""` and verification fails — it never falls
back to comparing plain text.

### Brute-force throttle

Per scope (`app`, `vault`, `device`) in SharedPreferences `gothwad_lock_throttle`, timed with
`SystemClock.elapsedRealtime()` so a wall-clock change cannot shorten a lockout:

* 5 free attempts, then `30 s << (fails - 5)`, capped at **5 minutes**.
* A correct entry clears the counter (`recordSuccess`).

Deliberate gap: the reveal code typed into the **search box** is not throttled, because throttling
there would lock the user out of ordinary searching. The vault itself is still protected by the
second-layer `hiddenAppsLock` credential, which *is* throttled.

## 3. Enforcement layers

1. **UX** — `AppLockGate.evaluate(context, fragmentManager, app, config) { launch() }`, the only
   place `MainActivity` and `TvLauncherFragment` ask. One implementation, so a rule change applies
   everywhere (issue #31).
2. **Authoritative** — the accessibility service reacts to `TYPE_WINDOW_STATE_CHANGED`. For a
   locked package it shows `AppLockOverlayView`; for a hidden package not unlocked in this session
   it shows the vault overlay **or sends the user HOME** if no vault credential exists.
   * Fails **secure**: if the overlay window cannot be attached (firmware refuses
     `TYPE_ACCESSIBILITY_OVERLAY` and `SYSTEM_ALERT_WINDOW` is not granted) the user is sent HOME
     rather than left looking at the locked app.
   * Fails **closed on startup**: until the first config emission arrives, `cachedConfig` is an
     empty default in which nothing is locked. `configLoaded` parks the foreground package in
     `pendingEnforcementPkg` and re-checks it the moment the real config lands.
   * System and settings packages are never intercepted (`StockLauncherDetector
     .isSystemOrSettingsPackage`), so the user can always reach Android Settings.
3. **Session scoping** — `unlockedPackagesSession` is an in-memory synchronised set. A package is
   removed as soon as the user moves to a different foreground app, and the set dies with the
   process. Unlocking Netflix does not unlock the next app.

**Honest limitation:** layer 2 requires the accessibility service to be enabled. Without it only
layer 1 (launches from our own grid) is protected. The status bar shows a warning icon in that
state (`setA11yAlertVisible`) and tapping it opens accessibility settings.

## 4. Accessibility service capabilities

`res/xml/accessibility_service_config.xml` asks for the minimum:

```
accessibilityEventTypes = typeWindowStateChanged
accessibilityFlags      = flagRequestFilterKeyEvents | flagDefault
canRequestFilterKeyEvents = true      ← HOME key + remote hotkeys + volume hold
canRetrieveWindowContent = false      ← we never read other apps' screen content
```

No `flagRetrieveInteractiveWindows`, no `canPerformGestures`, no touch exploration. The service
cannot read what other apps display; it only learns *which package* is in front and can filter key
events.

## 5. Permissions

| Permission | Why | Notes |
| --- | --- | --- |
| `QUERY_ALL_PACKAGES` | A launcher must be able to list every launchable app | Play requires a declaration; this is the core use case |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Self-update check against `api.github.com`; network status tile | No analytics, no tracking, no third-party endpoint |
| `RECEIVE_BOOT_COMPLETED` | Start on TV boot, run the boot-ad shield | Receiver returns immediately; work happens in a service |
| `SYSTEM_ALERT_WINDOW` | Fallback for the lock overlay on firmware that refuses `TYPE_ACCESSIBILITY_OVERLAY` | Only used to *cover* a locked app, never to draw over an unlocked one |
| `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT` | Remote name + battery level in the Quick Dashboard | Runtime-requested on Android 12+ when the dashboard opens; without it we report "unknown", never a made-up value |
| `PACKAGE_USAGE_STATS` | Most-used ordering and the boot shield's foreground detection | Special access, granted in system settings; everything degrades gracefully without it |
| `KILL_BACKGROUND_PROCESSES` | Opt-in aggressive memory trim | **Off by default** (issue #34) |
| `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES` | Self-update and uninstall from app details | |

Explicitly **not** requested:

* `RECORD_AUDIO` — voice search delegates to the system recognizer through
  `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, which records in its own process and returns text.
* Any location permission — no weather, no SSID-based location. On Android 12+ the Wi-Fi SSID
  therefore often reads `<unknown ssid>`; the UI shows "Connected" instead of guessing.
* `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` — wallpapers come through the Storage Access
  Framework (`ActivityResultContracts.OpenDocument`), which grants per-file access.
* `POST_NOTIFICATIONS` — the launcher posts no notifications of its own.

`android:allowBackup="false"` and `fullBackupContent="false"`: config and credentials never leave
the device through auto-backup. (This is also why the plain-text PIN era was so bad — see
[ISSUE_LOG.md](ISSUE_LOG.md) #7.)

## 6. Self-update path

`AppUpdateManager` talks to `https://api.github.com/repos/GothwadTech/launcher/releases/latest`,
picks the `-Release.apk` asset (preferring it over a debug asset), downloads it into
`cacheDir/updates/` and installs it:

1. `su -c pm install -r` / `sh -c pm install -r` / `pm install -r` — silent, only possible on
   rooted boxes or where a shell can reach `pm`. Every command is bounded by an 8 s timeout.
2. Otherwise `ACTION_VIEW` on a FileProvider URI → the system Package Installer, which shows the
   user a consent screen.

Trust model: transport is HTTPS and redirects are followed only to `http(s)` targets
(`openFollowingRedirects` rejects `file:`/`content:`/`jar:`). Integrity ultimately rests on
**Android's installer signature check** — a tampered APK cannot replace the installed one because
the signing key would not match. The `su` path skips the *consent prompt*, not the signature check.

Residual risks, stated plainly:

* The downloaded APK is made world-readable (`setReadable(true, false)`) so `pm` can read it. It
  lives in the app's own cache directory and is deleted on the next download.
* There is no independent checksum/signature pinning of the asset beyond HTTPS + the installer.
  Adding a published SHA-256 per release is on [ROADMAP.md](ROADMAP.md).
* A malicious actor with write access to the GitHub repository could publish a release — but they
  could also publish source, so this is not an additional capability.

## 7. Data at rest

| Data | Location | Encrypted? |
| --- | --- | --- |
| Config JSON (`launcher_config`) | CE internal storage (DataStore) | No — but it contains only hashes |
| Lock throttle counters | CE SharedPreferences | No (timestamps + counters) |
| Heartbeat / relaunch budget / policy | **Device-protected** internal storage | No (timestamps + one flag) |
| Crash log (`gothwad_crash.log`, ≤64 KB) | CE internal storage | No — stack traces only |
| Wallpaper (`wallpaper.jpg`) | CE internal storage | No |
| App art WebP cache | `cacheDir/app_art` | No — regenerated on demand |

Nothing is uploaded anywhere. There is no account, no analytics SDK, no crash reporting service.
