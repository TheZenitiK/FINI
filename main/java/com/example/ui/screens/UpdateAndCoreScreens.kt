package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.data.model.AppSettings
import com.example.data.model.UpdateCheckInterval
import com.example.data.model.XrayCoreProfile
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusRed
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsScreen(
    settings: AppSettings,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val strings = LocalStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.connectionTitle) },
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
                item {
                    ConnectionToggleCard(
                        title = if (strings.isRussian) "Автоподключение при запуске" else "Auto-Connect on Launch",
                        subtitle = if (strings.isRussian) {
                            "При открытии приложения автоматически подключать последний выбранный сервер"
                        } else {
                            "Automatically connect the last selected server when opening FINI"
                        },
                        checked = settings.autoConnectOnLaunch,
                        testTag = "switch_auto_connect_launch",
                        onCheckedChange = { viewModel.setAutoConnectOnLaunch(it) }
                    )
                }

                item {
                    ConnectionToggleCard(
                        title = if (strings.isRussian) "Подключаться без Wi‑Fi" else "Connect Without Wi‑Fi",
                        subtitle = if (strings.isRussian) {
                            "Автоматически запускать VPN при переключении на мобильный интернет"
                        } else {
                            "Automatically enable VPN when switching to mobile data"
                        },
                        checked = settings.autoConnectOnMobile,
                        testTag = "switch_auto_connect_mobile",
                        onCheckedChange = { viewModel.setAutoConnectOnMobile(it) }
                    )
                }

                if (settings.autoConnectOnMobile) {
                    item {
                        ConnectionToggleCard(
                            title = if (strings.isRussian) "Отключаться на Wi‑Fi" else "Disconnect on Wi‑Fi",
                            subtitle = if (strings.isRussian) {
                                "Если VPN был включён на мобильной сети, автоматически отключать его при возврате на Wi‑Fi"
                            } else {
                                "Automatically disconnect VPN when returning to Wi‑Fi after mobile auto-connect"
                            },
                            checked = settings.disconnectOnWifi,
                            testTag = "switch_disconnect_wifi",
                            onCheckedChange = { viewModel.setDisconnectOnWifi(it) }
                        )
                    }
                }

                item {
                    ConnectionToggleCard(
                        title = if (strings.isRussian) {
                            "Показывать в строке уведомлений статус подключения"
                        } else {
                            "Show Connection Status in Notification Bar"
                        },
                        subtitle = if (strings.isRussian) {
                            "Отображать постоянное уведомление о состоянии VPN в строке статуса и шторке"
                        } else {
                            "Show persistent VPN status notification in the status bar and notification shade"
                        },
                        checked = settings.showVpnStatusNotification,
                        testTag = "switch_status_notification",
                        onCheckedChange = { viewModel.setShowVpnStatusNotification(it) }
                    )
                }

                item {
                    ConnectionToggleCard(
                        title = if (strings.isRussian) "Время, скорость и трафик в шторке" else "Time, Speed & Traffic in Shade",
                        subtitle = if (strings.isRussian) {
                            "Показывать длительность сессии, скорость и трафик в уведомлении (при выключении отображается только статус «Подключено»)"
                        } else {
                            "Shows elapsed time, speed and traffic in the notification (when off, shows Connected only)"
                        },
                        checked = settings.showSpeedInNotification,
                        enabled = settings.showVpnStatusNotification,
                        testTag = "switch_speed_notification",
                        onCheckedChange = { viewModel.setShowSpeedInNotification(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.alpha(if (enabled) 1f else 0.48f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XrayCoreScreen(
    settings: AppSettings,
    xrayVersion: String,
    remoteCores: List<XrayCoreProfile>,
    isLoadingCores: Boolean,
    downloadingCoreProgress: Map<String, Float>,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val strings = LocalStrings.current

    LaunchedEffect(Unit) {
        if (remoteCores.isEmpty()) {
            viewModel.fetchXrayCoreReleases()
        }
    }

    val savedIds = settings.savedCoreProfiles.map { it.id }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.xrayCoreTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchXrayCoreReleases() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh cores")
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
                        text = if (strings.isRussian) "Установленные профили" else "Installed Profiles",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Built-in core cannot be deleted
                item {
                    val isSelected = settings.selectedCoreId == "builtin"
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.selectXrayCoreProfile("builtin") }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.selectXrayCoreProfile("builtin") }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Rounded.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (strings.isRussian) "Встроенное ядро Xray (стандарт)" else "Built-in Xray Core (Default)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = xrayVersion.ifBlank { "libXray Built-in" },
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Saved user-added profiles (deletable)
                items(settings.savedCoreProfiles, key = { it.id }) { profile ->
                    val isSelected = settings.selectedCoreId == profile.id
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.selectXrayCoreProfile(profile.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.selectXrayCoreProfile(profile.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = profile.versionTag,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.deleteCoreProfile(profile.id) }) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = strings.delete,
                                    tint = StatusRed
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = if (strings.isRussian) "Доступные релизы Xray Core"
                        else "Available Xray Core Releases",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (isLoadingCores) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(remoteCores.filter { it.id !in savedIds }, key = { it.id }) { remote ->
                        val downloadProgress = downloadingCoreProgress[remote.id]
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(remote.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = remote.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (downloadProgress != null) {
                                        Text(
                                            text = "${(downloadProgress * 100f).roundToInt()}%",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        IconButton(onClick = { viewModel.downloadCoreProfile(remote) }) {
                                            Icon(
                                                Icons.Rounded.Download,
                                                contentDescription = if (strings.isRussian) "Скачать" else "Download",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                if (downloadProgress != null) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress.coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(50)),
                                        strokeCap = StrokeCap.Round
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoUpdateScreen(
    settings: AppSettings,
    isCheckingUpdates: Boolean,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val strings = LocalStrings.current
    val cfg = settings.autoUpdate

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.autoUpdateTitle) },
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
                item {
                    ConnectionToggleCard(
                        title = strings.autoUpdateTitle,
                        subtitle = if (strings.isRussian) {
                            "Предлагать установку новых релизов FINI с GitHub"
                        } else {
                            "Notify and prompt to install new FINI GitHub releases"
                        },
                        checked = cfg.autoUpdateEnabled,
                        testTag = "switch_auto_update",
                        onCheckedChange = {
                            viewModel.updateAutoUpdateConfig(cfg.copy(autoUpdateEnabled = it))
                        }
                    )
                }

                item {
                    ConnectionToggleCard(
                        title = if (strings.isRussian) "Автопроверка обновлений" else "Auto-Check on Launch",
                        subtitle = if (strings.isRussian) {
                            "Проверять наличие новой версии при запуске приложения по расписанию"
                        } else {
                            "Check for updates on launch according to the chosen interval"
                        },
                        checked = cfg.autoCheckOnLaunch,
                        testTag = "switch_auto_check_updates",
                        onCheckedChange = {
                            viewModel.updateAutoUpdateConfig(cfg.copy(autoCheckOnLaunch = it))
                        }
                    )
                }

                if (cfg.autoCheckOnLaunch) {
                    item {
                        Text(
                            text = if (strings.isRussian) "Частота проверки обновлений" else "Check Interval",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                UpdateCheckInterval.entries.forEach { interval ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                viewModel.updateAutoUpdateConfig(cfg.copy(checkInterval = interval))
                                            }
                                            .padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = cfg.checkInterval == interval,
                                            onClick = {
                                                viewModel.updateAutoUpdateConfig(cfg.copy(checkInterval = interval))
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(strings.intervalLabel(interval), style = MaterialTheme.typography.titleSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                if (cfg.lastCheckTimestamp > 0L) {
                    item {
                        val tsText = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                            .format(Date(cfg.lastCheckTimestamp))
                        Text(
                            text = if (strings.isRussian) "Последняя проверка: $tsText" else "Last checked: $tsText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.checkForAppUpdates(manual = true) },
                        enabled = !isCheckingUpdates,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.checkUpdatesTitle)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    logsEnabled: Boolean,
    logs: List<String>,
    onToggleEnabled: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.logsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (logs.isNotEmpty()) {
                                clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                                onShowMessage(
                                    if (strings.isRussian) "Логи скопированы (${logs.size})"
                                    else "Logs copied (${logs.size})"
                                )
                            } else {
                                onShowMessage(
                                    if (strings.isRussian) "Журнал пуст" else "Log buffer is empty"
                                )
                            }
                        },
                        modifier = Modifier.testTag("copy_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = strings.copy
                        )
                    }
                    IconButton(
                        onClick = {
                            onClearLogs()
                            onShowMessage(if (strings.isRussian) "Логи очищены" else "Logs cleared")
                        },
                        enabled = logs.isNotEmpty(),
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = strings.delete
                        )
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConnectionToggleCard(
                    title = if (strings.isRussian) "Запись логов" else "Enable Logging",
                    subtitle = if (strings.isRussian) {
                        "Сохраняет диагностические события VPN, ядра Xray и приложения"
                    } else {
                        "Records diagnostic events for VPN, Xray core, and the app"
                    },
                    checked = logsEnabled,
                    testTag = "switch_logs_enabled",
                    onCheckedChange = onToggleEnabled
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    !logsEnabled && strings.isRussian -> "Запись логов отключена. Включите переключатель выше, чтобы собирать диагностические события."
                                    !logsEnabled -> "Logging is disabled. Enable the switch above to record diagnostic events."
                                    strings.isRussian -> "Пока нет записей. Попробуйте подключиться к серверу или обновить подписку."
                                    else -> "No log entries yet. Try connecting or refreshing a subscription."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(logs.asReversed()) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
