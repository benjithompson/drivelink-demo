package com.drivelink.demo.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.CommandType
import com.drivelink.demo.remote.RemoteCommands
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home tab. Shows the shared vehicle state ([GarageRepository]) and the running command
 * ([RemoteCommands]). A load error [AppError.Unauthorized] ends the session; the app returns to Login.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val garage: GarageRepository,
    private val remote: RemoteCommands,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(garage.state, remote.current) { g, c -> homeUiState(g, c) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch {
            garage.state.map { it.error }.collect { error ->
                if (error is AppError.Unauthorized) {
                    remote.reset()
                    garage.expireSession(error.message)
                }
            }
        }
        onShown()
    }

    /** Loads on first use, and again when the profile or scenario changed. Call on each resume. */
    fun onShown() {
        viewModelScope.launch { garage.ensureLoaded() }
    }

    /** Pull to refresh, and Retry. */
    fun refresh() {
        viewModelScope.launch { garage.refresh() }
    }

    fun selectVehicle(vin: String) {
        viewModelScope.launch { garage.selectVehicle(vin) }
    }

    /** The Lock tile: lock when unlocked, unlock when locked. Opens the PIN screen next. */
    fun requestLockToggle() {
        val locked = state.value.locked
        remote.request(if (locked) CommandType.UNLOCK else CommandType.LOCK)
    }

    fun retryCommand() = remote.retry()

    fun dismissCommand() = remote.dismiss()
}
