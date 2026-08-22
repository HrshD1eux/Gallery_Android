package com.hrshd1eux.imava.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1), // Indigo
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4F46E5),
    secondary = Color(0xFF06B6D4), // Cyan
    onSecondary = Color.White,
    background = Color(0xFF090D16), // Dark slate
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF151D30),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF222E4A),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = Color(0xFFEF4444)
)

private val AmoledDarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF312E81),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFE2E8F0),
    error = Color(0xFFEF4444)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7D2FE),
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFDC2626)
)

@Composable
fun GalleryTheme(
    appTheme: String = "system",
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (appTheme) {
        "dark", "amoled" -> true
        "light" -> false
        else -> isSystemDark
    }

    val colorScheme = when {
        appTheme == "amoled" -> AmoledDarkColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && appTheme == "system" -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
