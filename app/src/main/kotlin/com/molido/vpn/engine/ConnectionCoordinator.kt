package com.molido.vpn.engine

import com.molido.vpn.verifier.TrafficVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
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

/** Serializes tunnel lifecycle operations and awaits teardown before reconnecting. */
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
    private val lifecycleMutex = Mutex()
    private var currentSession: EngineSession? = null
    private var activeAttemptId: Long = 0L
    private var pendingEngineId: String? = null
    private var pendingConfiguration: EngineConfiguration? = null

    fun getAdapter(engineId: String): EngineAdapter? = adapters[engineId]

    fun requestConnect(engineId: String, configuration: EngineConfiguration) {
        scope.launch {
            lifecycleMutex.withLock {
                if (currentSession != null || (_state.value !is ConnectionState.Idle && _state.value !is ConnectionState.Failed)) return@withLock
                val adapter = adapters[engineId]
                if (adapter == null) {
                    dispatch(ConnectionEvent.Fail("UNKNOWN_ENGINE"))
                    return@withLock
                }
                val validation = try {
                    adapter.validate(configuration)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    dispatch(ConnectionEvent.Fail("CONFIG_VALIDATION_FAILED"))
                    return@withLock
                }
                if (validation is ValidationResult.Invalid) {
                    dispatch(ConnectionEvent.Fail("CONFIG_VALIDATION_FAILED:" + validation.reasonCode))
                    return@withLock
                }
                pendingEngineId = engineId
                pendingConfiguration = configuration
                dispatch(ConnectionEvent.Start)
            }
        }
    }

    fun onVpnPermissionGranted(engineId: String, configuration: EngineConfiguration) {
        scope.launch {
            lifecycleMutex.withLock {
                // A permission callback must match the exact validated request.
                if (_state.value != ConnectionState.Preparing || pendingEngineId != engineId) return@withLock
                val expected = pendingConfiguration ?: return@withLock
                if (expected.allowIpv4 != configuration.allowIpv4 ||
                    expected.allowIpv6 != configuration.allowIpv6 ||
                    expected.dnsServers != configuration.dnsServers ||
                    !expected.opaquePayload.contentEquals(configuration.opaquePayload)
                ) return@withLock
                val adapter = adapters[engineId]
                if (adapter == null) {
                    dispatch(ConnectionEvent.Fail("UNKNOWN_ENGINE"))
                    return@withLock
                }
                pendingEngineId = null
                pendingConfiguration = null
                activeAttemptId += 1L
                val attemptId = activeAttemptId
                dispatch(ConnectionEvent.PermissionGranted(engineId))
                val interfaceReady = try {
                    onVpnInterfaceRequested(engineId, configuration)
                } catch (cancelled: CancellationException) {
                    cleanupSessionLocked()
                    throw cancelled
                } catch (_: Exception) {
                    cleanupSessionLocked()
                    dispatch(ConnectionEvent.Fail("TUN_INTERFACE_SETUP_FAILED"))
                    return@withLock
                }
                if (!interfaceReady) {
                    cleanupSessionLocked()
                    dispatch(ConnectionEvent.Fail("TUN_INTERFACE_SETUP_FAILED"))
                    return@withLock
                }
                try {
                    currentSession = adapter.connect(
                        configuration = configuration,
                        // GoBackend protects its native sockets; other adapters are fail-closed.
                        protectSocket = { false },
                        onEvent = { event -> handleEngineEvent(engineId, attemptId, event) }
                    )
                } catch (cancelled: CancellationException) {
                    cleanupSessionLocked()
                    dispatch(ConnectionEvent.Fail("CONNECTION_CANCELLED"))
                    throw cancelled
                } catch (_: Exception) {
                    cleanupSessionLocked()
                    dispatch(ConnectionEvent.Fail("ENGINE_CONNECTION_FAILED"))
                }
            }
        }
    }

    private fun handleEngineEvent(engineId: String, attemptId: Long, event: EngineEvent) {
        scope.launch {
            lifecycleMutex.withLock {
                if (activeAttemptId != attemptId || currentSession?.engineId != engineId) return@withLock
                when (event) {
                    is EngineEvent.TransportReady -> {
                        dispatch(ConnectionEvent.EngineReady(engineId))
                        verifyEgressTrafficLocked(engineId)
                    }
                    is EngineEvent.Statistics -> {
                        _diagnostics.value = _diagnostics.value.copy(
                            bytesIn = event.bytesIn.coerceAtLeast(0),
                            bytesOut = event.bytesOut.coerceAtLeast(0)
                        )
                    }
                    is EngineEvent.Failed -> {
                        cleanupSessionLocked()
                        if (event.recoverable) dispatch(ConnectionEvent.Recover(engineId, event.reasonCode))
                        else dispatch(ConnectionEvent.Fail(event.reasonCode))
                    }
                }
            }
        }
    }

    private suspend fun verifyEgressTrafficLocked(engineId: String) {
        val verification = try {
            trafficVerifier.verify(timeoutMillis = 4_000L)
        } catch (cancelled: CancellationException) {
            cleanupSessionLocked()
            throw cancelled
        } catch (_: Exception) {
            cleanupSessionLocked()
            dispatch(ConnectionEvent.Fail("EGRESS_VERIFICATION_FAILED"))
            return
        }
        if (verification.success && currentSession?.engineId == engineId) {
            val now = System.currentTimeMillis()
            _diagnostics.value = _diagnostics.value.copy(
                lastVerifiedAtMillis = now,
                lastLatencyMillis = verification.latencyMillis?.coerceAtLeast(0),
                activeEngineId = engineId,
                egressIp = verification.egressIp
            )
            dispatch(ConnectionEvent.TrafficVerified(engineId, now))
        } else {
            cleanupSessionLocked()
            dispatch(ConnectionEvent.Fail(verification.errorMessage ?: "EGRESS_VERIFICATION_FAILED"))
        }
    }

    private suspend fun cleanupSessionLocked(): Boolean = withContext(NonCancellable) {
            activeAttemptId += 1L
            pendingEngineId = null
            pendingConfiguration = null
            val session = currentSession
            var teardownSucceeded = true
            if (session != null) {
                try {
                    val adapter = adapters[session.engineId] ?: throw IllegalStateException("SESSION_ADAPTER_MISSING")
                    adapter.disconnect(session)
                    currentSession = null
                } catch (_: Exception) {
                    // Retain the session so a later explicit disconnect can retry teardown.
                    teardownSucceeded = false
                }
            }
            try {
                onVpnInterfaceTornDown()
            } catch (_: Exception) {
                teardownSucceeded = false
            }
            if (teardownSucceeded) {
                _diagnostics.value = _diagnostics.value.copy(
                    activeEngineId = null,
                    egressIp = null,
                    lastLatencyMillis = null
                )
            }
            teardownSucceeded
        }

    fun disconnect() {
        scope.launch {
            lifecycleMutex.withLock {
                if (cleanupSessionLocked()) {
                    dispatch(ConnectionEvent.Stop)
                } else {
                    dispatch(ConnectionEvent.Fail("TUN_TEARDOWN_FAILED"))
                }
            }
        }
    }

    private fun dispatch(event: ConnectionEvent) {
        _state.value = ConnectionStateMachine.transition(_state.value, event)
    }
}
