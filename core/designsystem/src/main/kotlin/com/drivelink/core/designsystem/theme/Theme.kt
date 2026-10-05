package com.drivelink.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/** Spacing scale, in dp. */
object DlSpacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 20.dp
}

/** Corner radii. Buttons and chips are full pills. */
object DlRadius {
    val card = 16.dp
    val tile = 14.dp
    val sheet = 24.dp
    val row = 12.dp
}

private val DlShapes = Shapes(
    small = RoundedCornerShape(DlRadius.row),
    medium = RoundedCornerShape(DlRadius.card),
    large = RoundedCornerShape(DlRadius.sheet),
)

private val LightScheme = lightColorScheme(
    primary = Navy,
    onPrimary = White,
    primaryContainer = TilePaleBlue,
    onPrimaryContainer = Navy,
    secondary = Cyan,
    onSecondary = White,
    secondaryContainer = TilePaleBlue,
    onSecondaryContainer = Navy,
    tertiaryContainer = TilePaleBlue,
    onTertiaryContainer = Navy,
    background = White,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    surfaceVariant = CardGray,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = CardGray,
    surfaceContainer = CardGray,
    surfaceContainerHigh = White,
    surfaceContainerHighest = CardGray,
    outline = Divider,
    outlineVariant = Divider,
    error = AlertRed,
)

private val DarkScheme = darkColorScheme(
    primary = DarkAccentNavy,
    onPrimary = White,
    primaryContainer = DarkTile,
    onPrimaryContainer = DarkTextPrimary,
    secondary = Cyan,
    onSecondary = White,
    secondaryContainer = DarkTile,
    onSecondaryContainer = DarkTextPrimary,
    tertiaryContainer = DarkTile,
    onTertiaryContainer = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLow = DarkCard,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkSurface,
    surfaceContainerHighest = DarkCard,
    outline = DarkDivider,
    outlineVariant = DarkDivider,
    error = DarkDlColors.error,
)

@Composable
fun DriveLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDlColors provides if (darkTheme) DarkDlColors else LightDlColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = DlTypography,
            shapes = DlShapes,
            content = content,
        )
    }
}

/** Shortcut: `DlTheme.colors.tile`. */
object DlTheme {
    val colors: DlColors
        @Composable @ReadOnlyComposable get() = LocalDlColors.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalDlColors.current === DarkDlColors
}
