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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    private var lastActiveConfiguration: EngineConfiguration? = null
    private var lastActiveEngineId: String? = null
    private var recoveryJob: Job? = null

    fun getAdapter(engineId: String): EngineAdapter? = adapters[engineId]

    fun requestConnect(engineId: String, configuration: EngineConfiguration) {
        scope.launch {
            lifecycleMutex.withLock {
                if (currentSession != null || (_state.value !is ConnectionState.Idle && _state.value !is ConnectionState.Failed && _state.value !is ConnectionState.Recovering)) return@withLock
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
                    val cleaned = cleanupSessionLocked()
                    dispatch(ConnectionEvent.Fail(if (cleaned) "TUN_INTERFACE_SETUP_FAILED" else "TUN_TEARDOWN_FAILED"))
                    return@withLock
                }
                if (!interfaceReady) {
                    val cleaned = cleanupSessionLocked()
                    dispatch(ConnectionEvent.Fail(if (cleaned) "TUN_INTERFACE_SETUP_FAILED" else "TUN_TEARDOWN_FAILED"))
                    return@withLock
                }
                try {
                    currentSession = adapter.connect(
                        configuration = configuration,
                        // GoBackend protects its native sockets; other adapters are fail-closed.
                        protectSocket = { false },
                        onEvent = { event -> handleEngineEvent(engineId, attemptId, event) }
                    )
                    lastActiveEngineId = engineId
                    lastActiveConfiguration = EngineConfiguration(
                        opaquePayload = configuration.opaquePayload.copyOf(),
                        allowIpv4 = configuration.allowIpv4,
                        allowIpv6 = configuration.allowIpv6,
                        dnsServers = configuration.dnsServers.toList()
                    )
                } catch (cancelled: CancellationException) {
                    cleanupSessionLocked()
                    dispatch(ConnectionEvent.Fail("CONNECTION_CANCELLED"))
                    throw cancelled
                } catch (_: Exception) {
                    val cleaned = cleanupSessionLocked()
                    dispatch(ConnectionEvent.Fail(if (cleaned) "ENGINE_CONNECTION_FAILED" else "TUN_TEARDOWN_FAILED"))
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
                        val cleaned = cleanupSessionLocked()
                        if (!cleaned) {
                            dispatch(ConnectionEvent.Fail("TUN_TEARDOWN_FAILED"))
                        } else if (event.recoverable) {
                            val recoveryConfig = lastActiveConfiguration
                            if (recoveryConfig == null) {
                                dispatch(ConnectionEvent.Fail("RECOVERY_CONFIG_UNAVAILABLE"))
                            } else {
                                dispatch(ConnectionEvent.Recover(engineId, event.reasonCode))
                                scheduleRecovery(engineId, recoveryConfig)
                            }
                        } else {
                            dispatch(ConnectionEvent.Fail(event.reasonCode))
                        }
                    }
                }
            }
        }
    }

    private suspend fun verifyEgressTrafficLocked(engineId: String, recoverOnFailure: Boolean = false) {
        val verification = try {
            trafficVerifier.verify(timeoutMillis = 4_000L)
        } catch (cancelled: CancellationException) {
            cleanupSessionLocked()
            throw cancelled
        } catch (_: Exception) {
            com.molido.vpn.verifier.VerificationResult(
                success = false,
                errorMessage = "EGRESS_VERIFICATION_FAILED"
            )
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
            val cleaned = cleanupSessionLocked()
            val failureCode = if (cleaned) verification.errorMessage ?: "EGRESS_VERIFICATION_FAILED"
                else "TUN_TEARDOWN_FAILED"
            val recoveryConfig = lastActiveConfiguration
            if (cleaned && recoverOnFailure && recoveryConfig != null) {
                dispatch(ConnectionEvent.Recover(engineId, failureCode))
                scheduleRecovery(engineId, recoveryConfig)
            } else {
                dispatch(ConnectionEvent.Fail(failureCode))
            }
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
                } catch (_: Exception) {
                    // Retain the session so a later explicit disconnect can retry teardown.
                    teardownSucceeded = false
                }
            }
            try {
                onVpnInterfaceTornDown()
            } catch (_: Exception) {
                // Keep session ownership until both adapter and TUN teardown have succeeded.
                teardownSucceeded = false
            }
            if (teardownSucceeded) {
                currentSession = null
                _diagnostics.value = _diagnostics.value.copy(
                    activeEngineId = null,
                    egressIp = null,
                    lastLatencyMillis = null
                )
            }
            teardownSucceeded
        }

    /**
     * Re-checks the active tunnel after Android reports a validated physical
     * network handover. A failed probe triggers bounded recovery only after teardown succeeds.
     */
    /**
     * Called only after the monitor confirms that no validated physical
     * non-VPN network remains. Tear down before entering Recovering; never
     * spin reconnect attempts while the device is offline.
     */
    fun onPhysicalNetworkLost() {
        recoveryJob?.cancel()
        recoveryJob = null
        scope.launch {
            lifecycleMutex.withLock {
                val session = currentSession
                if (session != null) {
                    val cleaned = cleanupSessionLocked()
                    if (cleaned) {
                        dispatch(ConnectionEvent.Recover(session.engineId, "NO_USABLE_PHYSICAL_NETWORK"))
                    } else {
                        dispatch(ConnectionEvent.Fail("TUN_TEARDOWN_FAILED"))
                    }
                } else if (_state.value is ConnectionState.Preparing) {
                    pendingEngineId = null
                    pendingConfiguration = null
                    dispatch(ConnectionEvent.Fail("NO_USABLE_PHYSICAL_NETWORK"))
                }
            }
        }
    }

    fun verifyActiveTunnelAfterNetworkChange() {
        scope.launch {
            lifecycleMutex.withLock {
                val session = currentSession
                if (session != null && _state.value is ConnectionState.Connected) {
                    verifyEgressTrafficLocked(session.engineId, recoverOnFailure = true)
                } else if (session == null &&
                    (_state.value is ConnectionState.Recovering || _state.value is ConnectionState.Failed)
                ) {
                    val config = lastActiveConfiguration
                    val recoverEngineId = lastActiveEngineId
                        ?: (_state.value as? ConnectionState.Recovering)?.previousEngineId
                    if (config != null && recoverEngineId != null) {
                        scheduleRecovery(recoverEngineId, config)
                    }
                }
            }
        }
    }

    private fun scheduleRecovery(engineId: String, configuration: EngineConfiguration) {
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            repeat(MAX_RECOVERY_ATTEMPTS) { attempt ->
                delay(RECOVERY_BACKOFF_MILLIS * (attempt + 1))
                if (_state.value !is ConnectionState.Recovering && _state.value !is ConnectionState.Failed) return@launch

                requestConnect(engineId, configuration)
                val prepared = state.first {
                    it is ConnectionState.Preparing || it is ConnectionState.Connected || it is ConnectionState.Failed
                }
                if (prepared is ConnectionState.Connected) return@launch
                if (prepared !is ConnectionState.Preparing) return@repeat

                onVpnPermissionGranted(engineId, configuration)
                val result = state.first { it is ConnectionState.Connected || it is ConnectionState.Failed }
                if (result is ConnectionState.Connected) return@launch
            }
            if (_state.value is ConnectionState.Failed) {
                dispatch(ConnectionEvent.Fail("RECOVERY_EXHAUSTED"))
            }
        }
    }

    fun disconnect() {
        recoveryJob?.cancel()
        recoveryJob = null
        lastActiveConfiguration = null
        lastActiveEngineId = null
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

    private companion object {
        const val MAX_RECOVERY_ATTEMPTS = 3
        const val RECOVERY_BACKOFF_MILLIS = 750L
    }

    private fun dispatch(event: ConnectionEvent) {
        _state.value = ConnectionStateMachine.transition(_state.value, event)
    }
}
