# Gothwad Launcher (TV Launcher) — Issue Report

**Repo:** `GothwadTech/launcher` · **Reviewed commit:** `9f1ed39` ("refactor: replace Jetpack Compose with Android Views")
**Scope:** poora app module (Kotlin ~5.6k lines + 40 layouts + manifest + CI workflows + README).
**Method:** deep static review + automated cross-checks (neeche "Appendix" dekho).

> ⚠️ **Caveat:** is sandbox me JDK / Android SDK / network nahi hai, isliye main actually `./gradlew assembleDebug` **nahi** chala paya.
> Build-related items (Section A) code-reading + version-compatibility ke base pe hain — inko apne machine/CI pe ek baar confirm kar lena.
> Jo item "verify" likha hai, wo suspicious hai but runtime pe confirm karna zaroori hai.

---

## TL;DR — jitne issues mile

| Severity | Count | Nature |
|---|---|---|
| 🔴 P0 — Ship-blocker / device-breaker | **6** | build fail, boot ANR, 10-sec relaunch loop, device-lock bypass |
| 🟠 P1 — Security / Privacy | **7** | plaintext PIN + backup, extra sensitive permissions, no rate-limit |
| 🟡 P1 — Functional bugs (TV UX) | **9** | JioTV/Binge/Xstream apps block ho jate hain, fake BT battery, layout modes fake, wizard dead |
| 🔵 P2 — Perf / code quality / dead code | **13** | notifyItemRangeChanged on ListAdapter, nested RecyclerView grid, ~450 lines dead code |
| ⚪ P3 — Docs / repo hygiene / CI | **7** | README galat (Compose + Media3 likha hai), LICENSE missing, CI double-release |

**Top 6 jo sabse pehle fix karne chahiye:** `#1` Gradle↔AGP mismatch · `#2` BootReceiver ANR · `#3` Watchdog relaunch loop · `#4` Crash-relaunch loop · `#5` Device-Lock bypass (remote hotkey) · `#6` PIN plaintext + `allowBackup`.

---

## 🔴 A. P0 — Build / Ship blockers

### 1. Gradle wrapper (9.3.1) vs AGP (8.5.2) mismatch — build fail hone ka strong chance
- `gradle/wrapper/gradle-wrapper.properties:3` → `gradle-9.3.1-bin.zip`
- `build.gradle.kts:3` → `com.android.application version "8.5.2"` (Kotlin 2.0.20)

AGP 8.5.2 ka supported range Gradle **8.6–8.x** hai. Gradle 9.x ne woh deprecated APIs hata diye jinke upar AGP 8.5 depend karta hai → `Failed to apply plugin 'com.android.application'` / `UnsupportedMethodException` / configuration-cache errors aana expected hai.
CI bhi isi confusing state me hai: `.github/workflows/build_apk.yml` wrapper ko sirf **tab** regenerate karta hai jab `gradle-wrapper.jar` missing ho (wo committed hai) → CI bhi 9.3.1 chalayega.
**Fix:** ya wrapper ko `8.10.2` (ya 8.7) pe pin karo, ya AGP ko Gradle-9-compatible version tak upgrade karo — dono jagah (root + workflows) ek hi version rakho.
*(verify: apne machine pe ek `./gradlew tasks` chala ke confirm kar lo.)*

### 2. BootReceiver me 90-second loop `goAsync()` ke andar → har boot pe ANR
`app/src/main/java/com/gothwad/launcher/receiver/BootReceiver.kt:43-90`
```kotlin
val pendingResult = goAsync()                 // line 43
...  val timeout = if (totalRamGb <= 2.2) 90_000L else 45_000L   // 58-60
while (System.currentTimeMillis() - startTime < timeout) { ... } // 67
```
`BroadcastReceiver.goAsync()` me kaam ~10s (foreground) / ~60s (background) ke andar khatam karna hota hai. 45–90 second loop = **"ANR in BroadcastReceiver: Broadcast of Intent { act=android.intent.action.BOOT_COMPLETED }"**, aur 2GB STB (jinke liye ye code likha gaya hai) pe 90s wala path hi chalta hai.
**Fix:** `goAsync()` wale loop ko `JobIntentService`/`WorkManager`/foreground service me daalo, ya receiver ko sirf 5-10s tak limit karke baaki kaam Alarms/WorkManager se karao. `pendingResult.finish()` bhi 1-2 attempts ke baad call ho jaye.

### 3. Watchdog har 10 second me launcher ko forcibly front me laata hai
`app/src/main/java/com/gothwad/launcher/service/LauncherWatchdogService.kt:68-76`
```kotlin
val runningProcesses = runCatching { am.runningAppProcesses }.getOrNull()
val mainProcInfo = runningProcesses?.firstOrNull { it.processName == targetProcessName }
if (mainProcInfo == null) { relaunchLauncher(); return }   // <-- null == "dead" maan liya
```
`getRunningAppProcesses()` third-party apps ke liye literally **null / incomplete** ho sakta hai (OEM/restricted firmware pe bahut common — Jio/Airtel STB pe especially). Null aayi to ye "main process mar gaya" samajhta hai aur **har 10 second par HOME intent** bhejta hai → user kisi aur app me 10s se zyada reh hi nahi payega, TV practically unusable.
**Fix:** null ko "unknown" treat karo (return, kill nahi). Health check ke liye `ActivityManager.getMyMemoryState()` + apne hi process ka heartbeat (e.g. main process ek timestamp file/preference me likhe, watchdog uski staleness dekhe) — zyada reliable hai aur deprecated API pe depend nahi karta.

### 4. Crash-handler + watchdog ka infinite relaunch loop (koi backoff / safe-mode nahi)
`GothwadApplication.kt:60-115` (`setupCrashSelfHealing` + `scheduleEmergencyRelaunch`), `LauncherWatchdogService.kt:75/85`
Agar startup pe hi crash hota hai (config corruption, overlay permission, density adapter, koi bhi bug), to crash-handler 1.5s me phir launch karta hai → phir crash → phir launch… Ye launcher default **HOME** hai, to user ke paas bachne ka rasta hi nahi bachta (recents/settings bhi reach nahi, kyunki HOME ko ye app handle kar raha hai).
**Fix:** crash count SharedPreferences me rakho; 2-3 consecutive crashes ke baad self-healing band (safe mode) — sirf banner/dialog dikhao, ya ek baar "safe" minimal UI dikhao. Watchdog ko bhi crash-loop detect karke cooldown de do.

### 5. Device Lock bypass ho jata hai mapped remote hotkey se (security)
`service/LauncherAccessibilityService.kt:205-221`
```kotlin
if (keyCode == KeyEvent.KEYCODE_HOME) { ... launchHome(this) }        // 205
val mappedPkg = cachedButtonMap[keyCode]
if (!mappedPkg.isNullOrEmpty()) { ... Actions.launchApp(this, mappedPkg) } // 213-219
```
Jab Device Lock active hai (`GothwadApplication.hasUnlockedDeviceThisProcess == false`), launcher lock screen dikhata hai — lekin accessibility service ka mapped-key path **koi lock check hi nahi karta**. Boot pe default seeding (red=YouTube, green=Netflix, yellow=Prime, blue=Hotstar/Disney+, GUIDE=JioTV) automatically ho jati hai — matlab **Netflix button dabao aur lock bypass**. `KEYCODE_HOME` bhi lock ke dauraan home pe le jata hai (thoda kam issue, kyunki lock screen home pe hi hai).
**Fix:** a11y `onKeyEvent` me sabse upar ye guard lagao:
```kotlin
if (cachedConfig.deviceLock.enabled && cachedConfig.deviceLock.value.isNotEmpty() &&
    !GothwadApplication.hasUnlockedDeviceThisProcess) {
    if (keyCode != KeyEvent.KEYCODE_BACK) return true   // sab consume, kuch launch na ho
}
```
(Saath me: lock screen pe HOME/Back ke alawa sab swallow karna.)

### 6. Release build me R8 + kotlinx.serialization risk *(verify)*
`app/build.gradle.kts` (`isMinifyEnabled = true` + `android.enableR8.fullMode=true`) + `proguard-rules.pro`
Rules `@Serializable` ke generated `serializer()`/`$$serializer` ko cover karte hain, lekin official kotlinx-serialization rules ke kuch hisse (e.g. `Companion` ke keep + `kotlinx.serialization.**` internals) missing hain. Release APK me `Json.decodeFromString<LauncherConfig>()` fail kare to **poora config reset** ho jata hai (silently default config).
**Fix:** official rules add karo aur ek release-APK smoke test karo (config save → app kill → reopen → settings persist hone chahiye).

---

## 🟠 B. P1 — Security / Privacy

### 7. PIN / password **plaintext** me store hota hai + `allowBackup="true"`
- `data/Config.kt:32-37` → `LockCredential.value` (raw PIN/password) JSON ban ke Preferences DataStore me jaata hai (`files/datastore/launcher_config.preferences_pb`, unencrypted).
- `AndroidManifest.xml:55` → `android:allowBackup="true"` → Android backup (Google Drive / `adb backup`) me ye file chali jaati hai.
**Fix:** PIN ko salted hash (PBKDF2/Argon2, per-install random salt) me store karo; credential ke liye `EncryptedSharedPreferences`/`EncryptedFile` use karo; `allowBackup="false"` ya `dataExtractionRules`/`fullBackupContent` se datastore exclude karo. (Aur PIN compare constant-time karo — `MessageDigest.isEqual`.)

### 8. `usesCleartextTraffic="true"` — app me koi network call hi nahi hai
`AndroidManifest.xml:56`. Wallpaper/weather/etc. sara offline hai, phir bhi cleartext allow kar diya gaya → unnecessary attack surface + Play review me red flag.
**Fix:** attribute hata do (ya `networkSecurityConfig` se specific domain allow karo).

### 9. Zaroorat se zyada sensitive permissions
| Permission | Status |
|---|---|
| `RECORD_AUDIO` (manifest:38) | **Kahin use hi nahi** — voice search `RecognizerIntent` se hota hai (recognizer app apna mic use karta hai). Hata do. |
| `READ_MEDIA_IMAGES` (manifest:46) | Wallpaper `OpenDocument` (SAF) se pick hota hai → permission unnecessary. Hata do. |
| `SYSTEM_ALERT_WINDOW` (manifest:44) | Sirf fallback path ke liye; primary lock flows (`DeviceLockViewController`) ko iski zaroorat nahi. Agar fallback nahi chahiye to hata do. |
| `KILL_BACKGROUND_PROCESSES` | Har memory trim pe doosre apps kill (hostile UX, Android 8+ pe largely ineffective). |
| `PACKAGE_USAGE_STATS` | Pata nahi kahan use hota hai — `UsageTracker.hasPermission()` define hai par `getMostUsedPackageNames()` **kahin call nahi hota** → ye permission bhi effectively dead. |
| `REQUEST_DELETE_PACKAGES`, `QUERY_ALL_PACKAGES` | Launcher ke liye justified hain (Play policy me launcher exemption hai) — rakhna OK. |

### 10. BLUETOOTH_CONNECT kabhi request hi nahi hota (Android 12+)
`data/SmartServices.kt:80-86` permission *check* karta hai (crash avoid — accha), lekin runtime pe **kabhi request nahi karta** (project me `requestPermissions` ek jagah bhi nahi hai) → Android 12+ pe remote battery/bonded-device info permanently "unknown" hi rahegi.
**Fix:** `ActivityResultContracts.RequestPermission` se request karo (Settings → Permissions row already exist karta hai).

### 11. PIN ke against koi rate-limit / lockout nahi + koi recovery nahi
`PinEntryDialogFragment.kt`, `SystemLockOverlayView.kt` — unlimited attempts, koi delay/backoff nahi. 4-digit PIN ko TV pe remote se brute-force kiya ja sakta hai (10k combinations, no throttle).
Aur: **Device Lock enable hone ke baad "PIN bhool gaya" ka koi recovery path nahi** — TV practically brick (sirf ADB `pm clear` / factory reset se thik). Ye 2GB STB customers ke liye support nightmare hai.
**Fix:** 5 failed attempts ke baad 30s/1min/5min escalating lockout; recovery ke liye documented path (e.g. emergency code, ya Play-account/ADB reset jo settings me clearly likha ho).

### 12. Hidden-apps "reveal code" bhi plaintext + settings me openly shown
`SettingsBottomSheetFragment.kt:906-909` → `"Active: \"<code>\" (type in search)"`. Ye code config me plaintext hi store hota hai. TV shared device hai, to ye "security" nahi hai, sirf "hiding" hai — UI me isko at least masked rakho (`••••`) aur label clearly "not a security feature" karo.

### 13. Hidden apps other launchers/settings se launch ho jaate hain
Hidden-apps vault sirf apne launcher ke search/grid se chhupata hai (is launcher ka design decision hai) — par note karo: `lockedApps` (app lock) to globally enforce hota hai, `hidden` nahi. Agar "hidden" bhi security boundary hai, to usko app-lock ke saath enforce karna padega.

---

## 🟡 C. P1 — Functional / TV-UX bugs

### 14. **JioTV / Binge / Airtel Xstream jaise apps turant home pe kick ho jate hain** (biggest UX bug)
`service/LauncherAccessibilityService.kt:228-268`
```kotlin
"com.jio.media.ondemand"   // 236 — JioTV streaming app
"com.jio.jiotv"            // 237 — JioTV
"com.airtel.tv"            // 240 — Airtel Xstream app
"com.tatasky.binge"        // 247 — Tata Play Binge app
```
`isStockTvLauncher()` = `STOCK_LAUNCHERS.any { pkg.startsWith(it) }` (line 267). Aap in streaming apps ke package **prefix** ko "stock launcher" list me daal chuke ho. User launcher se JioTV/Binge kholta hai → `onAccessibilityEvent` (line 99) "OEM launcher ne screen steal kiya" maan ke `launchHome()` kar deta hai → app kabhi khulta hi nahi.
Isi tarah `com.airtel.tv` prefix `com.airtel.…*` sab match karega.
**Fix:** sirf actual launcher/boot-ad components ko exact package-name se match karo (prefix matching hatao), aur list ko filter karke "launcher role wale packages" (e.g. `pm.queryIntentActivities(HOME intent)`) se verify karo. JioTV/Binge/Xstream ko list se nikaalo.

### 15. Apps ka auto-categorization kaam hi nahi karta — sab "Streaming" me chale jaate hain
- `ui/tv/TvLauncherFragment.kt:279` → `val assigned = currentConfig.sections[app.pkg] ?: setOf(categories.first().id)`
  Naya install / unassigned app **hamesha pehli category (Streaming)** me chala jata hai.
- `data/AppRepository.kt:156` `autoCategory` compute hota hai (`streaming`/`games`/`music`/`apps`) lekin **kahin use hi nahi hota** (`grep autoCategory` → sirf compute).
- `data/Config.kt:53` `knownApps`, `Config.kt:83` `autoCategoryOnInstall` → dono **dead**: `knownApps` kahin likha/read nahi hota, aur Settings ka "Auto-sort on install" toggle (SettingsBottomSheetFragment.kt:597-603) sirf value save karta hai, uska koi asar nahi. `AppRepository.ALL_APPS_ID = "__all__"` ("All apps" section) bhi unused.

### 16. Layout modes: Grid / Carousel / **Dock** — Dock fake hai
`TvLauncherFragment.kt:86` & `:248` → `val isGrid = currentConfig.layout == LAYOUT_GRID`. Baaki dono (`LAYOUT_CAROUSEL`, `LAYOUT_DOCK`) exactly same code path chalate hain. Settings me 3 options dikhte hain (SettingsBottomSheetFragment.kt:456-457), lekin Dock chunne se kuch bhi alag nahi hota.

### 17. Setup wizard kabhi auto-open nahi hota
`TvLauncherFragment.kt:160` → `val firstRun = !config.setupDone && currentConfig.setupDone`
`LauncherConfig.setupDone` ka default **`true`** hai (`Config.kt:59`) aur koi code ise `false` nahi karta → ye condition practical me kabhi true nahi hoti. Matlab fresh install pe naya user wizard **dekhta hi nahi** (sirf Settings → "Setup wizard" se chalta hai). Default `false` karo + ek migration/shared-pref flag rakho "wizard shown once".

### 18. Config me dead settings (UI me toggle hai, kode me asar nahi)
| Field | Kahan set hota hai | Kahan padha jaata hai |
|---|---|---|
| `uiScale` (Config.kt:76, `UI_SCALES` :21) | kahin nahi | kahin nahi |
| `launchOnBoot` (Config.kt:82) | kahin nahi | kahin nahi — **BootReceiver bina check kiye launcher force-launch karta hai** aur OEM launcher suppress karta hai |
| `showHidden` (Config.kt:58) | kahin nahi | kahin nahi |
| `vpnApp`, `showVpnButton` (Config.kt:63-64) | kahin nahi | kahin nahi (status bar me VPN button hi nahi hai) |
| `knownApps` (Config.kt:53) | kahin nahi | kahin nahi |

Ya to inhe implement karo, ya `LauncherConfig` se hata do (warna har config save pe useless JSON chalta rahega).

### 19. Language / locale system poora dead hai, aur 90% UI hardcoded English
- `MainActivity.kt:403-420`: `persistLocale()`, `currentLocalePref()`, `applyLocale()` — teeno **kahin call nahi** hote, aur Settings me language picker bhi nahi hai (strings.xml me `item_language`, `lang_system` padi hui hain).
- `app/src/main/res/values/strings.xml` → **270 me se 224 strings unused**; meanwhile dialogs/settings me literally 60+ hardcoded English strings hain (e.g. SettingsBottomSheetFragment.kt me `"Security & Locks"`, `"Device Lock Enabled"`, QuickDashboard me `"Volume x / y"`, SearchDialog me `"No apps found matching…"`). Matlab i18n/internationalisation practically zero hai.

### 20. Bluetooth pill hamesha "connected" dikhata hai (fake data) + fake battery %
`data/SmartServices.kt:105,113,119`
```kotlin
BluetoothDeviceStatus(connected = true, name = "TV Remote", batteryLevel = -1)      // 113
else -> BluetoothDeviceStatus(connected = true, name = "TV Remote", batteryLevel = -1) // 119 (getOrDefault)
batteryLevel = if (bestBattery >= 0) bestBattery else 85,                            // 105 — "estimate"
```
Na koi remote juda ho, na bluetooth on — status bar pe "TV Remote" pill dikhata hai, aur fake **85%** battery. Status bar second-by-second jhooth dikhata hai; `StatusBarView.setBluetoothStatus()` `batteryLevel >= 0` par pill visible karta hai.
**Fix:** `connected` ko actually `adapter.isEnabled && bonded.isNotEmpty()` (ya bonded connected-device check) se bharo; battery unknown ho to "—" dikhao, 85 ka guess hatao.

### 21. Bade screens pe `AlertDialog`/`DialogFragment` theme + focus issues *(verify on device)*
`SettingsBottomSheetFragment.kt:919` ek `AlertDialog` ko `Theme.LiteTV_Dialog` (fullscreen, transparent) ke saath banata hai — TV pe ye aksar bina focus ke dikhta hai (D-pad navigation atak jata hai). Device pe ek baar confirm karo.

### 22. Sab dialog-fragment apne callbacks/data **field** me lete hain, `arguments` Bundle me nahi
Har jagah pattern ye hai:
```kotlin
fun newInstance(apps: List<AppEntry>, config: LauncherConfig, onLaunch: (AppEntry) -> Unit)
    = SearchDialogFragment().apply { this.allApps = apps; ... }
```
(SettingsBottomSheetFragment.kt:1240+, SearchDialogFragment.kt:180+, QuickDashboardDialogFragment.kt:135+, SetupWizardDialogFragment.kt:180+, BackgroundMediaDialogFragment.kt:110+, VoiceSearchDialogFragment.kt)
Do problem:
1. **Process death / low-memory pe fragment recreate** hone par ye fields khaali/default ho jaate hain → blank dialogs, `apps = emptyList()` (Settings "Manage locked apps" khali list dikhayega), ya crash.
2. Lambdas process me capture hote hain (leak-prone) aur `IllegalStateException: Fragment not attached` ka classic source hain.
**Fix:** `setArguments(Bundle)` + `FragmentResultListener`, ya dialogs ke bajaye direct views.

### 23. `binding` / `lifecycleScope` ka istemal view destroy ke baad bhi (NPE risk)
- `TvLauncherFragment.kt:188` `applyWallpaper()` → `lifecycleScope` (Fragment scope, view se lamba) + `binding.imgWallpaper…`; MainActivity ke config flow se bhi call hota hai → view destroy hone ke baad `_binding` null → **NPE**.
- `TvLauncherFragment.kt:236` `onRescanRequested()` → `viewLifecycleOwner.lifecycleScope`; ye MainActivity ke package-receiver se call hota hai, aur view destroyed ho to `viewLifecycleOwner` `IllegalStateException` deta hai.
- `SettingsBottomSheetFragment.kt:62` (photoPicker callback me `lifecycleScope` + `requireContext()`), `VoiceSearchDialogFragment.kt:104` (`binding.root.postDelayed` → 300ms baad binding null ho sakta hai), `SearchDialogFragment.kt:144` (`dialog.onDismissListener` me `binding` access) — teeno same class ka bug.
**Fix:** `viewLifecycleOwner.lifecycleScope` + `_binding?.let { … }` guard.

### 24. `getItem(pos)` click/key listeners me bina bounds-check (crash on list update)
`ui/view/AppCardAdapter.kt:89, 97, 111, 124, 134, 144, 153, 160, 175`
`bindingAdapterPosition != NO_POSITION` check hai, lekin uske turant baad `getItem(pos)` — agar `submitList()` ne list chhoti kar di (app uninstall / rescan / hidden flag) to `IndexOutOfBoundsException` → launcher crash (aur crash-loop ke saath combine hota hai, dekho #4).
**Fix:** `getItem(pos)` ko `runCatching`/`currentList.getOrNull(pos)?.let{}` se safe karo.

### 25. `AlarmManager.setExactAndAllowWhileIdle` — API 23 call on minSdk 21, aur API 31+ pe permission missing
`GothwadApplication.kt:104`. `minSdk = 21`, method API 23 ka hai → Lollipop devices pe `NoSuchMethodError` (jo `runCatching` me chhup jaata hai). Android 12+ pe `SCHEDULE_EXACT_ALARM` (ya `USE_EXACT_ALARM`) permission ke bina `SecurityException` → **crash-recovery relaunch ka poora mechanism silently dead**. `runCatching` ki wajah se koi log/alert bhi nahi.
**Fix:** version-guard + `alarmManager.canScheduleExactAlarms()` check; warna `set()`/plain `Handler`/`WorkManager` fallback.

### 26. `LOCKED_BOOT_COMPLETED` receive hi nahi ho sakta
`AndroidManifest.xml:128` receiver me `LOCKED_BOOT_COMPLETED` declared hai, par receiver/`application` me `android:directBootAware="true"` nahi → ye broadcast kabhi nahi aayega (device-encrypted storage bhi us waqt available nahi hoti). Boot-ad shield ka "sabse pehle" wala case chhoot jata hai.

### 27. Boot-guard ka `getRunningTasks(1)` fallback bekaar hai
`BootReceiver.kt:145-146`. App ki `PACKAGE_USAGE_STATS` grant ho to UsageStats path OK hai; warna `getRunningTasks` third-party app ko sirf **apne hi** tasks deta hai → top activity hamesha khud ka launcher hoga → stock launcher detection kabhi trigger nahi hogi (aur audio-silence loop 45-90s chalta rahega, dekho #2).

---

## 🔵 D. P2 — Performance / code quality

### 28. `ListAdapter` pe manually `notifyItemRangeChanged(0, itemCount)`
`ui/view/AppCardAdapter.kt:55`, `ui/tv/TvCategoryAdapter.kt:79` — ListAdapter ko diff-based updates ke liye banaya gaya hai; manual notify se (a) DiffUtil ki apni notifications se fight, (b) **har** card rebind (settings badalne pe poori grid ka flicker + scroll jank), aur (c) `RecyclerView` layout/scroll ke dauraan call hone pe `IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling`.
**Fix:** config changes ko `payload` diff me bhejo, ya `ListAdapter` ki jagah plain `RecyclerView.Adapter` + DiffUtil use karo.

### 29. Grid mode me nested RecyclerView + `wrap_content` + `setHasFixedSize(true)` (contradiction)
`ui/tv/TvCategoryAdapter.kt:120-140`, `res/layout/item_tv_category_row.xml` (inner RV `layout_height="wrap_content"`).
Har category row ke andar 6-span GridLayoutManager wrap_content par baithta hai → inner RV apne **saare** children ek saath measure/inflate karta hai, outer list ki recycling ka fayda chala jata hai. 60+ apps wale box pe cold-start memory/time dono badhta hai. Saath hi `setHasFixedSize(true)` wrap_content ke saath galat combination hai (child size badalne par stale measurement).
**Fix:** `layout_height` ko fixed dp do (card height config se), ya poori grid ko single outer GridLayoutManager + spanSizeLookup se render karo.

### 30. Notification list har event pe poori dobara decode hoti hai
`service/TvNotificationListenerService.kt:122-175` — `onNotificationPosted`/`onNotificationRemoved` har baar **sab** active notifications ka `toNativeBitmap()` chalate hain (icon bitmap re-render 32–96px), aur `appIcon` + `nativeBitmap` me same bitmap do baar rakha jaata hai. Notification storm (Wi-Fi/update/downloads) me visible jank + CPU spikes.
**Fix:** sirf naye key ke liye bitmap banao (key → bitmap cache), `MediaStyle` bade bitmaps ko skip karo, aur refresh ko ~300ms debounce karo.

### 31. App-lock ka logic **do jagah duplicate** hai
`MainActivity.kt:284-310` aur `ui/tv/TvLauncherFragment.kt:110-140` — dono me bilkul same `if (!skipLock && config.appLock.enabled && …) PinEntryDialogFragment…`. (Ye wahi drift hai jiska zikr repo ke `task-lock-system.md` me bhi hai — aur wahan ek `PcLauncherFragment.kt` ka reference hai jo project me exist hi nahi karta.)
**Fix:** ek `AppLockGate`/`UnlockCoordinator` banao aur dono jagah se call karo.

### 32. Status bar clock har second naya `SimpleDateFormat` banata hai
`MainActivity.kt:360-375` — loop har 1s pe 2 naye `SimpleDateFormat` + `Date` allocate karta hai. TV pe 24×7 chalne wala app hai — formatters ko `companion object` me cache karo (ya `Calendar`/`java.time` with formatter reuse).

### 33. `MainActivity.refreshAppsList()` background thread se UI-adjacent state likhta hai
`MainActivity.kt:95-102` → `lifecycleScope.launch(Dispatchers.IO) { allApps = …; ButtonMappingManager.seedDefaultMappings(…) }`. `allApps` bina synchronization ke IO thread se set hota hai aur `notifyFragmentRescan()` (86) fragment ko call karta hai jabki view destroy ho sakta hai (dekho #23). Saath hi ye har package install/remove pe **poora rescan + disk art cache sweep** chalata hai.

### 34. Memory-trim aggressive hai (doosre apps kill)
`data/AppLaunchTracker.kt:60-118` + `GothwadApplication.onTrimMemory/onLowMemory`: user ka background music/Spotify bhi kill ho sakta hai (jo "most recent" nahi hai), aur `killBackgroundProcesses` Android 8+ pe mostly no-op hai. Low-RAM box ke liye intent samajh me aata hai, par ye OEM ko blame dene layak UX issue banata hai — isko user-off-by-default karna behtar hoga.

### 35. Dead code / unused resources (bada cleanup)
- `res/layout/view_tv_launcher_grid.xml` — koi reference nahi.
- `ui/views/PlaceholderFragment.kt` + `res/layout/fragment_placeholder.xml` — Phase-1 leftover, navigate nahi hota.
- `ui/Icons.kt` — 61 `PATH_*` me se **32 unused** (`PATH_PIN`, `PATH_UNPIN`, `PATH_KEYBOARD`, `PATH_STANDBY`, …).
- `strings.xml` — **224/270 strings unused** (aerials, language, sections, save/load-config, weather, "menu_force_stop"…). Ye "features jo README me hain lekin code me nahi" ka direct saaman hai.
- `AppRepository.ALL_APPS_ID`, `AppEntry.autoCategory`, `Actions.AERIAL_PKG`, `UsageTracker.getMostUsedPackageNames()`, `TvControlHelper` ka kuch hissa, `Config` ke 5 dead fields (#18), locale helpers (#19).

### 36. Hover/touch listeners TV pe bekaar hain
`AppCardAdapter.kt:148-172` — `setOnHoverListener` + `ACTION_BUTTON_PRESS` (mouse/right-click) TV remote pe kabhi fire nahi hote; 40+ lines extra. (Haan, mouse wale boxes pe kaam karta hai.)

### 37. `baseline-prof.txt` hand-authored hai (real impact uncertain)
`app/src/main/baseline-prof.txt` — `HSPLcom/gothwad/launcher/**->**(**)**` jaisa wildcard profile ART pe valid hai, par hand-written humesha real hotspots miss karta hai. Macrobenchmark + `BaselineProfileRule` se generate karo, warna "Baseline Profile" ka claim kaafi kam value de raha hai.

### 38. Zero tests
`app/src/test` / `app/src/androidTest` — dono hi missing hain. Lock logic, config serialization, category assignment, aur `AppRepository` scan — chaaron pure/unit-testable hain; inke regression tests ke bina ye codebase har refactor pe toota hai (jaise abhi lock logic 2 jagah duplicate hai).

---

## ⚪ E. P3 — Docs / repo hygiene / CI

### 39. README code se match nahi karta
`README.md`:
- Badges "Native Android Views" ✔, **lekin "🛠️ Tech Stack" section me abhi bhi `Jetpack Compose (1.7+)`, `androidx.tv:tv-material`, `Material 3`, `Compose compiler` likha hai** — jabki `AGENTS.md` Compose ko strictly forbidden bolta hai aur `app/build.gradle.kts` me Compose ka ek dependency bhi nahi hai.
- "Dynamic Aerial & Video Wallpapers powered by **Media3 (ExoPlayer)**" — repo me `media3` ki dependency nahi hai, video wallpaper ka koi code nahi (sirf gradient presets + ek static image). `Actions.AERIAL_PKG` sirf ek constant hai.
- "Live Weather with automatic location detection" — poore code me weather ka koi implementation nahi (bas 3 `status_weather_*` colors bache hain).
Matlab README marketing sach se aage hai — isko honest karo (ya features implement karo).

### 40. LICENSE file missing but Apache-2.0 claim
`README.md:102` → "See the LICENSE file for details", par repo me `LICENSE` file **hi nahi hai**. Legal/distribution ke liye zaroori — Apache-2.0 ka full text add karo.

### 41. Root me AI-Studio planning artifacts pade hain
- `task.md`, `task-lock-system.md` — Google AI Studio ke liye likhe gaye prompt-files (inme `PcLauncherFragment.kt` jaise references hain jo exist nahi karte). Chahe `docs/` me daalo ya delete karo.
- `metadata.json` — AI Studio/IDX ka leftover (`"majorCapabilities": ["MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"]`, `requestFramePermissions`) — Android app se iska koi lena-dena nahi. Delete.

### 42. CI: do workflows ek saath release banate hain (conflict + duplicate builds)
`.github/workflows/build_apk.yml` (push on `main`) aur `release.yml` (push on `v*` tags) — dono `softprops/action-gh-release` se release create karte hain.
- `build_apk.yml` har push pe `v1.0.<run_number>` tag banata hai → wahi tag `release.yml` ko trigger karta hai → **do builds, same tag par release collision** (`action-gh-release` fail karega: tag already exists / release exists).
- `build_apk.yml` me keystore secret missing ho to **hard fail** (`exit 1`) — forks/PRs pe CI red.
**Fix:** ek hi workflow rakho (tag-based release), ya `build_apk` me `softprops` ke saath `overwrite`/`skipIfExists` + `permissions` tune karo; `release.yml` ko `workflow_run` se chalao.

### 43. Wrapper regeneration step CI me kaam nahi karega
`.github/workflows/*.yml` "Validate and Prepare Gradle Wrapper" → `gradle wrapper --gradle-version 8.10.2` (system `gradle` par depend karta hai, jo `ubuntu-latest` pe by default nahi hota) aur committed wrapper 9.3.1 se mismatch (#1 dekho). Ye step essentially dead code hai — hata do, wrapper ko repo me pin karo.

### 44. `.gitignore` me `!debug.keystore` negation
`.gitignore:22-23` → `*.keystore` ignore, par `!debug.keystore` exception. `app/build.gradle.kts:22` root se `debug.keystore` uthata hai — accidentally commit hone ka risk (CI me already `~/.android/debug.keystore` generate hota hai, isliye exception ki zaroorat nahi). Negation line hata do.

### 45. DensityAdapter system font-scale ko force karta hai
`ui/DensityAdapter.kt:82-100` `config.fontScale = 1.0f` karke user ki accessibility "Font size / Display size" setting ko override kar deta hai. TV pe ye kam-bade text wale ya kam-hindi (bade font) users ke liye accessibility regression hai. Kam se kam ek option to do.

---

## Suggested fix order (practical)

**Sprint 1 — device ko non-brickable banao**
1. `#1` Gradle/AGP pin (build green karo) → 2. `#2` BootReceiver ANR → 3. `#3` Watchdog null-handling → 4. `#4` crash-loop safe mode → 5. `#5` device-lock bypass guard → 6. `#24` `getItem` bounds-check (crash source).

**Sprint 2 — security & Play readiness**
7. `#7` PIN hashing + `allowBackup=false` → 8. `#9/#10` extra permissions hatao + BLUETOOTH_CONNECT request → 9. `#11` attempt throttling → 10. `#8` cleartext off → 11. `#13` hidden-apps enforcement decision.

**Sprint 3 — TV UX jo user turant notice karta hai**
12. `#14` stock-launcher list fix (JioTV/Binge/Xstream) → 13. `#15` autoCategory actually use karo → 14. `#20` Bluetooth fake data → 15. `#16` Dock mode implement/remove → 16. `#17` wizard default.

**Sprint 4 — cleanup**
17. `#18/#19/#35` dead code + config strip → 18. `#22/#23` fragment arguments + lifecycle safety → 19. `#28/#29/#30` perf → 20. `#39/#40/#41/#42` docs & CI.

---

## Appendix — jo maine programmatically verify kiya (ye sab **theek** hai)

Ye important hai, kyunki isse pata chalta hai ki issue "resource wiring" me nahi, logic/architecture me hai:

- ✅ **Saare `R.drawable / R.string / R.color / R.style / R.layout / R.id` references resolve hote hain** (Kotlin + XML dono) — koi missing resource nahi.
- ✅ **Saare ViewBinding fields** (`binding.xyz`) apne corresponding layout ke ids se match karte hain — 40 layouts, 0 mismatch.
- ✅ **`findViewById(R.id.*)`** ke saare ids existing hain.
- ✅ **Compose ka ek bhi leftover nahi** (`androidx.compose`, `@Composable`, `buildFeatures { compose }`, tv-material — zero hits) — AGENTS.md ka migration rule follow hua hai.
- ✅ `AppIcons.PATH_*` ka koi bhi undefined constant use nahi ho raha (ulta 32 unused hain).
- ✅ `accessibility_service_config.xml` sahi hai: `canRequestFilterKeyEvents="true"` + `flagRequestFilterKeyEvents` — HOME key interception technically enable hai.
- ✅ Manifest me launcher intent-filters (`HOME` + `LEANBACK_LAUNCHER` + `LAUNCHER`) aur dono services (a11y + notification listener) correctly declared hain, `exported` flags sahi hain.
- ✅ `MaterialComponents` parent styles (`Theme.MaterialComponents.DayNight.*`, `TextAppearance.MaterialComponents.*`, `ShapeAppearance.*`) sab `material:1.12.0` se aate hain — koi broken style parent nahi.
- ✅ `tv_banner.png` 320×180 hai (Android TV banner requirement) ✔.

---

## ✅ Fix status — P0 + P1-Security + P1-Functional (#1–27)

Branch: `arena/01a0c484-launcher` (uncommitted working tree). Ye section batata hai ki kaunsa item kaise fix hua, aur kya **device pe verify karna baaki** hai (is sandbox me JDK/Android SDK nahi hai, isliye koi build/compile nahi ho paya — sab changes static review + apne resource/symbol checker se verify kiye gaye).

### A. P0 — ship blockers
| # | Fix | Files |
|---|-----|-------|
| 1 | ✅ Wrapper ab `gradle-8.10.2-bin.zip` (AGP 8.5.2 ke saath compatible). CI me auto-regenerate hata kar **strict validation** lagayi: `gradle-wrapper.jar` missing ho ya `distributionUrl` galat ho to build fail (silent regeneration nahi). | `gradle/wrapper/gradle-wrapper.properties`, `.github/workflows/{build_apk,release}.yml` |
| 2 | ✅ Boot ANR khatam: `BootReceiver` turant return karta hai, 45/90s ka guard loop naye `BootShieldService` (foreground-less service, `START_NOT_STICKY`) me chala gaya. Adaptive window RAM ke hisaab se (≤2.2GB → 90s). | `receiver/BootReceiver.kt`, `service/BootShieldService.kt`, manifest |
| 3 | ✅ Watchdog ab `getRunningAppProcesses()` par verdict nahi deta. Main process apna liveness **file-backed heartbeat** (elapsedRealtime, 5s beat / 45s stale) publish karta hai; watchdog 15s pe check karta hai. `processesInErrorState == null` = "unknown", crash nahi. | `data/ProcessHeartbeat.kt`, `service/LauncherWatchdogService.kt`, `GothwadApplication.kt` |
| 4 | ✅ Crash-loop safe mode: `SelfHealGuard` (file-backed, cross-process) 10 min me 3 relaunch ke baad self-healing rok deta hai; 20s stable rehne par budget reset + toast. Dono jagah (crash handler + watchdog) gate laga hai. | `data/ProcessHeartbeat.kt` (SelfHealGuard), `GothwadApplication.kt`, `MainActivity.kt` |
| 5 | ✅ Device lock bypass band: `onKeyEvent` me sabse pehle guard — lock active hai aur is process me satisfy nahi hua to mapped hotkeys/listen-mode sab swallow (sirf BACK allowed, HOME home pe). | `service/LauncherAccessibilityService.kt` |
| 6 | ✅ R8 full-mode ke liye kotlinx.serialization keep rules (generated `$$serializer`, Companion, `@Serializable` models, Json entry points) add kiye — release build me config silently default pe reset nahi hoga. | `app/proguard-rules.pro` |

### B. P1 — Security / Privacy
| # | Fix | Files |
|---|-----|-------|
| 7 | ✅ PIN/password ab **PBKDF2WithHmacSHA1** (12k iters, 256-bit, per-credential 16-byte salt, Base64) se hash hote hain; comparison `MessageDigest.isEqual` (constant time). Purane plaintext credentials ka **silent migration** app start pe + legacy fallback (koi user lock-out nahi). `allowBackup="false"` + `fullBackupContent="false"`. | `data/LockSecurity.kt`, `data/Config.kt`, `MainActivity.kt`, Pin* / lock views |
| 8 | ✅ `usesCleartextTraffic` hata diya (koi cleartext endpoint nahi hai). | manifest |
| 9 | ✅ Permissions trim: `RECORD_AUDIO` (use hi nahi hota — voice search RecognizerIntent se chalta hai) aur `READ_MEDIA_IMAGES`/`READ_EXTERNAL_STORAGE` (SAF picker use hota hai) remove + explanatory comments. `SYSTEM_ALERT_WINDOW` **jaan-boojhkar** rakha (fail-safe overlay layer). Dead `UsageTracker.getMostUsedPackageNames()` aur dead `AERIAL_PKG` hata diye; `PACKAGE_USAGE_STATS` ka asli use (boot-shield foreground detection) comment me documented. | manifest, `data/SmartServices.kt`, `Actions.kt` |
| 10 | ✅ Android 12+ par `BLUETOOTH_CONNECT` ab **runtime request** hota hai (dashboard kholte hi), aur flow permission-gated hai. Fake `connected = true` / fake "85%" hata diya: `ACTION_ACL_CONNECTED/DISCONNECTED` se real state, unknown battery par "—". | `data/SmartServices.kt`, `MainActivity.kt`, `ui/view/StatusBarView.kt` |
| 11 | ✅ Lockout: 5 free attempts, uske baad 30s → 60s → … max 5 min (scope-wise: device/app/vault alag). Teeno entry points (dialog, device-lock controller, system overlay) me wired. | `data/LockSecurity.kt`, `PinEntryDialogFragment.kt`, `DeviceLockViewController.kt`, `SystemLockOverlayView.kt` |
| 12 | ✅ Reveal code ab kabhi screen pe plaintext me nahi dikhta — sirf "Active: ••••••". | `SettingsBottomSheetFragment.kt` |
| 13 | ✅ Vault ab sirf apne grid/search tak seemit nahi: hidden app kisi bhi raaste (a11y, settings, recents) se khule to **vault credential ka overlay** (SCOPE_VAULT) milta hai, warna Home; deliberate launch par session me whitelist. Voice search bhi hidden apps filter karta hai. | `LauncherAccessibilityService.kt`, `MainActivity.kt`, `SearchDialogFragment.kt` |

### C. P1 — Functional (TV UX)
| # | Fix | Files |
|---|-----|-------|
| 14 | ✅ `STOCK_LAUNCHERS` sirf **exact package match**; streaming apps (JioTV/Binge/Xstream) list se hataye — wo ab khul sakte hain. Context-aware `isStockTvLauncher(context, pkg)` + 60s HOME-handler cache. | `LauncherAccessibilityService.kt` |
| 15 | ✅ Auto-categorization asli me kaam karta hai: unassigned apps `AppEntry.autoCategory` (streaming/games/music/apps) me jaate hain, `__all__` category sab dikhati hai, `autoCategoryOnInstall` toggle respected, install-time pe `knownApps` diff se naye apps auto-assign. | `TvLauncherFragment.kt`, `MainActivity.kt` |
| 16 | ✅ "Dock" option hide (jo Carousel jaisa hi behave karta tha) — button `GONE` + comment; legacy config Dock ko carousel row ki tarah render karta hai. | `sheet_settings.xml`, `SettingsBottomSheetFragment.kt` |
| 17 | ✅ Setup wizard ab pehli install pe khud khulta hai: `setupDone` default `false` + `!setupDone && !wizardShownThisView` guard (pehle comparison galat thi, wizard kabhi nahi aata tha). | `Config.kt`, `TvLauncherFragment.kt` |
| 18 | ✅ Dead settings ab live hain: **UI Scale** (0.75x–1.3x → grid columns 8/7/6/5/4 + carousel tile size), **Show hidden apps**, **Launch at boot / block boot ads**, **VPN shortcut** (status-bar button jab VPN active ho) + VPN app picker, `knownApps` install detection. | `Config.kt`, `sheet_settings.xml`, `SettingsBottomSheetFragment.kt`, `TvLauncherFragment.kt`, `StatusBarView.kt`, `view_status_bar.xml`, `MainActivity.kt` |
| 19 | ◑ Dead locale plumbing (`persistLocale/currentLocalePref` + phantom prefs) hata diya aur **224 unused strings** cull kar diye (270 → 48). Baaki: UI text abhi bhi Kotlin/XML me hardcoded hai — pura i18n ke liye strings extract karke translate karna padega (naya kaam). | `MainActivity.kt`, `values/strings.xml` |
| 20 | ✅ Bluetooth status real (upar #10) — Bluetooth off ho to pill dikhta hi nahi, battery unknown par "—". | `SmartServices.kt`, `StatusBarView.kt` |
| 21 | ✅ Reveal-code `AlertDialog` ko `setOnShowListener` + `requestFocus()` + explicit window layout diya — TV D-pad focus atakna band. (Device pe ek confirm karna baaki.) | `SettingsBottomSheetFragment.kt` |
| 22 | ◑ Recreation-safe: har dialog jise host callbacks chahiye (Search/VoiceSearch/QuickDashboard/BackgroundMedia/PinEntry/PinSetup/Settings) ab process-death ke baad **dismiss** hota hai (blank/dead UI nahi); SearchDialog ka `executePendingTransactions()` hack hata kar `onCancelled` callback; wizard apna `setupDone` khud persist karta hai. Baaki (optional): poora `setArguments`/FragmentResult refactor. | dialogs |
| 23 | ✅ Lifecycle safety: `viewLifecycleOwner.lifecycleScope` + `_binding`/`isAdded` guards (`applyWallpaper`, `onRescanRequested`, photo picker result, voice-search `postDelayed`, SearchDialog dismiss). | `TvLauncherFragment.kt`, `SettingsBottomSheetFragment.kt`, `VoiceSearchDialogFragment.kt`, `SearchDialogFragment.kt` |
| 24 | ✅ Har `getItem(pos)` ki jagah `currentList.getOrNull(...)` (AppCardAdapter/TvCategoryAdapter/SearchAppAdapter/NotificationAdapter) — uninstall/rescan ke beech click par `IndexOutOfBoundsException` nahi. | adapters |
| 25 | ✅ `setExactAndAllowWhileIdle` version-guarded (minSdk 21) + `canScheduleExactAlarms()` (API 31+), warna `set()` aur last-resort Handler fallback — crash recovery silently dead nahi hota. | `GothwadApplication.kt` |
| 26 | ✅ `LOCKED_BOOT_COMPLETED` ab sach me receive hoga: receiver + watchdog + boot-shield services par `android:directBootAware="true"`, aur pre-unlock storage access `runCatching` se safe (defaults par fallback). | manifest, `BootReceiver.kt` |
| 27 | ✅ Boot-guard ka bekaar `getRunningTasks(1)` fallback hata diya — ab UsageStats (special access ho to) → warna accessibility service ka last-known foreground package; unknown = `null` (stock launcher maan kar home nahi kheenchta). | `BootShieldService.kt`, `LauncherAccessibilityService.kt` |

### Verification jo maine chalaayi
- **Static checks (pass):** saare XML well-formed; saare `R.*` references; saare ViewBinding fields vs layout ids; brace/paren balance; naye symbols (`LockSecurity`, `ProcessHeartbeat`, `SelfHealGuard`, `BootShieldService`, `UI_SCALES`, `Actions.openVpnSettings`, `AppIcons.PATH_*`) existing.
- **Nahi ho paya:** compile / lint / R8 / device test — sandbox me JDK, Android SDK aur network nahi hai. Isliye #21 (dialog focus) aur boot flows device pe ek baar dekh lena; koi bhi build error aaye to batao, main turant fix kar dunga.

---

## ✅ Fix status — P2 (#28–38) + P3 (#39–45)

Wo saare bache hue issues bhi fix kar diye. Neeche item-wise:

### D. P2 — Performance / code quality
| # | Fix | Files |
|---|-----|-------|
| 28 | ✅ Manual `notifyItemRangeChanged(0, itemCount)` ki jagah ab **payload-based rebind**: sirf style change (accent/radius/labels) par `PAYLOAD_CONFIG` jaata hai aur card geometry hi re-apply hoti hai (bitmap/banner dobara load nahi). Structural change par normal notify, aur dono `runCatching` ke andar — "Cannot call this method while RecyclerView is computing a layout" crash khatam. | `ui/view/AppCardAdapter.kt`, `ui/tv/TvCategoryAdapter.kt` |
| 29 | ✅ Nested row ka `wrap_content` + `setHasFixedSize(true)` contradiction fix: inner RecyclerView ko config se **explicit height** (card height + 8dp focus headroom) milti hai, tab `setHasFixedSize(true)` lagta hai — recycling sach me chalti hai. | `ui/tv/TvCategoryAdapter.kt` |
| 30 | ✅ Notification storm fix: icon ab **package-wise cache** (LinkedHashMap, max 48) se aata hai, refresh ~300ms **debounced** hai, aur duplicate `appIcon` field hata diya (wahi bitmap do baar store hota tha). | `service/TvNotificationListenerService.kt`, `ui/dialogs/NotificationAdapter.kt` |
| 31 | ✅ App-lock ka duplicate logic ek jagah: naya **`AppLockGate`** (device lock → app lock → vault credential, scope-wise) — MainActivity aur TvLauncherFragment dono ab isi ko call karte hain. | `ui/AppLockGate.kt`, `MainActivity.kt`, `ui/tv/TvLauncherFragment.kt` |
| 32 | ✅ Status-bar clock ab cached formatters use karta hai (`SimpleDateFormat` sirf pattern badalne par), per-second allocation khatam. | `MainActivity.kt` |
| 33 | ✅ `allApps` `@Volatile` + rescan ab **600ms debounced** hai aur fragment callback main thread par jaata hai (pehle IO thread se UI callback + har broadcast par poora rescan). | `MainActivity.kt` |
| 34 | ✅ Aggressive memory trim **opt-in** ho gaya (default OFF, Settings → Apps → "Aggressive Memory Trim"): ab launcher apne aap user ka background music/stream kill nahi karega. | `data/AppLaunchTracker.kt`, `Config.kt`, settings UI |
| 35 | ✅ Dead-code cleanup: `view_tv_launcher_grid.xml`, `PlaceholderFragment.kt`, `fragment_placeholder.xml` delete; `Icons.kt` ke **31 unused `PATH_*`** hataye (61 → 30); **40 unused colors** aur pehle hi 224 unused strings remove; dead `UsageTracker.getMostUsedPackageNames()`, `Actions.AERIAL_PKG`, locale plumbing hataye; `ALL_APPS_ID`/`autoCategory`/`knownApps`/`uiScale`/`vpnApp`/`showVpnButton`/`showHidden`/`launchOnBoot` sab ab **actually use** ho rahe hain. | kai files |
| 36 | ✅ Hover/mouse listeners ab **pointer wale devices** par hi attach hote hain (`FEATURE_TOUCHSCREEN` / `type.pc`), remote-only TV par 40+ lines ka per-card overhead khatam. | `ui/view/AppCardAdapter.kt` |
| 37 | ✅ Honesty + raasta: profile ko "starter, hand-maintained" document kiya aur **`docs/baseline-profile.md`** me Macrobenchmark module + generator ka poora recipe diya (is sandbox me SDK nahi hai, isliye generate nahi kar saka). | `README.md`, `docs/baseline-profile.md` |
| 38 | ✅ **Unit tests add kiye** (`app/src/test/`): `CategoryAssignerTest` (9 tests — auto-category, hidden vault, `__all__`, order), `LockSecurityTest` (hashing/salt/legacy fallback), `ConfigSerializationTest` (JSON round-trip + unknown keys). Grid rules ko testable banane ke liye pure `data/CategoryAssigner.kt` extract kiya; CI me `:app:testDebugUnitTest` step bhi add. | `data/CategoryAssigner.kt`, `app/src/test/**`, CI |

### E. P3 — Docs / repo hygiene / CI
| # | Fix | Files |
|---|-----|-------|
| 39 | ✅ README ab code se match karta hai: Tech Stack section se **Compose / tv-material / Media3 / weather** hataye (Views + ViewBinding, Kotlin 2.0, DataStore, kotlinx-serialization, PBKDF2, R8 full mode, tests), aur Features section sach ke hisaab se likha (wallpaper presets + SAF, boot shield, hashed locks, vault enforcement, memory-trim opt-in). | `README.md` |
| 40 | ✅ **`LICENSE`** file add ki — Apache-2.0 ka poora text (README:102 ka reference ab sach hai). | `LICENSE` |
| 41 | ✅ AI-Studio leftovers: `task.md` + `task-lock-system.md` → **`docs/`** me move, `metadata.json` delete. | `docs/`, `metadata.json` |
| 42 | ✅ CI conflict khatam: **`build_apk.yml` sirf artifacts** banata hai (koi tag/release nahi), **`release.yml` hi release publish** karta hai (tag-based). Signing secrets missing ho to build **fail nahi hota** — unsigned artifacts + notice. Dono me unit tests step. | `.github/workflows/*` |
| 43 | ✅ Wrapper regenerate step pehle hi hata chuka hoon (Sprint 1): ab CI wrapper ko **pin 8.10.2** ke liye validate karta hai aur mismatch par fail karta hai. | `.github/workflows/*` |
| 44 | ✅ `.gitignore` se `!debug.keystore` negation hata di — ab koi bhi keystore accidentally commit nahi hoga (CI apna debug key khud banata hai). | `.gitignore` |
| 45 | ✅ `DensityAdapter` ab **system font scale respect** karta hai (0.85x–1.3x clamp, `scaledDensity = density × fontScale`); Settings → Display me toggle "Use System Font Size" (default ON). DPI normalisation pehle jaisa hi. | `ui/DensityAdapter.kt`, `Config.kt`, settings UI |

### Ek asli build-breaker bhi pakda gaya
`@color/switch_thumb_tint` / `@color/switch_track_tint` "missing" wala lead **false positive** tha: dono `res/color/*.xml` me ColorStateList ke roop me defined hain (mismatch sirf mere checker me tha — `res/color/` scan nahi ho raha tha). Checker theek kar diya, dono files HEAD par wapas revert kar di.

### Verification (is turn ka)
- ✅ XML well-formed (saare layouts/values/color/xml), saare `R.*` refs, saare `@color/@drawable/@string/@style` refs, saare ViewBinding fields vs layout ids, suspend-context check (`store.update` har jagah coroutine me), brace/paren balance, aur **function-level diff** (HEAD vs working tree) — koi bhi function accidentally delete nahi hua (jahan hua tha, wo restore kar diya).
- ⚠️ Compile/lint/R8/unit-tests **nahi chala paye** — sandbox me JDK, Android SDK aur network nahi hai. `./gradlew :app:testDebugUnitTest` device/CI par chalana zaroori hai.
