package com.molido.vpn.engine.warp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarpProvisionerTest {

    @Test
    fun generateFreeProfileGeneratesValidWireGuardStructure() {
        val config = WarpProvisioner.generateFreeProfile()

        assertNotNull(config.interfaceConfig.privateKey)
        assertEquals(WarpProvisioner.WARP_PUBLIC_KEY, config.peerConfig.publicKey)
        assertEquals("162.159.192.1", config.peerConfig.endpointHost)
        assertEquals(2408, config.peerConfig.endpointPort)
        assertEquals(1280, config.interfaceConfig.mtu)
        assertTrue(config.rawText.contains("[Interface]"))
        assertTrue(config.rawText.contains("[Peer]"))
    }
}
