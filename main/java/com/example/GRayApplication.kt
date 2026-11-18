package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.util.Base64
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.model.ActiveNetworkType
import com.example.data.model.AppIconStyle
import com.example.data.model.VpnStatus
import com.example.data.repository.VpnRepository
import com.example.vpn.GRayVpnService
import com.example.vpn.VpnStateHolder
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.core.logging.AppLogger
import com.example.data.local.MincifryWhitelist
import java.io.File
import kotlinx.coroutines.Job
import libXray.LibXray
import org.json.JSONObject

class AppContainer(application: Application) {
    private val database = AppDatabase.getInstance(application)
    val settingsStore = SettingsDataStore(application)
    val repository = VpnRepository(
        serverDao = database.serverDao(),
        subscriptionDao = database.subscriptionDao(),
        settingsStore = settingsStore
    )
}

class GRayApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var networkCheckJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
        SettingsDataStore.readCachedXrayVersion(this)?.let { cachedVer ->
            VpnStateHolder.update { it.copy(xrayVersion = cachedVer) }
        }
        initDeferredStartupTasks()
        registerAppLevelNetworkAutomation()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val vpnChannel = NotificationChannel(
                VPN_CHANNEL_ID,
                getString(R.string.vpn_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_notification_channel_desc)
                setShowBadge(false)
            }
            val updateChannel = NotificationChannel(
                UPDATE_CHANNEL_ID,
                getString(R.string.app_update_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.app_update_channel_desc)
                setShowBadge(false)
            }
            nm?.createNotificationChannel(vpnChannel)
            nm?.createNotificationChannel(updateChannel)
        }
    }

    private fun initDeferredStartupTasks() {
        appScope.launch {
            val initial = runCatching { container.repository.settingsFlow.first() }.getOrNull()
            if (initial?.logsEnabled == true) {
                AppLogger.setEnabled(
                    true,
                    "Журнал событий включён"
                )
                AppLogger.log("APP", "Приложение FINI v${BuildConfig.VERSION_NAME} запущено")
            }

            // Defer heavy disk/native work until after initial UI frames render smoothly
            delay(650L)

            MincifryWhitelist.init(this@GRayApplication)
            runCatching { File(cacheDir, "updates").deleteRecursively() }

            runCatching {
                val settings = initial ?: container.repository.settingsFlow.first()
                val selectedProfile = settings.savedCoreProfiles.firstOrNull { it.id == settings.selectedCoreId }
                val version = if (selectedProfile != null) {
                    selectedProfile.versionTag.trimStart('v', 'V')
                } else {
                    SettingsDataStore.readCachedXrayVersion(this@GRayApplication) ?: run {
                        Seq.setContext(this@GRayApplication)
                        val raw = LibXray.xrayVersion()
                        val decoded = runCatching {
                            String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8)
                        }.getOrDefault(raw)
                        val parsed = runCatching {
                            JSONObject(decoded).optString("data").ifBlank { decoded }
                        }.getOrDefault(DEFAULT_XRAY_VERSION).trim()
                        val resolved = if (parsed.isBlank()) DEFAULT_XRAY_VERSION else parsed.trimStart('v', 'V')
                        SettingsDataStore.writeCachedXrayVersion(this@GRayApplication, resolved)
                        resolved
                    }
                }
                VpnStateHolder.update { it.copy(xrayVersion = version) }
                AppLogger.log("XRAY", "Xray metadata initialized: v$version")
            }.onFailure {
                VpnStateHolder.update { it.copy(xrayVersion = DEFAULT_XRAY_VERSION) }
            }
        }
    }

    private fun registerAppLevelNetworkAutomation() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = checkPhysicalNetworkState()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                checkPhysicalNetworkState()
            override fun onLost(network: Network) = checkPhysicalNetworkState()
        }
        runCatching { cm.registerNetworkCallback(req, cb) }
    }

    @Suppress("DEPRECATION")
    private fun checkPhysicalNetworkState() {
        networkCheckJob?.cancel()
        networkCheckJob = appScope.launch {
            delay(450L)
            val cm = getSystemService(ConnectivityManager::class.java) ?: return@launch
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
            val currentType = when {
                hasWifi -> ActiveNetworkType.WIFI
                hasMobile -> ActiveNetworkType.MOBILE
                else -> ActiveNetworkType.OTHER
            }
            val prevType = VpnStateHolder.state.value.activeNetwork
            VpnStateHolder.update { it.copy(activeNetwork = currentType) }

            // Auto-connect on Mobile when VPN is currently disconnected
            if (prevType != ActiveNetworkType.MOBILE &&
                currentType == ActiveNetworkType.MOBILE &&
                VpnStateHolder.state.value.status == VpnStatus.DISCONNECTED
            ) {
                val settings = container.repository.settingsFlow.first()
                if (settings.autoConnectOnMobile && VpnService.prepare(this@GRayApplication) == null) {
                    val hasAnyServer = container.repository.getAllServersOnce().isNotEmpty()
                    if (hasAnyServer) {
                        val intent = Intent(this@GRayApplication, GRayVpnService::class.java).apply {
                            action = GRayVpnService.ACTION_START
                            putExtra(GRayVpnService.EXTRA_AUTO_MOBILE, true)
                            putExtra(GRayVpnService.EXTRA_STARTED_IN_FOREGROUND, Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    }
                }
            }
        }
    }

    fun applyAppIconStyle(style: AppIconStyle) {
        val pm = packageManager
        val darkComponent = ComponentName(this, "com.example.MainActivityAliasDark")
        val whiteComponent = ComponentName(this, "com.example.MainActivityAliasWhite")
        val (enableComp, disableComp) = when (style) {
            AppIconStyle.DARK -> darkComponent to whiteComponent
            AppIconStyle.WHITE -> whiteComponent to darkComponent
        }
        runCatching {
            pm.setComponentEnabledSetting(
                enableComp,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                disableComp,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    companion object {
        const val VPN_CHANNEL_ID = "gray_vpn_status"
        const val UPDATE_CHANNEL_ID = "fini_app_updates"
        const val DEFAULT_XRAY_VERSION = "26.6.27"
    }
}
