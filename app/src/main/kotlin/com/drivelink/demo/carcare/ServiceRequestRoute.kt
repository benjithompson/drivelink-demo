package com.drivelink.demo.carcare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.DlCard
import com.drivelink.core.designsystem.component.DlPrimaryButton
import com.drivelink.core.designsystem.component.ErrorState
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.core.domain.model.ServiceItemStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Schedule Service sub-screen (createServiceRequest). [onDone] returns to Car Care. The top bar comes from the app shell. */
@Composable
fun ServiceRequestRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServiceRequestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onShown() }
    ServiceRequestScreen(
        state = state,
        actions = ServiceRequestActions(
            onDate = viewModel::setDate,
            onSlot = viewModel::setSlot,
            onService = viewModel::toggleService,
            onOther = viewModel::toggleOther,
            onNotes = viewModel::setNotes,
            onSubmit = viewModel::submit,
            onRetryLoad = viewModel::reload,
            onDone = onDone,
        ),
        modifier = modifier,
    )
}

/** What the user can do on Schedule Service. */
data class ServiceRequestActions(
    val onDate: (LocalDate) -> Unit,
    val onSlot: (TimeSlot) -> Unit,
    val onService: (String) -> Unit,
    val onOther: () -> Unit,
    val onNotes: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onRetryLoad: () -> Unit,
    val onDone: () -> Unit,
)

/**
 * Tags: `screen_service_request`, `service_loading`, `service_form_center`, `service_no_center`, `service_date`,
 * `service_date_picker`, `service_date_confirm`, `service_date_error`, `slot_morning`, `slot_midday`,
 * `slot_afternoon`, `service_option_<itemId>`, `service_option_other`, `service_services_error`, `service_notes`,
 * `service_error`, `service_error_message`, `service_error_correlation_id`, `service_submit`, `service_confirmation`,
 * `service_confirmation_title`, `service_request_id`, `service_confirmation_date`, `service_confirmation_center`,
 * `service_confirmation_status`, `service_done`.
 */
@Composable
fun ServiceRequestScreen(
    state: ServiceRequestUiState,
    actions: ServiceRequestActions,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("screen_service_request"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            state.confirmation != null -> Confirmation(state.confirmation, actions.onDone)
            state.loadError != null -> ErrorState(
                message = state.loadError.displayMessage(),
                correlationId = state.loadError.correlationId,
                onRetry = actions.onRetryLoad,
                title = "Couldn't load your service center",
            )
            state.loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.brand, modifier = Modifier.testTag("service_loading"))
            }
            else -> Form(state, actions)
        }
        // The scenario chip (bottom left, drawn by the app shell) must cover no text.
        Spacer(Modifier.height(64.dp))
    }
}

@Composable
private fun Form(state: ServiceRequestUiState, actions: ServiceRequestActions) {
    val c = DlTheme.colors

    FieldLabel("Service center")
    val center = state.center
    if (center == null) {
        Text(
            "No preferred service center is set for this vehicle.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.error,
            modifier = Modifier.testTag("service_no_center"),
        )
    } else {
        DlCard(Modifier.testTag("service_form_center"), color = c.accentContainer) {
            Column {
                Text(center.name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(center.address, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            }
        }
    }

    FieldLabel("Preferred date")
    DateField(state, actions.onDate)
    state.errors.date?.let { FieldError(it, "service_date_error") }

    FieldLabel("Preferred time (optional)")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeSlot.entries.forEach { slot ->
            FilterChip(
                selected = state.slot == slot,
                onClick = { actions.onSlot(slot) },
                label = {
                    Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(slot.label, style = MaterialTheme.typography.labelLarge)
                        Text(slot.range, style = MaterialTheme.typography.bodySmall)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { contentDescription = "${slot.label}, ${slot.range}" }
                    .testTag("slot_${slot.name.lowercase()}"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = c.brand,
                    selectedLabelColor = c.onBrand,
                    labelColor = c.textPrimary,
                ),
            )
        }
    }

    FieldLabel("Services")
    Column {
        state.options.forEach { option ->
            ServiceCheckbox(
                label = option.name,
                detail = listOfNotNull(option.status.label(), option.detail).joinToString(" · "),
                checked = option.checked,
                onToggle = { actions.onService(option.id) },
                tag = "service_option_${option.id}",
            )
        }
        ServiceCheckbox(
            label = "Other",
            detail = "Describe it in the notes",
            checked = state.otherChecked,
            onToggle = actions.onOther,
            tag = "service_option_other",
        )
    }
    state.errors.services?.let { FieldError(it, "service_services_error") }

    FieldLabel("Notes (optional)")
    OutlinedTextField(
        value = state.notes,
        onValueChange = actions.onNotes,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 96.dp).testTag("service_notes"),
        placeholder = { Text("Anything the service center should know") },
        supportingText = { Text("${state.notes.length}/${state.notesLimit}") },
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.brand,
            unfocusedBorderColor = c.divider,
            focusedTextColor = c.textPrimary,
            unfocusedTextColor = c.textPrimary,
            cursorColor = c.brand,
            focusedContainerColor = c.row,
            unfocusedContainerColor = c.row,
        ),
    )

    state.submitError?.let { error ->
        Column(
            Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, c.error), RoundedCornerShape(12.dp))
                .padding(12.dp)
                .testTag("service_error"),
        ) {
            Text(error.message, style = MaterialTheme.typography.bodyMedium, color = c.error, modifier = Modifier.testTag("service_error_message"))
            error.correlationId?.let {
                Text("ID: $it", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.testTag("service_error_correlation_id"))
            }
        }
    }

    DlPrimaryButton(
        if (state.submitting) "Sending request…" else "Request Service",
        actions.onSubmit,
        Modifier.fillMaxWidth().testTag("service_submit"),
        enabled = state.canSubmit,
    )
}

private fun ServiceItemStatus.label(): String? = when (this) {
    ServiceItemStatus.UPCOMING -> null
    ServiceItemStatus.DUE -> "Due"
    ServiceItemStatus.OVERDUE -> "Overdue"
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = DlTheme.colors.textPrimary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun FieldError(text: String, tag: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = DlTheme.colors.error, modifier = Modifier.testTag(tag))
}

@Composable
private fun ServiceCheckbox(label: String, detail: String?, checked: Boolean, onToggle: () -> Unit, tag: String) {
    val c = DlTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 4.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = c.brand, checkmarkColor = c.onBrand, uncheckedColor = c.textSecondary),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            if (!detail.isNullOrEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
    }
}

/** The read-only date row. A tap opens the Material 3 date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(state: ServiceRequestUiState, onDate: (LocalDate) -> Unit) {
    val c = DlTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .border(BorderStroke(1.dp, if (state.errors.date != null) c.error else c.divider), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = "Choose a date") { open = true }
            .padding(horizontal = 16.dp)
            .testTag("service_date"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            state.dateText ?: "Choose a date",
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.dateText != null) c.textPrimary else c.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = c.brand, modifier = Modifier.size(22.dp))
    }
    if (open) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date?.toUtcMillis(),
            initialDisplayedMonthMillis = (state.date ?: state.minDate).toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toUtcDate() in state.minDate..state.maxDate
                override fun isSelectableYear(year: Int): Boolean = year in state.minDate.year..state.maxDate.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onDate(it.toUtcDate()) }
                        open = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                    modifier = Modifier.testTag("service_date_confirm"),
                ) { Text("OK", color = c.brand) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel", color = c.brand) } },
            modifier = Modifier.testTag("service_date_picker"),
        ) { DatePicker(state = pickerState) }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun Confirmation(confirmation: ConfirmationUi, onDone: () -> Unit) {
    val c = DlTheme.colors
    Column(
        Modifier.fillMaxWidth().testTag("service_confirmation"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = c.successStrong, modifier = Modifier.size(56.dp))
        Text(
            "Service requested",
            style = MaterialTheme.typography.titleLarge,
            color = c.textPrimary,
            modifier = Modifier.semantics { heading() }.testTag("service_confirmation_title"),
        )
        DlCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ConfirmationRow("Request ID", confirmation.requestId, "service_request_id")
                ConfirmationRow("Date", confirmation.dateText, "service_confirmation_date")
                ConfirmationRow("Service center", confirmation.centerName, "service_confirmation_center")
                ConfirmationRow("Status", confirmation.statusLabel, "service_confirmation_status")
            }
        }
        DlPrimaryButton("Done", onDone, Modifier.fillMaxWidth().testTag("service_done"))
    }
}

@Composable
private fun ConfirmationRow(label: String, value: String, tag: String) {
    val c = DlTheme.colors
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.testTag(tag))
    }
}
