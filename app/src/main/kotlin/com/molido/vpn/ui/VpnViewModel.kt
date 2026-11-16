package com.molido.vpn.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.molido.vpn.data.EncryptedConfigStore
import com.molido.vpn.data.FreeServerCatalog
import com.molido.vpn.data.FreeServerProfile
import com.molido.vpn.dns.DohDnsResolver
import com.molido.vpn.engine.ConnectionCoordinator
import com.molido.vpn.engine.ConnectionDiagnostics
import com.molido.vpn.engine.ConnectionState
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.EngineRuntimeCatalog
import com.molido.vpn.engine.EngineReadiness
import com.molido.vpn.engine.amnezia.AmneziaWgEngineAdapter
import com.molido.vpn.engine.warp.WarpEngineAdapter
import com.molido.vpn.engine.wireguard.WireGuardEngineAdapter
import com.molido.vpn.engine.xray.XrayEngineAdapter
import com.molido.vpn.importer.ConfigImporter
import com.molido.vpn.importer.SubscriptionManager
import com.molido.vpn.policy.AppInfo
import com.molido.vpn.policy.RouteMeasurement
import com.molido.vpn.policy.RouteScore
import com.molido.vpn.policy.RouteScorer
import com.molido.vpn.policy.SplitTunnelManager
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
    val engineId: String,
    val country: String,
    val flagEmoji: String,
    val payload: String,
    val measurement: RouteMeasurement,
    val score: RouteScore,
    val isFree: Boolean = true
)

sealed interface VpnUiEffect {
    data class RequestVpnPermission(val intent: Intent) : VpnUiEffect
    data class ShowToast(val message: String) : VpnUiEffect
}

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    val configStore = EncryptedConfigStore(application)

    private val wireguardAdapter = WireGuardEngineAdapter()
    private val amneziaAdapter = AmneziaWgEngineAdapter()
    private val xrayAdapter = XrayEngineAdapter()
    private val warpAdapter = WarpEngineAdapter()

    val coordinator = ConnectionCoordinator(
        scope = viewModelScope,
        trafficVerifier = NetworkTrafficVerifier(),
        adapters = mapOf(
            wireguardAdapter.id to wireguardAdapter,
            amneziaAdapter.id to amneziaAdapter,
            xrayAdapter.id to xrayAdapter,
            warpAdapter.id to warpAdapter
        ),
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

    private val _selectedEngineId = MutableStateFlow(configStore.getSelectedEngine())
    val selectedEngineId: StateFlow<String> = _selectedEngineId.asStateFlow()

    private val _activeConfigText = MutableStateFlow(
        configStore.getActiveConfig().orEmpty()
    )
    val activeConfigText: StateFlow<String> = _activeConfigText.asStateFlow()

    private val _servers = MutableStateFlow<List<ServerItem>>(emptyList())
    val servers: StateFlow<List<ServerItem>> = _servers.asStateFlow()

    private val _killSwitchEnabled = MutableStateFlow(configStore.isKillSwitchEnabled())
    val killSwitchEnabled: StateFlow<Boolean> = _killSwitchEnabled.asStateFlow()

    private val _selectedDns = MutableStateFlow(configStore.getSelectedDns())
    val selectedDns: StateFlow<String> = _selectedDns.asStateFlow()

    private val _splitApps = MutableStateFlow(configStore.getSplitTunnelApps())
    val splitApps: StateFlow<Set<String>> = _splitApps.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    init {
        loadInstalledApps()
        refreshServers()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            try {
                val apps = SplitTunnelManager.getInstalledApps(getApplication())
                _installedApps.value = apps
            } catch (_: Exception) {}
        }
    }

    fun setSelectedEngine(engineId: String) {
        _selectedEngineId.value = engineId
        configStore.saveSelectedEngine(engineId)

        // If active config doesn't match engine, pick default free server of that engine
        val freeMatch = FreeServerCatalog.getDefaultFreeServers().firstOrNull { it.engineId == engineId }
        if (freeMatch != null) {
            updateActiveConfig(freeMatch.rawPayload)
        }
    }

    fun updateActiveConfig(newText: String) {
        _activeConfigText.value = newText
        configStore.saveActiveConfig(newText)
    }

    fun toggleKillSwitch(enabled: Boolean) {
        _killSwitchEnabled.value = enabled
        configStore.saveKillSwitchEnabled(enabled)
    }

    fun setDnsProvider(providerId: String) {
        _selectedDns.value = providerId
        configStore.saveSelectedDns(providerId)
    }

    fun toggleAppExclusion(packageName: String) {
        val current = _splitApps.value.toMutableSet()
        if (current.contains(packageName)) current.remove(packageName) else current.add(packageName)
        _splitApps.value = current
        configStore.saveSplitTunnelApps(current)
    }

    fun selectServer(server: ServerItem) {
        setSelectedEngine(server.engineId)
        updateActiveConfig(server.payload)
        viewModelScope.launch {
            _effects.emit(VpnUiEffect.ShowToast("Selected: ${server.name}"))
        }
    }

    fun importFromRawText(rawText: String) {
        try {
            val imported = ConfigImporter.detectAndParse(rawText)
            setSelectedEngine(imported.engineId)
            updateActiveConfig(imported.payload)
            refreshServers()
            viewModelScope.launch {
                _effects.emit(VpnUiEffect.ShowToast("Imported successfully as ${imported.engineId.uppercase()}"))
            }
        } catch (e: Exception) {
            viewModelScope.launch {
                _effects.emit(VpnUiEffect.ShowToast("Import failed: ${e.message}"))
            }
        }
    }

    fun importFromSubscription(url: String) {
        viewModelScope.launch {
            try {
                _effects.emit(VpnUiEffect.ShowToast("Fetching subscription..."))
                val configs = SubscriptionManager.fetchSubscription(url)
                if (configs.isEmpty()) {
                    _effects.emit(VpnUiEffect.ShowToast("No valid configurations found in subscription"))
                    return@launch
                }

                // Add to saved subscriptions
                val currentSubs = configStore.getSubscriptions().toMutableList()
                if (!currentSubs.contains(url)) currentSubs.add(url)
                configStore.saveSubscriptions(currentSubs)

                // Select the first imported server
                val first = configs.first()
                setSelectedEngine(first.engineId)
                updateActiveConfig(first.payload)
                refreshServers()
                _effects.emit(VpnUiEffect.ShowToast("Fetched ${configs.size} servers from subscription"))
            } catch (e: Exception) {
                _effects.emit(VpnUiEffect.ShowToast("Subscription error: ${e.message}"))
            }
        }
    }

    fun refreshServers() {
        val freeServers = emptyList<FreeServerProfile>() // Real provider profiles only; no fabricated demo endpoints.

        // No synthetic measurements are eligible for AI route selection.

        val items = freeServers.map { profile ->
            val measurement = RouteMeasurement(profile.engineId, false, null, null, null)
            val score = RouteScorer.score(measurement)
            ServerItem(
                id = profile.id,
                name = profile.name,
                engineId = profile.engineId,
                country = profile.country,
                flagEmoji = profile.flagEmoji,
                payload = profile.rawPayload,
                measurement = measurement,
                score = score
            )
        }.sortedByDescending { it.score.score }

        _servers.value = items
    }

    fun onConnectClicked(context: Context) {
        val engineId = _selectedEngineId.value
        val configPayload = _activeConfigText.value.toByteArray(Charsets.UTF_8)
        val config = EngineConfiguration(configPayload)

        val runtime = EngineRuntimeCatalog.find(engineId)
        if (runtime == null || !runtime.selectable || runtime.readiness != EngineReadiness.PRODUCTION) {
            viewModelScope.launch { _effects.emit(VpnUiEffect.ShowToast(runtime?.releaseBlocker ?: "ENGINE_NOT_AVAILABLE")) }
            return
        }

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
            val configPayload = _activeConfigText.value.toByteArray(Charsets.UTF_8)
            coordinator.onVpnPermissionGranted(engineId, EngineConfiguration(configPayload))
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
