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
    val egressIp: String? = null
)

class ConnectionCoordinator(
    private val scope: CoroutineScope,
    private val trafficVerifier: TrafficVerifier,
    private val adapters: Map<String, EngineAdapter>,
    private val onVpnInterfaceRequested: suspend (engineId: String, config: EngineConfiguration) -> Boolean = { _, _ -> true },
    private val onVpnInterfaceTornDown: suspend () -> Unit = {}
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

                val validation = adapter.validate(configuration)
                if (validation is ValidationResult.Invalid) {
                    dispatch(ConnectionEvent.Fail("Config validation failed: ${validation.reasonCode}"))
                    return@withLock
                }

                dispatch(ConnectionEvent.Start)
            }
        }
    }

    fun onVpnPermissionGranted(engineId: String, configuration: EngineConfiguration) {
        connectJob?.cancel()
        connectJob = scope.launch {
            mutex.withLock {
                val adapter = adapters[engineId] ?: return@withLock
                dispatch(ConnectionEvent.PermissionGranted(engineId))

                val interfaceReady = try {
                    onVpnInterfaceRequested(engineId, configuration)
                } catch (e: Exception) {
                    dispatch(ConnectionEvent.Fail("TUN_INTERFACE_SETUP_FAILED"))
                    return@withLock
                }

                if (!interfaceReady) {
                    dispatch(ConnectionEvent.Fail("Failed to establish TUN interface"))
                    return@withLock
                }

                try {
                    val session = adapter.connect(
                        configuration = configuration,
                        // Do not claim socket protection for adapters that do not implement it.
                        // GoBackend protects its own native sockets internally.
                        protectSocket = { false },
                        onEvent = { event -> handleEngineEvent(engineId, event) }
                    )
                    currentSession = session
                } catch (e: Exception) {
                    cleanupSession()
                    dispatch(ConnectionEvent.Fail("ENGINE_CONNECTION_FAILED"))
                }
            }
        }
    }

    private fun handleEngineEvent(engineId: String, event: EngineEvent) {
        scope.launch {
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

    private suspend fun verifyEgressTraffic(engineId: String) {
        val verification = trafficVerifier.verify(timeoutMillis = 4000L)
        if (verification.success) {
            val now = System.currentTimeMillis()
            _diagnostics.value = _diagnostics.value.copy(
                lastVerifiedAtMillis = now,
                lastLatencyMillis = verification.latencyMillis,
                activeEngineId = engineId,
                egressIp = verification.egressIp
            )
            dispatch(ConnectionEvent.TrafficVerified(engineId, now))
        } else {
            val reason = verification.errorMessage ?: "Egress traffic verification failed"
            cleanupSession()
            dispatch(ConnectionEvent.Fail(reason))
        }
    }

    private fun cleanupSession() {
        currentSession?.let { session ->
            adapters[session.engineId]?.let { adapter ->
                scope.launch {
                    try { adapter.disconnect(session) } catch (_: Exception) { /* cleanup remains fail-closed */ }
                }
            }
            currentSession = null
        }
        scope.launch {
            try {
                onVpnInterfaceTornDown()
            } catch (_: Exception) {}
        }
        _diagnostics.value = _diagnostics.value.copy(activeEngineId = null, egressIp = null)
    }

    fun disconnect() {
        connectJob?.cancel()
        cleanupSession()
        dispatch(ConnectionEvent.Stop)
    }

    private fun dispatch(event: ConnectionEvent) {
        _state.value = ConnectionStateMachine.transition(_state.value, event)
    }
}
