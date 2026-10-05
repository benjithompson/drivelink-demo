package com.drivelink.demo.maps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.DemoScenarios
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Maps tab. The vehicle and the user (units) come from the shared [GarageRepository]. This
 * ViewModel loads the location itself (getVehicleLocation) so that it can show its own loading
 * and error states. It loads again when the selected vehicle, the profile or the scenario changes.
 *
 * [AppError.Unauthorized] ends the session, like on Home; the app then opens Login.
 */
@HiltViewModel
class MapsViewModel @Inject constructor(
    private val garage: GarageRepository,
    private val vehicles: VehicleRepository,
    private val demoConfig: DemoConfig,
) : ViewModel() {

    private val load = MutableStateFlow(LocationLoad())
    private var loadedKey: String? = null
    private var job: Job? = null

    val state: StateFlow<MapsUiState> = combine(garage.state, load, demoConfig.scenario) { g, l, scenario ->
        mapsUiState(g, l, scenarioChipVisible = scenario != DemoScenarios.DEFAULT)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MapsUiState())

    init {
        // A vehicle switch on Home changes the selected VIN while this tab is hidden.
        viewModelScope.launch {
            garage.state.map { it.selected?.vin }.distinctUntilChanged().collect { loadIfNeeded() }
        }
        onShown()
    }

    /** Loads on first use, and again when the vehicle, the profile or the scenario changed. Call on each resume. */
    fun onShown() {
        viewModelScope.launch {
            garage.ensureLoaded()
            loadIfNeeded()
        }
    }

    /** The Refresh button, and Retry. Reloads the location; reloads the vehicle list when it is missing. */
    fun refresh() {
        viewModelScope.launch {
            if (garage.state.value.selected == null) {
                garage.refresh()
                loadIfNeeded()
            } else {
                loadLocation(force = true)
            }
        }
    }

    private fun loadIfNeeded() = loadLocation(force = false)

    private fun loadLocation(force: Boolean) {
        val vin = garage.state.value.selected?.vin ?: return
        val key = "$vin|${demoConfig.activeProfile.value.id}|${demoConfig.scenario.value}"
        if (!force && key == loadedKey) return
        loadedKey = key
        job?.cancel()
        job = viewModelScope.launch {
            // Keep the old location while the same vehicle reloads; drop it for another vehicle.
            load.update { old ->
                old.copy(vin = vin, location = if (old.vin == vin) old.location else null, loading = true, error = null)
            }
            when (val result = vehicles.getLocation(vin)) {
                is Outcome.Ok -> load.value = LocationLoad(vin, result.value, loading = false, error = null)
                is Outcome.Err -> {
                    load.update { it.copy(loading = false, error = result.error) }
                    if (result.error is AppError.Unauthorized) garage.expireSession(result.error.message)
                }
            }
        }
    }
}
