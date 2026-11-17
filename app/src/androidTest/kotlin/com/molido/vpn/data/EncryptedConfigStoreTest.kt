package com.molido.vpn.data

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedConfigStoreTest {
    private lateinit var context: Context
    private lateinit var preferences: android.content.SharedPreferences
    private lateinit var store: EncryptedConfigStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences("molido_secure_config", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        deleteTestKey()
        store = EncryptedConfigStore(context)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
        deleteTestKey()
    }

    @Test
    fun activeProfileIsEncryptedAndRoundTripsWithFreshIv() {
        val profile = "[Interface]\nPrivateKey = never-log-this-secret\nAddress = 10.0.0.2/32"
        store.saveActiveConfig(profile)

        val firstStoredValue = preferences.getString("active_config_payload", null)
        assertNotNull(firstStoredValue)
        assertTrue(firstStoredValue!!.startsWith("enc:v1:"))
        assertFalse(firstStoredValue.contains("never-log-this-secret"))
        assertEquals(profile, store.getActiveConfig())

        store.saveActiveConfig(profile)
        val secondStoredValue = preferences.getString("active_config_payload", null)
        assertNotEquals("AES-GCM must use a fresh IV for each save", firstStoredValue, secondStoredValue)
    }

    @Test
    fun legacyBase64ProfileMigratesToKeystoreCiphertext() {
        val profile = "[Interface]\nPrivateKey = legacy-secret\nAddress = 10.0.0.3/32"
        val legacy = Base64.encodeToString(profile.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        preferences.edit().putString("active_config_payload", legacy).commit()

        assertEquals(profile, store.getActiveConfig())

        val migrated = preferences.getString("active_config_payload", null)
        assertNotNull(migrated)
        assertTrue(migrated!!.startsWith("enc:v1:"))
        assertNotEquals(legacy, migrated)
    }

    @Test
    fun subscriptionUrlsAndSnapshotsAreEncryptedAndRoundTrip() {
        val urls = listOf("https://example.com/sub.txt", "https://raw.githubusercontent.com/example/sub.txt")
        store.saveSubscriptions(urls)
        val encryptedUrls = preferences.getString("subscriptions_urls_enc", null)
        assertNotNull(encryptedUrls)
        assertTrue(encryptedUrls!!.startsWith("enc:v1:"))
        assertFalse(encryptedUrls.contains("example.com"))
        assertEquals(urls, store.getSubscriptions())

        val disabled = setOf(urls.last())
        store.saveDisabledSubscriptions(disabled)
        val encryptedDisabled = preferences.getString("subscriptions_urls_disabled_enc", null)
        assertNotNull(encryptedDisabled)
        assertTrue(encryptedDisabled!!.startsWith("enc:v1:"))
        assertFalse(encryptedDisabled.contains("raw.githubusercontent.com"))
        assertEquals(disabled, store.getDisabledSubscriptions())

        val snapshot = """{"sources":{"test":{"configs":[{"engineId":"wireguard","payload":"private-profile"}]}}}"""
        store.saveSubscriptionSnapshot(snapshot)
        val encryptedSnapshot = preferences.getString("subscription_snapshot_enc", null)
        assertNotNull(encryptedSnapshot)
        assertFalse(encryptedSnapshot!!.contains("private-profile"))
        assertEquals(snapshot, store.getSubscriptionSnapshot())
    }

    @Test
    fun corruptedCiphertextFailsClosedAndIsCleared() {
        preferences.edit().putString("active_config_payload", "enc:v1:not-valid-ciphertext").commit()

        assertNull(store.getActiveConfig())
        assertNull(preferences.getString("active_config_payload", null))
    }

    private fun deleteTestKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias("molido.vpn.profile.aes256gcm.v1")) {
            keyStore.deleteEntry("molido.vpn.profile.aes256gcm.v1")
        }
    }
}
