package com.example.vpn

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.example.GRayApplication
import com.example.MainActivity
import com.example.R
import com.example.core.logging.AppLogger
import com.example.core.network.PingEngine
import com.example.core.parser.XrayConfigBuilder
import com.example.data.local.MincifryWhitelist
import com.example.data.model.ActiveNetworkType
import com.example.data.model.AppLanguage
import com.example.data.model.AppRoutingMode
import com.example.data.model.AppSettings
import com.example.data.model.AutoDisconnectConfig
import com.example.data.model.PingMethod
import com.example.data.model.ServerConfig
import com.example.data.model.VpnStatus
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libXray.DialerController
import libXray.LibXray
import org.amnezia.awg.hevtunnel.TProxyService
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class GRayVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tunnelMutex = Mutex()
    private val commandGeneration = AtomicLong(0L)
    private var tunInterface: ParcelFileDescriptor? = null
    private var tickerJob: Job? = null
    private var idleCheckJob: Job? = null
    private var networkTransitionJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var screenReceiverRegistered = false
    private var connectedStartMs: Long = 0L
    private var currentServer: ServerConfig? = null
    private var activeRequestedServerId: String? = null
    @Volatile
    private var lastServicePhysicalNetwork: ActiveNetworkType? = null
    @Volatile
    private var cachedSettings: AppSettings = AppSettings()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> scheduleSleepInactivityCheck()
                Intent.ACTION_SCREEN_ON -> {
                    idleCheckJob?.cancel()
                    idleCheckJob = null
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching { Seq.setContext(applicationContext) }
        lastServicePhysicalNetwork = detectCurrentPhysicalNetworkType()
        val app = application as GRayApplication
        serviceScope.launch {
            app.container.repository.settingsFlow.collect { s ->
                val prev = cachedSettings
                cachedSettings = s
                AppLogger.setEnabled(s.logsEnabled)
                val status = VpnStateHolder.state.value.status
                val isActiveTunnel = status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING
                if (isActiveTunnel) {
                    val srv = currentServer ?: VpnStateHolder.state.value.activeServer
                    val st = VpnStateHolder.state.value
                    if (prev.showVpnStatusNotification != s.showVpnStatusNotification) {
                        if (s.showVpnStatusNotification && srv != null) {
                            startForegroundCompat(
                                buildNotification(
                                    server = srv,
                                    elapsedSec = st.elapsedSeconds,
                                    rxSpeedBps = st.rxSpeedBytesPerSec,
                                    txSpeedBps = st.txSpeedBytesPerSec,
                                    rxTotalBytes = st.rxBytes,
                                    txTotalBytes = st.txBytes,
                                    settings = s
                                )
                            )
                        } else {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
                        }
                    } else if (s.showVpnStatusNotification &&
                        srv != null &&
                        (prev.showSpeedInNotification != s.showSpeedInNotification || prev.language != s.language)
                    ) {
                        getSystemService(NotificationManager::class.java)?.notify(
                            NOTIFICATION_ID,
                            buildNotification(
                                server = srv,
                                elapsedSec = st.elapsedSeconds,
                                rxSpeedBps = st.rxSpeedBytesPerSec,
                                txSpeedBps = st.txSpeedBytesPerSec,
                                rxTotalBytes = st.rxBytes,
                                txTotalBytes = st.txBytes,
                                settings = s
                            )
                        )
                    }
                }
            }
        }
        registerPhysicalNetworkMonitor()
        registerScreenMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startedFromForegroundApi = intent?.getBooleanExtra(EXTRA_STARTED_IN_FOREGROUND, false) == true
        when (intent?.action) {
            ACTION_STOP -> {
                val gen = commandGeneration.incrementAndGet()
                AppLogger.log("VPN", "Команда остановки туннеля")
                VpnStateHolder.update {
                    it.copy(
                        sleepPausedServerId = null,
                        autoConnectedOnMobile = false
                    )
                }
                serviceScope.launch {
                    tunnelMutex.withLock {
                        if (commandGeneration.get() == gen) {
                            stopTunnelInternal(clearSleep = true)
                        }
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_SLEEP_RECONNECT -> {
                val gen = commandGeneration.incrementAndGet()
                val serverId = intent.getStringExtra(EXTRA_SERVER_ID)
                    ?: VpnStateHolder.state.value.sleepPausedServerId
                serviceScope.launch {
                    val app = application as GRayApplication
                    val target = serverId?.let { app.container.repository.getServerById(it) }
                        ?: app.container.repository.getAllServersOnce().firstOrNull()
                    if (target != null) {
                        VpnStateHolder.update { it.copy(sleepPausedServerId = null) }
                        startTunnelSerialized(
                            requestedServer = target,
                            autoMobile = false,
                            generation = gen,
                            startedFromForegroundApi = startedFromForegroundApi
                        )
                    } else if (startedFromForegroundApi) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
                return START_STICKY
            }
            ACTION_START, null -> {
                val gen = commandGeneration.incrementAndGet()
                val serverId = intent?.getStringExtra(EXTRA_SERVER_ID)
                val isAutoMobile = intent?.getBooleanExtra(EXTRA_AUTO_MOBILE, false) == true
                serviceScope.launch {
                    val app = application as GRayApplication
                    val repo = app.container.repository
                    val settings = repo.settingsFlow.first()
                    val networkType = detectCurrentPhysicalNetworkType()
                    lastServicePhysicalNetwork = networkType
                    VpnStateHolder.update { it.copy(activeNetwork = networkType) }

                    val resolvedServer = resolveTargetServer(
                        requestedId = serverId,
                        settings = settings,
                        networkType = networkType,
                        repo = repo
                    )
                    if (resolvedServer != null) {
                        startTunnelSerialized(
                            requestedServer = resolvedServer,
                            autoMobile = isAutoMobile,
                            generation = gen,
                            startedFromForegroundApi = startedFromForegroundApi
                        )
                    } else {
                        tunnelMutex.withLock {
                            stopTunnelInternal(clearSleep = true, errorMsg = "Выберите сервер перед подключением")
                        }
                    }
                }
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun resolveTargetServer(
        requestedId: String?,
        settings: AppSettings,
        networkType: ActiveNetworkType,
        repo: com.example.data.repository.VpnRepository
    ): ServerConfig? {
        if (requestedId != null) {
            repo.getServerById(requestedId)?.let { return it }
        }
        if (settings.autoSwitchServerByNetwork && settings.networkAutoSwitchActive) {
            val preferredId = when (networkType) {
                ActiveNetworkType.WIFI -> settings.wifiServerId
                ActiveNetworkType.MOBILE -> settings.mobileServerId
                ActiveNetworkType.OTHER -> null
            }
            if (preferredId != null) {
                repo.getServerById(preferredId)?.let { return it }
            }
        }
        val effectiveId = settings.selectedServerId
        if (effectiveId != null) {
            repo.getServerById(effectiveId)?.let { return it }
        }
        return repo.getAllServersOnce().firstOrNull()
    }

    private suspend fun resolveAutoSelectIfNeeded(
        candidate: ServerConfig,
        repo: com.example.data.repository.VpnRepository
    ): ServerConfig {
        if (!candidate.isAutoSelect || candidate.customJsonConfig.isNotBlank()) return candidate
        val pool = (candidate.subscriptionId?.let { repo.getServersBySubscriptionOnce(it) }
            ?: repo.getAllServersOnce()).filter { !it.isAutoSelect }
        if (pool.isEmpty()) return candidate

        // Pick existing fastest or probe quickly in parallel
        val measured = coroutineScope {
            pool.take(12).map { srv ->
                async {
                    val ms = PingEngine.measurePing(srv, PingMethod.TCP)
                    srv to ms
                }
            }.awaitAll()
        }
        val best = measured.filter { it.second != null }.minByOrNull { it.second!! }?.first
        return best ?: pool.first()
    }

    private suspend fun startTunnelSerialized(
        requestedServer: ServerConfig,
        autoMobile: Boolean,
        generation: Long = commandGeneration.incrementAndGet(),
        startedFromForegroundApi: Boolean = false
    ) {
        tunnelMutex.withLock {
            if (commandGeneration.get() != generation) return

            tickerJob?.cancel()
            tickerJob = null
            if (tunInterface != null ||
                VpnStateHolder.state.value.status == VpnStatus.CONNECTED ||
                VpnStateHolder.state.value.status == VpnStatus.CONNECTING
            ) {
                teardownNativeCore()
            }

            val app = application as GRayApplication
            val repo = app.container.repository
            val settings = repo.settingsFlow.first()
            cachedSettings = settings

            VpnStateHolder.update {
                it.copy(
                    status = VpnStatus.CONNECTING,
                    activeServer = requestedServer,
                    lastError = null,
                    elapsedSeconds = 0L,
                    rxBytes = 0L,
                    txBytes = 0L,
                    rxSpeedBytesPerSec = 0L,
                    txSpeedBytesPerSec = 0L,
                    autoConnectedOnMobile = autoMobile
                )
            }

            if (settings.showVpnStatusNotification) {
                startForegroundCompat(buildNotification(requestedServer, 0L, 0L, 0L, 0L, 0L, settings))
            } else {
                if (startedFromForegroundApi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundCompat(buildNotification(requestedServer, 0L, 0L, 0L, 0L, 0L, settings))
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
            }

            val server = resolveAutoSelectIfNeeded(requestedServer, repo)
            if (commandGeneration.get() != generation) return

            currentServer = server
            activeRequestedServerId = requestedServer.id
            app.container.settingsStore.updateSelectedServerId(requestedServer.id)

            val networkType = detectCurrentPhysicalNetworkType()
            lastServicePhysicalNetwork = networkType
            val socksPort = findFreeLocalPort()
            AppLogger.log(
                "VPN",
                "Запуск туннеля: ${server.name} (${server.protocol.displayName}/${server.subProtocolLabel}, ${server.host}:${server.port}), сеть=$networkType, socks=$socksPort"
            )

            val result = runCatching {
                MincifryWhitelist.init(applicationContext)
                val controller = DialerController { fd -> protect(fd.toInt()) }
                LibXray.registerDialerController(controller)
                LibXray.registerListenerController(controller)

                val configJson = XrayConfigBuilder.buildConfigJson(
                    server = server,
                    settings = settings,
                    socksPort = socksPort,
                    datDir = filesDir
                )
                AppLogger.log(
                    "XRAY",
                    "Конфиг Xray (${configJson.length} Б, core=${VpnStateHolder.state.value.xrayVersion}): " +
                        "lan=${settings.bypassLan}, ru=${settings.bypassRussianDomains}, " +
                        "mincifry=${settings.bypassMincifry}, custom=${settings.customRoutesEnabled}(${settings.customDirectDomains.size}), " +
                        "perApp=${settings.perAppEnabled}"
                )
                val requestB64 = LibXray.newXrayRunFromJSONRequest(filesDir.absolutePath, configJson)
                val rawResponse = LibXray.runXrayFromJSON(requestB64)
                parseXrayResponseError(rawResponse)?.let { err ->
                    AppLogger.log("XRAY", "Ошибка запуска Xray: $err")
                    error("Ошибка Xray: $err")
                }

                val pfd = establishVpnInterface(server, settings, networkType)
                    ?: error("Система не выдала разрешение на создание VPN-туннеля")
                tunInterface = pfd
                AppLogger.log("VPN", "TUN интерфейс создан (fd=${pfd.fd}, mtu=8500)")

                val yamlFile = File(cacheDir, "hev-socks5-tunnel.yaml")
                yamlFile.writeText(buildHevTunnelYaml(socksPort))
                TProxyService.TProxyStartService(yamlFile.absolutePath, pfd.fd)
                AppLogger.log("VPN", "hev-socks5-tunnel запущен; соединение установлено")
            }

            if (result.isFailure || commandGeneration.get() != generation) {
                val msg = if (result.isFailure) {
                    result.exceptionOrNull()?.localizedMessage ?: "Не удалось запустить туннель"
                } else null
                if (msg != null) AppLogger.log("VPN", "Сбой запуска: $msg")
                teardownNativeCore()
                currentServer = null
                if (commandGeneration.get() == generation) {
                    VpnStateHolder.update {
                        it.copy(
                            status = VpnStatus.DISCONNECTED,
                            activeServer = null,
                            lastError = msg,
                            elapsedSeconds = 0L,
                            rxBytes = 0L,
                            txBytes = 0L,
                            rxSpeedBytesPerSec = 0L,
                            txSpeedBytesPerSec = 0L,
                            autoConnectedOnMobile = false
                        )
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return
            }

            connectedStartMs = System.currentTimeMillis()
            VpnStateHolder.update {
                it.copy(
                    status = VpnStatus.CONNECTED,
                    activeServer = server,
                    activeNetwork = networkType,
                    lastError = null
                )
            }
            startTicker(server)
        }
    }

    private fun establishVpnInterface(
        server: ServerConfig,
        settings: AppSettings,
        networkType: ActiveNetworkType
    ): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("FINI • ${server.name}")
            .setMtu(8500)
            .addAddress("10.0.0.2", 30)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        if (settings.bypassLan && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lanExclusions = listOf(
                "10.0.0.0" to 8,
                "172.16.0.0" to 12,
                "192.168.0.0" to 16,
                "169.254.0.0" to 16
            )
            lanExclusions.forEach { (ip, prefix) ->
                runCatching {
                    builder.excludeRoute(IpPrefix(InetAddress.getByName(ip), prefix))
                }
            }
        }

        if (settings.perAppEnabled) {
            val (mode, packages) = if (settings.splitAppRulesByNetwork) {
                when (networkType) {
                    ActiveNetworkType.WIFI -> settings.wifiAppMode to settings.wifiAppPackages
                    ActiveNetworkType.MOBILE -> settings.mobileAppMode to settings.mobileAppPackages
                    ActiveNetworkType.OTHER -> settings.unifiedAppMode to settings.unifiedAppPackages
                }
            } else {
                settings.unifiedAppMode to settings.unifiedAppPackages
            }

            when (mode) {
                AppRoutingMode.ONLY_SELECTED -> {
                    var addedAny = false
                    packages.filter { it != packageName }.forEach { pkg ->
                        runCatching {
                            builder.addAllowedApplication(pkg)
                            addedAny = true
                        }
                    }
                    if (!addedAny) {
                        runCatching { builder.addDisallowedApplication(packageName) }
                    }
                }
                AppRoutingMode.BYPASS -> {
                    runCatching { builder.addDisallowedApplication(packageName) }
                    packages.filter { it != packageName }.forEach { pkg ->
                        runCatching { builder.addDisallowedApplication(pkg) }
                    }
                }
            }
        } else {
            runCatching { builder.addDisallowedApplication(packageName) }
        }

        return builder.establish()
    }

    private fun startTicker(server: ServerConfig) {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            val notifManager = getSystemService(NotificationManager::class.java)
            var prevRx = 0L
            var prevTx = 0L
            var prevNotifRx = 0L
            var prevNotifTx = 0L
            var tickCount = 0

            while (isActive) {
                val elapsed = ((System.currentTimeMillis() - connectedStartMs) / 1000L).coerceAtLeast(0L)
                val stats = runCatching { TProxyService.TProxyGetStats() }.getOrNull()
                val txBytes = stats?.getOrNull(1) ?: 0L
                val rxBytes = stats?.getOrNull(3) ?: 0L

                val rxSpeed1s = (rxBytes - prevRx).coerceAtLeast(0L)
                val txSpeed1s = (txBytes - prevTx).coerceAtLeast(0L)
                prevRx = rxBytes
                prevTx = txBytes

                VpnStateHolder.update {
                    it.copy(
                        elapsedSeconds = elapsed,
                        rxBytes = rxBytes,
                        txBytes = txBytes,
                        rxSpeedBytesPerSec = rxSpeed1s,
                        txSpeedBytesPerSec = txSpeed1s
                    )
                }

                // Update notification only when status notification AND speed details are enabled
                if (cachedSettings.showVpnStatusNotification &&
                    cachedSettings.showSpeedInNotification &&
                    (tickCount < 5 || tickCount % 5 == 0)
                ) {
                    val settings = cachedSettings
                    val (rxDisplaySpeed, txDisplaySpeed) = if (tickCount < 5) {
                        rxSpeed1s to txSpeed1s
                    } else {
                        ((rxBytes - prevNotifRx).coerceAtLeast(0L) / 5L) to
                            ((txBytes - prevNotifTx).coerceAtLeast(0L) / 5L)
                    }
                    prevNotifRx = rxBytes
                    prevNotifTx = txBytes
                    notifManager?.notify(
                        NOTIFICATION_ID,
                        buildNotification(server, elapsed, rxDisplaySpeed, txDisplaySpeed, rxBytes, txBytes, settings)
                    )
                }
                tickCount++
                delay(1000L)
            }
        }
    }

    private fun stopTunnelInternal(clearSleep: Boolean, errorMsg: String? = null) {
        val snapshot = VpnStateHolder.state.value
        val statsSummary = if (snapshot.elapsedSeconds > 0L || snapshot.rxBytes > 0L || snapshot.txBytes > 0L) {
            " (сессия=${formatDuration(snapshot.elapsedSeconds)}, RX=${formatBytesCompact(snapshot.rxBytes)}, TX=${formatBytesCompact(snapshot.txBytes)})"
        } else ""
        AppLogger.log("VPN", "Остановка туннеля$statsSummary${if (errorMsg != null) ": $errorMsg" else ""}")
        tickerJob?.cancel()
        tickerJob = null
        idleCheckJob?.cancel()
        idleCheckJob = null
        networkTransitionJob?.cancel()
        networkTransitionJob = null
        currentServer = null
        activeRequestedServerId = null
        if (clearSleep) {
            cancelWakeReconnectAlarm()
        }
        teardownNativeCore()
        VpnStateHolder.update {
            it.copy(
                status = VpnStatus.DISCONNECTED,
                activeServer = null,
                lastError = errorMsg ?: it.lastError,
                elapsedSeconds = 0L,
                rxBytes = 0L,
                txBytes = 0L,
                rxSpeedBytesPerSec = 0L,
                txSpeedBytesPerSec = 0L,
                sleepPausedServerId = if (clearSleep) null else it.sleepPausedServerId
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun teardownNativeCore() {
        runCatching { TProxyService.TProxyStopService() }
        runCatching { LibXray.stopXray() }
        runCatching { tunInterface?.close() }
        tunInterface = null
    }

    private fun buildHevTunnelYaml(socksPort: Int): String = """
        tunnel:
          mtu: 8500
          ipv4: 10.0.0.2
        socks5:
          port: $socksPort
          address: 127.0.0.1
          udp: 'udp'
        misc:
          task-stack-size: 81920
          connect-timeout: 5000
          read-write-timeout: 60000
          log-level: warn
    """.trimIndent()

    private fun findFreeLocalPort(): Int {
        return runCatching {
            ServerSocket(0).use { it.localPort }
        }.getOrDefault(10808)
    }

    private fun parseXrayResponseError(rawBase64OrJson: String?): String? {
        if (rawBase64OrJson.isNullOrBlank()) return null
        val decoded = runCatching {
            val bytes = Base64.decode(rawBase64OrJson.trim(), Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        }.getOrDefault(rawBase64OrJson)
        return runCatching {
            val json = JSONObject(decoded)
            val success = json.optBoolean("success", true)
            if (!success) json.optString("error").ifBlank { "Неизвестная ошибка ядра" } else null
        }.getOrNull()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        server: ServerConfig,
        elapsedSec: Long,
        rxSpeedBps: Long,
        txSpeedBps: Long,
        rxTotalBytes: Long,
        txTotalBytes: Long,
        settings: AppSettings
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, GRayVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isRu = isRussianLanguage(settings.language)
        val contentText = buildNotificationContentText(
            showDetails = settings.showSpeedInNotification,
            elapsedSec = elapsedSec,
            rxSpeedBps = rxSpeedBps,
            txSpeedBps = txSpeedBps,
            rxTotalBytes = rxTotalBytes,
            txTotalBytes = txTotalBytes,
            isRussian = isRu
        )

        return NotificationCompat.Builder(this, GRayApplication.VPN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_vpn)
            .setContentTitle("${server.countryFlag} ${server.name}")
            .setContentText(contentText)
            .setSubText("FINI • ${server.protocol.displayName} • ${server.subProtocolLabel}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.disconnect), stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun isRussianLanguage(language: AppLanguage): Boolean = when (language) {
        AppLanguage.RUSSIAN -> true
        AppLanguage.ENGLISH -> false
        AppLanguage.SYSTEM -> Locale.getDefault().language.equals("ru", ignoreCase = true)
    }

    private fun registerPhysicalNetworkMonitor() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = schedulePhysicalNetworkCheck()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                schedulePhysicalNetworkCheck()
            override fun onLost(network: Network) = schedulePhysicalNetworkCheck()
        }
        networkCallback = callback
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    private fun schedulePhysicalNetworkCheck() {
        networkTransitionJob?.cancel()
        networkTransitionJob = serviceScope.launch {
            delay(350L)
            val newType = detectCurrentPhysicalNetworkType()
            val previous = lastServicePhysicalNetwork
            lastServicePhysicalNetwork = newType
            VpnStateHolder.update { it.copy(activeNetwork = newType) }

            if (previous != newType &&
                newType != ActiveNetworkType.OTHER &&
                VpnStateHolder.state.value.status == VpnStatus.CONNECTED
            ) {
                handleNetworkTransition(newType)
            }
        }
    }

    private suspend fun handleNetworkTransition(newType: ActiveNetworkType) {
        AppLogger.log("NET", "Смена физической сети: $newType")
        val app = application as GRayApplication
        val repo = app.container.repository
        val settings = repo.settingsFlow.first()

        // Immediately disconnect on Wi-Fi if VPN was auto-connected on mobile and disconnectOnWifi is enabled
        if (newType == ActiveNetworkType.WIFI &&
            settings.disconnectOnWifi &&
            VpnStateHolder.state.value.autoConnectedOnMobile
        ) {
            AppLogger.log("NET", "Возврат на Wi-Fi — автоотключение VPN")
            val gen = commandGeneration.incrementAndGet()
            VpnStateHolder.update { it.copy(autoConnectedOnMobile = false) }
            tunnelMutex.withLock {
                if (commandGeneration.get() == gen) {
                    stopTunnelInternal(clearSleep = true)
                }
            }
            return
        }

        if (settings.autoSwitchServerByNetwork && settings.networkAutoSwitchActive) {
            val targetId = when (newType) {
                ActiveNetworkType.WIFI -> settings.wifiServerId
                ActiveNetworkType.MOBILE -> settings.mobileServerId
                ActiveNetworkType.OTHER -> null
            }
            if (targetId != null && targetId != activeRequestedServerId && targetId != currentServer?.id) {
                val nextServer = repo.getServerById(targetId)
                if (nextServer != null) {
                    AppLogger.log("NET", "Автосмена сервера на ${nextServer.name} ($newType)")
                    startTunnelSerialized(nextServer, autoMobile = VpnStateHolder.state.value.autoConnectedOnMobile)
                    return
                }
            }
        }

        if (settings.perAppEnabled && settings.splitAppRulesByNetwork) {
            currentServer?.let {
                startTunnelSerialized(it, autoMobile = VpnStateHolder.state.value.autoConnectedOnMobile)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun detectCurrentPhysicalNetworkType(): ActiveNetworkType {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return ActiveNetworkType.OTHER
        var hasWifi = false
        var hasMobile = false
        cm.allNetworks.forEach { net ->
            val caps = cm.getNetworkCapabilities(net) ?: return@forEach
            val notSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
            } else true
            if (notSuspended &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) hasWifi = true
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) hasMobile = true
            }
        }
        return when {
            hasWifi -> ActiveNetworkType.WIFI
            hasMobile -> ActiveNetworkType.MOBILE
            else -> ActiveNetworkType.OTHER
        }
    }

    private fun registerScreenMonitor() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun scheduleSleepInactivityCheck() {
        idleCheckJob?.cancel()
        idleCheckJob = serviceScope.launch {
            val app = application as GRayApplication
            val settings = app.container.repository.settingsFlow.first()
            val sleepCfg = settings.autoDisconnect
            if (!sleepCfg.enabled) return@launch
            if (VpnStateHolder.state.value.status != VpnStatus.CONNECTED) return@launch
            if (!isWithinSleepWindow(sleepCfg)) return@launch

            val pm = getSystemService(PowerManager::class.java)
            val startRx = TrafficStats.getTotalRxBytes()
            val startTx = TrafficStats.getTotalTxBytes()
            val waitMillis = sleepCfg.inactivityMinutes.coerceAtLeast(1) * 60_000L

            delay(waitMillis)

            if (pm?.isInteractive == true) return@launch
            val rxDelta = (TrafficStats.getTotalRxBytes() - startRx).coerceAtLeast(0L)
            val txDelta = (TrafficStats.getTotalTxBytes() - startTx).coerceAtLeast(0L)
            if (rxDelta + txDelta > 256 * 1024L) return@launch
            if (!isWithinSleepWindow(sleepCfg)) return@launch

            val pausedId = currentServer?.id
            VpnStateHolder.update { it.copy(sleepPausedServerId = pausedId) }
            scheduleWakeReconnectAlarm(sleepCfg, pausedId)
            val gen = commandGeneration.incrementAndGet()
            tunnelMutex.withLock {
                if (commandGeneration.get() == gen) {
                    stopTunnelInternal(clearSleep = false)
                }
            }
        }
    }

    private fun scheduleWakeReconnectAlarm(config: AutoDisconnectConfig, serverId: String?) {
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, config.endHour)
            set(Calendar.MINUTE, config.endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val intent = Intent(this, GRayVpnService::class.java).apply {
            action = ACTION_SLEEP_RECONNECT
            putExtra(EXTRA_SERVER_ID, serverId)
            putExtra(EXTRA_STARTED_IN_FOREGROUND, Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        }
        val pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                202,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                202,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
    }

    private fun cancelWakeReconnectAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(this, GRayVpnService::class.java).apply {
            action = ACTION_SLEEP_RECONNECT
        }
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val pending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, 202, intent, flags)
        } else {
            PendingIntent.getService(this, 202, intent, flags)
        }
        if (pending != null) {
            runCatching {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    private fun isWithinSleepWindow(config: AutoDisconnectConfig): Boolean {
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = config.startHour * 60 + config.startMinute
        val endMinutes = config.endHour * 60 + config.endMinute
        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    override fun onRevoke() {
        AppLogger.log("VPN", "Система отозвала разрешение VPN (onRevoke)")
        val gen = commandGeneration.incrementAndGet()
        serviceScope.launch {
            tunnelMutex.withLock {
                if (commandGeneration.get() == gen) {
                    stopTunnelInternal(clearSleep = true)
                }
            }
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        idleCheckJob?.cancel()
        networkTransitionJob?.cancel()
        networkCallback?.let { cb ->
            runCatching {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb)
            }
        }
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        teardownNativeCore()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.vpn.ACTION_START"
        const val ACTION_STOP = "com.example.vpn.ACTION_STOP"
        const val ACTION_SLEEP_RECONNECT = "com.example.vpn.ACTION_SLEEP_RECONNECT"
        const val EXTRA_SERVER_ID = "extra_server_id"
        const val EXTRA_AUTO_MOBILE = "extra_auto_mobile"
        const val EXTRA_STARTED_IN_FOREGROUND = "extra_started_in_foreground"
        private const val NOTIFICATION_ID = 7701

        fun buildNotificationContentText(
            showDetails: Boolean,
            elapsedSec: Long,
            rxSpeedBps: Long,
            txSpeedBps: Long,
            rxTotalBytes: Long,
            txTotalBytes: Long,
            isRussian: Boolean
        ): String {
            if (!showDetails) {
                return if (isRussian) "Подключено" else "Connected"
            }
            val formattedTime = formatDuration(elapsedSec)
            val totalStr = formatBytesCompact(rxTotalBytes + txTotalBytes)
            return "$formattedTime • ↓ ${formatSpeedCompact(rxSpeedBps)}  ↑ ${formatSpeedCompact(txSpeedBps)} • $totalStr"
        }

        fun formatDuration(totalSeconds: Long): String {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        }

        fun formatSpeedCompact(bytesPerSec: Long): String {
            if (bytesPerSec < 1024) return "$bytesPerSec Б/с"
            val kb = bytesPerSec / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.0f КБ/с", kb)
            val mb = kb / 1024.0
            return String.format(Locale.US, "%.1f МБ/с", mb)
        }

        fun formatBytesCompact(bytes: Long): String {
            if (bytes < 1024) return "$bytes Б"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.0f КБ", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(Locale.US, "%.1f МБ", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.2f ГБ", gb)
        }
    }
}
