package com.drivelink.demo.carcare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.core.domain.model.Maintenance
import com.drivelink.core.domain.model.ServiceItemStatus
import com.drivelink.core.domain.model.ServiceRequest
import com.drivelink.core.domain.model.ServiceRequestCreate
import com.drivelink.core.domain.model.ServiceRequestStatus
import com.drivelink.core.domain.repository.VehicleRepository
import com.drivelink.demo.screenload.LoadState
import com.drivelink.demo.screenload.SelectedVehicleLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** The preferred time of day. The API has no field for it; the app adds it to the notes. */
enum class TimeSlot(val label: String, val range: String) {
    Morning("Morning", "8-11 am"),
    Midday("Midday", "11 am-2 pm"),
    Afternoon("Afternoon", "2-5 pm"),
    ;

    /** The text in the notes of the request. */
    val noteText: String get() = "$label ($range)"
}

/** One service checkbox. */
data class ServiceOptionUi(val id: String, val name: String, val detail: String?, val status: ServiceItemStatus, val checked: Boolean)

/** A message under a form field. */
data class FieldErrors(val date: String? = null, val services: String? = null)

/** A failed submit: the message and the correlation id of the request. */
data class FormError(val message: String, val correlationId: String?)

/** What the confirmation shows after the server accepted the request (201). */
data class ConfirmationUi(val requestId: String, val statusLabel: String, val dateText: String, val centerName: String)

data class ServiceRequestUiState(
    /** The maintenance document loads. */
    val loading: Boolean = true,
    /** The maintenance document failed to load; the form cannot start. */
    val loadError: AppError? = null,
    val center: ServiceCenterUi? = null,
    val options: List<ServiceOptionUi> = emptyList(),
    val otherChecked: Boolean = false,
    val date: LocalDate? = null,
    val dateText: String? = null,
    /** The first and the last day the user can choose: tomorrow and 30 days from today. */
    val minDate: LocalDate,
    val maxDate: LocalDate,
    val slot: TimeSlot? = null,
    val notes: String = "",
    val notesLimit: Int = NOTES_LIMIT,
    val errors: FieldErrors = FieldErrors(),
    val submitting: Boolean = false,
    val submitError: FormError? = null,
    val confirmation: ConfirmationUi? = null,
) {
    val canSubmit: Boolean get() = center != null && !submitting && confirmation == null
}

/** The user can type this many characters of notes. The app adds up to ~80 characters (slot, other) to stay under the API limit of 500. */
const val NOTES_LIMIT = 400

private val DateText = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)

/** Text of a date in the form and in the confirmation, for example "Sat, Oct 10, 2026". */
fun formatServiceDate(date: LocalDate): String = date.format(DateText)

/** The `notes` value of the request: the time slot, the "Other" marker and the user's text. Null when all are empty. */
fun buildNotes(slot: TimeSlot?, other: Boolean, notes: String): String? =
    listOfNotNull(
        slot?.let { "Preferred time: ${it.noteText}" },
        if (other) "Other service requested" else null,
        notes.trim().ifEmpty { null },
    ).joinToString("\n").ifEmpty { null }

private data class FormState(
    val date: LocalDate? = null,
    val slot: TimeSlot? = null,
    val checked: Set<String> = emptySet(),
    val other: Boolean = false,
    val notes: String = "",
    val errors: FieldErrors = FieldErrors(),
    val submitting: Boolean = false,
    val submitError: FormError? = null,
    val confirmation: ConfirmationUi? = null,
)

/**
 * Schedule Service. Loads the maintenance document (the preferred service center and the items),
 * validates the form (a date from tomorrow to 30 days ahead, and at least one service) and sends
 * createServiceRequest. A failed submit keeps the form. [AppError.Unauthorized] ends the session.
 */
@HiltViewModel
class ServiceRequestViewModel(
    private val garage: GarageRepository,
    private val vehicles: VehicleRepository,
    config: DemoConfig,
    private val clock: Clock,
) : ViewModel() {

    @Inject constructor(garage: GarageRepository, vehicles: VehicleRepository, config: DemoConfig) :
        this(garage, vehicles, config, Clock.systemDefaultZone())

    private val loader = SelectedVehicleLoader<Maintenance>(viewModelScope, garage, config) { vehicles.getMaintenance(it) }
    private val form = MutableStateFlow(FormState())

    private val today: LocalDate get() = LocalDate.now(clock)

    val state: StateFlow<ServiceRequestUiState> = combine(loader.state, form) { load, f -> uiState(load, f) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, uiState(LoadState(), FormState()))

    private fun uiState(load: LoadState<Maintenance>, f: FormState): ServiceRequestUiState {
        val maintenance = load.data
        val content = maintenance?.let { carCareContent(it) }
        val ordered = content?.items.orEmpty().sortedBy { if (it.status == ServiceItemStatus.UPCOMING) 1 else 0 }
        return ServiceRequestUiState(
            loading = load.loading && maintenance == null,
            loadError = if (maintenance == null) load.error else null,
            center = content?.center,
            options = ordered.map { ServiceOptionUi(it.id, it.name, it.due, it.status, it.id in f.checked) },
            otherChecked = f.other,
            date = f.date,
            dateText = f.date?.let(::formatServiceDate),
            minDate = today.plusDays(1),
            maxDate = today.plusDays(MAX_DAYS_AHEAD),
            slot = f.slot,
            notes = f.notes,
            errors = f.errors,
            submitting = f.submitting,
            submitError = f.submitError,
            confirmation = f.confirmation,
        )
    }

    /** Loads on first use, and again when the vehicle, profile or scenario changed. Call on each resume. */
    fun onShown() = loader.onShown()

    /** Retry after the load failed. */
    fun reload() = loader.refresh()

    /** Ignores a date outside tomorrow..+30 days. */
    fun setDate(date: LocalDate) {
        if (date < today.plusDays(1) || date > today.plusDays(MAX_DAYS_AHEAD)) return
        form.update { it.copy(date = date, errors = it.errors.copy(date = null), submitError = null) }
    }

    /** A second tap on the chosen slot clears it. */
    fun setSlot(slot: TimeSlot) {
        form.update { it.copy(slot = if (it.slot == slot) null else slot) }
    }

    fun toggleService(id: String) {
        form.update {
            val checked = if (id in it.checked) it.checked - id else it.checked + id
            it.copy(checked = checked, errors = it.errors.copy(services = null), submitError = null)
        }
    }

    fun toggleOther() {
        form.update { it.copy(other = !it.other, errors = it.errors.copy(services = null), submitError = null) }
    }

    fun setNotes(text: String) {
        form.update { it.copy(notes = text.take(NOTES_LIMIT)) }
    }

    /** Validates, then sends the request. Does nothing while a request runs or after the confirmation. */
    fun submit() {
        val f = form.value
        if (f.submitting || f.confirmation != null) return
        val load = loader.state.value
        val maintenance = load.data
        val center = maintenance?.preferredServiceCenter
        if (center == null) {
            form.update { it.copy(submitError = FormError("No preferred service center is set for this vehicle.", null)) }
            return
        }
        val validIds = maintenance.items.map { it.id }.toSet()
        val itemIds = maintenance.items.map { it.id }.filter { it in f.checked && it in validIds }
        val errors = FieldErrors(
            date = if (f.date == null) "Choose a date." else null,
            services = if (itemIds.isEmpty() && !f.other) "Choose at least one service." else null,
        )
        if (errors.date != null || errors.services != null) {
            form.update { it.copy(errors = errors, submitError = null) }
            return
        }
        val vin = garage.state.value.selected?.vin
        if (vin == null) {
            form.update { it.copy(submitError = FormError("No vehicle is selected.", null)) }
            return
        }
        val date = requireNotNull(f.date)
        val request = ServiceRequestCreate(
            serviceCenterId = center.id,
            preferredDate = date.toString(),
            itemIds = itemIds.ifEmpty { null },
            notes = buildNotes(f.slot, f.other, f.notes),
        )
        form.update { it.copy(errors = FieldErrors(), submitting = true, submitError = null) }
        viewModelScope.launch {
            when (val result = vehicles.createServiceRequest(vin, request)) {
                is Outcome.Ok -> form.update {
                    it.copy(submitting = false, confirmation = confirmation(result.value, date, center.name))
                }
                is Outcome.Err -> {
                    form.update {
                        it.copy(submitting = false, submitError = FormError(result.error.displayMessage(), result.error.correlationId))
                    }
                    if (result.error is AppError.Unauthorized) garage.expireSession(result.error.message)
                }
            }
        }
    }

    private fun confirmation(created: ServiceRequest, date: LocalDate, centerName: String) = ConfirmationUi(
        requestId = created.requestId,
        statusLabel = when (created.status) {
            ServiceRequestStatus.REQUESTED -> "Requested"
            ServiceRequestStatus.CONFIRMED -> "Confirmed"
            ServiceRequestStatus.DECLINED -> "Declined"
        },
        dateText = formatServiceDate(date),
        centerName = centerName,
    )

    private companion object {
        const val MAX_DAYS_AHEAD = 30L
    }
}
