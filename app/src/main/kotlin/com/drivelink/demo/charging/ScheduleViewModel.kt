package com.drivelink.demo.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.ChargeSchedule
import com.drivelink.core.domain.model.ChargeSettings
import com.drivelink.core.domain.model.DayOfWeek
import com.drivelink.core.domain.model.Departure
import com.drivelink.core.domain.model.Powertrain
import com.drivelink.demo.remote.RemoteCommands
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One charging schedule in the form. Times are HH:mm. */
data class ScheduleDraft(
    val id: String,
    val days: Set<DayOfWeek>,
    val start: String,
    val end: String,
    val enabled: Boolean,
)

/** The departure in the form. */
data class DepartureDraft(val enabled: Boolean, val days: Set<DayOfWeek>, val time: String)

data class ScheduleUiState(
    /** False for a gas car. */
    val available: Boolean = true,
    val loading: Boolean = true,
    val loadError: AppError? = null,
    val schedules: List<ScheduleDraft> = emptyList(),
    val departure: DepartureDraft? = null,
    val saving: Boolean = false,
    /** The last save failed. The form keeps the values of the user. */
    val saveError: AppError? = null,
    /** The save succeeded. The screen leaves. */
    val saved: Boolean = false,
) {
    val loaded: Boolean get() = departure != null
    /** An enabled schedule or departure needs at least one day. */
    val valid: Boolean
        get() = schedules.all { !it.enabled || it.days.isNotEmpty() } &&
            (departure == null || !departure.enabled || departure.days.isNotEmpty())
}

/**
 * Charging Schedule form. Loads the charge settings (kept in [GarageRepository]), edits copies, and
 * saves the whole settings object with PUT. A failed save keeps the form.
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val garage: GarageRepository,
    private val remote: RemoteCommands,
) : ViewModel() {

    private val ui = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = ui.asStateFlow()

    init {
        viewModelScope.launch {
            garage.ensureLoaded()
            load(force = false)
        }
    }

    /** Loads the settings and fills the form. Retry calls it with [force]. */
    fun load(force: Boolean = true) {
        viewModelScope.launch {
            if (garage.state.value.selected?.powertrain == Powertrain.ICE) {
                ui.update { it.copy(available = false, loading = false) }
                return@launch
            }
            ui.update { it.copy(loading = true, loadError = null) }
            when (val result = garage.loadChargeSettings(force)) {
                is Outcome.Ok -> ui.update { fill(it, result.value) }
                is Outcome.Err -> {
                    ui.update { it.copy(loading = false, loadError = result.error) }
                    if (result.error is AppError.Unauthorized) expire(result.error)
                }
            }
        }
    }

    fun setScheduleEnabled(index: Int, enabled: Boolean) = editSchedule(index) { it.copy(enabled = enabled) }

    fun setScheduleStart(index: Int, time: String) = editSchedule(index) { it.copy(start = time) }

    fun setScheduleEnd(index: Int, time: String) = editSchedule(index) { it.copy(end = time) }

    fun toggleScheduleDay(index: Int, day: DayOfWeek) = editSchedule(index) { it.copy(days = it.days.toggle(day)) }

    fun setDepartureEnabled(enabled: Boolean) = editDeparture { it.copy(enabled = enabled) }

    fun setDepartureTime(time: String) = editDeparture { it.copy(time = time) }

    fun toggleDepartureDay(day: DayOfWeek) = editDeparture { it.copy(days = it.days.toggle(day)) }

    /** Saves the form. On success [ScheduleUiState.saved] becomes true. */
    fun save() {
        val form = ui.value
        val settings = garage.state.value.chargeSettings
        if (form.saving || !form.valid || settings == null || form.departure == null) return
        ui.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            val updated = settings.copy(
                schedules = form.schedules.map {
                    ChargeSchedule(it.id, it.days.sorted(), it.start, it.end, it.enabled)
                },
                departure = Departure(
                    enabled = form.departure.enabled,
                    days = form.departure.days.sorted(),
                    time = form.departure.time,
                    climate = settings.departure?.climate,
                ),
            )
            when (val result = garage.saveChargeSettings(updated)) {
                is Outcome.Ok -> ui.update { it.copy(saving = false, saved = true) }
                is Outcome.Err -> {
                    ui.update { it.copy(saving = false, saveError = result.error) }
                    if (result.error is AppError.Unauthorized) expire(result.error)
                }
            }
        }
    }

    private fun fill(current: ScheduleUiState, settings: ChargeSettings): ScheduleUiState {
        val departure = settings.departure
        return current.copy(
            loading = false,
            loadError = null,
            schedules = settings.schedules.map { ScheduleDraft(it.id, it.days.toSet(), it.startTime, it.endTime, it.enabled) },
            departure = DepartureDraft(
                enabled = departure?.enabled ?: false,
                days = departure?.days?.toSet() ?: WEEKDAYS,
                time = departure?.time ?: DEFAULT_DEPARTURE,
            ),
        )
    }

    private fun editSchedule(index: Int, change: (ScheduleDraft) -> ScheduleDraft) = ui.update { s ->
        s.copy(
            schedules = s.schedules.mapIndexed { i, d -> if (i == index) change(d) else d },
            saveError = null,
        )
    }

    private fun editDeparture(change: (DepartureDraft) -> DepartureDraft) = ui.update { s ->
        s.copy(departure = s.departure?.let(change), saveError = null)
    }

    private fun Set<DayOfWeek>.toggle(day: DayOfWeek): Set<DayOfWeek> = if (day in this) this - day else this + day

    private fun expire(error: AppError) {
        viewModelScope.launch {
            remote.reset()
            garage.expireSession(error.message)
        }
    }

    private companion object {
        val WEEKDAYS = setOf(DayOfWeek.MON, DayOfWeek.TUE, DayOfWeek.WED, DayOfWeek.THU, DayOfWeek.FRI)
        const val DEFAULT_DEPARTURE = "07:30"
    }
}
