package com.example.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.data.model.AccentPreset
import com.example.data.model.AppLanguage
import com.example.data.model.AppRoutingMode
import com.example.data.model.PingDisplayMode
import com.example.data.model.PingMethod
import com.example.data.model.ThemeMode
import com.example.data.model.UpdateCheckInterval
import java.util.Locale

data class AppStrings(
    val isRussian: Boolean,
    val homeBrandTitle: String = "FINI",
    val fullBrandTitle: String = "FINI",
    val navHome: String,
    val navSettings: String,
    val connect: String,
    val disconnect: String,
    val cancel: String,
    val statusProtected: String,
    val statusConnecting: String,
    val statusDisconnecting: String,
    val statusDisconnected: String,
    val networkWifi: String,
    val networkMobile: String,
    val networkOther: String,
    val noServersTitle: String,
    val noServersSubtitle: String,
    val pasteFromClipboard: String,
    val scanQrCode: String,
    val importFromFile: String,
    val favoriteServers: String,
    val standaloneServers: String,
    val subscriptionLabel: String,
    val serversShort: String,
    val updatedShort: String,
    val usedTraffic: String,
    val remainingTraffic: String,
    val unlimitedTraffic: String,
    val settingsTitle: String,
    val sectionNetwork: String,
    val sectionInterface: String,
    val sectionUpdates: String,
    val routingTitle: String,
    val connectionTitle: String,
    val connectionSubtitle: String,
    val pingTitle: String,
    val autoDisconnectTitle: String,
    val personalizationTitle: String,
    val languageTitle: String,
    val xrayCoreTitle: String,
    val xrayCoreSubtitle: String,
    val backgroundTitle: String,
    val backgroundSubtitle: String,
    val backgroundAction: String,
    val autoUpdateTitle: String,
    val checkUpdatesTitle: String,
    val checkUpdatesSubtitle: String,
    val aboutTitle: String,
    val developerTitle: String,
    val leadDeveloperLabel: String,
    val githubLabel: String,
    val telegramLabel: String,
    val back: String,
    val save: String,
    val delete: String,
    val close: String,
    val copy: String,
    val shareQr: String,
    val copyServer: String,
    val renameSubscription: String,
    val deleteServerTitle: String,
    val deleteSubTitle: String,
    val updateAvailableTitle: String,
    val updateNow: String,
    val later: String,
    val clientSpoofTitle: String,
    val clientSpoofSubtitle: String,
    val appScaleTitle: String,
    val scaleSmaller: String,
    val scaleNormal: String,
    val scaleLarger: String,
    val shareSubscription: String,
    val copySubscriptionUrl: String,
    val logsTitle: String,
    val logsSubtitle: String,
    val experimentalTitle: String,
    val experimentalSubtitle: String,
    val viewConfig: String,
    val unsavedChangesTitle: String,
    val unsavedChangesBody: String,
    val stay: String,
    val exit: String,
    val reset: String,
    val allSubscriptionServers: String,
    val subscriptionLinksTitle: String,
    val resetSettingsTitle: String,
    val minimize: String
) {
    fun themeName(mode: ThemeMode): String = when (mode) {
        ThemeMode.SYSTEM -> if (isRussian) "Системная" else "System"
        ThemeMode.LIGHT -> if (isRussian) "Светлая" else "Light"
        ThemeMode.DARK -> if (isRussian) "Тёмная" else "Dark"
        ThemeMode.AMOLED -> if (isRussian) "AMOLED (чёрная)" else "AMOLED (Pure Black)"
    }

    fun accentName(preset: AccentPreset): String = when (preset) {
        AccentPreset.CYAN -> if (isRussian) "Электрик" else "Cyan"
        AccentPreset.EMERALD -> if (isRussian) "Изумруд" else "Emerald"
        AccentPreset.INDIGO -> if (isRussian) "Индиго" else "Indigo"
        AccentPreset.CORAL -> if (isRussian) "Коралл" else "Coral"
        AccentPreset.AMBER -> if (isRussian) "Янтарь" else "Amber"
        AccentPreset.CRIMSON -> if (isRussian) "Рубин" else "Crimson"
    }

    fun pingMethodTitle(method: PingMethod): String = when (method) {
        PingMethod.TCP -> if (isRussian) "TCP пинг" else "TCP Ping"
        PingMethod.HTTP_GET -> "HTTP GET"
        PingMethod.ICMP -> if (isRussian) "ICMP пинг" else "ICMP Ping"
    }

    fun pingMethodSubtitle(method: PingMethod): String = when (method) {
        PingMethod.TCP -> if (isRussian) "Замер времени установки TCP-соединения до узла" else "Measures TCP handshake latency to the server"
        PingMethod.HTTP_GET -> if (isRussian) "Запрос HTTP GET с проверкой фактического отклика" else "Measures HTTP GET response latency"
        PingMethod.ICMP -> if (isRussian) "Системный ICMP-эхо запрос (ping) до адреса сервера" else "System ICMP echo ping to the server host"
    }

    fun pingDisplayTitle(mode: PingDisplayMode): String = when (mode) {
        PingDisplayMode.MILLISECONDS -> if (isRussian) "Миллисекунды (мс)" else "Milliseconds (ms)"
        PingDisplayMode.DOTS -> if (isRussian) "Цветные точки" else "Colored Dots"
    }

    fun pingDisplaySubtitle(mode: PingDisplayMode): String = when (mode) {
        PingDisplayMode.MILLISECONDS -> if (isRussian) "Отображать точное числовое значение задержки" else "Display exact numeric latency in ms"
        PingDisplayMode.DOTS -> if (isRussian) "Зелёная ≤ 1000 мс, жёлтая > 1000 мс, красная — нет ответа" else "Green ≤ 1000 ms, yellow > 1000 ms, red = no response"
    }

    fun appRoutingTitle(mode: AppRoutingMode): String = when (mode) {
        AppRoutingMode.ONLY_SELECTED -> if (isRussian) "Только выбранные" else "Only Selected"
        AppRoutingMode.BYPASS -> if (isRussian) "Режим обхода" else "Bypass Mode"
    }

    fun appRoutingDesc(mode: AppRoutingMode): String = when (mode) {
        AppRoutingMode.ONLY_SELECTED -> if (isRussian) {
            "VPN работает только для отмеченных приложений; остальные выходят напрямую"
        } else {
            "Only checked apps use VPN; all other apps connect directly"
        }
        AppRoutingMode.BYPASS -> if (isRussian) {
            "Выбранные приложения работают напрямую без VPN; для всех остальных VPN включён"
        } else {
            "Checked apps bypass VPN; all other apps route through VPN"
        }
    }

    fun intervalLabel(interval: UpdateCheckInterval): String = when (interval) {
        UpdateCheckInterval.HOURS_6 -> if (isRussian) "Раз в 6 часов" else "Every 6 hours"
        UpdateCheckInterval.HOURS_12 -> if (isRussian) "Раз в 12 часов" else "Every 12 hours"
        UpdateCheckInterval.HOURS_24 -> if (isRussian) "Раз в 24 часа" else "Every 24 hours"
        UpdateCheckInterval.HOURS_48 -> if (isRussian) "Раз в 48 часов" else "Every 48 hours"
    }
}

val RussianStrings = AppStrings(
    isRussian = true,
    navHome = "Главное",
    navSettings = "Настройки",
    connect = "Подключить",
    disconnect = "Отключить",
    cancel = "Отмена",
    statusProtected = "ПОДКЛЮЧЕНО",
    statusConnecting = "ПОДКЛЮЧЕНИЕ…",
    statusDisconnecting = "ОТКЛЮЧЕНИЕ…",
    statusDisconnected = "НЕ ПОДКЛЮЧЕНО",
    networkWifi = "Wi‑Fi",
    networkMobile = "Мобильная сеть",
    networkOther = "Сеть",
    noServersTitle = "Нет добавленных серверов",
    noServersSubtitle = "Вставьте ссылку на подписку или конфигурацию VLESS из буфера обмена, либо отсканируйте QR-код",
    pasteFromClipboard = "Вставить из буфера обмена",
    scanQrCode = "Сканировать QR-код",
    importFromFile = "Добавить из файла",
    favoriteServers = "Избранные серверы",
    standaloneServers = "Отдельные серверы",
    subscriptionLabel = "Подписка",
    serversShort = "серв.",
    updatedShort = "обн.",
    usedTraffic = "Использовано",
    remainingTraffic = "Осталось",
    unlimitedTraffic = "Безлимит",
    settingsTitle = "Настройки",
    sectionNetwork = "Сеть и туннель",
    sectionInterface = "Интерфейс и система",
    sectionUpdates = "Ядро и обновления",
    routingTitle = "Маршрутизация",
    connectionTitle = "Подключение",
    connectionSubtitle = "Автоподключение, мобильный режим, статус в уведомлениях",
    pingTitle = "Настройка пинга",
    autoDisconnectTitle = "Автоотключение VPN",
    personalizationTitle = "Персонализация",
    languageTitle = "Язык приложения",
    xrayCoreTitle = "Ядро Xray",
    xrayCoreSubtitle = "Версии ядра и управление профилями",
    backgroundTitle = "Работа в фоне",
    backgroundSubtitle = "Нажмите, чтобы разрешить фоновую работу без ограничений",
    backgroundAction = "Настроить",
    autoUpdateTitle = "Обновления",
    checkUpdatesTitle = "Проверить обновления",
    checkUpdatesSubtitle = "Сверить релизы FINI на GitHub",
    aboutTitle = "О приложении",
    developerTitle = "Разработчик",
    leadDeveloperLabel = "Главный разработчик TheZenitiK",
    githubLabel = "GitHub проекта",
    telegramLabel = "Telegram-канал разработчика",
    back = "Назад",
    save = "Сохранить",
    delete = "Удалить",
    close = "Закрыть",
    copy = "Копировать",
    shareQr = "Поделиться с помощью QR-кода",
    copyServer = "Скопировать сервер",
    renameSubscription = "Переименовать подписку",
    deleteServerTitle = "Удалить сервер?",
    deleteSubTitle = "Хотите ли вы правда удалить подписку?",
    updateAvailableTitle = "Доступно обновление",
    updateNow = "Обновить",
    later = "Позже",
    clientSpoofTitle = "Подмена клиентов",
    clientSpoofSubtitle = "User-Agent подписок, Happ Spoof и передача HWID",
    appScaleTitle = "Масштаб приложения",
    scaleSmaller = "Меньше",
    scaleNormal = "Обычный",
    scaleLarger = "Больше",
    shareSubscription = "Поделиться подпиской",
    copySubscriptionUrl = "Скопировать ссылку подписки",
    logsTitle = "Логи",
    logsSubtitle = "Журнал событий VPN, ядра Xray и приложения",
    experimentalTitle = "Экспериментальные настройки",
    experimentalSubtitle = "Версии ядра Xray и журнал событий",
    viewConfig = "Просмотр конфигурации",
    unsavedChangesTitle = "Вы не сохранили изменения",
    unsavedChangesBody = "Изменения в списке приложений не будут применены, если выйти без сохранения.",
    stay = "Остаться",
    exit = "Выйти",
    reset = "Сбросить",
    allSubscriptionServers = "Все серверы подписки",
    subscriptionLinksTitle = "Ссылки",
    resetSettingsTitle = "Сбросить настройки",
    minimize = "Свернуть"
)

val EnglishStrings = AppStrings(
    isRussian = false,
    navHome = "Home",
    navSettings = "Settings",
    connect = "Connect",
    disconnect = "Disconnect",
    cancel = "Cancel",
    statusProtected = "CONNECTED",
    statusConnecting = "CONNECTING…",
    statusDisconnecting = "DISCONNECTING…",
    statusDisconnected = "DISCONNECTED",
    networkWifi = "Wi‑Fi",
    networkMobile = "Mobile Data",
    networkOther = "Network",
    noServersTitle = "No Servers Added",
    noServersSubtitle = "Paste a subscription URL or VLESS configuration from clipboard, or scan a QR code",
    pasteFromClipboard = "Paste from Clipboard",
    scanQrCode = "Scan QR Code",
    importFromFile = "Import from File",
    favoriteServers = "Favorite Servers",
    standaloneServers = "Standalone Servers",
    subscriptionLabel = "Subscription",
    serversShort = "srv.",
    updatedShort = "upd.",
    usedTraffic = "Used",
    remainingTraffic = "Left",
    unlimitedTraffic = "Unlimited",
    settingsTitle = "Settings",
    sectionNetwork = "Network & Tunnel",
    sectionInterface = "Appearance & System",
    sectionUpdates = "Core & Updates",
    routingTitle = "Routing",
    connectionTitle = "Connection",
    connectionSubtitle = "Auto-connect, mobile data trigger, status notification",
    pingTitle = "Ping Settings",
    autoDisconnectTitle = "Auto-Disconnect",
    personalizationTitle = "Personalization",
    languageTitle = "App Language",
    xrayCoreTitle = "Xray Core",
    xrayCoreSubtitle = "Manage Xray core versions & profiles",
    backgroundTitle = "Background Execution",
    backgroundSubtitle = "Tap to allow unrestricted background VPN operation",
    backgroundAction = "Allow",
    autoUpdateTitle = "Updates",
    checkUpdatesTitle = "Check for Updates",
    checkUpdatesSubtitle = "Check latest FINI releases on GitHub",
    aboutTitle = "About",
    developerTitle = "Developer",
    leadDeveloperLabel = "Lead Developer TheZenitiK",
    githubLabel = "Project GitHub",
    telegramLabel = "Developer Telegram Channel",
    back = "Back",
    save = "Save",
    delete = "Delete",
    close = "Close",
    copy = "Copy",
    shareQr = "Share via QR Code",
    copyServer = "Copy Server",
    renameSubscription = "Rename Subscription",
    deleteServerTitle = "Delete Server?",
    deleteSubTitle = "Delete Subscription?",
    updateAvailableTitle = "Update Available",
    updateNow = "Update",
    later = "Later",
    clientSpoofTitle = "Client Spoofing",
    clientSpoofSubtitle = "Subscription User-Agent, Happ Spoof & HWID headers",
    appScaleTitle = "App Scale",
    scaleSmaller = "Smaller",
    scaleNormal = "Normal",
    scaleLarger = "Larger",
    shareSubscription = "Share Subscription",
    copySubscriptionUrl = "Copy Subscription Link",
    logsTitle = "Logs",
    logsSubtitle = "VPN, Xray core and app event journal",
    experimentalTitle = "Experimental Settings",
    experimentalSubtitle = "Xray Core versions and event logs",
    viewConfig = "View Configuration",
    unsavedChangesTitle = "You haven't saved changes",
    unsavedChangesBody = "Changes to the app list will be discarded if you leave without saving.",
    stay = "Stay",
    exit = "Exit",
    reset = "Reset",
    allSubscriptionServers = "All subscription servers",
    subscriptionLinksTitle = "Links",
    resetSettingsTitle = "Reset Settings",
    minimize = "Minimize"
)

fun resolveAppStrings(language: AppLanguage): AppStrings {
    val isRu = when (language) {
        AppLanguage.RUSSIAN -> true
        AppLanguage.ENGLISH -> false
        AppLanguage.SYSTEM -> Locale.getDefault().language.equals("ru", ignoreCase = true)
    }
    return if (isRu) RussianStrings else EnglishStrings
}

val LocalStrings = staticCompositionLocalOf { RussianStrings }
