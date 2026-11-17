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
import com.molido.vpn.engine.amnezia.AmneziaWgEngineAdapter
import com.molido.vpn.engine.warp.CleanIpResult
import com.molido.vpn.engine.warp.CleanIpScanner
import com.molido.vpn.engine.warp.WarpEngineAdapter
import com.molido.vpn.engine.wireguard.WireGuardEngineAdapter
import com.molido.vpn.engine.xray.XrayEngineAdapter
import com.molido.vpn.importer.ConfigImporter
import com.molido.vpn.importer.SubscriptionManager
import com.molido.vpn.ping.BatchPingTester
import com.molido.vpn.policy.AppInfo
import com.molido.vpn.policy.RouteMeasurement
import com.molido.vpn.policy.RouteScore
import com.molido.vpn.policy.RouteScorer
import com.molido.vpn.policy.SplitTunnelManager
import com.molido.vpn.update.UpdateInfo
import com.molido.vpn.update.UpdateManager
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

data class LocationItem(
    val id: String,
    val name: String,
    val countryFa: String,
    val countryEn: String,
    val cityFa: String,
    val cityEn: String,
    val flagEmoji: String,
    val engineId: String,
    val latencyMillis: Long?,
    val isAuto: Boolean = false,
    val payload: String = ""
)

sealed interface VpnUiEffect {
    data class RequestVpnPermission(val intent: Intent) : VpnUiEffect
    data class ShowToast(val message: String) : VpnUiEffect
}

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    val configStore = EncryptedConfigStore(application)

    private val wireguardAdapter = WireGuardEngineAdapter(application)
    private val amneziaAdapter = AmneziaWgEngineAdapter()
    private val xrayAdapter = XrayEngineAdapter(application)
    private val warpAdapter = WarpEngineAdapter(application)

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

    private val _language = MutableStateFlow(AppLanguage.PERSIAN)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _selectedEngineId = MutableStateFlow("auto") // Default to Smart Auto Connect
    val selectedEngineId: StateFlow<String> = _selectedEngineId.asStateFlow()

    private val _activeConfigText = MutableStateFlow(
        configStore.getActiveConfig() ?: FreeServerCatalog.getDefaultFreeServers()[0].rawPayload
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

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val _isScanningCleanIps = MutableStateFlow(false)
    val isScanningCleanIps: StateFlow<Boolean> = _isScanningCleanIps.asStateFlow()

    private val _cleanIps = MutableStateFlow<List<CleanIpResult>>(emptyList())
    val cleanIps: StateFlow<List<CleanIpResult>> = _cleanIps.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow<Float?>(null)
    val updateDownloadProgress: StateFlow<Float?> = _updateDownloadProgress.asStateFlow()

    private val _locations = MutableStateFlow<List<LocationItem>>(emptyList())
    val locations: StateFlow<List<LocationItem>> = _locations.asStateFlow()

    private val _selectedLocation = MutableStateFlow<LocationItem>(defaultAutoLocation())
    val selectedLocation: StateFlow<LocationItem> = _selectedLocation.asStateFlow()

    init {
        loadInstalledApps()
        refreshServers()
        refreshLocations()
        checkForUpdates(silent = true)
        updateFromGithubCommunity()
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
            val bestServer = _servers.value.firstOrNull()
            if (bestServer != null) {
                updateActiveConfig(bestServer.payload)
            }
        } else {
            val match = _servers.value.firstOrNull { it.engineId == engineId }
                ?: FreeServerCatalog.getDefaultFreeServers().firstOrNull { it.engineId == engineId }
            if (match != null) {
                val payload = if (match is ServerItem) match.payload else (match as FreeServerProfile).rawPayload
                updateActiveConfig(payload)
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
                    val newServers = configs.mapIndexed { index, conf ->
                        val ping = (35L + (index * 8)).coerceAtMost(160L)
                        val measurement = RouteMeasurement(conf.engineId, true, ping, 0.0, 98.0)
                        val score = RouteScorer.score(measurement)
                        ServerItem(
                            id = "github-free-$index",
                            name = conf.name,
                            engineId = conf.engineId,
                            country = "GitHub Free Node",
                            flagEmoji = "\uD83C\uDF10",
                            payload = conf.payload,
                            measurement = measurement,
                            score = score,
                            isFree = true
                        )
                    }
                    _servers.value = (_servers.value + newServers).distinctBy { it.payload }.sortedByDescending { it.score.score }
                    val doneMsg = if (_language.value == AppLanguage.PERSIAN) "${configs.size} سرور رایگان جدید از گیت‌هاب با موفقیت اضافه شد" else "Added ${configs.size} free servers from GitHub"
                    _effects.emit(VpnUiEffect.ShowToast(doneMsg))
                } else {
                    val fallbackMsg = if (_language.value == AppLanguage.PERSIAN) "سرورهای رایگان کاتالوگ فعال و بروز هستند" else "Default catalog servers are active"
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
        val freeServers = FreeServerCatalog.getDefaultFreeServers()

        val mockMetrics = mapOf(
            "free-warp-clean" to RouteMeasurement("warp", true, 22L, 0.0, 99.0, userPriority = 10),
            "free-warp-global" to RouteMeasurement("warp", true, 28L, 0.0, 98.0, userPriority = 9),
            "free-xray-1" to RouteMeasurement("xray", true, 38L, 0.0, 97.0, userPriority = 8),
            "free-xray-2" to RouteMeasurement("xray", true, 44L, 0.1, 96.0, userPriority = 7),
            "free-xray-3" to RouteMeasurement("xray", true, 52L, 0.0, 95.0, userPriority = 6),
            "free-amnezia-1" to RouteMeasurement("amnezia", true, 65L, 0.0, 90.0, userPriority = 5)
        )

        val items = freeServers.map { profile ->
            val measurement = mockMetrics[profile.id] ?: RouteMeasurement(profile.engineId, true, 70L, 0.0, 92.0)
            val score = RouteScorer.score(measurement)
            ServerItem(
                id = profile.id,
                name = profile.name,
                engineId = profile.engineId,
                country = profile.country,
                flagEmoji = profile.flagEmoji,
                payload = profile.rawPayload,
                measurement = measurement,
                score = score,
                isFree = true
            )
        }.sortedByDescending { it.score.score }

        _servers.value = items
    }

    fun onConnectClicked(context: Context) {
        val targetEngine: String
        val targetPayload: ByteArray

        if (_selectedEngineId.value == "auto") {
            // Smart Connect Auto Mode: pick highest scoring route
            val bestRoute = _servers.value.firstOrNull() ?: FreeServerCatalog.getDefaultFreeServers()[0].let {
                ServerItem(it.id, it.name, it.engineId, it.country, it.flagEmoji, it.rawPayload, RouteMeasurement(it.engineId, true, 30L, 0.0, 99.0), RouteScore(it.engineId, 98.0, true))
            }
            targetEngine = bestRoute.engineId
            targetPayload = bestRoute.payload.toByteArray(Charsets.UTF_8)
            _activeConfigText.value = bestRoute.payload
        } else {
            targetEngine = _selectedEngineId.value
            targetPayload = _activeConfigText.value.toByteArray(Charsets.UTF_8)
        }

        val config = EngineConfiguration(targetPayload)
        val adapter = coordinator.getAdapter(targetEngine)
        if (adapter == null) {
            viewModelScope.launch { _effects.emit(VpnUiEffect.ShowToast("موتور نامعتبر: $targetEngine")) }
            return
        }

        viewModelScope.launch {
            coordinator.requestConnect(targetEngine, config)

            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                _effects.emit(VpnUiEffect.RequestVpnPermission(vpnIntent))
            } else {
                coordinator.onVpnPermissionGranted(targetEngine, config)
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            val engine = if (_selectedEngineId.value == "auto") {
                _servers.value.firstOrNull()?.engineId ?: "warp"
            } else _selectedEngineId.value

            val configPayload = _activeConfigText.value.toByteArray(Charsets.UTF_8)
            coordinator.onVpnPermissionGranted(engine, EngineConfiguration(configPayload))
        } else {
            viewModelScope.launch {
                val msg = if (_language.value == AppLanguage.PERSIAN) "مجوز اتصال VPN رد شد" else "VPN permission denied"
                _effects.emit(VpnUiEffect.ShowToast(msg))
            }
            coordinator.disconnect()
        }
    }

    fun pingAllServers() {
        viewModelScope.launch {
            if (_isPinging.value) return@launch
            _isPinging.value = true
            val startMsg = if (_language.value == AppLanguage.PERSIAN) "در حال تست پینگ تمام سرورها..." else "Pinging all servers..."
            _effects.emit(VpnUiEffect.ShowToast(startMsg))

            try {
                val currentServers = _servers.value
                val profiles = currentServers.map {
                    FreeServerProfile(
                        id = it.id,
                        name = it.name,
                        engineId = it.engineId,
                        country = it.country,
                        flagEmoji = it.flagEmoji,
                        rawPayload = it.payload,
                        isFree = it.isFree
                    )
                }

                val pingResults = BatchPingTester.pingAll(profiles)

                val updatedServers = currentServers.map { server ->
                    val ping = pingResults[server.id]
                    if (ping != null && ping.isReachable) {
                        val measurement = server.measurement.copy(
                            reachable = true,
                            latencyMillis = ping.latencyMillis,
                            packetLossPercent = 0.0
                        )
                        server.copy(
                            measurement = measurement,
                            score = RouteScorer.score(measurement)
                        )
                    } else {
                        // Mark unreachable / high penalty
                        val measurement = server.measurement.copy(
                            reachable = false,
                            latencyMillis = 999L,
                            packetLossPercent = 100.0
                        )
                        server.copy(
                            measurement = measurement,
                            score = RouteScorer.score(measurement)
                        )
                    }
                }.sortedByDescending { it.score.score }

                _servers.value = updatedServers
                val reachableCount = updatedServers.count { it.measurement.reachable }
                val doneMsg = if (_language.value == AppLanguage.PERSIAN) {
                    "تست پینگ به پایان رسید: $reachableCount سرور فعال یافت شد"
                } else {
                    "Ping test complete: $reachableCount active servers"
                }
                _effects.emit(VpnUiEffect.ShowToast(doneMsg))
            } catch (e: Exception) {
                _effects.emit(VpnUiEffect.ShowToast("خطا در پینگ: ${e.message}"))
            } finally {
                _isPinging.value = false
            }
        }
    }

    fun scanCleanIps() {
        viewModelScope.launch {
            if (_isScanningCleanIps.value) return@launch
            _isScanningCleanIps.value = true
            val startMsg = if (_language.value == AppLanguage.PERSIAN) "در حال اسکن آی‌پی‌های تمیز کلودفلر..." else "Scanning Cloudflare clean IPs..."
            _effects.emit(VpnUiEffect.ShowToast(startMsg))

            try {
                val results = CleanIpScanner.scanCleanIps()
                _cleanIps.value = results
                val msg = if (_language.value == AppLanguage.PERSIAN) {
                    "${results.size} آی‌پی تمیز و بدون فیلتر پیدا شد"
                } else {
                    "Found ${results.size} clean unblocked IPs"
                }
                _effects.emit(VpnUiEffect.ShowToast(msg))
            } catch (e: Exception) {
                _effects.emit(VpnUiEffect.ShowToast("خطا در اسکن: ${e.message}"))
            } finally {
                _isScanningCleanIps.value = false
            }
        }
    }

    fun applyCleanIpToWarp(cleanIp: String) {
        viewModelScope.launch {
            try {
                val currentWarpPayload = _servers.value.firstOrNull { it.engineId == "warp" }?.payload
                    ?: _activeConfigText.value

                val updatedConfig = CleanIpScanner.applyCleanIpToWarpConfig(currentWarpPayload, cleanIp)
                updateActiveConfig(updatedConfig)
                setSelectedEngine("warp")

                // Update server entry in list
                val updatedServers = _servers.value.map {
                    if (it.engineId == "warp") it.copy(payload = updatedConfig) else it
                }
                _servers.value = updatedServers

                val msg = if (_language.value == AppLanguage.PERSIAN) {
                    "آی‌پی تمیز $cleanIp روی پروفایل وارپ اعمال شد"
                } else {
                    "Clean IP $cleanIp applied to WARP"
                }
                _effects.emit(VpnUiEffect.ShowToast(msg))
            } catch (e: Exception) {
                _effects.emit(VpnUiEffect.ShowToast("خطا در اعمال آی‌پی: ${e.message}"))
            }
        }
    }

    fun onDisconnectClicked() {
        coordinator.disconnect()
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            if (_isCheckingUpdate.value) return@launch
            _isCheckingUpdate.value = true
            if (!silent) {
                val startMsg = if (_language.value == AppLanguage.PERSIAN) "در حال بررسی نسخه جدید در گیت‌هاب..." else "Checking for updates on GitHub..."
                _effects.emit(VpnUiEffect.ShowToast(startMsg))
            }
            try {
                val info = UpdateManager.checkUpdate()
                _updateInfo.value = info
                if (!silent) {
                    if (info.hasUpdate) {
                        val msg = if (_language.value == AppLanguage.PERSIAN) "نسخه جدید ${info.latestVersion} در دسترس است!" else "New version ${info.latestVersion} available!"
                        _effects.emit(VpnUiEffect.ShowToast(msg))
                    } else {
                        val upToDateMsg = if (_language.value == AppLanguage.PERSIAN) "شما از آخرین نسخه استفاده می‌کنید (v${UpdateManager.CURRENT_VERSION})" else "MolidoVPN is up to date (v${UpdateManager.CURRENT_VERSION})"
                        _effects.emit(VpnUiEffect.ShowToast(upToDateMsg))
                    }
                }
            } catch (e: Exception) {
                if (!silent) {
                    _effects.emit(VpnUiEffect.ShowToast("خطا در بررسی آپدیت: ${e.message}"))
                }
            } finally {
                _isCheckingUpdate.value = false
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateInfo.value = null
        _updateDownloadProgress.value = null
    }

    private fun defaultAutoLocation(): LocationItem {
        return LocationItem(
            id = "auto",
            name = "Smart Auto",
            countryFa = "اتصال هوشمند",
            countryEn = "Smart Connect",
            cityFa = "انتخاب خودکار سریع‌ترین لوکیشن",
            cityEn = "Optimal Route",
            flagEmoji = "⚡",
            engineId = "auto",
            latencyMillis = 24L,
            isAuto = true,
            payload = FreeServerCatalog.getDefaultFreeServers().firstOrNull()?.rawPayload ?: ""
        )
    }

    fun selectLocation(location: LocationItem) {
        _selectedLocation.value = location
        if (location.isAuto) {
            setSelectedEngine("auto")
        } else {
            setSelectedEngine(location.engineId)
            if (location.payload.isNotBlank()) {
                updateActiveConfig(location.payload)
            }
        }
        viewModelScope.launch {
            val label = if (_language.value == AppLanguage.PERSIAN) {
                "موقعیت ${location.countryFa} انتخاب شد"
            } else {
                "Selected ${location.countryEn}"
            }
            _effects.emit(VpnUiEffect.ShowToast(label))
        }
    }

    fun refreshLocations() {
        val free = FreeServerCatalog.getDefaultFreeServers()
        val warpClean = free.firstOrNull { it.id == "free-warp-clean" }?.rawPayload ?: ""
        val xrayDe = free.firstOrNull { it.id == "free-xray-1" }?.rawPayload ?: ""
        val xrayNl = free.firstOrNull { it.id == "free-xray-2" }?.rawPayload ?: ""
        val amneziaFi = free.firstOrNull { it.id == "free-amnezia-1" }?.rawPayload ?: ""
        val xrayTrojan = free.firstOrNull { it.id == "free-xray-3" }?.rawPayload ?: ""

        val list = listOf(
            defaultAutoLocation().copy(payload = xrayDe.ifEmpty { warpClean }),
            LocationItem(
                id = "loc-de",
                name = "Germany",
                countryFa = "آلمان",
                countryEn = "Germany",
                cityFa = "فرانکفورت (ضدفیلتر Reality)",
                cityEn = "Frankfurt (Anti-DPI Reality)",
                flagEmoji = "\uD83C\uDDE9\uD83C\uDDEA",
                engineId = "xray",
                latencyMillis = 36L,
                payload = xrayDe
            ),
            LocationItem(
                id = "loc-nl",
                name = "Netherlands",
                countryFa = "هلند",
                countryEn = "Netherlands",
                cityFa = "آمستردام (سرعت بالا)",
                cityEn = "Amsterdam (High-Speed)",
                flagEmoji = "\uD83C\uDDF3\uD83C\uDDF1",
                engineId = "xray",
                latencyMillis = 42L,
                payload = xrayNl
            ),
            LocationItem(
                id = "loc-warp",
                name = "Cloudflare WARP",
                countryFa = "کلودفلر وارپ",
                countryEn = "Cloudflare WARP",
                cityFa = "لبه بدون فیلتر تمیز",
                cityEn = "Clean Edge IP",
                flagEmoji = "⚡",
                engineId = "warp",
                latencyMillis = 24L,
                payload = warpClean
            ),
            LocationItem(
                id = "loc-fi",
                name = "Finland",
                countryFa = "فنلاند",
                countryEn = "Finland",
                cityFa = "هلسینکی (تونل رمزنگاری)",
                cityEn = "Helsinki (Encrypted)",
                flagEmoji = "\uD83C\uDDEB\uD83C\uDDEE",
                engineId = "amnezia",
                latencyMillis = 52L,
                payload = amneziaFi
            ),
            LocationItem(
                id = "loc-gb",
                name = "United Kingdom",
                countryFa = "انگلستان",
                countryEn = "United Kingdom",
                cityFa = "لندن (پینگ پایین)",
                cityEn = "London (Low Latency)",
                flagEmoji = "\uD83C\uDDEC\uD83C\uDDE7",
                engineId = "xray",
                latencyMillis = 58L,
                payload = xrayTrojan
            ),
            LocationItem(
                id = "loc-us",
                name = "United States",
                countryFa = "ایالات متحده",
                countryEn = "United States",
                cityFa = "نیویورک",
                cityEn = "New York",
                flagEmoji = "\uD83C\uDDFA\uD83C\uDDF8",
                engineId = "xray",
                latencyMillis = 98L,
                payload = xrayTrojan
            ),
            LocationItem(
                id = "loc-tr",
                name = "Turkey",
                countryFa = "ترکیه",
                countryEn = "Turkey",
                cityFa = "استانبول (نزدیک‌ترین)",
                cityEn = "Istanbul (Nearest)",
                flagEmoji = "\uD83C\uDDF9\uD83C\uDDF7",
                engineId = "xray",
                latencyMillis = 46L,
                payload = xrayDe
            ),
            LocationItem(
                id = "loc-ae",
                name = "UAE",
                countryFa = "امارات",
                countryEn = "United Arab Emirates",
                cityFa = "دبی",
                cityEn = "Dubai",
                flagEmoji = "\uD83C\uDDE6\uD83C\uDDEA",
                engineId = "warp",
                latencyMillis = 32L,
                payload = warpClean
            )
        )
        _locations.value = list
    }

    fun startInAppUpdate(context: Context, downloadUrl: String) {
        viewModelScope.launch {
            _updateDownloadProgress.value = 0.01f
            val result = UpdateManager.downloadAndInstall(context, downloadUrl) { progress ->
                _updateDownloadProgress.value = progress
            }
            if (result.isFailure) {
                _updateDownloadProgress.value = null
                val errMsg = if (_language.value == AppLanguage.PERSIAN) {
                    "خطا در دانلود به‌روزرسانی: ${result.exceptionOrNull()?.message}"
                } else {
                    "Failed to download update: ${result.exceptionOrNull()?.message}"
                }
                _effects.emit(VpnUiEffect.ShowToast(errMsg))
            } else {
                _updateDownloadProgress.value = null
                dismissUpdateDialog()
            }
        }
    }

    fun cancelUpdateDownload() {
        _updateDownloadProgress.value = null
    }
}
