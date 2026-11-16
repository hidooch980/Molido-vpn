package com.molido.vpn.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigImporterTest {

    @Test
    fun detectVlessScheme() {
        val uri = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@104.16.1.1:443?security=reality#MyVless"
        val imported = ConfigImporter.detectAndParse(uri)

        assertEquals("xray", imported.engineId)
        assertEquals("MyVless", imported.name)
    }

    @Test
    fun detectAmneziaScheme() {
        val config = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 10.200.0.2/32
            Jc = 4
            Jmin = 40
            Jmax = 70

            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 1.2.3.4:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        val imported = ConfigImporter.detectAndParse(config)
        assertEquals("amnezia", imported.engineId)
    }

    @Test
    fun detectWireGuardScheme() {
        val config = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 10.200.0.2/32

            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 1.2.3.4:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()

        val imported = ConfigImporter.detectAndParse(config)
        assertEquals("wireguard", imported.engineId)
    }

    @Test
    fun detectHysteria2Scheme() {
        val uri = "hy2://password@example.com:443/?sni=example.com#Hy2Node"
        val imported = ConfigImporter.detectAndParse(uri)

        assertEquals("hysteria2", imported.engineId)
        assertEquals("Hy2Node", imported.name)
    }
}
