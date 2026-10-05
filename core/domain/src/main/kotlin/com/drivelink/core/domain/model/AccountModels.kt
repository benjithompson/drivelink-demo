package com.drivelink.core.domain.model

import kotlinx.serialization.Serializable

/** 200 body of getHealth (GET /health). */
@Serializable
data class Health(
    val status: HealthStatus,
    val version: String,
    val service: String? = null,
)

@Serializable
enum class HealthStatus { UP, DEGRADED }

/** 200 body of getMe (GET /me). */
@Serializable
data class User(
    val userId: String,
    val name: String,
    val email: String,
    val units: UserUnits,
)

/** User.units in the spec (inline object). */
@Serializable
data class UserUnits(
    val distance: DistanceUnit,
    val temperature: TemperatureUnit,
)

@Serializable
enum class DistanceUnit { MI, KM }

@Serializable
enum class TemperatureUnit { F, C }
