package com.pulsemessenger.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pulsemessenger.android.core.session.ThemeMode

private val PulseDarkColorScheme = darkColorScheme(
    primary = PulseBlue,
    secondary = PulseMint,
    background = PulseDarkBackground,
    surface = PulseDarkSurface,
    surfaceBright = PulseDarkElevated,
    surfaceVariant = PulseDarkSurfaceAlt,
    outline = PulseDarkOutline,
    onPrimary = PulseText,
    onSecondary = PulseDarkBackground,
    onBackground = PulseText,
    onSurface = PulseText,
    onSurfaceVariant = PulseMuted
)

private val PulseLightColorScheme = lightColorScheme(
    primary = PulseBlue,
    secondary = PulseMint,
    background = PulseLightBackground,
    surface = PulseLightSurface,
    surfaceVariant = PulseLightSurfaceAlt,
    outline = PulseLightOutline,
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF1A3A4A),
    onBackground = PulseTextLight,
    onSurface = PulseTextLight,
    onSurfaceVariant = PulseMutedLight
)

@Composable
fun PulseAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PulseDarkColorScheme else PulseLightColorScheme,
        typography = Typography,
        content = content
    )
}
