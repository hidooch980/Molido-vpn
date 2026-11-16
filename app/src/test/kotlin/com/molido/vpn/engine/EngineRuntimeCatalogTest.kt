package com.molido.vpn.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class EngineRuntimeCatalogTest {
    @Test
    fun exposesExactlyTheSixApprovedEngineFamilies() {
        assertEquals(
            listOf("amneziawg", "xray", "sing-box", "wireguard", "hysteria2", "shadowsocks-2022"),
            EngineRuntimeCatalog.selectedEngines.map { it.id }
        )
    }

    @Test
    fun noPlaceholderOrUnverifiedEngineIsSelectable() {
        assertEquals(emptyList<EngineSupport>(), EngineRuntimeCatalog.selectableEngines())
        EngineRuntimeCatalog.selectedEngines.forEach { engine ->
            assertFalse("Engine ${engine.id} must remain disabled before production verification", engine.selectable)
            assertFalse(engine.readiness == EngineReadiness.PRODUCTION)
            assertNotNull(engine.releaseBlocker)
        }
    }
}
