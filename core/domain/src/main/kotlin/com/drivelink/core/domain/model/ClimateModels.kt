package com.drivelink.core.domain.model

import kotlinx.serialization.Serializable

/**
 * Climate mode. [SET] holds ClimateState.setTempF or ClimateParams.tempF.
 * [LO] is full cooling. [HI] is full heating.
 */
@Serializable
enum class TempMode { OFF, LO, SET, HI }

/** Current climate state (VehicleStatus.climate, Departure.climate). */
@Serializable
data class ClimateState(
    val on: Boolean,
    val tempMode: TempMode,
    /** 62..82. Meaningful when [tempMode] is SET. */
    val setTempF: Int? = null,
    val frontDefrost: Boolean? = null,
    val rearDefrost: Boolean? = null,
    val heatedSteeringWheel: Boolean? = null,
    /** 0 = off, 3 = high. */
    val heatedSeats: Int? = null,
)

/** Remote-start options. Used with the START command and stored in presets. */
@Serializable
data class ClimateParams(
    val tempMode: TempMode,
    /** 62..82. Required when [tempMode] is SET. */
    val tempF: Int? = null,
    val frontDefrost: Boolean? = null,
    val rearDefrost: Boolean? = null,
    val heatedSteeringWheel: Boolean? = null,
    /** 0..3. */
    val heatedSeats: Int? = null,
    /** 1..10. */
    val durationMin: Int,
    val presetId: String? = null,
)

/** Body and 200 response of getClimatePresets and updateClimatePresets. At most 4 presets. */
@Serializable
data class ClimatePresets(
    val presets: List<ClimatePreset>,
)

/** ClimatePresets.presets item in the spec (inline object). */
@Serializable
data class ClimatePreset(
    val id: String,
    val name: String,
    val params: ClimateParams,
)
