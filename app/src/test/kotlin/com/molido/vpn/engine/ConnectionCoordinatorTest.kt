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
    private class FakeSession : EngineSession {
        override val engineId = "fake"
    }

    private class FakeAdapter : EngineAdapter {
        override val id = "fake"
        override val displayName = "Test transport"
        override val capabilities = EngineCapabilities(true, true, true, true, true, true)

        override suspend fun validate(configuration: EngineConfiguration) =
            if (configuration.opaquePayload.isNotEmpty()) ValidationResult.Valid
            else ValidationResult.Invalid("EMPTY_PROFILE")

        override suspend fun connect(
            configuration: EngineConfiguration,
            onEvent: (EngineEvent) -> Unit
        ): EngineSession {
            onEvent(EngineEvent.TransportReady)
            return FakeSession()
        }

        override suspend fun disconnect(session: EngineSession) = Unit
    }

    @Test
    fun successfulConnectionRequiresVerifiedPublicEgress() = runTest {
        val adapter = FakeAdapter()
        val coordinator = ConnectionCoordinator(
            scope = this,
            trafficVerifier = MockTrafficVerifier(simulatedSuccess = true, simulatedLatency = 35L),
            adapters = mapOf(adapter.id to adapter)
        )
        val config = EngineConfiguration(byteArrayOf(1))
        coordinator.requestConnect("fake", config)
        advanceUntilIdle()
        assertEquals(ConnectionState.Preparing, coordinator.state.value)

        coordinator.onVpnPermissionGranted("fake", config)
        advanceUntilIdle()

        val state = coordinator.state.value
        assertTrue("Expected Connected only after egress verification, got $state", state is ConnectionState.Connected)
        assertEquals("fake", (state as ConnectionState.Connected).engineId)
        assertEquals("1.1.1.1", coordinator.diagnostics.value.publicEgressIp)
        coordinator.disconnect()
        advanceUntilIdle()
        assertEquals(ConnectionState.Idle, coordinator.state.value)
    }

    @Test
    fun failedEgressVerificationFailsClosed() = runTest {
        val adapter = FakeAdapter()
        val coordinator = ConnectionCoordinator(
            scope = this,
            trafficVerifier = MockTrafficVerifier(simulatedSuccess = false, simulatedError = "egress failure"),
            adapters = mapOf(adapter.id to adapter)
        )
        val config = EngineConfiguration(byteArrayOf(1))
        coordinator.requestConnect("fake", config)
        advanceUntilIdle()
        coordinator.onVpnPermissionGranted("fake", config)
        advanceUntilIdle()
        assertTrue(coordinator.state.value is ConnectionState.Failed)
        assertEquals(null, coordinator.diagnostics.value.publicEgressIp)
    }
}
