package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.EditRoad
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.network.InstalledAppsProvider
import com.example.data.local.MincifryWhitelist
import com.example.data.local.NetworkRuleTarget
import com.example.data.model.AppRoutingMode
import com.example.data.model.AppSettings
import com.example.data.model.InstalledAppInfo
import com.example.data.model.ServerConfig
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusRed
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(
    settings: AppSettings,
    servers: List<ServerConfig>,
    viewModel: MainViewModel,
    onOpenAppSelector: () -> Unit,
    onOpenCustomRoutes: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val strings = LocalStrings.current

    var pickWifiServerDialog by remember { mutableStateOf(false) }
    var pickMobileServerDialog by remember { mutableStateOf(false) }

    val wifiServer = servers.firstOrNull { it.id == settings.wifiServerId }
    val mobileServer = servers.firstOrNull { it.id == settings.mobileServerId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.routingTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Per-app split tunneling
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Rounded.Apps,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            if (strings.isRussian) "Приложения (Раздельное туннелирование)"
                                            else "Apps (Split Tunneling)",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = if (settings.splitAppRulesByNetwork) {
                                                if (strings.isRussian) "Раздельные списки для Wi‑Fi и Мобильной сети"
                                                else "Separate lists for Wi‑Fi and Mobile Data"
                                            } else {
                                                if (strings.isRussian) {
                                                    "Единый список для всех сетей (${strings.appRoutingTitle(settings.unifiedAppMode)})"
                                                } else {
                                                    "Unified list (${strings.appRoutingTitle(settings.unifiedAppMode)})"
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = settings.perAppEnabled,
                                    onCheckedChange = { viewModel.setPerAppEnabled(it) },
                                    modifier = Modifier.testTag("switch_per_app")
                                )
                            }

                            OutlinedButton(
                                onClick = onOpenAppSelector,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_app_selector_button")
                            ) {
                                Text(
                                    if (strings.isRussian) "Выбрать приложения и режим"
                                    else "Select Apps & Mode"
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                    }
                }

                // 2. Auto-switch server by Wi-Fi / Mobile
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (strings.isRussian) "Автосмена сервера по сети"
                                        else "Auto-Switch Server by Network",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (strings.isRussian) {
                                            "Автоматически переключать сервер в обе стороны между Wi‑Fi и мобильным интернетом. Активация выполняется нажатием на значок сети на главном экране."
                                        } else {
                                            "Automatically switch servers both ways between Wi‑Fi and Mobile Data. Activate by tapping the network badge on the main screen."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.autoSwitchServerByNetwork,
                                    onCheckedChange = { viewModel.setAutoSwitchServer(it) },
                                    modifier = Modifier.testTag("switch_auto_server_network")
                                )
                            }

                            if (settings.autoSwitchServerByNetwork) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { pickWifiServerDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                if (strings.isRussian) "Сервер для Wi‑Fi" else "Wi‑Fi Server",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            Text(
                                                text = wifiServer?.let { "${it.countryFlag} ${it.name}" }
                                                    ?: if (strings.isRussian) "Не выбран (текущий)" else "Not selected (keep current)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { pickMobileServerDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Rounded.CellTower, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                if (strings.isRussian) "Сервер для Мобильной сети" else "Mobile Data Server",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            Text(
                                                text = mobileServer?.let { "${it.countryFlag} ${it.name}" }
                                                    ?: if (strings.isRussian) "Не выбран (текущий)" else "Not selected (keep current)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Domain & IP Bypass Rules
                item {
                    ToggleRuleCard(
                        icon = Icons.Rounded.Lan,
                        title = if (strings.isRussian) "Игнорировать локальные адреса" else "Bypass Local Addresses (LAN)",
                        subtitle = if (strings.isRussian) {
                            "Включено по умолчанию. Доступ к роутеру (192.168.x.x) и устройствам локальной сети идёт напрямую без VPN"
                        } else {
                            "Enabled by default. Router (192.168.x.x) and local devices bypass VPN"
                        },
                        checked = settings.bypassLan,
                        testTag = "switch_bypass_lan",
                        onCheckedChange = { viewModel.setBypassLan(it) }
                    )
                }

                item {
                    ToggleRuleCard(
                        icon = Icons.Rounded.Public,
                        title = if (strings.isRussian) "Игнорировать все российские сайты" else "Bypass All Russian Websites",
                        subtitle = if (strings.isRussian) {
                            "Все домены .ru, .рф и .su открываются напрямую в обход VPN"
                        } else {
                            "All .ru, .рф and .su domains connect directly without VPN"
                        },
                        checked = settings.bypassRussianDomains,
                        testTag = "switch_bypass_ru",
                        onCheckedChange = { viewModel.setBypassRussianDomains(it) }
                    )
                }

                // 4. Separate Mincifry Whitelist (Pre-enabled by default)
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ListAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text(
                                            if (strings.isRussian) "Список Минцифры" else "Mincifry Allowlist",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = if (strings.isRussian) {
                                                "Включено заранее (${MincifryWhitelist.defaultDomains.size} доменов: Госуслуги, банки, маркетплейсы РФ) напрямую без VPN"
                                            } else {
                                                "Pre-enabled (${MincifryWhitelist.defaultDomains.size} Russian essential domains bypass VPN)"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = settings.bypassMincifry,
                                    onCheckedChange = { viewModel.setBypassMincifry(it) },
                                    modifier = Modifier.testTag("switch_bypass_mincifry")
                                )
                            }
                        }
                    }
                }

                // 5. Separate Custom Routes ("Мои маршруты")
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.EditRoad, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text(
                                            if (strings.isRussian) "Мои маршруты" else "Custom Routes",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = if (strings.isRussian) {
                                                "Свой список сайтов в обход VPN (${settings.customDirectDomains.size} доменов)"
                                            } else {
                                                "Custom direct-bypass domains (${settings.customDirectDomains.size} added)"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = settings.customRoutesEnabled,
                                    onCheckedChange = { viewModel.setCustomRoutesEnabled(it) },
                                    modifier = Modifier.testTag("switch_custom_routes")
                                )
                            }
                            OutlinedButton(
                                onClick = onOpenCustomRoutes,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_custom_routes_button")
                            ) {
                                Text(
                                    if (strings.isRussian) "Управлять своими маршрутами"
                                    else "Manage Custom Routes"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickWifiServerDialog) {
        ServerPickerDialog(
            title = if (strings.isRussian) "Сервер для Wi‑Fi" else "Wi‑Fi Server",
            servers = servers,
            selectedId = settings.wifiServerId,
            onSelect = { id ->
                viewModel.setWifiServerId(id)
                pickWifiServerDialog = false
            },
            onDismiss = { pickWifiServerDialog = false }
        )
    }

    if (pickMobileServerDialog) {
        ServerPickerDialog(
            title = if (strings.isRussian) "Сервер для Мобильной сети" else "Mobile Data Server",
            servers = servers,
            selectedId = settings.mobileServerId,
            onSelect = { id ->
                viewModel.setMobileServerId(id)
                pickMobileServerDialog = false
            },
            onDismiss = { pickMobileServerDialog = false }
        )
    }
}

@Composable
private fun ToggleRuleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
private fun ServerPickerDialog(
    title: String,
    servers: List<ServerConfig>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (servers.isEmpty()) {
                Text(
                    if (strings.isRussian) "Нет добавленных серверов. Добавьте сервер на главном экране."
                    else "No servers added yet. Add a server on Home screen."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(null) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedId == null, onClick = { onSelect(null) })
                            Text(if (strings.isRussian) "Не менять автоматически" else "Do not switch automatically")
                        }
                    }
                    items(servers, key = { it.id }) { srv ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(srv.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedId == srv.id, onClick = { onSelect(srv.id) })
                            Text(
                                text = "${srv.countryFlag} ${srv.name}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
    settings: AppSettings,
    installedApps: List<InstalledAppInfo>,
    isLoadingApps: Boolean,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current

    LaunchedEffect(Unit) {
        viewModel.loadInstalledAppsIfNeeded()
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var networkTabIndex by remember { mutableIntStateOf(0) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    // Draft state edited locally; persisted only on pressing "Сохранить"
    var draftSplitByNetwork by remember { mutableStateOf(settings.splitAppRulesByNetwork) }
    var draftUnifiedMode by remember { mutableStateOf(settings.unifiedAppMode) }
    var draftUnifiedPackages by remember { mutableStateOf(settings.unifiedAppPackages) }
    var draftWifiMode by remember { mutableStateOf(settings.wifiAppMode) }
    var draftWifiPackages by remember { mutableStateOf(settings.wifiAppPackages) }
    var draftMobileMode by remember { mutableStateOf(settings.mobileAppMode) }
    var draftMobilePackages by remember { mutableStateOf(settings.mobileAppPackages) }

    val hasUnsavedChanges = draftSplitByNetwork != settings.splitAppRulesByNetwork ||
        draftUnifiedMode != settings.unifiedAppMode ||
        draftUnifiedPackages != settings.unifiedAppPackages ||
        draftWifiMode != settings.wifiAppMode ||
        draftWifiPackages != settings.wifiAppPackages ||
        draftMobileMode != settings.mobileAppMode ||
        draftMobilePackages != settings.mobileAppPackages

    val attemptNavigateBack: () -> Unit = {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(onBack = attemptNavigateBack)

    val currentTarget = when {
        !draftSplitByNetwork -> NetworkRuleTarget.UNIFIED
        networkTabIndex == 0 -> NetworkRuleTarget.WIFI
        else -> NetworkRuleTarget.MOBILE
    }

    val currentMode = when (currentTarget) {
        NetworkRuleTarget.UNIFIED -> draftUnifiedMode
        NetworkRuleTarget.WIFI -> draftWifiMode
        NetworkRuleTarget.MOBILE -> draftMobileMode
    }

    val selectedPackages = when (currentTarget) {
        NetworkRuleTarget.UNIFIED -> draftUnifiedPackages
        NetworkRuleTarget.WIFI -> draftWifiPackages
        NetworkRuleTarget.MOBILE -> draftMobilePackages
    }

    // Anchor sorting snapshot updated ONLY on screen open / tab switch / Reset so checking
    // an item mid-list doesn't make the item teleport away under the user's finger.
    var anchorSelectedPackages by remember { mutableStateOf(selectedPackages) }
    LaunchedEffect(currentTarget) {
        anchorSelectedPackages = selectedPackages
    }

    val filteredApps = remember(installedApps, searchQuery, anchorSelectedPackages, showSystemApps) {
        val q = searchQuery.trim().lowercase()
        installedApps
            .filter { appInfo ->
                (showSystemApps || !appInfo.isSystemApp) &&
                    (q.isBlank() || appInfo.appName.lowercase().contains(q) || appInfo.packageName.lowercase().contains(q))
            }
            .sortedWith(
                compareByDescending<InstalledAppInfo> { it.packageName in anchorSelectedPackages }
                    .thenBy { it.appName.lowercase() }
            )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (strings.isRussian) "Приложения (${selectedPackages.size})"
                        else "Apps (${selectedPackages.size})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = attemptNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            when (currentTarget) {
                                NetworkRuleTarget.UNIFIED -> draftUnifiedPackages = emptySet()
                                NetworkRuleTarget.WIFI -> draftWifiPackages = emptySet()
                                NetworkRuleTarget.MOBILE -> draftMobilePackages = emptySet()
                            }
                            anchorSelectedPackages = emptySet()
                        },
                        enabled = selectedPackages.isNotEmpty(),
                        modifier = Modifier.testTag("reset_app_selector_button")
                    ) {
                        Text(strings.reset)
                    }
                    FilledTonalButton(
                        onClick = {
                            viewModel.saveAppSelectorSettings(
                                splitByNetwork = draftSplitByNetwork,
                                unifiedMode = draftUnifiedMode,
                                unifiedPackages = draftUnifiedPackages,
                                wifiMode = draftWifiMode,
                                wifiPackages = draftWifiPackages,
                                mobileMode = draftMobileMode,
                                mobilePackages = draftMobilePackages
                            )
                            onBack()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_app_selector_button")
                    ) {
                        Text(strings.save)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (strings.isRussian) "Разделить для Wi‑Fi и Мобильной сети"
                            else "Split for Wi‑Fi and Mobile Data",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            if (strings.isRussian) {
                                "Настроить разные списки приложений для домашнего Wi‑Fi и сотовой связи"
                            } else {
                                "Configure distinct app rules for Wi‑Fi and mobile networks"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = draftSplitByNetwork,
                        onCheckedChange = { draftSplitByNetwork = it },
                        modifier = Modifier.testTag("switch_split_apps_network")
                    )
                }
            }

            if (draftSplitByNetwork) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = networkTabIndex == 0,
                        onClick = { networkTabIndex = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(strings.networkWifi)
                    }
                    SegmentedButton(
                        selected = networkTabIndex == 1,
                        onClick = { networkTabIndex = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(strings.networkMobile)
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AppRoutingMode.entries.forEachIndexed { idx, mode ->
                    SegmentedButton(
                        selected = currentMode == mode,
                        onClick = {
                            when (currentTarget) {
                                NetworkRuleTarget.UNIFIED -> draftUnifiedMode = mode
                                NetworkRuleTarget.WIFI -> draftWifiMode = mode
                                NetworkRuleTarget.MOBILE -> draftMobileMode = mode
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = AppRoutingMode.entries.size)
                    ) {
                        Text(strings.appRoutingTitle(mode))
                    }
                }
            }

            Text(
                text = strings.appRoutingDesc(currentMode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(if (strings.isRussian) "Поиск по названию или пакету…" else "Search by name or package…")
                },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showSystemApps = !showSystemApps }
                    .testTag("checkbox_system_apps_row")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it },
                        modifier = Modifier.testTag("checkbox_system_apps")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (strings.isRussian) "Системные приложения" else "Show system apps",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            if (isLoadingApps) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { appInfo ->
                        val isChecked = appInfo.packageName in selectedPackages
                        AppSelectorRow(
                            appInfo = appInfo,
                            checked = isChecked,
                            onToggle = {
                                val pkg = appInfo.packageName
                                val next = if (pkg in selectedPackages) selectedPackages - pkg else selectedPackages + pkg
                                when (currentTarget) {
                                    NetworkRuleTarget.UNIFIED -> draftUnifiedPackages = next
                                    NetworkRuleTarget.WIFI -> draftWifiPackages = next
                                    NetworkRuleTarget.MOBILE -> draftMobilePackages = next
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(strings.unsavedChangesTitle) },
            text = { Text(strings.unsavedChangesBody) },
            confirmButton = {
                Button(
                    onClick = { showUnsavedDialog = false },
                    modifier = Modifier.testTag("unsaved_stay_button")
                ) {
                    Text(strings.stay)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        onBack()
                    },
                    modifier = Modifier.testTag("unsaved_exit_button")
                ) {
                    Text(strings.exit)
                }
            }
        )
    }
}

private fun drawableToImageBitmap(drawable: android.graphics.drawable.Drawable): androidx.compose.ui.graphics.ImageBitmap {
    return if (drawable is BitmapDrawable && drawable.bitmap != null) {
        drawable.bitmap.asImageBitmap()
    } else {
        val bmp = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }
}

@Composable
private fun AppSelectorRow(
    appInfo: InstalledAppInfo,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val initialCached = remember(appInfo.packageName) {
        InstalledAppsProvider.getCachedAppIcon(appInfo.packageName)?.let { drawableToImageBitmap(it) }
    }
    val iconBitmap by produceState(initialValue = initialCached, key1 = appInfo.packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                InstalledAppsProvider.loadAppIcon(context, appInfo.packageName)
                    ?.let { drawableToImageBitmap(it) }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onToggle,
        modifier = Modifier.testTag("app_row_${appInfo.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap!!,
                    contentDescription = appInfo.appName,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(38.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appInfo.appName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appInfo.packageName,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRoutesScreen(
    settings: AppSettings,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val strings = LocalStrings.current

    var newDomain by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (strings.isRussian) "Мои маршруты" else "Custom Routes")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (strings.isRussian) {
                            "Сайты из этого списка идут напрямую без VPN. Допускаются только корректные домены (например example.com или сайт.рф)."
                        } else {
                            "Domains listed here connect directly bypassing VPN. Only valid domains are accepted (e.g. example.com)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        OutlinedTextField(
                            value = newDomain,
                            onValueChange = {
                                newDomain = it
                                inputError = null
                            },
                            label = {
                                Text(
                                    if (strings.isRussian) "Домен (example.com или пример.рф)"
                                    else "Domain (e.g. example.com)"
                                )
                            },
                            isError = inputError != null,
                            supportingText = inputError?.let { err -> { Text(err) } },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_domain_input")
                        )
                        Button(
                            onClick = {
                                val ok = viewModel.addCustomDirectDomain(newDomain)
                                if (ok) {
                                    newDomain = ""
                                    inputError = null
                                } else {
                                    inputError = if (strings.isRussian) {
                                        "Некорректный домен"
                                    } else {
                                        "Invalid domain"
                                    }
                                }
                            },
                            enabled = newDomain.isNotBlank(),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .testTag("add_custom_domain_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add domain")
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (clip.isBlank()) {
                                viewModel.emitMessage(if (strings.isRussian) "Буфер обмена пуст" else "Clipboard is empty")
                            } else {
                                viewModel.importCustomDomainsFromText(clip)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (strings.isRussian) "Импортировать домены из буфера обмена"
                            else "Import Domains from Clipboard"
                        )
                    }
                }

                if (settings.customDirectDomains.isNotEmpty()) {
                    item {
                        Text(
                            text = if (strings.isRussian) {
                                "Добавленные домены (${settings.customDirectDomains.size})"
                            } else {
                                "Added Domains (${settings.customDirectDomains.size})"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(settings.customDirectDomains.sorted()) { domain ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = domain,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily)
                                )
                                IconButton(onClick = { viewModel.removeCustomDirectDomain(domain) }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = strings.delete,
                                        tint = StatusRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
