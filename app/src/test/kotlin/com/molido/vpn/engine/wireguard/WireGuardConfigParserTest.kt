package com.molido.vpn.engine.wireguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WireGuardConfigParserTest {

    private val validConfig = """
        [Interface]
        PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
        Address = 10.200.0.2/32, fd00::2/128
        DNS = 1.1.1.1, 8.8.8.8
        MTU = 1420

        [Peer]
        PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
        Endpoint = 198.51.100.1:51820
        AllowedIPs = 0.0.0.0/0, ::/0
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun parseValidConfigSuccess() {
        val config = WireGuardConfigParser.parse(validConfig)

        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=", config.interfaceConfig.privateKey)
        assertEquals(2, config.interfaceConfig.addresses.size)
        assertEquals("10.200.0.2/32", config.interfaceConfig.addresses[0])
        assertEquals(1420, config.interfaceConfig.mtu)
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), config.interfaceConfig.dnsServers)

        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=", config.peerConfig.publicKey)
        assertEquals("198.51.100.1", config.peerConfig.endpointHost)
        assertEquals(51820, config.peerConfig.endpointPort)
        assertEquals(25, config.peerConfig.persistentKeepalive)
    }

    @Test
    fun parseRejectsEmptyConfig() {
        try {
            WireGuardConfigParser.parse("")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("empty", ignoreCase = true) == true)
        }
    }

    @Test
    fun parseRejectsMissingPeer() {
        val noPeer = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 10.200.0.2/32
        """.trimIndent()

        try {
            WireGuardConfigParser.parse(noPeer)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Peer", ignoreCase = true) == true)
        }
    }

    @Test
    fun parseRejectsInvalidKeyLength() {
        val badKey = """
            [Interface]
            PrivateKey = shortKey=
            Address = 10.200.0.2/32

            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 1.2.3.4:51820
        """.trimIndent()

        try {
            WireGuardConfigParser.parse(badKey)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Base64", ignoreCase = true) == true)
        }
    }

    @Test
    fun parseRejectsInvalidEndpointPort() {
        val badPort = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 10.200.0.2/32

            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 1.2.3.4:999999
        """.trimIndent()

        try {
            WireGuardConfigParser.parse(badPort)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("port", ignoreCase = true) == true)
        }
    }
}
