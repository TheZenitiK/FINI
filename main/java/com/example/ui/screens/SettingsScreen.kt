package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.data.model.AppLanguage
import com.example.data.model.AppSettings
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.StatusRed
import java.util.Locale

private enum class PendingResetAction {
    SETTINGS_ONLY,
    SERVERS_AND_SUBS,
    FULL_RESET
}

private fun checkBatteryIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSelectLanguage: (AppLanguage) -> Unit,
    onOpenRouting: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenClientSpoof: () -> Unit,
    onOpenPing: () -> Unit,
    onOpenAutoDisconnect: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAutoUpdate: () -> Unit,
    onOpenExperimental: () -> Unit,
    onOpenAbout: () -> Unit,
    onResetSettingsOnly: () -> Unit,
    onDeleteAllServersAndSubs: () -> Unit,
    onFullReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isIgnoringBattery by remember { mutableStateOf(checkBatteryIgnored(context)) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var pendingResetConfirmation by remember { mutableStateOf<PendingResetAction?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = checkBatteryIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = strings.settingsTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SectionTitle(strings.sectionNetwork)
                }

                item {
                    val activeRulesCount = listOf(
                        settings.bypassLan,
                        settings.bypassRussianDomains,
                        settings.bypassMincifry,
                        settings.customRoutesEnabled && settings.customDirectDomains.isNotEmpty(),
                        settings.perAppEnabled,
                        settings.autoSwitchServerByNetwork
                    ).count { it }

                    SettingsRowCard(
                        icon = Icons.AutoMirrored.Rounded.AltRoute,
                        title = strings.routingTitle,
                        subtitle = if (strings.isRussian) {
                            "Приложения, автосмена сервера, Минцифры, Мои маршруты ($activeRulesCount акт.)"
                        } else {
                            "Split apps, network switch, allowlists, custom routes ($activeRulesCount active)"
                        },
                        testTag = "settings_routing_item",
                        onClick = onOpenRouting
                    )
                }

                item {
                    val connSummary = buildList {
                        if (settings.autoConnectOnLaunch) add(if (strings.isRussian) "При запуске" else "On launch")
                        if (settings.autoConnectOnMobile) add(if (strings.isRussian) "Без Wi‑Fi" else "Mobile")
                        if (settings.showVpnStatusNotification && settings.showSpeedInNotification) {
                            add(if (strings.isRussian) "Скорость в шторке" else "Shade speed")
                        }
                    }.joinToString(" • ").ifEmpty { strings.connectionSubtitle }

                    SettingsRowCard(
                        icon = Icons.Rounded.Bolt,
                        title = strings.connectionTitle,
                        subtitle = connSummary,
                        testTag = "settings_connection_item",
                        onClick = onOpenConnection
                    )
                }

                item {
                    SettingsRowCard(
                        icon = Icons.Rounded.Shield,
                        title = strings.clientSpoofTitle,
                        subtitle = buildString {
                            append(if (settings.happSpoofEnabled) "Happ Spoof" else "FINI")
                            append(" • ")
                            append(
                                if (settings.sendDeviceHwid) {
                                    if (strings.isRussian) "HWID вкл." else "HWID on"
                                } else {
                                    if (strings.isRussian) "HWID выкл." else "HWID off"
                                }
                            )
                        },
                        testTag = "settings_client_spoof_item",
                        onClick = onOpenClientSpoof
                    )
                }

                item {
                    SettingsRowCard(
                        icon = Icons.Rounded.Speed,
                        title = strings.pingTitle,
                        subtitle = "${strings.pingMethodTitle(settings.pingMethod)} • ${strings.pingDisplayTitle(settings.pingDisplayMode)}",
                        testTag = "settings_ping_item",
                        onClick = onOpenPing
                    )
                }

                item {
                    val sleepSubtitle = if (settings.autoDisconnect.enabled) {
                        String.format(
                            Locale.US,
                            if (strings.isRussian) "Активно: %02d:%02d — %02d:%02d (простой %d мин)"
                            else "Active: %02d:%02d — %02d:%02d (idle %d min)",
                            settings.autoDisconnect.startHour,
                            settings.autoDisconnect.startMinute,
                            settings.autoDisconnect.endHour,
                            settings.autoDisconnect.endMinute,
                            settings.autoDisconnect.inactivityMinutes
                        )
                    } else {
                        if (strings.isRussian) "Выключено • Умное отключение ночью при бездействии"
                        else "Off • Smart night disconnect when idle"
                    }
                    SettingsRowCard(
                        icon = Icons.Rounded.Bedtime,
                        title = strings.autoDisconnectTitle,
                        subtitle = sleepSubtitle,
                        testTag = "settings_sleep_item",
                        onClick = onOpenAutoDisconnect
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionTitle(strings.sectionInterface)
                }

                item {
                    SettingsRowCard(
                        icon = Icons.Rounded.Palette,
                        title = strings.personalizationTitle,
                        subtitle = "${strings.themeName(settings.themeMode)} • ${
                            if (settings.useDynamicColor) "Material You" else strings.accentName(settings.accentPreset)
                        }",
                        testTag = "settings_appearance_item",
                        onClick = onOpenAppearance
                    )
                }

                item {
                    val langLabel = when (settings.language) {
                        AppLanguage.SYSTEM -> if (strings.isRussian) "Системный" else "System Default"
                        AppLanguage.RUSSIAN -> "Русский"
                        AppLanguage.ENGLISH -> "English"
                    }
                    SettingsRowCard(
                        icon = Icons.Rounded.Language,
                        title = strings.languageTitle,
                        subtitle = langLabel,
                        testTag = "settings_language_item",
                        onClick = { showLanguageDialog = true }
                    )
                }

                // Shown ONLY if battery optimization is NOT yet ignored
                if (!isIgnoringBattery) {
                    item {
                        SettingsRowCard(
                            icon = Icons.Rounded.BatteryChargingFull,
                            title = strings.backgroundTitle,
                            subtitle = strings.backgroundSubtitle,
                            badgeText = strings.backgroundAction,
                            badgeColor = MaterialTheme.colorScheme.primary,
                            testTag = "settings_background_item",
                            onClick = {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                runCatching { context.startActivity(intent) }
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionTitle(strings.sectionUpdates)
                }

                item {
                    val updateStatus = if (settings.autoUpdate.autoUpdateEnabled) {
                        "${if (strings.isRussian) "Включено" else "On"} • ${strings.intervalLabel(settings.autoUpdate.checkInterval)} • v${BuildConfig.VERSION_NAME}"
                    } else {
                        "v${BuildConfig.VERSION_NAME} • ${strings.intervalLabel(settings.autoUpdate.checkInterval)}"
                    }
                    SettingsRowCard(
                        icon = Icons.Rounded.Update,
                        title = strings.autoUpdateTitle,
                        subtitle = updateStatus,
                        testTag = "settings_auto_update_item",
                        onClick = onOpenAutoUpdate
                    )
                }

                item {
                    SettingsRowCard(
                        icon = Icons.Rounded.Science,
                        title = strings.experimentalTitle,
                        subtitle = strings.experimentalSubtitle,
                        badgeText = if (settings.experimentalModeEnabled) "ON" else null,
                        badgeColor = MaterialTheme.colorScheme.primary,
                        testTag = "settings_experimental_item",
                        onClick = onOpenExperimental
                    )
                }

                item {
                    SettingsRowCard(
                        icon = Icons.Rounded.Info,
                        title = strings.aboutTitle,
                        subtitle = "${strings.fullBrandTitle} • ${strings.leadDeveloperLabel}",
                        testTag = "settings_about_item",
                        onClick = onOpenAbout
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { showResetDialog = true }
                            .testTag("settings_reset_button"),
                        shape = RoundedCornerShape(18.dp),
                        color = StatusRed.copy(alpha = 0.11f),
                        border = BorderStroke(1.5.dp, StatusRed.copy(alpha = 0.80f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = StatusRed.copy(alpha = 0.16f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.RestartAlt,
                                        contentDescription = strings.resetSettingsTitle,
                                        tint = StatusRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.resetSettingsTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StatusRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (strings.isRussian) {
                                        "Сброс параметров, очистка серверов или полный сброс"
                                    } else {
                                        "Reset preferences, wipe servers, or full factory reset"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusRed.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(14.dp)) }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = strings.resetSettingsTitle,
                    color = StatusRed
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResetOptionCard(
                        title = if (strings.isRussian) "Сбросить до настроек по умолчанию"
                        else "Reset to Default Settings",
                        subtitle = if (strings.isRussian) {
                            "Сбросит все настройки интерфейса, сети и ядра. Серверы и подписки останутся"
                        } else {
                            "Resets all app settings. Servers and subscriptions are preserved"
                        },
                        isDanger = false,
                        testTag = "reset_option_settings",
                        onClick = {
                            showResetDialog = false
                            pendingResetConfirmation = PendingResetAction.SETTINGS_ONLY
                        }
                    )
                    ResetOptionCard(
                        title = if (strings.isRussian) "Удаление серверов и подписок"
                        else "Delete All Servers & Subscriptions",
                        subtitle = if (strings.isRussian) {
                            "Остановит VPN и удалит все добавленные серверы и подписки. Настройки сохранятся"
                        } else {
                            "Stops VPN and deletes all servers and subscriptions. Settings are preserved"
                        },
                        isDanger = false,
                        testTag = "reset_option_servers",
                        onClick = {
                            showResetDialog = false
                            pendingResetConfirmation = PendingResetAction.SERVERS_AND_SUBS
                        }
                    )
                    ResetOptionCard(
                        title = if (strings.isRussian) "Полный сброс" else "Full Reset",
                        subtitle = if (strings.isRussian) {
                            "Остановит VPN, сбросит все настройки и удалит все серверы и подписки"
                        } else {
                            "Stops VPN, resets all settings, and deletes all servers and subscriptions"
                        },
                        isDanger = true,
                        testTag = "reset_option_full",
                        onClick = {
                            showResetDialog = false
                            pendingResetConfirmation = PendingResetAction.FULL_RESET
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    pendingResetConfirmation?.let { action ->
        val (confirmTitle, confirmMessage) = when (action) {
            PendingResetAction.SETTINGS_ONLY -> {
                if (strings.isRussian) {
                    "Сбросить настройки?" to
                        "Вы действительно хотите сбросить все настройки приложения до значений по умолчанию? Серверы и подписки останутся."
                } else {
                    "Reset Settings?" to
                        "Are you sure you want to reset all app settings to default values? Your servers and subscriptions will remain."
                }
            }
            PendingResetAction.SERVERS_AND_SUBS -> {
                if (strings.isRussian) {
                    "Удалить серверы и подписки?" to
                        "Вы действительно хотите остановить VPN и удалить все серверы и подписки? Настройки приложения сохранятся."
                } else {
                    "Delete Servers & Subscriptions?" to
                        "Are you sure you want to stop VPN and delete all servers and subscriptions? App settings will be preserved."
                }
            }
            PendingResetAction.FULL_RESET -> {
                if (strings.isRussian) {
                    "Выполнить полный сброс?" to
                        "Вы действительно хотите сбросить все настройки и удалить все серверы, подписки и логи? Это действие нельзя отменить."
                } else {
                    "Perform Full Reset?" to
                        "Are you sure you want to reset all settings and delete all servers, subscriptions, and logs? This cannot be undone."
                }
            }
        }
        AlertDialog(
            onDismissRequest = { pendingResetConfirmation = null },
            title = { Text(confirmTitle, color = StatusRed) },
            text = { Text(confirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingResetConfirmation = null
                        when (action) {
                            PendingResetAction.SETTINGS_ONLY -> onResetSettingsOnly()
                            PendingResetAction.SERVERS_AND_SUBS -> onDeleteAllServersAndSubs()
                            PendingResetAction.FULL_RESET -> onFullReset()
                        }
                    },
                    modifier = Modifier.testTag("reset_confirm_yes")
                ) {
                    Text(
                        text = if (strings.isRussian) "Да" else "Yes",
                        color = StatusRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingResetConfirmation = null },
                    modifier = Modifier.testTag("reset_confirm_no")
                ) {
                    Text(if (strings.isRussian) "Нет" else "No")
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(strings.languageTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val options = listOf(
                        AppLanguage.SYSTEM to (if (strings.isRussian) "Как в системе" else "System Default"),
                        AppLanguage.RUSSIAN to "Русский",
                        AppLanguage.ENGLISH to "English"
                    )
                    options.forEach { (lang, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectLanguage(lang)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.language == lang,
                                onClick = {
                                    onSelectLanguage(lang)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(strings.close)
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !isLoading, onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = badgeColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResetOptionCard(
    title: String,
    subtitle: String,
    isDanger: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = if (isDanger) StatusRed.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isDanger) StatusRed.copy(alpha = 0.85f)
            else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isDanger) StatusRed else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
