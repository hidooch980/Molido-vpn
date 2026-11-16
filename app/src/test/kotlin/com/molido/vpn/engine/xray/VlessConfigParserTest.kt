package com.molido.vpn.engine.xray

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessConfigParserTest {

    @Test
    fun parseVlessRealityUriCorrectly() {
        val uri = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@104.16.1.1:443?security=reality&sni=speed.cloudflare.com&pbk=bmXOC%2BF1FxEMF9dyiK2H5%2F1SUtzH0JuVo51h2wPfgyo%3D&type=tcp&flow=xtls-rprx-vision#MyReality"

        val config = VlessConfigParser.parse(uri)

        assertEquals("vless", config.protocol)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", config.id)
        assertEquals("104.16.1.1", config.server)
        assertEquals(443, config.port)
        assertEquals("reality", config.security)
        assertEquals("speed.cloudflare.com", config.sni)
        assertEquals("xtls-rprx-vision", config.flow)
        assertEquals("MyReality", config.remark)
    }

    @Test
    fun parseTrojanUriCorrectly() {
        val uri = "trojan://secretpass@example.com:443?security=tls&sni=example.com#TrojanServer"
        val config = VlessConfigParser.parse(uri)

        assertEquals("trojan", config.protocol)
        assertEquals("secretpass", config.id)
        assertEquals("example.com", config.server)
        assertEquals(443, config.port)
        assertEquals("tls", config.security)
        assertEquals("TrojanServer", config.remark)
    }

    @Test
    fun invalidSchemeThrowsException() {
        try {
            VlessConfigParser.parse("http://example.com")
            org.junit.Assert.fail("Expected exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("scheme", ignoreCase = true) == true)
        }
    }
}
