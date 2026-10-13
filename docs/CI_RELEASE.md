# CI, signing and releases

Three workflows, three different jobs. They used to overlap: `build_apk.yml` ran on every push to
`main` *and* published a GitHub Release, while `release.yml` published one on tags — two releases
per change, with version codes derived from two independent run-number counters.

| Workflow | Trigger | Purpose | Publishes |
| --- | --- | --- | --- |
| [`ci.yml`](../.github/workflows/ci.yml) | every **pull request**, push to `main`/`master`, manual | Does it compile, do tests pass, does lint regress, does the **minified release** build? | Debug APK + reports as artifacts |
| [`release.yml`](../.github/workflows/release.yml) | tag `v*`, manual | Build, sign, publish | GitHub Release (APK + AAB) |
| [`build_apk.yml`](../.github/workflows/build_apk.yml) | manual only | "Build me something I can sideload" | Artifacts only |

Shared logic lives in two composite actions so the workflows cannot drift apart:

* [`.github/actions/resolve-version`](../.github/actions/resolve-version/action.yml) — version name + code
* [`.github/actions/prepare-signing`](../.github/actions/prepare-signing/action.yml) — keystore from secrets

## 1. Version policy

```
versionCode = MAJOR*10000 + MINOR*100 + PATCH        v1.0.7  -> 10007
                                                      v1.2.13 -> 10213
```

Derived from the **version name**, not from a run counter, so it is deterministic and monotonic:
re-running a workflow cannot produce a lower code, and two workflows cannot collide. If the name
carries no digits at all, it falls back to `10000 + GITHUB_RUN_NUMBER`.

`app/build.gradle.kts` defaults to `versionCode = 7`, `versionName = "1.0.6"` and accepts
overrides:

```bash
./gradlew assembleRelease -PversionCode=10007 -PversionName=1.0.7
```

Any scheme that produced a code ≤ a previously published one would be rejected by the Play Store.

## 2. Signing

Gradle signs, not shell scripts. `prepare-signing` decodes the keystore, detects the alias when
`KEY_ALIAS` is not set, and exports:

```
KEYSTORE_FILE  KEYSTORE_PASSWORD  KEY_ALIAS  KEY_PASSWORD
```

`app/build.gradle.kts` reads them through `providers.environmentVariable(...)` — **not**
`System.getenv`. `gradle.properties` enables the configuration cache, and only the provider API
registers the variable as a configuration input; with `System.getenv` a cached configuration could
keep signing with a stale or absent keystore.

Signing priority for `release`:

| Condition | Result |
| --- | --- |
| `KEYSTORE_FILE` set | `signingConfigs["ci"]` — your real key (AGP produces v1+v2+v3 signatures) |
| `-PlocalSign` | `signingConfigs["debugConfig"]` — repo-root `debug.keystore` if present, else `~/.android/debug.keystore` |
| otherwise (incl. F-Droid) | **unsigned** — the publisher signs it |

The old flow signed the APK with `apksigner` and the AAB with `jarsigner` after the build.
`jarsigner` only produces a v1 signature, which is not what the Play Store wants for a bundle;
Play App Signing signs the AAB itself, so the AAB is now published exactly as Gradle produced it.

### Required repository secrets

| Secret | Required | Notes |
| --- | --- | --- |
| `RELEASE_KEYSTORE_BASE64` (or `KEYSTORE_BASE64`) | for `release.yml` | `base64 -w0 release.keystore` |
| `KEYSTORE_PASSWORD` | for signing | |
| `KEY_PASSWORD` | for signing | |
| `KEY_ALIAS` | optional | auto-detected with `keytool -list -v` when absent |

`release.yml` passes `required: true`, so a missing keystore **fails the job** instead of
publishing an unsigned "Release" APK to users. `build_apk.yml` passes `required: false` and just
warns.

The decoded keystore is written to `$RUNNER_TEMP` with mode 600 and deleted in an
`if: always()` step.

### The debug keystore step

`app/build.gradle.kts` signs the **debug** build type with a custom `debugConfig` pointing at
`~/.android/debug.keystore`. AGP only auto-generates a keystore for its own built-in `debug`
config, so every workflow that runs `assembleDebug` creates one with `keytool` first. Remove that
step and the build fails with *"Keystore file … not found for signing config 'debugConfig'"*.

## 3. F-Droid / reproducible builds

```kotlin
dependenciesInfo { includeInApk = false; includeInBundle = false }
```

The Google-signed dependency metadata block is not embedded, and a release build without
`KEYSTORE_FILE` stays unsigned so F-Droid can sign it with its own key. Keep both properties if
you touch the build script.

## 4. What CI actually verifies

`ci.yml` runs `assembleDebug assembleRelease testDebugUnitTest` and then `lintDebug`.

`assembleRelease` is part of verification **on purpose**: `isMinifyEnabled` and
`isShrinkResources` are on, and R8 is where missing keep rules and over-shrunk resources surface.
A debug-only CI would happily merge a release that boots into a reset home screen because
`Json.decodeFromString<LauncherConfig>()` lost its serializer — the exact scenario
`app/proguard-rules.pro` exists to prevent.

Lint currently runs with `abortOnError = false`, so it reports but does not block. Two ways to
make it bite once the outstanding warnings are cleaned up:

```kotlin
lint { abortOnError = true; baseline = file("lint-baseline.xml") }
```

## 5. Local commands

```bash
./gradlew assembleDebug                      # installable debug APK
./gradlew assembleRelease                    # unsigned, minified release
./gradlew assembleRelease -PlocalSign        # release signed with the debug key
./gradlew testDebugUnitTest                  # JVM unit tests
./gradlew lintDebug                          # report in app/build/reports/
./gradlew bundleRelease                      # Play Store AAB
```

Output lands in `app/build/outputs/apk/{debug,release}/` and `app/build/outputs/bundle/release/`.

## 6. Cutting a release

```bash
git tag v1.0.7
git push origin v1.0.7
```

`release.yml` builds `versionCode 10007`, signs, verifies with `apksigner verify --print-certs`,
and publishes `GothwadLauncher-v1.0.7-Release.apk` plus `-PlayStore.aab` to a GitHub Release with
generated notes. The debug APK is attached to the **run** as an artifact, not to the release: it is
signed with a different key, so a user who installs it cannot upgrade to the signed release without
uninstalling first.

Or use *Actions → Release → Run workflow* and type the version explicitly.
