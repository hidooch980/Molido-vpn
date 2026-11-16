package com.molido.vpn.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class RouteScorerTest {
    @Test
    fun unreachableRouteIsIneligible() {
        val score = RouteScorer.score(
            RouteMeasurement("wireguard", reachable = false, latencyMillis = 20,
                packetLossPercent = 0.0, stabilityPercent = 100.0)
        )
        assertFalse(score.eligible)
        assertEquals(0.0, score.score, 0.0)
    }

    @Test
    fun bestRoutePrefersStableLowLossPath() {
        val result = RouteScorer.best(
            listOf(
                RouteMeasurement("slow", true, 900, 8.0, 80.0),
                RouteMeasurement("fast", true, 45, 0.0, 99.0)
            )
        )
        assertNotNull(result)
        assertEquals("fast", result?.engineId)
    }

    @Test
    fun scoreIsBounded() {
        val result = RouteScorer.score(
            RouteMeasurement("test", true, 0, 0.0, 100.0, userPriority = 20)
        )
        assertEquals(100.0, result.score, 0.0)
    }
}
