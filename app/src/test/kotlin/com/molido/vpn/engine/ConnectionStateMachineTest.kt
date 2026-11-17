package com.molido.vpn.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionStateMachineTest {
    @Test
    fun connectedRequiresTrafficVerification() {
        var state: ConnectionState = ConnectionState.Idle
        state = ConnectionStateMachine.transition(state, ConnectionEvent.Start)
        state = ConnectionStateMachine.transition(state, ConnectionEvent.PermissionGranted("wireguard"))
        state = ConnectionStateMachine.transition(state, ConnectionEvent.EngineReady("wireguard"))

        assertEquals(ConnectionState.Verifying("wireguard"), state)
    }

    @Test
    fun verifiedTrafficMovesToConnected() {
        val state = ConnectionState.Verifying("wireguard")
        val next = ConnectionStateMachine.transition(
            state,
            ConnectionEvent.TrafficVerified("wireguard", 1234L)
        )
        assertEquals(ConnectionState.Connected("wireguard", 1234L), next)
    }

    @Test
    fun mismatchedEngineCannotBecomeConnected() {
        val state = ConnectionState.Verifying("wireguard")
        val next = ConnectionStateMachine.transition(
            state,
            ConnectionEvent.TrafficVerified("xray", 1234L)
        )
        assertEquals(state, next)
    }

    @Test
    fun recoveryCanStartANewPreparingAttempt() {
        val recovering = ConnectionState.Recovering("wireguard", "WIREGUARD_BACKEND_STOPPED")
        assertEquals(ConnectionState.Preparing, ConnectionStateMachine.transition(recovering, ConnectionEvent.Start))
    }

    @Test
    fun stopAlwaysReturnsToIdle() {
        val next = ConnectionStateMachine.transition(
            ConnectionState.Connected("wireguard", 1234L),
            ConnectionEvent.Stop
        )
        assertEquals(ConnectionState.Idle, next)
    }
}
