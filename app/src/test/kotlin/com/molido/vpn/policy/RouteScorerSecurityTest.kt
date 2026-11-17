package com.molido.vpn.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteScorerSecurityTest {
    @Test
    fun rejectsNegativeLatency() {
        assertFalse(RouteScorer.score(measurement(latency = -1L)).eligible)
    }

    @Test
    fun rejectsLossOutsideValidRange() {
        assertFalse(RouteScorer.score(measurement(loss = 101.0)).eligible)
        assertFalse(RouteScorer.score(measurement(loss = -0.1)).eligible)
    }

    @Test
    fun rejectsNonFiniteMeasurements() {
        assertFalse(RouteScorer.score(measurement(loss = Double.NaN)).eligible)
        assertFalse(RouteScorer.score(measurement(stability = Double.POSITIVE_INFINITY)).eligible)
    }

    @Test
    fun rejectsBlankEngineId() {
        assertFalse(RouteScorer.score(measurement(engineId = " ")).eligible)
    }

    @Test
    fun validMeasurementsRemainEligibleAndSelectable() {
        val valid = measurement()
        assertTrue(RouteScorer.score(valid).eligible)
        assertNotNull(RouteScorer.best(listOf(valid)))
        assertNull(RouteScorer.best(listOf(measurement(latency = -1L))))
    }

    private fun measurement(
        engineId: String = "wireguard",
        latency: Long = 45L,
        loss: Double = 0.0,
        stability: Double = 99.0
    ) = RouteMeasurement(
        engineId = engineId,
        reachable = true,
        latencyMillis = latency,
        packetLossPercent = loss,
        stabilityPercent = stability
    )
}
