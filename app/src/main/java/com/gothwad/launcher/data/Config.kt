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
    /**
     * LEGACY - only kept so older configs can be migrated. Never set this on new
     * credentials: the secret is stored as [credentialHash] + [credentialSalt]
     * (see [LockSecurity]). Cleared by `LockSecurity.migrateLegacyCredentials()`.
     */
    val value: String = "",
    /** Base64 PBKDF2 hash of the PIN/password. */
    val credentialHash: String = "",
    /** Base64 random salt used for [credentialHash]. */
    val credentialSalt: String = "",
    val pinLength: Int = 4,       // only meaningful when type == NUMERIC (4 or 6)
) {
    /** True when a (hashed) secret has been configured for this lock. */
    val configured: Boolean get() = credentialHash.isNotEmpty() && credentialSalt.isNotEmpty()

    /**
     * True when this lock has *any* secret - hashed (normal) or the legacy plain-text
     * value that has not been migrated yet. Guard code uses this so a lock is never
     * treated as "not configured" during the migration window.
     */
    val ready: Boolean get() = configured || value.isNotEmpty()
}

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
    val h24: Boolean = false,
    val showHidden: Boolean = false,
    val setupDone: Boolean = false,
    // ----- status bar -----
    val showStatusBar: Boolean = true,
    /** package of the app the VPN icon opens; empty = system VPN settings */
    val vpnApp: String = "",
    val showVpnButton: Boolean = true,
    /** wrap the status-bar icons in the same glass panel as dock mode */
    val statusBarGlass: Boolean = true,
    /** Match status bar header cards corner curve to app icon curve */
    val headerMatchIconCorners: Boolean = true,
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
    // ----- behavior -----
    /** Boot-ad shield / force-launch home at boot. Discoverable in Settings > Apps. */
    val launchOnBoot: Boolean = true,
    /**
     * Opt-in: kill *other* apps when the system reports memory pressure. Off by default -
     * killing the user's background music/streaming to save a few MB is not a launcher's
     * call to make (see issue #34).
     */
    val aggressiveMemoryTrim: Boolean = false,
    /** Honour the system font-size (accessibility) setting instead of forcing 1.0x. */
    val respectSystemFontScale: Boolean = true,
    val autoCategoryOnInstall: Boolean = true,
    /** app lock: list of package names requiring a PIN to open */
    val lockedApps: Set<String> = emptySet(),
    val appLock: LockCredential = LockCredential(),
    val hiddenAppsLock: LockCredential = LockCredential(),
    val hiddenAppsRevealCode: String = "",
    // ----- Remote Button Mapping (Phase 5) -----
    val buttonMap: Map<Int, String> = emptyMap(),
    val buttonMapDefaultsApplied: Boolean = false,
)

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
        } ?: LauncherConfig()
    }

    suspend fun update(transform: (LauncherConfig) -> LauncherConfig) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<LauncherConfig>(it) }.getOrNull()
            } ?: LauncherConfig()
            val updated = transform(current)
            prefs[key] = json.encodeToString(updated)
        }
    }
}
