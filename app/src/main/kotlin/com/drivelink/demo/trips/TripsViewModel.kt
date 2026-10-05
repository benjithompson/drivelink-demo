package com.drivelink.demo.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Units
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.model.Trip
import com.drivelink.core.domain.repository.VehicleRepository
import com.drivelink.demo.screenload.SelectedVehicleLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Number of trips the screen asks for (listTrips `limit`). */
const val TRIPS_LIMIT = 20

/**
 * Trips screen. Loads the last [TRIPS_LIMIT] trips of the selected vehicle and groups them by day.
 * [com.drivelink.core.domain.error.AppError.Unauthorized] ends the session; the app returns to Login.
 */
@HiltViewModel
class TripsViewModel @Inject constructor(
    garage: GarageRepository,
    vehicles: VehicleRepository,
    config: DemoConfig,
) : ViewModel() {

    private val loader = SelectedVehicleLoader<List<Trip>>(viewModelScope, garage, config) {
        vehicles.listTrips(it, TRIPS_LIMIT)
    }

    val state: StateFlow<TripsUiState> = combine(
        loader.state,
        garage.state.map { it.user?.units ?: Units.DEFAULT },
    ) { load, units ->
        val mapped = load.data?.let { tripsContent(it, units) }
        TripsUiState(
            summary = mapped?.first,
            days = mapped?.second.orEmpty(),
            empty = load.data != null && load.data.isEmpty(),
            error = load.error,
            loading = load.loading && load.data == null,
            refreshing = load.loading && load.data != null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TripsUiState())

    /** Loads on first use, and again when the vehicle, profile or scenario changed. Call on each resume. */
    fun onShown() = loader.onShown()

    /** Pull to refresh, and Retry. */
    fun refresh() = loader.refresh()
}
