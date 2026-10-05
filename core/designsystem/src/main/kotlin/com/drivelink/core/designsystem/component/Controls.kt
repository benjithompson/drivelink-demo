package com.drivelink.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.drivelink.core.designsystem.theme.DlTheme

private val PillPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)

/** Solid brand pill: the main action on a screen. */
@Composable
fun DlPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = DlTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PillPadding,
        colors = ButtonDefaults.buttonColors(containerColor = c.brand, contentColor = c.onBrand),
    ) { ButtonLabel(text, icon) }
}

/** Outlined pill: secondary action, e.g. "Stop charge". */
@Composable
fun DlOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = DlTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PillPadding,
        border = BorderStroke(1.dp, c.textPrimary),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary),
    ) { ButtonLabel(text, icon) }
}

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge)
}

/** Cyan pill: promotional or service action, e.g. "Schedule Service". */
@Composable
fun DlAccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        contentPadding = PillPadding,
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Small label pill on the status diagram. Alert = red fill. */
@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, alert: Boolean = false) {
    val c = DlTheme.colors
    Text(
        text,
        modifier = modifier
            .clip(CircleShape)
            .background(if (alert) c.error else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (alert) c.error else c.divider, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = if (alert) Color.White else c.textPrimary,
    )
}

/**
 * Thick battery bar: level label inside the fill, a darker segment up to the charge limit,
 * and an outlined limit chip under the bar.
 */
@Composable
fun ChargeProgressBar(percent: Int, limitPercent: Int, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "Battery $percent percent, limit $limitPercent percent"
        },
        horizontalAlignment = Alignment.End,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.card),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(limitPercent.coerceAtLeast(percent) / 100f)
                    .height(36.dp)
                    .background(c.successStrong),
            )
            Box(
                Modifier
                    .fillMaxWidth(percent / 100f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.success)
                    .padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .clip(CircleShape)
                .border(1.dp, c.textSecondary, CircleShape)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("$limitPercent%", style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
        }
    }
}

data class StatItem(val label: String, val value: String)

/**
 * Two-column grid of label/value pairs with dividers. [largeFirstRow] uses display-size values;
 * labels at [accentLabels] indexes use the accent color.
 */
@Composable
fun StatGrid(
    items: List<StatItem>,
    modifier: Modifier = Modifier,
    largeFirstRow: Boolean = false,
    accentLabels: Set<Int> = emptySet(),
) {
    val c = DlTheme.colors
    Column(modifier) {
        items.chunked(2).forEachIndexed { row, pair ->
            if (row > 0) HorizontalDivider(color = c.divider)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                pair.forEachIndexed { col, item ->
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text(item.label, style = MaterialTheme.typography.bodySmall, color = if (row * 2 + col in accentLabels) c.accent else c.textSecondary)
                        Text(
                            item.value,
                            style = if (largeFirstRow && row == 0) {
                                MaterialTheme.typography.displayMedium
                            } else {
                                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal)
                            },
                            color = c.textPrimary,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Remote command stages, in the order the user sees them. */
enum class CommandStage(val label: String) {
    Sending("Sending command…"),
    Waiting("Waiting for vehicle…"),
    Waking("Waking vehicle…"),
    Succeeded("Done"),
    Failed("Command failed"),
    TimedOut("Vehicle did not respond"),
}

/** Inline card for a running or finished remote command. */
@Composable
fun CommandProgressCard(
    commandLabel: String,
    stage: CommandStage,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val c = DlTheme.colors
    val (tint, icon) = when (stage) {
        CommandStage.Succeeded -> c.successStrong to Icons.Filled.CheckCircle
        CommandStage.Failed, CommandStage.TimedOut -> c.error to Icons.Filled.ErrorOutline
        else -> c.brand to null
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .padding(14.dp)
            .testTag("command_progress")
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon == null) {
            CircularProgressIndicator(color = tint, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(commandLabel, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(
                detail ?: stage.label,
                style = MaterialTheme.typography.bodySmall,
                color = if (icon != null) tint else c.textSecondary,
                modifier = Modifier.testTag("command_stage"),
            )
        }
        if (onRetry != null && (stage == CommandStage.Failed || stage == CommandStage.TimedOut)) {
            TextButton(onClick = onRetry, modifier = Modifier.testTag("command_retry")) { Text("Retry", color = c.brand) }
        }
        if (onDismiss != null && stage != CommandStage.Sending && stage != CommandStage.Waiting && stage != CommandStage.Waking) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp).testTag("command_dismiss")) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

enum class BannerKind { Info, Warning, Error, Stale }

/** Full-width message strip: offline, stale data, rate limit, server error. */
@Composable
fun DlBanner(
    kind: BannerKind,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = DlTheme.colors
    val (color, icon) = when (kind) {
        BannerKind.Info -> c.accent to Icons.Filled.Info
        BannerKind.Warning -> c.warning to Icons.Filled.WarningAmber
        BannerKind.Error -> c.error to Icons.Filled.ErrorOutline
        BannerKind.Stale -> c.textSecondary to Icons.Filled.Schedule
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("banner_${kind.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.testTag("banner_action")) { Text(actionLabel, color = c.brand) }
        }
    }
}

/** Underline tabs, e.g. "Quick View" / "Full List". */
@Composable
fun DlUnderlineTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    Row(modifier.fillMaxWidth()) {
        tabs.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(i) }
                    .testTag("tab_${label.lowercase().replace(' ', '_')}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (on) c.brand else c.textSecondary,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (on) 3.dp else 1.dp)
                        .background(if (on) c.brand else c.divider),
                )
            }
        }
    }
}

/** Label with a switch, for climate and settings options. */
@Composable
fun DlToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val c = DlTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.brand, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("toggle_${label.lowercase().replace(' ', '_')}"),
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.brand,
                uncheckedTrackColor = c.card,
                uncheckedThumbColor = c.iconInactive,
                uncheckedBorderColor = c.divider,
            ),
        )
    }
}

/** Temperature stepper: OFF, LO, 62–82 °F, HI. */
@Composable
fun TemperatureStepper(display: String, onDecrease: () -> Unit, onIncrease: () -> Unit, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        StepButton(Icons.Filled.Remove, "Lower temperature", onDecrease)
        Text(
            display,
            modifier = Modifier.padding(horizontal = 28.dp).testTag("climate_temperature"),
            style = MaterialTheme.typography.displayLarge,
            color = c.textPrimary,
        )
        StepButton(Icons.Filled.Add, "Raise temperature", onIncrease)
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = DlTheme.colors
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(c.tile),
    ) { Icon(icon, contentDescription = description, tint = c.onTile) }
}

/** Four-digit PIN entry with a numeric keypad. */
@Composable
fun PinPad(
    entered: Int,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Enter your PIN",
    error: String? = null,
) {
    val c = DlTheme.colors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.testTag("pin_dots")) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (i < entered) c.brand else Color.Transparent)
                        .border(2.dp, c.brand, CircleShape),
                )
            }
        }
        Text(
            error ?: " ",
            modifier = Modifier.padding(top = 12.dp).testTag("pin_error"),
            style = MaterialTheme.typography.bodySmall,
            color = c.error,
        )
        Spacer(Modifier.height(12.dp))
        val keys = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -2))
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(if (k >= 0) c.card else Color.Transparent)
                            .then(
                                when {
                                    k >= 0 -> Modifier.clickable { onDigit(k) }.testTag("pin_key_$k")
                                    k == -2 -> Modifier.clickable(onClick = onDelete).testTag("pin_key_delete")
                                    else -> Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            k >= 0 -> Text("$k", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                            k == -2 -> Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", tint = c.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

/** Floating chip that shows the active backend scenario and host, for the audience. */
@Composable
fun ScenarioChip(scenario: String, host: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = DlTheme.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(Color(0xE6111418))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("scenario_chip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (scenario == "default") c.success else c.warning))
        Spacer(Modifier.width(8.dp))
        Text(scenario, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
        Spacer(Modifier.width(6.dp))
        Text(host, style = MaterialTheme.typography.labelSmall, color = Color(0xFFB8C2CF))
    }
}

/**
 * Level selector, for example heated seats Off / 1 / 2 / 3. One pill per option; the selected pill is solid.
 * Tags: [tag] on the group, `<tag>_<index>` on each option.
 */
@Composable
fun DlLevelSelector(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val c = DlTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .testTag(tag)
            .semantics(mergeDescendants = false) { stateDescription = options.getOrNull(selectedIndex).orEmpty() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.brand, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
        Row(Modifier.clip(CircleShape).background(c.card)) {
            options.forEachIndexed { i, option ->
                val on = i == selectedIndex
                Text(
                    option,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) c.brand else Color.Transparent)
                        .clickable(role = Role.RadioButton) { onSelect(i) }
                        .semantics {
                            selected = on
                            contentDescription = "$label $option"
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("${tag}_$i"),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) c.onBrand else c.textPrimary,
                )
            }
        }
    }
}

/** Single-line text field in the app style: rounded outline, brand color when focused. */
@Composable
fun DlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = DlTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text(label) },
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.brand,
            unfocusedBorderColor = c.divider,
            focusedLabelColor = c.brand,
            unfocusedLabelColor = c.textSecondary,
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            cursorColor = c.brand,
            focusedContainerColor = c.row,
            unfocusedContainerColor = c.row,
        ),
    )
}

/**
 * Error state with the message, the correlation id and a Retry button.
 * Tags: `error_message`, `error_correlation_id`, `error_retry`.
 */
@Composable
fun ErrorState(
    message: String,
    correlationId: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Something went wrong",
) {
    val c = DlTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.card)
            .padding(20.dp)
            .testTag("error_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = c.error, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            message,
            modifier = Modifier.testTag("error_message"),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (correlationId != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "ID: $correlationId",
                modifier = Modifier.testTag("error_correlation_id"),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
        Spacer(Modifier.height(16.dp))
        DlPrimaryButton("Retry", onRetry, Modifier.testTag("error_retry"))
    }
}
