package com.gothwad.launcher.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for credential storage (issue #38, protects the fix for #7).
 *
 * The plain-text `value` field must never be required for a login, and hashes must be
 * salted (two identical PINs must not produce the same stored hash).
 */
class LockSecurityTest {

    @Test
    fun `created credential stores a hash, never the secret`() {
        val credential = LockSecurity.createCredential("1234", LockCredentialType.NUMERIC, 4)

        assertTrue(credential.ready)
        assertTrue(credential.configured)
        assertTrue(credential.value.isEmpty())
        assertTrue(credential.credentialHash.isNotEmpty())
        assertTrue(credential.credentialSalt.isNotEmpty())
    }

    @Test
    fun `correct secret verifies and wrong secret does not`() {
        val credential = LockSecurity.createCredential("1234", LockCredentialType.NUMERIC, 4)

        assertTrue(LockSecurity.verify(credential, "1234"))
        assertFalse(LockSecurity.verify(credential, "1235"))
        assertFalse(LockSecurity.verify(credential, ""))
    }

    @Test
    fun `same secret hashes differently every time (salted)`() {
        val first = LockSecurity.createCredential("password", LockCredentialType.ALPHANUMERIC, 0)
        val second = LockSecurity.createCredential("password", LockCredentialType.ALPHANUMERIC, 0)

        assertNotEquals(first.credentialSalt, second.credentialSalt)
        assertNotEquals(first.credentialHash, second.credentialHash)
        assertTrue(LockSecurity.verify(second, "password"))
    }

    @Test
    fun `legacy plain text credential still verifies during migration`() {
        // Old configs (before the hashing fix) carry the secret in `value` only.
        val legacy = LockCredential(enabled = true, type = LockCredentialType.NUMERIC, value = "4321")

        assertTrue(legacy.ready)
        assertFalse(legacy.configured)
        assertTrue(LockSecurity.verify(legacy, "4321"))
        assertFalse(LockSecurity.verify(legacy, "1234"))
    }

    @Test
    fun `an unconfigured credential never unlocks`() {
        val empty = LockCredential()

        assertFalse(empty.ready)
        assertFalse(LockSecurity.verify(empty, ""))
        assertFalse(LockSecurity.verify(empty, "0000"))
    }
}
