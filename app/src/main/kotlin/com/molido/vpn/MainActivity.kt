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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.molido.vpn.dns.DohDnsResolver
import com.molido.vpn.engine.ConnectionDiagnostics
import com.molido.vpn.engine.ConnectionState
import com.molido.vpn.policy.KillSwitchManager
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
                MainAppScreen(viewModel = viewModel)
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
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Connect, 1: Servers, 2: Settings

    val connectionState by viewModel.connectionState.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val selectedEngineId by viewModel.selectedEngineId.collectAsState()
    val servers by viewModel.servers.collectAsState()
    val activeConfigText by viewModel.activeConfigText.collectAsState()
    val killSwitchEnabled by viewModel.killSwitchEnabled.collectAsState()
    val selectedDns by viewModel.selectedDns.collectAsState()
    val splitApps by viewModel.splitApps.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }

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
                        Text("MolidoVPN AI", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "Multi-Engine • Anti-Censorship Core",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
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
                    icon = { Icon(Icons.Default.VpnLock, contentDescription = "Connect") },
                    label = { Text("Connect") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Storage, contentDescription = "Free Servers") },
                    label = { Text("Servers") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Security") },
                    label = { Text("Security") }
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
                    connectionState = connectionState,
                    diagnostics = diagnostics,
                    selectedEngine = selectedEngineId,
                    onSelectEngine = { viewModel.setSelectedEngine(it) },
                    onConnect = { viewModel.onConnectClicked(context) },
                    onDisconnect = { viewModel.onDisconnectClicked() }
                )
                1 -> ServersTab(
                    servers = servers,
                    onSelectServer = { viewModel.selectServer(it) },
                    onImportClipboard = {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = clip.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (text.isNotBlank()) {
                            viewModel.importFromRawText(text)
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenSubscription = { showSubscriptionDialog = true }
                )
                2 -> SecuritySettingsTab(
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

    if (showSubscriptionDialog) {
        var subUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSubscriptionDialog = false },
            title = { Text("Import Subscription") },
            text = {
                OutlinedTextField(
                    value = subUrl,
                    onValueChange = { subUrl = it },
                    placeholder = { Text("https://example.com/sub") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (subUrl.isNotBlank()) {
                        viewModel.importFromSubscription(subUrl)
                        showSubscriptionDialog = false
                    }
                }) {
                    Text("Fetch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubscriptionDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ConnectTab(
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
                connectionState = connectionState,
                selectedEngine = selectedEngine,
                onConnect = onConnect,
                onDisconnect = onDisconnect
            )
        }

        item {
            DiagnosticsCard(diagnostics = diagnostics, connectionState = connectionState)
        }

        item {
            MultiEngineSelector(
                selectedEngine = selectedEngine,
                onSelect = onSelectEngine
            )
        }
    }
}

@Composable
fun ServersTab(
    servers: List<ServerItem>,
    onSelectServer: (ServerItem) -> Unit,
    onImportClipboard: () -> Unit,
    onOpenSubscription: () -> Unit
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
                OutlinedButton(
                    onClick = onImportClipboard,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clipboard")
                }
                OutlinedButton(
                    onClick = onOpenSubscription,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Subscription")
                }
            }
        }

        item {
            Text(
                "Free & Curated Community Servers",
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
                            Text(server.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(
                                "Engine: ${server.engineId.uppercase()} • ${server.measurement.latencyMillis}ms",
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
                            "Score ${server.score.score.toInt()}",
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
                            Text("Kill Switch (Block Leaks)", fontWeight = FontWeight.Bold)
                            Text(
                                "Blocks all non-VPN traffic when tunnel drops",
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
                        Text("Configure Always-on VPN in Android Settings")
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
                    Text("Secure DNS-over-HTTPS (DoH)", fontWeight = FontWeight.Bold)
                    Text("Prevents DNS spoofing and poison attacks", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
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
                    Text("Split Tunneling (Bypass Apps)", fontWeight = FontWeight.Bold)
                    Text("Selected apps will bypass VPN tunnel directly", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                    Spacer(Modifier.height(8.dp))
                    Text("${splitApps.size} apps excluded", color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                    Text("Active Configuration Profile", fontWeight = FontWeight.Bold)
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
fun MultiEngineSelector(selectedEngine: String, onSelect: (String) -> Unit) {
    Column {
        Text("Supported Engines", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedEngine == "warp",
                onClick = { onSelect("warp") },
                label = { Text("WARP (Free)") }
            )
            FilterChip(
                selected = selectedEngine == "wireguard",
                onClick = { onSelect("wireguard") },
                label = { Text("WireGuard") }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedEngine == "amnezia",
                onClick = { onSelect("amnezia") },
                label = { Text("AmneziaWG") }
            )
            FilterChip(
                selected = selectedEngine == "xray",
                onClick = { onSelect("xray") },
                label = { Text("Xray/VLESS") }
            )
        }
    }
}

@Composable
fun StatusCard(
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
            StatusBadge(connectionState = connectionState)

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
                                is ConnectionState.Preparing -> "Preparing..."
                                is ConnectionState.Connecting -> "Connecting..."
                                is ConnectionState.Verifying -> "Verifying Traffic..."
                                else -> "Connecting..."
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    } else {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            if (isConnected) "Disconnect" else "Connect (${selectedEngine.uppercase()})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(connectionState: ConnectionState) {
    val (badgeBg, badgeText, badgeIcon) = when (connectionState) {
        is ConnectionState.Connected -> Triple(
            Color(0xFF10B981),
            "Connected (Traffic Verified)",
            Icons.Default.CheckCircle
        )
        is ConnectionState.Verifying -> Triple(
            Color(0xFFF59E0B),
            "Verifying Egress Traffic...",
            Icons.Default.Lock
        )
        is ConnectionState.Connecting -> Triple(
            Color(0xFF0284C7),
            "Connecting Engine (${connectionState.engineId})...",
            Icons.Default.Speed
        )
        is ConnectionState.Preparing -> Triple(
            Color(0xFF64748B),
            "Preparing Permissions...",
            Icons.Default.Info
        )
        is ConnectionState.Recovering -> Triple(
            Color(0xFFF59E0B),
            "Recovering Connection...",
            Icons.Default.Warning
        )
        is ConnectionState.Failed -> Triple(
            Color(0xFFEF4444),
            "Failed: ${connectionState.reasonCode}",
            Icons.Default.Warning
        )
        is ConnectionState.Idle -> Triple(
            Color(0xFF475569),
            "Disconnected (Fail-closed)",
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
fun DiagnosticsCard(diagnostics: ConnectionDiagnostics, connectionState: ConnectionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Connection Diagnostics", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DiagnosticItem("Egress Verification", if (connectionState is ConnectionState.Connected) "Passed" else "Pending")
                DiagnosticItem("Latency", "${diagnostics.lastLatencyMillis ?: 0} ms")
                DiagnosticItem("Bytes In/Out", "${diagnostics.bytesIn / 1024}k / ${diagnostics.bytesOut / 1024}k")
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
