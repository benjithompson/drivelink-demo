package com.drivelink.demo.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.Units
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.ClimateParams
import com.drivelink.core.domain.model.ClimatePreset
import com.drivelink.core.domain.model.CommandType
import com.drivelink.core.domain.model.TempMode
import com.drivelink.core.domain.model.TemperatureUnit
import com.drivelink.core.domain.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A stop of the temperature stepper: OFF, LO, a value in the user's unit, or HI. */
sealed interface TempStep {
    val display: String

    data object Off : TempStep { override val display = "OFF" }
    data object Lo : TempStep { override val display = "LO" }
    data object Hi : TempStep { override val display = "HI" }
    data class Value(val degrees: Int) : TempStep { override val display get() = "$degrees°" }
}

data class ClimateUiState(
    val presets: List<ClimatePreset> = emptyList(),
    /** Index in [presets] of the preset that the form still matches, else null. */
    val selectedPreset: Int? = null,
    val steps: List<TempStep> = emptyList(),
    val stepIndex: Int = 0,
    val frontDefrost: Boolean = false,
    val rearDefrost: Boolean = false,
    val heatedWheel: Boolean = false,
    /** 0 = off, 3 = high. */
    val heatedSeats: Int = 0,
    /** 1..10. */
    val durationMin: Int = 10,
    val presetError: AppError? = null,
) {
    val temperature: String get() = steps.getOrNull(stepIndex)?.display.orEmpty()
}

/** Remote Start form. "Start Vehicle" sends START with [ClimateParams] after the PIN. */
@HiltViewModel
class ClimateViewModel @Inject constructor(
    private val vehicles: VehicleRepository,
    private val garage: GarageRepository,
    private val remote: RemoteCommands,
) : ViewModel() {

    private val celsius = garage.state.value.user?.units?.temperature == TemperatureUnit.C
    private val steps: List<TempStep> = buildList {
        add(TempStep.Off)
        add(TempStep.Lo)
        val range = if (celsius) Units.MIN_TEMP_C..Units.MAX_TEMP_C else Units.MIN_TEMP_F..Units.MAX_TEMP_F
        range.forEach { add(TempStep.Value(it)) }
        add(TempStep.Hi)
    }

    private val ui = MutableStateFlow(
        ClimateUiState(steps = steps, stepIndex = indexOfFahrenheit(DEFAULT_TEMP_F)),
    )

    val state: StateFlow<ClimateUiState> = ui.asStateFlow()

    init {
        loadPresets()
    }

    fun loadPresets() {
        val vin = garage.state.value.selected?.vin ?: return
        viewModelScope.launch {
            when (val result = vehicles.getClimatePresets(vin)) {
                is Outcome.Ok -> {
                    val presets = result.value.presets
                    ui.update { it.copy(presets = presets, presetError = null) }
                    if (presets.isNotEmpty()) selectPreset(0)
                }
                is Outcome.Err -> {
                    ui.update { it.copy(presetError = result.error) }
                    if (result.error is AppError.Unauthorized) garage.expireSession(result.error.message)
                }
            }
        }
    }

    fun selectPreset(index: Int) {
        val preset = ui.value.presets.getOrNull(index) ?: return
        val p = preset.params
        ui.update {
            it.copy(
                selectedPreset = index,
                stepIndex = when (p.tempMode) {
                    TempMode.OFF -> steps.indexOf(TempStep.Off)
                    TempMode.LO -> steps.indexOf(TempStep.Lo)
                    TempMode.HI -> steps.indexOf(TempStep.Hi)
                    TempMode.SET -> indexOfFahrenheit(p.tempF ?: DEFAULT_TEMP_F)
                },
                frontDefrost = p.frontDefrost ?: false,
                rearDefrost = p.rearDefrost ?: false,
                heatedWheel = p.heatedSteeringWheel ?: false,
                heatedSeats = (p.heatedSeats ?: 0).coerceIn(0, 3),
                durationMin = p.durationMin.coerceIn(1, 10),
            )
        }
    }

    fun lower() = edit { it.copy(stepIndex = (it.stepIndex - 1).coerceAtLeast(0)) }

    fun raise() = edit { it.copy(stepIndex = (it.stepIndex + 1).coerceAtMost(it.steps.lastIndex)) }

    fun setFrontDefrost(on: Boolean) = edit { it.copy(frontDefrost = on) }

    fun setRearDefrost(on: Boolean) = edit { it.copy(rearDefrost = on) }

    fun setHeatedWheel(on: Boolean) = edit { it.copy(heatedWheel = on) }

    fun setHeatedSeats(level: Int) = edit { it.copy(heatedSeats = level.coerceIn(0, 3)) }

    fun setDuration(minutes: Int) = edit { it.copy(durationMin = minutes.coerceIn(1, 10)) }

    /** The options for START, in API units (degrees Fahrenheit). */
    fun buildParams(): ClimateParams {
        val s = ui.value
        val step = s.steps[s.stepIndex]
        val preset = s.selectedPreset?.let { s.presets.getOrNull(it) }
        return ClimateParams(
            tempMode = when (step) {
                TempStep.Off -> TempMode.OFF
                TempStep.Lo -> TempMode.LO
                TempStep.Hi -> TempMode.HI
                is TempStep.Value -> TempMode.SET
            },
            tempF = (step as? TempStep.Value)?.let { if (celsius) Units.celsiusToFahrenheit(it.degrees) else it.degrees },
            frontDefrost = s.frontDefrost,
            rearDefrost = s.rearDefrost,
            heatedSteeringWheel = s.heatedWheel,
            heatedSeats = s.heatedSeats,
            durationMin = s.durationMin,
            presetId = preset?.id,
        )
    }

    /** "Start Vehicle": remembers START with the options. The screen opens the PIN prompt next. */
    fun requestStart() = remote.request(CommandType.START, buildParams())

    private fun edit(change: (ClimateUiState) -> ClimateUiState) = ui.update { change(it).copy(selectedPreset = null) }

    private fun indexOfFahrenheit(f: Int): Int {
        val degrees = if (celsius) Units.fahrenheitToCelsius(f).coerceIn(Units.MIN_TEMP_C, Units.MAX_TEMP_C) else f.coerceIn(Units.MIN_TEMP_F, Units.MAX_TEMP_F)
        return steps.indexOf(TempStep.Value(degrees)).coerceAtLeast(0)
    }

    private companion object {
        const val DEFAULT_TEMP_F = 72
    }
}
