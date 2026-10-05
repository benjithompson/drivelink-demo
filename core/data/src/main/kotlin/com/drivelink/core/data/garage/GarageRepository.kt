package com.drivelink.core.data.garage

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.ChargeSettings
import com.drivelink.core.domain.model.ChargeState
import com.drivelink.core.domain.model.CommandType
import com.drivelink.core.domain.model.Connectivity
import com.drivelink.core.domain.model.Powertrain
import com.drivelink.core.domain.model.Trip
import com.drivelink.core.domain.model.User
import com.drivelink.core.domain.model.Vehicle
import com.drivelink.core.domain.model.VehicleStatus
import com.drivelink.core.domain.repository.AccountRepository
import com.drivelink.core.domain.repository.AlertsRepository
import com.drivelink.core.domain.repository.AuthRepository
import com.drivelink.core.domain.repository.VehicleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** What the app knows about the signed-in user's vehicles. One instance for all screens. */
data class GarageState(
    val vehicles: List<Vehicle> = emptyList(),
    /** The selected vehicle (D-09: the switcher shows two). Null until the list loads. */
    val selected: Vehicle? = null,
    /** Status of [selected], with the commanded states applied (see [GarageRepository.commandSucceeded]). Null while it loads, and after a load error on the first load. */
    val status: VehicleStatus? = null,
    val user: User? = null,
    /** Unread alerts for all vehicles. */
    val unreadAlerts: Int = 0,
    /** Short place name or address, for the Location row. */
    val locationLabel: String? = null,
    val lastTrip: Trip? = null,
    /** A full load or a refresh is in flight. */
    val loading: Boolean = false,
    /**
     * Charge settings of [selected], once a screen loaded or saved them (EV only). Kept here so the
     * Charging and Charging Schedule screens show the same values. Null for a gas car.
     */
    val chargeSettings: ChargeSettings? = null,
    /** The last load failed. [status] can still hold older data. */
    val error: AppError? = null,
    /** A load has finished once (with data or with an error). */
    val loaded: Boolean = false,
) {
    /** The telematics link is down. [status] is the last known state. */
    val stale: Boolean get() = status?.connectivity == Connectivity.OFFLINE
}

/**
 * Shared vehicle state (source plan, step 5). Loads the vehicles, keeps the selected VIN in
 * [DemoConfig], loads the status, the alert count, the location and the last trip, and refreshes
 * the status after a command succeeds.
 *
 * After a command succeeds, [commandSucceeded] keeps the commanded lock, climate or charging state
 * on top of the fetched status until a later fetched status shows a different value than at command
 * time (D-30). The mock returns one fixed status body; without this the Lock tile would not change.
 *
 * Every load takes a token (generation and source key). A load that finishes after sign-out, a
 * vehicle switch, or a profile or scenario change finds an old token and drops its result (D-31).
 *
 * A load that finds [AppError.Unauthorized] does not sign out here: the caller decides
 * ([expireSession]).
 */
@Singleton
class GarageRepository @Inject constructor(
    private val vehicles: VehicleRepository,
    private val alerts: AlertsRepository,
    private val account: AccountRepository,
    private val auth: AuthRepository,
    private val config: DemoConfig,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(GarageState())
    private var loadedKey: String? = null

    /** Raised by every reset of the data. A running load with an older value drops its result. */
    @Volatile private var generation = 0

    private val overrides = Overrides()

    private val _signInNotice = MutableStateFlow<String?>(null)

    val state: StateFlow<GarageState> = _state.asStateFlow()

    /** A message for the login screen after the session ended. Null after [consumeNotice]. */
    val signInNotice: StateFlow<String?> = _signInNotice.asStateFlow()

    /** True after [signOut] until the next sign-in: the session ended on purpose, so no notice is due. */
    @Volatile private var signedOutByUser = false

    fun consumeNotice() {
        _signInNotice.value = null
        signedOutByUser = false
    }

    /**
     * The app saw the session end. When [signOut] did not end it, the auth interceptor did: the
     * token refresh failed. The screens that would call [expireSession] can be gone by now (the
     * auth gate pops them), so this sets the notice for the login screen and drops the data.
     */
    fun noteSessionEnded(message: String = SESSION_EXPIRED) {
        if (signedOutByUser) return
        reset()
        if (_signInNotice.value == null) _signInNotice.value = message
    }

    /** Loads when nothing loaded yet, or when the endpoint profile or scenario changed since the last load. */
    suspend fun ensureLoaded() = mutex.withLock {
        if (loadedKey != currentKey()) refreshLocked()
    }

    /** Full load: vehicles, user, then the data of the selected vehicle. */
    suspend fun refresh() = mutex.withLock { refreshLocked() }

    private suspend fun refreshLocked() {
        val key = currentKey()
        // Data from another profile or scenario is not stale data of this one: drop it.
        val sourceChanged = loadedKey != null && loadedKey != key
        if (sourceChanged) discardData()
        loadedKey = key
        val token = Token(generation, key)
        _state.update {
            if (sourceChanged) GarageState(vehicles = it.vehicles, selected = it.selected, user = it.user, loading = true)
            else it.copy(loading = true)
        }
        val (list, user) = coroutineScope {
            val l = async { vehicles.listVehicles() }
            val u = async { account.getMe() }
            l.await() to u.await()
        }
        if (!token.isCurrent()) return
        val vehicleList = when (list) {
            is Outcome.Ok -> list.value
            is Outcome.Err -> return fail(token, list.error)
        }
        if (user is Outcome.Err && user.error is AppError.Unauthorized) return fail(token, user.error)
        val saved = config.vin.value
        val selected = vehicleList.firstOrNull { it.vin == saved } ?: vehicleList.firstOrNull()
            ?: return fail(token, AppError.NotFound(null, "No vehicle on this account."))
        if (selected.vin != saved) config.setVin(selected.vin)
        val changed = _state.value.selected?.vin != selected.vin
        if (changed) overrides.clear()
        _state.update {
            it.copy(
                vehicles = vehicleList,
                selected = selected,
                user = (user as? Outcome.Ok)?.value ?: it.user,
                status = if (changed) null else it.status,
                chargeSettings = if (changed) null else it.chargeSettings,
            )
        }
        loadSelected(token, selected)
    }

    /** Switches the vehicle and loads its data. */
    suspend fun selectVehicle(vin: String) = mutex.withLock {
        val vehicle = _state.value.vehicles.firstOrNull { it.vin == vin } ?: return@withLock
        if (vehicle.vin == _state.value.selected?.vin) return@withLock
        config.setVin(vin)
        discardData()
        _state.update {
            it.copy(
                selected = vehicle, status = null, chargeSettings = null,
                locationLabel = null, lastTrip = null, error = null, loading = true,
            )
        }
        loadSelected(newToken(), vehicle)
    }

    /** Reloads the status of the selected vehicle only. No loading state. */
    suspend fun refreshStatus() {
        val vin = _state.value.selected?.vin ?: return
        val token = newToken()
        when (val result = vehicles.getStatus(vin)) {
            is Outcome.Ok -> {
                if (!token.isCurrent()) return
                val effective = overrides.apply(result.value)
                _state.update { current ->
                    if (current.selected?.vin == vin) current.copy(status = effective, error = null) else current
                }
            }
            is Outcome.Err -> Unit
        }
    }

    /**
     * Call after the command [type] for [vin] succeeded. Keeps the commanded state of the lock
     * (LOCK, UNLOCK), the climate (START, STOP) or the charging (CHARGE_START, CHARGE_STOP) on top of
     * the fetched status (D-30), then reloads the status. Other command types only reload.
     */
    suspend fun commandSucceeded(vin: String, type: CommandType) {
        if (vin == _state.value.selected?.vin && overrides.record(type)) {
            val raw = overrides.raw
            if (raw != null) _state.update { it.copy(status = overrides.apply(raw)) }
        }
        refreshStatus()
    }

    /**
     * Charge settings of the selected vehicle. Returns the kept copy unless [force] is true or none
     * is kept. An ICE vehicle gets [AppError.NotFound] without a request (the API answers 404).
     */
    suspend fun loadChargeSettings(force: Boolean = false): Outcome<ChargeSettings> {
        val s = _state.value
        val vehicle = s.selected ?: return Outcome.Err(AppError.NotFound(null, "No vehicle selected."))
        if (vehicle.powertrain == Powertrain.ICE) {
            return Outcome.Err(AppError.NotFound(null, "This vehicle does not support charging."))
        }
        if (!force) s.chargeSettings?.let { return Outcome.Ok(it) }
        val token = newToken()
        val result = vehicles.getChargeSettings(vehicle.vin)
        if (result is Outcome.Ok && token.isCurrent()) {
            _state.update { if (it.selected?.vin == vehicle.vin) it.copy(chargeSettings = result.value) else it }
        }
        return result
    }

    /**
     * Saves [settings] with PUT. On success the app keeps the values that it sent, not the answer:
     * the mock returns a fixed body (D-32).
     */
    suspend fun saveChargeSettings(settings: ChargeSettings): Outcome<ChargeSettings> {
        val vin = _state.value.selected?.vin ?: return Outcome.Err(AppError.NotFound(null, "No vehicle selected."))
        val token = newToken()
        return when (val result = vehicles.updateChargeSettings(vin, settings)) {
            is Outcome.Ok -> {
                if (token.isCurrent()) {
                    _state.update { if (it.selected?.vin == vin) it.copy(chargeSettings = settings) else it }
                }
                Outcome.Ok(settings)
            }
            is Outcome.Err -> result
        }
    }

    /** Sets the unread alert count for the Home bell, after the Alerts screen loads the list or marks an alert read. */
    fun setUnreadAlerts(count: Int) {
        _state.update { it.copy(unreadAlerts = count) }
    }

    /** Clears the stored session and all vehicle data. */
    suspend fun signOut() {
        signedOutByUser = true
        auth.logout()
        reset()
    }

    /** Like [signOut], with a message for the login screen. */
    suspend fun expireSession(message: String) {
        signOut()
        _signInNotice.value = message
    }

    private fun reset() {
        loadedKey = null
        discardData()
        _state.value = GarageState()
    }

    /** Invalidates the running loads and drops the commanded states. */
    private fun discardData() {
        generation++
        overrides.clear()
    }

    private fun currentKey(): String = "${config.activeProfile.value.id}|${config.scenario.value}"

    private fun newToken() = Token(generation, currentKey())

    /** What a load saw when it started. It is current while no reset and no source change happened. */
    private inner class Token(val generation: Int, val key: String) {
        fun isCurrent(): Boolean = generation == this@GarageRepository.generation && key == currentKey()
    }

    private suspend fun loadSelected(token: Token, vehicle: Vehicle) {
        val vin = vehicle.vin
        val (status, count, location, trips) = coroutineScope {
            val s = async { vehicles.getStatus(vin) }
            val a = async { alerts.list(null) }
            val l = async { vehicles.getLocation(vin) }
            val t = async { vehicles.listTrips(vin, 1) }
            Four(s.await(), a.await(), l.await(), t.await())
        }
        if (!token.isCurrent()) return
        if (status is Outcome.Err) return fail(token, status.error)
        val unauthorized = listOf(count, location, trips).filterIsInstance<Outcome.Err>()
            .map { it.error }.firstOrNull { it is AppError.Unauthorized }
        if (unauthorized != null) return fail(token, unauthorized)
        val effective = overrides.apply((status as Outcome.Ok).value)
        _state.update {
            if (it.selected?.vin != vin) return@update it
            it.copy(
                status = effective,
                unreadAlerts = (count as? Outcome.Ok)?.value?.count { a -> !a.read } ?: it.unreadAlerts,
                locationLabel = (location as? Outcome.Ok)?.value?.let { l -> l.locality ?: l.address } ?: it.locationLabel,
                lastTrip = (trips as? Outcome.Ok)?.value?.firstOrNull() ?: it.lastTrip,
                loading = false,
                error = null,
                loaded = true,
            )
        }
    }

    private fun fail(token: Token, error: AppError) {
        if (!token.isCurrent()) return
        _state.update { it.copy(loading = false, error = error, loaded = true) }
    }

    private data class Four<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}

/**
 * The commanded lock, climate and charging states (D-30). Each holds the commanded value and the
 * fetched value at command time. A fetched status with another value than at command time ends the
 * override: the vehicle state changed, so the fetched value wins.
 */
private class Overrides {
    private class Entry(val value: Boolean, val baseline: Boolean)

    private var lock: Entry? = null
    private var climate: Entry? = null
    private var charge: Entry? = null

    /** The last fetched status, without the commanded states. */
    @get:Synchronized var raw: VehicleStatus? = null
        private set

    /** Remembers the commanded state for [type]. Returns false when the type has no override or no status is known. */
    @Synchronized
    fun record(type: CommandType): Boolean {
        val r = raw ?: return false
        when (type) {
            CommandType.LOCK, CommandType.UNLOCK -> lock = Entry(type == CommandType.LOCK, r.locked)
            CommandType.START, CommandType.STOP -> climate = Entry(type == CommandType.START, r.climate.on)
            CommandType.CHARGE_START, CommandType.CHARGE_STOP -> {
                val charging = r.charging ?: return false
                charge = Entry(type == CommandType.CHARGE_START, charging.active)
            }
            CommandType.HORN_LIGHTS, CommandType.LIGHTS -> return false
        }
        return true
    }

    /** Stores [fetched] as the latest raw status, ends the overrides that it contradicts, and returns the status to show. */
    @Synchronized
    fun apply(fetched: VehicleStatus): VehicleStatus {
        raw = fetched
        if (lock?.baseline != fetched.locked) lock = null
        if (climate?.baseline != fetched.climate.on) climate = null
        if (charge?.baseline != fetched.charging?.active) charge = null
        var s = fetched
        lock?.let { s = s.copy(locked = it.value) }
        climate?.let { s = s.copy(engineOn = it.value, climate = s.climate.copy(on = it.value)) }
        charge?.let { c ->
            val charging = s.charging ?: return@let
            s = s.copy(
                charging = if (c.value) {
                    charging.copy(active = true, state = ChargeState.CHARGING)
                } else {
                    // Stopped while plugged in: the charge waits for the schedule.
                    charging.copy(active = false, state = if (charging.pluggedIn) ChargeState.SCHEDULED else ChargeState.NOT_PLUGGED)
                },
            )
        }
        return s
    }

    @Synchronized
    fun clear() {
        lock = null
        climate = null
        charge = null
        raw = null
    }
}

private const val SESSION_EXPIRED = "Your session has expired. Sign in again."
