package com.molido.vpn.engine.warp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanIpScannerTest {

    @Test
    fun candidateIpsArePresent() {
        assertTrue(CleanIpScanner.CANDIDATE_IPS.isNotEmpty())
        assertTrue(CleanIpScanner.CANDIDATE_IPS.any { it.startsWith("162.159.") })
    }

    @Test
    fun applyCleanIpReplacesExistingEndpoint() {
        val originalConfig = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 172.16.0.2/32

            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 162.159.192.1:2408
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        val updated = CleanIpScanner.applyCleanIpToWarpConfig(originalConfig, "188.114.97.1", 2408)

        assertTrue(updated.contains("Endpoint = 188.114.97.1:2408"))
        assertTrue(!updated.contains("162.159.192.1:2408"))
    }

    @Test
    fun testCandidateUnreachableFailsGracefully() {
        // Test an unallocated documentation IP with short timeout
        val result = CleanIpScanner.testCandidate("192.0.2.1", 2408, timeoutMs = 100)
        assertEquals(false, result.isClean)
        assertEquals(-1L, result.latencyMillis)
    }
}
