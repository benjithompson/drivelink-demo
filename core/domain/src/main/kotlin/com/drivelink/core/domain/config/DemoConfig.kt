package com.drivelink.core.domain.config

import kotlinx.coroutines.flow.StateFlow

/** Build values that the app module provides (BuildConfig). */
data class AppBuildInfo(
    /** MOCK_BASE_URL from secrets.properties; empty when the file is absent. */
    val mockBaseUrl: String,
    /** Sent as X-Client-Version. */
    val clientVersion: String,
    val debug: Boolean,
)

/**
 * Demo settings, persisted (DataStore). The StateFlows are always current in memory, so the
 * OkHttp interceptors read them synchronously on each request; a change applies to the next
 * request without a restart.
 */
interface DemoConfig {
    val profiles: StateFlow<List<EndpointProfile>>
    val activeProfile: StateFlow<EndpointProfile>

    /** X-Scenario name; "default" means no header. */
    val scenario: StateFlow<String>

    /** Selected vehicle VIN; null until the vehicle list loads. */
    val vin: StateFlow<String?>

    suspend fun setActiveProfile(id: String)
    suspend fun upsertProfile(profile: EndpointProfile)

    /** Built-in profiles cannot be deleted. Deleting the active profile activates the cloud built-in. */
    suspend fun deleteProfile(id: String)
    suspend fun setScenario(name: String)
    suspend fun setVin(vin: String?)

    /**
     * External setup (instrumentation arguments, DemoConfigActivity intent).
     * A non-null [baseUrl] updates a profile named "External" and activates it.
     */
    suspend fun applyExternal(baseUrl: String?, scenario: String?)

    fun exportProfilesJson(): String
    suspend fun importProfilesJson(json: String)
}

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val email: String,
)

/** Token storage. [session] is read synchronously by the auth interceptor. */
interface SessionStore {
    val session: StateFlow<Session?>
    suspend fun save(session: Session)
    suspend fun clear()
}
