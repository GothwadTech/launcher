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

### 🎬 Dynamic Aerial & Video Wallpapers
- High-definition live video wallpapers and 4K aerial screensavers powered by **Media3 (ExoPlayer)**.
- Integrated background media control with automatic pause/resume on app launch.

### 🎙️ Quick Search & Voice Integration
- Instant app indexing and rapid fuzzy search.
- Voice search modal with direct voice recognition support (`RecognizerIntent`).

### 🌤️ Live Weather & Quick Dashboard
- Real-time weather display with automatic location detection and condition icons.
- Quick Dashboard for network status, storage insights, and fast access to system settings.

### 🔒 Privacy & App Security
- Built-in App Locker with PIN protection.
- Hide sensitive apps from the main grid with quick unhide settings.

### ⚡ Performance & Optimization
- **Baseline Profile** bundled for instant cold startup and jank-free scrolling.
- Pure Kotlin DSL and lightweight DataStore persistence.
- ProGuard and R8 rules pre-configured for minimal APK size.

---

## 🛠️ Tech Stack

- **UI Framework:** Jetpack Compose (1.7+), Android TV Material 3 (`androidx.tv:tv-material`), Material 3 (`androidx.compose.material3`)
- **Language:** Kotlin 2.0+ with Coroutines & StateFlow
- **Video & Media Engine:** AndroidX Media3 ExoPlayer (`media3-exoplayer`, `media3-datasource-okhttp`)
- **Continuous Corners:** AndroidX Graphics Shapes (`androidx.graphics:graphics-shapes`) for smooth squircle cards
- **Storage & State:** AndroidX Preferences DataStore, Kotlinx Serialization
- **Performance:** AndroidX ProfileInstaller with AOT Baseline Profiles

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
- **`build_apk.yml`**: Automatically builds, packages, and signs Debug and Release APKs + Play Store AAB bundles on every push or manual dispatch.
- **`release.yml`**: Automates GitHub Releases with version tagging and direct downloadable release APKs.

---

## 📄 License

This project is licensed under the Apache License 2.0. See the LICENSE file for details.

