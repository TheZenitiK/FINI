package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.model.ServerConfig
import com.example.data.model.Subscription
import com.example.data.model.VpnStatus
import com.example.data.model.formatSubscriptionRemainingTime
import com.example.ui.components.AddConfigBottomSheet
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.HeroConnectCard
import com.example.ui.components.ServerCard
import com.example.ui.components.ShareQrDialog
import com.example.ui.components.ShareServerBottomSheet
import com.example.ui.components.ShareSubscriptionBottomSheet
import com.example.ui.components.SubscriptionSettingsDialog
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import com.example.ui.viewmodel.HomeUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private data class QrSharePayload(
    val title: String,
    val subtitle: String,
    val rawUri: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onToggleVpn: () -> Unit,
    onHeroStatusPillClick: () -> Unit,
    onNetworkPillClick: () -> Unit,
    onSelectServer: (ServerConfig) -> Unit,
    onViewConfigClick: (ServerConfig) -> Unit,
    onPingAll: () -> Unit,
    onPingSubscription: (String) -> Unit,
    onToggleSortByPing: (Boolean) -> Unit,
    onToggleOnlyAvailable: (Boolean) -> Unit,
    onClearPingResults: () -> Unit,
    onPingServer: (ServerConfig) -> Unit,
    onToggleFavorite: (ServerConfig) -> Unit,
    onReorderFavorites: (List<String>) -> Unit,
    onImportText: (String) -> Unit,
    onImportFile: (Uri) -> Unit,
    onOpenQrScanner: () -> Unit,
    onRefreshSubscription: (Subscription) -> Unit,
    onUpdateSubscriptionSettings: (Subscription, String, String, Int, Boolean, Boolean) -> Unit,
    onToggleSubscriptionCollapsed: (String) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onDeleteServer: (ServerConfig) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val haptic = LocalHapticFeedback.current

    var showAddSheet by remember { mutableStateOf(false) }
    var topMenuExpanded by remember { mutableStateOf(false) }
    var favMenuServerId by remember { mutableStateOf<String?>(null) }
    var shareTargetServer by remember { mutableStateOf<ServerConfig?>(null) }
    var shareTargetSubscription by remember { mutableStateOf<Subscription?>(null) }
    var qrDisplayPayload by remember { mutableStateOf<QrSharePayload?>(null) }
    var deleteServerTarget by remember { mutableStateOf<ServerConfig?>(null) }
    var settingsSubTarget by remember { mutableStateOf<Subscription?>(null) }
    var deleteSubTarget by remember { mutableStateOf<Subscription?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportFile(uri)
        }
    }

    val openFilePicker: () -> Unit = {
        runCatching { filePickerLauncher.launch(arrayOf("*/*")) }
            .onFailure {
                onShowMessage(
                    if (strings.isRussian) "Не удалось открыть выбор файла"
                    else "Failed to open file picker"
                )
            }
    }

    val pasteFromClipboard: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (clipText.isBlank()) {
            onShowMessage(if (strings.isRussian) "Буфер обмена пуст" else "Clipboard is empty")
        } else {
            onImportText(clipText)
        }
    }

    val copyToClipboard: (String, String) -> Unit = { text, message ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FINI", text))
        onShowMessage(message)
    }

    val copyServerToClipboard: (ServerConfig) -> Unit = { srv ->
        copyToClipboard(
            srv.rawUri,
            if (strings.isRussian) "Сервер «${srv.name}» скопирован"
            else "Server «${srv.name}» copied"
        )
    }

    val copySubToClipboard: (Subscription) -> Unit = { sub ->
        copyToClipboard(
            sub.url,
            if (strings.isRussian) "Ссылка подписки «${sub.name}» скопирована"
            else "Subscription link «${sub.name}» copied"
        )
    }

    val onlyAvailable = state.settings.onlyAvailableServers
    val sortByPing = state.settings.sortByPingEnabled
    val isServerAvailable: (ServerConfig) -> Boolean = { it.pingMs != null && !it.pingFailed }

    val favoriteServers = remember(state.servers, onlyAvailable, sortByPing) {
        val filtered = state.servers.filter { it.isFavorite && (!onlyAvailable || isServerAvailable(it)) }
        if (sortByPing) {
            sortServersForDisplay(filtered, sortByPing = true)
        } else {
            filtered
        }
    }
    val standaloneServers = remember(state.servers, onlyAvailable, sortByPing) {
        val filtered = state.servers.filter {
            it.subscriptionId == null && (!onlyAvailable || isServerAvailable(it))
        }
        sortServersForDisplay(filtered, sortByPing = sortByPing)
    }

    // Drag-and-drop state for Favorites
    val localFavorites = remember { mutableStateListOf<ServerConfig>() }
    var draggedFavId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val favItemHeights = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(favoriteServers) {
        if (draggedFavId == null) {
            localFavorites.clear()
            localFavorites.addAll(favoriteServers)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = strings.homeBrandTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(
                    onClick = { showAddSheet = true },
                    modifier = Modifier.testTag("add_config_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = if (strings.isRussian) "Добавить сервер или подписку" else "Add server or subscription"
                    )
                }

                Box {
                    IconButton(
                        onClick = { topMenuExpanded = true },
                        modifier = Modifier.testTag("home_overflow_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = if (strings.isRussian) "Меню" else "More options"
                        )
                    }

                    DropdownMenu(
                        expanded = topMenuExpanded,
                        onDismissRequest = { topMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (strings.isRussian) "Проверка доступности всех конфигов"
                                    else "Check availability of all configs"
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Speed, contentDescription = null)
                            },
                            enabled = state.servers.isNotEmpty() && !state.isPingingAll,
                            onClick = {
                                topMenuExpanded = false
                                onPingAll()
                            },
                            modifier = Modifier.testTag("menu_ping_all")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (strings.isRussian) "Сортировка по результатам теста"
                                    else "Sort by test results"
                                )
                            },
                            leadingIcon = {
                                if (sortByPing) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null)
                                }
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = sortByPing,
                                    onCheckedChange = null
                                )
                            },
                            onClick = {
                                topMenuExpanded = false
                                onToggleSortByPing(!sortByPing)
                            },
                            modifier = Modifier.testTag("menu_sort_by_ping")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (strings.isRussian) "Отображать только доступные конфигурации"
                                    else "Show only available configs"
                                )
                            },
                            leadingIcon = {
                                if (onlyAvailable) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.FilterList,
                                        contentDescription = null
                                    )
                                }
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = onlyAvailable,
                                    onCheckedChange = null
                                )
                            },
                            onClick = {
                                topMenuExpanded = false
                                onToggleOnlyAvailable(!onlyAvailable)
                            },
                            modifier = Modifier.testTag("menu_only_available")
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (strings.isRussian) "Очистить результаты теста"
                                    else "Clear test results"
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                            },
                            enabled = state.servers.isNotEmpty(),
                            onClick = {
                                topMenuExpanded = false
                                onClearPingResults()
                            },
                            modifier = Modifier.testTag("menu_clear_pings")
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        if (state.isImporting || state.isPingingAll) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
            ) {
                if (state.settings.pinConnectCard) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("pinned_hero_connect_container")
                    ) {
                        HeroConnectCard(
                            status = state.vpn.status,
                            activeServer = state.activeSelectedServer,
                            elapsedSeconds = state.vpn.elapsedSeconds,
                            rxBytes = state.vpn.rxBytes,
                            txBytes = state.vpn.txBytes,
                            activeNetwork = state.vpn.activeNetwork,
                            heroPingState = state.heroPingState,
                            autoSwitchEnabled = state.settings.autoSwitchServerByNetwork,
                            networkAutoSwitchActive = state.settings.networkAutoSwitchActive,
                            onNetworkPillClick = onNetworkPillClick,
                            onStatusPillClick = onHeroStatusPillClick,
                            onToggleConnect = onToggleVpn
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (state.settings.pinConnectCard) 4.dp else 8.dp,
                        bottom = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!state.settings.pinConnectCard) {
                        item(key = "hero_connect", contentType = "hero") {
                            HeroConnectCard(
                                status = state.vpn.status,
                                activeServer = state.activeSelectedServer,
                                elapsedSeconds = state.vpn.elapsedSeconds,
                                rxBytes = state.vpn.rxBytes,
                                txBytes = state.vpn.txBytes,
                                activeNetwork = state.vpn.activeNetwork,
                                heroPingState = state.heroPingState,
                                autoSwitchEnabled = state.settings.autoSwitchServerByNetwork,
                                networkAutoSwitchActive = state.settings.networkAutoSwitchActive,
                                onNetworkPillClick = onNetworkPillClick,
                                onStatusPillClick = onHeroStatusPillClick,
                                onToggleConnect = onToggleVpn
                            )
                        }
                    }

                if (state.isInitialDataLoaded && state.servers.isEmpty() && state.subscriptions.isEmpty()) {
                    item(key = "empty_state", contentType = "empty") {
                        EmptyServersCard(
                            onPasteClick = pasteFromClipboard,
                            onScanQrClick = onOpenQrScanner,
                            onImportFileClick = openFilePicker
                        )
                    }
                } else if (state.servers.isNotEmpty() || state.subscriptions.isNotEmpty()) {
                    if (onlyAvailable) {
                        item(key = "available_filter_banner") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                FilterChip(
                                    selected = true,
                                    onClick = { onToggleOnlyAvailable(false) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.FilterList,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            if (strings.isRussian) "Только доступные конфигурации • Сбросить"
                                            else "Available configs only • Reset"
                                        )
                                    },
                                    modifier = Modifier.testTag("available_only_filter_chip")
                                )
                            }
                        }
                    }

                    if (localFavorites.isNotEmpty()) {
                        item(key = "favorites_header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = strings.favoriteServers,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        item(key = "favorites_block") {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                localFavorites.forEach { server ->
                                    key(server.id) {
                                        val isBeingDragged = draggedFavId == server.id
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .zIndex(if (isBeingDragged) 10f else 0f)
                                                .onGloballyPositioned { coords ->
                                                    favItemHeights[server.id] = coords.size.height
                                                }
                                                .graphicsLayer {
                                                    if (isBeingDragged) {
                                                        translationY = dragOffsetY
                                                        scaleX = 1.02f
                                                        scaleY = 1.02f
                                                    }
                                                }
                                                .pointerInput(server.id, localFavorites.size) {
                                                    awaitEachGesture {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                                        var dragStarted = false
                                                        var prevPos = longPress.position
                                                        val slop = viewConfiguration.touchSlop

                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                            if (!change.pressed) {
                                                                if (dragStarted) change.consume()
                                                                break
                                                            }
                                                            if (!dragStarted &&
                                                                (abs(change.position.y - longPress.position.y) > slop ||
                                                                    abs(change.position.x - longPress.position.x) > slop)
                                                            ) {
                                                                dragStarted = true
                                                                favMenuServerId = null
                                                                draggedFavId = server.id
                                                                dragOffsetY = 0f
                                                            }
                                                            if (dragStarted) {
                                                                val deltaY = change.position.y - prevPos.y
                                                                change.consume()
                                                                val currentId = draggedFavId
                                                                if (currentId != null) {
                                                                    dragOffsetY += deltaY
                                                                    val currentIdx = localFavorites.indexOfFirst { it.id == currentId }
                                                                    if (currentIdx != -1) {
                                                                        val itemH = (favItemHeights[currentId] ?: 160).toFloat() + 12f
                                                                        if (dragOffsetY > itemH * 0.55f && currentIdx < localFavorites.lastIndex) {
                                                                            val moved = localFavorites.removeAt(currentIdx)
                                                                            localFavorites.add(currentIdx + 1, moved)
                                                                            dragOffsetY -= itemH
                                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        } else if (dragOffsetY < -itemH * 0.55f && currentIdx > 0) {
                                                                            val moved = localFavorites.removeAt(currentIdx)
                                                                            localFavorites.add(currentIdx - 1, moved)
                                                                            dragOffsetY += itemH
                                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            prevPos = change.position
                                                        }

                                                        if (dragStarted) {
                                                            val ordered = localFavorites.map { it.id }
                                                            draggedFavId = null
                                                            dragOffsetY = 0f
                                                            onReorderFavorites(ordered)
                                                        } else {
                                                            favMenuServerId = server.id
                                                        }
                                                    }
                                                }
                                        ) {
                                            ServerCard(
                                                server = server,
                                                isSelected = state.activeSelectedServer?.id == server.id,
                                                isConnectedToThis = state.vpn.status == VpnStatus.CONNECTED &&
                                                    state.vpn.activeServer?.id == server.id,
                                                pingDisplayMode = state.settings.pingDisplayMode,
                                                isPinging = server.id in state.pingingServerIds,
                                                isDragging = isBeingDragged,
                                                menuExpandedExternal = favMenuServerId == server.id,
                                                onDismissExternalMenu = { favMenuServerId = null },
                                                enableLongClick = false,
                                                onSelect = { onSelectServer(server) },
                                                onPingClick = { onPingServer(server) },
                                                onToggleFavorite = { onToggleFavorite(server) },
                                                onShareClick = { shareTargetServer = server },
                                                onViewConfigClick = { onViewConfigClick(server) },
                                                onDeleteClick = if (server.subscriptionId == null) {
                                                    { deleteServerTarget = server }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    state.subscriptions.forEach { subscription ->
                        val allSubServers = sortServersForDisplay(
                            state.servers.filter { it.subscriptionId == subscription.id },
                            sortByPing = sortByPing
                        )
                        val subServers = if (onlyAvailable) allSubServers.filter(isServerAvailable) else allSubServers
                        val isCollapsed = subscription.id in state.settings.collapsedSubscriptionIds

                        item(key = "sub_${subscription.id}", contentType = "subscription_banner") {
                            SubscriptionBannerCard(
                                subscription = subscription,
                                serverCount = subServers.size,
                                isCollapsed = isCollapsed,
                                isRefreshing = subscription.id in state.refreshingSubIds,
                                isPinging = subscription.id in state.pingingSubIds,
                                onToggleCollapse = { onToggleSubscriptionCollapsed(subscription.id) },
                                onPingSubscription = { onPingSubscription(subscription.id) },
                                onRefresh = { onRefreshSubscription(subscription) },
                                onOpenSettings = { settingsSubTarget = subscription },
                                onShare = { shareTargetSubscription = subscription },
                                onDelete = { deleteSubTarget = subscription }
                            )
                        }

                        if (!isCollapsed) {
                            items(
                                items = subServers,
                                key = { "sub_${subscription.id}_srv_${it.id}" },
                                contentType = { "server_card" }
                            ) { server ->
                                ServerCard(
                                    server = server,
                                    isSelected = state.activeSelectedServer?.id == server.id,
                                    isConnectedToThis = state.vpn.status == VpnStatus.CONNECTED &&
                                        state.vpn.activeServer?.id == server.id,
                                    pingDisplayMode = state.settings.pingDisplayMode,
                                    isPinging = server.id in state.pingingServerIds,
                                    onSelect = { onSelectServer(server) },
                                    onPingClick = { onPingServer(server) },
                                    onToggleFavorite = { onToggleFavorite(server) },
                                    onShareClick = { shareTargetServer = server },
                                    onViewConfigClick = { onViewConfigClick(server) },
                                    onDeleteClick = null
                                )
                            }
                        }
                    }

                    if (standaloneServers.isNotEmpty()) {
                        item(key = "standalone_header", contentType = "header") {
                            Text(
                                text = strings.standaloneServers,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }

                        items(
                            items = standaloneServers,
                            key = { "single_${it.id}" },
                            contentType = { "server_card" }
                        ) { server ->
                            ServerCard(
                                server = server,
                                isSelected = state.activeSelectedServer?.id == server.id,
                                isConnectedToThis = state.vpn.status == VpnStatus.CONNECTED &&
                                    state.vpn.activeServer?.id == server.id,
                                pingDisplayMode = state.settings.pingDisplayMode,
                                isPinging = server.id in state.pingingServerIds,
                                onSelect = { onSelectServer(server) },
                                onPingClick = { onPingServer(server) },
                                onToggleFavorite = { onToggleFavorite(server) },
                                onShareClick = { shareTargetServer = server },
                                onViewConfigClick = { onViewConfigClick(server) },
                                onDeleteClick = { deleteServerTarget = server }
                            )
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            }
        }
    }

    if (showAddSheet) {
        AddConfigBottomSheet(
            onDismiss = { showAddSheet = false },
            onPasteFromClipboard = pasteFromClipboard,
            onScanQrCode = onOpenQrScanner,
            onImportFromFile = openFilePicker,
            onImportManualText = onImportText
        )
    }

    shareTargetServer?.let { srv ->
        ShareServerBottomSheet(
            server = srv,
            onDismiss = { shareTargetServer = null },
            onShowQrCode = {
                qrDisplayPayload = QrSharePayload(
                    title = "${srv.countryFlag} ${srv.name}",
                    subtitle = "${srv.protocol.displayName} • ${srv.subProtocolLabel}",
                    rawUri = srv.rawUri
                )
            },
            onCopyServer = { copyServerToClipboard(srv) }
        )
    }

    shareTargetSubscription?.let { sub ->
        ShareSubscriptionBottomSheet(
            subscription = sub,
            onDismiss = { shareTargetSubscription = null },
            onShowQrCode = {
                qrDisplayPayload = QrSharePayload(
                    title = sub.name,
                    subtitle = strings.subscriptionLabel,
                    rawUri = sub.url
                )
            },
            onCopyUrl = { copySubToClipboard(sub) }
        )
    }

    qrDisplayPayload?.let { payload ->
        ShareQrDialog(
            title = payload.title,
            subtitle = payload.subtitle,
            rawUri = payload.rawUri,
            onDismiss = { qrDisplayPayload = null },
            onCopyUri = {
                copyToClipboard(
                    payload.rawUri,
                    if (strings.isRussian) "Скопировано в буфер обмена" else "Copied to clipboard"
                )
            }
        )
    }

    settingsSubTarget?.let { sub ->
        val currentSub = state.subscriptions.firstOrNull { it.id == sub.id } ?: sub
        val subServers = state.servers.filter { it.subscriptionId == currentSub.id }
        SubscriptionSettingsDialog(
            subscription = currentSub,
            servers = subServers,
            onDismiss = { settingsSubTarget = null },
            onSave = { newName, newUrl, intervalHours, onLaunch, pingOnLaunch ->
                onUpdateSubscriptionSettings(currentSub, newName, newUrl, intervalHours, onLaunch, pingOnLaunch)
            },
            onCopyText = { txt, msg -> copyToClipboard(txt, msg) }
        )
    }

    deleteSubTarget?.let { sub ->
        ConfirmDeleteDialog(
            title = strings.deleteSubTitle,
            message = if (strings.isRussian) {
                "Хотите ли вы правда удалить подписку «${sub.name}» и все входящие в неё серверы?"
            } else {
                "Delete subscription «${sub.name}» and all its servers?"
            },
            onDismiss = { deleteSubTarget = null },
            onConfirm = { onDeleteSubscription(sub.id) }
        )
    }

    deleteServerTarget?.let { srv ->
        ConfirmDeleteDialog(
            title = strings.deleteServerTitle,
            message = if (strings.isRussian) {
                "Хотите ли вы правда удалить сервер «${srv.name}»?"
            } else {
                "Delete server «${srv.name}»?"
            },
            onDismiss = { deleteServerTarget = null },
            onConfirm = { onDeleteServer(srv) }
        )
    }
}

@Composable
private fun EmptyServersCard(
    onPasteClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onImportFileClick: () -> Unit
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Text(
                text = strings.noServersTitle,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = strings.noServersSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onPasteClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("empty_paste_clipboard_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.pasteFromClipboard)
            }

            OutlinedButton(
                onClick = onScanQrClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("empty_scan_qr_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.scanQrCode)
            }

            OutlinedButton(
                onClick = onImportFileClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("empty_import_file_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.importFromFile)
            }
        }
    }
}

private fun formatBytesCompactBanner(bytes: Long, isRussian: Boolean): String {
    if (bytes < 1024L) return if (isRussian) "$bytes Б" else "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        val v = String.format(Locale.US, "%.0f", kb)
        return if (isRussian) "$v КБ" else "$v KB"
    }
    val mb = kb / 1024.0
    if (mb < 1024.0) {
        val raw = String.format(Locale.US, "%.1f", mb).removeSuffix(".0")
        return if (isRussian) "$raw МБ" else "$raw MB"
    }
    val gb = mb / 1024.0
    val raw = String.format(Locale.US, "%.2f", gb).replace(Regex("\\.?0+$"), "")
    return if (isRussian) "$raw ГБ" else "$raw GB"
}

@Composable
private fun SubscriptionBannerCard(
    subscription: Subscription,
    serverCount: Int,
    isCollapsed: Boolean,
    isRefreshing: Boolean,
    isPinging: Boolean,
    onToggleCollapse: () -> Unit,
    onPingSubscription: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current
    var menuExpanded by remember { mutableStateOf(false) }

    val updatedText = remember(subscription.lastUpdatedAt) {
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(subscription.lastUpdatedAt))
    }

    val hasTrafficTotal = subscription.totalBytes > 0L
    val hasUsedInfo = subscription.usedBytes > 0L || hasTrafficTotal
    val progress = remember(subscription.usedBytes, subscription.totalBytes) {
        if (subscription.totalBytes > 0L) {
            (subscription.usedBytes.toFloat() / subscription.totalBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag("subscription_banner_${subscription.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleCollapse)
                .padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.testTag("collapse_sub_${subscription.id}")
                ) {
                    Icon(
                        imageVector = if (isCollapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                        contentDescription = if (isCollapsed) "Expand" else "Collapse",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subscription.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$serverCount ${strings.serversShort} • ${strings.updatedShort} $updatedText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // 1. Refresh button on the left
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.testTag("refresh_sub_${subscription.id}")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = if (strings.isRussian) "Обновить подписку" else "Refresh subscription",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 2. Per-subscription Ping button in the middle
                IconButton(
                    onClick = onPingSubscription,
                    enabled = !isPinging,
                    modifier = Modifier.testTag("ping_sub_button_${subscription.id}")
                ) {
                    if (isPinging) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = if (strings.isRussian) "Проверить пинг подписки" else "Ping subscription servers",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 3. Three dots on the far right
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("sub_menu_button_${subscription.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = if (strings.isRussian) "Действия с подпиской" else "Subscription actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (strings.isRussian) "Настройки" else "Settings") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Tune, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            },
                            modifier = Modifier.testTag("settings_sub_${subscription.id}")
                        )
                        DropdownMenuItem(
                            text = { Text(if (strings.isRussian) "Поделиться" else "Share") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            },
                            modifier = Modifier.testTag("share_sub_${subscription.id}")
                        )
                        DropdownMenuItem(
                            text = { Text(strings.delete, color = StatusRed) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = StatusRed
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("delete_sub_${subscription.id}")
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (hasUsedInfo) {
                    val usedFormatted = formatBytesCompactBanner(subscription.usedBytes, strings.isRussian)
                    val trafficLine = if (hasTrafficTotal) {
                        val totalFormatted = formatBytesCompactBanner(subscription.totalBytes, strings.isRussian)
                        "$usedFormatted | $totalFormatted"
                    } else {
                        "$usedFormatted | ∞"
                    }

                    Text(
                        text = trafficLine,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (hasTrafficTotal) {
                        val barColor = when {
                            progress >= 0.9f -> StatusRed
                            progress >= 0.72f -> StatusYellow
                            else -> MaterialTheme.colorScheme.primary
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }

                val expireMs = subscription.normalizedExpireMillis()
                val remainingMs = if (expireMs > 0L) expireMs - System.currentTimeMillis() else Long.MAX_VALUE
                val remainingLabel = formatSubscriptionRemainingTime(subscription.expireTimestamp, strings.isRussian)
                val expiryColor = when {
                    expireMs <= 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                    remainingMs <= 0L -> StatusRed
                    remainingMs <= 3L * 24L * 3600_000L -> StatusYellow
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = expiryColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (strings.isRussian) "Осталось: $remainingLabel" else "Time left: $remainingLabel",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = expiryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun sortServersForDisplay(
    servers: List<ServerConfig>,
    sortByPing: Boolean
): List<ServerConfig> {
    if (!sortByPing) return servers.sortedBy { it.sortOrder }
    return servers.sortedWith(
        compareBy<ServerConfig>(
            {
                when {
                    it.pingMs != null && !it.pingFailed -> 0
                    it.pingMs == null && !it.pingFailed -> 1
                    else -> 2
                }
            },
            { it.pingMs ?: Int.MAX_VALUE },
            { it.sortOrder }
        )
    )
}
