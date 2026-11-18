package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveNetworkType
import com.example.data.model.ServerConfig
import com.example.data.model.VpnStatus
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.viewmodel.HeroPingUiState
import com.example.vpn.GRayVpnService
import java.util.Locale

@Composable
fun HeroConnectCard(
    status: VpnStatus,
    activeServer: ServerConfig?,
    elapsedSeconds: Long,
    rxBytes: Long,
    txBytes: Long,
    activeNetwork: ActiveNetworkType,
    heroPingState: HeroPingUiState?,
    autoSwitchEnabled: Boolean = false,
    networkAutoSwitchActive: Boolean = false,
    onNetworkPillClick: () -> Unit = {},
    onStatusPillClick: () -> Unit,
    onToggleConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val isConnected = status == VpnStatus.CONNECTED
    val isConnecting = status == VpnStatus.CONNECTING

    val networkLabel = when (activeNetwork) {
        ActiveNetworkType.WIFI -> strings.networkWifi
        ActiveNetworkType.MOBILE -> strings.networkMobile
        ActiveNetworkType.OTHER -> strings.networkOther
    }

    val coreColor by animateColorAsState(
        targetValue = when (status) {
            VpnStatus.CONNECTED -> StatusGreen
            VpnStatus.CONNECTING -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primary
        },
        label = "core_color"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "btn_scale"
    )

    val pulseProgress = if (isConnected || isConnecting) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (isConnecting) 1100 else 2400,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse_progress"
        ).value
    } else {
        0f
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isConnected) StatusGreen.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
        ),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pingResultMs = (heroPingState as? HeroPingUiState.Result)?.pingMs
                    val isHeroPinging = heroPingState is HeroPingUiState.Pinging
                    val isHeroTimeout = heroPingState is HeroPingUiState.Result && pingResultMs == null

                    val pillBgColor = when {
                        isHeroTimeout -> StatusRed.copy(alpha = 0.18f)
                        status == VpnStatus.CONNECTED -> StatusGreen.copy(alpha = 0.16f)
                        status == VpnStatus.CONNECTING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val pillTextColor = when {
                        isHeroTimeout -> StatusRed
                        status == VpnStatus.CONNECTED -> StatusGreen
                        status == VpnStatus.CONNECTING -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = pillBgColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .then(
                                if (isConnected) {
                                    Modifier.clickable(onClick = onStatusPillClick)
                                } else {
                                    Modifier
                                }
                            )
                            .testTag("hero_status_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(pillTextColor)
                            )
                            val pillLabel = when {
                                status != VpnStatus.CONNECTED -> when (status) {
                                    VpnStatus.CONNECTING -> strings.statusConnecting
                                    VpnStatus.DISCONNECTING -> strings.statusDisconnecting
                                    else -> strings.statusDisconnected
                                }
                                isHeroPinging -> if (strings.isRussian) "ПИНГ…" else "PING…"
                                heroPingState is HeroPingUiState.Result -> {
                                    if (pingResultMs != null) {
                                        if (strings.isRussian) "$pingResultMs мс" else "$pingResultMs ms"
                                    } else {
                                        if (strings.isRussian) "Нет отв." else "Timeout"
                                    }
                                }
                                else -> strings.statusProtected
                            }
                            Text(
                                text = pillLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = pillTextColor
                            )
                        }
                    }

                    val isAutoSwitchLit = autoSwitchEnabled && networkAutoSwitchActive
                    val netPillBg by animateColorAsState(
                        targetValue = if (isAutoSwitchLit) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        label = "net_pill_bg"
                    )
                    val netPillContentColor by animateColorAsState(
                        targetValue = if (isAutoSwitchLit) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "net_pill_fg"
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = netPillBg,
                        border = if (isAutoSwitchLit) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                        } else null,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .then(
                                if (autoSwitchEnabled) {
                                    Modifier.clickable(onClick = onNetworkPillClick)
                                } else {
                                    Modifier
                                }
                            )
                            .testTag("hero_network_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = when (activeNetwork) {
                                    ActiveNetworkType.WIFI -> Icons.Rounded.Wifi
                                    ActiveNetworkType.MOBILE -> Icons.Rounded.CellTower
                                    ActiveNetworkType.OTHER -> Icons.Rounded.Shield
                                },
                                contentDescription = networkLabel,
                                modifier = Modifier.size(14.dp),
                                tint = netPillContentColor
                            )
                            Text(
                                text = networkLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isAutoSwitchLit) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = netPillContentColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .size(172.dp)
                        .scale(buttonScale),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerRadius = size.minDimension / 2f
                        if (isConnected || isConnecting) {
                            val ringRadius = centerRadius * (0.72f + 0.28f * pulseProgress)
                            drawCircle(
                                color = coreColor.copy(alpha = (1f - pulseProgress) * 0.45f),
                                radius = ringRadius,
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                        drawCircle(
                            color = coreColor.copy(alpha = if (isConnected) 0.25f else 0.14f),
                            radius = centerRadius * 0.84f,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .size(134.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onToggleConnect
                            )
                            .testTag("hero_connect_button"),
                        shape = CircleShape,
                        color = if (isConnected) StatusGreen else MaterialTheme.colorScheme.primary,
                        shadowElevation = if (isConnected) 14.dp else 6.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.22f),
                                            Color.Transparent
                                        )
                                    )
                                ),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = if (isConnected) strings.disconnect else strings.connect,
                                tint = if (isConnected) Color(0xFF04210E) else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (status) {
                                    VpnStatus.CONNECTED -> strings.disconnect
                                    VpnStatus.CONNECTING -> strings.cancel
                                    else -> strings.connect
                                },
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isConnected) Color(0xFF04210E) else MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = GRayVpnService.formatDuration(elapsedSeconds),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = if (isConnected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.testTag("connection_timer")
                )

                if (isConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = "RX",
                                tint = StatusGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = formatBytes(rxBytes, strings.isRussian),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = "TX",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = formatBytes(txBytes, strings.isRussian),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (activeServer != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = activeServer.countryFlag, fontSize = 16.sp)
                            Text(
                                text = activeServer.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = "${activeServer.protocol.displayName} • ${activeServer.subProtocolLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatBytes(bytes: Long, isRussian: Boolean): String {
    if (bytes < 1024) return if (isRussian) "$bytes Б" else "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, if (isRussian) "%.1f КБ" else "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, if (isRussian) "%.2f МБ" else "%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, if (isRussian) "%.2f ГБ" else "%.2f GB", gb)
}
