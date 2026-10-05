package com.drivelink.demo.carcare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Units
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.model.Maintenance
import com.drivelink.core.domain.repository.VehicleRepository
import com.drivelink.demo.screenload.SelectedVehicleLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Car Care tab. Loads the maintenance document of the selected vehicle (one data set for each demo car).
 * [com.drivelink.core.domain.error.AppError.Unauthorized] ends the session; the app returns to Login.
 */
@HiltViewModel
class CarCareViewModel @Inject constructor(
    garage: GarageRepository,
    vehicles: VehicleRepository,
    config: DemoConfig,
) : ViewModel() {

    private val loader = SelectedVehicleLoader<Maintenance>(viewModelScope, garage, config) { vehicles.getMaintenance(it) }

    val state: StateFlow<CarCareUiState> = combine(
        loader.state,
        garage.state.map { it.user?.units ?: Units.DEFAULT },
    ) { load, units ->
        CarCareUiState(
            content = load.data?.let { carCareContent(it, units) },
            error = load.error,
            loading = load.loading && load.data == null,
            refreshing = load.loading && load.data != null,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CarCareUiState())

    /** Loads on first use, and again when the vehicle, profile or scenario changed. Call on each resume. */
    fun onShown() = loader.onShown()

    /** Pull to refresh, and Retry. */
    fun refresh() = loader.refresh()
}
