package com.tenan.batteryanalyzer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Chart series colors, validated for contrast and color-vision-deficiency
 * separation against both surfaces. Charging segments additionally use a
 * thicker stroke so the distinction never relies on color alone.
 */
data class ChartColors(
    val discharge: Color,
    val charge: Color,
)

val LocalChartColors = staticCompositionLocalOf {
    ChartColors(discharge = Color(0xFF16A34A), charge = Color(0xFF2563EB))
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF16A34A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FADF),
    onPrimaryContainer = Color(0xFF052E16),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFD1FADF),
)

@Composable
fun BatteryAnalyzerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    // Material You dynamic color on Android 12+: the app takes on the user's
    // own wallpaper palette — another "tuned to this phone" touch.
    val colorScheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val chartColors = if (dark) {
        ChartColors(discharge = Color(0xFF16A34A), charge = Color(0xFF3B82F6))
    } else {
        ChartColors(discharge = Color(0xFF16A34A), charge = Color(0xFF2563EB))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalChartColors provides chartColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
