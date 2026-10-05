package com.drivelink.core.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Powertrain { EV, ICE, HYBRID }

/** One item of the listVehicles (GET /vehicles) 200 array. */
@Serializable
data class Vehicle(
    val vin: String,
    val nickname: String,
    val model: String,
    val trim: String? = null,
    val year: Int,
    val color: String? = null,
    val powertrain: Powertrain,
    /** Selects the in-app vehicle illustration and paint, for example `crossover-glacier-blue`. */
    val imageKey: String,
)

/** 200 body of getVehicleStatus (GET /vehicles/{vin}/status). */
@Serializable
data class VehicleStatus(
    val vin: String,
    val powertrain: Powertrain,
    /** ISO 8601, UTC. */
    val updatedAt: String,
    val connectivity: Connectivity,
    val locked: Boolean,
    val engineOn: Boolean,
    val climate: ClimateState,
    val doors: Doors,
    val windows: Windows? = null,
    /** ICE and HYBRID only. */
    val fuelPct: Int? = null,
    /** EV and HYBRID only. */
    val batteryPct: Int? = null,
    val rangeMi: Int,
    /** EV and HYBRID only. */
    val charging: ChargingState? = null,
    val tires: Tires,
    val odometerMi: Int,
    val oilLifePct: Int? = null,
    val aux12vOk: Boolean,
)

/** Telematics link state. [OFFLINE] means the status is the last known state. */
@Serializable
enum class Connectivity { ONLINE, ASLEEP, OFFLINE }

@Serializable
enum class OpenClosed { OPEN, CLOSED }

@Serializable
enum class WindowState { OPEN, CLOSED, VENTED }

@Serializable
enum class WheelPosition {
    @SerialName("fl") FL,
    @SerialName("fr") FR,
    @SerialName("rl") RL,
    @SerialName("rr") RR,
}

/** VehicleStatus.doors in the spec (inline object). */
@Serializable
data class Doors(
    val fl: OpenClosed,
    val fr: OpenClosed,
    val rl: OpenClosed,
    val rr: OpenClosed,
    val trunk: OpenClosed,
    val hood: OpenClosed,
)

/** VehicleStatus.windows in the spec (inline object). Every property is optional. */
@Serializable
data class Windows(
    val fl: WindowState? = null,
    val fr: WindowState? = null,
    val rl: WindowState? = null,
    val rr: WindowState? = null,
)

/** VehicleStatus.tires in the spec (inline object). */
@Serializable
data class Tires(
    val flPsi: Int,
    val frPsi: Int,
    val rlPsi: Int,
    val rrPsi: Int,
    val lowWarning: Boolean,
    /** Wheels with low pressure. Empty when no tire is low. */
    val lowWheels: List<WheelPosition>,
)

/** 200 body of getVehicleLocation (GET /vehicles/{vin}/location). */
@Serializable
data class Location(
    val lat: Double,
    val lon: Double,
    val accuracyM: Int,
    val address: String? = null,
    /** Short area name for the map label, for example "Harbor District". */
    val locality: String? = null,
    /** ISO 8601, UTC. */
    val updatedAt: String,
)
