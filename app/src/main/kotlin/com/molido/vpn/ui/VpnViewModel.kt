package com.molido.vpn.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.molido.vpn.BuildConfig
import com.molido.vpn.data.EncryptedConfigStore
import com.molido.vpn.data.FreeServerCatalog
import com.molido.vpn.data.FreeServerProfile
import com.molido.vpn.data.ServerCountryClassifier
import com.molido.vpn.dns.DohDnsResolver
import com.molido.vpn.dns.DnsRecordType
import com.molido.vpn.dns.VpnNetworkDohClient
import com.molido.vpn.engine.ConnectionCoordinator
import com.molido.vpn.engine.ConnectionDiagnostics
import com.molido.vpn.engine.ConnectionState
import com.molido.vpn.engine.EngineConfiguration
import com.molido.vpn.engine.amnezia.AmneziaWgEngineAdapter
import com.molido.vpn.engine.hysteria.Hysteria2EngineAdapter
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val wireguardAdapter = WireGuardEngineAdapter(application)
    private val amneziaAdapter = AmneziaWgEngineAdapter()
    private val xrayAdapter = XrayEngineAdapter()
    private val warpAdapter = WarpEngineAdapter()
    private val hysteriaAdapter = Hysteria2EngineAdapter()

    val coordinator = ConnectionCoordinator(
        scope = viewModelScope,
        trafficVerifier = NetworkTrafficVerifier(application),
        adapters = mapOf(
            wireguardAdapter.id to wireguardAdapter,
            amneziaAdapter.id to amneziaAdapter,
            xrayAdapter.id to xrayAdapter,
            warpAdapter.id to warpAdapter,
            hysteriaAdapter.id to hysteriaAdapter
        ),
        // GoBackend creates, owns, and tears down its own TUN. Other engines
        // remain blocked until their own complete transport adapters exist.
        // Permit internal device acceptance only in debug builds. Release stays fail-closed
        // until the physical-device acceptance matrix is completed and reviewed.
        onVpnInterfaceRequested = { engineId, _ -> BuildConfig.DEBUG && engineId == "wireguard" },
        onVpnInterfaceTornDown = { }
    )

    val connectionState: StateFlow<ConnectionState> = coordinator.state
    val diagnostics: StateFlow<ConnectionDiagnostics> = coordinator.diagnostics

    private val _effects = MutableSharedFlow<VpnUiEffect>()
    val effects: SharedFlow<VpnUiEffect> = _effects.asSharedFlow()

    private val _language = MutableStateFlow(AppLanguage.PERSIAN)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _selectedEngineId = MutableStateFlow("auto") // Default to Smart Auto Connect
    private var pendingPermissionRequest: Pair<String, EngineConfiguration>? = null
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

    private val _subscriptionUrls = MutableStateFlow(configStore.getSubscriptions())
    val subscriptionUrls: StateFlow<List<String>> = _subscriptionUrls.asStateFlow()
    private val _disabledSubscriptionUrls = MutableStateFlow(configStore.getDisabledSubscriptions())
    val disabledSubscriptionUrls: StateFlow<Set<String>> = _disabledSubscriptionUrls.asStateFlow()

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

    init {
        loadInstalledApps()
        refreshServers()
        checkForUpdates(silent = true)
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
            val bestServer = _servers.value.firstOrNull()
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

    /**
     * Performs an app-owned DNS-over-HTTPS lookup over the active VPN Network.
     * Android system DNS traffic is not intercepted by this helper.
     */
    suspend fun resolveDnsOverHttps(
        hostname: String,
        recordType: DnsRecordType = DnsRecordType.A
    ): ByteArray {
        return VpnNetworkDohClient(
            context = getApplication(),
            providerId = _selectedDns.value
        ).resolve(hostname, recordType)
    }

    fun setDnsProvider(providerId: String) {
        val provider = runCatching { DohDnsResolver.getProvider(providerId) }.getOrNull() ?: return
        _selectedDns.value = provider.id
        configStore.saveSelectedDns(provider.id)
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

    fun addSubscription(rawUrl: String) {
        viewModelScope.launch {
            try {
                val normalized = withContext(Dispatchers.IO) {
                    SubscriptionManager.validateSubscriptionUrl(rawUrl).toExternalForm()
                }
                val updated = (_subscriptionUrls.value + normalized).distinct()
                withContext(Dispatchers.IO) { configStore.saveSubscriptions(updated) }
                _subscriptionUrls.value = updated
                _effects.emit(VpnUiEffect.ShowToast(
                    if (_language.value == AppLanguage.PERSIAN) "اشتراک با موفقیت ذخیره شد" else "Subscription saved"
                ))
            } catch (error: Exception) {
                val reason = error.message ?: "SUBSCRIPTION_URL_INVALID"
                _effects.emit(VpnUiEffect.ShowToast(
                    if (_language.value == AppLanguage.PERSIAN) "آدرس اشتراک معتبر نیست: $reason" else "Invalid subscription URL: $reason"
                ))
            }
        }
    }

    fun removeSubscription(url: String) {
        viewModelScope.launch {
            try {
                val updated = _subscriptionUrls.value.filterNot { it == url }
                withContext(Dispatchers.IO) { configStore.saveSubscriptions(updated) }
                _subscriptionUrls.value = updated
            } catch (error: Exception) {
                val detail = error.message ?: "storage error"
                _effects.emit(VpnUiEffect.ShowToast(
                    if (_language.value == AppLanguage.PERSIAN) "حذف اشتراک ذخیره نشد: $detail"
                    else "Could not save subscription removal: $detail"
                ))
            }
        }
    }

    fun setSubscriptionEnabled(url: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = _disabledSubscriptionUrls.value.toMutableSet().apply {
                    if (enabled) remove(url) else add(url)
                }
                withContext(Dispatchers.IO) { configStore.saveDisabledSubscriptions(updated) }
                _disabledSubscriptionUrls.value = updated
            } catch (error: Exception) {
                _effects.emit(VpnUiEffect.ShowToast(
                    if (_language.value == AppLanguage.PERSIAN) "تغییر وضعیت اشتراک ذخیره نشد"
                    else "Could not save subscription state"
                ))
            }
        }
    }
    fun refreshSubscriptionsNow() {
        val request = androidx.work.OneTimeWorkRequestBuilder<com.molido.vpn.importer.SubscriptionRefreshWorker>()
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()
        androidx.work.WorkManager.getInstance(getApplication()).enqueue(request)
        viewModelScope.launch {
            _effects.emit(VpnUiEffect.ShowToast(
                if (_language.value == AppLanguage.PERSIAN) "به‌روزرسانی اشتراک‌ها در صف اجرا قرار گرفت" else "Subscription refresh queued"
            ))
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
                        // Importing a profile is not a reachability or quality measurement.
                        val measurement = RouteMeasurement(conf.engineId, false, null, null, null)
                        val score = RouteScorer.score(measurement)
                        ServerItem(
                            id = "github-free-$index",
                            name = conf.name,
                            engineId = conf.engineId,
                            country = ServerCountryClassifier.resolve(conf.name).nameEn,
                            flagEmoji = ServerCountryClassifier.resolve(conf.name).flag,
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
                    val fallbackMsg = if (_language.value == AppLanguage.PERSIAN) "هیچ پروفایل قابل‌اتصال تأییدشده‌ای پیدا نشد" else "No verified connectable profile was found"
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
        _servers.value = FreeServerCatalog.getDefaultFreeServers().map { profile ->
            val measurement = RouteMeasurement(profile.engineId, false, null, null, null)
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
        val route = if (selected == "auto") _servers.value.firstOrNull { it.score.eligible } else null
        if (selected == "auto" && route == null) {
            viewModelScope.launch {
                _effects.emit(VpnUiEffect.ShowToast(
                    if (_language.value == AppLanguage.PERSIAN)
                        "هیچ مسیر دارای اندازه‌گیری واقعی برای انتخاب هوشمند موجود نیست"
                    else "No route with real measurements is available for Smart Routing"
                ))
            }
            return
        }

        val engine = route?.engineId ?: selected
        val payload = route?.payload ?: _activeConfigText.value
        if (payload.isBlank()) {
            viewModelScope.launch {
                _effects.emit(VpnUiEffect.ShowToast(
                    if (_language.value == AppLanguage.PERSIAN) "ابتدا پروفایل معتبر VPN را وارد کنید"
                    else "Import a valid VPN profile first"
                ))
            }
            return
        }

        val dnsProvider = DohDnsResolver.getProvider(_selectedDns.value)
        val config = EngineConfiguration(
            opaquePayload = payload.toByteArray(Charsets.UTF_8),
            dnsServers = listOf(dnsProvider.primaryIp, dnsProvider.secondaryIp)
        )
        if (coordinator.getAdapter(engine) == null) {
            viewModelScope.launch { _effects.emit(VpnUiEffect.ShowToast("موتور نامعتبر: $engine")) }
            return
        }
        if (route != null) updateActiveConfig(route.payload)

        pendingPermissionRequest = engine to config
        viewModelScope.launch {
            coordinator.requestConnect(engine, config)
            val vpnIntent = VpnService.prepare(context)
            if (vpnIntent != null) {
                _effects.emit(VpnUiEffect.RequestVpnPermission(vpnIntent))
            } else {
                pendingPermissionRequest = null
                coordinator.onVpnPermissionGranted(engine, config)
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        val pending = pendingPermissionRequest
        pendingPermissionRequest = null
        if (!isGranted) {
            viewModelScope.launch {
                _effects.emit(VpnUiEffect.ShowToast(
                    if (_language.value == AppLanguage.PERSIAN) "مجوز اتصال VPN رد شد" else "VPN permission denied"
                ))
            }
            coordinator.disconnect()
            return
        }

        if (pending == null) {
            viewModelScope.launch {
                _effects.emit(VpnUiEffect.ShowToast(
                    if (_language.value == AppLanguage.PERSIAN)
                        "درخواست اتصال منقضی شده است؛ دوباره تلاش کنید"
                    else "The connection request expired; please try again"
                ))
            }
            coordinator.disconnect()
            return
        }
        coordinator.onVpnPermissionGranted(pending.first, pending.second)
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
                            packetLossPercent = null,
                            stabilityPercent = null
                        )
                        server.copy(
                            measurement = measurement,
                            score = RouteScorer.score(measurement)
                        )
                    } else {
                        // Mark unreachable / high penalty
                        val measurement = server.measurement.copy(
                            reachable = false,
                            latencyMillis = null,
                            packetLossPercent = null,
                            stabilityPercent = null
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
    }
}
