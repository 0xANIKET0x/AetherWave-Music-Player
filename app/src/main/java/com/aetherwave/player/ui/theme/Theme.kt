package com.aetherwave.player.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = AetherDarkBackground,
    surface = AetherDarkSurface
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = AetherLightBackground,
    surface = AetherLightSurface
)

// ─── Modern Color Schemes ───
private val MidnightColorScheme = darkColorScheme(
    primary = MidnightPrimary,
    secondary = Color(0xFF2188FF),
    background = MidnightBackground,
    surface = MidnightSurface,
    onBackground = Color.White,
    onSurface = Color.White
)

private val EmeraldColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    secondary = Color(0xFF00C9A7),
    background = EmeraldBackground,
    surface = EmeraldSurface,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LavenderColorScheme = darkColorScheme(
    primary = LavenderPrimary,
    secondary = Color(0xFFD4BBFF),
    background = LavenderBackground,
    surface = LavenderSurface,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun AetherWaveTheme(
    theme: String = "DARK",
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        "MIDNIGHT" -> MidnightColorScheme
        "EMERALD" -> EmeraldColorScheme
        "LAVENDER" -> LavenderColorScheme
        "LIGHT" -> LightColorScheme
        else -> DarkColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
