package com.molido.vpn.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.molido.vpn.engine.ConnectionCoordinator
import com.molido.vpn.engine.ConnectionDiagnostics
import com.molido.vpn.engine.ConnectionState
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.ValidationResult
import com.molido.vpn.engine.wireguard.WireGuardEngineAdapter
import com.molido.vpn.policy.RouteMeasurement
import com.molido.vpn.policy.RouteScore
import com.molido.vpn.policy.RouteScorer
import com.molido.vpn.verifier.NetworkTrafficVerifier
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ServerItem(
    val id: String,
    val name: String,
    val country: String,
    val flagEmoji: String,
    val measurement: RouteMeasurement,
    val score: RouteScore
)

sealed interface VpnUiEffect {
    data class RequestVpnPermission(val intent: Intent) : VpnUiEffect
    data class ShowToast(val message: String) : VpnUiEffect
}

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val wireguardAdapter = WireGuardEngineAdapter(application)

    val coordinator = ConnectionCoordinator(
        scope = viewModelScope,
        trafficVerifier = NetworkTrafficVerifier(),
        adapters = mapOf(wireguardAdapter.id to wireguardAdapter)
    )

    val connectionState: StateFlow<ConnectionState> = coordinator.state
    val diagnostics: StateFlow<ConnectionDiagnostics> = coordinator.diagnostics

    private val _effects = MutableSharedFlow<VpnUiEffect>()
    val effects: SharedFlow<VpnUiEffect> = _effects.asSharedFlow()

    private val _selectedEngineId = MutableStateFlow("wireguard")
    val selectedEngineId: StateFlow<String> = _selectedEngineId.asStateFlow()

    private val _wireGuardConfigText = MutableStateFlow("")
    val wireGuardConfigText: StateFlow<String> = _wireGuardConfigText.asStateFlow()

    private val _servers = MutableStateFlow<List<ServerItem>>(emptyList())
    val servers: StateFlow<List<ServerItem>> = _servers.asStateFlow()

    fun setSelectedEngine(engineId: String) {
        _selectedEngineId.value = engineId
    }

    fun updateWireGuardConfig(newText: String) {
        _wireGuardConfigText.value = newText
    }

    /** Server inventory is intentionally empty until real, operator-provided endpoints exist. */
    fun refreshServers() {
        _servers.value = emptyList()
    }

    fun onConnectClicked(context: Context) {
        val engineId = _selectedEngineId.value
        val config = EngineConfiguration(_wireGuardConfigText.value.toByteArray(Charsets.UTF_8))
        val adapter = coordinator.getAdapter(engineId)
        if (adapter == null) {
            viewModelScope.launch { _effects.emit(VpnUiEffect.ShowToast("Unsupported engine")) }
            return
        }

        viewModelScope.launch {
            when (val validation = adapter.validate(config)) {
                is ValidationResult.Invalid -> {
                    _effects.emit(VpnUiEffect.ShowToast("A valid WireGuard profile is required"))
                    return@launch
                }
                ValidationResult.Valid -> Unit
            }
            coordinator.requestConnect(engineId, config)
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                _effects.emit(VpnUiEffect.RequestVpnPermission(vpnIntent))
            } else {
                coordinator.onVpnPermissionGranted(engineId, config)
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            val config = EngineConfiguration(_wireGuardConfigText.value.toByteArray(Charsets.UTF_8))
            coordinator.onVpnPermissionGranted(_selectedEngineId.value, config)
        } else {
            viewModelScope.launch {
                _effects.emit(VpnUiEffect.ShowToast("VPN permission denied"))
            }
            coordinator.disconnect()
        }
    }

    fun onDisconnectClicked() {
        coordinator.disconnect()
    }
}
