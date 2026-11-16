package com.molido.vpn.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.molido.vpn.updater.AppUpdateInfo
import com.molido.vpn.updater.AppUpdateManager
import com.molido.vpn.updater.UpdateCheckResult
import java.io.File
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
    data class OpenSettings(val intent: Intent) : VpnUiEffect
}

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val updateManager = AppUpdateManager(application)
    private val _updateStatus = MutableStateFlow("Checking for updates…")
    val updateStatus: StateFlow<String> = _updateStatus.asStateFlow()
    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()
    private var downloadedUpdate: File? = null
    private val _updateReady = MutableStateFlow(false)
    val updateReady: StateFlow<Boolean> = _updateReady.asStateFlow()

    private val wireguardAdapter = WireGuardEngineAdapter(application)

    val coordinator = ConnectionCoordinator(
        scope = viewModelScope,
        trafficVerifier = NetworkTrafficVerifier(),
        adapters = mapOf(wireguardAdapter.id to wireguardAdapter)
    )

    val connectionState: StateFlow<ConnectionState> = coordinator.state
    val diagnostics: StateFlow<ConnectionDiagnostics> = coordinator.diagnostics
    init { checkForUpdates() }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateStatus.value = "Checking official MolidoVPN releases…"
            when (val result = updateManager.check()) {
                UpdateCheckResult.UpToDate -> {
                    _availableUpdate.value = null
                    _updateStatus.value = "App and bundled VPN cores are up to date"
                }
                is UpdateCheckResult.Available -> {
                    _availableUpdate.value = result.update
                    _updateStatus.value = "Version ${result.update.versionName} is available; downloading securely…"
                    downloadUpdate()
                }
                is UpdateCheckResult.Failed -> _updateStatus.value = "Update check unavailable: ${result.reason}"
            }
        }
    }

    fun downloadUpdate() {
        val update = _availableUpdate.value ?: return
        viewModelScope.launch {
            _updateStatus.value = "Downloading and verifying update…"
            updateManager.downloadAndVerify(update).onSuccess { apk ->
                downloadedUpdate = apk
                _updateReady.value = true
                _updateStatus.value = "Verified update downloaded. Ready for Android installation."
            }.onFailure { error ->
                downloadedUpdate = null
                _updateReady.value = false
                _updateStatus.value = "Update rejected: ${error.message ?: "verification failed"}"
            }
        }
    }

    fun installUpdate() {
        val apk = downloadedUpdate ?: return
        updateManager.requestInstall(apk).onSuccess {
            _updateStatus.value = "Android installer opened. Confirm installation to continue."
        }.onFailure { error ->
            if (error is AppUpdateManager.InstallPermissionRequired) {
                val app = getApplication<Application>()
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                viewModelScope.launch { _effects.emit(VpnUiEffect.OpenSettings(intent)) }
                _updateStatus.value = "Allow MolidoVPN to install its verified updates, then return here."
            } else {
                _updateStatus.value = "Could not start installer: ${error.message}"
            }
        }
    }


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
