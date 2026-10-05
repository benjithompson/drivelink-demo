package com.drivelink.demo.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.CommandType
import com.drivelink.core.domain.model.Powertrain
import com.drivelink.demo.remote.RemoteCommands
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Charging screen. Shows the status of the selected EV, the charge settings and the running charge
 * command. A gas car sends no charging request (the API answers 404).
 *
 * A move of a limit slider updates the screen at once. The app saves the limits [SAVE_DELAY_MS] after
 * the last move (PUT). When the save fails, the sliders go back to the saved values (D-32).
 */
@HiltViewModel
class ChargingViewModel @Inject constructor(
    private val garage: GarageRepository,
    private val remote: RemoteCommands,
) : ViewModel() {

    private val local = MutableStateFlow(ChargingLocal())
    private val saveLock = Mutex()
    private var saveJob: Job? = null

    val state: StateFlow<ChargingUiState> = combine(garage.state, local, remote.current) { g, l, c ->
        chargingUiState(g, l, c)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChargingUiState())

    init {
        viewModelScope.launch {
            garage.state.map { it.error }.collect { error ->
                if (error is AppError.Unauthorized) expire(error)
            }
        }
        // Load the settings once the vehicle is known, and again after a vehicle switch.
        viewModelScope.launch {
            garage.state.map { it.selected?.vin to it.selected?.powertrain }.distinctUntilChanged().collect { (vin, power) ->
                local.update { ChargingLocal() }
                if (vin != null && power != Powertrain.ICE) loadSettings(force = false)
            }
        }
        viewModelScope.launch { garage.ensureLoaded() }
    }

    /** Pull to refresh: reloads the status and the settings. */
    fun refresh() {
        viewModelScope.launch {
            garage.refresh()
            if (state.value.available && garage.state.value.selected != null) loadSettings(force = true)
        }
    }

    /** Retry for the limits section. */
    fun retrySettings() {
        viewModelScope.launch { loadSettings(force = true) }
    }

    /** Start charge when not charging, stop charge when charging. The screen opens the PIN prompt next. */
    fun requestToggle() {
        remote.request(if (state.value.active) CommandType.CHARGE_STOP else CommandType.CHARGE_START)
    }

    fun setAcLimit(percent: Int) = setLimits { it.copy(ac = snap(percent)) }

    fun setDcLimit(percent: Int) = setLimits { it.copy(dc = snap(percent)) }

    private fun setLimits(change: (Limits) -> Limits) {
        val current = state.value.limits ?: return
        val next = change(current)
        if (next == current) return
        local.update { it.copy(draft = next, saveStatus = SaveStatus.Idle, saveError = null) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            // A save that has started runs to its end. Only the wait can be cancelled.
            withContext(NonCancellable) { saveLock.withLock { saveDraft() } }
        }
    }

    private suspend fun saveDraft() {
        val sent = local.value.draft ?: return
        val settings = garage.state.value.chargeSettings ?: return
        if (sent.ac == settings.acTargetPct && sent.dc == settings.dcTargetPct) {
            local.update { it.copy(draft = null) }
            return
        }
        local.update { it.copy(saveStatus = SaveStatus.Saving, saveError = null) }
        when (val result = garage.saveChargeSettings(settings.copy(acTargetPct = sent.ac, dcTargetPct = sent.dc))) {
            is Outcome.Ok -> local.update {
                it.copy(draft = if (it.draft == sent) null else it.draft, saveStatus = SaveStatus.Saved)
            }
            is Outcome.Err -> {
                // Back to the saved values, unless the user already moved a slider again.
                local.update {
                    it.copy(draft = if (it.draft == sent) null else it.draft, saveStatus = SaveStatus.Error, saveError = result.error)
                }
                if (result.error is AppError.Unauthorized) expire(result.error)
            }
        }
    }

    private suspend fun loadSettings(force: Boolean) {
        local.update { it.copy(settingsLoading = true, settingsError = null) }
        when (val result = garage.loadChargeSettings(force)) {
            is Outcome.Ok -> local.update { it.copy(settingsLoading = false) }
            is Outcome.Err -> {
                local.update { it.copy(settingsLoading = false, settingsError = result.error) }
                if (result.error is AppError.Unauthorized) expire(result.error)
            }
        }
    }

    private suspend fun expire(error: AppError) {
        remote.reset()
        garage.expireSession(error.message)
    }

    private fun snap(percent: Int): Int = (Math.round(percent / 10.0) * 10).toInt().coerceIn(MIN_LIMIT, MAX_LIMIT)

    companion object {
        /** How long after the last slider move the app saves. */
        const val SAVE_DELAY_MS = 500L
        const val MIN_LIMIT = 50
        const val MAX_LIMIT = 100
    }
}
