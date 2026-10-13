package com.gothwad.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Spacing steps between cards — 5 levels (in dp). */
val GAP_SIZES: List<Float> = listOf(4f, 10f, 16f, 24f, 32f)
/** Icon corner roundness steps (index stored in config.cornerRadius, in dp). */
val CORNER_RADII: List<Float> = listOf(0f, 4f, 10f, 18f, 28f)
/** Manual whole-UI scale steps (config.uiScale indexes this, 0..4). <1 = more compact. */
val UI_SCALES: List<Float> = listOf(0.75f, 0.9f, 1.0f, 1.15f, 1.3f)

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
    /**
     * Explicit package order for the home grid (package list, flattened by
     * `TvLauncherFragment.rebuildAppList`). Everything not listed follows, sorted by
     * first-install time.
     *
     * NOTE: this used to be keyed per category (`Map<categoryId, List<pkg>>`). The category
     * rows were removed from the home screen, but the map shape is kept so configs written by
     * older versions still decode (`Json { ignoreUnknownKeys = true }` drops the rest).
     */
    val order: Map<String, List<String>> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    /** Index into `WALLPAPERS` (ui/Theme.kt); see `DEFAULT_WALLPAPER_INDEX`. */
    val wallpaper: Int = 1,
    val useCustomWallpaper: Boolean = false,
    val accent: Int = 0,
    val h24: Boolean = false,
    val showHidden: Boolean = false,
    val setupDone: Boolean = false,
    // ----- status bar -----
    val showStatusBar: Boolean = true,
    /** wrap the status-bar icons in the same glass panel as dock mode */
    val statusBarGlass: Boolean = true,
    /** Match status bar header cards corner curve to app icon curve */
    val headerMatchIconCorners: Boolean = true,
    // ----- layout & visuals -----
    /** spacing step 0..4 (see GAP_SIZES) */
    val spacing: Int = 2,
    /** icon/panel corner roundness step 0..4 (see CORNER_RADII) */
    val cornerRadius: Int = 2,
    /**
     * Whole-UI scale step 0..4 (see UI_SCALES, default 2 = 1.0x). Applied to the density by
     * `DensityAdapter` - see `MainStatusBarController.applyDensityPolicy`.
     */
    val uiScale: Int = 2,
    /** 0 = full screen, 1 = top edge, 2 = bottom edge, 3 = both edges, 4 = none */
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
    /** app lock: list of package names requiring a PIN to open */
    val lockedApps: Set<String> = emptySet(),
    val appLock: LockCredential = LockCredential(),
    val hiddenAppsLock: LockCredential = LockCredential(),
    /**
     * LEGACY plain-text hidden-apps reveal code. Kept only so an existing install can be
     * migrated to [hiddenAppsRevealCodeHash] on first launch (see
     * `LockSecurity.migrateLegacyCredentials`); it is cleared by that migration and must
     * never be written again.
     */
    val hiddenAppsRevealCode: String = "",
    /** Base64 PBKDF2 hash of the search-bar reveal code. */
    val hiddenAppsRevealCodeHash: String = "",
    /** Base64 random salt for [hiddenAppsRevealCodeHash]. */
    val hiddenAppsRevealCodeSalt: String = "",
    // ----- Remote Button Mapping (Phase 5) -----
    val buttonMap: Map<Int, String> = emptyMap(),
    val buttonMapDefaultsApplied: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "launcher_config")

class ConfigStore(private val context: Context) {
    private companion object {
        const val TAG = "ConfigStore"
    }

    private val key = stringPreferencesKey("config_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val flow: Flow<LauncherConfig> = context.dataStore.data.map { prefs ->
        val stored = prefs[key] ?: return@map LauncherConfig()
        runCatching { json.decodeFromString<LauncherConfig>(stored) }
            .onFailure { e ->
                // A failed decode means the launcher silently comes up with factory defaults,
                // which for a home screen looks like "all my settings and locks disappeared".
                // Log it loudly: the raw JSON is still in the DataStore file and is recoverable
                // from a bug report.
                android.util.Log.e(TAG, "Config decode failed - falling back to defaults", e)
            }
            .getOrDefault(LauncherConfig())
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
