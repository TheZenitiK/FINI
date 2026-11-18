package com.example

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.network.AppReleaseInfo
import com.example.core.parser.XrayConfigBuilder
import com.example.data.local.SettingsDataStore
import com.example.data.model.ServerConfig
import com.example.data.model.VpnStatus
import com.example.ui.components.ConfigViewerSheet
import com.example.ui.components.SnowfallOverlay
import com.example.ui.i18n.LocalStrings
import com.example.ui.i18n.resolveAppStrings
import com.example.ui.navigation.AboutRoute
import com.example.ui.navigation.AppSelectorRoute
import com.example.ui.navigation.AppearanceRoute
import com.example.ui.navigation.AutoDisconnectRoute
import com.example.ui.navigation.AutoUpdateRoute
import com.example.ui.navigation.ClientSpoofRoute
import com.example.ui.navigation.ConnectionRoute
import com.example.ui.navigation.CustomRoutesRoute
import com.example.ui.navigation.ExperimentalSettingsRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.LogsRoute
import com.example.ui.navigation.PingSettingsRoute
import com.example.ui.navigation.QrScannerRoute
import com.example.ui.navigation.RoutingRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.XrayCoreRoute
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AppSelectorScreen
import com.example.ui.screens.AppearanceScreen
import com.example.ui.screens.AutoDisconnectScreen
import com.example.ui.screens.AutoUpdateScreen
import com.example.ui.screens.ClientSpoofScreen
import com.example.ui.screens.ConnectionSettingsScreen
import com.example.ui.screens.CustomRoutesScreen
import com.example.ui.screens.ExperimentalSettingsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PingSettingsScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.RoutingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.XrayCoreScreen
import com.example.ui.theme.GRayTheme
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.viewmodel.MainViewModel
import com.example.vpn.GRayTileService
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(EXTRA_OPEN_UPDATE_DIALOG, false) == true) {
            mainViewModel.showReleaseDialogIfActive()
        }
        val autoConnectFromTile = intent?.getBooleanExtra(GRayTileService.EXTRA_AUTO_CONNECT, false) == true
        setContent {
            GRayAppRoot(
                autoConnectFromTile = autoConnectFromTile,
                viewModel = mainViewModel
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATE_DIALOG, false)) {
            mainViewModel.showReleaseDialogIfActive()
        }
    }

    companion object {
        const val EXTRA_OPEN_UPDATE_DIALOG = "extra_open_update_dialog"
    }
}

@Composable
fun GRayAppRoot(
    autoConnectFromTile: Boolean = false,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bootCache = remember(context) { SettingsDataStore.readBootAppearance(context) }

    val effectiveLanguage = if (state.isInitialDataLoaded) state.settings.language else bootCache.language
    val effectiveThemeMode = if (state.isInitialDataLoaded) state.settings.themeMode else bootCache.themeMode
    val effectiveDynamicColor = if (state.isInitialDataLoaded) state.settings.useDynamicColor else bootCache.useDynamicColor
    val effectiveAccent = if (state.isInitialDataLoaded) state.settings.accentPreset else bootCache.accentPreset

    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()
    val availableRelease by viewModel.availableRelease.collectAsStateWithLifecycle()
    val isUpdateDialogVisible by viewModel.isUpdateDialogVisible.collectAsStateWithLifecycle()
    val appUpdateProgress by viewModel.appUpdateDownloadProgress.collectAsStateWithLifecycle()
    val appUpdateBytes by viewModel.appUpdateBytesProgress.collectAsStateWithLifecycle()
    val downloadedApkFile by viewModel.downloadedAppApkFile.collectAsStateWithLifecycle()
    val appUpdateError by viewModel.appUpdateError.collectAsStateWithLifecycle()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsStateWithLifecycle()
    val remoteCores by viewModel.remoteCores.collectAsStateWithLifecycle()
    val isLoadingCores by viewModel.isLoadingCores.collectAsStateWithLifecycle()
    val downloadingCoreProgress by viewModel.downloadingCoreProgress.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    val strings = remember(effectiveLanguage) {
        resolveAppStrings(effectiveLanguage)
    }
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, state.settings.appScalePreset) {
        val f = state.settings.appScalePreset.factor
        Density(density = baseDensity.density * f, fontScale = baseDensity.fontScale * f)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    LaunchedEffect(currentDest?.route) {
        val routeLabel = currentDest?.route?.substringAfterLast(".")?.substringBefore("/") ?: "HomeRoute"
        viewModel.logUiAction("Открыт экран: $routeLabel")
    }

    val showOnboarding = if (state.isInitialDataLoaded) {
        !state.settings.onboardingCompleted
    } else {
        !bootCache.onboardingCompleted
    }

    var pendingServerToConnect by remember { mutableStateOf<ServerConfig?>(null) }
    var pendingForceConnect by remember { mutableStateOf(false) }
    var configViewerServer by remember { mutableStateOf<ServerConfig?>(null) }
    var showOnboardingQrScanner by remember { mutableStateOf(false) }
    var didAutoConnectOnLaunch by remember { mutableStateOf(false) }

    val dispatchPendingVpnConnect: () -> Unit = {
        val target = pendingServerToConnect
        if (pendingForceConnect && target != null) {
            viewModel.startOrSwitchToServer(target)
        } else {
            viewModel.startOrStopVpn(target)
        }
        pendingServerToConnect = null
        pendingForceConnect = false
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            dispatchPendingVpnConnect()
        } else {
            viewModel.emitMessage(
                if (strings.isRussian) "Разрешение на создание VPN не предоставлено"
                else "VPN permission denied"
            )
            pendingServerToConnect = null
            pendingForceConnect = false
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            dispatchPendingVpnConnect()
        }
    }

    val requestVpnConnect: (ServerConfig?) -> Unit = { overrideServer ->
        pendingServerToConnect = overrideServer
        pendingForceConnect = false
        val needsNotificationPerm = state.settings.showVpnStatusNotification &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

        if (needsNotificationPerm) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                viewModel.startOrStopVpn(overrideServer)
                pendingServerToConnect = null
            }
        }
    }

    val requestForceConnectServer: (ServerConfig) -> Unit = { targetServer ->
        pendingServerToConnect = targetServer
        pendingForceConnect = true
        val needsNotificationPerm = state.settings.showVpnStatusNotification &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

        if (needsNotificationPerm) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                viewModel.startOrSwitchToServer(targetServer)
                pendingServerToConnect = null
                pendingForceConnect = false
            }
        }
    }

    LaunchedEffect(autoConnectFromTile) {
        if (autoConnectFromTile) {
            requestVpnConnect(null)
        }
    }

    LaunchedEffect(state.settings.autoConnectOnLaunch, state.servers.size, showOnboarding) {
        if (!showOnboarding &&
            !didAutoConnectOnLaunch &&
            state.settings.autoConnectOnLaunch &&
            state.servers.isNotEmpty() &&
            state.vpn.status == VpnStatus.DISCONNECTED
        ) {
            didAutoConnectOnLaunch = true
            if (VpnService.prepare(context) == null) {
                viewModel.startOrStopVpn(null)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(state.vpn.lastError) {
        state.vpn.lastError?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearVpnError()
        }
    }

    val showTopLevelNav = currentDest?.hasRoute<HomeRoute>() == true ||
        currentDest?.hasRoute<SettingsRoute>() == true

    val isHomeSelected = currentDest?.hasRoute<HomeRoute>() == true
    val isSettingsSelected = currentDest?.hasRoute<SettingsRoute>() == true

    val navigateTopLevel: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalDensity provides scaledDensity
    ) {
        GRayTheme(
            themeMode = effectiveThemeMode,
            useDynamicColor = effectiveDynamicColor,
            accentPreset = effectiveAccent
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Scaffold(
                    snackbarHost = {
                        if (configViewerServer == null && !showOnboarding) {
                            SnackbarHost(hostState = snackbarHostState)
                        }
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (showTopLevelNav && !useNavRail) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                NavigationBarItem(
                                    selected = isHomeSelected,
                                    onClick = { navigateTopLevel(HomeRoute) },
                                    icon = {
                                        Icon(
                                            imageVector = if (isHomeSelected) Icons.Rounded.Home else Icons.Outlined.Home,
                                            contentDescription = strings.navHome
                                        )
                                    },
                                    label = { Text(strings.navHome) },
                                    modifier = Modifier.testTag("tab_home")
                                )
                                NavigationBarItem(
                                    selected = isSettingsSelected,
                                    onClick = { navigateTopLevel(SettingsRoute) },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSettingsSelected) Icons.Rounded.Settings else Icons.Outlined.Settings,
                                            contentDescription = strings.navSettings
                                        )
                                    },
                                    label = { Text(strings.navSettings) },
                                    modifier = Modifier.testTag("tab_settings")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (showTopLevelNav && useNavRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                NavigationRailItem(
                                    selected = isHomeSelected,
                                    onClick = { navigateTopLevel(HomeRoute) },
                                    icon = {
                                        Icon(
                                            imageVector = if (isHomeSelected) Icons.Rounded.Home else Icons.Outlined.Home,
                                            contentDescription = strings.navHome
                                        )
                                    },
                                    label = { Text(strings.navHome) }
                                )
                                NavigationRailItem(
                                    selected = isSettingsSelected,
                                    onClick = { navigateTopLevel(SettingsRoute) },
                                    icon = {
                                        Icon(
                                            imageVector = if (isSettingsSelected) Icons.Rounded.Settings else Icons.Outlined.Settings,
                                            contentDescription = strings.navSettings
                                        )
                                    },
                                    label = { Text(strings.navSettings) }
                                )
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = HomeRoute,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable<HomeRoute> {
                                HomeScreen(
                                    state = state,
                                    onToggleVpn = { requestVpnConnect(null) },
                                    onHeroStatusPillClick = { viewModel.pingActiveServerForHeroBadge() },
                                    onNetworkPillClick = {
                                        viewModel.toggleNetworkAutoSwitchFromHome { srv ->
                                            requestForceConnectServer(srv)
                                        }
                                    },
                                    onSelectServer = { viewModel.selectServer(it) },
                                    onViewConfigClick = { srv -> configViewerServer = srv },
                                    onPingAll = { viewModel.pingAllServers() },
                                    onPingSubscription = { viewModel.pingSubscriptionServers(it) },
                                    onToggleSortByPing = { viewModel.setSortByPingEnabled(it) },
                                    onToggleOnlyAvailable = { viewModel.setOnlyAvailableServers(it) },
                                    onClearPingResults = { viewModel.clearAllPingResults() },
                                    onPingServer = { viewModel.pingSingleServer(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onReorderFavorites = { viewModel.reorderFavorites(it) },
                                    onImportText = { viewModel.importFromInput(it) },
                                    onImportFile = { viewModel.importFromFileUri(it) },
                                    onOpenQrScanner = { navController.navigate(QrScannerRoute) },
                                    onRefreshSubscription = { viewModel.refreshSubscription(it) },
                                    onUpdateSubscriptionSettings = { sub, newName, newUrl, intervalHours, onLaunch, pingOnLaunch ->
                                        viewModel.updateSubscriptionSettings(
                                            sub,
                                            newName,
                                            newUrl,
                                            intervalHours,
                                            onLaunch,
                                            pingOnLaunch
                                        )
                                    },
                                    onToggleSubscriptionCollapsed = { viewModel.toggleSubscriptionCollapsed(it) },
                                    onDeleteSubscription = { viewModel.deleteSubscription(it) },
                                    onDeleteServer = { viewModel.deleteStandaloneServer(it) },
                                    onShowMessage = { viewModel.emitMessage(it) }
                                )
                            }

                            composable<SettingsRoute> {
                                SettingsScreen(
                                    settings = state.settings,
                                    onSelectLanguage = { viewModel.setLanguage(it) },
                                    onOpenRouting = { navController.navigate(RoutingRoute) },
                                    onOpenConnection = { navController.navigate(ConnectionRoute) },
                                    onOpenClientSpoof = { navController.navigate(ClientSpoofRoute) },
                                    onOpenPing = { navController.navigate(PingSettingsRoute) },
                                    onOpenAutoDisconnect = { navController.navigate(AutoDisconnectRoute) },
                                    onOpenAppearance = { navController.navigate(AppearanceRoute) },
                                    onOpenExperimental = { navController.navigate(ExperimentalSettingsRoute) },
                                    onOpenAutoUpdate = { navController.navigate(AutoUpdateRoute) },
                                    onOpenAbout = { navController.navigate(AboutRoute) },
                                    onResetSettingsOnly = { viewModel.resetSettingsOnly() },
                                    onDeleteAllServersAndSubs = { viewModel.deleteAllServersAndSubscriptions() },
                                    onFullReset = { viewModel.fullReset() }
                                )
                            }

                            composable<ExperimentalSettingsRoute> {
                                ExperimentalSettingsScreen(
                                    settings = state.settings,
                                    onToggleExperimental = { viewModel.setExperimentalModeEnabled(it) },
                                    onOpenXrayCore = { navController.navigate(XrayCoreRoute) },
                                    onOpenLogs = { navController.navigate(LogsRoute) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<RoutingRoute> {
                                RoutingScreen(
                                    settings = state.settings,
                                    servers = state.servers,
                                    viewModel = viewModel,
                                    onOpenAppSelector = { navController.navigate(AppSelectorRoute) },
                                    onOpenCustomRoutes = { navController.navigate(CustomRoutesRoute) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<ConnectionRoute> {
                                ConnectionSettingsScreen(
                                    settings = state.settings,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<ClientSpoofRoute> {
                                ClientSpoofScreen(
                                    settings = state.settings,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<AppSelectorRoute> {
                                AppSelectorScreen(
                                    settings = state.settings,
                                    installedApps = installedApps,
                                    isLoadingApps = isLoadingApps,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<CustomRoutesRoute> {
                                CustomRoutesScreen(
                                    settings = state.settings,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<AutoDisconnectRoute> {
                                AutoDisconnectScreen(
                                    settings = state.settings,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<PingSettingsRoute> {
                                PingSettingsScreen(
                                    settings = state.settings,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<AppearanceRoute> {
                                AppearanceScreen(
                                    settings = state.settings,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<XrayCoreRoute> {
                                XrayCoreScreen(
                                    settings = state.settings,
                                    xrayVersion = state.vpn.xrayVersion,
                                    remoteCores = remoteCores,
                                    isLoadingCores = isLoadingCores,
                                    downloadingCoreProgress = downloadingCoreProgress,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<AutoUpdateRoute> {
                                AutoUpdateScreen(
                                    settings = state.settings,
                                    isCheckingUpdates = isCheckingUpdates,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<AboutRoute> {
                                AboutScreen(
                                    xrayVersion = state.vpn.xrayVersion,
                                    appIconStyle = state.settings.appIconStyle,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<QrScannerRoute> {
                                QrScannerScreen(
                                    onCodeScanned = { scanned ->
                                        viewModel.importFromInput(scanned)
                                        navController.popBackStack()
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<LogsRoute> {
                                LogsScreen(
                                    logsEnabled = state.settings.logsEnabled,
                                    logs = logs,
                                    onToggleEnabled = { viewModel.setLogsEnabled(it) },
                                    onClearLogs = { viewModel.clearLogs() },
                                    onShowMessage = { viewModel.emitMessage(it) },
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }

                val launchApkInstaller: (File) -> Unit = { apkFile ->
                    runCatching {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.fileprovider",
                            apkFile
                        )
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(installIntent)
                        viewModel.dismissReleaseDialog(deleteCache = false)
                    }.onFailure { err ->
                        viewModel.emitMessage(
                            err.localizedMessage
                                ?: if (strings.isRussian) "Не удалось запустить установщик APK" else "Failed to start APK installer"
                        )
                    }
                }

                val unknownSourcesLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { _ ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        context.packageManager.canRequestPackageInstalls()
                    ) {
                        downloadedApkFile?.let(launchApkInstaller)
                    }
                }

                // GitHub Release Update Available Dialog (Direct In-App APK Downloader + Minimize)
                availableRelease?.takeIf { isUpdateDialogVisible }?.let { release ->
                    AppUpdateDialog(
                        release = release,
                        downloadProgress = appUpdateProgress,
                        bytesProgress = appUpdateBytes,
                        downloadedApkFile = downloadedApkFile,
                        downloadError = appUpdateError,
                        onStartOrOpenRelease = {
                            if (release.apkDownloadUrl != null) {
                                viewModel.startAppUpdateDownload()
                            } else {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                                runCatching { context.startActivity(intent) }
                                viewModel.dismissReleaseDialog(deleteCache = true)
                            }
                        },
                        onInstallReadyApk = { readyApk ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                !context.packageManager.canRequestPackageInstalls()
                            ) {
                                viewModel.emitMessage(
                                    if (strings.isRussian) {
                                        "Разрешите установку из неизвестных источников для FINI"
                                    } else {
                                        "Allow installing unknown apps for FINI"
                                    }
                                )
                                val settingsIntent = Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}")
                                )
                                runCatching { unknownSourcesLauncher.launch(settingsIntent) }
                            } else {
                                launchApkInstaller(readyApk)
                            }
                        },
                        onMinimize = { viewModel.hideReleaseDialog() },
                        onCancelOrLater = { viewModel.dismissReleaseDialog(deleteCache = true) }
                    )
                }

                configViewerServer?.let { viewingServer ->
                    val currentServer = state.servers.firstOrNull { it.id == viewingServer.id } ?: viewingServer
                    val initialJson = remember(currentServer.id, currentServer.customJsonConfig, state.settings) {
                        XrayConfigBuilder.getEditableConfigJson(currentServer, state.settings)
                    }
                    ConfigViewerSheet(
                        server = currentServer,
                        initialJson = initialJson,
                        onDismiss = { configViewerServer = null },
                        onCopy = { text ->
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("fini_config_json", text))
                            viewModel.emitMessage(
                                if (strings.isRussian) "Скопировано" else "Copied"
                            )
                        },
                        onSave = { updatedJson ->
                            viewModel.updateServerCustomJson(currentServer.id, updatedJson)
                        }
                    )
                }

                if (showOnboarding) {
                    OnboardingScreen(
                        settings = state.settings,
                        hasAddedConfigs = state.servers.isNotEmpty() || state.subscriptions.isNotEmpty(),
                        isImporting = state.isImporting,
                        onToggleStatusNotification = { viewModel.setShowVpnStatusNotification(it) },
                        onSelectThemeMode = { viewModel.setThemeMode(it) },
                        onImportText = { viewModel.importFromInput(it) },
                        onOpenQrScanner = { showOnboardingQrScanner = true },
                        onShowMessage = { viewModel.emitMessage(it) },
                        onCompleteOnboarding = {
                            showOnboardingQrScanner = false
                            viewModel.completeOnboarding()
                        }
                    )

                    if (showOnboardingQrScanner) {
                        BackHandler { showOnboardingQrScanner = false }
                        QrScannerScreen(
                            onCodeScanned = { scanned ->
                                viewModel.importFromInput(scanned)
                                showOnboardingQrScanner = false
                            },
                            onBack = { showOnboardingQrScanner = false }
                        )
                    }
                }

                if (configViewerServer != null || showOnboarding) {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(bottom = 16.dp)
                    )
                }

                SnowfallOverlay(
                    enabled = state.settings.seasonalSnowEnabled,
                    themeMode = effectiveThemeMode
                )
            }
        }
    }
}

@Composable
private fun AppUpdateDialog(
    release: AppReleaseInfo,
    downloadProgress: Float?,
    bytesProgress: Pair<Long, Long>?,
    downloadedApkFile: File?,
    downloadError: String?,
    onStartOrOpenRelease: () -> Unit,
    onInstallReadyApk: (File) -> Unit,
    onMinimize: () -> Unit,
    onCancelOrLater: () -> Unit
) {
    val strings = LocalStrings.current
    val isDownloading = downloadProgress != null
    val isReady = downloadedApkFile != null

    Dialog(
        onDismissRequest = {
            if (isDownloading) onMinimize() else onCancelOrLater()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = !isDownloading
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val badgeColor = when {
                        isReady -> StatusGreen.copy(alpha = 0.16f)
                        downloadError != null -> StatusRed.copy(alpha = 0.14f)
                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                    }
                    val iconTint = when {
                        isReady -> StatusGreen
                        downloadError != null -> StatusRed
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val headerIcon = when {
                        isReady -> Icons.Rounded.CheckCircle
                        downloadError != null -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.SystemUpdate
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = headerIcon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isReady -> if (strings.isRussian) "Обновление готово" else "Update Ready"
                                isDownloading -> if (strings.isRussian) "Загрузка обновления" else "Downloading Update"
                                else -> strings.updateAvailableTitle
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} → v${release.versionName}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (release.releaseNotes.isNotBlank() && !isDownloading) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (strings.isRussian) "Что нового" else "Release Notes",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = release.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (downloadProgress != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val pct = (downloadProgress * 100f).roundToInt().coerceIn(0, 100)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bytesText = if (bytesProgress != null && bytesProgress.first > 0L) {
                                    val dlMb = bytesProgress.first / (1024.0 * 1024.0)
                                    if (bytesProgress.second > 0L) {
                                        val totalMb = bytesProgress.second / (1024.0 * 1024.0)
                                        String.format(Locale.US, "%.1f / %.1f %s", dlMb, totalMb, if (strings.isRussian) "МБ" else "MB")
                                    } else {
                                        String.format(Locale.US, "%.1f %s", dlMb, if (strings.isRussian) "МБ" else "MB")
                                    }
                                } else {
                                    if (strings.isRussian) "Подготовка загрузки…" else "Preparing download…"
                                }
                                Text(
                                    text = bytesText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50)),
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                } else if (downloadedApkFile != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = StatusGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = if (strings.isRussian) {
                                "Установочный пакет Fini загружен и готов к обновлению."
                            } else {
                                "Update APK has been downloaded and is ready to install."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else if (downloadError != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = StatusRed.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = downloadError,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        downloadProgress != null -> {
                            TextButton(
                                onClick = onCancelOrLater,
                                modifier = Modifier.testTag("cancel_app_update_button")
                            ) {
                                Text(strings.cancel, color = StatusRed)
                            }
                            Button(
                                onClick = onMinimize,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("minimize_app_update_button")
                            ) {
                                Text(strings.minimize, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        downloadedApkFile != null -> {
                            TextButton(onClick = onCancelOrLater) {
                                Text(strings.later)
                            }
                            Button(
                                onClick = { onInstallReadyApk(downloadedApkFile) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("install_downloaded_apk_button")
                            ) {
                                Text(if (strings.isRussian) "Установить" else "Install")
                            }
                        }
                        else -> {
                            TextButton(onClick = onCancelOrLater) {
                                Text(strings.later)
                            }
                            Button(
                                onClick = onStartOrOpenRelease,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("start_app_update_button")
                            ) {
                                Text(
                                    if (downloadError != null) {
                                        if (strings.isRussian) "Повторить" else "Retry"
                                    } else {
                                        strings.updateNow
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
