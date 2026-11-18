package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppSettings
import com.example.data.model.ThemeMode
import com.example.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    settings: AppSettings,
    hasAddedConfigs: Boolean,
    isImporting: Boolean,
    onToggleStatusNotification: (Boolean) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onImportText: (String) -> Unit,
    onOpenQrScanner: () -> Unit,
    onShowMessage: (String) -> Unit,
    onCompleteOnboarding: () -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })

    val isReadyCompletePage = pagerState.currentPage == 3 && hasAddedConfigs

    val isDarkVisual = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
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

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar with top-right Skip button (hidden on ready checkmark view)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(48.dp)
            ) {
                if (!isReadyCompletePage) {
                    TextButton(
                        onClick = onCompleteOnboarding,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .testTag("onboarding_skip_button")
                    ) {
                        Text(
                            text = if (strings.isRussian) "Пропустить" else "Skip",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 4-page swipeable HorizontalPager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .testTag("onboarding_pager")
            ) { page ->
                when (page) {
                    0 -> OnboardingWelcomePage(
                        isRussian = strings.isRussian,
                        isDarkVisual = isDarkVisual
                    )
                    1 -> OnboardingNotificationsPage(
                        isRussian = strings.isRussian,
                        checked = settings.showVpnStatusNotification,
                        onCheckedChange = onToggleStatusNotification
                    )
                    2 -> OnboardingThemePage(
                        isRussian = strings.isRussian,
                        themeMode = settings.themeMode,
                        onSelectThemeMode = onSelectThemeMode
                    )
                    3 -> OnboardingSubscriptionPage(
                        isRussian = strings.isRussian,
                        hasAddedConfigs = hasAddedConfigs,
                        isImporting = isImporting,
                        onPasteClick = pasteFromClipboard,
                        onScanQrClick = onOpenQrScanner
                    )
                }
            }

            // Page dots indicator with elongated active pill + main CTA button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(top = 12.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("onboarding_dots")
                ) {
                    repeat(4) { idx ->
                        val isSelected = pagerState.currentPage == idx
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 8.dp,
                            label = "dot_width_$idx"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                                )
                        )
                    }
                }

                val currentPage = pagerState.currentPage
                val buttonLabel = when (currentPage) {
                    2 -> if (settings.themeMode == ThemeMode.SYSTEM) {
                        if (strings.isRussian) "Оставить по умолчанию" else "Keep Default"
                    } else {
                        if (strings.isRussian) "Далее" else "Next"
                    }
                    3 -> if (hasAddedConfigs) {
                        if (strings.isRussian) "Начать использование" else "Start Using"
                    } else {
                        null
                    }
                    else -> if (strings.isRussian) "Далее" else "Next"
                }

                if (buttonLabel != null) {
                    Button(
                        onClick = {
                            if (currentPage < 3) {
                                scope.launch {
                                    pagerState.animateScrollToPage(currentPage + 1)
                                }
                            } else {
                                onCompleteOnboarding()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_next_button")
                    ) {
                        Text(
                            text = buttonLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(52.dp))
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomePage(
    isRussian: Boolean,
    isDarkVisual: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(104.dp)
        ) {
            Image(
                painter = painterResource(
                    id = if (isDarkVisual) R.drawable.img_icon_dark else R.drawable.img_icon_white
                ),
                contentDescription = "FINI",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "FINI",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ) {
            Text(
                text = if (isRussian) "Бесплатный клиент" else "Free Client",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (isRussian) {
                "Бесплатный VPN-клиент с открытым исходным кодом. Мы не продаём VPN и не берём плату за подключение."
            } else {
                "Free open-source VPN client. We do not sell VPN access or charge any connection fees."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun OnboardingNotificationsPage(
    isRussian: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(108.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = if (isRussian) "Уведомления и строка состояния" else "Notifications & Status Bar",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isRussian) {
                "Отображать статус подключения VPN в строке уведомлений и шторке? Вы всегда сможете изменить это в настройках подключения."
            } else {
                "Display VPN connection status in the status bar and shade? You can change this anytime in Connection settings."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { onCheckedChange(!checked) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isRussian) {
                        "Показывать статус подключения в строке уведомлений"
                    } else {
                        "Show connection status in notification bar"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.testTag("onboarding_status_notif_switch")
                )
            }
        }
    }
}

@Composable
private fun OnboardingThemePage(
    isRussian: Boolean,
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit
) {
    val isLightSelected = themeMode == ThemeMode.LIGHT
    val isDarkSelected = themeMode == ThemeMode.DARK || themeMode == ThemeMode.AMOLED
    val isAmoledSelected = themeMode == ThemeMode.AMOLED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(108.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = if (isRussian) "Внешний вид" else "Appearance",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = if (isRussian) {
                "Выберите тему оформления или оставьте системную тему по умолчанию"
            } else {
                "Choose your preferred theme or keep your system default"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isLightSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = if (isLightSelected) 2.dp else 1.dp,
                    color = if (isLightSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelectThemeMode(ThemeMode.LIGHT) }
                    .testTag("onboarding_theme_light")
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LightMode,
                        contentDescription = null,
                        tint = if (isLightSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = if (isRussian) "Светлая" else "Light",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isLightSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isDarkSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = if (isDarkSelected) 2.dp else 1.dp,
                    color = if (isDarkSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        if (!isDarkSelected) {
                            onSelectThemeMode(ThemeMode.DARK)
                        }
                    }
                    .testTag("onboarding_theme_dark")
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DarkMode,
                        contentDescription = null,
                        tint = if (isDarkSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = if (isRussian) "Тёмная" else "Dark",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isDarkSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isDarkSelected,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onSelectThemeMode(if (isAmoledSelected) ThemeMode.DARK else ThemeMode.AMOLED)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isRussian) {
                            "AMOLED — использовать натуральный чёрный цвет"
                        } else {
                            "AMOLED — use pure black surfaces"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isAmoledSelected,
                        onCheckedChange = { useAmoled ->
                            onSelectThemeMode(if (useAmoled) ThemeMode.AMOLED else ThemeMode.DARK)
                        },
                        modifier = Modifier.testTag("onboarding_amoled_switch")
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingSubscriptionPage(
    isRussian: Boolean,
    hasAddedConfigs: Boolean,
    isImporting: Boolean,
    onPasteClick: () -> Unit,
    onScanQrClick: () -> Unit
) {
    AnimatedContent(
        targetState = hasAddedConfigs,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "onboarding_sub_state"
    ) { ready ->
        if (ready) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedSuccessCheckmark(
                    contentDesc = if (isRussian) "Подписка успешно добавлена" else "Subscription added"
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = if (isRussian) {
                        "Всё отлично, вы можете начать использование приложения"
                    } else {
                        "Everything is ready — you can now start using the app"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(108.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = if (isRussian) "Добавить подписку" else "Add Subscription",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isRussian) {
                        "Вставьте ссылку на подписку или конфигурацию сервера из буфера обмена, либо отсканируйте QR-код"
                    } else {
                        "Paste a subscription URL or server configuration from your clipboard, or scan a QR code"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = onPasteClick,
                    enabled = !isImporting,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboarding_paste_button")
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isRussian) "Вставить из буфера обмена" else "Paste from Clipboard"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onScanQrClick,
                    enabled = !isImporting,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboarding_scan_qr_button")
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isRussian) "Сканировать QR-код" else "Scan QR Code")
                }
            }
        }
    }
}

@Composable
private fun AnimatedSuccessCheckmark(
    contentDesc: String
) {
    val containerScale = remember { Animatable(0.55f) }
    val ringSweep = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            containerScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            ringSweep.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }
        launch {
            checkProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 460, delayMillis = 180, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(136.dp)
            .graphicsLayer {
                scaleX = containerScale.value
                scaleY = containerScale.value
            }
            .semantics { contentDescription = contentDesc }
            .testTag("onboarding_success_checkmark")
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary

        Canvas(modifier = Modifier.size(124.dp)) {
            val strokePx = 7.dp.toPx()

            drawCircle(
                color = primaryColor.copy(alpha = 0.14f),
                radius = size.minDimension / 2f
            )

            drawArc(
                color = primaryColor.copy(alpha = 0.28f),
                startAngle = -90f,
                sweepAngle = 360f * ringSweep.value,
                useCenter = false,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            val fullPath = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.67f)
                lineTo(size.width * 0.73f, size.height * 0.35f)
            }
            val measure = PathMeasure().apply { setPath(fullPath, false) }
            val drawnPath = Path()
            measure.getSegment(0f, measure.length * checkProgress.value, drawnPath, true)

            if (checkProgress.value > 0f) {
                drawPath(
                    path = drawnPath,
                    color = primaryColor,
                    style = Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

