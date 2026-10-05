package com.drivelink.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light palette.
internal val Navy = Color(0xFF002C5E)
internal val NavyPressed = Color(0xFF001F44)
internal val Cyan = Color(0xFF00B0DB)
internal val CyanLight = Color(0xFFE5F7FA)
internal val TilePaleBlue = Color(0xFFC7E2EF)
internal val HeroTop = Color(0xFFE9F7FB)
internal val ChargeGreen = Color(0xFF00D17E)
internal val ChargeGreenDark = Color(0xFF02B774)
internal val ChargeMint = Color(0xFFDDF8EC)
internal val AlertRed = Color(0xFFE63212)
internal val WarnAmber = Color(0xFFF2A900)
internal val White = Color(0xFFFFFFFF)
internal val RowGray = Color(0xFFF9F9F9)
internal val CardGray = Color(0xFFF5F5F5)
internal val Divider = Color(0xFFEBEBEB)
internal val TextPrimary = Color(0xFF111418)
internal val TextSecondary = Color(0xFF6B7280)
internal val IconInactive = Color(0xFF9AA0A6)

// Dark palette. Original design: the reference app has no dark theme.
internal val DarkBackground = Color(0xFF0A1424)
internal val DarkSurface = Color(0xFF111F35)
internal val DarkCard = Color(0xFF172A45)
internal val DarkTile = Color(0xFF1E3A5C)
internal val DarkDivider = Color(0xFF233654)
internal val DarkTextPrimary = Color(0xFFF2F5F9)
internal val DarkTextSecondary = Color(0xFF9FB0C6)
internal val DarkAccentNavy = Color(0xFF3D7BD9)

/** Brand roles that Material 3's ColorScheme does not cover. */
@Immutable
data class DlColors(
    val topBar: Color,
    val onTopBar: Color,
    val brand: Color,
    val onBrand: Color,
    val accent: Color,
    val accentContainer: Color,
    val tile: Color,
    val onTile: Color,
    val tileActive: Color,
    val onTileActive: Color,
    val heroTop: Color,
    val heroBottom: Color,
    val heroHill: Color,
    val row: Color,
    val card: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val iconInactive: Color,
    val success: Color,
    val successStrong: Color,
    val chargeHeader: Color,
    val warning: Color,
    val error: Color,
    val navBar: Color,
)

internal val LightDlColors = DlColors(
    topBar = Navy,
    onTopBar = White,
    brand = Navy,
    onBrand = White,
    accent = Cyan,
    accentContainer = CyanLight,
    tile = TilePaleBlue,
    onTile = Navy,
    tileActive = Navy,
    onTileActive = White,
    heroTop = HeroTop,
    heroBottom = White,
    heroHill = Color(0xFFDDEFF6),
    row = RowGray,
    card = CardGray,
    divider = Divider,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    iconInactive = IconInactive,
    success = ChargeGreen,
    successStrong = ChargeGreenDark,
    chargeHeader = ChargeMint,
    warning = WarnAmber,
    error = AlertRed,
    navBar = White,
)

internal val DarkDlColors = DlColors(
    topBar = DarkSurface,
    onTopBar = DarkTextPrimary,
    brand = DarkAccentNavy,
    onBrand = White,
    accent = Cyan,
    accentContainer = Color(0xFF0E3341),
    tile = DarkTile,
    onTile = DarkTextPrimary,
    tileActive = DarkAccentNavy,
    onTileActive = White,
    heroTop = Color(0xFF13294A),
    heroBottom = DarkBackground,
    heroHill = Color(0xFF162E50),
    row = DarkSurface,
    card = DarkCard,
    divider = DarkDivider,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    iconInactive = Color(0xFF6E7F96),
    success = ChargeGreen,
    successStrong = ChargeGreen,
    chargeHeader = Color(0xFF0F3A2C),
    warning = WarnAmber,
    error = Color(0xFFFF5A3C),
    navBar = DarkSurface,
)

val LocalDlColors = staticCompositionLocalOf { LightDlColors }
