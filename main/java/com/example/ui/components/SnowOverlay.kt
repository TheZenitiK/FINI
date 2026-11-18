package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.data.model.ThemeMode
import java.util.Calendar
import kotlin.math.sin
import kotlin.random.Random

fun isHolidaySnowSeason(calendar: Calendar = Calendar.getInstance()): Boolean {
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return (month == Calendar.DECEMBER && day >= 15) ||
        (month == Calendar.JANUARY && day <= 15)
}

fun isDarkThemeActive(themeMode: ThemeMode, systemInDarkTheme: Boolean): Boolean =
    when (themeMode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemInDarkTheme
    }

private data class Snowflake(
    var xNorm: Float,
    var yNorm: Float,
    val radiusDp: Float,
    val speedNormPerSec: Float,
    val swayAmplitude: Float,
    val swayFreq: Float,
    val phase: Float,
    val alpha: Float
)

@Composable
fun SnowfallOverlay(
    enabled: Boolean,
    themeMode: ThemeMode = ThemeMode.DARK,
    modifier: Modifier = Modifier
) {
    val isSeason = remember { isHolidaySnowSeason() }
    val isDark = isDarkThemeActive(themeMode, isSystemInDarkTheme())
    if (!enabled || !isSeason || !isDark) return

    val flakes = remember {
        val rng = Random(2025)
        List(26) {
            Snowflake(
                xNorm = rng.nextFloat(),
                yNorm = rng.nextFloat(),
                radiusDp = 1.4f + rng.nextFloat() * 2.2f,
                speedNormPerSec = 0.045f + rng.nextFloat() * 0.055f,
                swayAmplitude = 0.012f + rng.nextFloat() * 0.018f,
                swayFreq = 0.8f + rng.nextFloat() * 1.1f,
                phase = rng.nextFloat() * 6.28f,
                alpha = 0.18f + rng.nextFloat() * 0.28f
            )
        }
    }

    var frameTimeSec by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos == 0L) {
                    lastNanos = now
                } else {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    lastNanos = now
                    for (flake in flakes) {
                        flake.yNorm += flake.speedNormPerSec * dt
                        if (flake.yNorm > 1.03f) {
                            flake.yNorm = -0.03f
                        }
                    }
                }
                frameTimeSec = now
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val tSec = (frameTimeSec % 60_000_000_000L) / 1_000_000_000f
        for (flake in flakes) {
            val sway = sin(tSec * flake.swayFreq + flake.phase) * flake.swayAmplitude
            val x = ((flake.xNorm + sway) * w).coerceIn(0f, w)
            val y = flake.yNorm * h
            drawCircle(
                color = Color.White.copy(alpha = flake.alpha),
                radius = flake.radiusDp * density,
                center = Offset(x, y)
            )
        }
    }
}
