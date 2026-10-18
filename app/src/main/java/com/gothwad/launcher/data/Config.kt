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

/** Index in [UI_SCALES] that means "no scaling" (1.0x). */
const val UI_SCALE_NEUTRAL_INDEX = 2

/**
 * Version of the *meaning* of the stored config, bumped when a field changes semantics rather
 * than name. `0` is what a config written before this marker decodes to.
 *
 * Version 1: `uiScale` (see [normalizeConfig]).
 */
const val CONFIG_VERSION = 1

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
    /**
     * Automatic wallpaper rotation interval in minutes. `0` = off. The presets
     * ([WALLPAPERS]) rotate in order; a custom wallpaper is never auto-replaced.
     */
    val wallpaperAutoMinutes: Int = 0,
    /** `System.currentTimeMillis()` of the last auto-rotation. */
    val wallpaperAutoLastMs: Long = 0L,
    val accent: Int = 0,
    // ----- theme -----
    /** Index into `THEMES` (ui/Theme.kt). */
    val themeIndex: Int = 0,
    /** Derive the accent colour from the current wallpaper instead of [accent]. */
    val followWallpaperPalette: Boolean = false,
    // ----- header / clock -----
    /** 0 = date + time, 1 = time only, 2 = greeting + time. */
    val clockStyle: Int = 0,
    /** 0 = "d MMM • EEE", 1 = "dd/MM/yyyy", 2 = "EEE, d MMM", 3 = hidden. */
    val dateStyle: Int = 0,
    val showGreeting: Boolean = true,
    // ----- grid & icons -----
    /** 0 = banner grid, 1 = compact grid, 2 = large tiles, 3 = square icon grid. */
    val layoutMode: Int = 0,
    /** Columns for grid modes, 3..8. */
    val gridColumns: Int = 6,
    /** App card size in percent of the layout mode's base size (60..160). */
    val iconSizePct: Int = 100,
    val showTileLabels: Boolean = true,
    /** Tile label size in sp (10..16). */
    val labelSizeSp: Int = 12,
    /** Focus scale in percent (100..140; 108 = 1.08x). */
    val focusScalePct: Int = 108,
    /** Focus effect: 0 = scale, 1 = glow, 2 = outline, 3 = fill. */
    val focusEffect: Int = 0,
    /** App-open transition: 0 = none, 1 = fade, 2 = zoom, 3 = slide. */
    val openAnimation: Int = 1,
    /** UI animation speed: 0 = slow, 1 = normal, 2 = fast. */
    val animSpeed: Int = 1,
    val reduceMotion: Boolean = false,
    // ----- rows -----
    val showFavoritesRow: Boolean = false,
    /** Package names shown in the favourites row, in user order. */
    val favorites: List<String> = emptyList(),
    val showRecentRow: Boolean = false,
    // ----- system -----
    /** 0 = name A-Z, 1 = install date, 2 = recently updated, 3 = manual (order map only). */
    val appSort: Int = 0,
    /** Warm-screen overlay for night viewing. */
    val nightLight: Boolean = false,
    /** Warm overlay strength in percent (10..80). */
    val nightLightStrength: Int = 40,
    /** 0 = lite, 1 = balanced, 2 = full (see `docs/PERFORMANCE.md`). */
    val perfProfile: Int = 1,
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
    // ----- migration -----
    /**
     * Marker for [normalizeConfig]: `0` means "written by a build that predates the marker".
     * Never read for behaviour, only for migration - see `docs/CONFIG.md`.
     */
    val configVersion: Int = 0,
)

/**
 * Brings [config] in line with the current meaning of its fields and stamps it with
 * [CONFIG_VERSION].
 *
 * **Why this exists.** `uiScale` was stored, shown in Settings and read back since the earliest
 * builds, but nothing ever *applied* it - the UI looked identical at 0.75x and 1.3x. The release
 * that finally applies it (1.0.79) also moved the value's index base from `1..5` with
 * `3 = 1.0x` to `0..4` with `2 = 1.0x`. A config written before that therefore holds a number
 * the user never saw rendered, and interpreting it under the new meaning would silently resize
 * the whole launcher by +/-15% for every existing install the moment it updates.
 *
 * So: a config without the marker is reset to [UI_SCALE_NEUTRAL_INDEX] (1.0x, i.e. exactly what
 * those installs looked like until now). Anything the user picks *after* the update is stored
 * with the marker (`ConfigStore.update` stamps it) and is honoured from then on.
 *
 * Called on every read (`ConfigStore.flow`) and on every write (`ConfigStore.update`), so a
 * stored config that has not been rewritten yet is re-normalised each time it is read.
 */
fun normalizeConfig(config: LauncherConfig): LauncherConfig {
    val migrated = if (config.configVersion < CONFIG_VERSION) {
        config.copy(uiScale = UI_SCALE_NEUTRAL_INDEX)
    } else {
        config
    }
    return migrated.copy(configVersion = CONFIG_VERSION)
}

private val Context.dataStore by preferencesDataStore(name = "launcher_config")

class ConfigStore(private val context: Context) {
    private companion object {
        const val TAG = "ConfigStore"
    }

    private val key = stringPreferencesKey("config_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val flow: Flow<LauncherConfig> = context.dataStore.data.map { prefs ->
        val stored = prefs[key] ?: return@map LauncherConfig()
        normalizeConfig(decodeOrLog(stored))
    }

    suspend fun update(transform: (LauncherConfig) -> LauncherConfig) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { normalizeConfig(decodeOrLog(it)) } ?: LauncherConfig()
            // Normalise again after the transform so the write is always stamped with the
            // current [CONFIG_VERSION] - that stamp is what tells the next read "these values
            // already use today's semantics, do not migrate them".
            val updated = normalizeConfig(transform(current))
            prefs[key] = json.encodeToString(updated)
        }
    }

    private fun decodeOrLog(raw: String): LauncherConfig =
        runCatching { json.decodeFromString<LauncherConfig>(raw) }
            .onFailure { e ->
                // A failed decode means the launcher silently comes up with factory defaults,
                // which for a home screen looks like "all my settings and locks disappeared".
                // Log it loudly: the raw JSON is still in the DataStore file and is recoverable
                // from a bug report.
                android.util.Log.e(TAG, "Config decode failed - falling back to defaults", e)
            }
            .getOrDefault(LauncherConfig())
}
