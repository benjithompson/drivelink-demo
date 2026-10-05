package com.drivelink.demo.charging

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.BannerKind
import com.drivelink.core.designsystem.component.DlBanner
import com.drivelink.core.designsystem.component.DlCard
import com.drivelink.core.designsystem.component.DlPrimaryButton
import com.drivelink.core.designsystem.component.ErrorState
import com.drivelink.core.designsystem.component.SectionTitle
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.model.DayOfWeek
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.core.domain.format.displayMessageWithId

/** Charging Schedule sub-screen: schedules and departure. [onDone] runs after a successful save. */
@Composable
fun ChargeScheduleRoute(onDone: () -> Unit, viewModel: ScheduleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    ScheduleScreen(
        state = state,
        onRetry = { viewModel.load() },
        onScheduleEnabled = viewModel::setScheduleEnabled,
        onScheduleStart = viewModel::setScheduleStart,
        onScheduleEnd = viewModel::setScheduleEnd,
        onScheduleDay = viewModel::toggleScheduleDay,
        onDepartureEnabled = viewModel::setDepartureEnabled,
        onDepartureTime = viewModel::setDepartureTime,
        onDepartureDay = viewModel::toggleDepartureDay,
        onSave = viewModel::save,
    )
}

@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onRetry: () -> Unit,
    onScheduleEnabled: (Int, Boolean) -> Unit,
    onScheduleStart: (Int, String) -> Unit,
    onScheduleEnd: (Int, String) -> Unit,
    onScheduleDay: (Int, DayOfWeek) -> Unit,
    onDepartureEnabled: (Boolean) -> Unit,
    onDepartureTime: (String) -> Unit,
    onDepartureDay: (DayOfWeek) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("screen_charge_schedule"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            !state.available -> Text(
                "Charging is not available for this vehicle",
                modifier = Modifier.testTag("schedule_unavailable"),
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
            )
            state.loadError != null && !state.loaded -> ErrorState(
                message = state.loadError.displayMessage(),
                correlationId = state.loadError.correlationId,
                onRetry = onRetry,
                title = "Couldn't load the schedule",
            )
            !state.loaded -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.brand, modifier = Modifier.testTag("schedule_loading"))
            }
            else -> Form(
                state, onScheduleEnabled, onScheduleStart, onScheduleEnd, onScheduleDay,
                onDepartureEnabled, onDepartureTime, onDepartureDay, onSave,
            )
        }
        Spacer(Modifier.height(64.dp))
    }
}

@Composable
private fun Form(
    state: ScheduleUiState,
    onScheduleEnabled: (Int, Boolean) -> Unit,
    onScheduleStart: (Int, String) -> Unit,
    onScheduleEnd: (Int, String) -> Unit,
    onScheduleDay: (Int, DayOfWeek) -> Unit,
    onDepartureEnabled: (Boolean) -> Unit,
    onDepartureTime: (String) -> Unit,
    onDepartureDay: (DayOfWeek) -> Unit,
    onSave: () -> Unit,
) {
    val c = DlTheme.colors
    SectionTitle("Charging Schedules")
    if (state.schedules.isEmpty()) {
        Text("No charging schedules.", modifier = Modifier.testTag("schedule_empty"), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
    state.schedules.forEachIndexed { i, schedule ->
        DlCard(Modifier.testTag("schedule_$i")) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EnabledRow("Schedule ${i + 1}", schedule.enabled, "schedule_enabled_$i") { onScheduleEnabled(i, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField("Start", schedule.start, "schedule_start_$i", Modifier.weight(1f)) { onScheduleStart(i, it) }
                    TimeField("End", schedule.end, "schedule_end_$i", Modifier.weight(1f)) { onScheduleEnd(i, it) }
                }
                DayChips(schedule.days, "schedule_day_$i") { onScheduleDay(i, it) }
            }
        }
    }
    SectionTitle("Departure")
    state.departure?.let { departure ->
        DlCard(Modifier.testTag("departure")) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EnabledRow("Departure time", departure.enabled, "departure_enabled", onDepartureEnabled)
                TimeField("Leave at", departure.time, "departure_time", Modifier.fillMaxWidth(), onDepartureTime)
                DayChips(departure.days, "departure_day", onDepartureDay)
                Text(
                    "The vehicle charges and warms up so that it is ready at this time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }
    }
    if (!state.valid) {
        Text(
            "Pick at least one day for each schedule that is on.",
            modifier = Modifier.testTag("schedule_invalid"),
            style = MaterialTheme.typography.bodySmall,
            color = c.error,
        )
    }
    state.saveError?.let {
        DlBanner(
            BannerKind.Error,
            "Could not save. " + it.displayMessageWithId(),
            actionLabel = "Retry",
            onAction = onSave,
        )
    }
    DlPrimaryButton(
        if (state.saving) "Saving…" else "Save",
        onSave,
        Modifier.fillMaxWidth().testTag("schedule_save"),
        enabled = !state.saving && state.valid,
    )
}

@Composable
private fun EnabledRow(label: String, enabled: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val c = DlTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
            modifier = Modifier.testTag(tag),
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.brand,
                uncheckedTrackColor = c.card,
                uncheckedThumbColor = c.iconInactive,
                uncheckedBorderColor = c.divider,
            ),
        )
    }
}

/** A time (HH:mm) shown as "7:30 AM". A tap opens a time picker. The text has the tag `<tag>_value`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(label: String, time: String, tag: String, modifier: Modifier, onChange: (String) -> Unit) {
    val c = DlTheme.colors
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable(role = Role.Button) { picking = true }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label ${formatTime(time)}" }
            .testTag(tag),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Text(formatTime(time), modifier = Modifier.testTag("${tag}_value"), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
    }
    if (picking) {
        val parts = time.split(":")
        val picker = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(label) },
            text = { TimePicker(picker) },
            confirmButton = {
                TextButton(
                    onClick = {
                        picking = false
                        onChange(String.format(java.util.Locale.US, "%02d:%02d", picker.hour, picker.minute))
                    },
                    modifier = Modifier.testTag("time_ok"),
                ) { Text("OK", color = c.brand) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }, modifier = Modifier.testTag("time_cancel")) {
                    Text("Cancel", color = c.textSecondary)
                }
            },
        )
    }
}

/** Seven day buttons, Monday first. Tags: `<tag>_<MON..SUN>`. */
@Composable
private fun DayChips(days: Set<DayOfWeek>, tag: String, onToggle: (DayOfWeek) -> Unit) {
    val c = DlTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            val on = day in days
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (on) c.brand else MaterialTheme.colorScheme.background)
                    .clickable(role = Role.Checkbox) { onToggle(day) }
                    .semantics {
                        selected = on
                        contentDescription = fullName(day)
                        stateDescription = if (on) "On" else "Off"
                    }
                    .testTag("${tag}_${day.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.name.take(1),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) c.onBrand else c.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun fullName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MON -> "Monday"
    DayOfWeek.TUE -> "Tuesday"
    DayOfWeek.WED -> "Wednesday"
    DayOfWeek.THU -> "Thursday"
    DayOfWeek.FRI -> "Friday"
    DayOfWeek.SAT -> "Saturday"
    DayOfWeek.SUN -> "Sunday"
}
