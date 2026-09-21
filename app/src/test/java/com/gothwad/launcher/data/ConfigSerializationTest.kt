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
            sections = mapOf("com.example.app" to setOf("games")),
            lockedApps = setOf("com.example.app"),
            hidden = setOf("com.example.secret"),
            launchOnBoot = false,
            showHidden = true,
            aggressiveMemoryTrim = true,
            respectSystemFontScale = false,
            uiScale = 4,
            knownApps = setOf("a", "b"),
        )

        val encoded = json.encodeToString(LauncherConfig.serializer(), config)
        val decoded = json.decodeFromString(LauncherConfig.serializer(), encoded)

        assertEquals(config.sections, decoded.sections)
        assertEquals(config.lockedApps, decoded.lockedApps)
        assertEquals(config.hidden, decoded.hidden)
        assertTrue(decoded.showHidden)
        assertTrue(decoded.aggressiveMemoryTrim)
        assertTrue(!decoded.respectSystemFontScale)
        assertEquals(4, decoded.uiScale)
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
        val config = LauncherConfig(deviceLock = credential)

        val decoded = json.decodeFromString(
            LauncherConfig.serializer(),
            json.encodeToString(LauncherConfig.serializer(), config),
        )

        assertEquals(credential.credentialHash, decoded.deviceLock.credentialHash)
        assertEquals(credential.credentialSalt, decoded.deviceLock.credentialSalt)
        assertTrue(LockSecurity.verify(decoded.deviceLock, "9876"))
        assertTrue(decoded.deviceLock.value.isEmpty())
    }
}
