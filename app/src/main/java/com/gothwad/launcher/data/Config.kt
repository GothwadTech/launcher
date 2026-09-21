package com.gothwad.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gothwad.launcher.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Spacing steps between cards — 5 levels (in dp). */
val GAP_SIZES: List<Float> = listOf(4f, 10f, 16f, 24f, 32f)
/** Card icon size steps — 5 levels (in dp). */
val ICON_SIZES: List<Float> = listOf(120f, 150f, 190f, 230f, 270f)
/** Icon corner roundness steps (index stored in config.cornerRadius, in dp). */
val CORNER_RADII: List<Float> = listOf(0f, 4f, 10f, 18f, 28f)
/** Manual whole-UI scale steps (config.uiScale 1..5). <1 = more compact. */
val UI_SCALES: List<Float> = listOf(0.75f, 0.9f, 1.0f, 1.15f, 1.3f)

@Serializable
data class CategoryCfg(
    val id: String,
    val name: String,
)

enum class LockCredentialType { NUMERIC, ALPHANUMERIC }

@Serializable
data class LockCredential(
    val enabled: Boolean = false,
    val type: LockCredentialType = LockCredentialType.NUMERIC,
    val value: String = "",       // the PIN digits or the alphanumeric password
    val pinLength: Int = 4,       // only meaningful when type == NUMERIC (4 or 6)
)

@Serializable
data class LauncherConfig(
    val categories: List<CategoryCfg> = listOf(
        CategoryCfg("streaming", "Streaming"),
        CategoryCfg("games", "Games"),
        CategoryCfg("music", "Music"),
        CategoryCfg("apps", "Apps"),
    ),
    /** package -> section ids the user assigned (an app may be in several) */
    val sections: Map<String, Set<String>> = emptyMap(),
    /** category id -> explicit package order */
    val order: Map<String, List<String>> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    /** packages seen on the last scan — new installs are auto-added to the first section */
    val knownApps: Set<String> = emptySet(),
    val wallpaper: Int = 1, // Aurora
    val useCustomWallpaper: Boolean = false,
    val accent: Int = 0,
    val h24: Boolean = true,
    val showHidden: Boolean = false,
    val setupDone: Boolean = true,
    // ----- status bar -----
    val showStatusBar: Boolean = true,
    /** package of the app the VPN icon opens; empty = system VPN settings */
    val vpnApp: String = "",
    val showVpnButton: Boolean = true,
    /** wrap the status-bar icons in the same glass panel as dock mode */
    val statusBarGlass: Boolean = true,
    // ----- layout & visuals -----
    val layout: Int = LAYOUT_GRID,
    /** spacing step 0..4 (see GAP_SIZES) */
    val spacing: Int = 2,
    /** icon size step 0..4 (see ICON_SIZES) */
    val iconScale: Int = 2,
    /** icon/panel corner roundness step 0..4 (see CORNER_RADII) */
    val cornerRadius: Int = 2,
    /** whole-UI scale step 1..5 (see UI_SCALES, default 3 = 1.0x) */
    val uiScale: Int = 3,
    val showCategoryNames: Boolean = true,
    val showAppLabels: Boolean = true,
    /** 0 = full, 1 = top only, 2 = bottom only, 3 = both, 4 = none */
    val scrimMode: Int = 0,
    // ----- display density (DPI) -----
    val useCustomDpi: Boolean = false,
    val customDpi: Int = 0,
    // ----- behavior -----
    val launchOnBoot: Boolean = false,
    val autoCategoryOnInstall: Boolean = true,
    /** app lock: list of package names requiring a PIN to open */
    val lockedApps: Set<String> = emptySet(),
    val deviceLock: LockCredential = LockCredential(),
    val appLock: LockCredential = LockCredential(),
    val hiddenAppsLock: LockCredential = LockCredential(),
    val hiddenAppsRevealCode: String = "",
    /** launcher mode: 0 = TV, 1 = PC Desktop */
    val launcherMode: Int = MODE_TV,
    // ----- PC Desktop settings -----
    val pcWallpaper: Int = 0,
    val pcUseCustomWallpaper: Boolean = false,
    val pcUiScale: Float = 0.85f,
    val pcIconSize: Int = 44,
    val pcShowLabels: Boolean = true,
    val pcDesktopOrder: List<String> = emptyList(),
    val pcCustomLabels: Map<String, String> = emptyMap(),
    val pcCustomIcons: Map<String, String> = emptyMap(),
    val pcPinnedApps: List<String> = emptyList(),
    val pcTaskbarHeight: Int = 44,
    val pcTaskbarCenter: Boolean = false,
    val pcOverlayTaskbarEnabled: Boolean = true,
    val pcGridSpacing: Int = 12,
    val pcSortOrder: Int = 0,
    // ----- Remote Button Mapping (Phase 5) -----
    val buttonMap: Map<Int, String> = emptyMap(),
    val buttonMapDefaultsApplied: Boolean = false,
)

const val MODE_TV = 0
const val MODE_PC = 1

const val LAYOUT_GRID = 0
const val LAYOUT_CAROUSEL = 1
const val LAYOUT_DOCK = 2

private val Context.dataStore by preferencesDataStore(name = "launcher_config")

class ConfigStore(private val context: Context) {
    private val key = stringPreferencesKey("config_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val flow: Flow<LauncherConfig> = context.dataStore.data.map { prefs ->
        prefs[key]?.let {
            runCatching { json.decodeFromString<LauncherConfig>(it) }.getOrNull()
        } ?: LauncherConfig(
            // On fresh install on a touch-first non-TV device, default to PC mode
            launcherMode = if (isProbablyTouchDevice(context)) MODE_PC else MODE_TV
        )
    }

    suspend fun update(transform: (LauncherConfig) -> LauncherConfig) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<LauncherConfig>(it) }.getOrNull()
            } ?: LauncherConfig(
                launcherMode = if (isProbablyTouchDevice(context)) MODE_PC else MODE_TV
            )
            val updated = transform(current)
            prefs[key] = json.encodeToString(updated)
        }
    }

    private fun isProbablyTouchDevice(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        val isTv = (uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        val hasTouch = context.packageManager.hasSystemFeature("android.hardware.touchscreen")
        val hasLeanback = context.packageManager.hasSystemFeature("android.software.leanback")
        return !isTv && !hasLeanback && hasTouch
    }
}
