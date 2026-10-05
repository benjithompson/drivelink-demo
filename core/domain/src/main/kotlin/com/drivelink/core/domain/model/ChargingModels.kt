package com.drivelink.core.domain.model

import kotlinx.serialization.Serializable

/** Charging state in VehicleStatus.charging. EV and HYBRID only. */
@Serializable
data class ChargingState(
    val pluggedIn: Boolean,
    val active: Boolean,
    val state: ChargeState,
    /** Present when [pluggedIn] is true. */
    val chargerType: ChargerType? = null,
    val rateKw: Double? = null,
    val minutesToTarget: Int? = null,
    val targetPct: Int? = null,
    val energyAddedKwh: Double? = null,
    val costUsd: Double? = null,
)

/** NOT_PLUGGED when pluggedIn is false. CHARGING when active is true. */
@Serializable
enum class ChargeState { NOT_PLUGGED, CHARGING, COMPLETE, SCHEDULED }

@Serializable
enum class ChargerType { AC, DC }

@Serializable
enum class DayOfWeek { MON, TUE, WED, THU, FRI, SAT, SUN }

/** Body and 200 response of getChargeSettings and updateChargeSettings. */
@Serializable
data class ChargeSettings(
    /** 50..100, multiple of 10. */
    val acTargetPct: Int,
    /** 50..100, multiple of 10. */
    val dcTargetPct: Int,
    val offPeakMode: OffPeakMode? = null,
    val schedules: List<ChargeSchedule>,
    val departure: Departure? = null,
)

@Serializable
enum class OffPeakMode { OFF_PEAK_PRIORITY, OFF_PEAK_ONLY }

@Serializable
data class ChargeSchedule(
    val id: String,
    val days: List<DayOfWeek>,
    /** HH:mm. */
    val startTime: String,
    /** HH:mm. */
    val endTime: String,
    val enabled: Boolean,
)

@Serializable
data class Departure(
    val enabled: Boolean,
    val days: List<DayOfWeek>,
    /** HH:mm. */
    val time: String,
    val climate: ClimateState? = null,
)
