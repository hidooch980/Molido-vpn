package com.molido.vpn.verifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class VerificationResult(
    val success: Boolean,
    val latencyMillis: Long? = null,
    val egressIp: String? = null,
    val errorMessage: String? = null
)

interface TrafficVerifier {
    suspend fun verify(timeoutMillis: Long = 3000L): VerificationResult
}

class NetworkTrafficVerifier(
    private val probeHosts: List<Pair<String, Int>> = listOf(
        Pair("1.1.1.1", 443),
        Pair("1.0.0.1", 443),
        Pair("8.8.8.8", 443)
    )
) : TrafficVerifier {
    override suspend fun verify(timeoutMillis: Long): VerificationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var lastError: String? = null

        for ((host, port) in probeHosts) {
            try {
                Socket().use { socket ->
                    val endpoint = InetSocketAddress(host, port)
                    socket.connect(endpoint, timeoutMillis.toInt())
                    val latency = System.currentTimeMillis() - startTime
                    return@withContext VerificationResult(
                        success = true,
                        latencyMillis = latency,
                        egressIp = host
                    )
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Connection timeout"
            }
        }

        VerificationResult(
            success = false,
            errorMessage = lastError ?: "All probe targets failed verification"
        )
    }
}

class MockTrafficVerifier(
    var simulatedSuccess: Boolean = true,
    var simulatedLatency: Long = 45L,
    var simulatedError: String? = null
) : TrafficVerifier {
    override suspend fun verify(timeoutMillis: Long): VerificationResult {
        return if (simulatedSuccess) {
            VerificationResult(success = true, latencyMillis = simulatedLatency, egressIp = "1.1.1.1")
        } else {
            VerificationResult(success = false, errorMessage = simulatedError ?: "Probe failed")
        }
    }
}
