package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.AccentPreset
import com.example.data.model.ThemeMode

private val GRayShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GRayTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = false,
    accentPreset: AccentPreset = AccentPreset.CYAN,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val isAmoled = themeMode == ThemeMode.AMOLED
    val context = LocalContext.current

    val accent = Color(accentPreset.primaryHex)
    val accentDark = Color(accentPreset.darkPrimaryHex)
    val accentContainerLight = Color(accentPreset.lightContainerHex)
    val accentContainerDark = Color(accentPreset.darkContainerHex)

    val colorScheme: ColorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dyn = if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (isAmoled) {
                dyn.copy(
                    background = AmoledBackground,
                    surface = AmoledSurface,
                    surfaceVariant = AmoledSurfaceVariant,
                    surfaceContainerLowest = AmoledBackground,
                    surfaceContainerLow = AmoledSurface,
                    surfaceContainer = AmoledSurfaceContainer,
                    surfaceContainerHigh = AmoledSurfaceVariant,
                    outline = AmoledOutline
                )
            } else dyn
        }
        isAmoled -> darkColorScheme(
            primary = accentDark,
            onPrimary = Color(0xFF041016),
            primaryContainer = accentContainerDark,
            onPrimaryContainer = accentDark,
            secondary = Color(0xFF94A3B8),
            onSecondary = Color(0xFF0B111E),
            secondaryContainer = AmoledSurfaceVariant,
            onSecondaryContainer = Color(0xFFE2E8F0),
            tertiary = StatusGreen,
            background = AmoledBackground,
            onBackground = DarkOnBackground,
            surface = AmoledSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = AmoledSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            surfaceContainerLowest = AmoledBackground,
            surfaceContainerLow = AmoledSurface,
            surfaceContainer = AmoledSurfaceContainer,
            surfaceContainerHigh = AmoledSurfaceVariant,
            outline = AmoledOutline,
            outlineVariant = Color(0xFF18202C),
            error = StatusRed,
            onError = Color.White
        )
        useDark -> darkColorScheme(
            primary = accentDark,
            onPrimary = Color(0xFF05131B),
            primaryContainer = accentContainerDark,
            onPrimaryContainer = accentDark,
            secondary = Color(0xFF94A3B8),
            onSecondary = Color(0xFF0F172A),
            secondaryContainer = DarkSurfaceVariant,
            onSecondaryContainer = Color(0xFFE2E8F0),
            tertiary = StatusGreen,
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            surfaceContainerLowest = Color(0xFF090D14),
            surfaceContainerLow = DarkSurface,
            surfaceContainer = DarkSurfaceContainer,
            surfaceContainerHigh = DarkSurfaceVariant,
            outline = DarkOutline,
            outlineVariant = Color(0xFF202A3B),
            error = StatusRed,
            onError = Color.White
        )
        else -> lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accentContainerLight,
            onPrimaryContainer = accent,
            secondary = Color(0xFF475569),
            onSecondary = Color.White,
            secondaryContainer = LightSurfaceVariant,
            onSecondaryContainer = LightOnSurface,
            tertiary = StatusGreen,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = LightSurface,
            surfaceContainer = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceVariant,
            outline = LightOutline,
            outlineVariant = Color(0xFFE4EAF2),
            error = StatusRed,
            onError = Color.White
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GRayTypography,
        shapes = GRayShapes,
        content = content
    )
}
