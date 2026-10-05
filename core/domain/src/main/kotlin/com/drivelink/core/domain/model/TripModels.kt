package com.drivelink.core.domain.model

import kotlinx.serialization.Serializable

/** One item of the listTrips (GET /vehicles/{vin}/trips) 200 array. */
@Serializable
data class Trip(
    val id: String,
    /** ISO 8601, UTC. */
    val startedAt: String,
    /** ISO 8601, UTC. */
    val endedAt: String,
    val distanceMi: Double,
    val durationMin: Int,
    val avgMph: Double? = null,
    val efficiency: Double? = null,
    val efficiencyUnit: EfficiencyUnit? = null,
)

@Serializable
enum class EfficiencyUnit { MI_PER_KWH, MPG }
