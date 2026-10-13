# Contributing

Thanks — this project runs on a TV box with 2 GB of RAM and firmware that lies about its own
density, so contributions that respect those constraints are especially welcome.

## Before you write code

1. Read [AGENTS.md](AGENTS.md). It is short and its first rule is absolute: **no Jetpack Compose**,
   not even a `ComposeView` bridge. This is a measured memory decision, not a taste preference.
2. Skim [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the layering and the process model, and
   [docs/PERFORMANCE.md](docs/PERFORMANCE.md) for the rules that keep it fast on a slow box.
3. Check [docs/ROADMAP.md](docs/ROADMAP.md) — your idea may already be scoped, with the reason it
   has not been done yet.

## Ground rules

- **Native Views only.** XML layouts, `Activity`/`Fragment`, ViewBinding, `RecyclerView`,
  `DialogFragment`/`BottomSheetDialogFragment`. Coroutines + `StateFlow` + `repeatOnLifecycle`.
  `SmoothCornerDrawable` and `AppIcons`/`VectorPathDrawable` for shapes and vectors.
- **No blocking work on the main thread.** No `PackageManager` queries, no bitmap decode, no
  `Runtime.exec`. `su -c id` on the main thread was a real ANR.
- **Only the main process touches `ConfigStore`.** The `:watchdog` process communicates through
  small files in device-protected storage (`ProcessHeartbeat`). Two processes on one Preferences
  DataStore file can corrupt it.
- **Fail secure.** If a lock overlay cannot attach, or config has not loaded yet, the user waits or
  goes HOME — a locked app is never revealed.
- **Never invent device state.** Bluetooth off means "unknown", not a made-up remote name and
  battery percentage. `getRunningAppProcesses() == null` means "unknown", not "dead".
- **Destructive behaviour is opt-in.** Anything that kills other apps or reboots defaults to off.
- **minSdk is 21.** Guard newer APIs (`SDK_INT >= M` for `PendingIntent.FLAG_IMMUTABLE`, etc.).
- **No `BuildConfig`.** `buildFeatures` enables ViewBinding only; read the version from
  `packageManager.getPackageInfo`.
- **No new dependencies without a memory justification.** `dependenciesInfo` is disabled for
  F-Droid reproducibility; keep it that way.

## Making a change

1. Fork, branch from `main`, keep commits focused.
2. Write the code. If you add a config field, follow the checklist in
   [docs/CONFIG.md](docs/CONFIG.md#adding-a-field--checklist) and update that table.
3. Add tests where the logic is pure (`data/` classes, no Android imports). See
   [docs/TESTING.md](docs/TESTING.md).
4. Update the docs that your change invalidates — especially `docs/CONFIG.md`,
   `docs/SECURITY.md` and `CHANGELOG.md` (add your entry under **Unreleased**).
5. Extract user-visible strings into `values/strings.xml`. See [docs/I18N.md](docs/I18N.md) —
   there is a backlog, but do not add to it.
6. Verify locally:

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew assembleRelease        # R8/minification is a different failure mode from compiling
```

   No Android SDK? The repository ships two static checks that need only Python, and CI runs them
   before Gradle:

```bash
python3 tools/ktcheck.py app/src              # every .kt file parses and is brace-balanced
python3 tools/check_resources.py app/src/main # every R.* / @type/name reference resolves
```

   They catch the two failure modes that hurt most without a compiler: a truncated source file,
   and a resource id that does not exist (a first-frame crash on a launcher).

7. Open a pull request against `main`. CI builds debug + release, runs the tests and lint, and
   attaches the debug APK — install it on a real box before asking for review if the change is
   visual.

## Pull request checklist

- [ ] No Compose, no `androidx.compose.*` import, no `@Composable`.
- [ ] No blocking work added to the main thread.
- [ ] New/changed config fields documented in `docs/CONFIG.md`.
- [ ] `CHANGELOG.md` updated under **Unreleased**.
- [ ] User-visible strings are resources.
- [ ] Tests added for pure logic, or an explicit note on what was verified by hand and on which
      device/firmware.
- [ ] `assembleRelease` builds (minification-safe).

The PR template repeats this; fill it in rather than deleting it.

## Reporting a bug

Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.yml). For a TV launcher the device
details are not optional boilerplate: **firmware behaviour varies more than phone behaviour does**.
Always include the box model, Android version, and how the launcher was installed (GitHub Release
APK / Play / F-Droid / self-built debug).

If the launcher crashes, attach the log:

```bash
adb shell run-as com.gothwad.launcher cat files/gothwad_crash.log
adb logcat -d | grep -iE "gothwad|AndroidRuntime" > logcat.txt
```

`run-as` requires a debuggable build.

## Security reports

Do not open a public issue for anything that lets someone bypass a lock or read a credential. Email
the maintainers listed in the repository profile instead, and allow time for a fix before
disclosure. Threat model and current guarantees: [docs/SECURITY.md](docs/SECURITY.md).

## Code style

Kotlin official style, 4 spaces, no wildcard imports, trailing commas in multi-line literals.
Comments explain **why** — a comment that restates the code is noise, a comment that records the
firmware bug the code works around is a gift to the next reader. Prefer `when` over if-chains,
`runCatching` over try/catch where the failure is expected, and `Log.e(TAG, msg, e)` with the
throwable attached.

## License

By contributing you agree your contributions are licensed under the project's license (see
[LICENSE](LICENSE)).
