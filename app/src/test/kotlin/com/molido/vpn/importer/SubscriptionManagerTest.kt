package com.molido.vpn.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class SubscriptionManagerTest {

    @Test
    fun parsesAndDeduplicatesSupportedUriLines() {
        val line = "vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls#Node"
        val (configs, skipped) = SubscriptionManager.parseSubscriptionBody(
            "# sample subscription\n$line\n$line\nnot-a-profile"
        )
        assertEquals(1, configs.size)
        assertEquals(1, skipped)
        assertEquals("Node", configs.single().name)
    }

    @Test
    fun parsesBase64WrappedSubscription() {
        val line = "vless://11111111-1111-1111-1111-111111111111@example.com:443?security=tls#Encoded"
        val encoded = Base64.getEncoder().encodeToString(line.toByteArray())
        val (configs, _) = SubscriptionManager.parseSubscriptionBody(encoded)
        assertEquals("Encoded", configs.single().name)
    }

    @Test
    fun rejectsEmptyAndUnsupportedYamlInsteadOfTreatingItAsValid() {
        listOf("", "proxies:\n  - name: unknown-yaml").forEach { body ->
            try {
                SubscriptionManager.parseSubscriptionBody(body)
                fail("Expected unsupported/empty subscription to be rejected")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun rejectsNonHttpsAndPrivateNetworkUrls() {
        val invalid = listOf(
            "http://example.com/sub",
            "https://127.0.0.1/sub",
            "https://10.0.0.5/sub",
            "https://192.168.1.10/sub",
            "https://localhost/sub",
            "https://user:pass@example.com/sub",
            "https://example.com:8443/sub"
        )
        invalid.forEach { value ->
            try {
                SubscriptionManager.validateSubscriptionUrl(value, resolveDns = false)
                fail("Expected URL rejection: $value")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun acceptsHttpsPublicHostnameSyntaxWithoutNetworkLookup() {
        val url = SubscriptionManager.validateSubscriptionUrl(
            "https://example.com/subscription.txt",
            resolveDns = false
        )
        assertEquals("https", url.protocol)
        assertEquals("example.com", url.host)
    }

    @Test
    fun curatedSourcesAreUniqueAndHttpsOnly() {
        val sources = SubscriptionSourceCatalog.sources
        assertTrue(sources.size >= 5)
        assertEquals(sources.size, sources.map { it.id }.toSet().size)
        assertTrue(sources.all { it.url.startsWith("https://raw.githubusercontent.com/") })
        assertFalse(sources.any { it.url.contains("radarhub.top") })
    }
}
