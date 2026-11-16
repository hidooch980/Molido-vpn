package com.molido.vpn.engine

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Preparing : ConnectionState
    data class Connecting(val engineId: String) : ConnectionState
    data class Verifying(val engineId: String) : ConnectionState
    data class Connected(
        val engineId: String,
        val verifiedAtEpochMillis: Long
    ) : ConnectionState
    data class Recovering(val previousEngineId: String?, val reasonCode: String) : ConnectionState
    data class Failed(val reasonCode: String) : ConnectionState
}
