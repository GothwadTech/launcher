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
- Three scrim modes (top, bottom, both) so text and tiles stay readable over bright images.
- Background-media awareness: when another app is playing audio, the launcher shows what is
  playing and can silence stock boot-ad audio while the boot shield is active.

### 🎙️ Quick Search & Voice Integration
- Instant app indexing and rapid fuzzy search.
- Voice search modal with direct voice recognition support (`RecognizerIntent`).

### 🌤️ Quick Dashboard
- One-panel overview: Wi-Fi/network status, Bluetooth remote state and battery (when the
  remote reports it), volume/mute control and shortcuts into the relevant system settings.
- No location access, no weather service — the launcher requests neither.

### 🔒 Privacy & App Security
- Built-in App Locker for device, individual apps and a hidden-apps vault.
- Credentials are stored as salted **PBKDF2** hashes (never plain text), with escalating
  lockout after repeated wrong attempts.
- The vault is enforced by the accessibility service too, so hidden apps cannot be opened
  from another launcher, the recents switcher or notifications while locked.
- `allowBackup=false`: config and credentials never leave the device through backups.

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
- **Tests:** JVM unit tests for the grid rules, config JSON round-trip and credential hashing
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

This repository includes automated workflows in `.github/workflows/`:
- **`build_apk.yml`**: builds Debug + Release APKs and the Play Store AAB on every push to
  `main` and on manual dispatch. It uploads them as **build artifacts only** — it does not
  create tags or releases, so it can never collide with a release build.
- **`release.yml`**: triggered by a `v*` **tag**. This is the only workflow that publishes a
  GitHub Release (with the APKs/AAB attached).
- Signing secrets are optional for ordinary builds: when they are missing the workflow
  skips the export step instead of failing the run (forks/PRs stay green).

---

## 📄 License

This project is licensed under the Apache License 2.0 — see the [LICENSE](LICENSE) file
for the full text.

