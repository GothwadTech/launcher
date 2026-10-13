<!--
Thanks for the pull request. Please fill in the sections below rather than deleting them.
The checklist maps to the rules in AGENTS.md and CONTRIBUTING.md; a reviewer will check it
against the diff.
-->

## What does this change?

<!-- One or two sentences. Link the issue it fixes: "Fixes #123". -->

## Why?

<!-- The user-visible problem. If it is a performance or memory change, include the numbers. -->

## How did you verify it?

<!--
Be concrete. For anything visual or firmware-dependent, name the device.
"I ran it in my head" is not verification, and this project has no local Android SDK for some
contributors — say so and lean on CI plus a device you do have.
-->

- Device / firmware:
- Build installed (`assembleDebug`, GitHub Release APK, …):
- What you did and what happened:
- Unit tests added / run:

## Screenshots or recordings

<!-- Drag files in. Required for any visual change. -->

## Checklist

- [ ] **No Jetpack Compose** — no `androidx.compose.*` import, no `@Composable`, no
      `ComposeView`, no `buildFeatures { compose = true }` (AGENTS.md)
- [ ] No blocking work added to the main thread (no PackageManager queries, bitmap decode or
      `Runtime.exec` outside a background dispatcher)
- [ ] Only the main process touches `ConfigStore`; any cross-process state goes through
      `ProcessHeartbeat` in device-protected storage
- [ ] Security decisions stay in their single owner (`AppLockGate`, the accessibility service,
      `LockSecurity`) and fail **secure**, never open
- [ ] No invented device state — unknown is reported as unknown
- [ ] New/changed `LauncherConfig` fields documented in `docs/CONFIG.md` (and defaulted, never
      nullable)
- [ ] `CHANGELOG.md` updated under **Unreleased**
- [ ] User-visible strings are resources in `values/strings.xml` (docs/I18N.md)
- [ ] Newer-than-API-21 calls are guarded (`minSdk 21`)
- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` passes locally
- [ ] `./gradlew assembleRelease` passes (R8/minification is a separate failure mode)
- [ ] Docs that this change invalidates were updated (`docs/ARCHITECTURE.md`, `docs/SECURITY.md`,
      `docs/PERFORMANCE.md`, `README.md`)

## Notes for the reviewer

<!-- Anything subtle, anything you are unsure about, anything you deliberately did NOT do. -->
