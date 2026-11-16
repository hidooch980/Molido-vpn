package com.molido.vpn

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molido.vpn.dns.DohDnsResolver
import com.molido.vpn.engine.ConnectionDiagnostics
import com.molido.vpn.engine.ConnectionState
import com.molido.vpn.policy.KillSwitchManager
import com.molido.vpn.ui.AppLanguage
import com.molido.vpn.ui.AppStrings
import com.molido.vpn.ui.Localization
import com.molido.vpn.ui.ServerItem
import com.molido.vpn.ui.VpnUiEffect
import com.molido.vpn.ui.VpnViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {

    private val viewModel: VpnViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MolidoVpnTheme {
                val language by viewModel.language.collectAsState()
                val layoutDirection = if (language == AppLanguage.PERSIAN) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    MainAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MolidoVpnTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = Color(0xFF38BDF8),
        onPrimary = Color(0xFF0F172A),
        surface = Color(0xFF1E293B),
        onSurface = Color(0xFFF8FAFC),
        background = Color(0xFF0F172A),
        onBackground = Color(0xFFF1F5F9),
        error = Color(0xFFEF4444)
    )

    MaterialTheme(colorScheme = darkColors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: VpnViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    val language by viewModel.language.collectAsState()
    val strings = Localization.get(language)

    val connectionState by viewModel.connectionState.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val selectedEngineId by viewModel.selectedEngineId.collectAsState()
    val servers by viewModel.servers.collectAsState()
    val activeConfigText by viewModel.activeConfigText.collectAsState()
    val killSwitchEnabled by viewModel.killSwitchEnabled.collectAsState()
    val selectedDns by viewModel.selectedDns.collectAsState()
    val splitApps by viewModel.splitApps.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isFetchingCommunity by viewModel.isFetchingCommunity.collectAsState()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onPermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is VpnUiEffect.RequestVpnPermission -> vpnPermissionLauncher.launch(effect.intent)
                is VpnUiEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(strings.appName, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            strings.appSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Quick language toggle
                    IconButton(onClick = {
                        val nextLang = if (language == AppLanguage.PERSIAN) AppLanguage.ENGLISH else AppLanguage.PERSIAN
                        viewModel.setLanguage(nextLang)
                    }) {
                        Icon(Icons.Default.Language, contentDescription = "Language", tint = Color(0xFF38BDF8))
                    }
                    IconButton(onClick = { viewModel.refreshServers() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.VpnLock, contentDescription = strings.tabConnect) },
                    label = { Text(strings.tabConnect) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Storage, contentDescription = strings.tabServers) },
                    label = { Text(strings.tabServers) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Security, contentDescription = strings.tabSecurity) },
                    label = { Text(strings.tabSecurity) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> ConnectTab(
                    strings = strings,
                    connectionState = connectionState,
                    diagnostics = diagnostics,
                    selectedEngine = selectedEngineId,
                    onSelectEngine = { viewModel.setSelectedEngine(it) },
                    onConnect = { viewModel.onConnectClicked(context) },
                    onDisconnect = { viewModel.onDisconnectClicked() }
                )
                1 -> ServersTab(
                    strings = strings,
                    servers = servers,
                    isFetching = isFetchingCommunity,
                    onUpdateFromGithub = { viewModel.updateFromGithubCommunity() },
                    onSelectServer = { viewModel.selectServer(it) },
                    onImportClipboard = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clip.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (text.isNotBlank()) {
                            viewModel.importFromRawText(text)
                        } else {
                            Toast.makeText(context, if (language == AppLanguage.PERSIAN) "کلیپ‌بورد خالی است" else "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                2 -> SecuritySettingsTab(
                    strings = strings,
                    currentLanguage = language,
                    onSelectLanguage = { viewModel.setLanguage(it) },
                    killSwitch = killSwitchEnabled,
                    onToggleKillSwitch = { viewModel.toggleKillSwitch(it) },
                    onOpenAndroidSettings = {
                        try {
                            context.startActivity(KillSwitchManager.createVpnSettingsIntent())
                        } catch (_: Exception) {
                            Toast.makeText(context, "Cannot open VPN settings", Toast.LENGTH_SHORT).show()
                        }
                    },
                    selectedDns = selectedDns,
                    onSelectDns = { viewModel.setDnsProvider(it) },
                    splitApps = splitApps,
                    installedApps = installedApps,
                    onToggleApp = { viewModel.toggleAppExclusion(it) },
                    activeConfigText = activeConfigText,
                    onUpdateConfig = { viewModel.updateActiveConfig(it) }
                )
            }
        }
    }
}

@Composable
fun ConnectTab(
    strings: AppStrings,
    connectionState: ConnectionState,
    diagnostics: ConnectionDiagnostics,
    selectedEngine: String,
    onSelectEngine: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            StatusCard(
                strings = strings,
                connectionState = connectionState,
                selectedEngine = selectedEngine,
                onConnect = onConnect,
                onDisconnect = onDisconnect
            )
        }

        item {
            DiagnosticsCard(strings = strings, diagnostics = diagnostics, connectionState = connectionState)
        }

        item {
            SmartAutoEngineSelector(
                strings = strings,
                selectedEngine = selectedEngine,
                onSelect = onSelectEngine
            )
        }
    }
}

@Composable
fun SmartAutoEngineSelector(
    strings: AppStrings,
    selectedEngine: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(strings.enginesTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // Smart Connect Card / Chip
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect("auto") },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selectedEngine == "auto") Color(0xFF0369A1).copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
            ),
            border = if (selectedEngine == "auto") androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF38BDF8)) else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(strings.autoConnect, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text(strings.autoConnectDesc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                    }
                }
                if (selectedEngine == "auto") {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Protocol Specific Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedEngine == "warp",
                onClick = { onSelect("warp") },
                label = { Text("WARP (Free)") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedEngine == "amnezia",
                onClick = { onSelect("amnezia") },
                label = { Text("AmneziaWG") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedEngine == "xray",
                onClick = { onSelect("xray") },
                label = { Text("Xray / VLESS") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedEngine == "wireguard",
                onClick = { onSelect("wireguard") },
                label = { Text("WireGuard") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ServersTab(
    strings: AppStrings,
    servers: List<ServerItem>,
    isFetching: Boolean,
    onUpdateFromGithub: () -> Unit,
    onSelectServer: (ServerItem) -> Unit,
    onImportClipboard: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Update from GitHub community button
                Button(
                    onClick = onUpdateFromGithub,
                    enabled = !isFetching,
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(strings.updatingGithub, fontSize = 11.sp)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.updateFromGithub, fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = onImportClipboard,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.clipboardButton, fontSize = 12.sp)
                }
            }
        }

        item {
            Text(
                strings.freeServersTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(servers) { server ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectServer(server) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(server.flagEmoji, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(server.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text("FREE", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                }
                            }
                            Text(
                                "${server.engineId.uppercase()} • ${server.measurement.latencyMillis} ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${strings.scoreLabel} ${server.score.score.toInt()}",
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecuritySettingsTab(
    strings: AppStrings,
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    killSwitch: Boolean,
    onToggleKillSwitch: (Boolean) -> Unit,
    onOpenAndroidSettings: () -> Unit,
    selectedDns: String,
    onSelectDns: (String) -> Unit,
    splitApps: Set<String>,
    installedApps: List<com.molido.vpn.policy.AppInfo>,
    onToggleApp: (String) -> Unit,
    activeConfigText: String,
    onUpdateConfig: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Language Switcher Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(strings.languageLabel, fontWeight = FontWeight.Bold)
                        Text(currentLanguage.displayName, style = MaterialTheme.typography.bodySmall, color = Color(0xFF38BDF8))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = currentLanguage == AppLanguage.PERSIAN,
                            onClick = { onSelectLanguage(AppLanguage.PERSIAN) },
                            label = { Text("فارسی") }
                        )
                        FilterChip(
                            selected = currentLanguage == AppLanguage.ENGLISH,
                            onClick = { onSelectLanguage(AppLanguage.ENGLISH) },
                            label = { Text("English") }
                        )
                    }
                }
            }
        }

        // Kill Switch Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.killSwitchTitle, fontWeight = FontWeight.Bold)
                            Text(
                                strings.killSwitchDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Switch(checked = killSwitch, onCheckedChange = onToggleKillSwitch)
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onOpenAndroidSettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.alwaysOnButton, fontSize = 12.sp)
                    }
                }
            }
        }

        // Anti-Censorship DNS Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.dohTitle, fontWeight = FontWeight.Bold)
                    Text(strings.dohDesc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                    Spacer(Modifier.height(10.dp))
                    for (provider in DohDnsResolver.PROVIDERS) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDns(provider.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (selectedDns == provider.id) Icons.Default.CheckCircle else Icons.Default.Dns,
                                contentDescription = null,
                                tint = if (selectedDns == provider.id) Color(0xFF38BDF8) else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(provider.name, fontWeight = FontWeight.Medium)
                                Text(provider.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            }
        }

        // Split Tunneling Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.splitTunnelTitle, fontWeight = FontWeight.Bold)
                    Text(strings.splitTunnelDesc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                    Spacer(Modifier.height(8.dp))
                    Text("${splitApps.size} apps bypassed", color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        // Raw Config Editor
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.activeConfigTitle, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = activeConfigText,
                        onValueChange = onUpdateConfig,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 5,
                        maxLines = 8
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun StatusCard(
    strings: AppStrings,
    connectionState: ConnectionState,
    selectedEngine: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusBadge(strings = strings, connectionState = connectionState)

            Spacer(modifier = Modifier.height(20.dp))

            val isConnected = connectionState is ConnectionState.Connected
            val isBusy = connectionState is ConnectionState.Preparing ||
                connectionState is ConnectionState.Connecting ||
                connectionState is ConnectionState.Verifying

            val buttonColor = when {
                isConnected -> Color(0xFFEF4444)
                isBusy -> Color(0xFFF59E0B)
                else -> MaterialTheme.colorScheme.primary
            }

            Button(
                onClick = {
                    if (isConnected || isBusy) onDisconnect() else onConnect()
                },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            when (connectionState) {
                                is ConnectionState.Preparing -> strings.connecting
                                is ConnectionState.Connecting -> strings.connecting
                                is ConnectionState.Verifying -> strings.verifying
                                else -> strings.connecting
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    } else {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        val engineLabel = if (selectedEngine == "auto") "AUTO" else selectedEngine.uppercase()
                        Text(
                            if (isConnected) strings.disconnectAction else "${strings.connectAction} ($engineLabel)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(strings: AppStrings, connectionState: ConnectionState) {
    val (badgeBg, badgeText, badgeIcon) = when (connectionState) {
        is ConnectionState.Connected -> Triple(
            Color(0xFF10B981),
            strings.connectedVerified,
            Icons.Default.CheckCircle
        )
        is ConnectionState.Verifying -> Triple(
            Color(0xFFF59E0B),
            strings.verifying,
            Icons.Default.Lock
        )
        is ConnectionState.Connecting -> Triple(
            Color(0xFF0284C7),
            "${strings.connecting} (${connectionState.engineId})",
            Icons.Default.Speed
        )
        is ConnectionState.Preparing -> Triple(
            Color(0xFF64748B),
            strings.connecting,
            Icons.Default.Info
        )
        is ConnectionState.Recovering -> Triple(
            Color(0xFFF59E0B),
            strings.verifying,
            Icons.Default.Warning
        )
        is ConnectionState.Failed -> Triple(
            Color(0xFFEF4444),
            "${strings.failedPrefix}${connectionState.reasonCode}",
            Icons.Default.Warning
        )
        is ConnectionState.Idle -> Triple(
            Color(0xFF475569),
            strings.disconnected,
            Icons.Default.PowerSettingsNew
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(badgeBg.copy(alpha = 0.2f))
            .border(1.dp, badgeBg, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = badgeIcon,
                contentDescription = null,
                tint = badgeBg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(badgeText, color = badgeBg, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}

@Composable
fun DiagnosticsCard(strings: AppStrings, diagnostics: ConnectionDiagnostics, connectionState: ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(strings.diagnosticsTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DiagnosticItem(strings.egressVerified, if (connectionState is ConnectionState.Connected) strings.egressVerified else strings.egressPending)
                DiagnosticItem(strings.latencyLabel, "${diagnostics.lastLatencyMillis ?: 0} ms")
                DiagnosticItem("${strings.downloadLabel} / ${strings.uploadLabel}", "${diagnostics.bytesIn / 1024}k / ${diagnostics.bytesOut / 1024}k")
            }
        }
    }
}

@Composable
fun DiagnosticItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
