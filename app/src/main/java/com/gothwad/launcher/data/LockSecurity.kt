@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.gothwad.launcher.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlin.io.encoding.Base64 as KotlinBase64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Credential storage + verification for Device Lock / App Lock / Hidden-Apps Vault.
 *
 * Before this existed, every PIN/password was stored as plain text inside the
 * DataStore JSON (`LockCredential.value`). That file lives in internal storage, but it
 * was also included in Android auto-backup (`allowBackup="true"`), so a 4-digit PIN
 * could be read straight out of a device/cloud backup.
 *
 * Now the credential itself is only kept as a salted PBKDF2 hash
 * ([LockCredential.credentialHash] + [LockCredential.credentialSalt]). The legacy
 * plain-text [LockCredential.value] field remains in the model so old configs can be
 * migrated on first launch (see [migrateLegacyCredentials]); it is cleared afterwards.
 *
 * This module also provides a small brute-force throttle: on TV remotes a 4-digit PIN
 * is only 10k combinations, so unlimited attempts made the lock decorative.
 */
object LockSecurity {

    private const val TAG = "LockSecurity"

    /** Throttle buckets - one per independent lock (App Lock and Vault). */
    const val SCOPE_APP = "app"
    const val SCOPE_VAULT = "vault"
    const val SCOPE_DEFAULT = "default"

    private const val ALGORITHM = "PBKDF2WithHmacSHA1" // available on every supported API level
    private const val ITERATIONS = 12_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    // ----- throttle tuning -----
    private const val MAX_FREE_ATTEMPTS = 5
    private const val BASE_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 5 * 60_000L
    private const val THROTTLE_PREFS = "gothwad_lock_throttle"

    // =========================================================================
    // Hashing
    // =========================================================================

    /** Fresh random salt, Base64 encoded. */
    fun randomSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        // kotlin.io.encoding.Base64 is pure Kotlin: the security core stays testable in
        // plain JVM unit tests (android.util.Base64 is a stub outside a device/Robolectric).
        return KotlinBase64.encode(bytes)
    }

    /** PBKDF2 hash of [secret] with [saltB64], Base64 encoded. */
    fun hash(secret: String, saltB64: String): String {
        return runCatching {
            val salt = KotlinBase64.decode(saltB64)
            val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_BITS)
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            try {
                KotlinBase64.encode(factory.generateSecret(spec).encoded)
            } finally {
                spec.clearPassword()
            }
        }.getOrElse { e ->
            // Should never happen (PBKDF2WithHmacSHA1 ships with Android), but never
            // silently fall back to plain text.
            Log.e(TAG, "PBKDF2 unavailable", e)
            ""
        }
    }

    /** Builds a credential that stores only the derived hash (+ keep the type/length). */
    fun createCredential(secret: String, type: LockCredentialType, pinLength: Int): LockCredential {
        val salt = randomSalt()
        return LockCredential(
            enabled = true,
            type = type,
            value = "", // never persist the secret itself
            credentialHash = hash(secret, salt),
            credentialSalt = salt,
            pinLength = pinLength,
        )
    }

    /**
     * Constant-time verification against [credential]. Returns false when the credential
     * carries no hash (not configured / migration not possible).
     */
    fun verify(credential: LockCredential, candidate: String): Boolean {
        if (candidate.isEmpty()) return false
        val salt = credential.credentialSalt
        val stored = credential.credentialHash
        // Legacy plain-text credential that has not been migrated yet (tiny window right
        // after an app update): compare directly, constant-time, rather than locking the
        // user out of their own TV.
        if (salt.isBlank() || stored.isBlank()) {
            return credential.value.isNotEmpty() &&
                MessageDigest.isEqual(candidate.toByteArray(), credential.value.toByteArray())
        }

        val computed = hash(candidate, salt)
        if (computed.isBlank()) return false
        return MessageDigest.isEqual(
            computed.toByteArray(Charsets.UTF_8),
            stored.toByteArray(Charsets.UTF_8),
        )
    }

    /** True when a credential is configured (hashed) and enabled. */
    fun isConfigured(credential: LockCredential): Boolean =
        credential.credentialHash.isNotBlank() && credential.credentialSalt.isNotBlank()

    // =========================================================================
    // Legacy plain-text migration
    // =========================================================================

    /**
     * Hashes + clears any plain-text credential left over from an older install.
     * Safe to call on every launch; it is a no-op once migration is complete.
     */
    suspend fun migrateLegacyCredentials(store: ConfigStore) {
        val config = runCatching { store.flow.first() }.getOrNull() ?: return
        if (!hasLegacyPlainText(config)) return

        Log.i(TAG, "Migrating legacy plain-text lock credentials to PBKDF2 hashes")
        store.update { cfg ->
            cfg.copy(
                appLock = migrateOne(cfg.appLock),
                hiddenAppsLock = migrateOne(cfg.hiddenAppsLock),
            )
        }
    }

    private fun hasLegacyPlainText(config: LauncherConfig): Boolean =
        (config.appLock.value.isNotEmpty() && config.appLock.credentialHash.isEmpty()) ||
            (config.hiddenAppsLock.value.isNotEmpty() && config.hiddenAppsLock.credentialHash.isEmpty())

    private fun migrateOne(credential: LockCredential): LockCredential {
        if (credential.value.isEmpty() || credential.credentialHash.isNotEmpty()) return credential
        val salt = randomSalt()
        return credential.copy(
            credentialHash = hash(credential.value, salt),
            credentialSalt = salt,
            value = "",
        )
    }

    // =========================================================================
    // Brute-force throttle (per lock scope: "device", "app", "vault")
    // =========================================================================

    /** Milliseconds the given scope is still locked out for; 0 when attempts are allowed. */
    fun lockoutRemainingMs(context: Context, scope: String): Long {
        val until = prefs(context).getLong("${scope}_until", 0L)
        if (until <= 0L) return 0L
        val remaining = until - SystemClock.elapsedRealtime()
        return remaining.coerceAtLeast(0L)
    }

    /** Records a wrong attempt; locks the scope out for an increasing delay after 5 tries. */
    fun recordFailure(context: Context, scope: String) {
        val prefs = prefs(context)
        val fails = prefs.getInt("${scope}_fails", 0) + 1
        val editor = prefs.edit().putInt("${scope}_fails", fails)

        if (fails >= MAX_FREE_ATTEMPTS) {
            val exponent = (fails - MAX_FREE_ATTEMPTS).coerceIn(0, 4)
            val delay = (BASE_LOCKOUT_MS shl exponent).coerceAtMost(MAX_LOCKOUT_MS)
            editor.putLong("${scope}_until", SystemClock.elapsedRealtime() + delay)
            Log.w(TAG, "Lock scope '$scope': $fails failed attempts - locked out for ${delay / 1000}s")
        }
        editor.apply()
    }

    /** Clears the failure counter after a correct credential. */
    fun recordSuccess(context: Context, scope: String) {
        prefs(context).edit()
            .remove("${scope}_fails")
            .remove("${scope}_until")
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(THROTTLE_PREFS, Context.MODE_PRIVATE)
}
