package com.molido.vpn.engine.wireguard

import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.ValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardEngineAdapterTest {

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
    fun adapterHasCorrectMetadataAndCapabilities() {
        val adapter = WireGuardEngineAdapter()
        assertEquals("wireguard", adapter.id)
        assertEquals("WireGuard", adapter.displayName)
        assertTrue(adapter.capabilities.supportsIpv4)
        assertTrue(adapter.capabilities.supportsIpv6)
        assertTrue(adapter.capabilities.supportsUdp)
        assertTrue(adapter.capabilities.supportsDnsControl)
        assertTrue(adapter.capabilities.supportsPerAppRouting)
        assertTrue(adapter.capabilities.supportsMtuConfiguration)
    }

    @Test
    fun adapterValidatesPayloadCorrectly() = runBlocking {
        val adapter = WireGuardEngineAdapter()
        val validResult = adapter.validate(EngineConfiguration(validConfig.toByteArray(Charsets.UTF_8)))
        assertEquals(ValidationResult.Valid, validResult)

        val invalidResult = adapter.validate(EngineConfiguration("invalid-payload".toByteArray(Charsets.UTF_8)))
        assertTrue(invalidResult is ValidationResult.Invalid)
    }

    @Test
    fun connectFailsClosedUntilRealGoBackendIsIntegrated() = runBlocking {
        val adapter = WireGuardEngineAdapter()
        val events = mutableListOf<EngineEvent>()
        var failed = false
        try {
            adapter.connect(
                configuration = EngineConfiguration(validConfig.toByteArray(Charsets.UTF_8)),
                protectSocket = { true },
                onEvent = { events.add(it) }
            )
        } catch (e: UnsupportedOperationException) {
            failed = e.message == "WIREGUARD_BACKEND_NOT_INTEGRATED"
        }

        assertTrue("WireGuard must not report a fake transport-ready event", failed)
        assertEquals(1, events.size)
        assertTrue(events[0] is EngineEvent.Failed)
    }
}
