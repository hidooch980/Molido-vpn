package com.molido.vpn

import android.app.Activity
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.launch
import com.molido.vpn.engine.ConnectionDiagnostics
import com.molido.vpn.engine.ConnectionState
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
                MainScreen(viewModel = viewModel)
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

    MaterialTheme(
        colorScheme = darkColors,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: VpnViewModel) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val selectedEngineId by viewModel.selectedEngineId.collectAsState()
    val servers by viewModel.servers.collectAsState()
    val wireGuardConfigText by viewModel.wireGuardConfigText.collectAsState()

    var showConfigEditor by remember { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf("home") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF111C31),
                drawerContentColor = Color(0xFFF1F5F9)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MolidoVPN AI", fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Color(0xFF38BDF8))
                    Text("Secure • Fast • Verified", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
                    Spacer(modifier = Modifier.height(14.dp))
                    listOf(
                        "home" to "Home",
                        "servers" to "Servers & Countries",
                        "statistics" to "Statistics",
                        "security" to "Security",
                        "settings" to "Settings & Protocols",
                        "about" to "About"
                    ).forEach { (page, label) ->
                        NavigationDrawerItem(
                            label = { Text(label) },
                            selected = selectedPage == page,
                            onClick = {
                                selectedPage = page
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                        }
                    },
                    title = {
                        Column {
                            Text("MolidoVPN AI", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(
                                when (selectedPage) {
                                    "servers" -> "Servers & Countries"
                                    "statistics" -> "Connection Statistics"
                                    "security" -> "Security & Privacy"
                                    "settings" -> "Settings & Protocols"
                                    "about" -> "About MolidoVPN AI"
                                    else -> "Verified VPN Connectivity"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        if (selectedPage == "home" || selectedPage == "servers") {
                            IconButton(onClick = { viewModel.refreshServers() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh verified servers")
                            }
                        }
                        if (selectedPage == "home" || selectedPage == "settings") {
                            IconButton(onClick = { showConfigEditor = !showConfigEditor }) {
                                Icon(Icons.Default.Settings, contentDescription = "WireGuard profile settings")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            when (selectedPage) {
                "home" -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        StatusCard(
                            connectionState = connectionState,
                            selectedEngine = selectedEngineId,
                            onConnect = { viewModel.onConnectClicked(context) },
                            onDisconnect = { viewModel.onDisconnectClicked() }
                        )
                    }
                    item { DiagnosticsCard(diagnostics = diagnostics, connectionState = connectionState) }
                    item { EngineSelector(selectedEngine = selectedEngineId, onSelect = { viewModel.setSelectedEngine(it) }) }
                    if (showConfigEditor) {
                        item {
                            ConfigEditorCard(
                                configText = wireGuardConfigText,
                                onConfigChanged = { viewModel.updateWireGuardConfig(it) }
                            )
                        }
                    }
                    if (servers.isEmpty()) {
                        item {
                            Text(
                                "No verified server profiles are configured. Import your own WireGuard .conf profile in Settings to connect.",
                                color = Color(0xFFF59E0B),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(servers) { server -> ServerRouteCard(server = server) }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }

                "servers" -> Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Verified server inventory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (servers.isEmpty()) "No real server endpoints are configured yet. MolidoVPN will not invent countries, latency, or server availability."
                        else "${servers.size} verified server entries",
                        color = Color(0xFF94A3B8)
                    )
                    if (servers.isNotEmpty()) servers.forEach { ServerRouteCard(it) }
                }

                "statistics" -> Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DiagnosticsCard(diagnostics = diagnostics, connectionState = connectionState)
                    Text(
                        "Live throughput history and long-term charts will appear only when real backend counters and sampling are implemented.",
                        color = Color(0xFF94A3B8)
                    )
                }

                "security" -> Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Security verification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Public egress IP and approximate location are checked over HTTPS after the WireGuard backend starts.")
                    Text("DNS leak, IPv6 leak, Android lockdown/Kill Switch, network handover, and process-death tests are not yet device-verified.", color = Color(0xFFF59E0B))
                    Text("The location estimate is based on the VPN exit IP, not GPS. The IP lookup provider receives that VPN egress IP.", color = Color(0xFF94A3B8))
                }

                "settings" -> Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    EngineSelector(selectedEngine = selectedEngineId, onSelect = { viewModel.setSelectedEngine(it) })
                    ConfigEditorCard(configText = wireGuardConfigText, onConfigChanged = { viewModel.updateWireGuardConfig(it) })
                }

                else -> Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("MolidoVPN AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Android VPN client using the official WireGuard userspace backend.")
                    Text("Only real, verified connection information is displayed. No mock server list or simulated IP is used.", color = Color(0xFF94A3B8))
                    Text("Privacy data flows: docs/privacy-data-flows.md", color = Color(0xFF38BDF8))
                }
            }
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
                            if (isConnected) "Disconnect" else "Connect ($selectedEngine)",
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
    val approximateLocation = listOfNotNull(
        diagnostics.publicEgressCity,
        diagnostics.publicEgressRegion,
        diagnostics.publicEgressCountry
    ).distinct().joinToString(", ").ifBlank { "Not available" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Connection Diagnostics", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DiagnosticItem("Egress check", if (connectionState is ConnectionState.Connected) "Passed" else "Pending")
                DiagnosticItem("Latency", diagnostics.lastLatencyMillis?.let { "$it ms" } ?: "—")
                DiagnosticItem("Traffic", "${diagnostics.bytesIn / 1024}k / ${diagnostics.bytesOut / 1024}k")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DiagnosticItem("Public IP", diagnostics.publicEgressIp ?: "Not verified")
                DiagnosticItem("Approx. location", approximateLocation)
            }
            Text(
                "Location is estimated from the VPN exit IP, not GPS.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )
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

@Composable
fun EngineSelector(selectedEngine: String, onSelect: (String) -> Unit) {
    Column {
        Text("Active Engine Family", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedEngine == "wireguard",
                onClick = { onSelect("wireguard") },
                label = { Text("WireGuard (Official)") }
            )
            FilterChip(
                selected = selectedEngine == "xray",
                onClick = { /* Planned */ },
                enabled = false,
                label = { Text("Xray-core (Planned)") }
            )
            FilterChip(
                selected = selectedEngine == "amnezia",
                onClick = { /* Planned */ },
                enabled = false,
                label = { Text("AmneziaWG (Planned)") }
            )
        }
    }
}

@Composable
fun ServerRouteCard(server: ServerItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${server.measurement.latencyMillis}ms • Loss ${server.measurement.packetLossPercent}% • Stab ${server.measurement.stabilityPercent?.toInt()}%",
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

@Composable
fun ConfigEditorCard(configText: String, onConfigChanged: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("WireGuard Profile (.conf)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = configText,
                onValueChange = onConfigChanged,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 6,
                maxLines = 10
            )
        }
    }
}
