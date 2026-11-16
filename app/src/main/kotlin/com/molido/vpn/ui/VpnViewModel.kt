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
import com.molido.vpn.engine.wireguard.WireGuardEngineAdapter
import com.molido.vpn.policy.RouteMeasurement
import com.molido.vpn.policy.RouteScore
import com.molido.vpn.policy.RouteScorer
import com.molido.vpn.verifier.NetworkTrafficVerifier
import com.molido.vpn.vpn.MolidoVpnService
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

    private val wireguardAdapter = WireGuardEngineAdapter()

    val coordinator = ConnectionCoordinator(
        scope = viewModelScope,
        trafficVerifier = NetworkTrafficVerifier(),
        adapters = mapOf(wireguardAdapter.id to wireguardAdapter),
        onVpnInterfaceRequested = { engineId, config ->
            val context = getApplication<Application>()
            val configText = String(config.opaquePayload, Charsets.UTF_8)
            MolidoVpnService.startService(context, engineId, configText)
            true
        },
        onVpnInterfaceTornDown = {
            val context = getApplication<Application>()
            MolidoVpnService.stopService(context)
        }
    )

    val connectionState: StateFlow<ConnectionState> = coordinator.state
    val diagnostics: StateFlow<ConnectionDiagnostics> = coordinator.diagnostics

    private val _effects = MutableSharedFlow<VpnUiEffect>()
    val effects: SharedFlow<VpnUiEffect> = _effects.asSharedFlow()

    private val _selectedEngineId = MutableStateFlow("wireguard")
    val selectedEngineId: StateFlow<String> = _selectedEngineId.asStateFlow()

    private val _wireGuardConfigText = MutableStateFlow(DEFAULT_WG_CONFIG)
    val wireGuardConfigText: StateFlow<String> = _wireGuardConfigText.asStateFlow()

    private val _servers = MutableStateFlow<List<ServerItem>>(emptyList())
    val servers: StateFlow<List<ServerItem>> = _servers.asStateFlow()

    init {
        refreshServers()
    }

    fun setSelectedEngine(engineId: String) {
        _selectedEngineId.value = engineId
    }

    fun updateWireGuardConfig(newText: String) {
        _wireGuardConfigText.value = newText
    }

    fun refreshServers() {
        val rawMeasurements = listOf(
            Triple("wg-de-1", "Germany (Frankfurt)", "\uD83C\uDDE9\uD83C\uDDEA") to RouteMeasurement("wireguard", true, 38L, 0.0, 99.0, userPriority = 5),
            Triple("wg-nl-1", "Netherlands (Amsterdam)", "\uD83C\uDDF3\uD83C\uDDF1") to RouteMeasurement("wireguard", true, 45L, 0.2, 98.0, userPriority = 0),
            Triple("wg-fi-1", "Finland (Helsinki)", "\uD83C\uDDEB\uD83C\uDDEE") to RouteMeasurement("wireguard", true, 68L, 0.0, 96.0, userPriority = 0),
            Triple("wg-us-1", "USA (New York)", "\uD83C\uDDFA\uD83C\uDDF8") to RouteMeasurement("wireguard", true, 115L, 1.0, 92.0, userPriority = -5)
        )

        val items = rawMeasurements.map { (meta, measurement) ->
            val score = RouteScorer.score(measurement)
            ServerItem(
                id = meta.first,
                name = meta.second,
                country = meta.second,
                flagEmoji = meta.third,
                measurement = measurement,
                score = score
            )
        }.sortedByDescending { it.score.score }

        _servers.value = items
    }

    fun onConnectClicked(context: Context) {
        val engineId = _selectedEngineId.value
        val configPayload = _wireGuardConfigText.value.toByteArray(Charsets.UTF_8)
        val config = EngineConfiguration(configPayload)

        // Validate configuration first
        val adapter = coordinator.getAdapter(engineId)
        if (adapter == null) {
            viewModelScope.launch { _effects.emit(VpnUiEffect.ShowToast("Unsupported engine: $engineId")) }
            return
        }

        viewModelScope.launch {
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
            val engineId = _selectedEngineId.value
            val configPayload = _wireGuardConfigText.value.toByteArray(Charsets.UTF_8)
            coordinator.onVpnPermissionGranted(engineId, EngineConfiguration(configPayload))
        } else {
            viewModelScope.launch {
                _effects.emit(VpnUiEffect.ShowToast("VPN permission denied by user"))
            }
            coordinator.disconnect()
        }
    }

    fun onDisconnectClicked() {
        coordinator.disconnect()
    }

    companion object {
        val DEFAULT_WG_CONFIG = """
            [Interface]
            PrivateKey = aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa=
            Address = 10.200.0.2/32
            DNS = 1.1.1.1, 8.8.8.8
            MTU = 1420

            [Peer]
            PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
            Endpoint = 198.51.100.1:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 25
        """.trimIndent()
    }
}
