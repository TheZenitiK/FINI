package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.core.network.QrCodeUtils
import com.example.data.model.ServerConfig
import com.example.data.model.Subscription
import com.example.data.model.formatSubscriptionRemainingTime
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConfigBottomSheet(
    onDismiss: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onScanQrCode: () -> Unit,
    onImportFromFile: () -> Unit,
    onImportManualText: (String) -> Unit
) {
    val strings = LocalStrings.current
    var manualInput by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (strings.isRussian) "Добавить конфигурацию" else "Add Configuration",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = if (strings.isRussian) {
                    "Поддерживаются VLESS (Reality, XHTTP/Xhttp, gRPC), Hysteria 2, VMess, Trojan, Shadowsocks, JSON, YAML и ссылки подписок"
                } else {
                    "Supports VLESS (Reality, XHTTP/Xhttp, gRPC), Hysteria 2, VMess, Trojan, Shadowsocks, JSON, YAML and subscription URLs"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onPasteFromClipboard()
                    }
                    .testTag("sheet_paste_clipboard")
            ) {
                ListItem(
                    headlineContent = { Text(strings.pasteFromClipboard) },
                    supportingContent = {
                        Text(
                            if (strings.isRussian) "Один сервер или ссылка на подписку"
                            else "Single server or subscription URL"
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = strings.pasteFromClipboard,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onScanQrCode()
                    }
                    .testTag("sheet_scan_qr")
            ) {
                ListItem(
                    headlineContent = { Text(strings.scanQrCode) },
                    supportingContent = {
                        Text(
                            if (strings.isRussian) "Наведите камеру на QR-код сервера или подписки"
                            else "Point camera at a server or subscription QR code"
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = strings.scanQrCode,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onImportFromFile()
                    }
                    .testTag("sheet_import_file")
            ) {
                ListItem(
                    headlineContent = { Text(strings.importFromFile) },
                    supportingContent = {
                        Text(
                            if (strings.isRussian) "Файл конфигурации (.txt, .json, .yaml, .conf)"
                            else "Config file (.txt, .json, .yaml, .conf)"
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.UploadFile,
                            contentDescription = strings.importFromFile,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            OutlinedTextField(
                value = manualInput,
                onValueChange = { manualInput = it },
                label = {
                    Text(
                        if (strings.isRussian) "Или введите vless:// / https:// вручную"
                        else "Or paste vless:// / https:// manually"
                    )
                },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_import_input")
            )

            if (manualInput.isNotBlank()) {
                Button(
                    onClick = {
                        onImportManualText(manualInput)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_import_submit")
                ) {
                    Text(if (strings.isRussian) "Импортировать" else "Import")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareServerBottomSheet(
    server: ServerConfig,
    onDismiss: () -> Unit,
    onShowQrCode: () -> Unit,
    onCopyServer: () -> Unit
) {
    val strings = LocalStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "${server.countryFlag} ${server.name}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "${server.protocol.displayName} • ${server.subProtocolLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onShowQrCode()
                    }
                    .testTag("share_option_qr")
            ) {
                ListItem(
                    headlineContent = { Text(strings.shareQr) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.QrCode,
                            contentDescription = strings.shareQr,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onCopyServer()
                    }
                    .testTag("share_option_copy")
            ) {
                ListItem(
                    headlineContent = { Text(strings.copyServer) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = strings.copyServer,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSubscriptionBottomSheet(
    subscription: Subscription,
    onDismiss: () -> Unit,
    onShowQrCode: () -> Unit,
    onCopyUrl: () -> Unit
) {
    val strings = LocalStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = subscription.name,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = strings.shareSubscription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onShowQrCode()
                    }
                    .testTag("share_sub_option_qr")
            ) {
                ListItem(
                    headlineContent = { Text(strings.shareQr) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.QrCode,
                            contentDescription = strings.shareQr,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onCopyUrl()
                    }
                    .testTag("share_sub_option_copy")
            ) {
                ListItem(
                    headlineContent = { Text(strings.copySubscriptionUrl) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = strings.copySubscriptionUrl,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ShareQrDialog(
    title: String,
    subtitle: String,
    rawUri: String,
    onDismiss: () -> Unit,
    onCopyUri: () -> Unit
) {
    val strings = LocalStrings.current
    val qrBitmap = remember(rawUri) {
        QrCodeUtils.generateQrBitmap(rawUri, 720)?.asImageBitmap()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(236.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "QR $title",
                            modifier = Modifier.size(212.dp)
                        )
                    }
                } else {
                    Text(
                        text = if (strings.isRussian) "Не удалось сгенерировать QR-код" else "Failed to generate QR code",
                        color = StatusRed,
                        fontSize = 14.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyUri,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.copy)
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.close)
                    }
                }
            }
        }
    }
}

@Composable
fun RenameSubscriptionDialog(
    subscription: Subscription,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val strings = LocalStrings.current
    var name by remember(subscription.id) { mutableStateOf(subscription.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.renameSubscription) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(if (strings.isRussian) "Название VPN-подписки" else "Subscription name")
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_subscription_input")
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name)
                    onDismiss()
                },
                enabled = name.isNotBlank()
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusRed,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text(strings.delete)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_button")
            ) {
                Text(strings.cancel)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionSettingsDialog(
    subscription: Subscription,
    servers: List<ServerConfig>,
    onDismiss: () -> Unit,
    onSave: (newName: String, newUrl: String, intervalHours: Int, autoUpdateOnLaunch: Boolean, pingOnLaunch: Boolean) -> Unit,
    onCopyText: (text: String, message: String) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    var name by remember(subscription.id) { mutableStateOf(subscription.name) }
    var url by remember(subscription.id) { mutableStateOf(subscription.url) }
    var intervalHours by remember(subscription.id) { mutableIntStateOf(subscription.autoUpdateIntervalHours) }
    var updateOnLaunch by remember(subscription.id) { mutableStateOf(subscription.autoUpdateOnLaunch) }
    var pingOnLaunch by remember(subscription.id) { mutableStateOf(subscription.pingOnLaunch) }
    var showAllLinksDialog by remember { mutableStateOf(false) }

    val intervalOptions = remember { listOf(0, 1, 2, 6, 12, 24) }
    val updatedText = remember(subscription.lastUpdatedAt) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(subscription.lastUpdatedAt))
    }
    val expireMs = remember(subscription.expireTimestamp) { subscription.normalizedExpireMillis() }
    val remainingTimeText = remember(subscription.expireTimestamp, strings.isRussian) {
        formatSubscriptionRemainingTime(subscription.expireTimestamp, strings.isRussian)
    }
    val expireDateText = remember(expireMs) {
        if (expireMs > 0L) {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(expireMs))
        } else null
    }
    val trafficText = remember(subscription.usedBytes, subscription.totalBytes, strings.isRussian) {
        val usedStr = formatBytes(subscription.usedBytes, strings.isRussian)
        if (subscription.totalBytes > 0L) {
            val remStr = formatBytes(subscription.remainingBytes, strings.isRussian)
            val totalStr = formatBytes(subscription.totalBytes, strings.isRussian)
            "$usedStr / $totalStr (${strings.remainingTraffic.lowercase()}: $remStr)"
        } else {
            "$usedStr • ${strings.unlimitedTraffic}"
        }
    }

    val isUrlValid = url.trim().startsWith("http://", ignoreCase = true) ||
        url.trim().startsWith("https://", ignoreCase = true)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (strings.isRussian) "Настройки подписки" else "Subscription Settings",
                    style = MaterialTheme.typography.titleLarge
                )

                // Subscription Summary Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SubInfoRow(
                            label = if (strings.isRussian) "Серверов" else "Servers",
                            value = "${servers.size}"
                        )
                        SubInfoRow(
                            label = if (strings.isRussian) "Трафик" else "Traffic",
                            value = trafficText
                        )
                        SubInfoRow(
                            label = if (strings.isRussian) "До окончания" else "Expires in",
                            value = if (expireDateText != null) "$remainingTimeText ($expireDateText)" else remainingTimeText
                        )
                        SubInfoRow(
                            label = if (strings.isRussian) "Обновлена" else "Last updated",
                            value = updatedText
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(if (strings.isRussian) "Название подписки" else "Subscription Name")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sub_settings_name_input")
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = {
                        Text(if (strings.isRussian) "URL подписки" else "Subscription URL")
                    },
                    isError = url.isNotBlank() && !isUrlValid,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sub_settings_url_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (strings.isRussian) "Автообновление подписки" else "Auto-Update Schedule",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        intervalOptions.forEach { hours ->
                            val chipLabel = when (hours) {
                                0 -> if (strings.isRussian) "Выкл" else "Off"
                                1 -> if (strings.isRussian) "1 час" else "1h"
                                2 -> if (strings.isRussian) "2 часа" else "2h"
                                6 -> if (strings.isRussian) "6 часов" else "6h"
                                12 -> if (strings.isRussian) "12 часов" else "12h"
                                else -> if (strings.isRussian) "24 часа" else "24h"
                            }
                            FilterChip(
                                selected = intervalHours == hours,
                                onClick = { intervalHours = hours },
                                label = { Text(chipLabel) },
                                modifier = Modifier.testTag("sub_auto_interval_$hours")
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { updateOnLaunch = !updateOnLaunch }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (strings.isRussian) "Обновлять при запуске" else "Update on App Launch",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (strings.isRussian) {
                                    "Автоматически обновлять подписку сразу при открытии приложения"
                                } else {
                                    "Automatically refresh this subscription whenever the app opens"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = updateOnLaunch,
                            onCheckedChange = { updateOnLaunch = it },
                            modifier = Modifier.testTag("sub_settings_on_launch_switch")
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { pingOnLaunch = !pingOnLaunch }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (strings.isRussian) "Проверка при запуске" else "Ping on App Launch",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (strings.isRussian) {
                                    "Автоматически проверять пинг серверов этой подписки при запуске"
                                } else {
                                    "Automatically ping servers of this subscription on app launch"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = pingOnLaunch,
                            onCheckedChange = { pingOnLaunch = it },
                            modifier = Modifier.testTag("sub_settings_ping_on_launch_switch")
                        )
                    }
                }

                if (subscription.externalLinks.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("sub_external_links_section")
                    ) {
                        Text(
                            text = strings.subscriptionLinksTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        subscription.externalLinks.forEachIndexed { idx, ext ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(ext.url))
                                            )
                                        }
                                    }
                                    .testTag("sub_external_link_$idx")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ext.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = ext.url,
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onCopyText(
                                                ext.url,
                                                if (strings.isRussian) "Ссылка скопирована" else "Link copied"
                                            )
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = strings.copy,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showAllLinksDialog = true },
                    enabled = servers.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sub_settings_view_links_button")
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${strings.allSubscriptionServers} (${servers.size})")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(name.trim(), url.trim(), intervalHours, updateOnLaunch, pingOnLaunch)
                            onDismiss()
                        },
                        enabled = name.isNotBlank() && isUrlValid,
                        modifier = Modifier.testTag("sub_settings_save_button")
                    ) {
                        Text(strings.save)
                    }
                }
            }
        }
    }

    if (showAllLinksDialog) {
        SubscriptionLinksDialog(
            subscriptionName = subscription.name,
            servers = servers,
            onDismiss = { showAllLinksDialog = false },
            onCopyText = onCopyText
        )
    }
}

@Composable
private fun SubInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
fun SubscriptionLinksDialog(
    subscriptionName: String,
    servers: List<ServerConfig>,
    onDismiss: () -> Unit,
    onCopyText: (text: String, message: String) -> Unit
) {
    val strings = LocalStrings.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.allSubscriptionServers,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "$subscriptionName • ${servers.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(servers, key = { it.id }) { srv ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${srv.countryFlag} ${srv.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        onCopyText(
                                            srv.rawUri,
                                            if (strings.isRussian) "Ссылка «${srv.name}» скопирована"
                                            else "Link «${srv.name}» copied"
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = strings.copy,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val allUris = servers.joinToString("\n") { it.rawUri }
                            onCopyText(
                                allUris,
                                if (strings.isRussian) "Скопировано ссылок: ${servers.size}"
                                else "Copied ${servers.size} links"
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (strings.isRussian) "Скопировать все" else "Copy All")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(strings.close)
                    }
                }
            }
        }
    }
}
