package com.gothwad.launcher.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Config JSON round-trip (issue #38): a failed decode means the launcher boots with
 * default settings, so this is worth locking down.
 */
class ConfigSerializationTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `config survives a json round trip`() {
        val config = LauncherConfig(
            order = mapOf("grid" to listOf("com.example.app", "com.example.other")),
            lockedApps = setOf("com.example.app"),
            hidden = setOf("com.example.secret"),
            launchOnBoot = false,
            showHidden = true,
            aggressiveMemoryTrim = true,
            respectSystemFontScale = false,
            uiScale = 4,
        )

        val encoded = json.encodeToString(LauncherConfig.serializer(), config)
        val decoded = json.decodeFromString(LauncherConfig.serializer(), encoded)

        assertEquals(config.order, decoded.order)
        assertEquals(config.lockedApps, decoded.lockedApps)
        assertEquals(config.hidden, decoded.hidden)
        assertTrue(decoded.showHidden)
        assertTrue(decoded.aggressiveMemoryTrim)
        assertTrue(!decoded.respectSystemFontScale)
        assertEquals(4, decoded.uiScale)
    }

    /**
     * Configs written by older versions carry keys that no longer exist (the removed category
     * grid: `categories`, `sections`, `knownApps`, `autoCategoryOnInstall`, plus `iconScale`,
     * `layout`, `showCategoryNames`, `showAppLabels`, `vpnApp`, `showVpnButton`). They must
     * decode instead of resetting the user's whole config to defaults.
     */
    @Test
    fun `keys removed in a newer version are ignored, not fatal`() {
        val decoded = json.decodeFromString(
            LauncherConfig.serializer(),
            """{
                "categories":[{"id":"games","name":"Games"}],
                "sections":{"com.example.app":["games"]},
                "knownApps":["com.example.app"],
                "autoCategoryOnInstall":true,
                "iconScale":3,
                "layout":0,
                "showCategoryNames":true,
                "showAppLabels":true,
                "vpnApp":"com.example.vpn",
                "showVpnButton":true,
                "h24":true,
                "wallpaper":4
            }""",
        )

        assertTrue(decoded.h24)
        assertEquals(4, decoded.wallpaper)
    }

    /** The default UI scale has to be the neutral 1.0x step. */
    @Test
    fun `default ui scale is neutral`() {
        assertEquals(1.0f, UI_SCALES[LauncherConfig().uiScale], 0.0001f)
    }

    @Test
    fun `unknown keys from a newer version do not break the config`() {
        val decoded = json.decodeFromString(
            LauncherConfig.serializer(),
            """{"setupDone":true,"someFutureFlag":123,"nested":{"a":1}}""",
        )

        assertTrue(decoded.setupDone)
    }

    @Test
    fun `credential hashing fields round-trip`() {
        val credential = LockSecurity.createCredential("9876", LockCredentialType.NUMERIC, 4)
        val config = LauncherConfig(appLock = credential)

        val decoded = json.decodeFromString(
            LauncherConfig.serializer(),
            json.encodeToString(LauncherConfig.serializer(), config),
        )

        assertEquals(credential.credentialHash, decoded.appLock.credentialHash)
        assertEquals(credential.credentialSalt, decoded.appLock.credentialSalt)
        assertTrue(LockSecurity.verify(decoded.appLock, "9876"))
        assertTrue(decoded.appLock.value.isEmpty())
    }
}
