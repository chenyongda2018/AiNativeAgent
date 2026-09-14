package com.yongda.ainativeagent.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LightMaterialColors = lightColorScheme(
    background = LightChatColors.background,
    surface = LightChatColors.surface,
    surfaceVariant = LightChatColors.surfaceVariant,
    primary = LightChatColors.brand,
    onPrimary = LightChatColors.onBrand,
    onBackground = LightChatColors.textPrimary,
    onSurface = LightChatColors.textPrimary,
    onSurfaceVariant = LightChatColors.textSecondary,
    error = LightChatColors.error,
    errorContainer = LightChatColors.errorContainer,
    onErrorContainer = LightChatColors.onErrorContainer,
    outline = LightChatColors.border,
)

private val DarkMaterialColors = darkColorScheme(
    background = DarkChatColors.background,
    surface = DarkChatColors.surface,
    surfaceVariant = DarkChatColors.surfaceVariant,
    primary = DarkChatColors.brand,
    onPrimary = DarkChatColors.onBrand,
    onBackground = DarkChatColors.textPrimary,
    onSurface = DarkChatColors.textPrimary,
    onSurfaceVariant = DarkChatColors.textSecondary,
    error = DarkChatColors.error,
    errorContainer = DarkChatColors.errorContainer,
    onErrorContainer = DarkChatColors.onErrorContainer,
    outline = DarkChatColors.border,
)

@Composable
fun ChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val chatColors = if (darkTheme) DarkChatColors else LightChatColors
    val materialColors = if (darkTheme) DarkMaterialColors else LightMaterialColors

    CompositionLocalProvider(LocalChatColors provides chatColors) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}

object ChatTheme {
    val colors: ChatColorScheme
        @Composable @ReadOnlyComposable
        get() = LocalChatColors.current
}

object ChatTactileTokens {
    val radiusSmall: Dp = 8.dp
    val radiusMedium: Dp = 12.dp
    val radiusLarge: Dp = 16.dp
    val radiusPanel: Dp = 24.dp
    val toolbarControl: Dp = 40.dp
    val composerControl: Dp = 40.dp
    val minimumTouchTarget: Dp = 44.dp
    val elevationRaised: Dp = 2.dp
    val elevationFloating: Dp = 10.dp
}
