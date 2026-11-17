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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.molido.vpn.data.ServerCountryClassifier
import com.molido.vpn.importer.SubscriptionRefreshScheduler
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: VpnViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SubscriptionRefreshScheduler.schedule(applicationContext)
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
    val activeConfigText by viewModel.activeConfigText.collectAsState()
    val killSwitchEnabled by viewModel.killSwitchEnabled.collectAsState()
    val selectedDns by viewModel.selectedDns.collectAsState()
    val splitApps by viewModel.splitApps.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isFetchingCommunity by viewModel.isFetchingCommunity.collectAsState()
    val subscriptionUrls by viewModel.subscriptionUrls.collectAsState()

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF07131A),
                drawerContentColor = Color(0xFFE8F7F8)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                                .background(Color(0xFF073B42)).border(1.dp, Color(0xFF20D6D2), RoundedCornerShape(15.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VpnLock, contentDescription = null, tint = Color(0xFF36E0D5), modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("MOLIDO", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = 2.sp)
                            Text("VPN  •  AI SECURITY", color = Color(0xFF48CFC8), fontSize = 10.sp, letterSpacing = 1.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(if (language == AppLanguage.PERSIAN) "منوی اصلی" else "MAIN MENU", color = Color(0xFF6F9299), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    NavigationDrawerItem(
                        label = { Text(if (language == AppLanguage.PERSIAN) "داشبورد" else "Dashboard", fontWeight = FontWeight.SemiBold) },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; scope.launch { drawerState.close() } },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF103C40),
                            selectedIconColor = Color(0xFF42E2D7),
                            selectedTextColor = Color.White,
                            unselectedIconColor = Color(0xFF91AEB2),
                            unselectedTextColor = Color(0xFFD3E2E4)
                        )
                    )
                    NavigationDrawerItem(
                        label = { Text(if (language == AppLanguage.PERSIAN) "سرورها و پروفایل‌ها" else "Servers & Profiles") },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; scope.launch { drawerState.close() } },
                        icon = { Icon(Icons.Default.Storage, contentDescription = null) },
                        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF103C40), selectedIconColor = Color(0xFF42E2D7),
                            selectedTextColor = Color.White, unselectedIconColor = Color(0xFF91AEB2),
                            unselectedTextColor = Color(0xFFD3E2E4)
                        )
                    )
                    NavigationDrawerItem(
                        label = { Text(if (language == AppLanguage.PERSIAN) "امنیت و تنظیمات" else "Security & Settings") },
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2; scope.launch { drawerState.close() } },
                        icon = { Icon(Icons.Default.Security, contentDescription = null) },
                        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF103C40), selectedIconColor = Color(0xFF42E2D7),
                            selectedTextColor = Color.White, unselectedIconColor = Color(0xFF91AEB2),
                            unselectedTextColor = Color(0xFFD3E2E4)
                        )
                    )
                    NavigationDrawerItem(
                        label = { Text(if (language == AppLanguage.PERSIAN) "مرکز امکانات" else "Feature Center") },
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3; scope.launch { drawerState.close() } },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFF103C40), selectedIconColor = Color(0xFF42E2D7),
                            selectedTextColor = Color.White, unselectedIconColor = Color(0xFF91AEB2),
                            unselectedTextColor = Color(0xFFD3E2E4)
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(if (language == AppLanguage.PERSIAN) "ماژول‌های در حال توسعه" else "IN DEVELOPMENT", color = Color(0xFF6F9299), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    DrawerPlannedItem(Icons.Default.Analytics, if (language == AppLanguage.PERSIAN) "تحلیل و عملکرد" else "Analytics")
                    DrawerPlannedItem(Icons.Default.AutoAwesome, if (language == AppLanguage.PERSIAN) "مسیریابی هوشمند AI" else "AI Smart Routing")
                    DrawerPlannedItem(Icons.Default.SystemUpdate, if (language == AppLanguage.PERSIAN) "به‌روزرسانی هسته‌ها" else "Core Updates")
                    Spacer(Modifier.height(20.dp))
                    Text("PREMIUM • PRIVATE • SECURE", color = Color(0xFF3ABFB9), fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = if (language == AppLanguage.PERSIAN) "باز کردن منو" else "Open menu", tint = Color(0xFF43DCD3))
                        }
                    },
                    title = {
                        Column {
                            Text(
                                when (selectedTab) {
                                    1 -> if (language == AppLanguage.PERSIAN) "سرورها" else "Servers"
                                    2 -> if (language == AppLanguage.PERSIAN) "امنیت" else "Security"
                                    3 -> if (language == AppLanguage.PERSIAN) "مرکز امکانات" else "Feature Center"
                                    else -> "MolidoVPN AI"
                                },
                                fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White
                            )
                            Text(
                                if (language == AppLanguage.PERSIAN) "اتصال خصوصی، کنترل هوشمند" else "Private connection • intelligent control",
                                style = MaterialTheme.typography.bodySmall, color = Color(0xFF48CFC8), fontSize = 10.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val nextLang = if (language == AppLanguage.PERSIAN) AppLanguage.ENGLISH else AppLanguage.PERSIAN
                            viewModel.setLanguage(nextLang)
                        }) { Icon(Icons.Default.Language, contentDescription = "Language", tint = Color(0xFF43DCD3)) }
                        IconButton(onClick = { viewModel.refreshServers() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFFB4CBCD))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF07131A))
                )
            },
            containerColor = Color(0xFF07131A)
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                when (selectedTab) {
                    0 -> ConnectTab(
                        strings = strings, connectionState = connectionState, diagnostics = diagnostics,
                        killSwitchEnabled = killSwitchEnabled,
                        selectedEngine = selectedEngineId,
                        onSelectEngine = { viewModel.setSelectedEngine(it) },
                        onConnect = { viewModel.onConnectClicked(context) },
                        onDisconnect = { viewModel.onDisconnectClicked() }
                    )
                    1 -> ServersTab(
                        strings = strings, servers = servers, isFetching = isFetchingCommunity,
                        isPersian = language == AppLanguage.PERSIAN,
                        subscriptionUrls = subscriptionUrls,
                        onAddSubscription = { viewModel.addSubscription(it) },
                        onRemoveSubscription = { viewModel.removeSubscription(it) },
                        onRefreshSubscriptions = { viewModel.refreshSubscriptionsNow() },
                        onUpdateFromGithub = { viewModel.updateFromGithubCommunity() },
                        onSelectServer = { viewModel.selectServer(it) },
                        onImportClipboard = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val text = clip.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (text.isNotBlank()) viewModel.importFromRawText(text)
                            else Toast.makeText(context, if (language == AppLanguage.PERSIAN) "کلیپ‌بورد خالی است" else "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    )
                    3 -> DashboardFeatureCenter(language = language)
                    2 -> SecuritySettingsTab(
                        strings = strings, currentLanguage = language,
                        onSelectLanguage = { viewModel.setLanguage(it) },
                        killSwitch = killSwitchEnabled, onToggleKillSwitch = { viewModel.toggleKillSwitch(it) },
                        onOpenAndroidSettings = {
                            try { context.startActivity(KillSwitchManager.createVpnSettingsIntent()) }
                            catch (_: Exception) { Toast.makeText(context, "Cannot open VPN settings", Toast.LENGTH_SHORT).show() }
                        },
                        selectedDns = selectedDns, onSelectDns = { viewModel.setDnsProvider(it) },
                        splitApps = splitApps, installedApps = installedApps,
                        onToggleApp = { viewModel.toggleAppExclusion(it) },
                        activeConfigText = activeConfigText, onUpdateConfig = { viewModel.updateActiveConfig(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerPlannedItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF54777D), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = Color(0xFF78969B), fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text("SOON", color = Color(0xFF54777D), fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ConnectTab(
    strings: AppStrings,
    connectionState: ConnectionState,
    diagnostics: ConnectionDiagnostics,
    killSwitchEnabled: Boolean,
    selectedEngine: String,
    onSelectEngine: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isVerified = connectionState is ConnectionState.Connected
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
                Text(
                    if (strings.appName == "MolidoVPN") "اتصال امن شما" else "Your secure connection",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp
                )
                Text(
                    if (strings.appName == "MolidoVPN") "وضعیت تونل و اطلاعات شبکه در یک نگاه" else "Tunnel and network status at a glance",
                    color = Color(0xFF78999E), fontSize = 12.sp
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2028)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF15545A))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(18.dp).size(92.dp)
                            .clip(RoundedCornerShape(50.dp)).background(Color(0xFF12B8B0).copy(alpha = 0.07f))
                    )
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(if (strings.appName == "MolidoVPN") "وضعیت محافظت" else "PROTECTION STATUS", color = Color(0xFF9BB9BC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (isVerified) Color(0xFF0C594F) else Color(0xFF493A20)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                                Text(if (isVerified) "VERIFIED" else "NOT CONNECTED", color = if (isVerified) Color(0xFF5BE0BD) else Color(0xFFFFCF70), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        StatusCard(strings, connectionState, selectedEngine, onConnect, onDisconnect)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DashboardMetricCard(
                    modifier = Modifier.weight(1f),
                    title = if (strings.appName == "MolidoVPN") "IP خروجی" else "EXIT IP",
                    value = diagnostics.egressIp ?: "—",
                    detail = if (diagnostics.egressIp != null) "HTTPS egress verified" else if (isVerified) "IP هنوز تأیید نشده" else "پس از اتصال تأییدشده",
                    icon = Icons.Default.Security
                )
                DashboardMetricCard(
                    modifier = Modifier.weight(1f),
                    title = if (strings.appName == "MolidoVPN") "موقعیت سرور" else "SERVER LOCATION",
                    value = "—",
                    detail = "داده واقعی هنوز موجود نیست",
                    icon = Icons.Default.Language
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1C25)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A3942))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (strings.appName == "MolidoVPN") "عملکرد شبکه" else "NETWORK PERFORMANCE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DashboardMetricCard(Modifier.weight(1f), "PING", diagnostics.lastLatencyMillis?.takeIf { isVerified }?.let { "$it ms" } ?: "—", "اندازه‌گیری واقعی", Icons.Default.Speed)
                        DashboardMetricCard(Modifier.weight(1f), "DOWNLOAD", if (isVerified) "${diagnostics.bytesIn / 1024} KB" else "—", "ترافیک تونل", Icons.Default.CloudDownload)
                        DashboardMetricCard(Modifier.weight(1f), "UPLOAD", if (isVerified) "${diagnostics.bytesOut / 1024} KB" else "—", "ترافیک تونل", Icons.Default.Sync)
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1C25)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A3942))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (strings.appName == "MolidoVPN") "وضعیت لایه‌های امنیتی" else "SECURITY LAYERS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    DashboardSecurityRow("DNS Leak Protection", if (isVerified) "نیازمند تست نشت" else "در انتظار تونل", isVerified)
                    DashboardSecurityRow("IPv6 Protection", if (isVerified) "نیازمند تست IPv6" else "در انتظار تونل", isVerified)
                    DashboardSecurityRow("Kill Switch", if (killSwitchEnabled) "تنظیم روشن است؛ آزمون عملکرد لازم است" else "تنظیم خاموش است", needsTest = true)
                }
            }
        }
        item {
            SmartAutoEngineSelector(strings = strings, selectedEngine = selectedEngine, onSelect = onSelectEngine)
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun DashboardMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10242D)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B4650))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color(0xFF42D8D0), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(title, color = Color(0xFF8BAEB2), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(detail, color = Color(0xFF718F94), fontSize = 9.sp)
        }
    }
}

@Composable
private fun DashboardSecurityRow(title: String, detail: String, needsTest: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF36CFC7), modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color(0xFFE5F1F2), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = Color(0xFF7F9DA1), fontSize = 10.sp)
        }
        Text(if (needsTest) "TEST" else "PENDING", color = Color(0xFFFFC86A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
    isPersian: Boolean,
    subscriptionUrls: List<String>,
    onAddSubscription: (String) -> Unit,
    onRemoveSubscription: (String) -> Unit,
    onRefreshSubscriptions: () -> Unit,
    onUpdateFromGithub: () -> Unit,
    onSelectServer: (ServerItem) -> Unit,
    onImportClipboard: () -> Unit
) {
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var selectedCountryCode by remember { mutableStateOf("ALL") }
    var subscriptionInput by remember { mutableStateOf("") }
    val countries = remember(servers, isPersian) {
        ServerCountryClassifier.sorted(servers.map { ServerCountryClassifier.resolve(it.country) }, isPersian)
    }
    val visibleServers = remember(servers, selectedCountryCode) {
        servers.filter { selectedCountryCode == "ALL" || ServerCountryClassifier.resolve(it.country).code == selectedCountryCode }
            .sortedWith(compareBy<ServerItem> { ServerCountryClassifier.resolve(it.country).code == "ZZ" }
                .thenBy { ServerCountryClassifier.resolve(it.country).nameEn.lowercase() }
                .thenByDescending { it.score.score })
    }
    val groupedServers = remember(visibleServers) { visibleServers.groupBy { ServerCountryClassifier.resolve(it.country) } }
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isPersian) "مدیریت اشتراک‌ها" else "Subscription management", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = subscriptionInput,
                        onValueChange = { subscriptionInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(if (isPersian) "آدرس HTTPS اشتراک" else "HTTPS subscription URL") },
                        placeholder = { Text("https://example.com/sub.txt") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onAddSubscription(subscriptionInput); subscriptionInput = "" },
                            enabled = subscriptionInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text(if (isPersian) "ذخیره اشتراک" else "Add subscription") }
                        OutlinedButton(onClick = onRefreshSubscriptions, modifier = Modifier.weight(1f)) {
                            Text(if (isPersian) "به‌روزرسانی" else "Refresh")
                        }
                    }
                    if (subscriptionUrls.isEmpty()) {
                        Text(if (isPersian) "هنوز اشتراک سفارشی ثبت نشده است." else "No custom subscriptions saved.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        subscriptionUrls.forEach { url ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(url, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                TextButton(onClick = { onRemoveSubscription(url) }) {
                                    Text(if (isPersian) "حذف" else "Remove")
                                }
                            }
                        }
                    }
                    Text(
                        if (isPersian) "آدرس‌ها پس از اعتبارسنجی HTTPS در Android Keystore رمزگذاری می‌شوند. منابع عمومی ممکن است ناپایدار یا غیرقابل‌اعتماد باشند."
                        else "URLs are validated as HTTPS and encrypted at rest with Android Keystore. Public feeds may be unstable or untrusted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        item {
            Text(strings.freeServersTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { countryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    val selected = countries.firstOrNull { it.code == selectedCountryCode }
                    Text(if (selectedCountryCode == "ALL") {
                        if (isPersian) "انتخاب کشور: همه کشورها" else "Select country: All countries"
                    } else {
                        selected?.let { "${it.flag}  ${ServerCountryClassifier.displayName(it, isPersian)}" } ?: "Select country"
                    })
                }
                DropdownMenu(expanded = countryMenuExpanded, onDismissRequest = { countryMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text(if (isPersian) "🌐 همه کشورها" else "🌐 All countries") }, onClick = { selectedCountryCode = "ALL"; countryMenuExpanded = false })
                    countries.forEach { country ->
                        DropdownMenuItem(text = { Text("${country.flag}  ${ServerCountryClassifier.displayName(country, isPersian)}") }, onClick = { selectedCountryCode = country.code; countryMenuExpanded = false })
                    }
                }
            }
            Text(if (isPersian) "${visibleServers.size} سرور" else "${visibleServers.size} servers", style = MaterialTheme.typography.bodySmall, color = Color(0xFF94A3B8))
        }
        groupedServers.forEach { (country, countryServers) ->
            item(key = "country-${country.code}") {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(country.flag, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(ServerCountryClassifier.displayName(country, isPersian), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("(${countryServers.size})", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
            items(countryServers, key = { it.id }) { server ->
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


private data class DashboardFeature(
    val number: String,
    val titleFa: String,
    val titleEn: String,
    val descriptionFa: String,
    val descriptionEn: String,
    val statusFa: String,
    val statusEn: String
)

@Composable
private fun DashboardFeatureCenter(language: AppLanguage) {
    val persian = language == AppLanguage.PERSIAN
    val features = listOf(
        DashboardFeature("01", "حلقه اتصال هوشمند", "Smart Connection Ring", "وضعیت اتصال فقط پس از تأیید ترافیک واقعی معتبر است.", "Connection is valid only after real traffic verification.", "رابط آماده؛ موتور WireGuard هنوز یکپارچه نشده.", "UI ready; WireGuard backend integration pending."),
        DashboardFeature("02", "IP و موقعیت خروجی", "Exit IP & Location", "IP از پاسخ HTTPS خروجی گرفته می‌شود؛ مکان نیازمند منبع معتبر است.", "IP comes from HTTPS egress; geolocation needs a trusted provider.", "IP verifier در حال اتصال؛ مکان در انتظار.", "IP verifier integration in progress; location pending."),
        DashboardFeature("03", "مانیتور زنده شبکه", "Live Network Monitor", "تأخیر و شمارنده ترافیک؛ packet loss و throughput نیازمند probe واقعی‌اند.", "Latency and traffic counters; packet loss and throughput need real probes.", "جزئی؛ اندازه‌گیری کامل باقی است.", "Partial; full measurement pending."),
        DashboardFeature("04", "مرکز امنیت", "Security Center", "تنظیم DNS و Kill Switch موجود است؛ آزمون نشت DNS و IPv6 باقی است.", "DNS and Kill Switch settings exist; DNS and IPv6 leak tests remain.", "تنظیمات موجود؛ آزمون دستگاه لازم است.", "Settings exist; device tests required."),
        DashboardFeature("05", "مسیریابی هوشمند", "AI Smart Routing", "انتخاب خودکار فقط از مسیرهای دارای اندازه‌گیری واقعی مجاز است.", "Automatic selection is limited to routes with real measurements.", "تا ثبت اندازه‌گیری واقعی غیرفعال.", "Disabled until real measurements exist."),
        DashboardFeature("06", "مدیریت موتورهای VPN", "VPN Engine Manager", "کاتالوگ و Adapterها نیازمند GoBackend و ادغام موتورهای واقعی‌اند.", "Catalog and adapters need GoBackend and real engine integrations.", "در حال توسعه؛ هیچ موتور تأیید دستگاه نیست.", "In development; no engine is device-verified."),
        DashboardFeature("07", "تاریخچه اتصال", "Connection History", "ثبت امن رخدادها، مدت اتصال و علت قطع؛ بدون ذخیره کلیدها.", "Secure event history, duration and disconnect causes; never store keys.", "در انتظار پیاده‌سازی.", "Implementation pending."),
        DashboardFeature("08", "به‌روزرسانی هسته‌ها", "Core Updates", "بررسی امضا، نسخه، ABI، ضد rollback و بازیابی نسخه قبلی.", "Signature, version, ABI, anti-rollback and recovery checks.", "در انتظار پیاده‌سازی.", "Implementation pending."),
        DashboardFeature("09", "دستیار AI عیب‌یابی", "AI Diagnostics", "تحلیل خطا و پیشنهاد اصلاح؛ اقدامات حساس با تأیید کاربر.", "Error analysis and remediation; sensitive actions require approval.", "در انتظار موتور تحلیل و داده واقعی.", "AI engine and real telemetry pending."),
        DashboardFeature("10", "شخصی‌سازی", "Personalization", "زبان و تنظیمات پایه موجود است؛ تم و اعلان‌ها نیازمند تکمیل‌اند.", "Language and basic settings exist; themes and notifications need completion.", "جزئی؛ توسعه ادامه دارد.", "Partial; further work required.")
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(top = 14.dp, bottom = 4.dp)) {
                Text(if (persian) "مرکز کنترل MolidoVPN AI" else "MolidoVPN AI Feature Center", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(if (persian) "وضعیت واقعی هر ده قابلیت؛ موارد تأییدنشده فعال نمایش داده نمی‌شوند." else "Readiness for all ten features; unverified capabilities are never shown as active.", color = Color(0xFF86A5AA), fontSize = 12.sp)
            }
        }
        items(features) { feature ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10242D)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B4650))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(feature.number, color = Color(0xFF35D6CD), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(if (persian) feature.titleFa else feature.titleEn, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(if (persian) "در حال تکمیل" else "IN PROGRESS", color = Color(0xFFFFC86A), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(if (persian) feature.descriptionFa else feature.descriptionEn, color = Color(0xFF9BB5B9), fontSize = 11.sp)
                    Text(if (persian) feature.statusFa else feature.statusEn, color = Color(0xFF45CFC7), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
