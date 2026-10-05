package com.drivelink.core.domain.repository

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.model.Alert
import com.drivelink.core.domain.model.ChargeSettings
import com.drivelink.core.domain.model.ClimatePresets
import com.drivelink.core.domain.model.Command
import com.drivelink.core.domain.model.CommandRequest
import com.drivelink.core.domain.model.Health
import com.drivelink.core.domain.model.Location
import com.drivelink.core.domain.model.Maintenance
import com.drivelink.core.domain.model.ServiceRequest
import com.drivelink.core.domain.model.ServiceRequestCreate
import com.drivelink.core.domain.model.TokenResponse
import com.drivelink.core.domain.model.Trip
import com.drivelink.core.domain.model.User
import com.drivelink.core.domain.model.Vehicle
import com.drivelink.core.domain.model.VehicleStatus

// Every function returns Outcome and never throws for HTTP, transport or parse errors.
// Each KDoc names the operationId in api/openapi.yaml.

/** Sign-in and sign-out. Token refresh is not here: the network layer does it on 401 TOKEN_EXPIRED. */
interface AuthRepository {
    /** createToken: POST /auth/token. On success, the implementation also saves the session. */
    suspend fun login(email: String, password: String): Outcome<TokenResponse>

    /** Local only: clears the stored session. No API call. */
    suspend fun logout(): Outcome<Unit>
}

interface AccountRepository {
    /** getHealth: GET /health. No auth. Used by "Test connection". */
    suspend fun getHealth(): Outcome<Health>

    /** getMe: GET /me. */
    suspend fun getMe(): Outcome<User>
}

interface VehicleRepository {
    /** listVehicles: GET /vehicles. */
    suspend fun listVehicles(): Outcome<List<Vehicle>>

    /** getVehicleStatus: GET /vehicles/{vin}/status. */
    suspend fun getStatus(vin: String): Outcome<VehicleStatus>

    /** getVehicleLocation: GET /vehicles/{vin}/location. */
    suspend fun getLocation(vin: String): Outcome<Location>

    /** getChargeSettings: GET /vehicles/{vin}/charge-settings. 404 for an ICE vehicle. */
    suspend fun getChargeSettings(vin: String): Outcome<ChargeSettings>

    /** updateChargeSettings: PUT /vehicles/{vin}/charge-settings. Returns the stored settings. */
    suspend fun updateChargeSettings(vin: String, settings: ChargeSettings): Outcome<ChargeSettings>

    /** getClimatePresets: GET /vehicles/{vin}/climate-presets. */
    suspend fun getClimatePresets(vin: String): Outcome<ClimatePresets>

    /** updateClimatePresets: PUT /vehicles/{vin}/climate-presets. Returns the stored presets. */
    suspend fun updateClimatePresets(vin: String, presets: ClimatePresets): Outcome<ClimatePresets>

    /** listTrips: GET /vehicles/{vin}/trips?limit=[limit]. [limit] is 1..50. */
    suspend fun listTrips(vin: String, limit: Int = 20): Outcome<List<Trip>>

    /** getMaintenance: GET /vehicles/{vin}/maintenance. */
    suspend fun getMaintenance(vin: String): Outcome<Maintenance>

    /** createServiceRequest: POST /vehicles/{vin}/service-requests. */
    suspend fun createServiceRequest(vin: String, request: ServiceRequestCreate): Outcome<ServiceRequest>
}

/** Remote commands. [com.drivelink.core.domain.command.RunCommandUseCase] runs the send-and-poll flow. */
interface CommandRepository {
    /** sendCommand: POST /vehicles/{vin}/commands. 202 with the queued [Command]. */
    suspend fun send(vin: String, request: CommandRequest): Outcome<Command>

    /** getCommand: GET /commands/{commandId}. [attempt] (1, 2, 3 ...) is sent as X-Poll-Attempt. */
    suspend fun get(commandId: String, attempt: Int): Outcome<Command>
}

interface AlertsRepository {
    /** listAlerts: GET /alerts. A non-null [vin] adds ?vin= and limits the list to one vehicle. */
    suspend fun list(vin: String? = null): Outcome<List<Alert>>

    /** markAlertRead: POST /alerts/{alertId}/read. 204, no body. */
    suspend fun markRead(alertId: String): Outcome<Unit>
}
