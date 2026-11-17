package com.molido.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServerCountryClassifierTest {
    @Test
    fun resolvesOnlyExplicitCountryLabels() {
        assertEquals("DE", ServerCountryClassifier.resolve("Fast Germany Server").code)
        assertEquals("US", ServerCountryClassifier.resolve("🇺🇸 Premium Node").code)
        assertEquals("US", ServerCountryClassifier.resolve("New York US 01").code)
        assertEquals("ZZ", ServerCountryClassifier.resolve("GitHub Free Node").code)
        assertEquals("ZZ", ServerCountryClassifier.resolve("de-fast.example.net").code)
    }

    @Test
    fun sortsCountriesAndKeepsUnknownLast() {
        val sorted = ServerCountryClassifier.sorted(
            listOf(
                ServerCountryClassifier.resolve("GitHub Free Node"),
                ServerCountryClassifier.resolve("US server"),
                ServerCountryClassifier.resolve("Germany")
            ),
            persian = true
        )
        assertEquals("ZZ", sorted.last().code)
        assertNotEquals("ZZ", sorted.first().code)
    }
}
