package com.molido.vpn.engine.amnezia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmneziaWgConfigParserTest {

    private val sampleAmnezia = """
        [Interface]
        PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
        Address = 10.200.0.2/32
        DNS = 1.1.1.1, 8.8.8.8
        MTU = 1360
        Jc = 5
        Jmin = 40
        Jmax = 70
        S1 = 20
        S2 = 30
        H1 = 12345
        H2 = 67890
        H3 = 11111
        H4 = 22222

        [Peer]
        PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
        Endpoint = 198.51.100.2:51820
        AllowedIPs = 0.0.0.0/0, ::/0
    """.trimIndent()

    @Test
    fun parseAmneziaObfuscationParametersCorrectly() {
        val config = AmneziaWgConfigParser.parse(sampleAmnezia)

        assertEquals(5, config.obfuscation.jc)
        assertEquals(40, config.obfuscation.jmin)
        assertEquals(70, config.obfuscation.jmax)
        assertEquals(20, config.obfuscation.s1)
        assertEquals(30, config.obfuscation.s2)
        assertEquals(12345L, config.obfuscation.h1)
        assertEquals(67890L, config.obfuscation.h2)
        assertEquals(11111L, config.obfuscation.h3)
        assertEquals(22222L, config.obfuscation.h4)
    }

    @Test
    fun invalidJminThrowsException() {
        val badJmin = sampleAmnezia.replace("Jmin = 40", "Jmin = 100") // Jmin > Jmax (70)
        try {
            AmneziaWgConfigParser.parse(badJmin)
            org.junit.Assert.fail("Expected exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Jmin", ignoreCase = true) == true)
        }
    }
}
