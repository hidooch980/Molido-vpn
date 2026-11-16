package com.molido.vpn.engine.hysteria

import com.molido.vpn.engine.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Hysteria2ConfigParserTest {

    @Test
    fun parseStandardHy2UriCorrectly() {
        val uri = "hy2://molidopassword@185.190.140.5:443/?insecure=1&sni=speed.cloudflare.com&obfs=salamander&obfs-password=secretpass#Germany-HighSpeed"
        val config = Hysteria2ConfigParser.parse(uri)

        assertEquals("molidopassword", config.auth)
        assertEquals("185.190.140.5", config.server)
        assertEquals(443, config.port)
        assertTrue(config.insecure)
        assertEquals("speed.cloudflare.com", config.sni)
        assertEquals("salamander", config.obfsType)
        assertEquals("secretpass", config.obfsPassword)
        assertEquals("Germany-HighSpeed", config.remark)
    }

    @Test
    fun parseHysteria2AliasUriCorrectly() {
        val uri = "hysteria2://freeuser@fastnode.org:8443/?sni=fastnode.org#Node1"
        val config = Hysteria2ConfigParser.parse(uri)

        assertEquals("freeuser", config.auth)
        assertEquals("fastnode.org", config.server)
        assertEquals(8443, config.port)
        assertEquals("fastnode.org", config.sni)
        assertEquals("Node1", config.remark)
    }

    @Test
    fun validationRejectsInvalidUri() {
        val invalidScheme = Hysteria2ConfigParser.validate("vless://invalid")
        assertTrue(invalidScheme is ValidationResult.Invalid)

        val invalidPort = Hysteria2ConfigParser.validate("hy2://user@example.com:99999/")
        assertTrue(invalidPort is ValidationResult.Invalid)
    }
}
