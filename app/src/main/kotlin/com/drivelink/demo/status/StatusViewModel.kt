package com.drivelink.demo.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.error.AppError
import com.drivelink.demo.remote.RemoteCommands
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Vehicle Status. Shows the shared vehicle state ([GarageRepository]). Pull to refresh and Retry
 * reload it. A load error [AppError.Unauthorized] ends the session, as on Home.
 */
@HiltViewModel
class StatusViewModel @Inject constructor(
    private val garage: GarageRepository,
    private val remote: RemoteCommands,
) : ViewModel() {

    val state: StateFlow<StatusUiState> = garage.state.map { statusUiState(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StatusUiState())

    init {
        viewModelScope.launch {
            garage.state.map { it.error }.collect { error ->
                if (error is AppError.Unauthorized) {
                    remote.reset()
                    garage.expireSession(error.message)
                }
            }
        }
        viewModelScope.launch { garage.ensureLoaded() }
    }

    /** Pull to refresh, and Retry. */
    fun refresh() {
        viewModelScope.launch { garage.refresh() }
    }
}
