package com.drivelink.demo.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.TemperatureUnit
import com.drivelink.core.domain.model.User
import com.drivelink.core.domain.model.UserUnits
import com.drivelink.core.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The selected vehicle on the Profile screen. */
data class VehicleSummary(val title: String, val trim: String, val vin: String, val nickname: String)

data class ProfileUiState(
    val user: User? = null,
    val vehicle: VehicleSummary? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
)

/** "Miles, °F" or "Kilometers, °C": the units of the account, as text. */
fun UserUnits.describe(): String {
    val distance = if (distance == DistanceUnit.KM) "Kilometers (km)" else "Miles (mi)"
    val temperature = if (temperature == TemperatureUnit.C) "Celsius (°C)" else "Fahrenheit (°F)"
    return "$distance, $temperature"
}

/**
 * Profile sub-screen. Calls getMe itself, so that it shows its own loading and error states.
 * The vehicle summary comes from the shared [GarageRepository]. [AppError.Unauthorized] ends the
 * session, like on Home.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val account: AccountRepository,
    private val garage: GarageRepository,
) : ViewModel() {

    private data class Load(val user: User? = null, val loading: Boolean = true, val error: AppError? = null)

    private val load = MutableStateFlow(Load())

    val state: StateFlow<ProfileUiState> = combine(garage.state, load) { g, l ->
        ProfileUiState(
            user = l.user,
            vehicle = g.selected?.let { VehicleSummary("${it.year} ${it.model}", it.trim.orEmpty(), it.vin, it.nickname) },
            loading = l.loading,
            error = l.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState())

    init {
        refresh()
        viewModelScope.launch { garage.ensureLoaded() }
    }

    /** Loads the account again. Also used by Retry. */
    fun refresh() {
        viewModelScope.launch {
            load.update { it.copy(loading = true, error = null) }
            when (val result = account.getMe()) {
                is Outcome.Ok -> load.value = Load(result.value, loading = false)
                is Outcome.Err -> {
                    load.update { it.copy(loading = false, error = result.error) }
                    if (result.error is AppError.Unauthorized) garage.expireSession(result.error.message)
                }
            }
        }
    }
}
