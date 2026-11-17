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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.molido.vpn.ui.LocationItem
import com.molido.vpn.ui.ServerItem
import com.molido.vpn.ui.VpnUiEffect
import com.molido.vpn.ui.VpnViewModel
import com.molido.vpn.update.UpdateInfo
import com.molido.vpn.update.UpdateManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
        primary = Color(0xFF35D6CD),
        onPrimary = Color(0xFF061418),
        secondary = Color(0xFF20B8B2),
        onSecondary = Color(0xFF041214),
        surface = Color(0xFF10242D),
        onSurface = Color(0xFFE8F7F8),
        background = Color(0xFF07131A),
        onBackground = Color(0xFFE8F7F8),
        error = Color(0xFFEF4444)
    )

    MaterialTheme(colorScheme = darkColors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: VpnViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val language by viewModel.language.collectAsState()
    val strings = Localization.get(language)
    val connectionState by viewModel.connectionState.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val selectedEngineId by viewModel.selectedEngineId.collectAsState()
    val servers by viewModel.servers.collectAsState()
    val locations by viewModel.locations.collectAsState()
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    val activeConfigText by viewModel.activeConfigText.collectAsState()
    val killSwitchEnabled by viewModel.killSwitchEnabled.collectAsState()
    val selectedDns by viewModel.selectedDns.collectAsState()
    val splitApps by viewModel.splitApps.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isFetchingCommunity by viewModel.isFetchingCommunity.collectAsState()
    val isPinging by viewModel.isPinging.collectAsState()
    val isScanningCleanIps by viewModel.isScanningCleanIps.collectAsState()
    val cleanIps by viewModel.cleanIps.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateDownloadProgress by viewModel.updateDownloadProgress.collectAsState()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.onPermissionResult(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is VpnUiEffect.RequestVpnPermission -> vpnPermissionLauncher.launch(effect.intent)
                is VpnUiEffect.ShowToast -> Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (updateInfo?.hasUpdate == true) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color(0xFF35D6CD))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.updateAvailable, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column {
                    Text(
                        "${strings.currentVersionLabel}: v${info.currentVersion} ➔ v${info.latestVersion}",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF10B981),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    if (info.releaseNotes.isNotBlank()) {
                        Text(info.releaseNotes, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE2E8F0))
                    }
                    if (updateDownloadProgress != null) {
                        Spacer(Modifier.height(14.dp))
                        val progress = updateDownloadProgress ?: 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF35D6CD),
                            trackColor = Color(0xFF1A3942)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${strings.downloadingUpdate} ${(progress * 100).toInt()}%",
                            color = Color(0xFF35D6CD),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = info.downloadUrl
                        if (!url.isNullOrBlank()) {
                            viewModel.startInAppUpdate(context, url)
                        } else {
                            UpdateManager.openDownloadPage(context, null)
                            viewModel.dismissUpdateDialog()
                        }
                    },
                    enabled = updateDownloadProgress == null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF35D6CD))
                ) {
                    if (updateDownloadProgress != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF061418),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(strings.downloadingUpdate, color = Color(0xFF061418), fontWeight = FontWeight.Bold)
                    } else {
                        Text(strings.updateDownloadAction, color = Color(0xFF061418), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text(strings.updateLaterAction, color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF10242D),
            shape = RoundedCornerShape(16.dp)
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF07131A),
                drawerContentColor = Color(0xFFE8F7F8)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF10242D))
                                .border(1.dp, Color(0xFF35D6CD), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VpnLock,
                                contentDescription = null,
                                tint = Color(0xFF35D6CD),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "MolidoVPN",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            )
                            Text(
                                if (language == AppLanguage.PERSIAN) "اتصال خصوصی و امن" else "Private & Secure Connection",
                                color = Color(0xFF35D6CD),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (language == AppLanguage.PERSIAN) "منوی اصلی" else "MAIN MENU",
                        color = Color(0xFF6F9299),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    NavigationDrawerItem(
                        label = { Text(strings.tabDashboard, fontWeight = FontWeight.SemiBold) },
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF10242D),
                            selectedIconColor = Color(0xFF35D6CD),
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color(0xFF6F9299),
                            unselectedTextColor = Color(0xFFD3E2E4)
                        )
                    )
                    NavigationDrawerItem(
                        label = { Text(strings.tabLocations, fontWeight = FontWeight.SemiBold) },
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Public, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF10242D),
                            selectedIconColor = Color(0xFF35D6CD),
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color(0xFF6F9299),
                            unselectedTextColor = Color(0xFFD3E2E4)
                        )
                    )
                    NavigationDrawerItem(
                        label = { Text(strings.tabSettings) },
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF10242D),
                            selectedIconColor = Color(0xFF35D6CD),
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color(0xFF6F9299),
                            unselectedTextColor = Color(0xFFD3E2E4)
                        )
                    )
                    NavigationDrawerItem(
                        label = { Text(strings.updateTitle) },
                        selected = false,
                        onClick = {
                            viewModel.checkForUpdates(silent = false)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF10242D),
                            selectedIconColor = Color(0xFF35D6CD),
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color(0xFF35D6CD),
                            unselectedTextColor = Color(0xFFD3E2E4)
                        )
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = if (language == AppLanguage.PERSIAN) "باز کردن منو" else "Open menu",
                                tint = Color(0xFF35D6CD)
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                when (selectedTab) {
                                    1 -> strings.tabLocations
                                    2 -> strings.tabSettings
                                    else -> "MolidoVPN"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = Color.White
                            )
                            Text(
                                if (language == AppLanguage.PERSIAN) "اتصال خصوصی و امن" else "Private & Secure Connection",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF35D6CD),
                                fontSize = 10.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val nextLang = if (language == AppLanguage.PERSIAN) AppLanguage.ENGLISH else AppLanguage.PERSIAN
                            viewModel.setLanguage(nextLang)
                        }) {
                            Icon(Icons.Default.Language, contentDescription = "Language", tint = Color(0xFF35D6CD))
                        }
                        IconButton(onClick = {
                            if (selectedTab == 1) viewModel.refreshLocations() else viewModel.refreshServers()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF6F9299))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF07131A))
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF07131A),
                    contentColor = Color(0xFFE8F7F8),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = strings.tabDashboard) },
                        label = {
                            Text(
                                strings.tabDashboard,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF35D6CD),
                            selectedTextColor = Color(0xFF35D6CD),
                            unselectedIconColor = Color(0xFF6F9299),
                            unselectedTextColor = Color(0xFF6F9299),
                            indicatorColor = Color(0xFF10242D)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Public, contentDescription = strings.tabLocations) },
                        label = {
                            Text(
                                strings.tabLocations,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF35D6CD),
                            selectedTextColor = Color(0xFF35D6CD),
                            unselectedIconColor = Color(0xFF6F9299),
                            unselectedTextColor = Color(0xFF6F9299),
                            indicatorColor = Color(0xFF10242D)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = strings.tabSettings) },
                        label = {
                            Text(
                                strings.tabSettings,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF35D6CD),
                            selectedTextColor = Color(0xFF35D6CD),
                            unselectedIconColor = Color(0xFF6F9299),
                            unselectedTextColor = Color(0xFF6F9299),
                            indicatorColor = Color(0xFF10242D)
                        )
                    )
                }
            },
            containerColor = Color(0xFF07131A)
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
                        selectedLocation = selectedLocation,
                        currentLanguage = language,
                        onSelectEngine = { viewModel.setSelectedEngine(it) },
                        onOpenLocations = { selectedTab = 1 },
                        onConnect = { viewModel.onConnectClicked(context) },
                        onDisconnect = { viewModel.onDisconnectClicked() }
                    )
                    1 -> LocationsTab(
                        strings = strings,
                        currentLanguage = language,
                        locations = locations,
                        selectedLocation = selectedLocation,
                        onSelectLocation = { viewModel.selectLocation(it) },
                        onRefresh = { viewModel.refreshLocations() }
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
                        onUpdateConfig = { viewModel.updateActiveConfig(it) },
                        isScanningCleanIps = isScanningCleanIps,
                        cleanIps = cleanIps,
                        onScanCleanIps = { viewModel.scanCleanIps() },
                        onApplyCleanIp = { viewModel.applyCleanIpToWarp(it) },
                        isCheckingUpdate = isCheckingUpdate,
                        onCheckUpdate = { viewModel.checkForUpdates(silent = false) }
                    )
                }
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
    selectedLocation: LocationItem,
    currentLanguage: AppLanguage,
    onSelectEngine: (String) -> Unit,
    onOpenLocations: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Status badge at top
        item {
            StatusBadge(strings = strings, connectionState = connectionState)
        }

        // Location selector pill / card
        item {
            LocationSelectorCard(
                strings = strings,
                location = selectedLocation,
                currentLanguage = currentLanguage,
                onClick = onOpenLocations
            )
        }

        // Big beautiful animated circular connect button in the center
        item {
            CircularConnectButton(
                strings = strings,
                connectionState = connectionState,
                onConnect = onConnect,
                onDisconnect = onDisconnect
            )
        }

        // When connected: show real-time stats
        item {
            AnimatedVisibility(
                visible = connectionState is ConnectionState.Connected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                RealTimeStatsCard(
                    strings = strings,
                    diagnostics = diagnostics
                )
            }
        }

        // Protocol selector chips below
        item {
            ProtocolSelector(
                strings = strings,
                selectedEngine = selectedEngine,
                onSelectEngine = onSelectEngine
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun CircularConnectButton(
    strings: AppStrings,
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = connectionState is ConnectionState.Connected
    val isBusy = connectionState is ConnectionState.Preparing ||
        connectionState is ConnectionState.Connecting ||
        connectionState is ConnectionState.Verifying

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isBusy) 1.10f else if (isConnected) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isConnected) 0.5f else if (isBusy) 0.7f else 0.15f,
        targetValue = if (isConnected || isBusy) 0.08f else 0.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val accentColor = when {
        isConnected -> Color(0xFF35D6CD)
        isBusy -> Color(0xFFF59E0B)
        else -> Color(0xFF35D6CD)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(200.dp)
    ) {
        // Outer animated pulsing glow
        if (isConnected || isBusy) {
            Box(
                modifier = Modifier
                    .size(196.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = pulseAlpha))
            )
        }

        // Outer ring border
        Box(
            modifier = Modifier
                .size(175.dp)
                .clip(CircleShape)
                .background(Color(0xFF0B1E26))
                .border(
                    width = if (isConnected) 2.dp else 1.dp,
                    color = if (isConnected) Color(0xFF35D6CD) else if (isBusy) Color(0xFFF59E0B) else Color(0xFF1A3942),
                    shape = CircleShape
                )
        )

        // Main circular button
        Box(
            modifier = Modifier
                .size(146.dp)
                .clip(CircleShape)
                .background(
                    if (isConnected) Brush.radialGradient(
                        colors = listOf(Color(0xFF15444D), Color(0xFF10242D))
                    ) else Brush.radialGradient(
                        colors = listOf(Color(0xFF16323D), Color(0xFF0B1E26))
                    )
                )
                .border(
                    width = 2.dp,
                    color = if (isConnected) Color(0xFF35D6CD) else if (isBusy) Color(0xFFF59E0B) else Color(0xFF1F4E58),
                    shape = CircleShape
                )
                .clickable {
                    if (isConnected || isBusy) onDisconnect() else onConnect()
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color(0xFFF59E0B),
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = if (isConnected) strings.disconnectAction else strings.connectAction,
                        tint = if (isConnected) Color(0xFF35D6CD) else Color(0xFFE8F7F8),
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when {
                        isConnected -> strings.disconnectAction
                        isBusy -> strings.connecting
                        else -> strings.connectAction
                    },
                    color = if (isConnected) Color(0xFF35D6CD) else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

private data class StatusBadgeStyle(
    val bg: Color,
    val text: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun StatusBadge(strings: AppStrings, connectionState: ConnectionState) {
    val style = when (connectionState) {
        is ConnectionState.Connected -> StatusBadgeStyle(
            Color(0xFF10242D),
            strings.connectedVerified,
            Icons.Default.CheckCircle,
            Color(0xFF35D6CD)
        )
        is ConnectionState.Verifying -> StatusBadgeStyle(
            Color(0xFF10242D),
            strings.verifying,
            Icons.Default.Lock,
            Color(0xFFF59E0B)
        )
        is ConnectionState.Connecting -> StatusBadgeStyle(
            Color(0xFF10242D),
            "${strings.connecting} (${connectionState.engineId})",
            Icons.Default.Speed,
            Color(0xFF35D6CD)
        )
        is ConnectionState.Preparing -> StatusBadgeStyle(
            Color(0xFF10242D),
            strings.connecting,
            Icons.Default.Info,
            Color(0xFF94A3B8)
        )
        is ConnectionState.Recovering -> StatusBadgeStyle(
            Color(0xFF10242D),
            strings.verifying,
            Icons.Default.Warning,
            Color(0xFFF59E0B)
        )
        is ConnectionState.Failed -> StatusBadgeStyle(
            Color(0xFF2D1515),
            "${strings.failedPrefix}${connectionState.reasonCode}",
            Icons.Default.Warning,
            Color(0xFFEF4444)
        )
        is ConnectionState.Idle -> StatusBadgeStyle(
            Color(0xFF10242D),
            strings.disconnected,
            Icons.Default.PowerSettingsNew,
            Color(0xFF6F9299)
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(style.bg)
            .border(1.dp, style.color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                style.text,
                color = style.color,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun RealTimeStatsCard(
    strings: AppStrings,
    diagnostics: ConnectionDiagnostics
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10242D)),
        border = BorderStroke(1.dp, Color(0xFF1A3942))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiveStatItem(
                icon = Icons.Default.Speed,
                label = strings.latencyLabel,
                value = diagnostics.lastLatencyMillis?.let { "$it ms" } ?: "—",
                color = Color(0xFF35D6CD)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color(0xFF1A3942))
            )
            LiveStatItem(
                icon = Icons.Default.CloudDownload,
                label = strings.downloadLabel,
                value = "${diagnostics.bytesIn / 1024} KB",
                color = Color(0xFF38BDF8)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color(0xFF1A3942))
            )
            LiveStatItem(
                icon = Icons.Default.Sync,
                label = strings.uploadLabel,
                value = "${diagnostics.bytesOut / 1024} KB",
                color = Color(0xFF20B8B2)
            )
        }
    }
}

@Composable
fun LiveStatItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                color = Color(0xFF6F9299),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
fun ProtocolSelector(
    strings: AppStrings,
    selectedEngine: String,
    onSelectEngine: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10242D)),
        border = BorderStroke(1.dp, Color(0xFF1A3942))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = strings.enginesTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (selectedEngine == "auto") "AUTO" else selectedEngine.uppercase(),
                    color = Color(0xFF35D6CD),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto connect Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectEngine("auto") },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedEngine == "auto") Color(0xFF0D323A) else Color(0xFF07131A)
                ),
                border = if (selectedEngine == "auto") BorderStroke(1.5.dp, Color(0xFF35D6CD)) else BorderStroke(1.dp, Color(0xFF1A3942))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF35D6CD),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(strings.autoConnect, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            Text(strings.autoConnectDesc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6F9299), fontSize = 10.sp)
                        }
                    }
                    if (selectedEngine == "auto") {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF35D6CD),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Protocol Chips: WARP, Xray, AmneziaWG, WireGuard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "warp" to "WARP",
                    "xray" to "Xray",
                    "amnezia" to "AmneziaWG",
                    "wireguard" to "WireGuard"
                ).forEach { (id, name) ->
                    val isSelected = selectedEngine == id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF35D6CD) else Color(0xFF07131A))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF35D6CD) else Color(0xFF1A3942),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectEngine(id) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            color = if (isSelected) Color(0xFF061418) else Color(0xFFE8F7F8),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocationSelectorCard(
    strings: AppStrings,
    location: LocationItem,
    currentLanguage: AppLanguage,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10242D)),
        border = BorderStroke(1.dp, Color(0xFF1A3942))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF07131A))
                        .border(1.dp, Color(0xFF35D6CD).copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        location.flagEmoji,
                        fontSize = 22.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (currentLanguage == AppLanguage.PERSIAN) location.countryFa else location.countryEn,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (currentLanguage == AppLanguage.PERSIAN) location.cityFa else location.cityEn,
                        color = Color(0xFF6F9299),
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF35D6CD).copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        strings.changeLocation,
                        color = Color(0xFF35D6CD),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = Color(0xFF35D6CD),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LocationsTab(
    strings: AppStrings,
    currentLanguage: AppLanguage,
    locations: List<LocationItem>,
    selectedLocation: LocationItem,
    onSelectLocation: (LocationItem) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.selectLocation,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (currentLanguage == AppLanguage.PERSIAN)
                            "لوکیشن مورد نظر را برای اتصال انتخاب کنید"
                        else
                            "Select your desired connection location",
                        fontSize = 12.sp,
                        color = Color(0xFF6F9299)
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF35D6CD)
                    )
                }
            }
        }

        items(locations) { loc ->
            val isSelected = selectedLocation.id == loc.id
            val latency = loc.latencyMillis
            val latencyColor = when {
                latency == null -> Color(0xFF6F9299)
                latency < 60 -> Color(0xFF10B981)
                latency < 120 -> Color(0xFFF59E0B)
                else -> Color(0xFFF97316)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectLocation(loc) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFF0E2F37) else Color(0xFF10242D)
                ),
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) Color(0xFF35D6CD) else Color(0xFF1A3942)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF07131A))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF35D6CD) else Color(0xFF1A3942),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(loc.flagEmoji, fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (currentLanguage == AppLanguage.PERSIAN) loc.countryFa else loc.countryEn,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                if (loc.isAuto) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF35D6CD).copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            if (currentLanguage == AppLanguage.PERSIAN) "پیشنهادی" else "Recommended",
                                            color = Color(0xFF35D6CD),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (currentLanguage == AppLanguage.PERSIAN) loc.cityFa else loc.cityEn,
                                fontSize = 11.sp,
                                color = Color(0xFF6F9299)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (latency != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(latencyColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$latency ms",
                                    color = latencyColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF35D6CD),
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, Color(0xFF6F9299), CircleShape)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
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
    onUpdateConfig: (String) -> Unit,
    isScanningCleanIps: Boolean,
    cleanIps: List<com.molido.vpn.engine.warp.CleanIpResult>,
    onScanCleanIps: () -> Unit,
    onApplyCleanIp: (String) -> Unit,
    isCheckingUpdate: Boolean,
    onCheckUpdate: () -> Unit
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
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10242D)),
                border = BorderStroke(1.dp, Color(0xFF1A3942))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(strings.languageLabel, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(currentLanguage.displayName, style = MaterialTheme.typography.bodySmall, color = Color(0xFF35D6CD))
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
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10242D)),
                border = BorderStroke(1.dp, Color(0xFF1A3942))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.killSwitchTitle, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                strings.killSwitchDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6F9299)
                            )
                        }
                        Switch(
                            checked = killSwitch,
                            onCheckedChange = onToggleKillSwitch,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF061418),
                                checkedTrackColor = Color(0xFF35D6CD)
                            )
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onOpenAndroidSettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3942))
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF35D6CD))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.alwaysOnButton, fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }

        // Anti-Censorship DNS Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10242D)),
                border = BorderStroke(1.dp, Color(0xFF1A3942))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.dohTitle, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(strings.dohDesc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6F9299))
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
                                tint = if (selectedDns == provider.id) Color(0xFF35D6CD) else Color(0xFF6F9299),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(provider.name, fontWeight = FontWeight.Medium, color = Color.White)
                                Text(provider.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6F9299))
                            }
                        }
                    }
                }
            }
        }

        // Cloudflare Clean IP Scanner Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10242D)),
                border = BorderStroke(1.dp, Color(0xFF1A3942))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.cleanIpTitle, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(strings.cleanIpDesc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6F9299))
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onScanCleanIps,
                        enabled = !isScanningCleanIps,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF35D6CD))
                    ) {
                        if (isScanningCleanIps) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF061418), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.cleanIpScanning, fontSize = 12.sp, color = Color(0xFF061418))
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF061418))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.cleanIpAction, fontSize = 13.sp, color = Color(0xFF061418), fontWeight = FontWeight.Bold)
                        }
                    }

                    if (cleanIps.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (currentLanguage == AppLanguage.PERSIAN) "آی‌پی‌های تمیز با کمترین پینگ:" else "Discovered Clean Endpoints:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF35D6CD),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        for (clean in cleanIps.take(4)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(clean.ip, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${clean.latencyMillis} ms", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { onApplyCleanIp(clean.ip) }) {
                                    Text(strings.cleanIpApply, fontSize = 12.sp, color = Color(0xFF35D6CD))
                                }
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
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10242D)),
                border = BorderStroke(1.dp, Color(0xFF1A3942))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.splitTunnelTitle, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(strings.splitTunnelDesc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6F9299))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (currentLanguage == AppLanguage.PERSIAN) "${splitApps.size} برنامه مستثنی شده است" else "${splitApps.size} apps bypassed",
                        color = Color(0xFF35D6CD),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // App Version & Updates Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10242D)),
                border = BorderStroke(1.dp, Color(0xFF1A3942))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(strings.updateTitle, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${strings.currentVersionLabel}: v${UpdateManager.CURRENT_VERSION}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF35D6CD))
                        }
                        Button(
                            onClick = onCheckUpdate,
                            enabled = !isCheckingUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A3942))
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF35D6CD), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(strings.updateChecking, fontSize = 11.sp, color = Color(0xFF35D6CD))
                            } else {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF35D6CD))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.updateCheckAction, fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Raw Config Editor
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10242D)),
                border = BorderStroke(1.dp, Color(0xFF1A3942))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(strings.activeConfigTitle, fontWeight = FontWeight.Bold, color = Color.White)
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
