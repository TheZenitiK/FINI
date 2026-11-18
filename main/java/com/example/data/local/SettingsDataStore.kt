package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AccentPreset
import com.example.data.model.AppIconStyle
import com.example.data.model.AppLanguage
import com.example.data.model.AppRoutingMode
import com.example.data.model.AppScalePreset
import com.example.data.model.AppSettings
import com.example.data.model.AutoDisconnectConfig
import com.example.data.model.AutoUpdateConfig
import com.example.data.model.PingDisplayMode
import com.example.data.model.PingMethod
import com.example.data.model.ThemeMode
import com.example.data.model.UpdateCheckInterval
import com.example.data.model.XrayCoreProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "gray_settings")

data class BootAppearanceCache(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val accentPreset: AccentPreset = AccentPreset.CYAN,
    val onboardingCompleted: Boolean = false
)

class SettingsDataStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val bootPrefs by lazy {
        context.getSharedPreferences("ui_boot_cache", Context.MODE_PRIVATE)
    }

    companion object {
        fun readBootAppearance(context: Context): BootAppearanceCache {
            val prefs = context.getSharedPreferences("ui_boot_cache", Context.MODE_PRIVATE)
            return BootAppearanceCache(
                language = prefs.getString("language", null)
                    ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                    ?: AppLanguage.SYSTEM,
                themeMode = prefs.getString("theme_mode", null)
                    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM,
                useDynamicColor = prefs.getBoolean("use_dynamic_color", false),
                accentPreset = prefs.getString("accent_preset", null)
                    ?.let { runCatching { AccentPreset.valueOf(it) }.getOrNull() }
                    ?: AccentPreset.CYAN,
                onboardingCompleted = prefs.getBoolean("onboarding_completed", false)
            )
        }

        fun readCachedXrayVersion(context: Context): String? {
            val prefs = context.getSharedPreferences("ui_boot_cache", Context.MODE_PRIVATE)
            return prefs.getString("xray_version", null)?.takeIf { it.isNotBlank() }
        }

        fun writeCachedXrayVersion(context: Context, version: String) {
            if (version.isBlank()) return
            context.getSharedPreferences("ui_boot_cache", Context.MODE_PRIVATE)
                .edit()
                .putString("xray_version", version)
                .apply()
        }
    }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val APP_ICON_STYLE = stringPreferencesKey("app_icon_style")
        val PING_METHOD = stringPreferencesKey("ping_method")
        val PING_DISPLAY = stringPreferencesKey("ping_display")
        val BYPASS_LAN = booleanPreferencesKey("bypass_lan")
        val BYPASS_RUSSIAN = booleanPreferencesKey("bypass_russian")
        val BYPASS_MINCIFRY = booleanPreferencesKey("bypass_mincifry")
        val CUSTOM_ROUTES_ENABLED = booleanPreferencesKey("custom_routes_enabled")
        val CUSTOM_DIRECT_DOMAINS = stringSetPreferencesKey("custom_mincifry_domains")
        val PER_APP_ENABLED = booleanPreferencesKey("per_app_enabled")
        val SPLIT_APPS_BY_NETWORK = booleanPreferencesKey("split_apps_by_network")
        val UNIFIED_APP_MODE = stringPreferencesKey("unified_app_mode")
        val UNIFIED_APP_PACKAGES = stringSetPreferencesKey("unified_app_packages")
        val WIFI_APP_MODE = stringPreferencesKey("wifi_app_mode")
        val WIFI_APP_PACKAGES = stringSetPreferencesKey("wifi_app_packages")
        val MOBILE_APP_MODE = stringPreferencesKey("mobile_app_mode")
        val MOBILE_APP_PACKAGES = stringSetPreferencesKey("mobile_app_packages")
        val AUTO_SWITCH_SERVER = booleanPreferencesKey("auto_switch_server")
        val NETWORK_AUTO_SWITCH_ACTIVE = booleanPreferencesKey("network_auto_switch_active")
        val WIFI_SERVER_ID = stringPreferencesKey("wifi_server_id")
        val MOBILE_SERVER_ID = stringPreferencesKey("mobile_server_id")
        val AUTO_CONNECT_ON_LAUNCH = booleanPreferencesKey("auto_connect_on_launch")
        val AUTO_CONNECT_ON_MOBILE = booleanPreferencesKey("auto_connect_on_mobile")
        val DISCONNECT_ON_WIFI = booleanPreferencesKey("prompt_disconnect_on_wifi")
        val SHOW_VPN_STATUS_NOTIFICATION = booleanPreferencesKey("show_vpn_status_notification")
        val SHOW_SPEED_IN_NOTIFICATION = booleanPreferencesKey("show_speed_in_notification")
        val AUTO_DISCONNECT_ENABLED = booleanPreferencesKey("auto_disconnect_enabled")
        val AUTO_DISCONNECT_START_HOUR = intPreferencesKey("auto_disconnect_start_hour")
        val AUTO_DISCONNECT_START_MIN = intPreferencesKey("auto_disconnect_start_min")
        val AUTO_DISCONNECT_END_HOUR = intPreferencesKey("auto_disconnect_end_hour")
        val AUTO_DISCONNECT_END_MIN = intPreferencesKey("auto_disconnect_end_min")
        val AUTO_DISCONNECT_IDLE_MIN = intPreferencesKey("auto_disconnect_idle_min")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val AUTO_CHECK_ON_LAUNCH = booleanPreferencesKey("auto_check_on_launch")
        val UPDATE_CHECK_INTERVAL = stringPreferencesKey("update_check_interval")
        val LAST_UPDATE_CHECK_TS = longPreferencesKey("last_update_check_ts")
        val SELECTED_CORE_ID = stringPreferencesKey("selected_core_id")
        val SAVED_CORE_PROFILES = stringPreferencesKey("saved_core_profiles")
        val COLLAPSED_SUB_IDS = stringSetPreferencesKey("collapsed_sub_ids")
        val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        val APP_SCALE_PRESET = stringPreferencesKey("app_scale_preset")
        val SEASONAL_SNOW_ENABLED = booleanPreferencesKey("seasonal_snow_enabled")
        val HAPP_SPOOF_ENABLED = booleanPreferencesKey("happ_spoof_enabled")
        val SEND_DEVICE_HWID = booleanPreferencesKey("send_device_hwid")
        val DEVICE_HWID = stringPreferencesKey("device_hwid")
        val LOGS_ENABLED = booleanPreferencesKey("logs_enabled")
        val EXPERIMENTAL_MODE_ENABLED = booleanPreferencesKey("experimental_mode_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PIN_CONNECT_CARD = booleanPreferencesKey("pin_connect_card")
        val ONLY_AVAILABLE_SERVERS = booleanPreferencesKey("only_available_servers")
        val SORT_BY_PING_ENABLED = booleanPreferencesKey("sort_by_ping_enabled")
    }

    private val fallbackSessionHwid: String by lazy {
        UUID.randomUUID().toString().replace("-", "").take(16)
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val savedCores = prefs[Keys.SAVED_CORE_PROFILES]?.let { raw ->
            runCatching { json.decodeFromString<List<XrayCoreProfile>>(raw) }.getOrNull()
        } ?: emptyList()

        AppSettings(
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.SYSTEM,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
            accentPreset = prefs[Keys.ACCENT_PRESET]?.let { runCatching { AccentPreset.valueOf(it) }.getOrNull() }
                ?: AccentPreset.CYAN,
            appIconStyle = prefs[Keys.APP_ICON_STYLE]?.let { runCatching { AppIconStyle.valueOf(it) }.getOrNull() }
                ?: AppIconStyle.DARK,
            pingMethod = prefs[Keys.PING_METHOD]?.let { runCatching { PingMethod.valueOf(it) }.getOrNull() }
                ?: PingMethod.TCP,
            pingDisplayMode = prefs[Keys.PING_DISPLAY]?.let { runCatching { PingDisplayMode.valueOf(it) }.getOrNull() }
                ?: PingDisplayMode.MILLISECONDS,
            bypassLan = prefs[Keys.BYPASS_LAN] ?: true,
            bypassRussianDomains = prefs[Keys.BYPASS_RUSSIAN] ?: false,
            bypassMincifry = prefs[Keys.BYPASS_MINCIFRY] ?: false,
            customRoutesEnabled = prefs[Keys.CUSTOM_ROUTES_ENABLED] ?: false,
            customDirectDomains = prefs[Keys.CUSTOM_DIRECT_DOMAINS] ?: emptySet(),
            perAppEnabled = prefs[Keys.PER_APP_ENABLED] ?: false,
            splitAppRulesByNetwork = prefs[Keys.SPLIT_APPS_BY_NETWORK] ?: false,
            unifiedAppMode = prefs[Keys.UNIFIED_APP_MODE]?.let { runCatching { AppRoutingMode.valueOf(it) }.getOrNull() }
                ?: AppRoutingMode.BYPASS,
            unifiedAppPackages = prefs[Keys.UNIFIED_APP_PACKAGES] ?: emptySet(),
            wifiAppMode = prefs[Keys.WIFI_APP_MODE]?.let { runCatching { AppRoutingMode.valueOf(it) }.getOrNull() }
                ?: AppRoutingMode.BYPASS,
            wifiAppPackages = prefs[Keys.WIFI_APP_PACKAGES] ?: emptySet(),
            mobileAppMode = prefs[Keys.MOBILE_APP_MODE]?.let { runCatching { AppRoutingMode.valueOf(it) }.getOrNull() }
                ?: AppRoutingMode.BYPASS,
            mobileAppPackages = prefs[Keys.MOBILE_APP_PACKAGES] ?: emptySet(),
            autoSwitchServerByNetwork = prefs[Keys.AUTO_SWITCH_SERVER] ?: false,
            networkAutoSwitchActive = prefs[Keys.NETWORK_AUTO_SWITCH_ACTIVE] ?: false,
            wifiServerId = prefs[Keys.WIFI_SERVER_ID]?.takeIf { it.isNotBlank() },
            mobileServerId = prefs[Keys.MOBILE_SERVER_ID]?.takeIf { it.isNotBlank() },
            autoConnectOnLaunch = prefs[Keys.AUTO_CONNECT_ON_LAUNCH] ?: false,
            autoConnectOnMobile = prefs[Keys.AUTO_CONNECT_ON_MOBILE] ?: false,
            disconnectOnWifi = prefs[Keys.DISCONNECT_ON_WIFI] ?: false,
            showVpnStatusNotification = prefs[Keys.SHOW_VPN_STATUS_NOTIFICATION] ?: true,
            showSpeedInNotification = prefs[Keys.SHOW_SPEED_IN_NOTIFICATION] ?: false,
            autoDisconnect = AutoDisconnectConfig(
                enabled = prefs[Keys.AUTO_DISCONNECT_ENABLED] ?: false,
                startHour = prefs[Keys.AUTO_DISCONNECT_START_HOUR] ?: 1,
                startMinute = prefs[Keys.AUTO_DISCONNECT_START_MIN] ?: 0,
                endHour = prefs[Keys.AUTO_DISCONNECT_END_HOUR] ?: 8,
                endMinute = prefs[Keys.AUTO_DISCONNECT_END_MIN] ?: 0,
                inactivityMinutes = prefs[Keys.AUTO_DISCONNECT_IDLE_MIN] ?: 2
            ),
            autoUpdate = AutoUpdateConfig(
                autoUpdateEnabled = prefs[Keys.AUTO_UPDATE_ENABLED] ?: false,
                autoCheckOnLaunch = prefs[Keys.AUTO_CHECK_ON_LAUNCH] ?: true,
                checkInterval = prefs[Keys.UPDATE_CHECK_INTERVAL]?.let {
                    runCatching { UpdateCheckInterval.valueOf(it) }.getOrNull()
                } ?: UpdateCheckInterval.HOURS_24,
                lastCheckTimestamp = prefs[Keys.LAST_UPDATE_CHECK_TS] ?: 0L
            ),
            selectedCoreId = prefs[Keys.SELECTED_CORE_ID] ?: "builtin",
            savedCoreProfiles = savedCores,
            collapsedSubscriptionIds = prefs[Keys.COLLAPSED_SUB_IDS] ?: emptySet(),
            selectedServerId = prefs[Keys.SELECTED_SERVER_ID]?.takeIf { it.isNotBlank() },
            appScalePreset = prefs[Keys.APP_SCALE_PRESET]?.let {
                runCatching { AppScalePreset.valueOf(it) }.getOrNull()
            } ?: AppScalePreset.NORMAL,
            seasonalSnowEnabled = prefs[Keys.SEASONAL_SNOW_ENABLED] ?: true,
            pinConnectCard = prefs[Keys.PIN_CONNECT_CARD] ?: false,
            happSpoofEnabled = prefs[Keys.HAPP_SPOOF_ENABLED] ?: false,
            sendDeviceHwid = prefs[Keys.SEND_DEVICE_HWID] ?: true,
            deviceHwid = prefs[Keys.DEVICE_HWID]?.takeIf { it.isNotBlank() } ?: fallbackSessionHwid,
            logsEnabled = prefs[Keys.LOGS_ENABLED] ?: false,
            experimentalModeEnabled = prefs[Keys.EXPERIMENTAL_MODE_ENABLED] ?: false,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            onlyAvailableServers = prefs[Keys.ONLY_AVAILABLE_SERVERS] ?: false,
            sortByPingEnabled = prefs[Keys.SORT_BY_PING_ENABLED] ?: false
        )
    }

    suspend fun updateLanguage(language: AppLanguage) {
        bootPrefs.edit().putString("language", language.name).apply()
        context.settingsDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun updateSelectedServerId(serverId: String?) {
        context.settingsDataStore.edit { prefs ->
            if (serverId == null) prefs.remove(Keys.SELECTED_SERVER_ID)
            else prefs[Keys.SELECTED_SERVER_ID] = serverId
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        bootPrefs.edit().putString("theme_mode", mode.name).apply()
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateUseDynamicColor(enabled: Boolean) {
        bootPrefs.edit().putBoolean("use_dynamic_color", enabled).apply()
        context.settingsDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun updateAccentPreset(preset: AccentPreset) {
        bootPrefs.edit().putString("accent_preset", preset.name).apply()
        context.settingsDataStore.edit { it[Keys.ACCENT_PRESET] = preset.name }
    }

    suspend fun updateAppIconStyle(style: AppIconStyle) {
        context.settingsDataStore.edit { it[Keys.APP_ICON_STYLE] = style.name }
    }

    suspend fun updatePingMethod(method: PingMethod) {
        context.settingsDataStore.edit { it[Keys.PING_METHOD] = method.name }
    }

    suspend fun updatePingDisplayMode(mode: PingDisplayMode) {
        context.settingsDataStore.edit { it[Keys.PING_DISPLAY] = mode.name }
    }

    suspend fun updateBypassLan(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BYPASS_LAN] = enabled }
    }

    suspend fun updateBypassRussianDomains(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BYPASS_RUSSIAN] = enabled }
    }

    suspend fun updateBypassMincifry(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BYPASS_MINCIFRY] = enabled }
    }

    suspend fun updateCustomRoutesEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.CUSTOM_ROUTES_ENABLED] = enabled }
    }

    suspend fun updateCustomDirectDomains(domains: Set<String>) {
        context.settingsDataStore.edit { it[Keys.CUSTOM_DIRECT_DOMAINS] = domains }
    }

    suspend fun updatePerAppEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.PER_APP_ENABLED] = enabled }
    }

    suspend fun updateSplitAppsByNetwork(split: Boolean) {
        context.settingsDataStore.edit { it[Keys.SPLIT_APPS_BY_NETWORK] = split }
    }

    suspend fun updateAppRule(
        target: NetworkRuleTarget,
        mode: AppRoutingMode? = null,
        packages: Set<String>? = null
    ) {
        context.settingsDataStore.edit { prefs ->
            when (target) {
                NetworkRuleTarget.UNIFIED -> {
                    mode?.let { prefs[Keys.UNIFIED_APP_MODE] = it.name }
                    packages?.let { prefs[Keys.UNIFIED_APP_PACKAGES] = it }
                }
                NetworkRuleTarget.WIFI -> {
                    mode?.let { prefs[Keys.WIFI_APP_MODE] = it.name }
                    packages?.let { prefs[Keys.WIFI_APP_PACKAGES] = it }
                }
                NetworkRuleTarget.MOBILE -> {
                    mode?.let { prefs[Keys.MOBILE_APP_MODE] = it.name }
                    packages?.let { prefs[Keys.MOBILE_APP_PACKAGES] = it }
                }
            }
        }
    }

    suspend fun updateAutoSwitchServer(enabled: Boolean) {
        context.settingsDataStore.edit {
            it[Keys.AUTO_SWITCH_SERVER] = enabled
            if (!enabled) {
                it[Keys.NETWORK_AUTO_SWITCH_ACTIVE] = false
            }
        }
    }

    suspend fun updateNetworkAutoSwitchActive(active: Boolean) {
        context.settingsDataStore.edit { it[Keys.NETWORK_AUTO_SWITCH_ACTIVE] = active }
    }

    suspend fun updateWifiServerId(serverId: String?) {
        context.settingsDataStore.edit {
            if (serverId == null) it.remove(Keys.WIFI_SERVER_ID)
            else it[Keys.WIFI_SERVER_ID] = serverId
        }
    }

    suspend fun updateMobileServerId(serverId: String?) {
        context.settingsDataStore.edit {
            if (serverId == null) it.remove(Keys.MOBILE_SERVER_ID)
            else it[Keys.MOBILE_SERVER_ID] = serverId
        }
    }

    suspend fun updateConnectionAutomation(
        autoConnectOnLaunch: Boolean? = null,
        autoConnectOnMobile: Boolean? = null,
        disconnectOnWifi: Boolean? = null,
        showVpnStatusNotification: Boolean? = null,
        showSpeedInNotification: Boolean? = null
    ) {
        context.settingsDataStore.edit { prefs ->
            autoConnectOnLaunch?.let { prefs[Keys.AUTO_CONNECT_ON_LAUNCH] = it }
            autoConnectOnMobile?.let { prefs[Keys.AUTO_CONNECT_ON_MOBILE] = it }
            disconnectOnWifi?.let { prefs[Keys.DISCONNECT_ON_WIFI] = it }
            showVpnStatusNotification?.let { prefs[Keys.SHOW_VPN_STATUS_NOTIFICATION] = it }
            showSpeedInNotification?.let { prefs[Keys.SHOW_SPEED_IN_NOTIFICATION] = it }
        }
    }

    suspend fun updateAutoDisconnect(config: AutoDisconnectConfig) {
        context.settingsDataStore.edit {
            it[Keys.AUTO_DISCONNECT_ENABLED] = config.enabled
            it[Keys.AUTO_DISCONNECT_START_HOUR] = config.startHour
            it[Keys.AUTO_DISCONNECT_START_MIN] = config.startMinute
            it[Keys.AUTO_DISCONNECT_END_HOUR] = config.endHour
            it[Keys.AUTO_DISCONNECT_END_MIN] = config.endMinute
            it[Keys.AUTO_DISCONNECT_IDLE_MIN] = config.inactivityMinutes
        }
    }

    suspend fun updateAutoUpdate(config: AutoUpdateConfig) {
        context.settingsDataStore.edit {
            it[Keys.AUTO_UPDATE_ENABLED] = config.autoUpdateEnabled
            it[Keys.AUTO_CHECK_ON_LAUNCH] = config.autoCheckOnLaunch
            it[Keys.UPDATE_CHECK_INTERVAL] = config.checkInterval.name
            it[Keys.LAST_UPDATE_CHECK_TS] = config.lastCheckTimestamp
        }
    }

    suspend fun updateSelectedCoreId(coreId: String) {
        context.settingsDataStore.edit { it[Keys.SELECTED_CORE_ID] = coreId }
    }

    suspend fun updateSavedCoreProfiles(profiles: List<XrayCoreProfile>) {
        context.settingsDataStore.edit {
            it[Keys.SAVED_CORE_PROFILES] = json.encodeToString(profiles)
        }
    }

    suspend fun toggleSubscriptionCollapsed(subscriptionId: String) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.COLLAPSED_SUB_IDS] ?: emptySet()
            prefs[Keys.COLLAPSED_SUB_IDS] =
                if (subscriptionId in current) current - subscriptionId else current + subscriptionId
        }
    }

    suspend fun updateAppScalePreset(preset: AppScalePreset) {
        context.settingsDataStore.edit { it[Keys.APP_SCALE_PRESET] = preset.name }
    }

    suspend fun updateSeasonalSnowEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SEASONAL_SNOW_ENABLED] = enabled }
    }

    suspend fun updateHappSpoofEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAPP_SPOOF_ENABLED] = enabled }
    }

    suspend fun updateSendDeviceHwid(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SEND_DEVICE_HWID] = enabled }
    }

    suspend fun updateLogsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.LOGS_ENABLED] = enabled }
    }

    suspend fun updateExperimentalModeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.EXPERIMENTAL_MODE_ENABLED] = enabled }
    }

    suspend fun updateOnboardingCompleted(completed: Boolean) {
        bootPrefs.edit().putBoolean("onboarding_completed", completed).apply()
        context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun updatePinConnectCard(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.PIN_CONNECT_CARD] = enabled }
    }

    suspend fun saveAppSelectorSettings(
        splitByNetwork: Boolean,
        unifiedMode: AppRoutingMode,
        unifiedPackages: Set<String>,
        wifiMode: AppRoutingMode,
        wifiPackages: Set<String>,
        mobileMode: AppRoutingMode,
        mobilePackages: Set<String>
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SPLIT_APPS_BY_NETWORK] = splitByNetwork
            prefs[Keys.UNIFIED_APP_MODE] = unifiedMode.name
            prefs[Keys.UNIFIED_APP_PACKAGES] = unifiedPackages
            prefs[Keys.WIFI_APP_MODE] = wifiMode.name
            prefs[Keys.WIFI_APP_PACKAGES] = wifiPackages
            prefs[Keys.MOBILE_APP_MODE] = mobileMode.name
            prefs[Keys.MOBILE_APP_PACKAGES] = mobilePackages
        }
    }

    suspend fun ensureDeviceHwidPersisted(): String {
        var resolved = fallbackSessionHwid
        context.settingsDataStore.edit { prefs ->
            val existing = prefs[Keys.DEVICE_HWID]?.takeIf { it.isNotBlank() }
            if (existing != null) {
                resolved = existing
            } else {
                prefs[Keys.DEVICE_HWID] = resolved
            }
        }
        return resolved
    }

    suspend fun updateOnlyAvailableServers(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONLY_AVAILABLE_SERVERS] = enabled }
    }

    suspend fun updateSortByPingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SORT_BY_PING_ENABLED] = enabled }
    }

    suspend fun clearServerReferences() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.SELECTED_SERVER_ID)
            prefs.remove(Keys.WIFI_SERVER_ID)
            prefs.remove(Keys.MOBILE_SERVER_ID)
            prefs.remove(Keys.COLLAPSED_SUB_IDS)
        }
    }

    suspend fun resetSettingsToDefault(preserveServerRefs: Boolean) {
        bootPrefs.edit().clear().apply()
        if (preserveServerRefs) {
            bootPrefs.edit().putBoolean("onboarding_completed", true).apply()
        }
        context.settingsDataStore.edit { prefs ->
            val keptHwid = prefs[Keys.DEVICE_HWID]
            val keptOnboarding = if (preserveServerRefs) (prefs[Keys.ONBOARDING_COMPLETED] ?: false) else false
            val keptSelected = if (preserveServerRefs) prefs[Keys.SELECTED_SERVER_ID] else null
            val keptWifi = if (preserveServerRefs) prefs[Keys.WIFI_SERVER_ID] else null
            val keptMobile = if (preserveServerRefs) prefs[Keys.MOBILE_SERVER_ID] else null
            val keptCollapsed = if (preserveServerRefs) prefs[Keys.COLLAPSED_SUB_IDS] else null

            prefs.clear()

            if (!keptHwid.isNullOrBlank()) prefs[Keys.DEVICE_HWID] = keptHwid
            if (keptOnboarding) prefs[Keys.ONBOARDING_COMPLETED] = true
            if (!keptSelected.isNullOrBlank()) prefs[Keys.SELECTED_SERVER_ID] = keptSelected
            if (!keptWifi.isNullOrBlank()) prefs[Keys.WIFI_SERVER_ID] = keptWifi
            if (!keptMobile.isNullOrBlank()) prefs[Keys.MOBILE_SERVER_ID] = keptMobile
            if (!keptCollapsed.isNullOrEmpty()) prefs[Keys.COLLAPSED_SUB_IDS] = keptCollapsed
        }
    }
}

enum class NetworkRuleTarget {
    UNIFIED,
    WIFI,
    MOBILE
}
