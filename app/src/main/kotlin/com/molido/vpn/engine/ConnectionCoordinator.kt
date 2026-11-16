package com.molido.vpn.engine

import com.molido.vpn.verifier.TrafficVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ConnectionDiagnostics(
    val lastVerifiedAtMillis: Long? = null,
    val lastLatencyMillis: Long? = null,
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val activeEngineId: String? = null,
    val publicEgressIp: String? = null,
    val publicEgressCity: String? = null,
    val publicEgressRegion: String? = null,
    val publicEgressCountry: String? = null
)

class ConnectionCoordinator(
    private val scope: CoroutineScope,
    private val trafficVerifier: TrafficVerifier,
    private val adapters: Map<String, EngineAdapter>
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow(ConnectionDiagnostics())
    val diagnostics: StateFlow<ConnectionDiagnostics> = _diagnostics.asStateFlow()

    private val mutex = Mutex()
    private var currentSession: EngineSession? = null
    private var connectJob: Job? = null

    fun getAdapter(engineId: String): EngineAdapter? = adapters[engineId]

    fun requestConnect(engineId: String, configuration: EngineConfiguration) {
        scope.launch {
            mutex.withLock {
                val adapter = adapters[engineId]
                if (adapter == null) {
                    dispatch(ConnectionEvent.Fail("Unknown engine: $engineId"))
                    return@withLock
                }
                when (val validation = adapter.validate(configuration)) {
                    is ValidationResult.Invalid -> {
                        dispatch(ConnectionEvent.Fail("Config validation failed: ${validation.reasonCode}"))
                        return@withLock
                    }
                    ValidationResult.Valid -> dispatch(ConnectionEvent.Start)
                }
            }
        }
    }

    fun onVpnPermissionGranted(engineId: String, configuration: EngineConfiguration) {
        connectJob?.cancel()
        connectJob = scope.launch {
            mutex.withLock {
                val adapter = adapters[engineId] ?: return@withLock
                dispatch(ConnectionEvent.PermissionGranted(engineId))
                try {
                    val session = adapter.connect(
                        configuration = configuration,
                        onEvent = { event -> handleEngineEvent(engineId, event) }
                    )
                    currentSession = session
                } catch (_: Exception) {
                    cleanupSession()
                    dispatch(ConnectionEvent.Fail("ENGINE_START_FAILED"))
                }
            }
        }
    }

    private fun handleEngineEvent(engineId: String, event: EngineEvent) {
        scope.launch {
            mutex.withLock {
                when (event) {
                    is EngineEvent.TransportReady -> {
                        dispatch(ConnectionEvent.EngineReady(engineId))
                        verifyEgressTraffic(engineId)
                    }
                    is EngineEvent.Statistics -> {
                        _diagnostics.value = _diagnostics.value.copy(
                            bytesIn = event.bytesIn,
                            bytesOut = event.bytesOut
                        )
                    }
                    is EngineEvent.Failed -> {
                        cleanupSession()
                        if (event.recoverable) {
                            dispatch(ConnectionEvent.Recover(engineId, event.reasonCode))
                        } else {
                            dispatch(ConnectionEvent.Fail(event.reasonCode))
                        }
                    }
                }
            }
        }
    }

    private suspend fun verifyEgressTraffic(engineId: String) {
        val verification = trafficVerifier.verify(timeoutMillis = 5000L)
        if (verification.success && !verification.egressIp.isNullOrBlank()) {
            val now = System.currentTimeMillis()
            _diagnostics.value = _diagnostics.value.copy(
                lastVerifiedAtMillis = now,
                lastLatencyMillis = verification.latencyMillis,
                activeEngineId = engineId,
                publicEgressIp = verification.egressIp,
                publicEgressCity = verification.egressCity,
                publicEgressRegion = verification.egressRegion,
                publicEgressCountry = verification.egressCountry
            )
            dispatch(ConnectionEvent.TrafficVerified(engineId, now))
        } else {
            cleanupSession()
            dispatch(ConnectionEvent.Fail(verification.errorMessage ?: "PUBLIC_EGRESS_VERIFICATION_FAILED"))
        }
    }

    private suspend fun cleanupSession() {
        val session = currentSession
        currentSession = null
        if (session != null) {
            adapters[session.engineId]?.disconnect(session)
        }
        _diagnostics.value = _diagnostics.value.copy(
            activeEngineId = null,
            publicEgressIp = null,
            publicEgressCity = null,
            publicEgressRegion = null,
            publicEgressCountry = null
        )
    }

    fun disconnect() {
        connectJob?.cancel()
        scope.launch {
            mutex.withLock {
                runCatching { cleanupSession() }
                dispatch(ConnectionEvent.Stop)
            }
        }
    }

    private fun dispatch(event: ConnectionEvent) {
        _state.value = ConnectionStateMachine.transition(_state.value, event)
    }
}
