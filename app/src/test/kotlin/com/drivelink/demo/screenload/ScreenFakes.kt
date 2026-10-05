package com.drivelink.demo.screenload

import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.Alert
import com.drivelink.core.domain.model.Maintenance
import com.drivelink.core.domain.model.ServiceRequest
import com.drivelink.core.domain.model.ServiceRequestCreate
import com.drivelink.core.domain.model.ServiceRequestStatus
import com.drivelink.core.domain.model.Trip
import com.drivelink.core.domain.repository.AlertsRepository
import com.drivelink.core.domain.repository.VehicleRepository
import com.drivelink.demo.AURORA
import com.drivelink.demo.Examples
import com.drivelink.demo.FakeAccountRepository
import com.drivelink.demo.FakeAuthRepository
import com.drivelink.demo.FakeDemoConfig
import com.drivelink.demo.FakeSessionStore
import com.drivelink.demo.FakeVehicleRepository
import com.drivelink.demo.SOLACE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.builtins.ListSerializer

/** Contract examples for Car Care, Trips and Alerts. */
object ExampleData {
    fun maintenance(name: String = "200.default"): Maintenance =
        Examples.decode("getMaintenance/$name.json", Maintenance.serializer())

    fun trips(name: String = "200.default"): List<Trip> =
        Examples.decode("listTrips/$name.json", ListSerializer(Trip.serializer()))

    fun alerts(name: String = "200.default"): List<Alert> =
        Examples.decode("listAlerts/$name.json", ListSerializer(Alert.serializer()))
}

fun networkError(id: String = "cid-net") = AppError.Network(timeout = true, correlationId = id)

/**
 * Vehicle fake. Status, vehicles and location come from the base fake ([base]).
 * Each demo car has its own maintenance and trips.
 */
class ScreenVehicleRepository(val base: FakeVehicleRepository = FakeVehicleRepository()) : VehicleRepository by base {
    val maintenance = mutableMapOf<String, Outcome<Maintenance>>(
        AURORA to Outcome.Ok(ExampleData.maintenance("200.default")),
        SOLACE to Outcome.Ok(ExampleData.maintenance("200.default-ice")),
    )
    val trips = mutableMapOf<String, Outcome<List<Trip>>>(
        AURORA to Outcome.Ok(ExampleData.trips("200.default")),
        SOLACE to Outcome.Ok(ExampleData.trips("200.default-ice")),
    )
    var maintenanceCalls = 0
    val tripLimits = mutableListOf<Int>()

    var serviceResult: Outcome<ServiceRequest> = Outcome.Ok(ServiceRequest("srq-8812", ServiceRequestStatus.REQUESTED))
    val serviceRequests = mutableListOf<Pair<String, ServiceRequestCreate>>()

    /** When set, createServiceRequest waits for it. Lets a test act while a request runs. */
    var serviceGate: CompletableDeferred<Unit>? = null

    override suspend fun getMaintenance(vin: String): Outcome<Maintenance> {
        maintenanceCalls++
        return maintenance.getValue(vin)
    }

    override suspend fun listTrips(vin: String, limit: Int): Outcome<List<Trip>> {
        tripLimits += limit
        return trips.getValue(vin)
    }

    override suspend fun createServiceRequest(vin: String, request: ServiceRequestCreate): Outcome<ServiceRequest> {
        serviceRequests += vin to request
        serviceGate?.await()
        return serviceResult
    }
}

/** Alerts fake. [markResults] answers markRead by alert id (default: success); [markGate] holds a call open. */
class ScreenAlertsRepository(var alerts: Outcome<List<Alert>> = Outcome.Ok(ExampleData.alerts())) : AlertsRepository {
    val markResults = mutableMapOf<String, Outcome<Unit>>()
    val marked = mutableListOf<String>()
    var markGate: CompletableDeferred<Unit>? = null
    var listCalls = 0

    override suspend fun list(vin: String?): Outcome<List<Alert>> {
        listCalls++
        return alerts
    }

    override suspend fun markRead(alertId: String): Outcome<Unit> {
        marked += alertId
        markGate?.await()
        return markResults[alertId] ?: Outcome.Ok(Unit)
    }
}

/** The repositories and the shared state, wired like the app wires them. */
class ScreenHarness {
    val config = FakeDemoConfig()
    val sessions = FakeSessionStore()
    val auth = FakeAuthRepository(sessions)
    val vehicles = ScreenVehicleRepository()
    val alerts = ScreenAlertsRepository()
    val account = FakeAccountRepository()
    val garage = GarageRepository(vehicles, alerts, account, auth, config)
}
