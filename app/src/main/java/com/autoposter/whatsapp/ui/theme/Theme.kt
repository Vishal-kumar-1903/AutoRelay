package com.autoposter.whatsapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BrandEmerald,
    onPrimary = LightSurface,
    secondary = BrandTealDark,
    background = LightBg,
    surface = LightSurface,
    surfaceVariant = LightCard,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandEmerald,
    onPrimary = DarkBg,
    secondary = BrandTealDark,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary
)

@Composable
fun WhatsAppAutoPosterTheme(
    darkTheme: Boolean = false, // Default is Light Mode!
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
