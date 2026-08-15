package com.preappointment1.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MonochromeColorScheme = lightColorScheme(
    primary = SagePrimary,
    onPrimary = White,
    secondary = SageDark,
    onSecondary = White,
    tertiary = SageLight,
    onTertiary = White,
    background = CanvasBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = CanvasBackground,
    onSurfaceVariant = TextSecondary,
    outline = CardBorderSoft,
    outlineVariant = CardBorderSoft
)

@Composable
fun LivingPatientMemoryTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = MonochromeColorScheme,
        typography = Typography,
        content = content
    )
}
