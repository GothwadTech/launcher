# PC Windowing System - Windows/Linux Style in Android Launcher

## Sawal: Kya Android me Windows jaisa windowing possible hai?
**Jawab: Haan, 100% possible hai - 2 tariko se:**

### 1. Real Android Freeform Windowing (Native)
Android 7.0+ me freeform multi-window mode hai jo bilkul Windows jaisa kaam karta hai:
- Samsung DeX, Android 12L+, ChromeOS, Android 14+ Desktop Mode
- Apps real resizable windows me chalte hain
- System khud titlebar deta hai (minimize/maximize/close)
- Enable karne ke liye:
```bash
adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
```
- Launch: `ActivityOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM) = 5`

**Limitation:** Har device me enabled nahi hota, specially Android TV boxes me.

### 2. Simulated Windowing Inside Launcher (Humne jo banaya hai) ✅
Yeh har Android device pe kaam karta hai:

#### Architecture:
```
PcLauncherFragment
├── Desktop Grid (App Icons)
├── Windows Layer (FrameLayout) ← NEW! Yahan windows cards render hote hain
│   ├── PcWindowView (Card with titlebar)
│   │   ├── Titlebar: Icon + App Name + [Minimize] [Maximize] [Close]
│   │   └── Content: Big Icon + Open Button + Info
│   └── Multiple windows with z-index (focus)
└── Taskbar
    ├── Pinned Apps
    ├── Running Windows (NEW!) - Open apps dikhte hain
    └── System Tray
```

#### Features Jo Add Kiye:

**PcWindow.kt** - Window data model:
- x, y, width, height, zIndex
- isMinimized, isMaximized
- restore bounds for maximize/restore toggle

**PcWindowManager.kt** - Window manager (singleton):
- `openWindow()` - Naya window banao ya existing ko focus karo
- `closeWindow()` - Window band karo + app kill
- `minimizeWindow()` - Taskbar me minimize
- `restoreWindow()` - Wapas lao
- `toggleMaximize()` - Fullscreen / restore
- `focusWindow()` - Z-order update
- Cascade positioning (har naya window thoda offset)

**PcWindowView.kt** - Actual window card UI:
- Titlebar drag se move karo
- Bottom-right corner drag se resize karo
- Minimize: animation ke saath taskbar me jata hai
- Maximize: desktop area fill karta hai (taskbar ke upar tak)
- Close: scale down animation + kill app
- Focus: click se front me aata hai, elevation badhta hai

**Taskbar Running Apps:**
- `PcTaskbarRunningAdapter` - Open windows taskbar me dikhte hain
- Focused window highlighted
- Minimized faded
- Click: restore / focus / minimize toggle (Windows jaisa)
- Long press: close

**Show Desktop Button (Peek):**
- Windows style: ek click me saare windows minimize
- Dobara click: saare restore

#### Kya Kaam Karta Hai Ab:

1. **Desktop icon pe click** → Window card banta hai desktop pe
   - Titlebar me: app icon + name + [—] [□] [X]
   - Card draggable, resizable
   - Content me "Open Fullscreen" button

2. **Window controls:**
   - **Minimize (—)**: Window gayab, taskbar me chala jata hai running apps me
   - **Maximize (□)**: Poora desktop fill (taskbar ke upar), dobara click pe restore
   - **Close (X)**: Window band + app background process kill

3. **Taskbar running apps:**
   - Har open window ka icon taskbar me
   - Click se restore/focus
   - Already focused pe click se minimize (Windows behavior)
   - Long press se close

4. **Freeform support (optional):**
   - Settings me `pcFreeformEnabled = true` karne pe
   - Supported devices (DeX, 12L+) pe real floating window me app khulega
   - Humara window card minimize ho jayega, real window dikhega

#### Files Added/Modified:

**New Files:**
- `app/src/main/java/com/gothwad/launcher/ui/pc/PcWindow.kt`
- `app/src/main/java/com/gothwad/launcher/ui/pc/PcWindowManager.kt`
- `app/src/main/java/com/gothwad/launcher/ui/pc/PcWindowView.kt`
- `app/src/main/java/com/gothwad/launcher/ui/pc/PcTaskbarRunningAdapter.kt`
- `app/src/main/res/layout/layout_pc_window.xml`
- `app/src/main/res/drawable/bg_pc_window.xml`
- `app/src/main/res/drawable/bg_pc_window_titlebar.xml`
- `app/src/main/res/drawable/bg_pc_window_btn.xml`
- `app/src/main/res/drawable/bg_pc_window_btn_close.xml`

**Modified:**
- `fragment_pc_launcher.xml` - Added windows layer + running apps recycler
- `PcLauncherFragment.kt` - Full window manager integration
- `Actions.kt` - Added `launchAppInWindowedMode()` with freeform support
- `Config.kt` - Added `pcWindowingEnabled`, `pcFreeformEnabled`
- `Icons.kt` - Added window icons (minus, maximize, restore, resize)

#### Kaise Use Kare:

1. **Enable/Disable Windowing:**
```kotlin
// ConfigStore me
pcWindowingEnabled = true  // Window cards dikhenge
pcWindowingEnabled = false // Old direct launch
```

2. **Real Freeform Enable (DeX/12L devices):**
```kotlin
pcFreeformEnabled = true
// Device me developer options se freeform enable karna padega
```

3. **Custom Window Size:**
```kotlin
pcWindowDefaultWidth = 480
pcWindowDefaultHeight = 320
```

#### Future Improvements:

- [ ] TaskView / ActivityView se real app embedding (Android 10+)
- [ ] Window snapping (Windows 11 snap layouts)
- [ ] Alt+Tab switcher
- [ ] Virtual desktops
- [ ] Window thumbnails in taskbar hover

#### Demo Flow:

1. Desktop pe YouTube icon click → Window card appears (YouTube icon + title + controls)
2. Drag titlebar → Window moves
3. Drag bottom-right corner → Resize
4. Click Maximize → Full desktop fill
5. Click Minimize → Taskbar me jata hai
6. Taskbar me YouTube icon click → Restore
7. Window me "Open Fullscreen" click → Real YouTube app opens, window minimizes to taskbar
8. Taskbar running app long press → Close + kill

**Result: Bilkul Windows/Linux jaisa experience Android me!**
