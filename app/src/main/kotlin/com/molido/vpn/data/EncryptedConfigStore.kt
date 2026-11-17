package com.molido.vpn.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores VPN profiles encrypted with an AES-256-GCM key held by Android Keystore.
 * Legacy Base64-only values are migrated on first read and never treated as encrypted.
 */
class EncryptedConfigStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun saveActiveConfig(configText: String) {
        require(configText.isNotBlank()) { "CONFIG_EMPTY" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(configText.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        val stored = ENCRYPTED_PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
        check(prefs.edit().putString(KEY_ACTIVE_CONFIG, stored).commit()) {
            "CONFIG_STORAGE_FAILED"
        }
    }

    @Synchronized
    fun getActiveConfig(): String? {
        val stored = prefs.getString(KEY_ACTIVE_CONFIG, null) ?: return null
        if (stored.startsWith(ENCRYPTED_PREFIX)) {
            return try {
                decrypt(stored.removePrefix(ENCRYPTED_PREFIX))
            } catch (_: Exception) {
                // Corrupt ciphertext or an invalidated Keystore key must fail closed.
                prefs.edit().remove(KEY_ACTIVE_CONFIG).commit()
                null
            }
        }

        // One-time migration from the previous Base64-only storage format.
        return try {
            val legacyBytes = Base64.decode(stored, Base64.DEFAULT)
            val legacyPlaintext = String(legacyBytes, Charsets.UTF_8)
            if (legacyPlaintext.isBlank()) {
                prefs.edit().remove(KEY_ACTIVE_CONFIG).commit()
                null
            } else {
                saveActiveConfig(legacyPlaintext)
                legacyPlaintext
            }
        } catch (_: Exception) {
            prefs.edit().remove(KEY_ACTIVE_CONFIG).commit()
            null
        }
    }

    private fun decrypt(encodedPayload: String): String {
        val payload = Base64.decode(encodedPayload, Base64.NO_WRAP)
        require(payload.size > GCM_IV_LENGTH + GCM_TAG_LENGTH_BYTES) { "CONFIG_CIPHERTEXT_INVALID" }
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = payload.copyOfRange(GCM_IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(AAD)
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    @Synchronized
    fun saveSelectedEngine(engineId: String) {
        prefs.edit().putString(KEY_ENGINE_ID, engineId).apply()
    }

    fun getSelectedEngine(): String =
        prefs.getString(KEY_ENGINE_ID, "wireguard") ?: "wireguard"

    @Synchronized
    fun saveKillSwitchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KILL_SWITCH, enabled).apply()
    }

    fun isKillSwitchEnabled(): Boolean = prefs.getBoolean(KEY_KILL_SWITCH, false)

    @Synchronized
    fun saveSelectedDns(dnsId: String) {
        prefs.edit().putString(KEY_SELECTED_DNS, dnsId).apply()
    }

    fun getSelectedDns(): String =
        prefs.getString(KEY_SELECTED_DNS, "cloudflare") ?: "cloudflare"

    @Synchronized
    fun saveSplitTunnelApps(packageNames: Set<String>) {
        prefs.edit().putStringSet(KEY_SPLIT_APPS, packageNames.toSet()).apply()
    }

    fun getSplitTunnelApps(): Set<String> =
        prefs.getStringSet(KEY_SPLIT_APPS, emptySet())?.toSet() ?: emptySet()

    @Synchronized
    fun saveSubscriptions(urls: List<String>) {
        val normalized = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val encrypted = encryptText(normalized.joinToString("\n"), SUBSCRIPTIONS_AAD)
        check(prefs.edit()
            .putString(KEY_SUBSCRIPTIONS_ENCRYPTED, encrypted)
            .remove(KEY_SUBSCRIPTIONS)
            .commit()) { "SUBSCRIPTION_STORAGE_FAILED" }
    }

    @Synchronized
    fun getSubscriptions(): List<String> {
        val encrypted = prefs.getString(KEY_SUBSCRIPTIONS_ENCRYPTED, null)
        if (encrypted != null) {
            return try {
                decryptText(encrypted, SUBSCRIPTIONS_AAD)
                    .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            } catch (_: Exception) {
                prefs.edit().remove(KEY_SUBSCRIPTIONS_ENCRYPTED).commit()
                emptyList()
            }
        }
        // Migrate legacy plaintext URL set once, then remove it.
        val legacy = try { prefs.getStringSet(KEY_SUBSCRIPTIONS, emptySet())?.toList().orEmpty() }
        catch (_: ClassCastException) { emptyList() }
        if (legacy.isNotEmpty()) saveSubscriptions(legacy)
        return legacy
    }

    @Synchronized
    fun saveSubscriptionSnapshot(snapshotJson: String) {
        require(snapshotJson.length <= MAX_SNAPSHOT_CHARS) { "SUBSCRIPTION_SNAPSHOT_TOO_LARGE" }
        val encrypted = encryptText(snapshotJson, SNAPSHOT_AAD)
        check(prefs.edit().putString(KEY_SUBSCRIPTION_SNAPSHOT, encrypted).commit()) {
            "SUBSCRIPTION_SNAPSHOT_STORAGE_FAILED"
        }
    }

    @Synchronized
    fun getSubscriptionSnapshot(): String? {
        val stored = prefs.getString(KEY_SUBSCRIPTION_SNAPSHOT, null) ?: return null
        return try {
            decryptText(stored, SNAPSHOT_AAD)
        } catch (_: Exception) {
            prefs.edit().remove(KEY_SUBSCRIPTION_SNAPSHOT).commit()
            null
        }
    }

    private fun encryptText(value: String, aad: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad)
        return ENCRYPTED_PREFIX + Base64.encodeToString(
            cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
    }

    private fun decryptText(stored: String, aad: ByteArray): String {
        require(stored.startsWith(ENCRYPTED_PREFIX)) { "ENCRYPTED_VALUE_INVALID" }
        val payload = Base64.decode(stored.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
        require(payload.size > GCM_IV_LENGTH + GCM_TAG_LENGTH_BYTES) { "ENCRYPTED_VALUE_INVALID" }
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = payload.copyOfRange(GCM_IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad)
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    companion object {
        private const val PREFS_NAME = "molido_secure_config"
        private const val KEY_ACTIVE_CONFIG = "active_config_payload"
        private const val KEY_ENGINE_ID = "selected_engine_id"
        private const val KEY_KILL_SWITCH = "kill_switch_enabled"
        private const val KEY_SELECTED_DNS = "selected_dns_provider"
        private const val KEY_SPLIT_APPS = "split_tunnel_apps"
        private const val KEY_SUBSCRIPTIONS = "subscriptions_urls"
        private const val KEY_SUBSCRIPTIONS_ENCRYPTED = "subscriptions_urls_enc"
        private const val KEY_SUBSCRIPTION_SNAPSHOT = "subscription_snapshot_enc"
        private const val MAX_SNAPSHOT_CHARS = 2 * 1024 * 1024
        private const val KEY_ALIAS = "molido.vpn.profile.aes256gcm.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENCRYPTED_PREFIX = "enc:v1:"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8
        private val AAD = "MolidoVPN/active-profile/v1".toByteArray(Charsets.UTF_8)
        private val SUBSCRIPTIONS_AAD = "MolidoVPN/subscription-urls/v1".toByteArray(Charsets.UTF_8)
        private val SNAPSHOT_AAD = "MolidoVPN/subscription-snapshot/v1".toByteArray(Charsets.UTF_8)
    }
}
