package com.molido.vpn.engine

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
        val adapter = TestReadyAdapter()
        val coordinator = ConnectionCoordinator(
            scope = this,
            trafficVerifier = mockVerifier,
            adapters = mapOf(adapter.id to adapter)
        )

        val config = EngineConfiguration(validConfig.toByteArray(Charsets.UTF_8))
        coordinator.requestConnect("test-ready", config)
        advanceUntilIdle()

        assertEquals(ConnectionState.Preparing, coordinator.state.value)

        coordinator.onVpnPermissionGranted("test-ready", config)
        advanceUntilIdle()

        val state = coordinator.state.value
        assertTrue("State should be Connected, but was: $state", state is ConnectionState.Connected)
        val connected = state as ConnectionState.Connected
        assertEquals("test-ready", connected.engineId)
        assertEquals(35L, coordinator.diagnostics.value.lastLatencyMillis)

        coordinator.disconnect()
        advanceUntilIdle()
        assertEquals(ConnectionState.Idle, coordinator.state.value)
    }

    @Test
    fun permissionCallbackMustMatchValidatedEngineAndConfiguration() = runTest {
        val adapter = TestReadyAdapter()
        val coordinator = ConnectionCoordinator(
            scope = this,
            trafficVerifier = MockTrafficVerifier(simulatedSuccess = true),
            adapters = mapOf(adapter.id to adapter)
        )
        val config = EngineConfiguration(validConfig.toByteArray(Charsets.UTF_8))
        val alteredConfig = EngineConfiguration("[Interface]".toByteArray(Charsets.UTF_8))

        coordinator.requestConnect("test-ready", config)
        advanceUntilIdle()
        assertEquals(ConnectionState.Preparing, coordinator.state.value)

        coordinator.onVpnPermissionGranted("unknown-engine", config)
        coordinator.onVpnPermissionGranted("test-ready", alteredConfig)
        advanceUntilIdle()
        assertEquals(ConnectionState.Preparing, coordinator.state.value)

        coordinator.onVpnPermissionGranted("test-ready", config)
        advanceUntilIdle()
        assertTrue(coordinator.state.value is ConnectionState.Connected)
    }

    @Test
    fun failedAdapterDisconnectRetainsSessionAndCanBeRetried() = runTest {
        val adapter = RetryDisconnectAdapter()
        var tunTeardownCalls = 0
        val coordinator = ConnectionCoordinator(
            scope = this,
            trafficVerifier = MockTrafficVerifier(simulatedSuccess = true),
            adapters = mapOf(adapter.id to adapter),
            onVpnInterfaceTornDown = { tunTeardownCalls++ }
        )
        val config = EngineConfiguration(validConfig.toByteArray(Charsets.UTF_8))

        coordinator.requestConnect(adapter.id, config)
        advanceUntilIdle()
        coordinator.onVpnPermissionGranted(adapter.id, config)
        advanceUntilIdle()
        assertTrue(coordinator.state.value is ConnectionState.Connected)

        coordinator.disconnect()
        advanceUntilIdle()
        assertTrue("A failed teardown must not report Idle", coordinator.state.value is ConnectionState.Failed)
        assertEquals(1, adapter.disconnectCalls)

        // A new connection must remain blocked while the old session is still owned.
        coordinator.requestConnect(adapter.id, config)
        advanceUntilIdle()
        assertTrue(coordinator.state.value is ConnectionState.Failed)

        coordinator.disconnect()
        advanceUntilIdle()
        assertEquals(ConnectionState.Idle, coordinator.state.value)
        assertEquals(2, adapter.disconnectCalls)
        assertEquals(2, tunTeardownCalls)
    }

    @Test
    fun failedVerificationFailsClosed() = runTest {
        val mockVerifier = MockTrafficVerifier(simulatedSuccess = false, simulatedError = "Egress test failed")
        val adapter = TestReadyAdapter()
        val coordinator = ConnectionCoordinator(
            scope = this,
            trafficVerifier = mockVerifier,
            adapters = mapOf(adapter.id to adapter)
        )

        val config = EngineConfiguration(validConfig.toByteArray(Charsets.UTF_8))
        coordinator.requestConnect("test-ready", config)
        advanceUntilIdle()

        coordinator.onVpnPermissionGranted("test-ready", config)
        advanceUntilIdle()

        val state = coordinator.state.value
        assertTrue("State should be Failed, but was: $state", state is ConnectionState.Failed)
        val failed = state as ConnectionState.Failed
        assertTrue(failed.reasonCode.contains("Egress test failed"))
    }

    private class RetryDisconnectAdapter : EngineAdapter {
        override val id: String = "retry-disconnect"
        override val displayName: String = "Retry disconnect test adapter"
        override val capabilities = EngineCapabilities(true, true, true, true, false, false)
        var disconnectCalls = 0

        override suspend fun validate(configuration: EngineConfiguration): ValidationResult =
            ValidationResult.Valid

        override suspend fun connect(
            configuration: EngineConfiguration,
            protectSocket: (Int) -> Boolean,
            onEvent: (EngineEvent) -> Unit
        ): EngineSession {
            val session = object : EngineSession {
                override val engineId: String = id
            }
            onEvent(EngineEvent.TransportReady)
            return session
        }

        override suspend fun disconnect(session: EngineSession) {
            disconnectCalls++
            if (disconnectCalls == 1) error("simulated backend teardown failure")
        }
    }

    private class TestReadyAdapter : EngineAdapter {
        override val id: String = "test-ready"
        override val displayName: String = "Test-only ready adapter"
        override val capabilities = EngineCapabilities(
            supportsIpv4 = true,
            supportsIpv6 = true,
            supportsUdp = true,
            supportsDnsControl = true,
            supportsPerAppRouting = false,
            supportsMtuConfiguration = false
        )

        override suspend fun validate(configuration: EngineConfiguration): ValidationResult =
            ValidationResult.Valid

        override suspend fun connect(
            configuration: EngineConfiguration,
            protectSocket: (Int) -> Boolean,
            onEvent: (EngineEvent) -> Unit
        ): EngineSession {
            onEvent(EngineEvent.TransportReady)
            return object : EngineSession {
                override val engineId: String = id
            }
        }

        override suspend fun disconnect(session: EngineSession) = Unit
    }

}
