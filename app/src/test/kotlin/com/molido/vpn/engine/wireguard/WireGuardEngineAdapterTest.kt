package com.molido.vpn.engine.wireguard

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The real GoBackend requires Android's VPN service and native wg-go library.
 * JVM unit tests therefore validate profile parsing only; tunnel activation is
 * covered by the Android instrumentation/device test suite.
 */
class WireGuardEngineAdapterTest {
    @Test
    fun parserRejectsEmptyProfile() {
        try {
            WireGuardConfigParser.parse("")
            throw AssertionError("Empty profile must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("empty", ignoreCase = true))
        }
    }
}
