package com.molido.vpn.engine.security

import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineEvent
import com.molido.vpn.engine.ValidationResult
import com.molido.vpn.engine.amnezia.AmneziaWgEngineAdapter
import com.molido.vpn.engine.hysteria.Hysteria2EngineAdapter
import com.molido.vpn.engine.warp.WarpEngineAdapter
import com.molido.vpn.engine.xray.XrayEngineAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnimplementedEnginesFailClosedTest {
    private val configuration = EngineConfiguration("test".toByteArray())

    @Test
    fun amneziaDoesNotReportTransportReady() = runBlocking {
        assertUnavailable { events ->
            AmneziaWgEngineAdapter().connect(configuration, { false }, { event -> events.add(event); Unit })
        }
    }

    @Test
    fun xrayDoesNotReportTransportReady() = runBlocking {
        assertUnavailable { events ->
            XrayEngineAdapter().connect(configuration, { false }, { event -> events.add(event); Unit })
        }
    }

    @Test
    fun hysteriaDoesNotReportTransportReady() = runBlocking {
        assertUnavailable { events ->
            Hysteria2EngineAdapter().connect(configuration, { false }, { event -> events.add(event); Unit })
        }
    }

    @Test
    fun warpDoesNotReportTransportReady() = runBlocking {
        assertUnavailable { events ->
            WarpEngineAdapter().connect(configuration, { false }, { event -> events.add(event); Unit })
        }
    }


    @Test
    fun warpValidationDoesNotClaimSupportWithoutRuntime() = runBlocking {
        assertTrue(WarpEngineAdapter().validate(configuration) is ValidationResult.Invalid)
    }

    @Test
    fun nonWireGuardAdaptersExposeNoRuntimeCapabilities() {
        val adapters = listOf(AmneziaWgEngineAdapter(), XrayEngineAdapter(), Hysteria2EngineAdapter(), WarpEngineAdapter())
        adapters.forEach { adapter ->
            val capabilities = adapter.capabilities
            assertTrue(!capabilities.supportsIpv4)
            assertTrue(!capabilities.supportsIpv6)
            assertTrue(!capabilities.supportsUdp)
            assertTrue(!capabilities.supportsDnsControl)
            assertTrue(!capabilities.supportsPerAppRouting)
            assertTrue(!capabilities.supportsMtuConfiguration)
        }
    }

    private suspend fun assertUnavailable(
        connect: suspend (MutableList<EngineEvent>) -> Unit
    ) {
        val events = mutableListOf<EngineEvent>()
        try {
            connect(events)
            throw AssertionError("Unimplemented engine must fail closed")
        } catch (error: UnsupportedOperationException) {
            assertEquals("ENGINE_RUNTIME_NOT_IMPLEMENTED", error.message)
        }
        assertTrue(events.none { it is EngineEvent.TransportReady })
    }
}
