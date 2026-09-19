# DPI Independence + File Manager + Taskbar Overlay Fix

## 1. DPI Independence - System DPI se Independent UI

### Problem:
- Android me Developer Options me DPI change hota hai: 200, 320, 400, etc
- `ro.sf.lcd_density` ya Settings -> Display -> Display Size
- Jab user DPI change karta hai, launcher UI bigad jata hai:
  - Icons bade/chote ho jate hain
  - Taskbar height change
  - Layout toot jata hai
  - Tumne jo exact UI design kiya hai wo change ho jata hai

### Current Situation (Pehle):
```kotlin
val density = resources.displayMetrics.density // System DPI pe depend
// System DPI 320 = density 2.0
// System DPI 400 = density 2.5
// System DPI 200 = density 1.25
// Same dp value different px me convert hota hai
```

### Solution Implemented:

#### A. Fixed DPI via `DpiHelper`:
- `GothwadApplication.attachBaseContext()` me system DPI override
- `MainActivity.attachBaseContext()` me bhi override
- `onConfigurationChanged()` me re-apply jab system DPI change ho

```kotlin
// Fixed DPI = 320 (density 2.0) - hamesha same rahega
DpiHelper.applyFixedDensity(context, 320)
// Chahe system DPI 200 ho ya 400, humara UI 320 pe hi rahega
```

#### B. How it works:
1. `createConfigurationContext()` se naya context banate hain jisme `densityDpi = 320` forced
2. `displayMetrics.density = 2.0` forced
3. System DPI change ka event aaye to `patchResources()` se wapas fixed kar dete hain

#### C. Config options:
```kotlin
pcDpiIndependent = true  // System DPI ignore karo
pcFixedDpi = 320         // Kaunsa fixed DPI use karna hai
// Options: 240 (compact), 280 (medium), 320 (standard), 360 (large), 400 (xlarge)
```

#### D. Benefits:
- ✅ User system DPI kuch bhi set kare, launcher UI exact same rahega
- ✅ Tumne jo design kiya hai wo hi dikhega
- ✅ PC mode stable rahega
- ✅ `pcUiScale` still works for user preference scaling on top

#### E. Disable karna ho to:
```kotlin
// SharedPrefs
getSharedPreferences("launcher_dpi_prefs", MODE_PRIVATE)
  .edit()
  .putBoolean("dpi_independent", false)
  .apply()
// Ya ConfigStore me pcDpiIndependent = false
```

---

## 2. PC Level File Manager

### Problem:
- PC launcher basic tha, file manager nahi tha
- Windows me File Explorer hota hai

### Solution: Full Windows Explorer Style File Manager

#### Features:
- **Storages:** Internal Storage, SD Card, USB Drive auto-detect
- **Quick Access:** Downloads, Pictures, Movies, Music, Documents, DCIM
- **File Operations:**
  - Open file (with FileProvider, any app)
  - New Folder
  - Rename
  - Delete (with confirm)
  - Share
  - Properties (size, date, permissions)
- **Sorting:** Name, Size, Date, Type
- **View:** List mode (grid future)
- **Navigation:** Back, Up, Home, Refresh, Address bar
- **Status Bar:** File count, free space, selected path

#### Files:
- `PcFileItem.kt` - File model with type detection (image, video, audio, apk, zip, doc)
- `PcFileManager.kt` - Core logic, storage detection via `getExternalFilesDirs()`
- `PcFileManagerAdapter.kt` - Recycler for files + sidebar
- `PcFileManagerDialogFragment.kt` - Fullscreen dialog with Windows Explorer UI
- `layout_pc_file_manager.xml` - Main layout: titlebar + nav bar + sidebar + files + status bar
- `item_pc_file.xml` - File item: icon + name + details + more button
- `file_paths.xml` - FileProvider config for sharing

#### How to Open:
- Taskbar me File Manager icon (yellow folder) click
- Start Menu me File Manager button click
- Desktop context menu se (future)

#### Permissions:
```xml
READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE (legacy)
MANAGE_EXTERNAL_STORAGE (Android 11+)
FileProvider for secure file sharing
```

#### Future:
- Grid view toggle
- Copy/Cut/Paste with clipboard
- Search inside folder
- Show hidden files toggle
- Thumbnail preview for images

---

## 3. Taskbar Over Other Apps - Correct Fix

### Problem (User ne sahi bola):
- `SYSTEM_ALERT_WINDOW` permission se taskbar overlay karte hain over other apps
- Lekin actual app fullscreen open hota hai, niche tak
- Hamara taskbar uske upar aata hai, app ka bottom part hide ho jata hai
- Example: YouTube ka controls taskbar ke peeche chale jate hain

### Wrong Approach (Pehle):
```kotlin
// Overlay taskbar over fullscreen app
WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
// App fullscreen, taskbar uske upar = content hidden
```

### Correct Approach (Ab):

#### A. Freeform Windowing with Bounds (Best - Windows jaisa):
```kotlin
// Launch app with bounds excluding taskbar area
val taskbarHeight = 44dp
val bounds = Rect(0, 0, screenWidth, screenHeight - taskbarHeight)
ActivityOptions.setLaunchBounds(bounds)
ActivityOptions.setLaunchWindowingMode(FREEFORM = 5)
// App taskbar ke upar tak hi open hoga, niche nahi jayega
// Bilkul Windows jaisa: app maximize hota hai taskbar ke upar tak
```

#### B. When Freeform Not Supported (Fallback):
- Taskbar overlay nahi dikhana over other apps
- Taskbar sirf launcher me dikhega
- App open hote hi launcher background, taskbar hidden
- Ya auto-hide with transparency + toggle button

#### C. Implementation:

**`Actions.kt`:**
```kotlin
fun launchAppWithTaskbarBounds(context, pkg, taskbarHeightPx): Boolean {
    // Launch with bounds = screen - taskbarHeight
    // App won't go behind taskbar
}

fun launchAppInWindowedMode() {
    // Uses taskbar-aware bounds
}
```

**`PcTaskbarOverlayService.kt`:**
- Service jo taskbar ko overlay ke roop me dikhata hai
- Sirf tab chalta hai jab `pcTaskbarOverlayEnabled = true`
- Freeform mode me apps ko taskbar bounds ke saath launch karta hai
- Config change pe auto hide/show

**`Config`:**
```kotlin
pcTaskbarOverlayEnabled = false // Default off - safe
pcTaskbarAutoHide = false       // Auto-hide like Windows
```

#### D. User Options:
1. **No Overlay (Default, Safe):**
   - Taskbar only in launcher
   - App fullscreen, taskbar hidden
   - No content hidden issue

2. **Overlay with Freeform (Best, Windows-like):**
   - Enable `pcTaskbarOverlayEnabled` + `pcFreeformEnabled`
   - Enable freeform via ADB:
     ```bash
     adb shell settings put global enable_freeform_support 1
     adb shell settings put global force_resizable_activities 1
     ```
   - Apps will open above taskbar, not behind
   - Real Windows taskbar experience

3. **Overlay without Freeform (Not Recommended):**
   - Taskbar over fullscreen app
   - Content hidden behind taskbar
   - User can toggle auto-hide

#### E. Why Can't We Reserve Space Without System Permission?
- Android non-system apps cannot reserve screen space like Windows
- Only system launcher or freeform can control other apps' window bounds
- Our best effort is freeform bounds + overlay service
- For true Windows taskbar, device needs to be rooted or system app, or use DeX/ChromeOS

### Summary:
- ✅ DPI fix: `DpiHelper` forces fixed DPI, UI stays exact
- ✅ File Manager: Full Explorer with storage detection, file ops
- ✅ Taskbar fix: Freeform bounds exclude taskbar, no hidden content; fallback hides overlay when app open

---

## Testing:

1. **DPI Test:**
   - Developer Options -> Smallest width / DPI change to 200, 320, 400
   - Launcher UI should stay same
   - Check logs: `DpiHelper` logs applied DPI

2. **File Manager Test:**
   - Taskbar folder icon click -> File Manager opens
   - Navigate folders, open image/video
   - Create folder, rename, delete
   - Check SD Card/USB detection

3. **Taskbar Overlay Test:**
   - Settings -> Enable Taskbar Overlay
   - Open YouTube -> Check if controls hidden behind taskbar?
   - If freeform enabled -> Should open above taskbar
   - If not freeform -> Taskbar should auto-hide or be toggleable
