package com.drivelink.demo.menu

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.BannerKind
import com.drivelink.core.designsystem.component.DlBanner
import com.drivelink.core.designsystem.component.DlToggleRow
import com.drivelink.core.designsystem.component.InfoRow
import com.drivelink.core.designsystem.component.PinPad
import com.drivelink.core.designsystem.component.SectionTitle
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.config.NotificationKind
import com.drivelink.core.domain.config.ThemeMode

/** Settings sub-screen. The app shell draws the top bar with the back arrow. */
@Composable
fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onTheme = viewModel::setTheme,
        onNotification = viewModel::setNotification,
        onChangePin = viewModel::startChangePin,
        onPinDigit = viewModel::pinDigit,
        onPinDelete = viewModel::pinDelete,
        onPinCancel = viewModel::cancelChangePin,
        modifier = modifier,
    )
}

private val ThemeOptions = listOf(
    Triple(ThemeMode.SYSTEM, "System", "settings_theme_system"),
    Triple(ThemeMode.LIGHT, "Light", "settings_theme_light"),
    Triple(ThemeMode.DARK, "Dark", "settings_theme_dark"),
)

private val NotificationRows = listOf(
    NotificationKind.VEHICLE_ALERTS to "Vehicle alerts",
    NotificationKind.CHARGING_UPDATES to "Charging updates",
    NotificationKind.SERVICE_REMINDERS to "Service reminders",
)

/**
 * Test tags: `screen_settings`, `settings_theme`, `settings_theme_system`, `settings_theme_light`,
 * `settings_theme_dark`, `settings_units`, `settings_change_pin`, `settings_pin_changed`,
 * `settings_notifications`, `toggle_vehicle_alerts`, `toggle_charging_updates`,
 * `toggle_service_reminders`, `settings_version`; the Change PIN overlay `settings_pin_dialog`,
 * `settings_pin_cancel`, `settings_pin_step_current`, `settings_pin_step_new`, `settings_pin_step_confirm`, the PIN pad tags `pin_key_0` to `pin_key_9`, `pin_key_delete`,
 * `pin_dots`, `pin_error`.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onTheme: (ThemeMode) -> Unit,
    onNotification: (NotificationKind, Boolean) -> Unit,
    onChangePin: () -> Unit,
    onPinDigit: (Int) -> Unit,
    onPinDelete: () -> Unit,
    onPinCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    Box(modifier.fillMaxSize().testTag("screen_settings")) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Appearance")
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(c.card).testTag("settings_theme"),
            ) {
                ThemeOptions.forEach { (mode, label, tag) ->
                    val on = state.theme == mode
                    Text(
                        label,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (on) c.brand else Color.Transparent)
                            .clickable(role = Role.RadioButton) { onTheme(mode) }
                            .semantics { selected = on }
                            .padding(vertical = 12.dp)
                            .testTag(tag),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) c.onBrand else c.textPrimary,
                    )
                }
            }
            SectionTitle("Units")
            Column(Modifier.fillMaxWidth().testTag("settings_units")) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Distance and temperature",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(state.units, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
                Text(
                    if (state.unitsAreDefault) "Your account units did not load. The defaults show."
                    else "Set in your account. You cannot change them in the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
            SectionTitle("Security")
            InfoRow(
                Icons.Filled.Lock, "Change PIN", "",
                showDivider = false, onClick = onChangePin, modifier = Modifier.testTag("settings_change_pin"),
            )
            if (state.pinChanged) {
                DlBanner(BannerKind.Info, "Your PIN was changed.", modifier = Modifier.testTag("settings_pin_changed"))
            }
            SectionTitle("Notifications")
            Column(Modifier.fillMaxWidth().testTag("settings_notifications")) {
                NotificationRows.forEach { (kind, label) ->
                    DlToggleRow(label, checked = kind in state.notifications, onCheckedChange = { onNotification(kind, it) })
                }
                Text(
                    "These switches are stored on this device. The demo sends no push messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
            SectionTitle("About")
            Text(
                "Version ${state.version}",
                modifier = Modifier.testTag("settings_version"),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
        }
        state.changePin?.let { pin ->
            BackHandler(onBack = onPinCancel)
            ChangePinOverlay(pin, onPinDigit, onPinDelete, onPinCancel)
        }
    }
}

@Composable
private fun ChangePinOverlay(
    pin: ChangePinState,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val title = when (pin.step) {
        PinStep.CURRENT -> "Enter your current PIN"
        PinStep.NEW -> "Enter a new PIN"
        PinStep.CONFIRM -> "Confirm the new PIN"
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .testTag("settings_pin_dialog"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.testTag("settings_pin_step_${pin.step.name.lowercase()}")) {
            PinPad(entered = pin.entered.length, onDigit = onDigit, onDelete = onDelete, title = title, error = pin.error)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel, modifier = Modifier.testTag("settings_pin_cancel")) { Text("Cancel") }
    }
}
