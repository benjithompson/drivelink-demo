package com.drivelink.core.data.garage

import com.drivelink.core.data.FakeDemoConfig
import com.drivelink.core.data.Examples
import com.drivelink.core.domain.DriveLinkJson
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.EndpointProfile
import com.drivelink.core.domain.model.Alert
import com.drivelink.core.domain.model.ChargeSettings
import com.drivelink.core.domain.model.ClimatePresets
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
import com.drivelink.core.domain.repository.AccountRepository
import com.drivelink.core.domain.repository.AlertsRepository
import com.drivelink.core.domain.repository.AuthRepository
import com.drivelink.core.domain.repository.VehicleRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

const val AURORA = "DLEV26AURA0000101"
const val SOLACE = "DLGS25SLACE000202"

private fun <T> example(path: String, serializer: KSerializer<T>): T =
    DriveLinkJson.decodeFromString(serializer, Examples.read(path))

fun statusExample(name: String): VehicleStatus = example("getVehicleStatus/$name.json", VehicleStatus.serializer())

fun chargeSettingsExample(): ChargeSettings = example("getChargeSettings/200.default.json", ChargeSettings.serializer())

/**
 * Vehicle fake. [status] maps a VIN to its answer. [statusGate] holds every getStatus call until the
 * test completes it (for the race tests).
 */
class FakeVehicles : VehicleRepository {
    var vehicles: Outcome<List<Vehicle>> =
        Outcome.Ok(example("listVehicles/200.default.json", ListSerializer(Vehicle.serializer())))
    val status = mutableMapOf<String, Outcome<VehicleStatus>>(
        AURORA to Outcome.Ok(statusExample("200.default")),
        SOLACE to Outcome.Ok(statusExample("200.default-ice")),
    )
    var statusGate: CompletableDeferred<Unit>? = null
    var statusCalls = 0
    var chargeGets = 0
    var chargePuts = mutableListOf<ChargeSettings>()
    var charge: Outcome<ChargeSettings> = Outcome.Ok(chargeSettingsExample())
    var chargePut: Outcome<ChargeSettings>? = null

    override suspend fun listVehicles() = vehicles

    override suspend fun getStatus(vin: String): Outcome<VehicleStatus> {
        statusCalls++
        statusGate?.await()
        return status.getValue(vin)
    }

    override suspend fun getLocation(vin: String): Outcome<Location> =
        Outcome.Ok(Location(1.0, 2.0, 5, "1 Harbor Way", "Harbor District", "2026-10-02T16:58:00Z"))

    override suspend fun getChargeSettings(vin: String): Outcome<ChargeSettings> {
        chargeGets++
        return charge
    }

    override suspend fun updateChargeSettings(vin: String, settings: ChargeSettings): Outcome<ChargeSettings> {
        chargePuts += settings
        return chargePut ?: Outcome.Ok(chargeSettingsExample())
    }

    override suspend fun getClimatePresets(vin: String): Outcome<ClimatePresets> = error("not used")
    override suspend fun updateClimatePresets(vin: String, presets: ClimatePresets): Outcome<ClimatePresets> = error("not used")
    override suspend fun listTrips(vin: String, limit: Int): Outcome<List<Trip>> =
        Outcome.Ok(listOf(Trip("t1", "2026-10-02T10:00:00Z", "2026-10-02T10:30:00Z", 12.4, 30)))
    override suspend fun getMaintenance(vin: String): Outcome<Maintenance> = error("not used")
    override suspend fun createServiceRequest(vin: String, request: ServiceRequestCreate): Outcome<ServiceRequest> = error("not used")
}

class FakeAlerts : AlertsRepository {
    val alerts: List<Alert> = example("listAlerts/200.default.json", ListSerializer(Alert.serializer()))
    override suspend fun list(vin: String?): Outcome<List<Alert>> = Outcome.Ok(alerts)
    override suspend fun markRead(alertId: String): Outcome<Unit> = Outcome.Ok(Unit)
}

class FakeAccount : AccountRepository {
    var user: Outcome<User> = Outcome.Ok(example("getMe/200.default.json", User.serializer()))
    override suspend fun getHealth(): Outcome<Health> = error("not used")
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun getMe(): Outcome<User> {
        gate?.await()
        return user
    }
}

class FakeAuth : AuthRepository {
    var logouts = 0
    override suspend fun login(email: String, password: String): Outcome<TokenResponse> = error("not used")
    override suspend fun logout(): Outcome<Unit> {
        logouts++
        return Outcome.Ok(Unit)
    }
}

/** The fakes and the repository under test, wired like the app wires them. */
class GarageFixture {
    val config = FakeDemoConfig(EndpointProfile("test", "Test", "http://localhost"))
    val vehicles = FakeVehicles()
    val alerts = FakeAlerts()
    val account = FakeAccount()
    val auth = FakeAuth()
    val garage = GarageRepository(vehicles, alerts, account, auth, config)
}
