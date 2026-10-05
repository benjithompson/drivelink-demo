package com.drivelink.core.domain.config

import kotlinx.serialization.Serializable

/** API groups that a profile can send to a different host. Path prefixes: /auth = AUTH, /alerts = ALERTS, /vehicles and /commands = VEHICLE. /health and /me use [EndpointProfile.baseUrl]. */
enum class ApiGroup { AUTH, VEHICLE, ALERTS }

/**
 * An endpoint profile. [baseUrl] is scheme + host + optional port + optional path prefix,
 * without the /v1 API base path, for example https://vs1.mock.blazemeter.com or http://10.0.2.2:8080.
 * An empty [baseUrl] means "not configured" (the "Private location" built-in).
 */
@Serializable
data class EndpointProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKeyHeader: String? = null,
    val apiKeyValue: String? = null,
    val trustUserCerts: Boolean = false,
    val overrides: Map<ApiGroup, String> = emptyMap(),
    val builtIn: Boolean = false,
)

object BuiltInProfiles {
    const val CLOUD_ID = "blazemeter-cloud"
    const val PRIVATE_ID = "private-location"
    const val LOCAL_ID = "local"
    const val LOCAL_BASE_URL = "http://10.0.2.2:8080"
}
