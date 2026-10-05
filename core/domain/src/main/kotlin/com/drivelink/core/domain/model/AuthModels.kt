/**
 * Data classes for the API contract (api/openapi.yaml, components.schemas).
 *
 * Conventions for every file in this package:
 * - Property names are the JSON names.
 * - A required property is non-null and has no default. A missing required property fails the decode.
 * - An optional property is nullable with `= null`.
 * - Timestamp (ISO 8601 UTC), date (yyyy-MM-dd), LocalTime (HH:mm) and Vin stay as String.
 * - Problem bodies (4xx, 5xx) use [com.drivelink.core.domain.error.Problem].
 */
package com.drivelink.core.domain.model

import kotlinx.serialization.Serializable

/** Body of createToken (POST /auth/token). */
@Serializable
data class TokenRequest(
    val email: String,
    val password: String,
)

/** Body of refreshToken (POST /auth/refresh). */
@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

/** 200 body of createToken and refreshToken. */
@Serializable
data class TokenResponse(
    val accessToken: String,
    /** Seconds. */
    val expiresIn: Int,
    val refreshToken: String,
)
