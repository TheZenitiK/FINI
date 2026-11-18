package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.GRayApplication
import com.example.MainActivity
import com.example.R
import com.example.core.logging.AppLogger
import com.example.core.network.AppReleaseInfo
import com.example.core.network.GitHubUpdateChecker
import com.example.core.network.InstalledAppsProvider
import com.example.core.network.PingEngine
import com.example.core.parser.ParsedImportResult
import com.example.core.parser.SubscriptionFetcher
import com.example.core.parser.VlessUriParser
import com.example.data.local.MincifryWhitelist
import com.example.data.local.NetworkRuleTarget
import com.example.data.model.AccentPreset
import com.example.data.model.ActiveNetworkType
import com.example.data.model.AppIconStyle
import com.example.data.model.AppLanguage
import com.example.data.model.AppRoutingMode
import com.example.data.model.AppSettings
import com.example.data.model.AutoDisconnectConfig
import com.example.data.model.AutoUpdateConfig
import com.example.data.model.InstalledAppInfo
import com.example.data.model.PingDisplayMode
import com.example.data.model.PingMethod
import com.example.data.model.ServerConfig
import com.example.data.model.Subscription
import com.example.data.model.ThemeMode
import com.example.data.model.VpnStatus
import com.example.data.model.XrayCoreProfile
import com.example.ui.i18n.resolveAppStrings
import com.example.vpn.GRayVpnService
import com.example.vpn.VpnRuntimeState
import com.example.vpn.VpnStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

import com.example.data.model.AppScalePreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File

sealed interface HeroPingUiState {
    data object Pinging : HeroPingUiState
    data class Result(val pingMs: Int?) : HeroPingUiState
}

data class HomeUiState(
    val servers: List<ServerConfig> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val vpn: VpnRuntimeState = VpnRuntimeState(),
    val isImporting: Boolean = false,
    val isPingingAll: Boolean = false,
    val pingingServerIds: Set<String> = emptySet(),
    val pingingSubIds: Set<String> = emptySet(),
    val refreshingSubIds: Set<String> = emptySet(),
    val heroPingState: HeroPingUiState? = null,
    val isInitialDataLoaded: Boolean = false
) {
    val activeSelectedServer: ServerConfig?
        get() {
            val liveServer = if (vpn.status == VpnStatus.CONNECTED || vpn.status == VpnStatus.CONNECTING) {
                vpn.activeServer
            } else {
                null
            }
            return liveServer
                ?: servers.firstOrNull { it.id == settings.selectedServerId }
                ?: servers.firstOrNull()
        }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GRayApplication
    private val repository = app.container.repository
    private val settingsStore = app.container.settingsStore

    private val isImporting = MutableStateFlow(false)
    private val isPingingAll = MutableStateFlow(false)
    private val pingingServerIds = MutableStateFlow<Set<String>>(emptySet())
    private val pingingSubIds = MutableStateFlow<Set<String>>(emptySet())
    private val refreshingSubIds = MutableStateFlow<Set<String>>(emptySet())
    private val heroPingState = MutableStateFlow<HeroPingUiState?>(null)
    private var heroPingResetJob: Job? = null

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _availableRelease = MutableStateFlow<AppReleaseInfo?>(null)
    val availableRelease: StateFlow<AppReleaseInfo?> = _availableRelease.asStateFlow()

    private val _isUpdateDialogVisible = MutableStateFlow(false)
    val isUpdateDialogVisible: StateFlow<Boolean> = _isUpdateDialogVisible.asStateFlow()

    private val _appUpdateDownloadProgress = MutableStateFlow<Float?>(null)
    val appUpdateDownloadProgress: StateFlow<Float?> = _appUpdateDownloadProgress.asStateFlow()

    private val _appUpdateBytesProgress = MutableStateFlow<Pair<Long, Long>?>(null)
    val appUpdateBytesProgress: StateFlow<Pair<Long, Long>?> = _appUpdateBytesProgress.asStateFlow()

    private val _downloadedAppApkFile = MutableStateFlow<File?>(null)
    val downloadedAppApkFile: StateFlow<File?> = _downloadedAppApkFile.asStateFlow()

    private val _appUpdateError = MutableStateFlow<String?>(null)
    val appUpdateError: StateFlow<String?> = _appUpdateError.asStateFlow()

    private var appUpdateJob: Job? = null

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    private val _remoteCores = MutableStateFlow<List<XrayCoreProfile>>(emptyList())
    val remoteCores: StateFlow<List<XrayCoreProfile>> = _remoteCores.asStateFlow()

    private val _isLoadingCores = MutableStateFlow(false)
    val isLoadingCores: StateFlow<Boolean> = _isLoadingCores.asStateFlow()

    private val _downloadingCoreProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadingCoreProgress: StateFlow<Map<String, Float>> = _downloadingCoreProgress.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val logs: StateFlow<List<String>> = AppLogger.logs

    private var lastImportedPayloadHash: Int = 0
    private var lastImportedTimestampMs: Long = 0L

    private data class UiFlags(
        val importing: Boolean,
        val pingingAll: Boolean,
        val pingingIds: Set<String>,
        val pingingSubIds: Set<String>,
        val refreshingIds: Set<String>,
        val heroPing: HeroPingUiState?
    )

    val uiState: StateFlow<HomeUiState> = combine(
        repository.serversFlow,
        repository.subscriptionsFlow,
        repository.settingsFlow,
        VpnStateHolder.state,
        combine(
            isImporting,
            isPingingAll,
            combine(pingingServerIds, pingingSubIds) { srvIds, subIds -> srvIds to subIds },
            refreshingSubIds,
            heroPingState
        ) { imp, pingAll, pingPair, refIds, heroPing ->
            UiFlags(imp, pingAll, pingPair.first, pingPair.second, refIds, heroPing)
        }
    ) { servers, subscriptions, settings, vpn, flags ->
        HomeUiState(
            servers = servers,
            subscriptions = subscriptions,
            settings = settings,
            vpn = vpn,
            isImporting = flags.importing,
            isPingingAll = flags.pingingAll,
            pingingServerIds = flags.pingingIds,
            pingingSubIds = flags.pingingSubIds,
            refreshingSubIds = flags.refreshingIds,
            heroPingState = if (vpn.status == VpnStatus.CONNECTED) flags.heroPing else null,
            isInitialDataLoaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(isInitialDataLoaded = false)
    )

    private val lastAutoSubAttemptMs = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            var prevStatus: VpnStatus? = null
            var prevServerId: String? = null
            VpnStateHolder.state.collect { vpn ->
                val activeId = vpn.activeServer?.id
                if (vpn.status != VpnStatus.CONNECTED || (prevServerId != null && prevServerId != activeId)) {
                    clearHeroPing()
                }
                if (prevStatus != vpn.status) {
                    AppLogger.log("STATE", "Статус VPN: ${vpn.status} (сервер=${vpn.activeServer?.name ?: "—"})")
                }
                prevStatus = vpn.status
                prevServerId = activeId
            }
        }

        viewModelScope.launch {
            repository.settingsFlow.collect { s ->
                AppLogger.setEnabled(s.logsEnabled)
            }
        }

        viewModelScope.launch {
            settingsStore.ensureDeviceHwidPersisted()
            val s = repository.settingsFlow.first()
            AppLogger.setEnabled(s.logsEnabled, "Журнал событий включён")

            // Let initial UI frames settle before initializing OkHttp/TLS and network calls
            delay(900L)

            val cfg = s.autoUpdate
            val intervalMs = cfg.checkInterval.hours * 3600_000L
            val now = System.currentTimeMillis()
            if (cfg.autoCheckOnLaunch && (now - cfg.lastCheckTimestamp >= intervalMs)) {
                checkForAppUpdates(manual = false)
            }

            // Refresh subscriptions configured for update on launch or overdue interval
            val initialSubs = repository.subscriptionsFlow.first()
            val dueOnLaunch = initialSubs.filter { sub ->
                sub.autoUpdateOnLaunch ||
                    (sub.autoUpdateIntervalHours > 0 &&
                        now - sub.lastUpdatedAt >= sub.autoUpdateIntervalHours * 3600_000L)
            }
            dueOnLaunch.forEach { sub ->
                lastAutoSubAttemptMs[sub.id] = now
                refreshSubscriptionInternal(sub, showSuccessToast = false, showErrorToast = false)
            }

            // Auto-ping subscriptions that explicitly opted into "pingOnLaunch"
            val subsAfterRefresh = repository.subscriptionsFlow.first()
            subsAfterRefresh.filter { it.pingOnLaunch }.forEach { sub ->
                AppLogger.log("PING", "Проверка при запуске: подписка «${sub.name}»")
                pingSubscriptionServersInternal(sub.id)
            }
        }

        // Energy-efficient scheduled auto-updater: sleeps until earliest next due timestamp
        viewModelScope.launch {
            repository.subscriptionsFlow.collect { subs ->
                scheduledSubUpdateJob?.cancel()
                val scheduled = subs.filter { it.autoUpdateIntervalHours > 0 }
                if (scheduled.isEmpty()) return@collect
                scheduledSubUpdateJob = launch {
                    while (true) {
                        val currentSubs = repository.subscriptionsFlow.first().filter { it.autoUpdateIntervalHours > 0 }
                        if (currentSubs.isEmpty()) break
                        val now = System.currentTimeMillis()
                        val nextDueTimes = currentSubs.map { sub ->
                            val intervalMs = sub.autoUpdateIntervalHours * 3600_000L
                            val lastAttempt = lastAutoSubAttemptMs[sub.id] ?: 0L
                            val baseline = maxOf(sub.lastUpdatedAt, lastAttempt)
                            baseline + intervalMs
                        }
                        val earliest = nextDueTimes.minOrNull() ?: break
                        val waitMs = (earliest - now).coerceAtLeast(15_000L)
                        delay(waitMs)
                        val triggerNow = System.currentTimeMillis()
                        val dueNow = repository.subscriptionsFlow.first().filter { sub ->
                            if (sub.autoUpdateIntervalHours <= 0) return@filter false
                            val intervalMs = sub.autoUpdateIntervalHours * 3600_000L
                            val lastAttempt = lastAutoSubAttemptMs[sub.id] ?: 0L
                            val baseline = maxOf(sub.lastUpdatedAt, lastAttempt)
                            triggerNow - baseline >= intervalMs
                        }
                        dueNow.forEach { sub ->
                            lastAutoSubAttemptMs[sub.id] = triggerNow
                            refreshSubscriptionInternal(sub, showSuccessToast = false, showErrorToast = false)
                        }
                    }
                }
            }
        }
    }

    private var scheduledSubUpdateJob: Job? = null

    private fun strings() = resolveAppStrings(uiState.value.settings.language)

    private fun clearHeroPing() {
        heroPingResetJob?.cancel()
        heroPingResetJob = null
        heroPingState.value = null
    }

    fun startOrStopVpn(serverOverride: ServerConfig? = null) {
        val currentState = uiState.value
        val status = currentState.vpn.status
        clearHeroPing()

        if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) {
            if (serverOverride != null && serverOverride.id != currentState.vpn.activeServer?.id) {
                AppLogger.log("UI", "Переключение сервера на ${serverOverride.name}")
                launchVpnService(serverOverride.id)
            } else {
                AppLogger.log("UI", "Пользователь нажал Отключить VPN")
                stopVpnService()
            }
            return
        }

        val target = serverOverride ?: currentState.activeSelectedServer
        if (target == null) {
            emitMessage(
                if (strings().isRussian) "Сначала добавьте хотя бы один сервер"
                else "Add at least one server first"
            )
            return
        }
        AppLogger.log("UI", "Пользователь нажал Подключить (${target.name})")
        launchVpnService(target.id)
    }

    fun startOrSwitchToServer(server: ServerConfig) {
        clearHeroPing()
        val currentState = uiState.value
        val status = currentState.vpn.status
        if ((status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) &&
            currentState.vpn.activeServer?.id == server.id
        ) {
            return
        }
        AppLogger.log("UI", "Подключение / переключение на ${server.name}")
        launchVpnService(server.id)
    }

    fun stopVpnService() {
        clearHeroPing()
        val stopIntent = Intent(app, GRayVpnService::class.java).apply {
            action = GRayVpnService.ACTION_STOP
        }
        app.startService(stopIntent)
    }

    fun toggleNetworkAutoSwitchFromHome(
        onRequireVpnPermission: (ServerConfig) -> Unit
    ) {
        val currentState = uiState.value
        val settings = currentState.settings
        if (!settings.autoSwitchServerByNetwork) return

        viewModelScope.launch {
            if (settings.networkAutoSwitchActive) {
                settingsStore.updateNetworkAutoSwitchActive(false)
                AppLogger.log("NET", "Автосмена сервера по сети выключена с главного экрана")
                return@launch
            }

            settingsStore.updateNetworkAutoSwitchActive(true)
            AppLogger.log("NET", "Автосмена сервера по сети включена с главного экрана")

            val net = currentState.vpn.activeNetwork
            val preferredId = when (net) {
                ActiveNetworkType.WIFI -> settings.wifiServerId
                ActiveNetworkType.MOBILE -> settings.mobileServerId
                ActiveNetworkType.OTHER -> settings.wifiServerId ?: settings.mobileServerId
            }
            val target = preferredId?.let { id -> currentState.servers.firstOrNull { it.id == id } }
                ?: currentState.activeSelectedServer
            if (target != null) {
                if (VpnService.prepare(app) != null) {
                    onRequireVpnPermission(target)
                } else {
                    startOrSwitchToServer(target)
                }
            }
        }
    }

    private fun launchVpnService(serverId: String) {
        clearHeroPing()
        val showStatusNotification = uiState.value.settings.showVpnStatusNotification
        val intent = Intent(app, GRayVpnService::class.java).apply {
            action = GRayVpnService.ACTION_START
            putExtra(GRayVpnService.EXTRA_SERVER_ID, serverId)
            putExtra(GRayVpnService.EXTRA_STARTED_IN_FOREGROUND, showStatusNotification)
        }
        if (showStatusNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    fun selectServer(server: ServerConfig) {
        viewModelScope.launch {
            val state = uiState.value
            val previousSelectedId = state.settings.selectedServerId ?: state.activeSelectedServer?.id
            if (state.settings.networkAutoSwitchActive && previousSelectedId != server.id) {
                settingsStore.updateNetworkAutoSwitchActive(false)
                AppLogger.log("NET", "Автосмена по сети отключена: пользователь выбрал ${server.name}")
            }
            settingsStore.updateSelectedServerId(server.id)
            AppLogger.log("UI", "Выбран сервер: ${server.name}")
            val status = state.vpn.status
            if ((status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) &&
                state.vpn.activeServer?.id != server.id
            ) {
                clearHeroPing()
                launchVpnService(server.id)
            }
        }
    }

    fun updateServerCustomJson(serverId: String, jsonText: String): Boolean {
        val formatted = runCatching { JSONObject(jsonText.trim()).toString(2) }.getOrElse {
            emitMessage(
                if (strings().isRussian) "Некорректный JSON: проверьте синтаксис"
                else "Invalid JSON syntax"
            )
            return false
        }
        viewModelScope.launch {
            repository.updateServerCustomJson(serverId, formatted)
            AppLogger.log("UI", "Конфигурация сервера обновлена ($serverId)")
            emitMessage(
                if (strings().isRussian) "Конфигурация сохранена"
                else "Configuration saved"
            )
            val state = uiState.value
            if ((state.vpn.status == VpnStatus.CONNECTED || state.vpn.status == VpnStatus.CONNECTING) &&
                state.vpn.activeServer?.id == serverId
            ) {
                launchVpnService(serverId)
            }
        }
        return true
    }

    fun importFromFileUri(uri: Uri) {
        viewModelScope.launch {
            val contentResult = withContext(Dispatchers.IO) {
                runCatching {
                    val maxBytes = 5 * 1024 * 1024
                    app.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.size > maxBytes) {
                            error(
                                if (strings().isRussian) "Файл слишком большой (макс. 5 МБ)"
                                else "File is too large (max 5 MB)"
                            )
                        }
                        if (bytes.any { it == 0.toByte() }) {
                            error(
                                if (strings().isRussian) "Неподдерживаемый бинарный формат файла"
                                else "Unsupported binary file format"
                            )
                        }
                        bytes.toString(Charsets.UTF_8).trim()
                    } ?: error(
                        if (strings().isRussian) "Не удалось открыть выбранный файл"
                        else "Failed to open selected file"
                    )
                }
            }
            contentResult.onSuccess { text ->
                if (text.isBlank()) {
                    AppLogger.log("IMPORT", "Ошибка импорта: выбранный файл пуст")
                    emitMessage(
                        if (strings().isRussian) "Выбранный файл пуст"
                        else "Selected file is empty"
                    )
                } else {
                    AppLogger.log("IMPORT", "Импорт из файла (${text.length} симв.)")
                    importFromInput(text)
                }
            }.onFailure { err ->
                AppLogger.log("IMPORT", "Ошибка чтения файла: ${err.localizedMessage ?: err.javaClass.simpleName}")
                emitMessage(
                    err.localizedMessage
                        ?: if (strings().isRussian) "Не удалось прочитать файл" else "Failed to read file"
                )
            }
        }
    }

    fun importFromInput(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            emitMessage(if (strings().isRussian) "Буфер обмена пуст" else "Clipboard is empty")
            return
        }
        if (isImporting.value) return
        val hash = trimmed.hashCode()
        val now = System.currentTimeMillis()
        if (hash == lastImportedPayloadHash && now - lastImportedTimestampMs < 1500L) {
            return
        }
        lastImportedPayloadHash = hash
        lastImportedTimestampMs = now
        isImporting.value = true

        viewModelScope.launch {
            try {
                AppLogger.log("IMPORT", "Импорт данных (${trimmed.length} симв.)")
                when (val parsed = VlessUriParser.parseClipboardOrQrInput(trimmed)) {
                    is ParsedImportResult.Error -> {
                        AppLogger.log("IMPORT", "Ошибка парсинга: ${parsed.message}")
                        emitMessage(parsed.message)
                    }
                    is ParsedImportResult.SingleOrList -> {
                        repository.saveStandaloneServers(parsed.servers)
                        if (uiState.value.settings.selectedServerId == null) {
                            settingsStore.updateSelectedServerId(parsed.servers.first().id)
                        }
                        AppLogger.log("IMPORT", "Импортировано серверов: ${parsed.servers.size}")
                        emitMessage(
                            if (strings().isRussian) "Добавлено серверов: ${parsed.servers.size}"
                            else "Added servers: ${parsed.servers.size}"
                        )
                        pingServers(parsed.servers)
                    }
                    is ParsedImportResult.SubscriptionUrl -> {
                        val settings = repository.settingsFlow.first()
                        val hwid = settings.deviceHwid.ifBlank { settingsStore.ensureDeviceHwidPersisted() }
                        val existingSub = repository.getSubscriptionByUrl(parsed.url)
                        val result = SubscriptionFetcher.fetch(
                            url = parsed.url,
                            existingSubscription = existingSub,
                            happSpoofEnabled = settings.happSpoofEnabled,
                            sendDeviceHwid = settings.sendDeviceHwid,
                            deviceHwid = hwid
                        )
                        result.onSuccess { fetched ->
                            val existingFavorites = if (existingSub != null) {
                                repository.getServersBySubscriptionOnce(existingSub.id)
                                    .filter { it.isFavorite }
                                    .associate { it.rawUri to it.favoriteOrder }
                            } else emptyMap()

                            val merged = fetched.servers.map { srv ->
                                val favOrder = existingFavorites[srv.rawUri]
                                if (favOrder != null) srv.copy(isFavorite = true, favoriteOrder = favOrder) else srv
                            }
                            repository.saveSubscriptionWithServers(fetched.subscription, merged)
                            if (uiState.value.settings.selectedServerId == null && merged.isNotEmpty()) {
                                settingsStore.updateSelectedServerId(merged.first().id)
                            }
                            AppLogger.log(
                                "SUB",
                                "Подписка «${fetched.subscription.name}» добавлена (${merged.size} серв.)"
                            )
                            emitMessage(
                                if (strings().isRussian) {
                                    "Подписка «${fetched.subscription.name}» добавлена (${merged.size} серв.)"
                                } else {
                                    "Subscription «${fetched.subscription.name}» added (${merged.size} servers)"
                                }
                            )
                        }.onFailure { err ->
                            AppLogger.log("SUB", "Ошибка добавления подписки: ${err.localizedMessage ?: err.javaClass.simpleName}")
                            emitMessage(
                                err.localizedMessage
                                    ?: if (strings().isRussian) "Ошибка загрузки подписки" else "Subscription fetch error"
                            )
                        }
                    }
                }
            } finally {
                isImporting.value = false
            }
        }
    }

    fun refreshSubscription(subscription: Subscription) {
        viewModelScope.launch {
            refreshSubscriptionInternal(subscription, showSuccessToast = true, showErrorToast = true)
        }
    }

    private suspend fun refreshSubscriptionInternal(
        subscription: Subscription,
        showSuccessToast: Boolean,
        showErrorToast: Boolean
    ) {
        if (subscription.id in refreshingSubIds.value) return
        refreshingSubIds.value = refreshingSubIds.value + subscription.id
        try {
            val latestSub = repository.getSubscriptionById(subscription.id) ?: subscription
            val settings = repository.settingsFlow.first()
            val hwid = settings.deviceHwid.ifBlank { settingsStore.ensureDeviceHwidPersisted() }
            val existingFavorites = repository.getServersBySubscriptionOnce(latestSub.id)
                .filter { it.isFavorite }
                .associate { it.rawUri to it.favoriteOrder }

            SubscriptionFetcher.fetch(
                url = latestSub.url,
                existingSubscription = latestSub,
                happSpoofEnabled = settings.happSpoofEnabled,
                sendDeviceHwid = settings.sendDeviceHwid,
                deviceHwid = hwid
            )
                .onSuccess { fetched ->
                    val merged = fetched.servers.map { srv ->
                        val favOrder = existingFavorites[srv.rawUri]
                        if (favOrder != null) srv.copy(isFavorite = true, favoriteOrder = favOrder) else srv
                    }
                    repository.saveSubscriptionWithServers(fetched.subscription, merged)
                    AppLogger.log("SUB", "Подписка «${fetched.subscription.name}» обновлена (${merged.size} серв.)")
                    if (showSuccessToast) {
                        emitMessage(
                            if (strings().isRussian) {
                                "Подписка «${fetched.subscription.name}» обновлена (${merged.size} серв.)"
                            } else {
                                "Subscription «${fetched.subscription.name}» updated (${merged.size} servers)"
                            }
                        )
                    }
                }
                .onFailure { err ->
                    AppLogger.log("SUB", "Ошибка обновления «${latestSub.name}»: ${err.localizedMessage ?: err.javaClass.simpleName}")
                    if (showErrorToast) {
                        emitMessage(
                            err.localizedMessage
                                ?: if (strings().isRussian) "Не удалось обновить подписку" else "Failed to update subscription"
                        )
                    }
                }
        } finally {
            refreshingSubIds.value = refreshingSubIds.value - subscription.id
        }
    }

    fun renameSubscription(subscriptionId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameSubscription(subscriptionId, newName)
            AppLogger.log("SUB", "Подписка переименована: ${newName.trim()}")
            emitMessage(if (strings().isRussian) "Подписка переименована" else "Subscription renamed")
        }
    }

    fun updateSubscriptionSettings(
        subscription: Subscription,
        newName: String,
        newUrl: String,
        autoUpdateIntervalHours: Int,
        autoUpdateOnLaunch: Boolean,
        pingOnLaunch: Boolean
    ) {
        val cleanName = newName.trim()
        val cleanUrl = newUrl.trim()
        if (cleanName.isBlank() || cleanUrl.isBlank()) return
        viewModelScope.launch {
            val urlChanged = cleanUrl != subscription.url.trim()
            repository.updateSubscriptionSettings(
                subscriptionId = subscription.id,
                newName = cleanName,
                newUrl = cleanUrl,
                autoUpdateIntervalHours = autoUpdateIntervalHours,
                autoUpdateOnLaunch = autoUpdateOnLaunch,
                pingOnLaunch = pingOnLaunch
            )
            AppLogger.log(
                "SUB",
                "Настройки подписки «$cleanName»: автообновление=${autoUpdateIntervalHours}ч, приЗапуске=$autoUpdateOnLaunch, пингПриЗапуске=$pingOnLaunch"
            )
            emitMessage(
                if (strings().isRussian) "Настройки подписки сохранены"
                else "Subscription settings saved"
            )
            if (urlChanged) {
                val updated = subscription.copy(
                    name = cleanName,
                    url = cleanUrl,
                    autoUpdateIntervalHours = autoUpdateIntervalHours,
                    autoUpdateOnLaunch = autoUpdateOnLaunch,
                    pingOnLaunch = pingOnLaunch
                )
                refreshSubscriptionInternal(updated, showSuccessToast = true, showErrorToast = true)
            }
        }
    }

    fun toggleSubscriptionCollapsed(subscriptionId: String) {
        viewModelScope.launch {
            settingsStore.toggleSubscriptionCollapsed(subscriptionId)
        }
    }

    fun deleteSubscription(subscriptionId: String) {
        viewModelScope.launch {
            val active = uiState.value.vpn.activeServer
            if (active?.subscriptionId == subscriptionId && uiState.value.vpn.status == VpnStatus.CONNECTED) {
                stopVpnService()
            }
            repository.deleteSubscription(subscriptionId)
            emitMessage(if (strings().isRussian) "Подписка удалена" else "Subscription deleted")
        }
    }

    fun deleteStandaloneServer(server: ServerConfig) {
        viewModelScope.launch {
            if (uiState.value.vpn.activeServer?.id == server.id && uiState.value.vpn.status == VpnStatus.CONNECTED) {
                stopVpnService()
            }
            repository.deleteServer(server.id)
            emitMessage(
                if (strings().isRussian) "Сервер «${server.name}» удалён"
                else "Server «${server.name}» deleted"
            )
        }
    }

    fun toggleFavorite(server: ServerConfig) {
        viewModelScope.launch {
            repository.toggleFavorite(server.id, !server.isFavorite)
        }
    }

    fun reorderFavorites(orderedIds: List<String>) {
        viewModelScope.launch {
            repository.reorderFavorites(orderedIds)
        }
    }

    fun pingAllServers() {
        val list = uiState.value.servers
        viewModelScope.launch {
            val targetList = list.ifEmpty { repository.getAllServersOnce() }
            if (targetList.isEmpty() || isPingingAll.value) return@launch
            AppLogger.log("UI", "Запуск проверки всех серверов (${targetList.size})")
            isPingingAll.value = true
            try {
                pingServers(targetList)
            } finally {
                isPingingAll.value = false
            }
        }
    }

    fun pingSubscriptionServers(subscriptionId: String) {
        viewModelScope.launch {
            AppLogger.log("UI", "Запуск проверки серверов подписки")
            pingSubscriptionServersInternal(subscriptionId)
        }
    }

    private suspend fun pingSubscriptionServersInternal(subscriptionId: String) {
        if (subscriptionId in pingingSubIds.value) return
        val all = uiState.value.servers.ifEmpty { repository.getAllServersOnce() }
        val targets = all.filter { it.subscriptionId == subscriptionId }
        if (targets.isEmpty()) return
        pingingSubIds.value = pingingSubIds.value + subscriptionId
        try {
            pingServers(targets)
        } finally {
            pingingSubIds.value = pingingSubIds.value - subscriptionId
        }
    }

    fun setSortByPingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateSortByPingEnabled(enabled)
            AppLogger.log(
                "UI",
                if (enabled) "Включена сортировка серверов по результатам пинга"
                else "Сортировка по результатам пинга выключена (исходный порядок)"
            )
            emitMessage(
                if (strings().isRussian) {
                    if (enabled) "Отсортировано по результатам теста"
                    else "Восстановлен исходный порядок серверов"
                } else {
                    if (enabled) "Sorted by test results"
                    else "Original server order restored"
                }
            )
        }
    }

    fun setOnlyAvailableServers(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.updateOnlyAvailableServers(enabled)
            AppLogger.log("UI", "Фильтр «Только доступные серверы»: $enabled")
        }
    }

    fun clearAllPingResults() {
        viewModelScope.launch {
            repository.clearAllPingResults()
            AppLogger.log("UI", "Результаты теста пинга очищены")
            emitMessage(
                if (strings().isRussian) "Результаты теста очищены"
                else "Test results cleared"
            )
        }
    }

    fun pingSingleServer(server: ServerConfig) {
        viewModelScope.launch {
            pingingServerIds.value = pingingServerIds.value + server.id
            try {
                val method = uiState.value.settings.pingMethod
                val ms = withContext(Dispatchers.IO) { PingEngine.measurePing(server, method) }
                repository.updatePingResult(server.id, ms, ms == null)
                AppLogger.log(
                    "PING",
                    "Пинг «${server.name}» (${method.name}): ${ms?.let { "${it} мс" } ?: "нет ответа"}"
                )
            } finally {
                pingingServerIds.value = pingingServerIds.value - server.id
            }
        }
    }

    fun pingActiveServerForHeroBadge() {
        val current = uiState.value
        if (current.vpn.status != VpnStatus.CONNECTED) return
        val server = current.vpn.activeServer ?: current.activeSelectedServer ?: return

        heroPingResetJob?.cancel()
        heroPingResetJob = viewModelScope.launch {
            heroPingState.value = HeroPingUiState.Pinging
            val ms = withContext(Dispatchers.IO) {
                PingEngine.measurePing(server, current.settings.pingMethod)
            }
            repository.updatePingResult(server.id, ms, ms == null)
            AppLogger.log("PING", "Активный сервер «${server.name}»: ${ms?.let { "${it} мс" } ?: "нет ответа"}")
            heroPingState.value = HeroPingUiState.Result(ms)
            delay(5_000L)
            heroPingState.value = null
        }
    }

    private suspend fun pingServers(servers: List<ServerConfig>) {
        if (servers.isEmpty()) return
        val method = uiState.value.settings.pingMethod
        val semaphore = Semaphore(8)
        val results = withContext(Dispatchers.IO) {
            coroutineScope {
                servers.map { srv ->
                    async {
                        semaphore.withPermit {
                            val ms = PingEngine.measurePing(srv, method)
                            repository.updatePingResult(srv.id, ms, ms == null)
                            ms
                        }
                    }
                }.awaitAll()
            }
        }
        val okResults = results.filterNotNull()
        val failCount = results.size - okResults.size
        val minMs = okResults.minOrNull()
        val avgMs = if (okResults.isNotEmpty()) okResults.average().toInt() else null
        AppLogger.log(
            "PING",
            "Пакетный пинг (${method.name}): всего=${results.size}, доступно=${okResults.size}, недоступно=$failCount, мин=${minMs ?: "—"} мс, средн=${avgMs ?: "—"} мс"
        )
    }

    fun loadInstalledAppsIfNeeded() {
        if (_installedApps.value.isNotEmpty() || _isLoadingApps.value) return
        viewModelScope.launch {
            _isLoadingApps.value = true
            try {
                _installedApps.value = InstalledAppsProvider.getLaunchableApps(app)
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    // Settings mutations
    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        settingsStore.updateLanguage(language)
        AppLogger.log("SETTINGS", "Язык приложения: $language")
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsStore.updateThemeMode(mode)
        AppLogger.log("SETTINGS", "Тема оформления: $mode")
    }

    fun setUseDynamicColor(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateUseDynamicColor(enabled)
        AppLogger.log("SETTINGS", "Динамические цвета: $enabled")
    }

    fun setAccentPreset(preset: AccentPreset) = viewModelScope.launch {
        settingsStore.updateAccentPreset(preset)
        AppLogger.log("SETTINGS", "Акцентный цвет: $preset")
    }

    fun setAppIconStyle(style: AppIconStyle) = viewModelScope.launch {
        settingsStore.updateAppIconStyle(style)
        app.applyAppIconStyle(style)
        AppLogger.log("SETTINGS", "Стиль иконки приложения: $style")
        emitMessage(
            if (strings().isRussian) "Иконка приложения обновлена"
            else "App icon updated"
        )
    }

    fun setPinConnectCard(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updatePinConnectCard(enabled)
        AppLogger.log("SETTINGS", "Прикрепление окна подключения: $enabled")
    }

    fun setPingMethod(method: PingMethod) = viewModelScope.launch {
        settingsStore.updatePingMethod(method)
        AppLogger.log("SETTINGS", "Метод пинга: $method")
    }

    fun setPingDisplayMode(mode: PingDisplayMode) = viewModelScope.launch {
        settingsStore.updatePingDisplayMode(mode)
        AppLogger.log("SETTINGS", "Отображение пинга: $mode")
    }

    fun setBypassLan(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateBypassLan(enabled)
        AppLogger.log("ROUTING", "Обход локальных сетей: $enabled")
    }

    fun setBypassRussianDomains(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateBypassRussianDomains(enabled)
        AppLogger.log("ROUTING", "Обход доменов .ru/.рф: $enabled")
    }

    fun setBypassMincifry(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateBypassMincifry(enabled)
        AppLogger.log("ROUTING", "Список Минцифры: $enabled")
    }

    fun setCustomRoutesEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateCustomRoutesEnabled(enabled)
        AppLogger.log("ROUTING", "Мои маршруты: $enabled")
    }

    fun addCustomDirectDomain(rawDomain: String): Boolean {
        val valid = MincifryWhitelist.normalizeAndValidateDomain(rawDomain)
        if (valid == null) {
            emitMessage(
                if (strings().isRussian) {
                    "Некорректный домен. Введите реальный адрес (например example.com или сайт.рф)"
                } else {
                    "Invalid domain. Enter a real domain name (e.g., example.com)"
                }
            )
            return false
        }
        viewModelScope.launch {
            val next = uiState.value.settings.customDirectDomains + valid
            settingsStore.updateCustomDirectDomains(next)
            emitMessage(
                if (strings().isRussian) "Домен $valid добавлен в Мои маршруты"
                else "Added $valid to Custom Routes"
            )
        }
        return true
    }

    fun removeCustomDirectDomain(domain: String) {
        viewModelScope.launch {
            val next = uiState.value.settings.customDirectDomains - domain
            settingsStore.updateCustomDirectDomains(next)
        }
    }

    fun importCustomDomainsFromText(raw: String) {
        val tokens = raw.split('\n', '\r', ',', ';', ' ', '\t')
        val parsed = tokens.mapNotNull { MincifryWhitelist.normalizeAndValidateDomain(it) }.toSet()
        if (parsed.isEmpty()) {
            emitMessage(
                if (strings().isRussian) "Не найдено корректных доменов (например example.com)"
                else "No valid domains found (e.g., example.com)"
            )
            return
        }
        viewModelScope.launch {
            val next = uiState.value.settings.customDirectDomains + parsed
            settingsStore.updateCustomDirectDomains(next)
            emitMessage(
                if (strings().isRussian) "Добавлено доменов: ${parsed.size}"
                else "Imported domains: ${parsed.size}"
            )
        }
    }

    fun setPerAppEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updatePerAppEnabled(enabled)
    }

    fun setSplitAppsByNetwork(split: Boolean) = viewModelScope.launch {
        settingsStore.updateSplitAppsByNetwork(split)
    }

    fun setAppRoutingMode(target: NetworkRuleTarget, mode: AppRoutingMode) {
        viewModelScope.launch { settingsStore.updateAppRule(target, mode = mode) }
    }

    fun toggleAppPackage(target: NetworkRuleTarget, packageName: String) {
        viewModelScope.launch {
            val s = uiState.value.settings
            val current = when (target) {
                NetworkRuleTarget.UNIFIED -> s.unifiedAppPackages
                NetworkRuleTarget.WIFI -> s.wifiAppPackages
                NetworkRuleTarget.MOBILE -> s.mobileAppPackages
            }
            val next = if (packageName in current) current - packageName else current + packageName
            settingsStore.updateAppRule(target, packages = next)
        }
    }

    fun clearAppPackages(target: NetworkRuleTarget) {
        viewModelScope.launch { settingsStore.updateAppRule(target, packages = emptySet()) }
    }

    fun saveAppSelectorSettings(
        splitByNetwork: Boolean,
        unifiedMode: AppRoutingMode,
        unifiedPackages: Set<String>,
        wifiMode: AppRoutingMode,
        wifiPackages: Set<String>,
        mobileMode: AppRoutingMode,
        mobilePackages: Set<String>
    ) {
        viewModelScope.launch {
            settingsStore.saveAppSelectorSettings(
                splitByNetwork = splitByNetwork,
                unifiedMode = unifiedMode,
                unifiedPackages = unifiedPackages,
                wifiMode = wifiMode,
                wifiPackages = wifiPackages,
                mobileMode = mobileMode,
                mobilePackages = mobilePackages
            )
            AppLogger.log(
                "ROUTING",
                "Сохранено раздельное туннелирование (split=$splitByNetwork, unified=${unifiedPackages.size}, wifi=${wifiPackages.size}, mobile=${mobilePackages.size})"
            )
            emitMessage(
                if (strings().isRussian) "Настройки приложений сохранены"
                else "App routing saved"
            )
        }
    }

    fun setLogsEnabled(enabled: Boolean) = viewModelScope.launch {
        AppLogger.setEnabled(enabled, "Журнал событий включён")
        settingsStore.updateLogsEnabled(enabled)
    }

    fun clearLogs() {
        AppLogger.clear()
    }

    fun setAutoSwitchServer(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateAutoSwitchServer(enabled)
        if (!enabled) {
            settingsStore.updateNetworkAutoSwitchActive(false)
        }
        AppLogger.log("SETTINGS", "Автосмена сервера по сети: $enabled")
    }

    fun setWifiServerId(id: String?) = viewModelScope.launch {
        settingsStore.updateWifiServerId(id)
    }

    fun setMobileServerId(id: String?) = viewModelScope.launch {
        settingsStore.updateMobileServerId(id)
    }

    fun setAutoConnectOnLaunch(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateConnectionAutomation(autoConnectOnLaunch = enabled)
        AppLogger.log("SETTINGS", "Автоподключение при запуске: $enabled")
    }

    fun setAutoConnectOnMobile(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateConnectionAutomation(autoConnectOnMobile = enabled)
        AppLogger.log("SETTINGS", "Автоподключение на мобильной сети: $enabled")
    }

    fun setDisconnectOnWifi(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateConnectionAutomation(disconnectOnWifi = enabled)
        AppLogger.log("SETTINGS", "Отключаться на Wi-Fi: $enabled")
    }

    fun setShowVpnStatusNotification(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateConnectionAutomation(showVpnStatusNotification = enabled)
        AppLogger.log("SETTINGS", "Статус в строке уведомлений: $enabled")
    }

    fun setShowSpeedInNotification(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateConnectionAutomation(showSpeedInNotification = enabled)
        AppLogger.log("SETTINGS", "Время, скорость и трафик в шторке: $enabled")
    }

    fun setExperimentalModeEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateExperimentalModeEnabled(enabled)
        AppLogger.log("SETTINGS", "Экспериментальные настройки: $enabled")
    }

    fun completeOnboarding() = viewModelScope.launch {
        settingsStore.updateOnboardingCompleted(true)
        AppLogger.log("APP", "Первоначальная настройка завершена")
    }

    fun updateAutoDisconnect(config: AutoDisconnectConfig) = viewModelScope.launch {
        settingsStore.updateAutoDisconnect(config)
        AppLogger.log("SETTINGS", "Автоотключение: enabled=${config.enabled}, idle=${config.inactivityMinutes}м")
    }

    fun updateAutoUpdateConfig(config: AutoUpdateConfig) = viewModelScope.launch {
        settingsStore.updateAutoUpdate(config)
        AppLogger.log("UPDATE", "Автопроверка обновлений: enabled=${config.autoUpdateEnabled}, interval=${config.checkInterval.hours}ч")
    }

    fun checkForAppUpdates(manual: Boolean) {
        if (_isCheckingUpdates.value) return
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            try {
                val cfg = repository.settingsFlow.first().autoUpdate
                settingsStore.updateAutoUpdate(cfg.copy(lastCheckTimestamp = System.currentTimeMillis()))

                val result = GitHubUpdateChecker.checkLatestAppRelease()
                result.onSuccess { info ->
                    if (info != null && info.isNewer) {
                        _downloadedAppApkFile.value = null
                        _appUpdateDownloadProgress.value = null
                        _appUpdateBytesProgress.value = null
                        _appUpdateError.value = null
                        _availableRelease.value = info
                        _isUpdateDialogVisible.value = true
                    } else if (manual) {
                        emitMessage(
                            if (strings().isRussian) "У вас установлена актуальная версия FINI"
                            else "You are using the latest version of FINI"
                        )
                    }
                }.onFailure { err ->
                    if (manual) {
                        emitMessage(
                            if (strings().isRussian) "Не удалось проверить обновления: ${err.localizedMessage ?: "ошибка сети"}"
                            else "Update check failed: ${err.localizedMessage ?: "network error"}"
                        )
                    }
                }
            } finally {
                _isCheckingUpdates.value = false
            }
        }
    }

    fun hideReleaseDialog() {
        _isUpdateDialogVisible.value = false
        AppLogger.log("UI", "Окно обновления свёрнуто пользователем")
    }

    fun showReleaseDialogIfActive() {
        if (_availableRelease.value != null) {
            _isUpdateDialogVisible.value = true
        }
    }

    fun startAppUpdateDownload() {
        val release = _availableRelease.value ?: return
        val apkUrl = release.apkDownloadUrl ?: return
        if (_appUpdateDownloadProgress.value != null) return

        appUpdateJob?.cancel()
        _appUpdateError.value = null
        _downloadedAppApkFile.value = null
        _appUpdateDownloadProgress.value = 0f
        _appUpdateBytesProgress.value = 0L to -1L
        postAppUpdateProgressNotification(release.versionName, 0)

        appUpdateJob = viewModelScope.launch {
            val updatesDir = File(app.cacheDir, "updates")
            val safeVer = release.versionName.replace(Regex("[^0-9A-Za-z._-]"), "_").ifBlank { "latest" }
            val destFile = File(updatesDir, "Fini-$safeVer.apk")
            var lastNotifiedPct = -1
            GitHubUpdateChecker.downloadAppUpdateApk(
                downloadUrl = apkUrl,
                destFile = destFile,
                onProgress = { progress, downloaded, total ->
                    _appUpdateDownloadProgress.value = progress
                    _appUpdateBytesProgress.value = downloaded to total
                    val pct = (progress * 100f).toInt().coerceIn(0, 99)
                    if (pct != lastNotifiedPct) {
                        lastNotifiedPct = pct
                        postAppUpdateProgressNotification(release.versionName, pct)
                    }
                }
            ).onSuccess { file ->
                _appUpdateDownloadProgress.value = null
                _appUpdateBytesProgress.value = null
                _downloadedAppApkFile.value = file
                postAppUpdateCompletedNotification(release.versionName)
            }.onFailure { err ->
                if (err is kotlinx.coroutines.CancellationException) {
                    cancelAppUpdateNotification()
                    throw err
                }
                cancelAppUpdateNotification()
                _appUpdateDownloadProgress.value = null
                _appUpdateBytesProgress.value = null
                _appUpdateError.value = err.localizedMessage
                    ?: if (strings().isRussian) "Ошибка загрузки обновления" else "Update download failed"
            }
        }
    }

    fun dismissReleaseDialog(deleteCache: Boolean = true) {
        appUpdateJob?.cancel()
        appUpdateJob = null
        cancelAppUpdateNotification()
        _isUpdateDialogVisible.value = false
        _appUpdateDownloadProgress.value = null
        _appUpdateBytesProgress.value = null
        _appUpdateError.value = null
        _downloadedAppApkFile.value = null
        _availableRelease.value = null
        if (deleteCache) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { File(app.cacheDir, "updates").deleteRecursively() }
            }
        }
    }

    private fun canPostUpdateNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun createOpenUpdatePendingIntent(): PendingIntent {
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_UPDATE_DIALOG, true)
        }
        return PendingIntent.getActivity(
            app,
            401,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun postAppUpdateProgressNotification(versionName: String, percent: Int) {
        if (!canPostUpdateNotifications()) return
        runCatching {
            val nm = app.getSystemService(NotificationManager::class.java) ?: return
            val isRu = strings().isRussian
            val title = if (isRu) "Обновление приложения" else "App Update"
            val text = if (isRu) "FINI v$versionName • Скачано $percent%" else "FINI v$versionName • Downloaded $percent%"
            val notif = NotificationCompat.Builder(app, GRayApplication.UPDATE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_vpn)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(100, percent.coerceIn(0, 100), false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(createOpenUpdatePendingIntent())
                .build()
            nm.notify(APP_UPDATE_NOTIFICATION_ID, notif)
        }
    }

    private fun postAppUpdateCompletedNotification(versionName: String) {
        if (!canPostUpdateNotifications()) return
        runCatching {
            val nm = app.getSystemService(NotificationManager::class.java) ?: return
            val isRu = strings().isRussian
            val title = if (isRu) "Обновление приложения" else "App Update"
            val text = if (isRu) "FINI v$versionName загружено — нажмите для установки" else "FINI v$versionName downloaded — tap to install"
            val notif = NotificationCompat.Builder(app, GRayApplication.UPDATE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_vpn)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(createOpenUpdatePendingIntent())
                .build()
            nm.notify(APP_UPDATE_NOTIFICATION_ID, notif)
        }
    }

    private fun cancelAppUpdateNotification() {
        runCatching {
            app.getSystemService(NotificationManager::class.java)?.cancel(APP_UPDATE_NOTIFICATION_ID)
        }
    }

    fun fetchXrayCoreReleases() {
        if (_isLoadingCores.value) return
        viewModelScope.launch {
            _isLoadingCores.value = true
            try {
                GitHubUpdateChecker.fetchAvailableXrayCoreReleases()
                    .onSuccess { list -> _remoteCores.value = list }
                    .onFailure { err ->
                        emitMessage(
                            if (strings().isRussian) "Ошибка загрузки списка ядер Xray: ${err.localizedMessage ?: ""}"
                            else "Failed to fetch Xray core versions: ${err.localizedMessage ?: ""}"
                        )
                    }
            } finally {
                _isLoadingCores.value = false
            }
        }
    }

    fun setAppScalePreset(preset: AppScalePreset) = viewModelScope.launch {
        settingsStore.updateAppScalePreset(preset)
    }

    fun setSeasonalSnowEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateSeasonalSnowEnabled(enabled)
    }

    fun setHappSpoofEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateHappSpoofEnabled(enabled)
    }

    fun setSendDeviceHwid(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateSendDeviceHwid(enabled)
    }

    fun downloadCoreProfile(profile: XrayCoreProfile) {
        if (profile.id in _downloadingCoreProgress.value) return
        viewModelScope.launch {
            _downloadingCoreProgress.value = _downloadingCoreProgress.value + (profile.id to 0.05f)
            try {
                val coresDir = File(app.filesDir, "cores")
                GitHubUpdateChecker.downloadXrayCoreReleaseArchive(
                    profile = profile,
                    coresDir = coresDir,
                    onProgress = { progress ->
                        _downloadingCoreProgress.value = _downloadingCoreProgress.value + (profile.id to progress)
                    }
                ).onSuccess {
                    val current = repository.settingsFlow.first().savedCoreProfiles.filterNot { it.id == profile.id }
                    val saved = profile.copy(downloadedAt = System.currentTimeMillis())
                    settingsStore.updateSavedCoreProfiles(listOf(saved) + current)
                    emitMessage(
                        if (strings().isRussian) "Ядро ${profile.title} загружено"
                        else "Core ${profile.title} downloaded"
                    )
                }.onFailure { err ->
                    emitMessage(
                        if (strings().isRussian) "Ошибка скачивания ${profile.title}: ${err.localizedMessage ?: "сеть"}"
                        else "Download failed for ${profile.title}: ${err.localizedMessage ?: "network"}"
                    )
                }
            } finally {
                _downloadingCoreProgress.value = _downloadingCoreProgress.value - profile.id
            }
        }
    }

    fun selectXrayCoreProfile(profileId: String) {
        viewModelScope.launch {
            settingsStore.updateSelectedCoreId(profileId)
            val state = uiState.value
            val selectedProfile = state.settings.savedCoreProfiles.firstOrNull { it.id == profileId }
            val nextVersion = selectedProfile?.versionTag?.trimStart('v', 'V')
                ?: GRayApplication.DEFAULT_XRAY_VERSION
            VpnStateHolder.update { it.copy(xrayVersion = nextVersion) }
            if (state.vpn.status == VpnStatus.CONNECTED) {
                state.vpn.activeServer?.let { launchVpnService(it.id) }
            }
            emitMessage(
                if (strings().isRussian) "Выбрано ядро: $nextVersion"
                else "Selected core: $nextVersion"
            )
        }
    }

    fun deleteCoreProfile(profileId: String) {
        if (profileId == "builtin") return
        viewModelScope.launch {
            runCatching { File(File(app.filesDir, "cores"), "$profileId.zip").delete() }
            val s = repository.settingsFlow.first()
            val updated = s.savedCoreProfiles.filterNot { it.id == profileId }
            settingsStore.updateSavedCoreProfiles(updated)
            if (s.selectedCoreId == profileId) {
                settingsStore.updateSelectedCoreId("builtin")
            }
            emitMessage(if (strings().isRussian) "Профиль ядра удалён" else "Core profile removed")
        }
    }

    fun resetSettingsOnly() {
        viewModelScope.launch {
            settingsStore.resetSettingsToDefault(preserveServerRefs = true)
            app.applyAppIconStyle(AppIconStyle.DARK)
            AppLogger.setEnabled(false)
            VpnStateHolder.update { it.copy(xrayVersion = GRayApplication.DEFAULT_XRAY_VERSION) }
            emitMessage(
                if (strings().isRussian) "Настройки сброшены до значений по умолчанию"
                else "Settings reset to defaults"
            )
        }
    }

    fun deleteAllServersAndSubscriptions() {
        viewModelScope.launch {
            val status = uiState.value.vpn.status
            if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) {
                stopVpnService()
            }
            repository.deleteAllServersAndSubscriptions()
            emitMessage(
                if (strings().isRussian) "Все серверы и подписки удалены"
                else "All servers and subscriptions deleted"
            )
        }
    }

    fun fullReset() {
        viewModelScope.launch {
            val status = uiState.value.vpn.status
            if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) {
                stopVpnService()
            }
            repository.deleteAllServersAndSubscriptions()
            settingsStore.resetSettingsToDefault(preserveServerRefs = false)
            app.applyAppIconStyle(AppIconStyle.DARK)
            AppLogger.setEnabled(false)
            AppLogger.clear()
            VpnStateHolder.update { it.copy(xrayVersion = GRayApplication.DEFAULT_XRAY_VERSION) }
            emitMessage(
                if (strings().isRussian) "Выполнен полный сброс приложения"
                else "Full app reset completed"
            )
        }
    }

    fun logUiAction(message: String) {
        AppLogger.log("UI", message)
    }

    fun emitMessage(message: String) {
        _events.tryEmit(message)
    }

    fun clearVpnError() = VpnStateHolder.clearError()

    companion object {
        private const val APP_UPDATE_NOTIFICATION_ID = 7703
    }
}
