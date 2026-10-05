package com.drivelink.demo.screenload

import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.error.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a [SelectedVehicleLoader] holds: the last good [data], the last [error] and the load flag. */
data class LoadState<T>(
    val data: T? = null,
    val error: AppError? = null,
    /** A fetch runs now, or the shared vehicle state still loads. */
    val loading: Boolean = true,
)

/**
 * Loads one document for the selected vehicle (Car Care, Trips, Schedule Service).
 *
 * - Waits for the shared vehicle state ([GarageRepository]) and loads again when the vehicle,
 *   the endpoint profile or the demo scenario changes.
 * - A failed fetch keeps the old [LoadState.data] and sets [LoadState.error].
 * - [AppError.Unauthorized] ends the session, the same way Home does.
 *
 * All calls run on the scope of the ViewModel (the main dispatcher).
 */
class SelectedVehicleLoader<T>(
    private val scope: CoroutineScope,
    private val garage: GarageRepository,
    private val config: DemoConfig,
    private val fetch: suspend (vin: String) -> Outcome<T>,
) {
    private val _state = MutableStateFlow(LoadState<T>())
    val state: StateFlow<LoadState<T>> = _state.asStateFlow()

    /** Key (profile, scenario, VIN) of the data in [state]; null when there is none. */
    private var dataKey: String? = null
    private var fetchingKey: String? = null

    init {
        // A vehicle switch on Home, or the first load of the shared state, starts a fetch.
        scope.launch {
            garage.state.map { it.selected?.vin to it.error }.distinctUntilChanged().collect { load(force = false) }
        }
        // A new demo scenario or endpoint profile reloads the shared state, then this document.
        scope.launch {
            combine(config.scenario, config.activeProfile) { scenario, profile -> scenario to profile.id }
                .distinctUntilChanged()
                .collect {
                    garage.ensureLoaded()
                    load(force = false)
                }
        }
    }

    /** Call on each resume: loads the shared state, then fetches when the source changed. */
    fun onShown() {
        scope.launch {
            garage.ensureLoaded()
            load(force = false)
        }
    }

    /** Pull to refresh, and Retry. Reloads the shared state first when it has no vehicle. */
    fun refresh() {
        scope.launch {
            if (garage.state.value.selected == null) garage.refresh()
            load(force = true)
        }
    }

    private suspend fun load(force: Boolean) {
        val shared = garage.state.value
        val vin = shared.selected?.vin
        if (vin == null) {
            // No vehicle: the vehicle list failed (or has not loaded yet).
            val error = shared.error
            if (error != null) fail(error) else _state.update { it.copy(loading = true) }
            return
        }
        val key = "${config.activeProfile.value.id}|${config.scenario.value}|$vin"
        if (!force && (key == dataKey || key == fetchingKey)) return
        fetchingKey = key
        _state.update {
            // Data of another vehicle, profile or scenario is not stale data of this one: drop it.
            if (dataKey != null && dataKey != key) LoadState(loading = true) else it.copy(loading = true)
        }
        val result = fetch(vin)
        if (fetchingKey != key) return
        fetchingKey = null
        when (result) {
            is Outcome.Ok -> {
                dataKey = key
                _state.value = LoadState(data = result.value, error = null, loading = false)
            }
            is Outcome.Err -> fail(result.error)
        }
    }

    private suspend fun fail(error: AppError) {
        _state.update { it.copy(error = error, loading = false) }
        if (error is AppError.Unauthorized) garage.expireSession(error.message)
    }
}
