package com.rfm.onevsone.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import android.app.Activity
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF22D3EE), onPrimary = Color(0xFF04222A),
    primaryContainer = Color(0xFF123B48), onPrimaryContainer = Color(0xFFD6F7FF),
    secondary = Color(0xFFF472B6), onSecondary = Color(0xFF2B0A1B),
    secondaryContainer = Color(0xFF4A1D37), onSecondaryContainer = Color(0xFFFFD9EC),
    tertiary = Color(0xFFA78BFA),
    background = Color(0xFF0B0F1E), onBackground = Color(0xFFE8ECFF),
    surface = Color(0xFF151B34), onSurface = Color(0xFFE8ECFF),
    surfaceVariant = Color(0xFF1E2646), onSurfaceVariant = Color(0xFFA9B2D6),
    outline = Color(0xFF3A4570)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0891B2), onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF3FA), onPrimaryContainer = Color(0xFF042F3A),
    secondary = Color(0xFFDB2777), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9EC), onSecondaryContainer = Color(0xFF3D0A24),
    tertiary = Color(0xFF7C3AED),
    background = Color(0xFFF2F5FF), onBackground = Color(0xFF10142A),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF10142A),
    surfaceVariant = Color(0xFFE4E9FB), onSurfaceVariant = Color(0xFF4A5278),
    outline = Color(0xFFB4BCDD)
)

@Composable
fun OneVsOneTheme(theme: Int, content: @Composable () -> Unit) {
    val dark = when (theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val ctl = WindowCompat.getInsetsController(window, view)
                ctl.isAppearanceLightStatusBars = !dark
                ctl.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = Typography()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
    }
}
