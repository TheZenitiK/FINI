package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PingDisplayMode
import com.example.data.model.ServerConfig
import com.example.ui.i18n.LocalStrings
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerCard(
    server: ServerConfig,
    isSelected: Boolean,
    isConnectedToThis: Boolean,
    pingDisplayMode: PingDisplayMode,
    isPinging: Boolean = false,
    isDragging: Boolean = false,
    menuExpandedExternal: Boolean? = null,
    onDismissExternalMenu: (() -> Unit)? = null,
    enableLongClick: Boolean = true,
    onSelect: () -> Unit,
    onPingClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShareClick: () -> Unit,
    onViewConfigClick: () -> Unit,
    onDeleteClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val haptic = LocalHapticFeedback.current
    var internalMenuExpanded by remember { mutableStateOf(false) }
    val isMenuOpen = menuExpandedExternal ?: internalMenuExpanded
    val closeMenu: () -> Unit = {
        internalMenuExpanded = false
        onDismissExternalMenu?.invoke()
    }

    val borderColor by animateColorAsState(
        targetValue = when {
            isDragging -> MaterialTheme.colorScheme.primary
            isConnectedToThis -> StatusGreen
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "server_border"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            isDragging -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            isConnectedToThis -> StatusGreen.copy(alpha = 0.08f)
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "server_bg"
    )

    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = if (enableLongClick) {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            internalMenuExpanded = true
                        }
                    } else null
                )
                .testTag("server_card_${server.id}"),
            shape = RoundedCornerShape(18.dp),
            color = containerColor,
            tonalElevation = if (isDragging) 6.dp else 0.dp,
            shadowElevation = if (isDragging) 8.dp else 0.dp,
            border = BorderStroke(
                width = if (isDragging) 2.dp else if (isSelected || isConnectedToThis) 1.5.dp else 1.dp,
                color = borderColor
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (server.isAutoSelect) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Auto-select",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = server.countryFlag,
                        fontSize = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isConnectedToThis) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Active",
                            tint = StatusGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (server.note.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.testTag("server_note_${server.id}")
                    ) {
                        Text(
                            text = server.note,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = server.protocol.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = JetBrainsMonoFamily
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = server.subProtocolLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (server.pingMs != null || server.pingFailed) {
                        PingBadge(
                            pingMs = server.pingMs,
                            pingFailed = server.pingFailed,
                            isRussian = strings.isRussian,
                            displayMode = pingDisplayMode,
                            onClick = onPingClick
                        )
                    }
                }
            }

            IconButton(
                onClick = onPingClick,
                enabled = !isPinging,
                modifier = Modifier.testTag("ping_button_${server.id}")
            ) {
                if (isPinging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = if (strings.isRussian) "Проверить пинг" else "Ping server",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            }
        }

        DropdownMenu(
            expanded = isMenuOpen,
            onDismissRequest = closeMenu
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (strings.isRussian) {
                            if (server.isFavorite) "Убрать из избранного" else "Добавить в избранное"
                        } else {
                            if (server.isFavorite) "Remove favorite" else "Add to favorites"
                        }
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (server.isFavorite) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        tint = if (server.isFavorite) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    closeMenu()
                    onToggleFavorite()
                },
                modifier = Modifier.testTag("favorite_button_${server.id}")
            )
            DropdownMenuItem(
                text = {
                    Text(if (strings.isRussian) "Поделиться" else "Share")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null
                    )
                },
                onClick = {
                    closeMenu()
                    onShareClick()
                },
                modifier = Modifier.testTag("share_button_${server.id}")
            )
            DropdownMenuItem(
                text = { Text(strings.viewConfig) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null
                    )
                },
                onClick = {
                    closeMenu()
                    onViewConfigClick()
                },
                modifier = Modifier.testTag("view_config_button_${server.id}")
            )
            if (onDeleteClick != null) {
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
                        closeMenu()
                        onDeleteClick()
                    },
                    modifier = Modifier.testTag("delete_server_button_${server.id}")
                )
            }
        }
    }
}

@Composable
private fun PingBadge(
    pingMs: Int?,
    pingFailed: Boolean,
    isRussian: Boolean,
    displayMode: PingDisplayMode,
    onClick: () -> Unit
) {
    val dotColor = when {
        pingFailed -> StatusRed
        pingMs == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        pingMs > 1000 -> StatusYellow
        else -> StatusGreen
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        when (displayMode) {
            PingDisplayMode.DOTS -> {
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
            PingDisplayMode.MILLISECONDS -> {
                val label = when {
                    pingFailed -> if (isRussian) "Нет отв." else "Timeout"
                    pingMs != null -> if (isRussian) "$pingMs мс" else "$pingMs ms"
                    else -> "—"
                }
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = if (pingFailed) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
