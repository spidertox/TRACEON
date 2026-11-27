package com.traceon.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.traceon.domain.model.ThemeMode

object Brand {
    val Blue = Color(0xFF00A8FF)
    val Cyan = Color(0xFF29C6FF)
    val Indigo = Color(0xFF7C8CFF)
    val Mint = Color(0xFF2DE2C4)
    val Amber = Color(0xFFFFB84D)
    val Coral = Color(0xFFFF6B6B)
    val Pink = Color(0xFFFF7AC6)
    val Chart = listOf(Cyan, Indigo, Mint, Amber, Pink, Coral, Color(0xFF8AE234), Color(0xFF9AA7C7))
}

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF29B6FF), onPrimary = Color(0xFF00131F),
    primaryContainer = Color(0xFF0B3558), onPrimaryContainer = Color(0xFFCDEBFF),
    secondary = Color(0xFF7C8CFF), onSecondary = Color(0xFF0A0E33),
    tertiary = Color(0xFF2DE2C4), onTertiary = Color(0xFF00201A),
    background = Color(0xFF020817), onBackground = Color(0xFFE6EEFF),
    surface = Color(0xFF07122B), onSurface = Color(0xFFE6EEFF),
    surfaceVariant = Color(0xFF0D1B3D), onSurfaceVariant = Color(0xFF9BB0D6),
    surfaceContainerLowest = Color(0xFF020817), surfaceContainerLow = Color(0xFF050E24),
    surfaceContainer = Color(0xFF081330), surfaceContainerHigh = Color(0xFF0D1B3D), surfaceContainerHighest = Color(0xFF132550),
    outline = Color(0xFF2A4170), outlineVariant = Color(0xFF16284F),
    error = Color(0xFFFF6B6B), onError = Color(0xFF330000),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0078C2), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EBFF), onPrimaryContainer = Color(0xFF00243D),
    secondary = Color(0xFF4A5BD6), onSecondary = Color.White,
    tertiary = Color(0xFF00796B), onTertiary = Color.White,
    background = Color(0xFFF4F8FF), onBackground = Color(0xFF0A1530),
    surface = Color.White, onSurface = Color(0xFF0A1530),
    surfaceVariant = Color(0xFFE6EEFB), onSurfaceVariant = Color(0xFF45557A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F6FF),
    surfaceContainer = Color(0xFFE9F1FF), surfaceContainerHigh = Color(0xFFDFEAFB), surfaceContainerHighest = Color(0xFFD5E2F7),
    outline = Color(0xFF8CA0C8), outlineVariant = Color(0xFFCBD7EE),
    error = Color(0xFFBA1A1A), onError = Color.White,
)

private val TraceonTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp),
)

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun TraceonTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (mode.isDark()) DarkScheme else LightScheme,
        typography = TraceonTypography,
        content = content,
    )
}
