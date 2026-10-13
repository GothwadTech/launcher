# Testing

## What runs today

```
app/src/test/java/com/gothwad/launcher/data/
├── ConfigSerializationTest.kt   5 tests
└── LockSecurityTest.kt         10 tests
```

JVM only (`testDebugUnitTest`), JUnit 4.13.2 — no Android runtime, no Robolectric, no Espresso.
`testOptions.unitTests.isReturnDefaultValues = true` is set so an incidental `android.util.Log`
call returns a default instead of throwing.

```bash
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest --tests "*LockSecurityTest*"
# reports: app/build/reports/tests/testDebugUnitTest/index.html
```

CI runs the tests and also builds `assembleRelease`, because minification is a different failure
mode from compilation — see [CI_RELEASE.md](CI_RELEASE.md).

## The rule that keeps this testable

`LockSecurity` uses **`kotlin.io.encoding.Base64`** and never imports `android.util.Base64`.
That is a deliberate constraint, not a style choice: it keeps the security-critical code on the
JVM so hashing, verification, throttling and legacy migration are covered by tests that run in
seconds with no device. Keep any new crypto/parsing logic in `data/` free of Android imports.

`Config`'s JSON layer is pure kotlinx.serialization for the same reason.

## What is covered

**ConfigSerializationTest**

* a legacy config document (old `sections`/`knownApps` shape) decodes into the current
  `LauncherConfig`;
* keys removed in a newer version are ignored, not fatal;
* defaults are applied for absent keys;
* the default UI scale is the neutral step (`2`, not a stale index into a table that moved);
* round-trip encode → decode is lossless.

**LockSecurityTest**

* hash produces a salted, non-deterministic digest;
* `verify` accepts the right credential and rejects a wrong one, including near-misses;
* `verify` against an unconfigured credential fails closed;
* legacy plain-text `LockCredential.value` verifies and then migrates to hash + salt;
* `migrateLegacyCredentials` clears the plain-text field and leaves an already-migrated
  credential untouched;
* reveal-code hash/verify round-trip, case-insensitive (TV remotes), and empty-code rejection;
* throttle: free attempts, exponential backoff, 5-minute cap, reset on success.

## What to add when you change something

| You touched… | Add a test for… |
| --- | --- |
| `LauncherConfig` / a new field | Default value, round-trip, and a legacy document that lacks the field |
| `LockSecurity` | The new failure mode, including "credential not configured" |
| Any `data/` class with branching logic | Extract the pure part into `data/` and test it there |
| `ConfigStore` decode path | A malformed JSON blob must fall back to defaults (not crash) |
| `AppUpdateManager` URL/redirect logic | The scheme filter — anything not `http(s)` must be rejected |
| Adapter diffing / config application | Manual verification on a device is fine; document it in the PR |

## What is *not* automated (be explicit in PRs)

These paths cannot be tested on the JVM and are verified by hand on a device or emulator:

* accessibility enforcement (locked app opened from recents / another launcher);
* the boot shield and watchdog relaunch path, including the direct-boot window;
* overlay attach/deny behaviour per OEM firmware;
* density and UI-scale rendering (`DensityAdapter`);
* the self-update download + install flow.

A PR touching any of these should say what was tried, on which device/firmware, and what happened.
"Works on my box" without the box model is not enough — TV firmware differs more than phones do.

## Debugging aids on the device

```bash
# current config
adb shell run-as com.gothwad.launcher cat files/datastore/launcher_config.preferences_pb | strings

# relaunch budget / safe mode
adb shell run-as com.gothwad.launcher cat files/gothwad_selfheal
adb shell run-as com.gothwad.launcher cat files/gothwad_crash.log

# is the watchdog alive?
adb shell ps -A | grep gothwad

# which package is in front?
adb shell dumpsys activity activities | grep -E "ResumedActivity|topResumedActivity"
```

`run-as` needs a debuggable build.
