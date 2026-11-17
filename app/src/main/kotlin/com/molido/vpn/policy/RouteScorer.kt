package com.molido.vpn.policy

/**
 * Deterministic, bounded route scoring. Measurements are supplied by the
 * connectivity probe; this class does not perform network requests.
 */
data class RouteMeasurement(
    val engineId: String,
    val reachable: Boolean,
    val latencyMillis: Long?,
    val packetLossPercent: Double?,
    val stabilityPercent: Double?,
    val userPriority: Int = 0
)

data class RouteScore(
    val engineId: String,
    val score: Double,
    val eligible: Boolean
)

object RouteScorer {
    fun score(measurement: RouteMeasurement): RouteScore {
        val latencyValue = measurement.latencyMillis
        val lossValue = measurement.packetLossPercent
        val stabilityValue = measurement.stabilityPercent
        if (measurement.engineId.isBlank() ||
            !measurement.reachable ||
            latencyValue == null || latencyValue < 0L ||
            lossValue == null || !lossValue.isFinite() || lossValue !in 0.0..100.0 ||
            stabilityValue == null || !stabilityValue.isFinite() || stabilityValue !in 0.0..100.0
        ) {
            return RouteScore(measurement.engineId, 0.0, false)
        }

        val latency = latencyValue
        val loss = lossValue
        val stability = stabilityValue
        val latencyScore = (100.0 - (latency / 30.0)).coerceIn(0.0, 100.0)
        val lossScore = 100.0 - loss
        val priority = measurement.userPriority.coerceIn(-20, 20).toDouble()

        val total = (latencyScore * 0.35) +
            (lossScore * 0.35) +
            (stability * 0.30) +
            priority

        return RouteScore(measurement.engineId, total.coerceIn(0.0, 100.0), true)
    }

    fun best(measurements: List<RouteMeasurement>): RouteScore? =
        measurements.asSequence()
            .map(::score)
            .filter { it.eligible }
            .maxWithOrNull(compareBy<RouteScore> { it.score }.thenBy { it.engineId })
}
