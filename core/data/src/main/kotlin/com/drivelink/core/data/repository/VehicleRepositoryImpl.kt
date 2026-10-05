package com.drivelink.core.data.repository

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.model.ChargeSettings
import com.drivelink.core.domain.model.ClimatePresets
import com.drivelink.core.domain.model.Location
import com.drivelink.core.domain.model.Maintenance
import com.drivelink.core.domain.model.ServiceRequest
import com.drivelink.core.domain.model.ServiceRequestCreate
import com.drivelink.core.domain.model.Trip
import com.drivelink.core.domain.model.Vehicle
import com.drivelink.core.domain.model.VehicleStatus
import com.drivelink.core.domain.repository.VehicleRepository
import com.drivelink.core.network.api.DriveLinkApi
import com.drivelink.core.network.apiCall
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

class VehicleRepositoryImpl @Inject constructor(
    private val api: DriveLinkApi,
) : VehicleRepository {

    override suspend fun listVehicles(): Outcome<List<Vehicle>> =
        apiCall(ListSerializer(Vehicle.serializer())) { api.listVehicles() }

    override suspend fun getStatus(vin: String): Outcome<VehicleStatus> =
        apiCall(VehicleStatus.serializer()) { api.getVehicleStatus(vin) }

    override suspend fun getLocation(vin: String): Outcome<Location> =
        apiCall(Location.serializer()) { api.getVehicleLocation(vin) }

    override suspend fun getChargeSettings(vin: String): Outcome<ChargeSettings> =
        apiCall(ChargeSettings.serializer()) { api.getChargeSettings(vin) }

    override suspend fun updateChargeSettings(vin: String, settings: ChargeSettings): Outcome<ChargeSettings> =
        apiCall(ChargeSettings.serializer()) { api.updateChargeSettings(vin, settings) }

    override suspend fun getClimatePresets(vin: String): Outcome<ClimatePresets> =
        apiCall(ClimatePresets.serializer()) { api.getClimatePresets(vin) }

    override suspend fun updateClimatePresets(vin: String, presets: ClimatePresets): Outcome<ClimatePresets> =
        apiCall(ClimatePresets.serializer()) { api.updateClimatePresets(vin, presets) }

    override suspend fun listTrips(vin: String, limit: Int): Outcome<List<Trip>> =
        apiCall(ListSerializer(Trip.serializer())) { api.listTrips(vin, limit.coerceIn(1, 50)) }

    override suspend fun getMaintenance(vin: String): Outcome<Maintenance> =
        apiCall(Maintenance.serializer()) { api.getMaintenance(vin) }

    override suspend fun createServiceRequest(vin: String, request: ServiceRequestCreate): Outcome<ServiceRequest> =
        apiCall(ServiceRequest.serializer()) { api.createServiceRequest(vin, request) }
}
