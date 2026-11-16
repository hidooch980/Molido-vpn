package com.molido.vpn.engine

import com.molido.vpn.engine.wireguard.WireGuardEngineAdapter
import com.molido.vpn.verifier.MockTrafficVerifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionCoordinatorTest {

    private val validConfig = """
        [Interface]
        PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
        Address = 10.200.0.2/32
        DNS = 1.1.1.1
        MTU = 1420

        [Peer]
        PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
        Endpoint = 198.51.100.1:51820
        AllowedIPs = 0.0.0.0/0
    """.trimIndent()

    @Test
    fun successfulConnectionMovesThroughFullVerificationToConnected() = runTest {
        val mockVerifier = MockTrafficVerifier(simulatedSuccess = true, simulatedLatency = 35L)
        val adapter = WireGuardEngineAdapter()
        val coordinator = ConnectionCoordinator(
            scope = this,
            trafficVerifier = mockVerifier,
            adapters = mapOf(adapter.id to adapter)
        )

        val config = EngineConfiguration(validConfig.toByteArray(Charsets.UTF_8))
        coordinator.requestConnect("wireguard", config)
        advanceUntilIdle()

        assertEquals(ConnectionState.Preparing, coordinator.state.value)

        coordinator.onVpnPermissionGranted("wireguard", config)
        advanceUntilIdle()

        val state = coordinator.state.value
        assertTrue("State should be Connected, but was: $state", state is ConnectionState.Connected)
        val connected = state as ConnectionState.Connected
        assertEquals("wireguard", connected.engineId)
        assertEquals(35L, coordinator.diagnostics.value.lastLatencyMillis)

        coordinator.disconnect()
        advanceUntilIdle()
        assertEquals(ConnectionState.Idle, coordinator.state.value)
    }

    @Test
    fun failedVerificationFailsClosed() = runTest {
        val mockVerifier = MockTrafficVerifier(simulatedSuccess = false, simulatedError = "Egress test failed")
        val adapter = WireGuardEngineAdapter()
        val coordinator = ConnectionCoordinator(
            scope = this,
            trafficVerifier = mockVerifier,
            adapters = mapOf(adapter.id to adapter)
        )

        val config = EngineConfiguration(validConfig.toByteArray(Charsets.UTF_8))
        coordinator.requestConnect("wireguard", config)
        advanceUntilIdle()

        coordinator.onVpnPermissionGranted("wireguard", config)
        advanceUntilIdle()

        val state = coordinator.state.value
        assertTrue("State should be Failed, but was: $state", state is ConnectionState.Failed)
        val failed = state as ConnectionState.Failed
        assertTrue(failed.reasonCode.contains("Egress test failed"))
    }
}
