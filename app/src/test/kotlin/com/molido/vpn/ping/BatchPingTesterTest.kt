package com.molido.vpn.ping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BatchPingTesterTest {

    @Test
    fun extractEndpointFromHy2Config() {
        val hy2Uri = "hy2://secret@185.190.140.5:443/?sni=test#Test"
        val endpoint = BatchPingTester.extractEndpoint("hysteria2", hy2Uri)

        assertEquals("185.190.140.5", endpoint.first)
        assertEquals(443, endpoint.second)
    }

    @Test
    fun extractEndpointFromVlessConfig() {
        val vlessUri = "vless://uuid@104.16.1.1:8443?security=none#VlessNode"
        val endpoint = BatchPingTester.extractEndpoint("xray", vlessUri)

        assertEquals("104.16.1.1", endpoint.first)
        assertEquals(8443, endpoint.second)
    }

    @Test
    fun extractEndpointFromWireGuardConfig() {
        val wgConfig = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 10.200.0.2/32
            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 198.51.100.1:51820
        """.trimIndent()
        val endpoint = BatchPingTester.extractEndpoint("wireguard", wgConfig)

        assertEquals("198.51.100.1", endpoint.first)
        assertEquals(51820, endpoint.second)
    }

    @Test
    fun pingUnreachableEndpointFailsSafely() {
        // Test an unallocated documentation IP with short timeout
        val result = BatchPingTester.pingEndpoint("test-server", Pair("192.0.2.1", 12345), timeoutMs = 100)
        assertNotNull(result)
        assertEquals("test-server", result.serverId)
        assertEquals(-1L, result.latencyMillis)
        assertEquals(false, result.isReachable)
    }
}
