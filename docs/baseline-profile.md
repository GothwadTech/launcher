# Baseline profile

`app/src/main/baseline-prof.txt` is compiled into the release APK by AGP
(`compileReleaseArtProfile`) and installed on the device by
`androidx.profileinstaller:profileinstaller:1.4.1` on first run. ART then AOT-compiles the listed
methods, so cold start and the first scroll happen in compiled code instead of being interpreted
and JIT'd.

On a 2 GB TV box this is the difference between "the grid appears and the first two seconds of
D-pad navigation stutter" and "it just works".

## Current state: hand-authored

There is **no Macrobenchmark module** in this repository, so the profile was written by hand from
the known startup and interaction paths:

```
HSPLcom/gothwad/launcher/**->**(**)**      every launcher class (all are on the startup path)
HSPLandroidx/recyclerview/**->**(**)**     grid + adapter + diffing
HSPLandroidx/fragment/**->**(**)**         nav host, dialogs, bottom sheets
HSPLandroidx/constraintlayout/**->**(**)** layouts
```

Flags: `H` = hot, `S` = startup, `P` = post-startup.

That is coarse but safe: broad rules only cost profile size (a few KB) and slightly longer install
time. The risk of a hand-written profile is the opposite of a generated one — you may list classes
that do not exist, which are dropped silently, or miss ones that matter.

## Regenerating it properly

A generated profile is measured, not guessed. To add the harness:

1. Create a `:benchmark` module (com.android.test, `targetProjectPath = ":app"`) and add
   `androidx.benchmark:benchmark-macro-junit4` plus
   `androidx.test.ext:junit` / `androidx.test.uiautomator`.
2. Add the matching build types to `app/build.gradle.kts`:

   ```kotlin
   buildTypes {
       getByName("release") { /* existing */ }
       // Benchmarkable release: minified like the real thing, but profileable and
       // signed with the debug key so the instrumentation can attach.
       create("benchmark") {
           initWith(getByName("release"))
           signingConfig = signingConfigs.getByName("debugConfig")
           matchingFallbacks += listOf("release")
           isDebuggable = false
           isProfileable = true
       }
   }
   ```

3. Write a `BaselineProfileGenerator` that walks the real paths, in order:

   ```kotlin
   @RunWith(AndroidJUnit4::class)
   @LargeTest
   class BaselineProfileGenerator {
       @get:Rule val rule = BaselineProfileRule()

       @Test
       fun generate() = rule.collect(
           packageName = "com.gothwad.launcher",
           includeInStartupProfile = true,
       ) {
           startActivityAndWait()                    // cold start to the grid
           // D-pad through the grid: focus moves, cards inflate, icons decode.
           repeat(12) { pressDpadDown(); pressDpadUp() }
           // The status bar and its collectors.
           // Open the settings sheet, visit each page, close it.
           // Open search, type two characters, clear, close.
           // Open the Quick Dashboard, close it.
       }
   }
   ```

   `pressDpadDown()`/`pressDpadUp()` come from UiAutomator; the launcher is a HOME activity so
   `startActivityAndWait()` lands on the grid.

4. Run it against a device or emulator **matching the target architecture** (most boxes are
   `arm64-v8a`; the emulator image is x86_64 — profiles are architecture-independent in rule form,
   but measure on real hardware before trusting the numbers):

   ```bash
   ./gradlew :app:generateBenchmarkReleaseBaselineProfile
   ```

   The task writes `app/src/benchmarkRelease/generated/baselineProfiles/baseline-prof.txt`. Copy it
   over `app/src/main/baseline-prof.txt` (or move the module's generated output into
   `src/main/`) and commit it.

5. Add a `Durations`/`StartupMode` benchmark test (`startupTimeMs`, `timeToInitialDisplayMs`) and
   record the before/after numbers in the PR.

## Verifying a profile is applied

```bash
# install the release APK, launch once, then:
adb shell dumpsys package dexopt | grep -A3 com.gothwad.launcher
#   look for "status=speed-profile" — that means a profile was used.

# what the runtime thinks is hot
adb shell cmd package compile -m speed-profile -f com.gothwad.launcher
adb shell dumpsys package dexopt | grep -A3 com.gothwad.launcher
```

If the status is `verify` or `speed`, the profile did not install — usually because the APK is
debuggable (profileinstaller skips debug builds) or the profile was not packaged.

## Pitfalls

- **Debug builds ignore the profile.** `profileinstaller` only acts on non-debuggable builds, so
  `assembleDebug` will never show the benefit. Measure with `assembleRelease` (or the
  `benchmark` build type above).
- **R8 renames classes.** Rules are matched against the *shrunken, renamed* release APK. Broad
  `com/gothwad/launcher/**` rules survive because AGP applies the profile after minification;
  hand-written rules naming specific app classes can silently miss if those classes are renamed or
  removed. Prefer package globs for app code, exact paths for library code.
- **Do not list classes that no longer exist.** They are dropped without warning, which is why a
  generated profile is better than a maintained-by-hand one after a refactor.
- **Profile size is not free but is cheap.** A few hundred KB in the APK; keep it under ~1 MB.
- **Do not commit a profile generated on an emulator with a different Android version** than the
  fleet you target (minSdk 21, targetSdk 34) without checking it still parses — profile format
  versions differ across releases.
