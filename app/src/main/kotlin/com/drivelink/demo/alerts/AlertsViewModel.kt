package com.drivelink.demo.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.TimeAgo
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.core.domain.model.Alert
import com.drivelink.core.domain.model.AlertSeverity
import com.drivelink.core.domain.model.Vehicle
import com.drivelink.core.domain.repository.AlertsRepository
import com.drivelink.demo.screenload.SelectedVehicleLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** One alert row. */
data class AlertRowUi(
    val id: String,
    val title: String,
    val body: String,
    /** For example "3 hr ago"; empty when the time cannot be read. */
    val timeAgo: String,
    /** The vehicle name, for example "2026 Aurora EV"; null when the alert has no known vehicle. */
    val vehicle: String?,
    val severity: AlertSeverity,
    val unread: Boolean,
)

/** A failed mark-as-read: the message and the correlation id of the request. */
data class MarkError(val message: String, val correlationId: String?)

data class AlertsUiState(
    val rows: List<AlertRowUi> = emptyList(),
    val unreadCount: Int = 0,
    /** The list loaded and holds no alert. */
    val empty: Boolean = false,
    /** The last load failed. With data on screen, the screen keeps the data and shows a banner. */
    val error: AppError? = null,
    /** First load, no data yet. */
    val loading: Boolean = true,
    /** A reload runs while data is on screen. */
    val refreshing: Boolean = false,
    /** The last mark-as-read failed; the alert is unread again. */
    val markError: MarkError? = null,
) {
    val hasData: Boolean get() = rows.isNotEmpty() || empty
}

/** Pure mapping from the alerts of the API to rows, newest first. Tested without Android. */
fun alertRows(alerts: List<Alert>, vehicles: List<Vehicle>, now: Instant = Instant.now()): List<AlertRowUi> =
    alerts.sortedByDescending { it.createdAt }.map { alert ->
        val vehicle = vehicles.firstOrNull { it.vin == alert.vin }
        AlertRowUi(
            id = alert.id,
            title = alert.title,
            body = alert.body,
            timeAgo = TimeAgo.describe(alert.createdAt, now).orEmpty(),
            vehicle = vehicle?.let { "${it.year} ${it.model}" },
            severity = alert.severity,
            unread = !alert.read,
        )
    }

/**
 * Alerts screen. Shows the alerts of all vehicles of the account (the same list the Home bell counts).
 * A tap on an unread alert marks it read at once and reverts when the request fails.
 * The Home bell follows through [GarageRepository.setUnreadAlerts].
 * [AppError.Unauthorized] ends the session; the app returns to Login.
 */
@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val garage: GarageRepository,
    private val alerts: AlertsRepository,
    config: DemoConfig,
) : ViewModel() {

    // The list does not depend on the vehicle; the loader supplies the load, retry and session handling.
    private val loader = SelectedVehicleLoader<List<Alert>>(viewModelScope, garage, config) { alerts.list(null) }

    /** The list with the optimistic changes. Replaced by every successful load. */
    private val local = MutableStateFlow<List<Alert>?>(null)
    private val markError = MutableStateFlow<MarkError?>(null)
    private val marking = mutableSetOf<String>()
    private var lastLoaded: List<Alert>? = null

    val state: StateFlow<AlertsUiState> = combine(loader.state, local, garage.state, markError) { load, list, shared, mark ->
        val rows = list?.let { alertRows(it, shared.vehicles) }.orEmpty()
        AlertsUiState(
            rows = rows,
            unreadCount = rows.count { it.unread },
            empty = list != null && list.isEmpty(),
            error = load.error,
            loading = load.loading && list == null,
            refreshing = load.loading && list != null,
            markError = mark,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AlertsUiState())

    init {
        viewModelScope.launch {
            loader.state.collect { load ->
                val data = load.data
                if (data == null) {
                    if (lastLoaded != null) {
                        // The loader dropped the list (another vehicle, profile or scenario): drop ours too.
                        lastLoaded = null
                        local.value = null
                    }
                } else if (data !== lastLoaded) {
                    lastLoaded = data
                    local.value = data
                    markError.value = null
                    garage.setUnreadAlerts(data.count { !it.read })
                }
            }
        }
    }

    /** Loads on first use, and again when the profile or scenario changed. Call on each resume. */
    fun onShown() = loader.onShown()

    /** Pull to refresh, and Retry. */
    fun refresh() = loader.refresh()

    fun dismissMarkError() {
        markError.value = null
    }

    /** A tap on an alert. Marks an unread alert read; a read alert stays as it is. */
    fun markRead(id: String) {
        viewModelScope.launch { mark(id) }
    }

    /** Marks all unread alerts read, one request after the other. */
    fun markAllRead() {
        viewModelScope.launch {
            local.value.orEmpty().filter { !it.read }.forEach { mark(it.id) }
        }
    }

    private suspend fun mark(id: String) {
        val alert = local.value?.firstOrNull { it.id == id } ?: return
        if (alert.read || !marking.add(id)) return
        markError.value = null
        setRead(id, true)
        when (val result = alerts.markRead(id)) {
            is Outcome.Ok -> Unit
            is Outcome.Err -> {
                setRead(id, false)
                markError.value = MarkError(result.error.displayMessage(), result.error.correlationId)
                if (result.error is AppError.Unauthorized) garage.expireSession(result.error.message)
            }
        }
        marking.remove(id)
    }

    /** Changes one alert in the local list and updates the Home bell. */
    private fun setRead(id: String, read: Boolean) {
        local.update { list -> list?.map { if (it.id == id) it.copy(read = read) else it } }
        garage.setUnreadAlerts(local.value.orEmpty().count { !it.read })
    }
}
