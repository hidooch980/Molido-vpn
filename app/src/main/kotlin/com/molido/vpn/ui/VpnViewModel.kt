package com.molido.vpn.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.molido.vpn.data.EncryptedConfigStore
import com.molido.vpn.data.FreeServerCatalog
import com.molido.vpn.dns.DohDnsResolver
import com.molido.vpn.engine.ConnectionCoordinator
import com.molido.vpn.engine.ConnectionDiagnostics
import com.molido.vpn.engine.ConnectionState
import com.molido.vpn.engine.EngineConfiguration
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
        trafficVerifier = NetworkTrafficVerifier(application),
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

    private val _language = MutableStateFlow(AppLanguage.PERSIAN)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

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

    private val _isFetchingCommunity = MutableStateFlow(false)
    val isFetchingCommunity: StateFlow<Boolean> = _isFetchingCommunity.asStateFlow()

    init {
        loadInstalledApps()
        refreshServers()
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
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

        if (engineId == "auto") {
            // In auto mode, pick the best scored server from catalog
            val bestServer = _servers.value.firstOrNull { it.score.eligible }
            if (bestServer != null) {
                updateActiveConfig(bestServer.payload)
            }
        } else {
            val freeMatch = FreeServerCatalog.getDefaultFreeServers().firstOrNull { it.engineId == engineId }
            if (freeMatch != null) {
                updateActiveConfig(freeMatch.rawPayload)
            }
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
            val label = if (_language.value == AppLanguage.PERSIAN) "انتخاب شد: ${server.name}" else "Selected: ${server.name}"
            _effects.emit(VpnUiEffect.ShowToast(label))
        }
    }

    fun importFromRawText(rawText: String) {
        try {
            val imported = ConfigImporter.detectAndParse(rawText)
            setSelectedEngine(imported.engineId)
            updateActiveConfig(imported.payload)
            refreshServers()
            viewModelScope.launch {
                val msg = if (_language.value == AppLanguage.PERSIAN) "کانفیگ با موفقیت به عنوان ${imported.engineId.uppercase()} وارد شد" else "Imported as ${imported.engineId.uppercase()}"
                _effects.emit(VpnUiEffect.ShowToast(msg))
            }
        } catch (e: Exception) {
            viewModelScope.launch {
                _effects.emit(VpnUiEffect.ShowToast("خطا در ورود: ${e.message}"))
            }
        }
    }

    fun updateFromGithubCommunity() {
        viewModelScope.launch {
            _isFetchingCommunity.value = true
            val startMsg = if (_language.value == AppLanguage.PERSIAN) "در حال دریافت سرورهای رایگان جدید از مخازن گیت‌هاب..." else "Fetching free servers from GitHub..."
            _effects.emit(VpnUiEffect.ShowToast(startMsg))

            try {
                val configs = SubscriptionManager.fetchGithubCommunitySubscriptions()
                if (configs.isNotEmpty()) {
                    // Update server list with fetched free servers
                    val newServers = configs.map { conf ->
                        val measurement = RouteMeasurement(
                            engineId = conf.engineId,
                            reachable = false,
                            latencyMillis = null,
                            packetLossPercent = null,
                            stabilityPercent = null
                        )
                        ServerItem(
                            id = "github-${conf.name.hashCode()}",
                            name = conf.name,
                            engineId = conf.engineId,
                            country = "Unverified",
                            flagEmoji = "\uD83C\uDF10",
                            payload = conf.payload,
                            measurement = measurement,
                            score = RouteScorer.score(measurement),
                            isFree = true
                        )
                    }
                    _servers.value = (_servers.value + newServers).distinctBy { it.payload }.sortedByDescending { it.score.score }
                    val doneMsg = if (_language.value == AppLanguage.PERSIAN) "${configs.size} سرور رایگان جدید از گیت‌هاب با موفقیت اضافه شد" else "Added ${configs.size} free servers from GitHub"
                    _effects.emit(VpnUiEffect.ShowToast(doneMsg))
                } else {
                    val fallbackMsg = if (_language.value == AppLanguage.PERSIAN) "پروفایل قابل‌اتصال تأییدشده‌ای در منبع عمومی پیدا نشد" else "No verified connectable profile was found in the community source"
                    _effects.emit(VpnUiEffect.ShowToast(fallbackMsg))
                }
            } catch (e: Exception) {
                _effects.emit(VpnUiEffect.ShowToast("خطا: ${e.message}"))
            } finally {
                _isFetchingCommunity.value = false
            }
        }
    }

    fun refreshServers() {
        val profiles = FreeServerCatalog.getDefaultFreeServers()
        _servers.value = profiles.map { profile ->
            val measurement = RouteMeasurement(
                engineId = profile.engineId,
                reachable = false,
                latencyMillis = null,
                packetLossPercent = null,
                stabilityPercent = null
            )
            ServerItem(
                id = profile.id,
                name = profile.name,
                engineId = profile.engineId,
                country = profile.country,
                flagEmoji = profile.flagEmoji,
                payload = profile.rawPayload,
                measurement = measurement,
                score = RouteScorer.score(measurement),
                isFree = true
            )
        }
    }

    fun onConnectClicked(context: Context) {
        val selected = _selectedEngineId.value
        val server = if (selected == "auto") {
            _servers.value.firstOrNull { it.score.eligible }
        } else null

        val targetEngine = server?.engineId ?: selected
        val payload = server?.payload ?: _activeConfigText.value
        if (selected == "auto" && server == null) {
            viewModelScope.launch {
                _effects.emit(VpnUiEffect.ShowToast(
                    if (_language.value == AppLanguage.PERSIAN)
                        "هیچ سرور اندازه‌گیری‌شده و تأییدشده‌ای برای انتخاب هوشمند موجود نیست"
                    else "No measured and verified route is available for Smart Routing"
                ))
            }
            return
        }
        if (payload.isBlank()) {
            viewModelScope.launch {
                _effects.emit(VpnUiEffect.ShowToast(
                    if (_language.value == AppLanguage.PERSIAN)
                        "ابتدا پروفایل معتبر VPN را وارد کنید"
                    else "Import a valid VPN profile first"
                ))
            }
            return
        }

        val config = EngineConfiguration(payload.toByteArray(Charsets.UTF_8))
        val adapter = coordinator.getAdapter(targetEngine)
        if (adapter == null) {
            viewModelScope.launch { _effects.emit(VpnUiEffect.ShowToast("موتور نامعتبر: $targetEngine")) }
            return
        }

        if (server != null) updateActiveConfig(server.payload)
        viewModelScope.launch {
            coordinator.requestConnect(targetEngine, config)
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) _effects.emit(VpnUiEffect.RequestVpnPermission(vpnIntent))
            else coordinator.onVpnPermissionGranted(targetEngine, config)
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        if (!isGranted) {
            viewModelScope.launch {
                val msg = if (_language.value == AppLanguage.PERSIAN) "مجوز اتصال VPN رد شد" else "VPN permission denied"
                _effects.emit(VpnUiEffect.ShowToast(msg))
            }
            coordinator.disconnect()
            return
        }

        val selected = _selectedEngineId.value
        val route = if (selected == "auto") _servers.value.firstOrNull { it.score.eligible } else null
        val engine = route?.engineId ?: selected.takeUnless { it == "auto" }
        val payload = route?.payload ?: _activeConfigText.value
        if (engine == null || payload.isBlank()) {
            coordinator.disconnect()
            viewModelScope.launch {
                _effects.emit(VpnUiEffect.ShowToast(
                    if (_language.value == AppLanguage.PERSIAN)
                        "پروفایل معتبر یا مسیر اندازه‌گیری‌شده موجود نیست"
                    else "No valid profile or measured route is available"
                ))
            }
            return
        }

        coordinator.onVpnPermissionGranted(engine, EngineConfiguration(payload.toByteArray(Charsets.UTF_8)))
    }

    fun onDisconnectClicked() {
        coordinator.disconnect()
    }
}
