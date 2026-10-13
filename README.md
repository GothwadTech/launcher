# Gothwad Launcher

<p align="center">
  <b>A high-performance, lightweight launcher tailored for Android TV.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20TV-blue?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/Min%20SDK-21%20(Lollipop)-green?style=flat-square" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-orange?style=flat-square" alt="Target SDK" />
  <img src="https://img.shields.io/badge/Language-Kotlin-purple?style=flat-square" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Native%20Android%20Views%20%2F%20ViewBinding-blueviolet?style=flat-square" alt="Views" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-lightgrey?style=flat-square" alt="License" />
</p>

---

## 🌟 Overview

**Gothwad Launcher** is an ultra-fast, modern Android launcher designed from the ground up for TV screens (10-foot experience). Built with **Native Android Views, ViewBinding, and RecyclerView**, it delivers zero-lag navigation, rich visual aesthetics, and extensive customization without bloat.

---

## ✨ Features

### 📺 Android TV Interface
- Optimized for D-pad navigation, high-contrast focus indicators, leanback cards, and seamless remote control support.

### 🎬 Wallpapers
- Five built-in gradient presets (Midnight, Aurora, Sunset, Deep, Charcoal) plus custom
  wallpapers picked through the Storage Access Framework.
- Five scrim modes (full, top only, bottom only, both edges, none) so text and tiles stay
  readable over bright images.
- Background-media awareness: when another app is playing audio, the launcher shows what is
  playing and can silence stock boot-ad audio while the boot shield is active.

### 🎙️ Quick Search & Voice Integration
- Instant app indexing and rapid fuzzy search.
- Voice search modal with direct voice recognition support (`RecognizerIntent`).

### 🌤️ Quick Dashboard
- Opened by selecting the **clock cluster** in the status bar.
- One-panel overview: Wi-Fi/network status, Bluetooth remote state and battery (when the
  remote reports it), volume/mute control and shortcuts into the relevant system settings.
- Bluetooth details are requested at the moment you look at them (`BLUETOOTH_CONNECT` on
  Android 12+); if you decline, the panel says *unknown* rather than inventing a value.
- No location access, no weather service — the launcher requests neither.

### 🔒 Privacy & App Security
- Built-in App Locker for device, individual apps and a hidden-apps vault.
- Credentials are stored as salted **PBKDF2** hashes (never plain text), with escalating
  lockout after repeated wrong attempts.
- The vault is enforced by the accessibility service too, so hidden apps cannot be opened
  from another launcher, the recents switcher or notifications while locked.
- `allowBackup=false`: config and credentials never leave the device through backups.

### 📐 Display & scaling
- Density is derived from a 960 dp design width instead of trusting the firmware's reported
  DPI, which on TV boxes is often nonsense.
- **UI Scale** (0.75×–1.3×) and corner radius are user settings; the system font scale is
  honoured (clamped 0.85–1.30) or pinned to 1.0×, your choice.
- The scale is mirrored into a small SharedPreferences so the first frame after a cold start
  is already correct — the DataStore config arrives too late for that.

### ⚡ Performance & Optimization
- Crash/ANR self-healing: a file-backed heartbeat plus a `:watchdog` process, with a
  **safe mode** that stops the relaunch loop if the launcher keeps crashing.
- Boot-ad / stock-launcher shield runs in a service (never inside the boot receiver).
- Options are intentionally honest about memory: aggressive "close other apps" trimming is
  **off by default** (Settings → Apps) and only runs when you enable it.
- Pure Kotlin DSL and lightweight DataStore persistence.
- ProGuard/R8 rules pre-configured, including the kotlinx.serialization keep rules needed
  by R8 full mode.
- A starter baseline profile ships in `app/src/main/baseline-prof.txt`; it is
  hand-maintained, see [docs/baseline-profile.md](docs/baseline-profile.md) for how to
  regenerate it properly with Macrobenchmark.

---

## 🛠️ Tech Stack

- **UI Framework:** **Native Android Views** (XML layouts + ViewBinding), Material Components
  (`com.google.android.material:material`) — **no Jetpack Compose** anywhere in this project
  (see `AGENTS.md`: the Compose implementation was removed in favour of Views).
- **Language:** Kotlin 2.0 with Coroutines & StateFlow
- **Navigation & Lists:** Navigation Component (fragments), RecyclerView / ListAdapter
- **Continuous Corners:** AndroidX Graphics Shapes (`androidx.graphics:graphics-shapes`) for smooth squircle cards
- **Storage & State:** AndroidX Preferences DataStore, Kotlinx Serialization
- **Security:** PBKDF2 (`javax.crypto`) credential hashing, `MessageDigest.isEqual` comparisons
- **Performance:** AndroidX ProfileInstaller with a starter baseline profile
  (regeneration guide: `docs/baseline-profile.md`)
- **Build:** AGP 8.5.2 + Gradle 8.10.2 (wrapper pinned), JDK 17, R8 full mode
- **Tests:** JVM unit tests for config JSON decoding and credential hashing/verification
  (`./gradlew :app:testDebugUnitTest`)

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio Ladybug (2024.2+)** or later
- **JDK 17**
- Android SDK with Platform 34 installed

### Building Locally

1. **Clone the repository:**
   ```bash
   git clone https://github.com/gothwadtech/launcher.git
   cd launcher
   ```

2. **Build Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   The generated APK will be available in:
   `app/build/outputs/apk/debug/app-debug.apk`

3. **Build Release APK & App Bundle (AAB):**
   ```bash
   ./gradlew assembleRelease bundleRelease
   ```

---

## 🤖 CI / CD (GitHub Actions)

Three workflows in `.github/workflows/`, with the shared logic in two composite actions
(`resolve-version`, `prepare-signing`) so they cannot drift apart:

| Workflow | Trigger | Does | Publishes |
| --- | --- | --- | --- |
| `ci.yml` | every **pull request**, push to `main`, manual | `assembleDebug` + `assembleRelease` (minified) + unit tests + lint | Debug APK and reports as run artifacts |
| `release.yml` | tag `v*`, manual | build, sign, verify | **GitHub Release** — signed Release APK + Play Store AAB |
| `build_apk.yml` | manual only | build whatever you ask for | Artifacts only, never a release |

- `versionCode` is derived from the version name (`MAJOR*10000 + MINOR*100 + PATCH`), so it is
  monotonic and two workflows cannot collide on a tag.
- Gradle signs both artifacts (v1+v2+v3). Signing secrets are **required** for `release.yml` —
  a missing keystore fails the job instead of publishing an unsigned "release"; they stay
  optional for `build_apk.yml` so forks and PRs stay green.
- The debug APK is attached to the *run*, not to the release: it is signed with a different key,
  so installing it would block later upgrades.

Full details — secrets, local signing, F-Droid reproducibility, cutting a release:
[docs/CI_RELEASE.md](docs/CI_RELEASE.md).

---

## 📚 Documentation

| | |
| --- | --- |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layers, process model, data flow, startup paths, invariants |
| [docs/CONFIG.md](docs/CONFIG.md) | Every persisted setting: type, default, reader, writer |
| [docs/SECURITY.md](docs/SECURITY.md) | Threat model, credential storage, lock enforcement, permissions |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | Memory/startup budget, caching, the rules that keep a 2 GB box usable |
| [docs/CI_RELEASE.md](docs/CI_RELEASE.md) | Workflows, secrets, signing, version policy |
| [docs/TESTING.md](docs/TESTING.md) | What is covered, how to run it, what to add |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | Device/OEM symptoms → cause → fix |
| [docs/baseline-profile.md](docs/baseline-profile.md) | The shipped profile and how to regenerate it with Macrobenchmark |
| [docs/I18N.md](docs/I18N.md) | String-extraction status and remaining inventory |
| [docs/ISSUE_LOG.md](docs/ISSUE_LOG.md) | The `#NN` numbers referenced in code comments |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Known remaining work and explicit non-goals |
| [CONTRIBUTING.md](CONTRIBUTING.md) · [AGENTS.md](AGENTS.md) | Ground rules, PR checklist, code style |

---

## 📄 License

This project is licensed under the Apache License 2.0 — see the [LICENSE](LICENSE) file
for the full text.

