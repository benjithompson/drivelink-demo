package com.drivelink.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.drivelink.core.designsystem.theme.DlRadius
import com.drivelink.core.designsystem.theme.DlTheme

/** Model year and name with a switcher caret; trim on a second line. */
@Composable
fun VehicleHeader(
    title: String,
    trim: String,
    onSwitch: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    Column(modifier) {
        Row(
            Modifier
                .then(if (onSwitch != null) Modifier.clickable(onClick = onSwitch) else Modifier)
                .testTag("vehicle_switcher"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
            if (onSwitch != null) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Switch vehicle", tint = c.textPrimary)
            }
        }
        Text(trim, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

/** Light gradient backdrop with soft hills, behind the vehicle image. */
@Composable
fun HeroBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = DlTheme.colors
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.verticalGradient(listOf(c.heroTop, c.heroBottom)))
            val hill = Path().apply {
                moveTo(0f, size.height * 0.72f)
                cubicTo(size.width * 0.3f, size.height * 0.52f, size.width * 0.55f, size.height * 0.78f, size.width, size.height * 0.58f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(hill, c.heroHill.copy(alpha = 0.6f))
        }
        content()
    }
}

/** Large light-weight number with a unit and a caption, e.g. "84 %" / "Battery". */
@Composable
fun BigStat(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = DlTheme.colors.textPrimary,
    centered: Boolean = false,
) {
    val c = DlTheme.colors
    Column(
        modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            buildAnnotatedString {
                append(value)
                withStyle(SpanStyle(fontSize = MaterialTheme.typography.bodyMedium.fontSize, fontWeight = FontWeight.Normal)) {
                    append(" $unit")
                }
            },
            style = MaterialTheme.typography.displayMedium,
            color = valueColor,
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

/** Gray pill with label, optional value and chevron, e.g. "Charge Schedule  On ›". */
@Composable
fun StatusChip(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = DlTheme.colors
    // The pill is about 32 dp high. A clickable chip gets a 48 dp touch area around it.
    Box(
        modifier
            .heightIn(min = if (onClick != null) 48.dp else 0.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(c.card)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = c.brand, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
            if (value != null) {
                Spacer(Modifier.width(8.dp))
                Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = c.brand)
            }
            if (onClick != null) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Square quick-action tile. Active = solid brand fill, white icon. Inactive = pale tile, brand icon.
 * [busy] shows a spinner over the icon while a command runs.
 */
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    busy: Boolean = false,
    enabled: Boolean = true,
    testTag: String = "tile_${label.lowercase().replace(' ', '_')}",
) {
    val c = DlTheme.colors
    val bg = if (active) c.tileActive else c.tile
    val fg = if (active) c.onTileActive else c.onTile
    Column(
        modifier.testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(DlRadius.tile))
                .background(
                    Brush.verticalGradient(
                        listOf(lerp(bg, Color.White, if (active) 0.12f else 0.55f), bg),
                    ),
                    alpha = if (enabled) 1f else 0.4f,
                )
                .clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = label
                    stateDescription = when {
                        busy -> "In progress"
                        active -> "On"
                        else -> "Off"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(color = fg, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
            } else {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Row of four equal tiles. */
@Composable
fun ActionTileRow(modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        content(Modifier.weight(1f))
    }
}

/** Flat list row: leading icon, label, value on the right, chevron, divider below. */
@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = DlTheme.colors.textSecondary,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = DlTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
        if (showDivider) HorizontalDivider(color = c.divider)
    }
}

/** Rounded gray card. */
@Composable
fun DlCard(
    modifier: Modifier = Modifier,
    color: Color = DlTheme.colors.card,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DlRadius.card))
            .background(color)
            .padding(16.dp),
    ) { content() }
}

/** Section heading inside a scrolling screen. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        color = DlTheme.colors.textPrimary,
    )
}
