package com.molido.vpn.engine

/**
 * Pure connection-state reducer. A Connected state is reachable only after an
 * explicit traffic-verification event; engine startup/handshake alone is not enough.
 */
object ConnectionStateMachine {
    fun transition(current: ConnectionState, event: ConnectionEvent): ConnectionState =
        when (event) {
            ConnectionEvent.Start ->
                if (current is ConnectionState.Idle || current is ConnectionState.Failed) {
                    ConnectionState.Preparing
                } else current

            is ConnectionEvent.PermissionGranted ->
                if (current is ConnectionState.Preparing) ConnectionState.Connecting(event.engineId)
                else current

            is ConnectionEvent.EngineReady ->
                if (current is ConnectionState.Connecting && current.engineId == event.engineId) {
                    ConnectionState.Verifying(event.engineId)
                } else current

            is ConnectionEvent.TrafficVerified ->
                if (current is ConnectionState.Verifying && current.engineId == event.engineId) {
                    ConnectionState.Connected(event.engineId, event.verifiedAtEpochMillis)
                } else current

            is ConnectionEvent.Recover ->
                ConnectionState.Recovering(event.previousEngineId, event.reasonCode)

            is ConnectionEvent.Fail ->
                ConnectionState.Failed(event.reasonCode)

            ConnectionEvent.Stop -> ConnectionState.Idle
        }
}

sealed interface ConnectionEvent {
    data object Start : ConnectionEvent
    data class PermissionGranted(val engineId: String) : ConnectionEvent
    data class EngineReady(val engineId: String) : ConnectionEvent
    data class TrafficVerified(val engineId: String, val verifiedAtEpochMillis: Long) : ConnectionEvent
    data class Recover(val previousEngineId: String?, val reasonCode: String) : ConnectionEvent
    data class Fail(val reasonCode: String) : ConnectionEvent
    data object Stop : ConnectionEvent
}
